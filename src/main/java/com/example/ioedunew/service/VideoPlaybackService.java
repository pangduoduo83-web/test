package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.StoredFile;
import com.example.ioedunew.tenant.TenantContext;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;

/** Small signed playlists go through the API; all video segments go from private OSS through CDN. */
@Service
public class VideoPlaybackService {
    private final StorageProperties config;
    private final UploadStorage storage;
    private final OssObjectStore oss;
    private final Semaphore processors = new Semaphore(2);
    private final Object[] locks = new Object[32];
    // At most 64 raw playlists (<=4MB). Never cache expiring signed links.
    private final Map<String, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<String, Cached>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) { return size()>64; }
    });
    private static class Cached {
        final String body; final long until;
        Cached(String body, long until) { this.body=body; this.until=until; }
    }
    public VideoPlaybackService(StorageProperties config, UploadStorage storage, OssObjectStore oss) {
        this.config=config; this.storage=storage; this.oss=oss; Arrays.setAll(locks, i -> new Object());
    }

    public Map<String,Object> playback(String url) {
        String relative=relative(url);
        if (!storage.exists(relative)) throw new BusinessException(404,"视频不存在");
        Map<String,Object> result=new LinkedHashMap<>(); result.put("original", "/uploads/"+relative);
        if (config.getVideo().isHls() && storage.remote(relative)!=null) {
            long expires=Instant.now().getEpochSecond()+config.getCdn().getAuthSeconds();
            result.put("hls", link(relative,"master",expires,token(relative,expires)));
            result.put("expiresAt",expires);
        }
        return result;
    }

    private String relative(String url) {
        if (url==null || !url.matches("/uploads/\\d{6}/[A-Za-z0-9_-]+\\.(mp4|mov|webm)")) throw new BusinessException("视频地址无效");
        return url.substring(9);
    }

    private String token(String relative, long expires) {
        return OssUploadPolicy.hex(OssUploadPolicy.hmac(config.getCdn().getAuthKey().getBytes(StandardCharsets.UTF_8),
                "hls|"+TenantContext.require()+"|"+relative+"|"+expires));
    }
    private String link(String relative,String quality,long expires,String token) {
        return "/api/public/media/hls/"+relative+"/"+quality+".m3u8?expires="+expires+"&token="+token;
    }

    public String playlist(String relative,String quality,long expires,String signature) throws IOException {
        relative("/uploads/"+relative);
        if (!config.getVideo().isHls() || config.getCdn().getDomain().isEmpty()) throw new BusinessException(404,"HLS 未开启");
        if (signature==null || !signature.matches("[a-f0-9]{64}") || expires<=Instant.now().getEpochSecond()
                || !MessageDigest.isEqual(token(relative,expires).getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII)))
            throw new BusinessException(403,"播放链接已失效，请重新打开视频");
        StoredFile file=storage.remote(relative);
        if (file==null) throw new BusinessException(404,"视频不存在");
        if ("master".equals(quality)) {
            return "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=864000,AVERAGE-BANDWIDTH=664000\n"
                    +link(relative,"360p",expires,signature)+"\n#EXT-X-STREAM-INF:BANDWIDTH=1600000,AVERAGE-BANDWIDTH=1296000\n"
                    +link(relative,"720p",expires,signature)+"\n";
        }
        if (!"360p".equals(quality) && !"720p".equals(quality)) throw new BusinessException(404,"清晰度不存在");
        String prefix=config.getOss().getPrefix()+"/hls/"+TenantContext.require()+"/"+relative+"/"+quality+"/media";
        String raw=load(file.getObjectKey(),prefix,quality);
        return signSegments(raw,prefix);
    }

    private String load(String source,String prefix,String quality) throws IOException {
        synchronized (locks[(prefix.hashCode() & 0x7fffffff)%locks.length]) {
            Cached existing=cache.get(prefix);
            long now=System.currentTimeMillis();
            if (existing!=null && existing.until>now) {
                if (existing.body==null) throw new BusinessException(503,"云端视频处理暂不可用，可播放原视频");
                return existing.body;
            }
            if (!processors.tryAcquire()) throw new BusinessException(429,"视频正在准备，请稍后再试");
            try {
                String key=prefix+".m3u8";
                com.aliyun.oss.model.ObjectMetadata meta=oss.metadata(key);
                if (meta==null) {
                    String params="360p".equals(quality)?"s_640x360,vb_600000,ab_64000":"s_1280x720,vb_1200000,ab_96000";
                    // 5-second slices; initialize only 10 seconds. IMM performs all CPU-intensive work.
                    oss.generatePlaylist(source,prefix,"hls/m3u8,vcodec_h264,pixfmt_yuv420p,"+params
                            +",sopt_1,scaletype_fit,arotate_1,vbopt_1,acodec_aac,abopt_1,st_5000,initd_10000,ta_10");
                    meta=oss.metadata(key);
                }
                if (meta==null || meta.getContentLength()<=0 || meta.getContentLength()>65536) throw new IOException("云端播放列表无效");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=oss.open(key,null,null)) { UploadStorage.copy(in,out,meta.getContentLength()); }
                String raw=new String(out.toByteArray(),StandardCharsets.UTF_8);
                signSegments(raw,prefix); // Validate every URI before caching anything.
                cache.put(prefix,new Cached(raw,now+300000)); return raw;
            } catch (IOException | RuntimeException e) { cache.put(prefix,new Cached(null,now+30000)); throw e; }
            finally { processors.release(); }
        }
    }

    String signSegments(String raw,String prefix) {
        if (!raw.startsWith("#EXTM3U") || !raw.contains("#EXT-X-ENDLIST")) throw new BusinessException(502,"云端播放列表无效");
        String directory=prefix.substring(0,prefix.lastIndexOf('/')+1);
        StringBuilder result=new StringBuilder(); int segments=0;
        for(String item:raw.split("\\r?\\n")) {
            String line=item.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                // Only an unencrypted media playlist is expected. Never forward external keys or nested URIs.
                if (line.contains("URI=") || line.startsWith("#EXT-X-STREAM-INF")) throw new BusinessException(502,"不支持的云端播放列表");
                result.append(line);
            } else {
                if (!line.matches("media-[A-Za-z0-9_-]+-\\d+\\.ts")) throw new BusinessException(502,"云端视频分片路径无效");
                result.append(CdnUrls.sign(config.getCdn(),directory+line)); segments++;
            }
            result.append('\n');
        }
        if (segments==0) throw new BusinessException(502,"视频没有可播放分片");
        return result.toString();
    }
}

package com.example.ioedunew.service;

import com.aliyun.oss.model.ObjectMetadata;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.StoredFile;
import com.example.ioedunew.tenant.TenantContext;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoPlaybackTest {
    StorageProperties config=new StorageProperties();
    UploadStorage storage=mock(UploadStorage.class);
    OssObjectStore oss=mock(OssObjectStore.class);
    VideoPlaybackService service;
    String relative="202609/video.mp4";
    @BeforeEach void setup() {
        TenantContext.set("tenant-a");
        config.getCdn().setDomain("https://cdn.example.com"); config.getCdn().setAuthKey("0123456789abcdef");
        config.getVideo().setHls(true);
        when(storage.exists(relative)).thenReturn(true);
        StoredFile file=new StoredFile(); file.setObjectKey("ioedu/tenants/tenant-a/"+relative);
        when(storage.remote(relative)).thenReturn(file);
        service=new VideoPlaybackService(config,storage,oss);
    }
    @AfterEach void clear() { TenantContext.clear(); }
    @Test void cdnSignatureMatchesIndependentFixtureAndRejectsTraversal() {
        assertEquals("https://cdn.example.com/ioedu/tenants/a/202609/video.mp4?auth_key=1700000000-0-0-72dc87f5b4ac8b8f4c039981d18a3bd0",
                CdnUrls.sign(config.getCdn(),"ioedu/tenants/a/202609/video.mp4",1700000000));
        assertThrows(IllegalArgumentException.class,()->CdnUrls.sign(config.getCdn(),"../secret"));
    }
    @Test void manifestLinksAreTenantBoundAndExpiredLinksCannotTriggerCloudWork() throws Exception {
        Map<String,Object> playback=service.playback("/uploads/"+relative);
        String hls=(String)playback.get("hls"); String signature=hls.substring(hls.indexOf("&token=")+7);
        long expiry=((Number)playback.get("expiresAt")).longValue();
        String master=service.playlist(relative,"master",expiry,signature);
        assertTrue(master.contains("360p.m3u8")); assertTrue(master.contains("720p.m3u8"));
        TenantContext.set("tenant-b");
        assertEquals(403,assertThrows(BusinessException.class,()->service.playlist(relative,"master",expiry,signature)).getCode());
        TenantContext.set("tenant-a");
        assertEquals(403,assertThrows(BusinessException.class,()->service.playlist(relative,"master",Instant.now().getEpochSecond()-1,signature)).getCode());
        verifyNoInteractions(oss);
    }
    @Test void externalKeyUrisAndPathTraversalNeverAppearInSignedManifest() {
        String prefix="ioedu/hls/tenant-a/"+relative+"/360p/media";
        String raw="#EXTM3U\n#EXTINF:5,\nmedia-abc-0.ts\n#EXT-X-ENDLIST\n";
        String signed=service.signSegments(raw,prefix);
        assertTrue(signed.contains("https://cdn.example.com/"+prefix+"-abc-0.ts?auth_key="));
        for(String invalid:Arrays.asList(raw.replace("media-abc-0.ts","https://evil.example/file.ts"),
                raw.replace("media-abc-0.ts","../secret.ts"),raw.replace("#EXTINF:5,","#EXT-X-KEY:METHOD=AES-128,URI=\"https://evil.example/key\""),
                raw.replace("#EXT-X-ENDLIST", ""))) assertThrows(BusinessException.class,()->service.signSegments(invalid,prefix));
    }
    @Test void cloudPlaylistIsReusedAndOnlySmallPlaylistBytesReachApplication() throws Exception {
        String body="#EXTM3U\n#EXTINF:5,\nmedia-abc-0.ts\n#EXT-X-ENDLIST\n";
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        String key="ioedu/hls/tenant-a/"+relative+"/360p/media.m3u8";
        ObjectMetadata meta=new ObjectMetadata(); meta.setContentLength(bytes.length);
        when(oss.metadata(key)).thenReturn(meta); when(oss.open(key,null,null)).thenReturn(new ByteArrayInputStream(bytes));
        Map<String,Object> playback=service.playback("/uploads/"+relative);
        String hls=(String)playback.get("hls"); String signature=hls.substring(hls.indexOf("&token=")+7);
        long expiry=((Number)playback.get("expiresAt")).longValue();
        assertTrue(service.playlist(relative,"360p",expiry,signature).contains("cdn.example.com"));
        service.playlist(relative,"360p",expiry,signature);
        verify(oss,times(1)).open(key,null,null); verify(oss,never()).generatePlaylist(anyString(),anyString(),anyString());
    }
    @Test void disabledHlsAndHistoricalLocalFilesKeepOriginalPlayback() {
        config.getVideo().setHls(false);
        assertFalse(service.playback("/uploads/"+relative).containsKey("hls"));
        config.getVideo().setHls(true); when(storage.remote(relative)).thenReturn(null);
        assertFalse(service.playback("/uploads/"+relative).containsKey("hls"));
        assertThrows(BusinessException.class,()->service.playback("https://other.example/video.mp4"));
        verifyNoInteractions(oss);
    }
}

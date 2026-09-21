package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.entity.SubmissionAsset;
import com.example.ioedunew.repository.SubmissionAssetRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Resolves current-tenant uploads through storage; never fetches arbitrary student URLs. */
@Service
public class SubmissionMaterialService {
    private final SubmissionAssetRepository assets;
    private final UploadStorage storage;
    private final ObjectMapper json;
    private final ReviewModelService models;
    private final MineruService mineru;
    @Value("${ioedu.review.ffmpeg:ffmpeg}") private String ffmpeg;
    @Value("${ioedu.review.ffprobe:ffprobe}") private String ffprobe;
    public SubmissionMaterialService(SubmissionAssetRepository assets, UploadStorage storage, ObjectMapper json, ReviewModelService models, MineruService mineru) {
        this.assets=assets; this.storage=storage; this.json=json; this.models=models; this.mineru=mineru;
    }
    public String validate(Long user, List<Map<String, Object>> incoming) {
        ArrayNode result = json.createArrayNode();
        if (incoming == null) return result.toString();
        if (incoming.size()>8) throw new BusinessException("最多提交8个附件");
        Set<String> urls = new HashSet<>(); long total=0;
        for (Map<String, Object> item : incoming) {
            if (item == null) throw new BusinessException("附件格式错误");
            String url = String.valueOf(item.get("url"));
            SubmissionAsset asset=assets.findByUrlAndUserId(url,user).orElseThrow(() -> new BusinessException("请使用本人上传的成果附件"));
            if (!urls.add(url)) throw new BusinessException("请勿重复提交同一附件");
            validateLocation(url); total+=asset.getSize();
            if (total>200L*1024*1024) throw new BusinessException("一份成果附件合计不能超过200MB");
            result.addObject().put("url",url).put("name",asset.getName()).put("size",asset.getSize());
        }
        return result.toString();
    }
    public String validateLegacy(String url) {
        if (url==null || url.isEmpty()) return null;
        validateLocation(url);
        if (!url.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp|gif)$")) throw new BusinessException("旧版截图仅支持图片，请使用成果附件上传");
        return url;
    }
    Path local(String url) {
        if (url==null || !url.startsWith("/uploads/")) throw new BusinessException("仅支持本站上传的成果文件");
        Path path=storage.resolveForRead(url.substring(9));
        if (path==null || !Files.isRegularFile(path)) throw new BusinessException("附件不存在或路径无效");
        return path;
    }
    private String validateLocation(String url) {
        if (url == null || !url.startsWith("/uploads/")) throw new BusinessException("仅支持本站上传的成果文件");
        String relative = url.substring(9);
        if (!storage.exists(relative)) throw new BusinessException("附件不存在或路径无效");
        return relative;
    }
    public ArrayNode list(Submission submission) {
        try {
            ArrayNode list=(ArrayNode)json.readTree(submission.getAttachments()==null ? "[]" : submission.getAttachments());
            if (submission.getAttachmentUrl()!=null && !submission.getAttachmentUrl().isEmpty()) {
                boolean present=false;
                for(JsonNode n:list) if(submission.getAttachmentUrl().equals(n.path("url").asText())) present=true;
                if(!present) list.addObject().put("url",submission.getAttachmentUrl()).put("name","历史成果截图");
            }
            return list;
        } catch(Exception e) { throw new BusinessException("附件列表格式错误"); }
    }
    public ObjectNode extract(JsonNode file, int index, JsonNode previous, java.util.function.Consumer<ObjectNode> checkpoint) {
        ObjectNode material=json.createObjectNode();
        material.put("id","file-"+index); material.put("name",file.path("name").asText("附件"));
        material.put("url",file.path("url").asText()); material.put("fingerprint",fingerprint());
        if (previous != null && fingerprint().equals(previous.path("fingerprint").asText())) {
            for (String key : new String[]{"remoteId", "remoteStatus"}) if (previous.has(key)) material.set(key, previous.get(key));
        }
        material.put("status", "PARSING"); checkpoint.accept(material);
        ArrayNode segments=material.putArray("segments"); ArrayNode warnings=material.putArray("warnings");
        try (UploadStorage.WorkingFile working = storage.materialize(validateLocation(file.path("url").asText()), 100L*1024*1024)) {
            Path path=working.path();
            String extension=path.getFileName().toString().replaceAll("^.*\\.","").toLowerCase(Locale.ROOT);
            if(Files.size(path)>100L*1024*1024) throw new IllegalStateException("文件超过100MB解析上限");
            if(Arrays.asList("png","jpg","jpeg","webp","gif").contains(extension)) {
                BufferedImage image=readImage(path);
                add(segments,index,"图片",models.describe(jpeg(image)),null,null);
            } else if(Arrays.asList("pdf","docx","doc").contains(extension)) {
                mineru.parse(path,segments,warnings,index,material,checkpoint);
            } else if(isTextSource(extension)) {
                source(path,segments,warnings,index);
            } else if(Arrays.asList("zip","rar","7z","bin").contains(extension)) {
                warnings.add("压缩包或二进制文件不会自动展开，请教师下载后人工核对");
            } else if(Arrays.asList("mp4","mov","webm").contains(extension)) {
                video(path,segments,warnings,index);
            } else throw new IllegalStateException("此格式暂不支持自动解析，请人工查看");
        } catch(Exception e) { warnings.add(friendly(e)); }
        material.put("status",segments.size()==0 ? "FAILED" : warnings.size()>0 ? "PARTIAL" : "DONE");
        return material;
    }
    public String fingerprint() { return models.fingerprint() + "-" + mineru.fingerprint(); }

    /** 代码和文本按纯文本读取,不执行、不编译,只作为 AI 评审的材料证据。 */
    private void source(Path path,ArrayNode segments,ArrayNode warnings,int index) throws Exception {
        byte[] bytes;
        try(InputStream in=Files.newInputStream(path)) {
            bytes=MineruService.readBounded(in,256L*1024);
        }
        String text=new String(bytes,StandardCharsets.UTF_8).replace("\u0000", "").trim();
        if(text.isEmpty()) throw new IllegalStateException("源码文件为空或无法按文本读取");
        add(segments,index,"源码/文本",text,null,null);
        if(bytes.length>=256L*1024) warnings.add("源码超过256KB，仅将开头内容交给 AI；完整文件请教师打开原件核对");
    }

    private boolean isTextSource(String extension) {
        return Arrays.asList("c","h","cc","cpp","cxx","java","py","js","jsx","ts","tsx","vue",
                "html","css","scss","sql","json","xml","yaml","yml","md","txt","csv","log","sh",
                "bat","ps1","ino","kicad_sch","kicad_pcb","sch","brd","hex").contains(extension);
    }

    private void video(Path path,ArrayNode segments,ArrayNode warnings,int index) throws Exception {
        Path temp=Files.createTempDirectory("ioedu-review-");
        try {
            Path info=temp.resolve("probe.json");
            run(Arrays.asList(ffprobe,"-v","error","-protocol_whitelist","file,pipe","-show_format","-show_streams","-of","json",path.toString()),info);
            JsonNode probe=json.readTree(Files.readAllBytes(info));
            double seconds=probe.path("format").path("duration").asDouble(0);
            if(!Double.isFinite(seconds) || seconds<=0) throw new IllegalStateException("无法读取视频时长");
            if(seconds>300) throw new IllegalStateException("自动评审支持5分钟以内视频，请剪辑后重新提交");
            boolean audio=false, video=false;
            for(JsonNode stream:probe.path("streams")) { audio|="audio".equals(stream.path("codec_type").asText()); video|="video".equals(stream.path("codec_type").asText()); }
            if(!video) throw new IllegalStateException("文件没有可用的视频轨道");
            warnings.add("视频画面为均匀抽样，未出现于抽样中的行为不能判定为未完成");
            int frames=Math.min(8,Math.max(1,(int)Math.ceil(seconds/10)));
            for(int i=0;i<frames;i++) {
                double at=Math.max(0,seconds-0.1)*(i+0.5)/frames;
                Path frame=temp.resolve("frame-"+i+".jpg");
                try {
                    run(Arrays.asList(ffmpeg,"-nostdin","-v","error","-y","-protocol_whitelist","file,pipe","-ss",String.format(Locale.ROOT,"%.3f",at),"-i",path.toString(),"-frames:v","1","-vf","scale=1280:1280:force_original_aspect_ratio=decrease","-threads","1",frame.toString()),temp.resolve("frame.log"));
                    add(segments,index,"画面 "+time(at),models.describe(Files.readAllBytes(frame)),null,at);
                } catch(Exception e) { warnings.add("画面 "+time(at)+"："+friendly(e)); }
            }
            if(audio) for(int from=0;from<seconds;from+=60) {
                Path wav=temp.resolve("speech.wav");
                try {
                    run(Arrays.asList(ffmpeg,"-nostdin","-v","error","-y","-protocol_whitelist","file,pipe","-ss",String.valueOf(from),"-i",path.toString(),"-t","60","-vn","-ac","1","-ar","16000",wav.toString()),temp.resolve("speech.log"));
                    String transcript=models.transcribe(wav);
                    if(!transcript.trim().isEmpty()) add(segments,index,"语音 "+time(from)+"–"+time(Math.min(from+60,seconds)),transcript,null,(double)from);
                } catch(Exception e) { warnings.add("语音 "+time(from)+"："+friendly(e)); }
            }
            else warnings.add("视频无音轨，仅分析抽样画面");
        } finally {
            try(java.util.stream.Stream<Path> files=Files.walk(temp)) {
                files.sorted(Comparator.reverseOrder()).forEach(f -> {try {Files.deleteIfExists(f);} catch(IOException ignored) {}});
            }
        }
    }
    private static void run(List<String> args,Path log) throws Exception {
        Process process;
        try { process=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start(); }
        catch(IOException e) {throw new IllegalStateException("服务器尚未安装或配置FFmpeg/FFprobe");}
        try {
            if(!process.waitFor(90,TimeUnit.SECONDS)) throw new IllegalStateException("视频处理超时");
            if(process.exitValue()!=0) throw new IllegalStateException("视频解码失败，请确认文件格式和完整性");
        } finally { if(process.isAlive()) process.destroyForcibly(); }
    }
    private BufferedImage readImage(Path path) throws Exception {
        if (path.toString().toLowerCase(Locale.ROOT).endsWith(".webp")) {
            Path directory = Files.createTempDirectory("review-webp-");
            try {
                Path converted = directory.resolve("image.jpg");
                run(Arrays.asList(ffmpeg,"-nostdin","-v","error","-y","-protocol_whitelist","file,pipe","-i",path.toString(),"-frames:v","1","-vf","scale=1400:1400:force_original_aspect_ratio=decrease",converted.toString()),directory.resolve("convert.log"));
                return readImage(Files.readAllBytes(converted));
            } finally {
                Files.deleteIfExists(directory.resolve("image.jpg"));Files.deleteIfExists(directory.resolve("convert.log"));Files.deleteIfExists(directory);
            }
        }
        try(InputStream file = Files.newInputStream(path)) {
            return readImage(MineruService.readBounded(file, 100L*1024*1024));
        }
    }
    static BufferedImage readImage(byte[] bytes) throws Exception {
        try(javax.imageio.stream.ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<javax.imageio.ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IllegalStateException("图片无法解码，请改用JPG或PNG");
            javax.imageio.ImageReader reader=readers.next();
            try {
                reader.setInput(input);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if((long)width*height>40000000L) throw new IllegalStateException("图片像素过大，请压缩到4000万像素以内");
                javax.imageio.ImageReadParam params=reader.getDefaultReadParam();
                int sample=Math.max(1,Math.max(width,height)/1600); params.setSourceSubsampling(sample,sample,0,0);
                return reader.read(0,params);
            } finally {reader.dispose();}
        }
    }
    static byte[] jpeg(BufferedImage input) throws IOException {
        if(input==null) throw new IOException("无法读取图片");
        double scale=Math.min(1,1400.0/Math.max(input.getWidth(),input.getHeight()));
        BufferedImage image=new BufferedImage(Math.max(1,(int)(input.getWidth()*scale)),Math.max(1,(int)(input.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try {g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());g.drawImage(input,0,0,image.getWidth(),image.getHeight(),null);} finally {g.dispose();}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); ImageIO.write(image,"jpg",bytes); return bytes.toByteArray();
    }
    private void add(ArrayNode segments,int file,String location,String text,Integer page,Double seconds) {
        ObjectNode node=segments.addObject().put("id","file-"+file+"-"+(segments.size())).put("location",location).put("text",text.substring(0,Math.min(6000,text.length())));
        if(page!=null) node.put("page",page);
        if(seconds!=null) node.put("seconds",seconds);
    }
    private static String time(double seconds) {return String.format(Locale.ROOT,"%02d:%02d",(int)seconds/60,(int)seconds%60);}
    private static String friendly(Exception e) {
        if(e instanceof IllegalStateException || e instanceof BusinessException) return e.getMessage();
        return "解析失败，请确认文件未损坏、未加密，或转换格式后提交";
    }
}

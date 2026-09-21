package com.example.ioedunew.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Official V4 and self-hosted MinerU 4.x V1. Configuration is resolved inside TenantContext. */
@Service
public class MineruService {
    private final AiConfigService configs;
    private final ObjectMapper json;
    private final ReviewModelService vision;
    public MineruService(AiConfigService configs,ObjectMapper json,ReviewModelService vision) {
        this.configs=configs;this.json=json;this.vision=vision;
    }
    public String fingerprint() {
        AiConfigService.AiConfig cfg=config();
        return org.springframework.util.DigestUtils.md5DigestAsHex(("mineru-v1|"+configs.mineruMode()+"|"+cfg.enabled+"|"+cfg.baseUrl+"|"+cfg.model+"|"+cfg.apiKey).getBytes(StandardCharsets.UTF_8));
    }
    private AiConfigService.AiConfig config() {return configs.mediaConfig("selfhost".equals(configs.mineruMode())?"mineruLocal":"mineruCloud");}
    public void parse(Path file,ArrayNode segments,ArrayNode warnings,int index,ObjectNode state,Consumer<ObjectNode> checkpoint) throws Exception {
        AiConfigService.AiConfig cfg=config();
        boolean cloud=!"selfhost".equals(configs.mineruMode());
        if(!cfg.enabled || cfg.baseUrl.isEmpty() || (cloud && (cfg.apiKey==null || cfg.apiKey.isEmpty())))
            throw new IllegalStateException("请在本站AI设置中配置并启用所选MinerU服务");
        String base=cfg.baseUrl.replaceAll("/+$","");
        Path zip=Files.createTempFile("mineru-result-",".zip");
        try {
            String download=cloud?cloud(file,base,cfg,state,checkpoint):selfhost(file,base,cfg,state,checkpoint);
            transfer(base,download,"GET",null,Collections.emptyMap(),cloud?null:cfg.apiKey,zip,200L*1024*1024);
            readArchive(zip,segments,warnings,index,file.toString().toLowerCase(Locale.ROOT).endsWith(".pdf"));
        } finally {Files.deleteIfExists(zip);}
    }
    private String cloud(Path file,String base,AiConfigService.AiConfig cfg,ObjectNode state,Consumer<ObjectNode> checkpoint) throws Exception {
        String id=state.path("remoteId").asText("");
        if(id.isEmpty() || "failed".equals(state.path("remoteStatus").asText())) {
            ObjectNode body=json.createObjectNode();body.put("model_version",cfg.model.isEmpty()?"vlm":cfg.model);
            body.put("enable_formula",true).put("enable_table",true);
            body.putArray("files").addObject().put("name",file.getFileName().toString()).put("data_id",UUID.randomUUID().toString());
            JsonNode response=cloudResponse(request(base,base+"/api/v4/file-urls/batch","POST",body,cfg.apiKey));
            id=required(response.path("data"),"batch_id");
            String upload=response.path("data").path("file_urls").path(0).asText();
            state.put("remoteId",id).put("remoteStatus","uploading");checkpoint.accept(state);
            transfer(base,upload,"PUT",file,Collections.emptyMap(),null,null,1024*1024);
            state.put("remoteStatus","pending");checkpoint.accept(state);
        } else if("uploading".equals(state.path("remoteStatus").asText())) {
            // Upload completion is uncertain after a crash; avoid pretending it was submitted.
            state.put("remoteStatus","failed");checkpoint.accept(state);
            throw new IllegalStateException("上次MinerU上传中断，请重试此附件");
        }
        long deadline=System.nanoTime()+20L*60*1000000000L;
        while(System.nanoTime()<deadline) {
            JsonNode result=cloudResponse(request(base,base+"/api/v4/extract-results/batch/"+safeId(id),"GET",null,cfg.apiKey)).path("data").path("extract_result").path(0);
            String status=result.path("state").asText();
            state.put("remoteStatus",status);checkpoint.accept(state);
            if("done".equals(status)) return required(result,"full_zip_url");
            if("failed".equals(status)) throw new IllegalStateException("MinerU解析失败，请检查文件格式、页数、账户额度后重试");
            if(!Arrays.asList("waiting-file","pending","running","converting").contains(status)) throw new IllegalStateException("MinerU返回了无法识别的任务状态");
            Thread.sleep(3000);
        }
        throw new IllegalStateException("MinerU仍在处理中，请稍后重试此附件以继续查询原任务");
    }
    private String selfhost(Path file,String base,AiConfigService.AiConfig cfg,ObjectNode state,Consumer<ObjectNode> checkpoint) throws Exception {
        String id=state.path("remoteId").asText("");
        if(id.isEmpty() || Arrays.asList("failed","canceled").contains(state.path("remoteStatus").asText())) {
            JsonNode health=request(base,base+"/v1/health","GET",null,cfg.apiKey);
            JsonNode formats=health.path("features").path("output_formats");
            if(formats.isArray()) {
                boolean zip=false;for(JsonNode format:formats) if("zip".equals(format.asText())) zip=true;
                if(!zip) throw new IllegalStateException("该MinerU V1服务未启用ZIP输出，请检查服务能力");
            }
            ObjectNode uploadBody=json.createObjectNode().put("filename",file.getFileName().toString()).put("bytes",Files.size(file)).put("purpose","parse").put("sha256sum",sha256(file));
            String ext=file.toString().toLowerCase(Locale.ROOT);
            uploadBody.put("mime_type",ext.endsWith(".pdf")?"application/pdf":ext.endsWith(".docx")?"application/vnd.openxmlformats-officedocument.wordprocessingml.document":"application/msword");
            JsonNode upload=request(base,base+"/v1/uploads","POST",uploadBody,cfg.apiKey);
            if("pending".equals(upload.path("status").asText())) {
                String method=upload.path("upload_method").asText("PUT");
                if(!"PUT".equals(method)) throw new IllegalStateException("不支持的MinerU文件上传方式");
                Map<String,String> headers=new LinkedHashMap<>();
                Iterator<Map.Entry<String,JsonNode>> fields=upload.path("upload_headers").fields();
                while(fields.hasNext()) {Map.Entry<String,JsonNode> field=fields.next();headers.put(field.getKey(),field.getValue().asText());}
                transfer(base,required(upload,"upload_url"),method,file,headers,cfg.apiKey,null,1024*1024);
                upload=request(base,base+"/v1/uploads/"+safeId(required(upload,"id"))+"/complete","POST",null,cfg.apiKey);
            }
            if(!"completed".equals(upload.path("status").asText())) throw new IllegalStateException("MinerU上传尚未完成");
            ObjectNode body=json.createObjectNode().put("tier",cfg.model.isEmpty()?"standard":cfg.model);
            body.putArray("output_formats").add("zip");
            body.putArray("files").addObject().putObject("source").put("type","file_id").put("file_id",required(upload.path("file"),"id"));
            JsonNode job=request(base,base+"/v1/parse/jobs","POST",body,cfg.apiKey);
            id=required(job,"job_id");state.put("remoteId",id).put("remoteStatus",job.path("status").asText("queued"));checkpoint.accept(state);
        }
        long deadline=System.nanoTime()+20L*60*1000000000L;
        while(System.nanoTime()<deadline) {
            JsonNode job;
            try {job=request(base,base+"/v1/parse/jobs/"+safeId(id),"GET",null,cfg.apiKey);}
            catch(IllegalStateException e) {
                if(e.getMessage().contains("HTTP 404")) {state.put("remoteStatus","failed");checkpoint.accept(state);}
                throw e;
            }
            String status=job.path("status").asText();state.put("remoteStatus",status);checkpoint.accept(state);
            if("completed".equals(status) || "partial".equals(status)) {
                for(JsonNode f:job.path("files")) if("completed".equals(f.path("status").asText())) {
                    String artifact=required(f.path("output_files").path("zip"),"file_id");
                    return base+"/v1/files/"+safeId(artifact)+"/content";
                }
                throw new IllegalStateException("MinerU任务未返回完成的文档结果");
            }
            if(Arrays.asList("failed","canceled").contains(status)) throw new IllegalStateException("自部署MinerU解析失败，请检查服务日志后重试");
            if(!Arrays.asList("queued","running").contains(status)) throw new IllegalStateException("MinerU V1任务状态不兼容");
            Thread.sleep(3000);
        }
        throw new IllegalStateException("MinerU仍在处理中，请稍后重试此附件以继续查询原任务");
    }
    void readArchive(Path path,ArrayNode segments,ArrayNode warnings,int index,boolean pdf) throws Exception {
        try(ZipFile zip=new ZipFile(path.toFile())) {
            ZipEntry structured=null,legacy=null,markdown=null;int entries=0;
            Enumeration<? extends ZipEntry> all=zip.entries();
            while(all.hasMoreElements()) {
                ZipEntry entry=all.nextElement();if(++entries>10000) throw new IllegalStateException("MinerU结果文件过多");
                String name=entry.getName();
                if(!safeEntry(name)) throw new IllegalStateException("MinerU结果包含非法文件路径");
                if(name.endsWith("structured_content.json"))structured=entry;
                else if(name.endsWith("_content_list.json") || name.equals("content_list.json"))legacy=entry;
                else if(name.endsWith("full.md") || name.endsWith("markdown.md"))markdown=entry;
            }
            JsonNode content=null;String prefix="";
            ZipEntry chosen=structured!=null?structured:legacy;
            if(chosen!=null) {
                try(InputStream in=zip.getInputStream(chosen)) {content=json.readTree(readBounded(in,16*1024*1024));}
                prefix=chosen.getName().contains("/")?chosen.getName().substring(0,chosen.getName().lastIndexOf('/')+1):"";
            }
            ArrayNode blocks=json.createArrayNode();
            if(content!=null && content.path("pages").isArray()) {
                if(content.has("is_full_document") && !content.path("is_full_document").asBoolean())warnings.add("MinerU返回的文档不完整，请核对原件");
                for(JsonNode page:content.path("pages")) for(JsonNode block:page.path("blocks")) {
                    ObjectNode copy=block.deepCopy();if(page.path("page_idx").isIntegralNumber())copy.set("page_idx",page.get("page_idx"));blocks.add(copy);
                }
            } else if(content!=null && content.isArray()) blocks=(ArrayNode)content;
            int images=0,chars=0;
            for(JsonNode block:blocks) {
                if(segments.size()>=1200 || chars>=300000) {warnings.add("解析结果较长，已保留前1200块/30万字，其余请查看原件");break;}
                String text=blockText(block);
                String imagePath=block.path("img_path").asText(block.path("image_path").asText(""));
                if(imagePath.isEmpty() && "image".equals(block.path("type").asText()))
                    warnings.add("部分文档图像块没有可识别的图片路径，仅保留已提取的说明文字，请核实原图");
                if(!imagePath.isEmpty()) {
                    if(++images<=12) {
                        try {
                            if(!safeEntry(imagePath))throw new IllegalStateException("非法图片路径");
                            ZipEntry image=zip.getEntry(prefix+imagePath);
                            if(image==null)image=zip.getEntry(imagePath);
                            if(image==null)throw new IllegalStateException("解析结果缺少内嵌图片");
                            byte[] bytes;try(InputStream in=zip.getInputStream(image)){bytes=readBounded(in,8*1024*1024);}
                            // Decode through the same bounded image reader as ordinary attachments.
                            text+="\n图片观察："+vision.describe(SubmissionMaterialService.jpeg(SubmissionMaterialService.readImage(bytes)));
                        } catch(Exception e) {warnings.add("文档第"+images+"张图片未分析，请人工核实（需配置图片识别服务）");}
                    } else if(images==13)warnings.add("文档图片超过12张，其余图片请人工查看");
                }
                if(text.trim().isEmpty())continue;
                Integer page=block.path("page_idx").isIntegralNumber() && block.path("page_idx").asInt()>=0?block.path("page_idx").asInt()+1:null;
                for(int offset=0;offset<text.length();offset+=6000) {
                    if(chars>=300000 || segments.size()>=1200) {warnings.add("解析结果达到30万字/1200块上限，剩余内容请人工查看");break;}
                    String chunk=text.substring(offset,Math.min(offset+6000,text.length()));chars+=chunk.length();
                    ObjectNode segment=segments.addObject().put("id","file-"+index+"-"+segments.size()).put("text",chunk);
                    segment.put("location",page==null?"解析内容块 "+segments.size():(pdf?"第":"Word转换后第")+page+"页 · 内容块 "+segments.size());
                    if(page!=null && pdf)segment.put("page",page);
                    if(block.has("bbox"))segment.set("bbox",block.get("bbox"));
                }
            }
            if(segments.size()==0 && markdown!=null) {
                String text;try(InputStream in=zip.getInputStream(markdown)){text=new String(readBounded(in,4*1024*1024),StandardCharsets.UTF_8);}
                warnings.add("MinerU仅返回可用Markdown，缺少可靠页码；按内容段引用，不推测页码");
                int limit=Math.min(text.length(),300000);
                if(text.length()>limit)warnings.add("Markdown超过30万字，其余请人工查看");
                for(int offset=0;offset<limit;offset+=6000)segments.addObject().put("id","file-"+index+"-"+segments.size()).put("location","MinerU正文段 "+segments.size()).put("text",text.substring(offset,Math.min(offset+6000,limit)));
            }
            if(segments.size()==0)throw new IllegalStateException("MinerU未返回可用于评审的文档内容");
        }
    }
    static String blockText(JsonNode block) {
        StringBuilder text=new StringBuilder();
        for(String key:new String[]{"text","content","table_body","html","latex","image_caption","image_footnote","table_caption","table_footnote","list_items","caption","footnote"}) {
            JsonNode value=block.path(key);
            if(value.isTextual())text.append(value.asText()).append('\n');
            else if(value.isArray()) for(JsonNode item:value) {
                if(item.isTextual())text.append(item.asText()).append('\n');
                else if(item.isObject())text.append(blockText(item));
            }
        }
        return text.toString();
    }
    private JsonNode request(String base,String url,String method,JsonNode body,String key) throws Exception {
        HttpURLConnection conn=open(base,url,method,key);
        try {
            if(body!=null) {conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");try(OutputStream out=conn.getOutputStream()){out.write(json.writeValueAsBytes(body));}}
            check(conn);try(InputStream in=conn.getInputStream()){return json.readTree(readBounded(in,4*1024*1024));}
        } finally {conn.disconnect();}
    }
    private JsonNode cloudResponse(JsonNode response) {
        if(!response.path("code").isIntegralNumber() || response.path("code").asInt()!=0)throw new IllegalStateException("MinerU官方接口请求失败，请检查Token有效期、额度和服务设置");
        return response;
    }
    private void transfer(String base,String target,String method,Path input,Map<String,String> headers,String key,Path output,long limit) throws Exception {
        for(int redirects=0;redirects<4;redirects++) {
            HttpURLConnection conn=open(base,target,method,key);
            try {
                for(Map.Entry<String,String> h:headers.entrySet()) {
                    if(!h.getKey().matches("[A-Za-z0-9-]+") || h.getValue().contains("\r") || h.getValue().contains("\n"))throw new IllegalStateException("MinerU上传头格式错误");
                    conn.setRequestProperty(h.getKey(),h.getValue());
                }
                if(input!=null) {conn.setDoOutput(true);conn.setFixedLengthStreamingMode(Files.size(input));try(OutputStream out=conn.getOutputStream()){Files.copy(input,out);}}
                int code=conn.getResponseCode();
                if(code>=300 && code<400 && "GET".equals(method)) {
                    target=conn.getURL().toURI().resolve(conn.getHeaderField("Location")).toString();headers=Collections.emptyMap();continue;
                }
                check(conn);
                try(InputStream in=conn.getInputStream()) {
                    if(output==null) readBounded(in,limit);
                    else try(OutputStream out=Files.newOutputStream(output)){copyBounded(in,out,limit);}
                }
                return;
            } finally {conn.disconnect();}
        }
        throw new IllegalStateException("MinerU下载重定向次数过多");
    }
    private HttpURLConnection open(String base,String target,String method,String key) throws Exception {
        URI uri=new URI(base+"/").resolve(target);URI origin=new URI(base);
        if(!Arrays.asList("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null)throw new IllegalStateException("MinerU返回了无效地址");
        boolean same=sameOrigin(origin,uri);
        // Configured private service is allowed; its untrusted cross-origin artifact URLs must be public HTTPS.
        if(!same) {
            if(!"https".equals(uri.getScheme()))throw new IllegalStateException("MinerU跨域文件地址必须使用HTTPS");
            for(InetAddress address:InetAddress.getAllByName(uri.getHost())) if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())throw new IllegalStateException("MinerU跨域文件地址不能指向内网");
        }
        HttpURLConnection conn=(HttpURLConnection)uri.toURL().openConnection();
        conn.setRequestMethod(method);conn.setConnectTimeout(15000);conn.setReadTimeout(120000);conn.setInstanceFollowRedirects(false);
        if(same && key!=null && !key.isEmpty())conn.setRequestProperty("Authorization","Bearer "+key);
        return conn;
    }
    static boolean sameOrigin(URI a,URI b) {return Objects.equals(a.getScheme(),b.getScheme()) && a.getHost()!=null && a.getHost().equalsIgnoreCase(b.getHost()) && port(a)==port(b);}
    private static int port(URI u) {return u.getPort()<0?("https".equals(u.getScheme())?443:80):u.getPort();}
    private static void check(HttpURLConnection conn) throws IOException {
        int code=conn.getResponseCode();if(code<200||code>=300)throw new IllegalStateException("MinerU服务 HTTP "+code+"，请检查服务配置或任务是否仍有效");
    }
    private static String required(JsonNode node,String key) {String v=node.path(key).asText("");if(v.isEmpty())throw new IllegalStateException("MinerU响应缺少 "+key);return v;}
    private static String safeId(String id) {if(!id.matches("[A-Za-z0-9_-]{1,200}"))throw new IllegalStateException("MinerU任务编号无效");return id;}
    static boolean safeEntry(String name) {return !name.startsWith("/")&&!name.contains("\\")&&!name.contains(":")&&!Arrays.asList(name.split("/")).contains("..");}
    static byte[] readBounded(InputStream in,long limit) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();copyBounded(in,out,limit);return out.toByteArray();}
    private static void copyBounded(InputStream in,OutputStream out,long limit) throws IOException {byte[] buffer=new byte[8192];long size=0;int n;while((n=in.read(buffer))!=-1){size+=n;if(size>limit)throw new IllegalStateException("MinerU结果超过处理大小上限");out.write(buffer,0,n);}}
    private static String sha256(Path path) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(path)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
    }
}

package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.*;
import com.example.ioedunew.repository.SubmissionAssetRepository;
import com.example.ioedunew.tenant.TenantContext;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultimodalReviewTest {
    final ObjectMapper json=new ObjectMapper();
    @TempDir Path temp;
    @AfterEach void clearTenant(){TenantContext.clear();}

    @Test void stageRubricOverridesProjectWithoutUsingStageWeight() {
        Project project=new Project();project.setReviewRubric("[{\"name\":\"整体\",\"points\":100}]");
        project.setAssessments("[{\"name\":\"演示\",\"weight\":20,\"rubric\":[{\"name\":\"报警\",\"points\":100}]}]");
        assertEquals("报警",ReviewRubric.forSubmission(project,"演示").get(0).path("name").asText());
        assertEquals(100,ReviewRubric.forSubmission(project,"演示").get(0).path("points").asInt());
        assertEquals("整体",ReviewRubric.forSubmission(project,null).get(0).path("name").asText());
    }
    @Test void invalidRubricsAreRejected() {
        for(String value:Arrays.asList("{}","[{\"name\":\"x\",\"points\":50}]","[{\"name\":\"x\",\"points\":100.1}]","[{\"name\":\"x\",\"points\":50},{\"name\":\"x\",\"points\":50}]"))
            assertThrows(BusinessException.class,()->ReviewRubric.validate(value));
    }
    @Test void totalIsCalculatedFromCriteriaAndInventedCitationsAreDiscarded() throws Exception {
        ArrayNode rubric=ReviewRubric.validate("[{\"name\":\"功能\",\"points\":100}]");
        JsonNode answer=json.readTree("{\"suggestedScore\":100,\"criteria\":[{\"name\":\"功能\",\"score\":72,\"sourceIds\":[\"real\",\"invented\"]}]}");
        Map<String,JsonNode> sources=Collections.singletonMap("real",json.createObjectNode().put("id","real").put("page",3));
        Map<String,Object> result=AiReviewService.validateCriteria(answer,rubric,sources);
        assertEquals(72,result.get("suggestedScore"));
        Map<?,?> row=(Map<?,?>)((List<?>)result.get("criteria")).get(0);
        assertEquals(true,row.get("needsConfirmation"));assertEquals(1,((List<?>)row.get("evidence")).size());
    }
    @Test void malformedModelScoresDoNotBecomePassingGrades() throws Exception {
        ArrayNode rubric=ReviewRubric.validate("[{\"name\":\"功能\",\"points\":100}]");
        for(String value:Arrays.asList("{}","{\"criteria\":[{\"name\":\"功能\",\"score\":101}]}","{\"criteria\":[{\"name\":\"功能\",\"score\":\"80\"}]}")) {
            JsonNode answer=json.readTree(value);
            assertThrows(BusinessException.class,()->AiReviewService.validateCriteria(answer,rubric,Collections.emptyMap()));
        }
    }
    @Test void attachmentOwnershipAndLocalPathAreEnforced() throws Exception {
        SubmissionAssetRepository assets=mock(SubmissionAssetRepository.class);UploadStorage storage=mock(UploadStorage.class);
        SubmissionMaterialService materials=new SubmissionMaterialService(assets,storage,json,mock(ReviewModelService.class),mock(MineruService.class));
        Map<String,Object> file=new HashMap<>();file.put("url","/uploads/202609/one.pdf");
        when(assets.findByUrlAndUserId(anyString(),eq(2L))).thenReturn(Optional.empty());
        assertThrows(BusinessException.class,()->materials.validate(2L,Collections.singletonList(file)));
        assertThrows(BusinessException.class,()->materials.local("https://example.org/private.pdf"));
        assertThrows(BusinessException.class,()->materials.local("/uploads/../../secret.pdf"));
    }

    @Test void sourceCodeIsReadAsEvidenceWithoutExecution() throws Exception {
        Path source=temp.resolve("main.cpp");
        Files.write(source,"#include <iostream>\nint main(){ return 0; }".getBytes(StandardCharsets.UTF_8));
        UploadStorage storage=mock(UploadStorage.class); when(storage.exists("202609/main.cpp")).thenReturn(true);
        when(storage.materialize(eq("202609/main.cpp"),anyLong()))
                .thenReturn(new UploadStorage.WorkingFile(source,false));
        SubmissionMaterialService materials=new SubmissionMaterialService(mock(SubmissionAssetRepository.class),storage,json,
                mock(ReviewModelService.class),mock(MineruService.class));
        ObjectNode result=materials.extract(json.createObjectNode().put("url","/uploads/202609/main.cpp").put("name","main.cpp"),0,null,s->{});
        assertEquals("DONE",result.path("status").asText());
        assertTrue(result.path("segments").get(0).path("text").asText().contains("return 0"));
    }

    @Test void privateReferenceAnswerIsExcludedFromPublicProjectJson() throws Exception {
        Project project=new Project(); project.setTitle("demo"); project.setReferenceAnswer("teacher-only");
        project.setCreatedAt(null); project.setUpdatedAt(null);
        assertFalse(json.writeValueAsString(project).contains("teacher-only"));
    }
    @Test void zipContentKeepsTableFormulaAndRealPageNumbers() throws Exception {
        Path archive=archive("x_content_list.json","[{\"type\":\"table\",\"table_body\":\"<table><tr><td>12V</td></tr></table>\",\"page_idx\":2},{\"type\":\"equation\",\"text\":\"V=IR\",\"page_idx\":4}]");
        MineruService service=new MineruService(mock(AiConfigService.class),json,mock(ReviewModelService.class));
        ArrayNode segments=json.createArrayNode();service.readArchive(archive,segments,json.createArrayNode(),0,true);
        assertEquals(2,segments.size());assertEquals(3,segments.get(0).path("page").asInt());
        assertTrue(segments.get(0).path("text").asText().contains("12V"));assertTrue(segments.get(1).path("text").asText().contains("V=IR"));
    }
    @Test void selfHostedStructuredOutputAndWordLocationsAreSupported() throws Exception {
        Path archive=archive("structured_content.json","{\"pages\":[{\"page_idx\":0,\"blocks\":[{\"type\":\"text\",\"content\":\"实验成功\"}]}],\"is_full_document\":true}");
        MineruService service=new MineruService(mock(AiConfigService.class),json,mock(ReviewModelService.class));
        ArrayNode segments=json.createArrayNode();service.readArchive(archive,segments,json.createArrayNode(),0,false);
        assertFalse(segments.get(0).has("page"));assertTrue(segments.get(0).path("location").asText().contains("Word转换后"));
    }
    @Test void archivesCannotTraversePathsAndResponseSizesAreBounded() throws Exception {
        assertFalse(MineruService.safeEntry("../secrets"));assertFalse(MineruService.safeEntry("C:/temp/test"));
        assertFalse(MineruService.safeEntry("images\\test.png"));assertTrue(MineruService.safeEntry("images/test.png"));
        assertThrows(IllegalStateException.class,()->MineruService.readBounded(new ByteArrayInputStream(new byte[20]),10));
        Path archive=archive("../x_content_list.json","[]");
        MineruService service=new MineruService(mock(AiConfigService.class),json,mock(ReviewModelService.class));
        assertThrows(IllegalStateException.class,()->service.readArchive(archive,json.createArrayNode(),json.createArrayNode(),0,true));
    }
    @Test void credentialsAreScopedToSchemeHostAndPort() throws Exception {
        assertTrue(MineruService.sameOrigin(new URI("https://mineru.net"),new URI("https://mineru.net:443/objects/a")));
        assertFalse(MineruService.sameOrigin(new URI("https://mineru.net"),new URI("https://cdn.example.org/a")));
        assertFalse(MineruService.sameOrigin(new URI("http://localhost:8000"),new URI("http://localhost:8001")));
    }
    @Test void cloudUploadPollDownloadAndResumeUseDocumentedProtocol() throws Exception { exercise(false); }
    @Test void selfHostedUploadCompleteJobDownloadUseDocumentedProtocol() throws Exception { exercise(true); }

    @Test void actualVideoFramesHaveSourceTimestampsAndDoNotRequireAnAudioTrack() throws Exception {
        Process available;
        try {available=new ProcessBuilder("ffmpeg","-version").redirectErrorStream(true).redirectOutput(temp.resolve("version.txt").toFile()).start();}
        catch(IOException e){Assumptions.assumeTrue(false,"FFmpeg is not installed on this test host");return;}
        assertTrue(available.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));
        Path video=temp.resolve("demo.mp4");
        Process generate=new ProcessBuilder("ffmpeg","-nostdin","-v","error","-y","-f","lavfi","-i","color=c=red:s=160x120:d=2","-c:v","mpeg4",video.toString()).redirectErrorStream(true).redirectOutput(temp.resolve("generate.txt").toFile()).start();
        assertTrue(generate.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,generate.exitValue());
        UploadStorage storage=mock(UploadStorage.class);when(storage.exists("202609/demo.mp4")).thenReturn(true);
        when(storage.materialize(eq("202609/demo.mp4"), anyLong())).thenReturn(new UploadStorage.WorkingFile(video, false));
        ReviewModelService model=mock(ReviewModelService.class);when(model.describe(any())).thenReturn("红色测试画面");when(model.fingerprint()).thenReturn("vision-test");
        MineruService mineru=mock(MineruService.class);when(mineru.fingerprint()).thenReturn("mineru-test");
        SubmissionMaterialService material=new SubmissionMaterialService(mock(SubmissionAssetRepository.class),storage,json,model,mineru);
        org.springframework.test.util.ReflectionTestUtils.setField(material,"ffmpeg","ffmpeg");
        org.springframework.test.util.ReflectionTestUtils.setField(material,"ffprobe","ffprobe");
        ObjectNode result=material.extract(json.createObjectNode().put("url","/uploads/202609/demo.mp4").put("name","演示"),0,null,s->{});
        assertEquals("PARTIAL",result.path("status").asText());assertTrue(result.path("segments").size()>0,result.toString());
        assertTrue(result.path("segments").get(0).path("seconds").asDouble()>=0);
        assertTrue(result.path("warnings").toString().contains("无音轨"));verify(model,never()).transcribe(any());
    }

    void exercise(boolean local) throws Exception {
        byte[] zip=Files.readAllBytes(archive("content_list.json","[{\"text\":\"proof\",\"page_idx\":0}]"));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String base="http://127.0.0.1:"+server.getAddress().getPort();AtomicInteger creates=new AtomicInteger();
        List<String> failures=Collections.synchronizedList(new ArrayList<>());
        server.createContext("/",exchange -> {
            String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();
            String auth=exchange.getRequestHeaders().getFirst("Authorization");
            byte[] request=MineruService.readBounded(exchange.getRequestBody(),1000000);String output="{}";byte[] bytes=null;
            if(path.equals("/bytes")) {
                if(!"PUT".equals(method) || request.length==0 || (local?!"Bearer tenant-a-key".equals(auth):auth!=null))failures.add("upload/auth");
            } else if(path.equals("/artifact") || path.equals("/v1/files/out/content")) {
                if(local?!"Bearer tenant-a-key".equals(auth):auth!=null)failures.add("download/auth");bytes=zip;
            } else {
                if(!"Bearer tenant-a-key".equals(auth))failures.add("API auth");
                if(path.equals("/api/v4/file-urls/batch")) {creates.incrementAndGet();output="{\"code\":0,\"data\":{\"batch_id\":\"batch1\",\"file_urls\":[\""+base+"/bytes\"]}}";}
                else if(path.equals("/api/v4/extract-results/batch/batch1"))output="{\"code\":0,\"data\":{\"extract_result\":[{\"state\":\"done\",\"full_zip_url\":\""+base+"/artifact\"}]}}";
                else if(path.equals("/v1/health"))output="{\"features\":{\"output_formats\":[\"zip\"]}}";
                else if(path.equals("/v1/uploads")) {creates.incrementAndGet();output="{\"id\":\"upload1\",\"status\":\"pending\",\"upload_method\":\"PUT\",\"upload_url\":\"/bytes\"}";}
                else if(path.equals("/v1/uploads/upload1/complete"))output="{\"status\":\"completed\",\"file\":{\"id\":\"file1\"}}";
                else if(path.equals("/v1/parse/jobs"))output="{\"job_id\":\"job1\",\"status\":\"queued\"}";
                else if(path.equals("/v1/parse/jobs/job1"))output="{\"status\":\"completed\",\"files\":[{\"status\":\"completed\",\"output_files\":{\"zip\":{\"file_id\":\"out\"}}}]}";
                else failures.add(path);
            }
            if(bytes==null)bytes=output.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            AiConfigService configs=mock(AiConfigService.class);
            AiConfigService.AiConfig cfg=new AiConfigService.AiConfig();cfg.enabled=true;cfg.baseUrl=base;cfg.apiKey="tenant-a-key";cfg.model=local?"standard":"vlm";
            when(configs.mineruMode()).thenReturn(local?"selfhost":"cloud");when(configs.mediaConfig(anyString())).thenAnswer(call->{assertEquals("tenant-a",TenantContext.require());return cfg;});
            MineruService service=new MineruService(configs,json,mock(ReviewModelService.class));
            Path input=temp.resolve("test.pdf");Files.write(input,"test-pdf".getBytes(StandardCharsets.UTF_8));
            ObjectNode state=json.createObjectNode();AtomicInteger saved=new AtomicInteger();
            TenantContext.runAs("tenant-a",()-> {
                try {
                    ArrayNode segments=json.createArrayNode();service.parse(input,segments,json.createArrayNode(),0,state,s->saved.incrementAndGet());
                    assertEquals("proof\n",segments.get(0).path("text").asText());
                    service.parse(input,json.createArrayNode(),json.createArrayNode(),0,state,s->saved.incrementAndGet());
                } catch(Exception e){throw new RuntimeException(e);}
            });
            assertEquals(1,creates.get());assertTrue(saved.get()>0);assertTrue(failures.isEmpty(),failures.toString());
        } finally {server.stop(0);}
    }
    Path archive(String name,String content) throws IOException {
        Path file=Files.createTempFile(temp,"output-",".zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(file))) {zip.putNextEntry(new ZipEntry(name));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        return file;
    }
}

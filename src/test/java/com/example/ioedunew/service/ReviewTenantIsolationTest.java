package com.example.ioedunew.service;

import com.example.ioedunew.common.SecretCrypto;
import com.example.ioedunew.entity.*;
import com.example.ioedunew.repository.*;
import com.example.ioedunew.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ReviewTenantIsolationTest {
    @Test void cloudLocalAndOtherMerchantKeysAreIndependentAndMasked() {
        SystemSettingRepository repository=mock(SystemSettingRepository.class);
        Map<String,Map<String,SystemSetting>> databases=new HashMap<>();
        when(repository.findAll()).thenAnswer(c->new ArrayList<>(databases.computeIfAbsent(TenantContext.require(),x->new HashMap<>()).values()));
        when(repository.findById(anyString())).thenAnswer(c->Optional.ofNullable(databases.computeIfAbsent(TenantContext.require(),x->new HashMap<>()).get(c.getArgument(0))));
        when(repository.save(any())).thenAnswer(c->{SystemSetting s=c.getArgument(0);databases.get(TenantContext.require()).put(s.getSettingKey(),s);return s;});
        AiConfigService service=new AiConfigService(repository,new SecretCrypto("test-master-only"));
        TenantContext.runAs("merchant-a",()-> {
            Map<String,Object> fields=new HashMap<>();fields.put("mineruMode","cloud");
            fields.put("mineruCloudApiKey","cloud-a-secret");fields.put("mineruLocalApiKey","local-a-secret");
            fields.put("mineruCloudEnabled",true);fields.put("mineruLocalEnabled",true);
            service.update(fields);
            service.update(Collections.singletonMap("mineruMode","selfhost"));
            assertEquals("local-a-secret",service.mediaConfig("mineruLocal").apiKey);
            service.update(Collections.singletonMap("mineruMode","cloud"));
            service.update(Collections.singletonMap("mineruCloudApiKey",""));
            assertEquals("cloud-a-secret",service.mediaConfig("mineruCloud").apiKey);
            assertFalse(service.view().toString().contains("cloud-a-secret"));
            assertTrue(databases.get("merchant-a").get("ai.mineruCloudApiKey").getSettingValue().startsWith("enc:v1:"));
        });
        TenantContext.runAs("merchant-b",()-> {
            assertNull(service.mediaConfig("mineruCloud").apiKey);
            assertFalse(service.mediaConfig("mineruLocal").enabled);
            service.update(Collections.singletonMap("mineruCloudApiKey","cloud-b-secret"));
        });
        TenantContext.runAs("merchant-a",()->assertEquals("cloud-a-secret",service.mediaConfig("mineruCloud").apiKey));
        assertNull(TenantContext.get());
    }

    @Test void sameSubmissionIdsInDifferentTenantsRunAndCacheIndependently() throws Exception {
        ObjectMapper json=new ObjectMapper();
        SubmissionRepository submissions=mock(SubmissionRepository.class);ProjectRepository projects=mock(ProjectRepository.class);
        ReviewJobRepository jobs=mock(ReviewJobRepository.class);SubmissionMaterialService materials=mock(SubmissionMaterialService.class);
        AiReviewService reviews=mock(AiReviewService.class);AiConfigService configs=mock(AiConfigService.class);
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        Submission submission=new Submission();submission.setId(1L);submission.setProjectId(1L);submission.setContent("proof");
        when(submissions.lockById(1L)).thenReturn(Optional.of(submission));when(submissions.findById(1L)).thenReturn(Optional.of(submission));when(submissions.existsById(1L)).thenReturn(true);
        when(projects.findById(1L)).thenReturn(Optional.of(new Project()));when(materials.list(any())).thenReturn(json.createArrayNode());when(materials.fingerprint()).thenReturn("parser-v1");
        AiConfigService.AiConfig cfg=new AiConfigService.AiConfig();cfg.enabled=true;cfg.apiKey="mock-key";cfg.baseUrl="mock";cfg.model="test";when(configs.effective()).thenReturn(cfg);
        ConcurrentMap<String,ReviewJob> database=new ConcurrentHashMap<>();
        when(jobs.saveAndFlush(any())).thenAnswer(c->{ReviewJob job=c.getArgument(0);job.setId(1L);database.put(TenantContext.require(),copy(job,json));return job;});
        when(jobs.save(any())).thenAnswer(c->{ReviewJob job=c.getArgument(0);database.put(TenantContext.require(),copy(job,json));return job;});
        when(jobs.findById(1L)).thenAnswer(c->Optional.ofNullable(copy(database.get(TenantContext.require()),json)));
        when(jobs.findTopBySubmissionIdOrderByIdDesc(1L)).thenAnswer(c->Optional.ofNullable(copy(database.get(TenantContext.require()),json)));
        CountDownLatch entered=new CountDownLatch(2), release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        when(reviews.evaluate(any(),any(),any())).thenAnswer(c->{String tenant=TenantContext.require();calls.incrementAndGet();entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return Collections.singletonMap("summary",tenant);});
        ReviewJobService service=new ReviewJobService(submissions,projects,jobs,materials,mock(ReviewModelService.class),reviews,configs,json,manager);
        try {
            TenantContext.runAs("a",()->service.start(1L,false,null));TenantContext.runAs("b",()->service.start(1L,false,null));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            TenantContext.runAs("a",()->service.start(1L,false,null));assertEquals(2,calls.get());
            release.countDown();
            long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<limit && database.values().stream().anyMatch(j->!"DONE".equals(j.getStatus())))Thread.sleep(20);
            for(String tenant:Arrays.asList("a","b")) TenantContext.runAs(tenant,()-> {
                Map<String,Object> state=service.status(1L);assertEquals("DONE",state.get("status"));
                assertTrue(state.get("result").toString().contains(tenant));
                service.start(1L,false,null);
            });
            assertEquals(2,calls.get());assertNull(TenantContext.get());
        } finally {release.countDown();service.shutdown();}
    }
    private ReviewJob copy(ReviewJob job,ObjectMapper ignored) {
        if(job==null)return null;
        ReviewJob copy=new ReviewJob();copy.setId(job.getId());copy.setSubmissionId(job.getSubmissionId());copy.setFingerprint(job.getFingerprint());copy.setStatus(job.getStatus());copy.setProgress(job.getProgress());copy.setMessage(job.getMessage());copy.setMaterials(job.getMaterials());copy.setResult(job.getResult());copy.setCreatedAt(job.getCreatedAt());copy.setUpdatedAt(job.getUpdatedAt());return copy;
    }
}

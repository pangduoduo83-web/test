package com.example.ioedunew.service;

import com.aliyun.oss.model.ObjectMetadata;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.entity.*;
import com.example.ioedunew.repository.*;
import com.example.ioedunew.tenant.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectUploadTest {
    StorageProperties config=new StorageProperties();
    UploadStorage storage=mock(UploadStorage.class);
    OssObjectStore oss=mock(OssObjectStore.class);
    DirectUploadRepository uploads=mock(DirectUploadRepository.class);
    StoredFileRepository files=mock(StoredFileRepository.class);
    SubmissionAssetRepository assets=mock(SubmissionAssetRepository.class);
    TenantQuotaService quota=mock(TenantQuotaService.class);
    StorageQuotaLock lock=mock(StorageQuotaLock.class);
    AuthUser student=new AuthUser(7L,"STUDENT","merchant-a");
    AuthUser teacher=new AuthUser(8L,"TEACHER","merchant-a");
    DirectUploadService service;
    final String id="0123456789abcdef0123456789abcdef";

    @BeforeEach void setup() {
        TenantContext.set("merchant-a");
        config.getOss().setBucket("ioedu-test"); config.getOss().setRegion("cn-beijing");
        config.getOss().setAccessKeyId("test-id"); config.getOss().setAccessKeySecret("test-only-secret");
        config.getOss().setDirectUpload(true);
        when(oss.enabled()).thenReturn(true); when(oss.bucket()).thenReturn("ioedu-test"); when(oss.prefix()).thenReturn("ioedu");
        when(lock.run(any())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(0)).get());
        when(storage.objectKey(anyString())).thenAnswer(call -> "ioedu/tenants/"+TenantContext.require()+"/"+call.getArgument(0));
        service=new DirectUploadService(config,storage,oss,new OssUploadPolicy(config,new ObjectMapper()),uploads,files,assets,quota,lock);
    }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void signedGrantPinsTenantKeyExactSizeAndExpiryWithoutExposingSecret() throws Exception {
        Map<String,Object> result=service.initiate(student,"submission","视频.mp4",123);
        assertEquals("https://ioedu-test.oss-cn-beijing.aliyuncs.com",result.get("host"));
        Map<?,?> fields=(Map<?,?>)result.get("fields");
        String key=(String)fields.get("key");
        assertTrue(key.startsWith("ioedu/staging/merchant-a/"));
        JsonNode policy=new ObjectMapper().readTree(Base64.getDecoder().decode((String)fields.get("policy")));
        assertTrue(Instant.parse(policy.get("expiration").asText()).isAfter(Instant.now()));
        assertTrue(policy.get("conditions").toString().contains("[\"content-length-range\",123,123]"));
        assertTrue(policy.get("conditions").toString().contains("\"key\":\""+key+"\""));
        assertEquals("private",fields.get("x-oss-object-acl"));
        assertEquals("true",fields.get("x-oss-forbid-overwrite"));
        assertFalse(result.toString().contains("test-only-secret"));
        assertTrue(((String)fields.get("x-oss-signature")).matches("[a-f0-9]{64}"));
        verify(quota).checkStorageQuota(123);
        verify(uploads).saveAndFlush(any(DirectUpload.class));
    }

    @Test void legacyModeAndAuthorizationAreEnforcedBeforeIssuingAnyGrant() {
        config.getOss().setDirectUpload(false);
        assertEquals("server",service.initiate(student,"submission","a.mp4",1).get("mode"));
        assertEquals("server",service.initiate(student,"submission","main.cpp",1).get("mode"));
        assertEquals(403,assertThrows(BusinessException.class,()->service.initiate(student,"file","a.pdf",1)).getCode());
        assertThrows(BusinessException.class,()->service.initiate(student,"submission","a.exe",1));
        assertThrows(BusinessException.class,()->service.initiate(student,"submission","a.mp4",104857601));
        assertThrows(BusinessException.class,()->service.initiate(student,"image","a.png",0));
        assertThrows(BusinessException.class,()->service.initiate(teacher,"file","course.mp4",524288001L));
        assertEquals("server",service.initiate(teacher,"file","course.mp4",524288000L).get("mode"));
        verifyNoInteractions(uploads,files,assets,quota);
    }

    @Test void exhaustedQuotaOrTooManyOutstandingGrantsCannotIssueAnotherUpload() {
        doThrow(new BusinessException(409,"quota")).when(quota).checkStorageQuota(anyLong());
        assertThrows(BusinessException.class,()->service.initiate(student,"submission","a.mp4",10));
        verify(uploads,never()).saveAndFlush(any());
        when(uploads.countByUserIdAndCompletedFalseAndExpiresAtAfter(eq(7L),any())).thenReturn(10L);
        assertEquals(429,assertThrows(BusinessException.class,()->service.initiate(student,"submission","a.mp4",10)).getCode());
    }

    private DirectUpload grant() {
        DirectUpload grant=new DirectUpload(); grant.setId(id); grant.setUserId(7L); grant.setKind("submission");
        grant.setName("a.mp4"); grant.setRelativePath("202609/"+id+".mp4"); grant.setSizeBytes(3);
        grant.setBucket("ioedu-test"); grant.setStagingKey("ioedu/staging/merchant-a/"+grant.getRelativePath());
        grant.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(uploads.findByIdAndUserId(id,7L)).thenReturn(Optional.of(grant));
        return grant;
    }

    @Test void completedUploadIsPromotedWithinOssAndConfirmationIsIdempotent() {
        DirectUpload grant=grant();
        ObjectMetadata meta=new ObjectMetadata(); meta.setContentLength(3); meta.setHeader("ETag","fixture-etag"); meta.addUserMetadata("upload-id",id);
        when(oss.metadata(anyString())).thenReturn(meta);
        Map<String,String> first=service.complete(student,id);
        assertEquals(first,service.complete(student,id));
        verify(oss,times(1)).promote(eq(grant.getStagingKey()),eq("ioedu/tenants/merchant-a/"+grant.getRelativePath()),eq("fixture-etag"),eq("video/mp4"));
        ArgumentCaptor<SubmissionAsset> asset=ArgumentCaptor.forClass(SubmissionAsset.class);
        verify(assets).saveAndFlush(asset.capture());
        assertEquals(7L,asset.getValue().getUserId()); assertEquals(first.get("url"),asset.getValue().getUrl());
        verify(files,times(1)).saveAndFlush(any());
        verify(oss,never()).open(anyString(),any(),any());
    }

    @Test void missingOssUserMetadataReturnsConflictInsteadOfServerError() {
        grant();
        ObjectMetadata meta=new ObjectMetadata(); meta.setContentLength(3);
        when(oss.metadata(anyString())).thenReturn(meta);
        BusinessException error=assertThrows(BusinessException.class,()->service.complete(student,id));
        assertEquals(409,error.getCode());
        verify(oss,never()).promote(anyString(),anyString(),anyString(),anyString());
    }

    @Test void unknownOwnerExpiredGrantAndMismatchedBytesCannotPublishAnAttachment() {
        assertEquals(404,assertThrows(BusinessException.class,()->service.complete(student,id)).getCode());
        DirectUpload grant=grant(); grant.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertEquals(410,assertThrows(BusinessException.class,()->service.complete(student,id)).getCode());
        grant.setExpiresAt(LocalDateTime.now().plusHours(1));
        ObjectMetadata meta=new ObjectMetadata(); meta.setContentLength(4); meta.addUserMetadata("upload-id",id);
        when(oss.metadata(anyString())).thenReturn(meta);
        assertEquals(409,assertThrows(BusinessException.class,()->service.complete(student,id)).getCode());
        meta.setContentLength(3); meta.addUserMetadata("upload-id","wrong-id");
        assertEquals(409,assertThrows(BusinessException.class,()->service.complete(student,id)).getCode());
        verify(oss,never()).promote(anyString(),anyString(),anyString(),anyString()); verifyNoInteractions(files,assets);
    }

    @Test void identicalUserIdsInAnotherTenantCannotConfirmOriginalGrant() {
        grant(); TenantContext.set("merchant-b");
        assertThrows(BusinessException.class,()->service.complete(new AuthUser(7L,"STUDENT","merchant-b"),id));
        verify(oss,never()).metadata(anyString()); verifyNoInteractions(files,assets);
    }

    @Test void browserUploadHostRejectsInternalAndUnrelatedEndpoints() {
        OssUploadPolicy policy=new OssUploadPolicy(config,new ObjectMapper());
        for(String endpoint:Arrays.asList("http://oss-cn-beijing.aliyuncs.com","https://oss-cn-beijing-internal.aliyuncs.com","https://example.com","https://oss-cn-beijing.aliyuncs.com/evil")) {
            config.getOss().setUploadEndpoint(endpoint);
            assertThrows(BusinessException.class,policy::host);
        }
    }

    @Test void postV4SignatureMatchesIndependentFixedTimeFixture() {
        Map<String,String> fields=new OssUploadPolicy(config,new ObjectMapper()).fields(
                "ioedu/staging/merchant-a/202609/example.mp4",123,"video/mp4","fixture",Instant.parse("2026-09-20T00:00:00Z"));
        assertEquals("6cfac0ee6fdc67c305608cc3b3a9095621583ba0bdc8071acae77dcb53156698",fields.get("x-oss-signature"));
    }
}

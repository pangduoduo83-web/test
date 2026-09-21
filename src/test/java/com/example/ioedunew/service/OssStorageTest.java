package com.example.ioedunew.service;

import com.aliyun.oss.model.ObjectMetadata;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.StoredFile;
import com.example.ioedunew.repository.StoredFileRepository;
import com.example.ioedunew.tenant.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OssStorageTest {
    @TempDir Path root;
    OssObjectStore oss = mock(OssObjectStore.class);
    StoredFileRepository files = mock(StoredFileRepository.class);
    StoredFileCatalog catalog = mock(StoredFileCatalog.class);
    TenantQuotaService quota = mock(TenantQuotaService.class);
    Map<String, StoredFile> records = new HashMap<>();
    Map<String, byte[]> objects = new HashMap<>();
    Map<String, ObjectMetadata> metadata = new HashMap<>();
    UploadStorage storage;

    @BeforeEach void setup() throws Exception {
        TenantContext.set("merchant-a");
        when(oss.enabled()).thenReturn(true); when(oss.bucket()).thenReturn("ioedu-test"); when(oss.prefix()).thenReturn("ioedu");
        when(files.findById(anyString())).thenAnswer(call -> Optional.ofNullable(records.get(TenantContext.require()+":"+call.getArgument(0))));
        when(files.findAll()).thenAnswer(call -> {
            List<StoredFile> result = new ArrayList<>(); records.forEach((k,v) -> { if(k.startsWith(TenantContext.require()+":")) result.add(v); }); return result;
        });
        doAnswer(call -> { StoredFile file = call.getArgument(0); records.put(TenantContext.require()+":"+file.getRelativePath(),file); return null; }).when(catalog).record(any());
        doAnswer(call -> {
            String key = call.getArgument(0); InputStream in = call.getArgument(1); ByteArrayOutputStream out = new ByteArrayOutputStream();
            UploadStorage.copy(in, out, call.getArgument(2)); objects.put(key, out.toByteArray());
            ObjectMetadata meta = new ObjectMetadata(); meta.setContentLength(out.size()); meta.setHeader("ETag","abc");
            if (call.getArgument(4) != null) meta.addUserMetadata("sha256",call.getArgument(4)); metadata.put(key,meta); return null;
        }).when(oss).put(anyString(),any(),anyLong(),anyString(),any());
        when(oss.metadata(anyString())).thenAnswer(call -> metadata.get(call.getArgument(0)));
        when(oss.open(anyString(),any(),any())).thenAnswer(call -> {
            byte[] all = objects.get(call.getArgument(0)); Long from = call.getArgument(1), to = call.getArgument(2);
            return new ByteArrayInputStream(from == null ? all : Arrays.copyOfRange(all,from.intValue(),to.intValue()+1));
        });
        TenantProperties props = new TenantProperties(); props.setDefaultCode("default");
        StorageQuotaLock lock = mock(StorageQuotaLock.class);
        when(lock.run(any())).thenAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(0)).get());
        storage = new UploadStorage(root.toString(),props,oss,files,catalog,quota,lock);
    }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void uploadStreamsToTenantKeyWithoutCreatingPermanentLocalFile() throws Exception {
        storage.save("202609/one.png",new ByteArrayInputStream(new byte[]{1,2,3}),3);
        assertArrayEquals(new byte[]{1,2,3},objects.get("ioedu/tenants/merchant-a/202609/one.png"));
        assertFalse(Files.exists(root.resolve("merchant-a/202609/one.png")));
        assertEquals(3,storage.remote("202609/one.png").getSizeBytes());
        verify(quota).checkStorageQuota(3);
        TenantContext.set("merchant-b"); assertFalse(storage.exists("202609/one.png"));
        storage.save("202609/one.png",new ByteArrayInputStream(new byte[]{9}),1);
        assertArrayEquals(new byte[]{9},objects.get("ioedu/tenants/merchant-b/202609/one.png"));
    }

    @Test void workingCopyIsRemovedAndOriginalCloudObjectSurvives() throws Exception {
        storage.save("202609/report.pdf",new ByteArrayInputStream(new byte[]{1,2,3}),3);
        Path temp;
        try(UploadStorage.WorkingFile copy = storage.materialize("202609/report.pdf",10)) { temp=copy.path(); assertTrue(temp.toString().endsWith(".pdf")); assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(temp)); }
        assertFalse(Files.exists(temp)); assertEquals(1,objects.size());
        assertThrows(BusinessException.class,()->storage.materialize("202609/report.pdf",2));
        objects.put("ioedu/tenants/merchant-a/202609/report.pdf",new byte[]{1});
        assertThrows(IOException.class,()->storage.materialize("202609/report.pdf",10));
    }

    @Test void legacyFallbackBelongsOnlyToDefaultMerchantAndPathsCannotEscape() throws Exception {
        Files.createDirectories(root.resolve("202609")); Files.write(root.resolve("202609/old.jpg"),new byte[]{1});
        assertFalse(storage.exists("202609/old.jpg"));
        TenantContext.set("default"); assertTrue(storage.exists("202609/old.jpg"));
        try(UploadStorage.WorkingFile copy=storage.materialize("202609/old.jpg",10)){ assertEquals(root.resolve("202609/old.jpg"),copy.path()); }
        assertTrue(Files.exists(root.resolve("202609/old.jpg")));
        for(String path:Arrays.asList("../secret.pdf","merchant-a/202609/one.pdf","202609/%2e%2e.pdf","202609/a/b.png"))
            assertThrows(BusinessException.class,()->storage.exists(path));
        TenantContext.clear(); assertThrows(IllegalStateException.class,()->storage.exists("202609/one.pdf"));
    }

    @Test void ossFailureDoesNotSilentlyStoreOnDiskAndCatalogFailureCompensates() throws Exception {
        doThrow(new BusinessException(502,"unavailable")).when(oss).put(anyString(),any(),anyLong(),anyString(),any());
        assertThrows(BusinessException.class,()->storage.save("202609/a.pdf",new ByteArrayInputStream(new byte[]{1}),1));
        assertTrue(records.isEmpty()); assertFalse(Files.exists(root.resolve("merchant-a")));
        doNothing().when(oss).put(anyString(),any(),anyLong(),anyString(),any());
        doThrow(new IllegalStateException("database down")).when(catalog).record(any());
        assertThrows(IllegalStateException.class,()->storage.save("202609/b.pdf",new ByteArrayInputStream(new byte[]{1}),1));
        verify(oss).delete("ioedu/tenants/merchant-a/202609/b.pdf");
    }

    @Test void migrationIsDryByDefaultResumableAndRetainsOriginals() throws Exception {
        Path file=root.resolve("merchant-a/202609/old.pdf"); Files.createDirectories(file.getParent()); Files.write(file,new byte[]{4,5,6});
        Map<String,Object> plan=storage.migrate(10,true); assertEquals(1,((List<?>)plan.get("files")).size()); assertTrue(objects.isEmpty());
        Map<String,Object> result=storage.migrate(10,false); assertEquals(0,result.get("pending")); assertTrue(Files.exists(file));
        assertEquals(0,((List<?>)storage.migrate(10,false).get("files")).size());
        assertArrayEquals(new byte[]{4,5,6},objects.get("ioedu/tenants/merchant-a/202609/old.pdf"));
    }

    @Test void remoteCatalogCannotPointToAnotherMerchantOrBucket() throws Exception {
        storage.save("202609/a.pdf",new ByteArrayInputStream(new byte[]{1}),1);
        records.get("merchant-a:202609/a.pdf").setObjectKey("ioedu/tenants/merchant-b/202609/a.pdf");
        assertThrows(BusinessException.class,()->storage.remote("202609/a.pdf"));
    }

    @Test void remoteVideoSupportsRangeSuffixHeadAndInvalidRange() throws Exception {
        storage.save("202609/a.mp4",new ByteArrayInputStream(new byte[]{0,1,2,3,4,5}),6);
        OssFileResponse service=new OssFileResponse(oss); String key=storage.objectKey("202609/a.mp4");
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/uploads/202609/a.mp4"); request.addHeader("Range","bytes=2-4");
        MockHttpServletResponse response=new MockHttpServletResponse();service.serve(key,"a.mp4",request,response);
        assertEquals(206,response.getStatus());assertEquals("bytes 2-4/6",response.getHeader("Content-Range"));assertArrayEquals(new byte[]{2,3,4},response.getContentAsByteArray());
        request=new MockHttpServletRequest("GET","/");request.addHeader("Range","bytes=-2");response=new MockHttpServletResponse();service.serve(key,"a.mp4",request,response);assertArrayEquals(new byte[]{4,5},response.getContentAsByteArray());
        request=new MockHttpServletRequest("HEAD","/");response=new MockHttpServletResponse();service.serve(key,"a.mp4",request,response);assertEquals(6,response.getContentLength());assertEquals(0,response.getContentAsByteArray().length);
        request=new MockHttpServletRequest("GET","/");request.addHeader("Range","bytes=99-");response=new MockHttpServletResponse();service.serve(key,"a.mp4",request,response);assertEquals(416,response.getStatus());assertEquals("bytes */6",response.getHeader("Content-Range"));
    }

    @Test void signedRedirectIsNotCachedAndNeverExposesAccessKeys() throws Exception {
        when(oss.browserUrl("key",false)).thenReturn("https://files.example.com/key?signature=temporary");
        MockHttpServletResponse response=new MockHttpServletResponse();new OssFileResponse(oss).serve("key","file.pdf",new MockHttpServletRequest("GET","/"),response);
        assertEquals(302,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));verify(oss,never()).open(anyString(),any(),any());
    }

    @Test void quotaIncludesCloudAndCountsRetainedMigrationCopyOnlyOnce() throws Exception {
        Tenant tenant=new Tenant();tenant.setCode("merchant-a");tenant.setDbName("ioedu_a");tenant.setStorageLimitMb(1);
        JdbcTemplate jdbc=mock(JdbcTemplate.class);TenantRegistry registry=mock(TenantRegistry.class);when(registry.findByCode("merchant-a")).thenReturn(Optional.of(tenant));
        Map<String,Object> row=new HashMap<>();row.put("relative_path","202609/old.pdf");row.put("size_bytes",600000L);
        when(jdbc.queryForList("SELECT relative_path, size_bytes FROM `ioedu_a`.stored_files")).thenReturn(Collections.singletonList(row));
        Path local=root.resolve("merchant-a/202609/old.pdf");Files.createDirectories(local.getParent());Files.write(local,new byte[600000]);
        TenantQuotaService service=new TenantQuotaService(jdbc,registry,new TenantProperties());ReflectionTestUtils.setField(service,"uploadDir",root.toString());
        assertDoesNotThrow(()->service.checkStorageQuota(400000));
        assertThrows(BusinessException.class,()->service.checkStorageQuota(500000));
    }

    @Test void officialSdkUsesPrivateObjectsAndSignsHttpsCnameWithoutPuttingSecretInUrl() throws Exception {
        StorageProperties props=new StorageProperties();props.setType("oss");
        props.getOss().setBucket("ioedu-test");props.getOss().setRegion("cn-hangzhou");props.getOss().setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        props.getOss().setPublicDomain("https://files.example.com");props.getOss().setAccessKeyId("fake-id");props.getOss().setAccessKeySecret("fake-secret-must-not-appear");
        OssObjectStore store=new OssObjectStore(props);
        try {
            String url=store.browserUrl("ioedu/tenants/a/202609/test.pdf",false);
            assertTrue(url.startsWith("https://files.example.com/ioedu/tenants/a/202609/test.pdf?"));
            assertTrue(url.contains("x-oss-signature"));assertFalse(url.contains("fake-secret-must-not-appear"));
            com.aliyun.oss.OSS sdk=mock(com.aliyun.oss.OSS.class);ReflectionTestUtils.setField(store,"client",sdk);
            store.put("file",new ByteArrayInputStream(new byte[]{1}),1,"image/png",null);
            org.mockito.ArgumentCaptor<ObjectMetadata> capture=org.mockito.ArgumentCaptor.forClass(ObjectMetadata.class);
            verify(sdk).putObject(eq("ioedu-test"),eq("file"),any(InputStream.class),capture.capture());
            assertEquals("private",capture.getValue().getRawMetadata().get("x-oss-object-acl"));
            assertEquals("image/png",capture.getValue().getContentType());
        } finally { store.close(); }
    }

    @Test void ossModeRequiresValidConfigurationAndDoesNotAllowInsecureDownloadDomain() {
        StorageProperties props=new StorageProperties();props.setType("oss");
        assertThrows(IllegalArgumentException.class,()->new OssObjectStore(props));
        props.getOss().setBucket("ioedu-test");props.getOss().setRegion("cn-hangzhou");props.getOss().setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        props.getOss().setPublicDomain("http://files.example.com");
        assertThrows(IllegalArgumentException.class,()->new OssObjectStore(props));
    }
}

package com.example.ioeduhub.service;

import com.example.ioeduhub.config.HubProperties;
import com.example.ioeduhub.entity.HubAsset;
import com.example.ioeduhub.repository.AssetRepo;
import com.aliyun.oss.model.ObjectMetadata;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetStorageTest {
    @TempDir Path root;
    @Test void newAssetUsesHubPrefixAndLeavesNoPermanentLocalCopy() throws Exception {
        AssetRepo repo=mock(AssetRepo.class);OssObjectStore oss=mock(OssObjectStore.class);
        when(oss.enabled()).thenReturn(true);when(oss.bucket()).thenReturn("bucket");when(oss.prefix()).thenReturn("ioedu");
        ObjectMetadata metadata=new ObjectMetadata();metadata.setContentLength(3);
        doAnswer(call->{metadata.addUserMetadata("sha256",call.getArgument(4));return null;}).when(oss).put(anyString(),any(),anyLong(),anyString(),any());
        when(oss.metadata(anyString())).thenReturn(metadata);when(repo.save(any())).thenAnswer(call->call.getArgument(0));
        HubProperties props=new HubProperties();props.setAssetDir(root.toString());AssetService service=new AssetService(repo,props,oss);
        HubAsset asset=service.store(new MockMultipartFile("file","demo.pdf","application/pdf",new byte[]{1,2,3}),2L);
        assertTrue(asset.getObjectKey().startsWith("ioedu/hub/"));assertEquals("bucket",asset.getBucket());
        try(java.util.stream.Stream<Path> paths=Files.walk(root)){assertEquals(1,paths.count());}
        when(repo.findBySha256(asset.getSha256())).thenReturn(Optional.of(asset));assertSame(asset,service.remote(asset.getSha256(),"pdf"));assertNull(service.remote(asset.getSha256(),"png"));
        assertSame(asset,service.store(new MockMultipartFile("file","demo.pdf","application/pdf",new byte[]{1,2,3}),2L));
        verify(oss,times(1)).put(anyString(),any(),eq(3L),eq("application/pdf"),anyString());
    }
    @Test void migrationDryRunDoesNotUploadOrRemoveLocalFiles() throws Exception {
        AssetRepo repo=mock(AssetRepo.class);OssObjectStore oss=mock(OssObjectStore.class);when(oss.enabled()).thenReturn(true);
        HubAsset asset=new HubAsset();asset.setSha256(String.join("",Collections.nCopies(64,"a")));asset.setExt("pdf");asset.setSize(3L);
        when(repo.findByObjectKeyIsNull(any())).thenReturn(new PageImpl<>(Collections.singletonList(asset)));
        HubProperties props=new HubProperties();props.setAssetDir(root.toString());AssetService service=new AssetService(repo,props,oss);
        assertEquals(1,((List<?>)service.migrate(true,10).get("files")).size());verify(oss,never()).put(anyString(),any(),anyLong(),anyString(),any());
    }
}

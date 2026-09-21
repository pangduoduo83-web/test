package com.example.ioedunew.store;

import com.example.ioedunew.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HubClientTest {
    private static final String ASSET = "/hub-assets/cover.png";
    private static final String ORIGIN = "http://hub:8081";
    private HubClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        StoreSettingsService settings = mock(StoreSettingsService.class);
        when(settings.baseUrl()).thenReturn(ORIGIN);
        client = new HubClient(settings, new ObjectMapper());
        RestTemplate template = (RestTemplate) ReflectionTestUtils.getField(client, "uploadTemplate");
        server = MockRestServiceServer.bindTo(template).build();
    }

    @Test
    void downloadsBytesAcrossHttpToHttpsWithoutReencodingSignatureOrSendingCredentials() {
        String cdn = "https://files.example.com/ioedu/hub/cover.png?Signature=test%2Bvalue%3D&auth_key=test";
        byte[] image = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };
        server.expect(requestTo(ORIGIN + ASSET))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create(cdn)));
        server.expect(requestTo(cdn)).andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(image, MediaType.IMAGE_PNG));

        assertArrayEquals(image, client.downloadAsset(ASSET));
        server.verify();
    }

    @Test
    void followsRelativeRedirects() {
        server.expect(requestTo(ORIGIN + ASSET))
                .andRespond(withStatus(HttpStatus.TEMPORARY_REDIRECT).location(URI.create("resolved.png")));
        server.expect(requestTo(ORIGIN + "/hub-assets/resolved.png"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_PNG));
        assertArrayEquals(new byte[]{1, 2, 3}, client.downloadAsset(ASSET));
        server.verify();
    }

    @Test
    void emptySuccessDoesNotBecomeACacheableBrokenImage() {
        server.expect(requestTo(ORIGIN + ASSET)).andRespond(withSuccess());
        assertEquals(502, assertThrows(BusinessException.class, () -> client.downloadAsset(ASSET)).getCode());
        server.verify();
    }

    @Test
    void stopsRedirectLoops() {
        for (int i = 0; i < 6; i++) {
            server.expect(requestTo(ORIGIN + ASSET))
                    .andRespond(withStatus(HttpStatus.FOUND).location(URI.create(ASSET)));
        }
        assertEquals(502, assertThrows(BusinessException.class, () -> client.downloadAsset(ASSET)).getCode());
        server.verify();
    }

    @Test
    void rejectsNonHttpRedirects() {
        server.expect(requestTo(ORIGIN + ASSET))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create("file:///private.png")));
        assertEquals(502, assertThrows(BusinessException.class, () -> client.downloadAsset(ASSET)).getCode());
        server.verify();
    }

    @Test
    void cdnFailureIsNotReturnedAsImageBytes() {
        String cdn = "https://files.example.com/cover.png?auth_key=private-test-value";
        server.expect(requestTo(ORIGIN + ASSET))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create(cdn)));
        server.expect(requestTo(cdn)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        BusinessException error = assertThrows(BusinessException.class, () -> client.downloadAsset(ASSET));
        assertEquals(502, error.getCode());
        assertFalse(error.getMessage().contains("private-test-value"));
        server.verify();
    }
}

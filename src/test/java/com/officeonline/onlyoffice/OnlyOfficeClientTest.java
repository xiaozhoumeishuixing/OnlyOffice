package com.officeonline.onlyoffice;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeonline.config.AppProperties;
import com.officeonline.document.DocumentInfo;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OnlyOfficeClientTest {

    private MockWebServer server;
    private OnlyOfficeClient client;
    private ObjectMapper objectMapper;
    private DocumentInfo info;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        AppProperties properties = new AppProperties();
        properties.getOnlyOffice().setUrl(server.url("/").toString());
        properties.setPublicUrl("http://app:8081");

        objectMapper = new ObjectMapper();
        JwtSupport jwtSupport = new JwtSupport(objectMapper, properties);
        client = new OnlyOfficeClient(properties, objectMapper, jwtSupport);

        info = new DocumentInfo();
        info.setId("doc-1");
        info.setFilename("a.docx");
        info.setUpdatedAt(Instant.parse("2026-08-13T00:00:00Z"));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void callsConvertServiceAndReturnsFileUrl() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"endConvert\":true,\"fileUrl\":\"" + server.url("/out.pdf") + "\"}"));

        String fileUrl = client.convert(info, "http://app:8081/api/documents/doc-1/content");

        assertThat(fileUrl).isEqualTo(server.url("/out.pdf").toString());
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/ConvertService.ashx");
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertThat(body.get("filetype").asText()).isEqualTo("docx");
        assertThat(body.get("outputtype").asText()).isEqualTo("pdf");
        assertThat(body.get("async").asBoolean()).isFalse();
        assertThat(body.get("url").asText()).isEqualTo("http://app:8081/api/documents/doc-1/content");
    }

    @Test
    void downloadsBinaryContent() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(new byte[]{37, 80, 68, 70, 1, 2})));

        byte[] content = client.download(server.url("/file.bin").toString());

        assertThat(content).containsExactly(37, 80, 68, 70, 1, 2);
    }

    @Test
    void parsesXmlConvertResponse() throws Exception {
        String xml = "<FileResult>"
                + "<FileUrl>http://onlyoffice-document-server/cache/out.pdf?md5=abc&amp;expires=123</FileUrl>"
                + "<FileType>pdf</FileType><Percent>100</Percent><EndConvert>True</EndConvert>"
                + "</FileResult>";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(xml));

        String fileUrl = client.convert(info, "http://app:8081/api/documents/doc-1/content");

        assertThat(fileUrl).isEqualTo("http://onlyoffice-document-server/cache/out.pdf?md5=abc&expires=123");
    }
}

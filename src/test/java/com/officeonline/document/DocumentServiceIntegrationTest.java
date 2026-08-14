package com.officeonline.document;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeonline.config.AppProperties;
import com.officeonline.onlyoffice.JwtSupport;
import com.officeonline.onlyoffice.OnlyOfficeClient;
import com.officeonline.onlyoffice.OnlyOfficeService;
import com.officeonline.storage.StorageService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private MockWebServer server;
    private AppProperties properties;
    private InMemoryDocumentMetadataStore metadataStore;
    private StorageService storageService;
    private DocumentService documentService;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        properties = new AppProperties();
        properties.setPublicUrl("http://app:8081");
        properties.getOnlyOffice().setUrl(server.url("/").toString());
        properties.getStorage().setRoot(tempDir.toString());

        metadataStore = new InMemoryDocumentMetadataStore();
        storageService = new StorageService(properties);
        ObjectMapper objectMapper = new ObjectMapper();
        JwtSupport jwtSupport = new JwtSupport(objectMapper, properties);
        OnlyOfficeService onlyOfficeService = new OnlyOfficeService(properties, metadataStore, jwtSupport);
        OnlyOfficeClient onlyOfficeClient = new OnlyOfficeClient(properties, objectMapper, jwtSupport);
        documentService = new DocumentService(metadataStore, storageService, onlyOfficeService, onlyOfficeClient, properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void uploadsListsConvertsAndDownloadsPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "报告.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "original".getBytes(StandardCharsets.UTF_8));

        DocumentResponse uploaded = documentService.upload(file);
        String id = uploaded.id();
        assertThat(uploaded.filename()).isEqualTo("报告.docx");
        assertThat(documentService.list()).hasSize(1);

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"endConvert\":true,\"fileUrl\":\"" + server.url("/result.pdf") + "\"}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(new Buffer().write(new byte[]{37, 80, 68, 70, 65, 66})));

        Map<String, Object> conversion = documentService.convertToPdf(id);

        assertThat(conversion.get("status")).isEqualTo("converted");
        assertThat(conversion.get("pdfUrl")).isEqualTo("http://app:8081/api/documents/" + id + "/pdf/download");
        Resource pdf = documentService.pdf(id);
        assertThat(pdf.getInputStream().readAllBytes()).containsExactly(37, 80, 68, 70, 65, 66);
        assertThat(documentService.list().get(0).pdfUrl()).isNotNull();
    }

    @Test
    void savesEditedContentFromCallbackAndChangesKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.docx", null, "old".getBytes(StandardCharsets.UTF_8));
        String id = documentService.upload(file).id();
        String key = id + "_123";

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(new Buffer().write("updated".getBytes(StandardCharsets.UTF_8))));

        documentService.saveFromCallback(key, server.url("/edited.docx").toString());

        DocumentInfo info = metadataStore.findById(id).orElseThrow();
        assertThat(Files.readString(storageService.contentPath(info), StandardCharsets.UTF_8)).isEqualTo("updated");
        assertThat(onlyOfficeKey(id)).startsWith(id + "_");
    }

    @Test
    void listIsEmptyAfterDelete() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.docx", null, "x".getBytes(StandardCharsets.UTF_8));
        String id = documentService.upload(file).id();

        documentService.delete(id);

        assertThat(documentService.list()).isEmpty();
    }

    private String onlyOfficeKey(String id) {
        DocumentInfo info = metadataStore.findById(id).orElseThrow();
        return info.getId() + "_" + info.getUpdatedAt().toEpochMilli();
    }
}

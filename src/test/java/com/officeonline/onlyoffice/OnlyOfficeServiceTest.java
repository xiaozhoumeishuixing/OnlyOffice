package com.officeonline.onlyoffice;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeonline.config.AppProperties;
import com.officeonline.document.DocumentInfo;
import com.officeonline.document.InMemoryDocumentMetadataStore;
import com.officeonline.exception.DocumentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnlyOfficeServiceTest {

    private AppProperties properties;
    private InMemoryDocumentMetadataStore metadataStore;
    private OnlyOfficeService onlyOfficeService;
    private JwtSupport jwtSupport;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.setPublicUrl("http://localhost:8081");
        metadataStore = new InMemoryDocumentMetadataStore();
        jwtSupport = new JwtSupport(new ObjectMapper(), properties);
        onlyOfficeService = new OnlyOfficeService(properties, metadataStore, jwtSupport);

        DocumentInfo info = new DocumentInfo();
        info.setId("doc-1");
        info.setFilename("合同.docx");
        info.setSize(10);
        info.setUploadedAt(Instant.parse("2026-08-13T00:00:00Z"));
        info.setUpdatedAt(Instant.parse("2026-08-13T00:00:00Z"));
        metadataStore.save(info);
    }

    @Test
    void buildsViewConfigWithStableKey() {
        Map<String, Object> config = onlyOfficeService.buildConfig("doc-1", "view");

        assertThat(config.get("documentType")).isEqualTo("word");
        assertThat(config.get("height")).isEqualTo("100%");

        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) config.get("document");
        assertThat(document.get("fileType")).isEqualTo("docx");
        assertThat(document.get("title")).isEqualTo("合同.docx");
        assertThat(document.get("url")).isEqualTo("http://localhost:8081/api/documents/doc-1/content");
        DocumentInfo info = metadataStore.findById("doc-1").orElseThrow();
        assertThat(document.get("key")).isEqualTo("doc-1_" + info.getUpdatedAt().toEpochMilli());

        @SuppressWarnings("unchecked")
        Map<String, Object> editorConfig = (Map<String, Object>) config.get("editorConfig");
        assertThat(editorConfig.get("mode")).isEqualTo("view");
        assertThat(editorConfig.get("callbackUrl")).isEqualTo("http://localhost:8081/api/onlyoffice/callback");
    }

    @Test
    void editModeUsesEditAndKeyChangesWhenDocumentIsUpdated() {
        Map<String, Object> editConfig = onlyOfficeService.buildConfig("doc-1", "edit");
        @SuppressWarnings("unchecked")
        Map<String, Object> editorConfig = (Map<String, Object>) editConfig.get("editorConfig");
        assertThat(editorConfig.get("mode")).isEqualTo("edit");

        DocumentInfo info = metadataStore.findById("doc-1").orElseThrow();
        info.setUpdatedAt(Instant.parse("2026-08-13T01:00:00Z"));
        metadataStore.save(info);

        Map<String, Object> updatedConfig = onlyOfficeService.buildConfig("doc-1", "view");
        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) updatedConfig.get("document");
        DocumentInfo updated = metadataStore.findById("doc-1").orElseThrow();
        assertThat(document.get("key")).isEqualTo("doc-1_" + updated.getUpdatedAt().toEpochMilli());
    }

    @Test
    void addsJwtTokenWhenSecretConfigured() {
        properties.getOnlyOffice().setJwtSecret("secret");

        Map<String, Object> config = onlyOfficeService.buildConfig("doc-1", "view");

        String token = (String) config.get("token");
        assertThat(token).isNotNull().hasLineCount(1);
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void throwsWhenDocumentIsMissing() {
        assertThatThrownBy(() -> onlyOfficeService.buildConfig("missing", "view"))
                .isInstanceOf(DocumentNotFoundException.class);
    }
}

package com.officeonline.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.officeonline.config.AppProperties;
import com.officeonline.document.DocumentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class StorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getStorage().setRoot(tempDir.toString());
        storageService = new StorageService(properties);
    }

    @Test
    void storesUploadedFileUnderDocumentDirectory() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "报告.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "hello".getBytes(StandardCharsets.UTF_8));

        StoredDocument stored = storageService.store(file);

        assertThat(stored.info().getFilename()).isEqualTo("报告.docx");
        assertThat(Files.readString(stored.path(), StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(stored.path().getParent().getFileName().toString()).isEqualTo(stored.info().getId());
    }

    @Test
    void replacesContentAtomically() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.docx", null, "old".getBytes(StandardCharsets.UTF_8));
        StoredDocument stored = storageService.store(file);
        DocumentInfo info = stored.info();

        storageService.replaceContent(info, "new".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.readString(storageService.contentPath(info), StandardCharsets.UTF_8)).isEqualTo("new");
    }

    @Test
    void savesPdfWithFixedNameAndDeletesDocumentDirectory() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.docx", null, "x".getBytes(StandardCharsets.UTF_8));
        StoredDocument stored = storageService.store(file);

        Path pdfPath = storageService.savePdf(stored.info(), new byte[]{37, 80, 68, 70});

        assertThat(pdfPath.getFileName().toString()).isEqualTo("converted.pdf");
        assertThat(Files.readAllBytes(pdfPath)).containsExactly(37, 80, 68, 70);

        storageService.deleteDocument(stored.info().getId());
        assertThat(pdfPath).doesNotExist();
        assertThat(storageService.contentPath(stored.info())).doesNotExist();
    }
}

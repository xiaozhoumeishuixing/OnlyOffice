package com.officeonline.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.officeonline.config.AppProperties;
import com.officeonline.exception.DocumentNotFoundException;
import com.officeonline.exception.InvalidFileException;
import com.officeonline.exception.UnsupportedFileTypeException;
import com.officeonline.onlyoffice.OnlyOfficeClient;
import com.officeonline.onlyoffice.OnlyOfficeService;
import com.officeonline.storage.StoredDocument;
import com.officeonline.storage.StorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "docx", "doc", "xlsx", "xls", "pptx", "ppt",
            "odt", "ods", "odp", "rtf", "txt", "csv");

    private final DocumentMetadataStore metadataStore;
    private final StorageService storageService;
    private final OnlyOfficeService onlyOfficeService;
    private final OnlyOfficeClient onlyOfficeClient;
    private final AppProperties properties;

    public DocumentService(DocumentMetadataStore metadataStore,
                           StorageService storageService,
                           OnlyOfficeService onlyOfficeService,
                           OnlyOfficeClient onlyOfficeClient,
                           AppProperties properties) {
        this.metadataStore = metadataStore;
        this.storageService = storageService;
        this.onlyOfficeService = onlyOfficeService;
        this.onlyOfficeClient = onlyOfficeClient;
        this.properties = properties;
    }

    public DocumentResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file is empty");
        }
        String filename = file.getOriginalFilename();
        String extension = onlyOfficeService.extension(filename == null ? "" : filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedFileTypeException(extension);
        }
        StoredDocument stored = storageService.store(file);
        DocumentInfo info = stored.info();
        metadataStore.save(info);
        return DocumentResponse.from(info, properties.getPublicUrl());
    }

    public List<DocumentResponse> list() {
        return metadataStore.findAll().stream()
                .map(info -> DocumentResponse.from(info, properties.getPublicUrl()))
                .toList();
    }

    public Resource content(String id) {
        DocumentInfo info = require(id);
        Path path = storageService.contentPath(info);
        if (!Files.isRegularFile(path)) {
            throw new DocumentNotFoundException(id);
        }
        return new FileSystemResource(path);
    }

    public Resource pdf(String id) {
        DocumentInfo info = require(id);
        Path path = storageService.pdfPath(info);
        if (!Files.isRegularFile(path)) {
            throw new DocumentNotFoundException(id);
        }
        return new FileSystemResource(path);
    }

    public Map<String, Object> previewConfig(String id) {
        return onlyOfficeService.buildConfig(id, "view");
    }

    public Map<String, Object> editConfig(String id) {
        return onlyOfficeService.buildConfig(id, "edit");
    }

    public Map<String, Object> convertToPdf(String id) {
        DocumentInfo info = require(id);
        String fileUrl = onlyOfficeClient.convert(info, onlyOfficeService.contentUrl(info));
        byte[] pdfBytes = onlyOfficeClient.download(fileUrl);
        storageService.savePdf(info, pdfBytes);

        info.setPdfFilename("converted.pdf");
        info.setConvertedAt(Instant.now());
        metadataStore.save(info);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "converted");
        result.put("pdfUrl", baseUrl() + "/api/documents/" + id + "/pdf/download");
        result.put("size", pdfBytes.length);
        result.put("convertedAt", info.getConvertedAt().toString());
        return result;
    }

    public void saveFromCallback(String key, String url) {
        String id = key.split("_", 2)[0];
        DocumentInfo info = require(id);
        byte[] content = onlyOfficeClient.download(url);
        storageService.replaceContent(info, content);
        info.setSize(content.length);
        info.setUpdatedAt(Instant.now());
        metadataStore.save(info);
    }

    public void delete(String id) {
        DocumentInfo info = require(id);
        metadataStore.delete(id);
        storageService.deleteDocument(id);
    }

    private DocumentInfo require(String id) {
        return metadataStore.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private String baseUrl() {
        String base = properties.getPublicUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}

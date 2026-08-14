package com.officeonline.onlyoffice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.officeonline.config.AppProperties;
import com.officeonline.document.DocumentInfo;
import com.officeonline.document.DocumentMetadataStore;
import com.officeonline.exception.DocumentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OnlyOfficeService {

    private final AppProperties properties;
    private final DocumentMetadataStore metadataStore;
    private final JwtSupport jwtSupport;

    public OnlyOfficeService(AppProperties properties, DocumentMetadataStore metadataStore, JwtSupport jwtSupport) {
        this.properties = properties;
        this.metadataStore = metadataStore;
        this.jwtSupport = jwtSupport;
    }

    public Map<String, Object> buildConfig(String id, String mode) {
        DocumentInfo info = requireDocument(id);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", documentType(info.getFilename()));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", extension(info.getFilename()));
        document.put("key", documentKey(info));
        document.put("title", info.getFilename());
        document.put("url", contentUrl(info));
        config.put("document", document);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", mode);
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("callbackUrl", callbackUrl());
        editorConfig.put("user", Map.of("id", "office-online-user", "name", "Guest"));
        editorConfig.put("customization", Map.of(
                "autosave", true,
                "chat", false,
                "compactHeader", true,
                "feedback", false,
                "forcesave", false));
        config.put("editorConfig", editorConfig);
        config.put("height", "100%");
        config.put("width", "100%");

        if (jwtSupport.enabled()) {
            config.put("token", jwtSupport.sign(config));
        }
        return config;
    }

    public String contentUrl(DocumentInfo info) {
        return baseUrl() + "/api/documents/" + info.getId() + "/content";
    }

    public String callbackUrl() {
        if (StringUtils.hasText(properties.getCallbackUrl())) {
            return trimTrailingSlash(properties.getCallbackUrl());
        }
        return baseUrl() + "/api/onlyoffice/callback";
    }

    public String documentKey(DocumentInfo info) {
        return info.getId() + "_" + info.getUpdatedAt().toEpochMilli();
    }

    public String extension(String filename) {
        String name = filename == null ? "" : filename;
        int index = name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) {
            return "docx";
        }
        return name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public String documentType(String filename) {
        String extension = extension(filename);
        if (List.of("xls", "xlsx", "ods", "csv").contains(extension)) {
            return "cell";
        }
        if (List.of("ppt", "pptx", "odp").contains(extension)) {
            return "slide";
        }
        return "word";
    }

    private DocumentInfo requireDocument(String id) {
        return metadataStore.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private String baseUrl() {
        return trimTrailingSlash(properties.getPublicUrl());
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

package com.officeonline.document;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onlyoffice")
public class OnlyOfficeCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeCallbackController.class);

    private final DocumentService documentService;

    public OnlyOfficeCallbackController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/callback")
    public Map<String, Integer> callback(@RequestBody JsonNode body) {
        int status = body.path("status").asInt(-1);
        if (status == 2 || status == 6) {
            String key = body.path("key").asText(null);
            String url = body.path("url").asText(null);
            if (key == null || url == null || url.isBlank()) {
                log.warn("OnlyOffice callback missing key or url: {}", body);
                return Map.of("error", 1);
            }
            try {
                documentService.saveFromCallback(key, url);
                return Map.of("error", 0);
            } catch (Exception e) {
                log.error("Failed to save document from OnlyOffice callback", e);
                return Map.of("error", 1);
            }
        }
        return Map.of("error", 0);
    }
}

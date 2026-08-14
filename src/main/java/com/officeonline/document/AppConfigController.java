package com.officeonline.document;

import java.util.Map;

import com.officeonline.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AppConfigController {

    private final AppProperties properties;

    public AppConfigController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/config")
    public Map<String, String> config() {
        String publicUrl = properties.getOnlyOffice().getPublicUrl();
        if (publicUrl == null || publicUrl.isBlank()) {
            publicUrl = properties.getOnlyOffice().getUrl();
        }
        return Map.of(
                "onlyofficeUrl", trimTrailingSlash(publicUrl),
                "publicUrl", trimTrailingSlash(properties.getPublicUrl()));
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

package com.officeonline.onlyoffice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeonline.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtSupport {

    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public JwtSupport(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public boolean enabled() {
        return StringUtils.hasText(properties.getOnlyOffice().getJwtSecret());
    }

    public String sign(Object payload) {
        try {
            String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String body = base64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = header + "." + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getOnlyOffice().getJwtSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            String signature = base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign OnlyOffice payload", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

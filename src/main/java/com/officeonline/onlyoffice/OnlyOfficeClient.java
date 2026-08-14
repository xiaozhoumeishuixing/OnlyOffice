package com.officeonline.onlyoffice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeonline.config.AppProperties;
import com.officeonline.document.DocumentInfo;
import com.officeonline.exception.ConversionException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.springframework.stereotype.Component;

@Component
public class OnlyOfficeClient {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final JwtSupport jwtSupport;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public OnlyOfficeClient(AppProperties properties, ObjectMapper objectMapper, JwtSupport jwtSupport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jwtSupport = jwtSupport;
    }

    public String convert(DocumentInfo info, String contentUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", contentUrl);
        payload.put("async", false);
        payload.put("filetype", extension(info.getFilename()));
        payload.put("outputtype", "pdf");
        payload.put("key", keyForConvert(info));
        payload.put("title", info.getFilename());

        String responseBody = postJson(convertUrl(), payload);
        return parseFileUrl(responseBody);
    }

    private String parseFileUrl(String responseBody) {
        try {
            String trimmed = responseBody.trim();
            if (trimmed.startsWith("<")) {
                return parseXmlFileUrl(responseBody);
            }
            JsonNode root = objectMapper.readTree(trimmed);
            return readFileUrl(root.path("endConvert").asBoolean(), root.path("fileUrl").asText(null), responseBody);
        } catch (ConversionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConversionException(
                    "Failed to parse OnlyOffice conversion response: " + responseBody, e);
        }
    }

    private String parseXmlFileUrl(String responseBody) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)));

        NodeList endConvertNodes = document.getElementsByTagName("EndConvert");
        boolean endConvert = endConvertNodes.getLength() > 0
                && "true".equalsIgnoreCase(endConvertNodes.item(0).getTextContent().trim());
        NodeList fileUrlNodes = document.getElementsByTagName("FileUrl");
        String fileUrl = fileUrlNodes.getLength() > 0
                ? fileUrlNodes.item(0).getTextContent().trim()
                : null;
        return readFileUrl(endConvert, fileUrl, responseBody);
    }

    private String readFileUrl(boolean endConvert, String fileUrl, String responseBody) {
        if (!endConvert) {
            throw new ConversionException("OnlyOffice conversion is not finished: " + responseBody);
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new ConversionException("OnlyOffice conversion response has no fileUrl: " + responseBody);
        }
        return fileUrl;
    }

    public byte[] download(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept-Encoding", "identity")
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new ConversionException("Download failed, HTTP " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (IOException e) {
            throw new ConversionException("Failed to download " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversionException("Interrupted while downloading " + url, e);
        }
    }

    private String postJson(String url, Map<String, Object> payload) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept-Encoding", "identity")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8));
            if (jwtSupport.enabled()) {
                builder.header("Authorization", "Bearer " + jwtSupport.sign(payload));
            }
            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new ConversionException(
                        "OnlyOffice convert failed, HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new ConversionException("Failed to call OnlyOffice convert service at " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversionException("Interrupted while calling OnlyOffice convert service", e);
        }
    }

    private String convertUrl() {
        String base = properties.getOnlyOffice().getUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/ConvertService.ashx";
    }

    private String extension(String filename) {
        String name = filename == null ? "" : filename;
        int index = name.lastIndexOf('.');
        return index < 0 || index == name.length() - 1 ? "docx" : name.substring(index + 1);
    }

    private String keyForConvert(DocumentInfo info) {
        return info.getId() + "_" + info.getUpdatedAt().toEpochMilli();
    }
}

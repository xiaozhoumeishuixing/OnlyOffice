package com.officeonline.document;

import java.time.Instant;

public record DocumentResponse(
        String id,
        String filename,
        String contentType,
        long size,
        Instant uploadedAt,
        Instant updatedAt,
        String contentUrl,
        String previewUrl,
        String editUrl,
        String pdfUrl) {

    public static DocumentResponse from(DocumentInfo info, String publicUrl) {
        String base = trimTrailingSlash(publicUrl) + "/api/documents/" + info.getId();
        return new DocumentResponse(
                info.getId(),
                info.getFilename(),
                info.getContentType(),
                info.getSize(),
                info.getUploadedAt(),
                info.getUpdatedAt(),
                base + "/content",
                base + "/preview",
                base + "/edit",
                info.getPdfFilename() == null ? null : base + "/pdf/download");
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

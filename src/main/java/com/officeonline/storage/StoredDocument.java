package com.officeonline.storage;

import java.nio.file.Path;

import com.officeonline.document.DocumentInfo;

public record StoredDocument(DocumentInfo info, Path path) {
}

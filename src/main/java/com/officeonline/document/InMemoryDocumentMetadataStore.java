package com.officeonline.document;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryDocumentMetadataStore implements DocumentMetadataStore {

    private final Map<String, DocumentInfo> documents = new ConcurrentHashMap<>();

    @Override
    public Optional<DocumentInfo> findById(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    @Override
    public List<DocumentInfo> findAll() {
        return documents.values().stream()
                .sorted(Comparator.comparing(DocumentInfo::getUploadedAt).reversed())
                .toList();
    }

    @Override
    public DocumentInfo save(DocumentInfo document) {
        documents.put(document.getId(), document);
        return document;
    }

    @Override
    public boolean delete(String id) {
        return documents.remove(id) != null;
    }
}

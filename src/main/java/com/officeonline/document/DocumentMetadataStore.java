package com.officeonline.document;

import java.util.List;
import java.util.Optional;

/**
 * Metadata persistence abstraction. The default implementation is in-memory;
 * swap this interface with a database-backed implementation when needed.
 */
public interface DocumentMetadataStore {

    Optional<DocumentInfo> findById(String id);

    List<DocumentInfo> findAll();

    DocumentInfo save(DocumentInfo document);

    boolean delete(String id);
}

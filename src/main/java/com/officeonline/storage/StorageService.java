package com.officeonline.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import com.officeonline.config.AppProperties;
import com.officeonline.document.DocumentInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {

    private static final String PDF_FILENAME = "converted.pdf";

    private final AppProperties properties;

    public StorageService(AppProperties properties) {
        this.properties = properties;
    }

    public StoredDocument store(MultipartFile file) {
        try {
            String id = UUID.randomUUID().toString();
            String filename = sanitizeFilename(file.getOriginalFilename());
            Path directory = documentDirectory(id);
            Files.createDirectories(directory);
            Path target = directory.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            Instant now = Instant.now();
            DocumentInfo info = new DocumentInfo();
            info.setId(id);
            info.setFilename(filename);
            info.setContentType(file.getContentType());
            info.setSize(Files.size(target));
            info.setUploadedAt(now);
            info.setUpdatedAt(now);
            return new StoredDocument(info, target);
        } catch (IOException e) {
            throw new StorageException("Failed to store uploaded file", e);
        }
    }

    public Path contentPath(DocumentInfo info) {
        return documentDirectory(info.getId()).resolve(info.getFilename());
    }

    public Path pdfPath(DocumentInfo info) {
        return documentDirectory(info.getId()).resolve(PDF_FILENAME);
    }

    public boolean pdfExists(DocumentInfo info) {
        return Files.isRegularFile(pdfPath(info));
    }

    public void replaceContent(DocumentInfo info, byte[] content) {
        try {
            Path target = contentPath(info);
            Path temp = target.resolveSibling("." + target.getFileName() + ".tmp");
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to replace document content", e);
        }
    }

    public Path savePdf(DocumentInfo info, byte[] content) {
        try {
            Path target = pdfPath(info);
            Files.write(target, content);
            return target;
        } catch (IOException e) {
            throw new StorageException("Failed to save converted PDF", e);
        }
    }

    public void deleteDocument(String id) {
        try {
            Path directory = documentDirectory(id);
            if (!Files.exists(directory)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            throw new StorageException("Failed to delete document directory", e);
        }
    }

    private Path documentDirectory(String id) {
        return Path.of(properties.getStorage().getRoot()).toAbsolutePath().normalize().resolve(id);
    }

    private String sanitizeFilename(String original) {
        String name = original == null ? "" : original;
        name = Path.of(name).getFileName().toString();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            name = "document";
        }
        return name;
    }
}

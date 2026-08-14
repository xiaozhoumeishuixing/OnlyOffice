package com.officeonline.document;

import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file) {
        return documentService.upload(file);
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.list();
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable String id) {
        Resource resource = documentService.content(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/preview")
    public Map<String, Object> preview(@PathVariable String id) {
        return documentService.previewConfig(id);
    }

    @GetMapping("/{id}/edit")
    public Map<String, Object> edit(@PathVariable String id) {
        return documentService.editConfig(id);
    }

    @PostMapping("/{id}/convert/pdf")
    public Map<String, Object> convertToPdf(@PathVariable String id) {
        return documentService.convertToPdf(id);
    }

    @GetMapping("/{id}/pdf/download")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String id) {
        Resource resource = documentService.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

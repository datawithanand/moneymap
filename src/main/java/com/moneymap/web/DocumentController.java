package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.model.expense.Document;
import com.moneymap.service.DocumentStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Central document library API (standalone module). JSON-only, no views yet. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentStorageService documentStorageService;

    public DocumentController(DocumentStorageService documentStorageService) {
        this.documentStorageService = documentStorageService;
    }

    private User user(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    /** Own documents, optionally filtered by type and/or linked module. */
    @GetMapping
    public List<Document> list(@RequestParam(required = false) String docType,
                               @RequestParam(required = false) String linkedModulePath,
                               HttpServletRequest request) {
        return filter(documentStorageService.listOwn(user(request).getId()), docType, linkedModulePath);
    }

    /** A specific family member's documents (subject to the existing permission matrix). */
    @GetMapping("/family/{memberOwnerId}")
    public List<Document> listForFamilyMember(@PathVariable String memberOwnerId,
                                              @RequestParam(required = false) String docType,
                                              @RequestParam(required = false) String linkedModulePath,
                                              HttpServletRequest request) {
        return filter(documentStorageService.listForFamilyMember(user(request).getId(), memberOwnerId),
                docType, linkedModulePath);
    }

    private List<Document> filter(List<Document> docs, String docType, String linkedModulePath) {
        return docs.stream()
                .filter(d -> docType == null || docType.equals(d.getDocType()))
                .filter(d -> linkedModulePath == null || linkedModulePath.equals(d.getLinkedModulePath()))
                .toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Document upload(@RequestParam MultipartFile file,
                           @RequestParam String docType,
                           @RequestParam(required = false) String linkedModulePath,
                           @RequestParam(required = false) String linkedRecordId,
                           @RequestParam(required = false) String familyMemberTag,
                           HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "No file uploaded");
        }
        return documentStorageService.upload(user(request), file, docType, linkedModulePath, linkedRecordId, familyMemberTag);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id, HttpServletRequest request) throws IOException {
        Document doc = documentStorageService.find(id, user(request).getId());
        InputStream in = documentStorageService.openForDownload(doc, user(request).getId());
        String fileName = doc.getOriginalFileName() != null ? doc.getOriginalFileName() : doc.getStoredFileName();
        return ResponseEntity.ok()
                .contentType(doc.getContentType() != null ? MediaType.parseMediaType(doc.getContentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(new InputStreamResource(in));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, HttpServletRequest request) {
        Document doc = documentStorageService.find(id, user(request).getId());
        if (!doc.getOwnerId().equals(user(request).getId())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Only the owner can delete a document");
        }
        documentStorageService.delete(doc);
    }
}

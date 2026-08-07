package com.moneymap.service;

import com.moneymap.model.FamilyPermission;
import com.moneymap.model.User;
import com.moneymap.model.expense.Document;
import com.moneymap.repository.Db;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Central document library storage. Files live on disk under {data-dir}/documents/{ownerId}/,
 * named by a random UUID (never the original file name) — the Document record in the JSON
 * store is the only thing that maps a UUID back to a real name. Family visibility reuses the
 * existing PermissionService levels; no new permission system.
 */
@Service
public class DocumentStorageService {

    private final Db db;
    private final PermissionService permissionService;
    private final Path documentsRoot;

    public DocumentStorageService(Db db, PermissionService permissionService,
                                  @Value("${moneymap.data-dir}") String dataDir) {
        this.db = db;
        this.permissionService = permissionService;
        this.documentsRoot = Path.of(dataDir, "documents");
    }

    public Document upload(User owner, MultipartFile file, String docType, String linkedModulePath,
                           String linkedRecordId, String familyMemberTag) {
        try {
            Path ownerDir = documentsRoot.resolve(owner.getId());
            Files.createDirectories(ownerDir);
            String storedName = UUID.randomUUID() + fileExtension(file.getOriginalFilename());
            Path target = ownerDir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            Document doc = new Document();
            doc.setOwnerId(owner.getId());
            doc.setDocType(docType);
            doc.setOriginalFileName(file.getOriginalFilename());
            doc.setStoredFileName(storedName);
            doc.setContentType(file.getContentType());
            doc.setFileSizeBytes(file.getSize());
            doc.setLinkedModulePath(linkedModulePath);
            doc.setLinkedRecordId(linkedRecordId);
            if (familyMemberTag != null && !familyMemberTag.isBlank()) doc.setFamilyMemberTag(familyMemberTag);
            return db.documents.save(doc);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store file", e);
        }
    }

    /** The requesting user's own documents, always visible. */
    public List<Document> listOwn(String ownerId) {
        return db.documents.findWhere(d -> ownerId.equals(d.getOwnerId())).stream()
                .sorted(Comparator.comparing(Document::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** A family member's documents, gated by the existing permission matrix (CONTACTS_ONLY or higher to list). */
    public List<Document> listForFamilyMember(String viewerId, String memberOwnerId) {
        FamilyPermission.Level level = permissionService.effectiveLevel(memberOwnerId, viewerId);
        if (!permissionService.atLeast(level, FamilyPermission.Level.CONTACTS_ONLY)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permission to view documents");
        }
        return listOwn(memberOwnerId);
    }

    public Document find(String id, String viewerId) {
        Document doc = db.documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!viewerId.equals(doc.getOwnerId())) {
            FamilyPermission.Level level = permissionService.effectiveLevel(doc.getOwnerId(), viewerId);
            if (!permissionService.atLeast(level, FamilyPermission.Level.CONTACTS_ONLY)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }
        return doc;
    }

    /** Downloading the actual bytes requires FULL_ACCESS when viewing someone else's document. */
    public InputStream openForDownload(Document doc, String viewerId) throws IOException {
        if (!viewerId.equals(doc.getOwnerId())) {
            FamilyPermission.Level level = permissionService.effectiveLevel(doc.getOwnerId(), viewerId);
            if (!permissionService.atLeast(level, FamilyPermission.Level.FULL_ACCESS)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Full access required to download");
            }
        }
        Path path = documentsRoot.resolve(doc.getOwnerId()).resolve(doc.getStoredFileName());
        if (!Files.exists(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File missing on disk");
        return Files.newInputStream(path);
    }

    public void delete(Document doc) {
        try {
            Path path = documentsRoot.resolve(doc.getOwnerId()).resolve(doc.getStoredFileName());
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // record deletion still proceeds even if the on-disk file was already gone
        }
        db.documents.deleteById(doc.getId());
    }

    private static String fileExtension(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        return dot >= 0 ? originalName.substring(dot) : "";
    }
}

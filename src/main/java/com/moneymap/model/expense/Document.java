package com.moneymap.model.expense;

import com.moneymap.model.asset.OwnedRecord;

/**
 * Central document library. One record per uploaded file; the file itself lives on disk
 * under the configured documents directory (never in the JSON store).
 */
public class Document extends OwnedRecord {

    /** INSURANCE, BANK_STATEMENT, BROKER_STATEMENT, PROPERTY_DEED, LOAN_AGREEMENT, OTHER
     *  (or any free-text value for a family-defined type). */
    private String docType;
    private String originalFileName;
    /** UUID-based on-disk file name; never derived from user input. */
    private String storedFileName;
    private String contentType;
    private long fileSizeBytes;
    /** Optional link to the specific record this document belongs to, e.g. a term-insurance policy. */
    private String linkedModulePath;
    private String linkedRecordId;

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getLinkedModulePath() { return linkedModulePath; }
    public void setLinkedModulePath(String linkedModulePath) { this.linkedModulePath = linkedModulePath; }
    public String getLinkedRecordId() { return linkedRecordId; }
    public void setLinkedRecordId(String linkedRecordId) { this.linkedRecordId = linkedRecordId; }
}

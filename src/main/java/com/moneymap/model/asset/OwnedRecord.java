package com.moneymap.model.asset;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Common fields present on every financial record (Section 00 §6.1 / Section 17 §0).
 * The contact fields (nominee, customer care, portal, branch) exist specifically so
 * Contacts Only family access is useful in an emergency without exposing balances.
 */
public abstract class OwnedRecord {
    private String id;
    private String ownerId;
    private String familyMemberTag = "Self";
    private String nominee;
    private String customerCareNumber;
    private String customerCareEmail;
    private String portalUrl;
    private String branchAddress;
    private List<String> labels = new ArrayList<>();   // free-form user tags, independent of familyMemberTag
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getFamilyMemberTag() { return familyMemberTag; }
    public void setFamilyMemberTag(String familyMemberTag) { this.familyMemberTag = familyMemberTag; }
    public String getNominee() { return nominee; }
    public void setNominee(String nominee) { this.nominee = nominee; }
    public String getCustomerCareNumber() { return customerCareNumber; }
    public void setCustomerCareNumber(String customerCareNumber) { this.customerCareNumber = customerCareNumber; }
    public String getCustomerCareEmail() { return customerCareEmail; }
    public void setCustomerCareEmail(String customerCareEmail) { this.customerCareEmail = customerCareEmail; }
    public String getPortalUrl() { return portalUrl; }
    public void setPortalUrl(String portalUrl) { this.portalUrl = portalUrl; }
    public String getBranchAddress() { return branchAddress; }
    public void setBranchAddress(String branchAddress) { this.branchAddress = branchAddress; }
    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels == null ? new ArrayList<>() : labels; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

package com.moneymap.model.asset;


/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class PfAccount extends OwnedRecord {
    private String uan;

    public String getUan() { return uan; }
    public void setUan(String uan) { this.uan = uan; }
}

package com.moneymap.module;

/** The two classification schemes over the same money (Section 12 — must not be conflated). */
public final class Buckets {
    private Buckets() {}

    /** Grouping Scheme 1 — module-based 3-bucket breakdown + liabilities. */
    public enum Bucket { CASH, RETIREMENT, INVESTMENTS, LIABILITY, NONE }

    /** Grouping Scheme 2 — asset-class re-slicing for Portfolio Allocation. */
    public enum AllocationClass { EQUITY, DEBT, GOLD, REAL_ESTATE, CASH, ALTERNATIVE }
}

package com.moneymap.module;

import com.moneymap.model.User;
import com.moneymap.model.asset.OwnedRecord;
import com.moneymap.repository.EntityRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** Metadata for one asset module: schema fields, valuation, bucket, allocation split. */
public class ModuleDef<T extends OwnedRecord> {

    public final String path;              // URL segment, e.g. "savings-accounts"
    public final String displayName;
    public final String prdSection;
    public final EntityRepository<T> repo;
    public final Class<T> type;
    public final Buckets.Bucket bucket;
    public final List<FieldSpec> fields;
    public final List<String> listColumns; // property or computed-key names
    /** Value in owner's base currency (asset value, or amount owed for LIABILITY). Null fn = no net worth effect. */
    public final BiFunction<T, User, BigDecimal> value;
    /** Allocation split of the record's value across classes (Scheme 2). Null = excluded from allocation. */
    public final BiFunction<T, User, Map<Buckets.AllocationClass, BigDecimal>> allocation;
    /** Extra computed columns for the list view: key → function. */
    public final Map<String, BiFunction<T, User, Object>> computed;
    /** False for modules with custom controllers (Flexi RD, PF, Salary). */
    public final boolean genericCrud;

    public ModuleDef(String path, String displayName, String prdSection,
                     EntityRepository<T> repo, Class<T> type, Buckets.Bucket bucket,
                     List<FieldSpec> fields, List<String> listColumns,
                     BiFunction<T, User, BigDecimal> value,
                     BiFunction<T, User, Map<Buckets.AllocationClass, BigDecimal>> allocation,
                     Map<String, BiFunction<T, User, Object>> computed,
                     boolean genericCrud) {
        this.path = path;
        this.displayName = displayName;
        this.prdSection = prdSection;
        this.repo = repo;
        this.type = type;
        this.bucket = bucket;
        this.fields = fields;
        this.listColumns = listColumns;
        this.value = value;
        this.allocation = allocation;
        this.computed = computed;
        this.genericCrud = genericCrud;
    }

    @SuppressWarnings("unchecked")
    public BigDecimal valueOf(Object record, User owner) {
        return value == null ? null : value.apply((T) record, owner);
    }

    @SuppressWarnings("unchecked")
    public Map<Buckets.AllocationClass, BigDecimal> allocationOf(Object record, User owner) {
        return allocation == null ? null : allocation.apply((T) record, owner);
    }
}

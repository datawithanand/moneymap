package com.moneymap.repository.json;

import com.moneymap.model.GlobalSettings;
import com.moneymap.repository.GlobalSettingsRepository;
import com.moneymap.repository.JsonCollectionStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.function.UnaryOperator;

@Repository
public class JsonGlobalSettingsRepository implements GlobalSettingsRepository {

    private static final String COLLECTION = "global_settings";

    private final JsonCollectionStore store;

    public JsonGlobalSettingsRepository(JsonCollectionStore store) {
        this.store = store;
    }

    @Override
    public GlobalSettings get() {
        return store.readSingleton(COLLECTION, GlobalSettings.class, GlobalSettings::new);
    }

    @Override
    public GlobalSettings update(UnaryOperator<GlobalSettings> mutation) {
        return store.mutateSingleton(COLLECTION, GlobalSettings.class, GlobalSettings::new, current -> {
            GlobalSettings updated = mutation.apply(current);
            updated.setUpdatedAt(Instant.now());
            return updated;
        });
    }
}

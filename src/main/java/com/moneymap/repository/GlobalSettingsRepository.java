package com.moneymap.repository;

import com.moneymap.model.GlobalSettings;

import java.util.function.UnaryOperator;

/** Singleton collection — exactly one object in global_settings.json (Section 17 §12.4). */
public interface GlobalSettingsRepository {

    GlobalSettings get();

    GlobalSettings update(UnaryOperator<GlobalSettings> mutation);
}

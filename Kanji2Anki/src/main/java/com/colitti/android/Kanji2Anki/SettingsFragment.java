/*
 * Copyright 2013 Lorenzo Colitti.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2.
 */
package com.colitti.android.Kanji2Anki;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREF_IMPORT_FILE = "import_file";
    private static final String PREF_EXPORT_FILE = "export_file";
    private static final String PREF_EXPORT_DECK = "export_deck";

    private EditTextPreference mImportFile;
    private EditTextPreference mExportFile;
    private EditTextPreference mExportDeck;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        mImportFile = (EditTextPreference) findPreference(PREF_IMPORT_FILE);
        mExportFile = (EditTextPreference) findPreference(PREF_EXPORT_FILE);
        mExportDeck = (EditTextPreference) findPreference(PREF_EXPORT_DECK);

        mImportFile.setSummary(checkNull(mImportFile.getText()));
        mExportFile.setSummary(checkNull(mExportFile.getText()));
        mExportDeck.setSummary(checkNull(mExportDeck.getText()));
    }

    private String checkNull(String s) {
        if (s != null) {
            return s;
        } else {
            return "";
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setSummary(checkNull(sharedPreferences.getString(key, "")));
        }
    }
}

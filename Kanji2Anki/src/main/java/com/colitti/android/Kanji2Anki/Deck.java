package com.colitti.android.Kanji2Anki;

import android.util.Log;

import org.json.JSONObject;

public class Deck {
    private static final String TAG = "Deck";

    private String mID;
    private String mName;
    private String mModelID;

    public String getID() {
        return mID;
    }

    public String getName() {
        return mName;
    }

    public Deck(String id, JSONObject json) {
        mID = id;
        mName = json.optString("name");
        mModelID = json.optString("mid");
        Log.i(TAG, "Found deck id='" + mID + "', name='" + mName + "'");
    }
}

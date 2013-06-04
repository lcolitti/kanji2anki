/*
 * Copyright 2013 Lorenzo Colitti.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2.
 */
package com.colitti.android.kanji2anki;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * AnkiDroid card syncer
 *
 * Based mostly on observing decks created by AnkiDroid.
 *
 * Tables:
 *   - col: one record, contains a JSON(?) string describing configuration
 *     - decks: JSON string containing information about decks
 *  - cards: one record per card, contain card information and stats (what deck(s) each card
 *      belongs to, when it's due, etc.
 *  - notes: one record per card, contains card contents
 */
public class AnkiDroidSyncer {
    private static final String TAG = "AnkiDroidSyncer";
    private static final String MAGIC_DATE = "946684800";  // Jan 1, 2000.

    private static final String TABLE_COL = "col";
    private static final String COLUMN_DECKS = "decks";
    private static final String COLUMN_MODELS = "models";

    private static final String TABLE_NOTES = "notes";
    private static final String COLUMN_ID = "id";          // Equal to COLUMN_NOTE_ID in card table.
    private static final String COLUMN_GUID = "guid";      // Random string?
    private static final String COLUMN_MODEL_ID = "mid";   // Note type. See models field in col
    private static final String COLUMN_TIMESTAMP = "mod";  // Last modified.
    private static final String COLUMN_USN = "usn";        // Always -1?
    private static final String COLUMN_TAGS = "tags";      // Always empty?
    private static final String COLUMN_BACK = "flds";      // "fields?"
    private static final String COLUMN_FRONT = "sfld";     // "search field"?
    private static final String COLUMN_CSUM = "csum";      // Checksum? How is this calculated?
    private static final String COLUMN_FLAGS = "flags";    // Always 0?
    private static final String COLUMN_DATA = "data";      // Always empty?

    private static final String TABLE_CARDS = "cards";
    private static final String COLUMN_NOTE_ID = "nid";
    private static final String COLUMN_DECK_ID = "did";
    private static final String COLUMN_ORD = "ord";
    private static final String COLUMN_DUE = "due";

    private static final String GUID_CHARS =
            "!#$%&()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`" +
            "abcdefghijklmnopqrstuvwxyz{|}~".toCharArray();

    private String mFilename;
    private SQLiteDatabase mDB;

    public String getFilename() {
        return mFilename;
    }

    public void setFilename(String filename) {
        mFilename = filename;
        if (mDB != null) {
            mDB.close();
        }
        mDB = SQLiteDatabase.openDatabase(mFilename, null, 0);
    }

    public AnkiDroidSyncer() {}

    /**
     * Fetches the specified configuration setting as a JSON object.
     * @param column the name of the setting. Corresponds to the column name in the col table.
     * @return the specified configuration setting.
     * @throws JSONException if the setting cannot be parsed as a JSON object.
     */
    private JSONObject getConfKey(String column) throws JSONException {
        final String[] columns = {column};
        Cursor cursor = mDB.query(TABLE_COL, columns, null, null, null, null, "1");
        cursor.moveToFirst();
        String json = cursor.getString(0);
        return new JSONObject(json);
    }

    /**
     * Returns the decks defined in the collection.
     * @return a Map of deck names to Deck objects.
     */
    public Map<String,Deck> getDecks() throws JSONException {
        Hashtable<String,Deck> decks = new Hashtable<String,Deck>();
        JSONObject conf = getConfKey(COLUMN_DECKS);
        Iterator i = conf.keys();
        while(i.hasNext()) {
            String id = (String) i.next();
            JSONObject deckConf = conf.getJSONObject(id);
            Deck deck = new Deck(id, deckConf);
            decks.put(deck.getName(), deck);
        }
        return decks;
    }

    /**
     * Finds the lowest model ID configured in the specified collection.
     * From looking at the JSON configs, model IDs can be associated with decks, but do not have to
     * be. So we just return the lowest model ID we find, as long as it has a type of 0 (basic
     * cards?) and only one template. Note that this code only supports integer models and will
     * fail to find any models that have non-integer model IDs.
     * @throws JSONException: the JSON could not be parsed.
     * @return the model ID.
     */
    public String getDefaultModelID() throws JSONException {
        Hashtable<String,String> models = new Hashtable<String, String>();
        JSONObject conf = getConfKey(COLUMN_MODELS);
        Iterator i = conf.keys();
        long lowestValue = 0;
        String lowestID = null;
        while(i.hasNext()) {
            String id = (String) i.next();
            long value;
            try {
                value = Long.parseLong(id);
            } catch (NumberFormatException e) {
               continue;
            }
            // Skip non-basic models and models with more than one template.
            JSONObject deck = conf.getJSONObject(id);
            if (deck.getJSONArray("tmpls").length() > 1 ||
                deck.getInt("type") != 0) {
                continue;
            }
            if (lowestID == null || value < lowestValue) {
                lowestValue = value;
                lowestID = id;
            }
        }
        String name = conf.getJSONObject(lowestID).getString("name");
        // Log.i(TAG, "Found lowest model: id='" + lowestID + "', name='" + name + "'");
        return lowestID;
    }

    /**
     * Returns the cards currently in the specified deck.
     * @param deckId the deck to examine.
     * @return A map mapping card fronts to card objects.
     */
    public Map<String,Card> getCards(String deckId) {
        final String[] columns = {COLUMN_ID, COLUMN_FRONT};
        Map <String,Card> cards = new Hashtable<String,Card>();
        String[] selection = {deckId};
        // Example: "select cards.nid, notes.sfld, notes.flds from notes join cards on cards.nid =
        // id where cards.did = <deckid>;"
        String sql =
                "select " + TABLE_CARDS + "." + COLUMN_NOTE_ID +
                ", " + TABLE_NOTES + "." + COLUMN_FRONT +
                ", " + TABLE_NOTES + "." + COLUMN_BACK +
                " from " + TABLE_NOTES + " join " + TABLE_CARDS +
                " on " + TABLE_CARDS + "." + COLUMN_NOTE_ID + "=" + TABLE_NOTES + "." + COLUMN_ID +
                " where " + TABLE_CARDS + "." + COLUMN_DECK_ID + " = ?;";
        Log.i(TAG, "query='" + sql + "'");

        Cursor cursor = mDB.rawQuery(sql, selection);
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            String id = cursor.getString(0);
            String front = cursor.getString(1);
            String back = cursor.getString(2);
            Card card = new Card(id, front, back);
            cards.put(front, card);
        }
        return cards;
    }

    /**
     * Checks if the deck already has a certain card.
     * @param deckId
     * @param front
     * @return true if the deck contains a note with the specified front.
     */
    public boolean hasCard(String deckId, String front) {
        return getCards(deckId).containsValue(front);
    }

    /**
     * Calculates the due column for a newly-added card.
     * For cards that have been shown at least once, the due column appears to be the timestamp
     * at which it should be shown again. For newly-added cards, it appears to be sequential.
     * We attempt to find the highest sequence number on the basis of the assumption that numbers
     * >= MAGIC_DATE (January 1 2000) are timestamps and numbers smaller than MAGIC_DATE are
     * sequence numbers.
     * @param deckId the deck for which to find the next due value
     * @return the value of the due column.
     */
    public long findNextDue(String deckId) {
        final String[] args = {};
        String sql =
                "select max(" + COLUMN_DUE + ") + 1" +
                " from " + TABLE_CARDS +
                " where " + COLUMN_DECK_ID + " = " + deckId +
                " and " + COLUMN_DUE + " < " + MAGIC_DATE +";";

        Cursor cursor = mDB.rawQuery(sql, args);
        if (!cursor.moveToFirst())
            return 1;

        return cursor.getLong(0);
    }

    /**
     * Returns a guid for a new card.
     * guids appear to be 5 (rarely 4 or 3) of the follwing characters:
     * !#$%&()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~
     * Since we don't know how this works, we just return a random string of these,
     * 5 characters long. Hopefully the guid doesn't have any meaning and only needs to be unique.
     * @return a guid string.
     */
    public String generateGuid() {
        char[] guid = new char[5];
        for (int i = 0; i < 5; i++) {
            Random random = new Random();
            int r = random.nextInt(GUID_CHARS.length());
            guid[i] = GUID_CHARS.charAt(r);
        }
        return new String(guid);
    }


    /**
     * Adds a note and a corresponding card, with the specified model ID, to the specified deck.
     *
     * First create the note. Example:
     *
     * "1368853342612|d29hL|1368691978999|1368853350|-1||南ナ ナン みなみ south|南|3870058491|0|
     *   - id: current time in millis. unique field, will prevent dups
     *   - guid: globally unique ID?
     *   - mid: model ID of the card, from models in col table.
     *   - mod: timestamp in seconds
     *   - usn: -1 ?
     *   - tags: empty
     *   - flds: back of card
     *   - sfld: front of card
     *   - csum: we don't know how to calculate this, so we set it to zero. AnkiDroid doesn't care?
     *   - flags: 0
     *   - data: empty
     *
     *  Then we add the card to the cards table. Here we mostly set everything to zero except card
     *  ID, note ID (the ID of the note we just created), the timestamp, and the due time.
     *
     * @param deck the deck to add the card to.
     * @param modelID the ID of the model (card type) to use.
     * @param front the front of the card.
     * @param back the back of the card.
     * @return true if the card was added successfully, false otherwise.
     */
    public boolean addCard(Deck deck, String modelID, String front, String back) {
        long due = findNextDue(deck.getID());

        long millis = System.currentTimeMillis();
        String noteID = Long.toString(millis);
        String noteTimestamp = Long.toString(millis / 1000);

        // Get a 64-bit random number.
        long id = UUID.randomUUID().getLeastSignificantBits();
        String guid = Long.toHexString(id);

        ContentValues noteValues = new ContentValues();
        noteValues.put(COLUMN_ID, noteID);
        noteValues.put(COLUMN_GUID, generateGuid());
        noteValues.put(COLUMN_MODEL_ID, modelID);
        noteValues.put(COLUMN_TIMESTAMP, noteTimestamp);
        noteValues.put(COLUMN_USN, "-1");
        noteValues.put(COLUMN_TAGS, "");
        noteValues.put(COLUMN_BACK, back);
        noteValues.put(COLUMN_FRONT, front);
        noteValues.put(COLUMN_CSUM, "0");   // AnkiDroid doesn't seem to check this.
        noteValues.put(COLUMN_FLAGS, "0");
        noteValues.put(COLUMN_DATA, "");

        millis = System.currentTimeMillis();
        String cardID = Long.toString(millis);
        String cardTimestamp = Long.toString(millis / 1000);

        ContentValues cardValues = new ContentValues();
        cardValues.put(COLUMN_ID, cardID);
        cardValues.put(COLUMN_NOTE_ID, noteID);
        cardValues.put(COLUMN_DECK_ID, deck.getID());
        cardValues.put(COLUMN_ORD, 0);
        cardValues.put(COLUMN_TIMESTAMP, cardTimestamp);
        cardValues.put(COLUMN_USN, "-1");
        cardValues.put("type", 0);
        cardValues.put("queue", 0);
        cardValues.put(COLUMN_DUE, Long.toString(due));
        cardValues.put("ivl", 0);
        cardValues.put("factor", 0);
        cardValues.put("reps", 0);
        cardValues.put("lapses", 0);
        cardValues.put("left", 0);
        cardValues.put("odue", 0);
        cardValues.put("odid", 0);
        cardValues.put(COLUMN_FLAGS, 0);
        cardValues.put(COLUMN_DATA, "");

        // Start a transaction, so in case inserting into the notes table doesn't work, we don't
        // insert into the cards table either.
        boolean success = false;
        mDB.beginTransaction();
        try {
            mDB.insert(TABLE_NOTES, null, noteValues);
            mDB.insert(TABLE_CARDS, null, cardValues);
            mDB.setTransactionSuccessful();
            success = true;
        } finally {
            mDB.endTransaction();
        }

        return success;
    }

    public boolean addCard(Deck deck, String modelID, Card card) {
        return addCard(deck, modelID, card.getFront(), card.getBack());
    }
}
/*
 * Copyright 2013 Lorenzo Colitti.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2.
 */
package com.colitti.android.Kanji2Anki;

import android.util.Log;

import java.util.ArrayList;
import java.lang.Character.UnicodeBlock;


public class Kanji {
    private static final String TAG = "Kanji";

    private static final int FIELD_UNKNOWN = 0;
    private static final int FIELD_KANJI = 1;
    private static final int FIELD_READINGS = 2;
    private static final int FIELD_MEANING = 3;
    private static final int FIELD_TIMESTAMP = 4;
    private static final int FIELD_MAX = 5;

    private String mUnknown;
    private String mKanji;
    private String mMeaning;
    private String mTimestamp;

    private String mOnReadings;
    private String mKunReadings;
    private String mUnknownReadings;


    public String getKanji() {
        return mKanji;
    }

    public String getOnyomi() {
        return mOnReadings;
    }

    public String getKunyomi() {
        return mKunReadings;
    }

    public String getUnknownReadings() {
        return mUnknownReadings;
    }

    public String getMeaning() {
        return mMeaning;
    }

    private boolean firstLetterInUnicodeBlock(String word, UnicodeBlock block) {
        // Skip any leading symbols.
        int i = 0;
        int len = word.length();
        while (i < len) {
            final int codePoint = word.codePointAt(i);
            if (Character.isLetter(codePoint)) {
                break;
            }
            i += Character.charCount(codePoint);
        }
        return i < len && Character.UnicodeBlock.of(word.codePointAt(i)) == block;
    }

    private boolean isHiragana(String word) {
        return firstLetterInUnicodeBlock(word, UnicodeBlock.HIRAGANA);
    }

    private boolean isKatakana(String word) {
        return firstLetterInUnicodeBlock(word, UnicodeBlock.KATAKANA);
    }

    private void appendWord(String word, StringBuilder s) {
        if (s.length() > 0) {
            s.append(" ");
        }
        s.append(word);
    }

    private void parseReadings(String readings) {
        // Log.i(TAG, "'" + mKanji + "': parsing readings: '" + readings + "'");
        String[] words = readings.split(" ");
        StringBuilder onReadings = new StringBuilder();
        StringBuilder kunReadings = new StringBuilder();
        StringBuilder unknownReadings = new StringBuilder();

        int length = words.length;
        for (int i = 0; i < length; i++) {
            String word = words[i];
            if (isKatakana(word)) {
                appendWord(word, onReadings);
            } else if (isHiragana(word)) {
                appendWord(word, kunReadings);
            } else {
                // ???
                Log.w(TAG, "Unknown reading '" + word + "' for kanji '" + mKanji + "'");
                appendWord(word, unknownReadings);
            }
        }
        mOnReadings = onReadings.toString();
        mKunReadings = kunReadings.toString();
        mUnknownReadings = unknownReadings.toString();

        // Log.i("Kanji", "on='" + mOnReadings + "', kun='" + mKunReadings + "', " +
        //       "?='" + mUnknownReadings + "'");
    }

    private String getReadings() {
        return mOnReadings + "\n" + mKunReadings + " " + mUnknownReadings;
    }

    public Kanji(ArrayList<String> fields) {
        if (fields.size() != FIELD_MAX) {
            throw new ArrayIndexOutOfBoundsException(
                    "Invalid number of fields: " + fields.size() + ", expected " + FIELD_MAX);
        }

        mUnknown = fields.get(FIELD_UNKNOWN);
        mKanji = fields.get(FIELD_KANJI);
        parseReadings(fields.get(FIELD_READINGS));
        mMeaning = fields.get(FIELD_MEANING);
        mTimestamp = fields.get(FIELD_TIMESTAMP);
    }

    public String toString() {
        return "Kanji(" + mUnknown + ", " + mKanji +  ", " +
                mOnReadings + ", " + mKunReadings + ", " + mUnknownReadings +
                ", " + mMeaning + ", " + mTimestamp + ")";
    }
}

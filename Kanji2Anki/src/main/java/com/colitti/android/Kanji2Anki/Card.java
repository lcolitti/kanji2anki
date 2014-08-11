/*
 * Copyright 2013 Lorenzo Colitti.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2.
 */
package com.colitti.android.Kanji2Anki;

public class Card {
    public static final char FIELD_SEPARATOR = (char) 0x1f;

    public String mID;
    public String mFront;
    public String mBack;

    public Card(String id, String front, String back) {
        mID = id;
        mFront = front;
        mBack = back;
    }

    public Card(Kanji k) {
        mID = null;
        mFront = k.getKanji();
        mBack = k.getKanji() + FIELD_SEPARATOR +
                k.getOnyomi() + "<br>" + k.getKunyomi() + " " + k.getUnknownReadings() +
                "<br>" + k.getMeaning();
    }

    public String getFront() {
        return mFront;
    }

    public String getBack() {
        return mBack;
    }
}
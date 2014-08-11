/*
 * Copyright 2013 Lorenzo Colitti.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2.
 */
package com.colitti.android.Kanji2Anki;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class KanjiRecognizerImporter {
    private static final String TAG = "KanjiRecognizerImporter";

    private static final String PATH = "/data/org.nick.kanjirecognizer/files/kr-favorites-bkp.csv";

    private String mFilename;

    public static String getDefaultPath() {
        return PATH;
    }

    public KanjiRecognizerImporter(String filename) {
        this.mFilename = filename;
    }

    public KanjiRecognizerImporter() {
        this(PATH);
    }

    public String getFilename() {
        return mFilename;
    }

    public void setFilename(String filename) {
        mFilename = filename;
    }

    private static ArrayList<String> lineToFieldList(String line) {
        ArrayList fields = new ArrayList<String>();
        boolean inQuotes = false;
        String currentField = "";
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField);
                currentField = "";
            } else {
                currentField += c;
            }
        }
        if (!inQuotes) {
            fields.add(currentField);
        }
        return fields;
    }

    public boolean fileLooksValid() {
        try {
            BufferedReader r = new BufferedReader(new FileReader(new File(mFilename)));
            String line = r.readLine();
            ArrayList<String> fields = lineToFieldList(line);
            Kanji k = new Kanji(fields);
        } catch (IOException e) {
            return false;
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
        return true;
    }

    public List<Kanji> readFile() throws IOException {
        LinkedList<Kanji> kanjiList = new LinkedList<Kanji>();
        BufferedReader r = new BufferedReader(new FileReader(new File(mFilename)));
        String line = r.readLine();
        while (line != null) {
            ArrayList<String> fields = lineToFieldList(line);
            Kanji k = new Kanji(fields);
            kanjiList.add(k);
            line = r.readLine();
        }

        return kanjiList;
    }

}

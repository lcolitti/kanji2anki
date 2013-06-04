/*
 * Copyright 2013 Lorenzo Colitti.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2.
 */
package com.colitti.android.kanji2anki;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import android.util.Log;
import android.widget.TextView;
import android.app.AlertDialog;

import org.json.JSONException;

public class MainActivity extends Activity {

    public static final String TAG = "kanji2anki";

    private KanjiRecognizerImporter mImporter;
    private AnkiDroidSyncer mSyncer;
    Map<String,Deck> mDecks;
    private List<Kanji> mKanjiList;

    // For the progress meter.
    private String mCurrentKanji;
    private int mCurrentProgress;
    private Runnable mProgressUpdater = new Runnable() {
        public void run() {
            mCurrentKanjiText.setText(mCurrentKanji);
            mProgressCurrentText.setText(Integer.toString(mCurrentProgress));
        }
    };

    // For the UI.
    private Button mStartButton;
    private TextView mCurrentKanjiText;
    private TextView mProgressCurrentText;
    private TextView mProgressSlashText;
    private TextView mProgressMaxText;
    private boolean mStopped;

    private void runSync() {
        mImporter = new KanjiRecognizerImporter();
        mSyncer = new AnkiDroidSyncer();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        String importFile = settings.getString("import_file", "");
        Log.i(TAG, "Setting input file to: " + importFile);
        mImporter.setFilename(importFile);
        if (!mImporter.fileLooksValid()) {
            notifyError("Error reading input file '" + importFile + "'");
            return;
        }

        String exportFile = settings.getString("export_file", "");
        try {
            Log.i(TAG, "Setting export file to: " + exportFile);
            mSyncer.setFilename(exportFile);
        } catch(SQLException e) {
            notifyError("Can't open database " + exportFile + "'");
            return;
        }

        String deckName = settings.getString("export_deck", "");
        Log.i(TAG, "Setting output deck to: " + deckName);

        try {
            mDecks = mSyncer.getDecks();
        } catch(JSONException e) {
            notifyError("Can't find any decks in file '" + exportFile + "'");
            return;
        }

        Deck deck = mDecks.get(deckName);
        if (deck == null) {
            notifyError("Can't find deck '" + deckName + "' in file '" + exportFile + "'");
            return;
        }

        String deckId = deck.getID();

        // Get the lowest model ID.
        String modelID;
        try {
            modelID = mSyncer.getDefaultModelID();
        } catch(JSONException e) {
            notifyError("Can't determine card type.");
            return;
        }

        // Read the export file.
        try {
            mKanjiList = mImporter.readFile();
        } catch(Exception e) {
            notifyError("Error reading " + mImporter.getFilename());
            return;
        }

        runOnUiThread(new Runnable() {
            public void run() {
                initProgress();
            }
        });

        // Add the cards.
        Map <String,Card> cards = mSyncer.getCards(deckId);
        mCurrentProgress = 0;
        for (int i = mKanjiList.size() - 1; i >= 0; i--) {
            Kanji kanji = mKanjiList.get(i);
            if (mStopped)
                return;
            try {
                Thread.sleep(0, 500000); // Yield to the UI thread, even though we're low priority.
            } catch(InterruptedException e) {}

            String kanjiStr = kanji.getKanji();
            mCurrentKanji = kanjiStr;
            mCurrentProgress++;
            if (!cards.containsKey(kanjiStr)) {
                Card card = new Card(kanji);
                mSyncer.addCard(deck, modelID, card);
            }

            runOnUiThread(mProgressUpdater);
        }

        runOnUiThread(new Runnable() {
            public void run() {
                onSyncDone();
            }
        });
    }

    protected void onPause() {
        super.onPause();
        mStopped = true;
    }

    private void notifyError(String message) {
        final Activity activity = this;
        final String msg = message;
        Log.e(TAG, msg);
        runOnUiThread(new Runnable() {
            public void run() {
                onSyncDone();
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setMessage(msg).setNeutralButton(
                        getString(R.string.error_ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    private void onSyncDone() {
        mStartButton.setEnabled(true);
    }

    public void onSyncButtonClicked(View v) {
        mStartButton.setEnabled(false);
        Thread workerThread = new Thread(new Runnable() {
            public void run() {
                runSync();
            }
        });
        workerThread.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCurrentKanjiText = (TextView) findViewById(R.id.current_kanji);

        mStartButton = (Button) findViewById(R.id.sync_button);
        mProgressCurrentText = (TextView) findViewById(R.id.progress_current);
        mProgressSlashText = (TextView) findViewById(R.id.progress_slash);
        mProgressMaxText = (TextView) findViewById(R.id.progress_max);

        mStopped = false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public void openSettings(MenuItem item) {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivity(i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void initProgress() {
        mProgressCurrentText.setText("0");
        mProgressMaxText.setText(Integer.toString(mKanjiList.size()));

        mCurrentKanjiText.setVisibility(View.VISIBLE);
        mProgressCurrentText.setVisibility(View.VISIBLE);
        mProgressSlashText.setVisibility(View.VISIBLE);
        mProgressMaxText.setVisibility(View.VISIBLE);
    }

}
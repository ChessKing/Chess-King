package org.petero.droidfish.activities;

import java.io.File;
import java.util.Vector;

import org.petero.droidfish.R;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class LoadScid extends ListActivity {
    private static final class GameInfo {
        String summary = "";
        int gameId = -1;
        public String toString() {
            return summary;
        }
    }

    private static Vector<GameInfo> gamesInFile = new Vector<GameInfo>();
    private static boolean cacheValid = false;
    private String fileName;
    private ProgressDialog progress;

    private SharedPreferences settings;
    private int defaultItem = 0;
    private String lastFileName = "";
    private long lastModTime = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (savedInstanceState != null) {
            defaultItem = savedInstanceState.getInt("defaultScidItem");
            lastFileName = savedInstanceState.getString("lastScidFileName");
            if (lastFileName == null) lastFileName = "";
            lastModTime = savedInstanceState.getLong("lastScidModTime");
        } else {
            defaultItem = settings.getInt("defaultScidItem", 0);
            lastFileName = settings.getString("lastScidFileName", "");
            lastModTime = settings.getLong("lastScidModTime", 0);
        }

        Intent i = getIntent();
        String action = i.getAction();
        fileName = i.getStringExtra("org.petero.droidfish.pathname");
        if (action.equals("org.petero.droidfish.loadScid")) {
            showDialog(PROGRESS_DIALOG);
            final LoadScid lpgn = this;
            new Thread(new Runnable() {
                public void run() {
                    readFile();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            lpgn.showList();
                        }
                    });
                }
            }).start();
        } else if (action.equals("org.petero.droidfish.loadScidNextGame") ||
                   action.equals("org.petero.droidfish.loadScidPrevGame")) {
            boolean next = action.equals("org.petero.droidfish.loadScidNextGame");
            final int loadItem = defaultItem + (next ? 1 : -1);
            if (loadItem < 0) {
                Toast.makeText(getApplicationContext(), R.string.no_prev_game,
                        Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            } else {
                new Thread(new Runnable() {
                    public void run() {
                        readFile();
                        runOnUiThread(new Runnable() {
                            public void run() {
                                if (loadItem >= gamesInFile.size()) {
                                    Toast.makeText(getApplicationContext(), R.string.no_next_game,
                                                   Toast.LENGTH_SHORT).show();
                                    setResult(RESULT_CANCELED);
                                    finish();
                                } else {
                                    defaultItem = loadItem;
                                    sendBackResult(gamesInFile.get(loadItem));
                                }
                            }
                        });
                    }
                }).start();
            }
        } else { // Unsupported action
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("defaultScidItem", defaultItem);
        outState.putString("lastScidFileName", lastFileName);
        outState.putLong("lastScidModTime", lastModTime);
    }

    @Override
    protected void onPause() {
        Editor editor = settings.edit();
        editor.putInt("defaultScidItem", defaultItem);
        editor.putString("lastScidFileName", lastFileName);
        editor.putLong("lastScidModTime", lastModTime);
        editor.commit();
        super.onPause();
    }

    private final void showList() {
        progress.dismiss();
        final ArrayAdapter<GameInfo> aa = new ArrayAdapter<GameInfo>(this, R.layout.select_game_list_item, gamesInFile);
        setListAdapter(aa);
        ListView lv = getListView();
        lv.setSelectionFromTop(defaultItem, 0);
        lv.setFastScrollEnabled(true);
        lv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                defaultItem = pos;
                sendBackResult(aa.getItem(pos));
            }
        });
    }

    final static int PROGRESS_DIALOG = 0;

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case PROGRESS_DIALOG:
            progress = new ProgressDialog(this);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setTitle(R.string.reading_scid_file);
            progress.setMessage(getString(R.string.please_wait));
            progress.setCancelable(false);
            return progress;
        default:
            return null;
        }
    }

    private final void readFile() {
        if (!fileName.equals(lastFileName))
            defaultItem = 0;
        long modTime = new File(fileName).lastModified();
        if (cacheValid && (modTime == lastModTime) && fileName.equals(lastFileName))
            return;
        lastModTime = modTime;
        lastFileName = fileName;

        gamesInFile.clear();
        Cursor cursor = getListCursor();
        if (cursor != null) {
            int noGames = cursor.getCount();
            gamesInFile.ensureCapacity(noGames);
            int percent = -1;
            if (cursor.moveToFirst()) {
                addGameInfo(cursor);
                int gameNo = 1;
                while (cursor.moveToNext()) {
                    addGameInfo(cursor);
                    gameNo++;
                    final int newPercent = (int)(gameNo * 100 / noGames);
                    if (newPercent > percent) {
                        percent = newPercent;
                        if (progress != null) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    progress.setProgress(newPercent);
                                }
                            });
                        }
                    }
                }
            }
        }
        cacheValid = true;
    }

    private int idIdx;
    private int summaryIdx;

    private Cursor getListCursor() {
        String scidFileName = fileName.substring(0, fileName.indexOf("."));
        String[] proj = new String[]{"_id", "summary"};
        Cursor cursor = managedQuery(Uri.parse("content://org.scid.database.scidprovider/games"),
                                     proj, scidFileName, null, null);
        idIdx = cursor.getColumnIndex("_id");
        summaryIdx = cursor.getColumnIndex("summary");
        return cursor;
    }

    private Cursor getOneGameCursor(int gameId) {
        String scidFileName = fileName.substring(0, fileName.indexOf("."));
        String[] proj = new String[]{"pgn"};
        String uri = String.format("content://org.scid.database.scidprovider/games/%d", gameId);
        Cursor cursor = managedQuery(Uri.parse(uri),
                                     proj, scidFileName, null, null);
        return cursor;
    }

    private void addGameInfo(Cursor cursor) {
        GameInfo gi = new GameInfo();
        gi.gameId = cursor.getInt(idIdx);
        gi.summary = cursor.getString(summaryIdx);
        gamesInFile.add(gi);
    }

    private final void sendBackResult(GameInfo gi) {
        if (gi.gameId >= 0) {
            Cursor cursor = getOneGameCursor(gi.gameId);
            if (cursor != null && cursor.moveToFirst()) {
                String pgn = cursor.getString(cursor.getColumnIndex("pgn"));
                if (pgn != null && pgn.length() > 0) {
                    setResult(RESULT_OK, (new Intent()).setAction(pgn));
                    finish();
                    return;
                }
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }
}

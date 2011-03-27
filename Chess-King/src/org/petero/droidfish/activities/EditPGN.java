package org.petero.droidfish.activities;

import java.io.File;
import java.util.ArrayList;

import org.petero.droidfish.R;
import org.petero.droidfish.activities.PGNFile.GameInfo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

public class EditPGN extends ListActivity {
	static ArrayList<GameInfo> gamesInFile = new ArrayList<GameInfo>();
	static boolean cacheValid = false;
	PGNFile pgnFile;
	ProgressDialog progress;
	GameInfo selectedGi = null;
	ArrayAdapter<GameInfo> aa = null;
	EditText filterText = null;

	SharedPreferences settings;
	int defaultItem = 0;
	String lastSearchString = "";
	String lastFileName = "";
	long lastModTime = -1;

	boolean loadGame; // True when loading game, false when saving
	String pgnToSave;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

		if (savedInstanceState != null) {
			defaultItem = savedInstanceState.getInt("defaultItem");
			lastSearchString = savedInstanceState.getString("lastSearchString");
			if (lastSearchString == null) lastSearchString = "";
			lastFileName = savedInstanceState.getString("lastFileName");
			if (lastFileName == null) lastFileName = "";
			lastModTime = savedInstanceState.getLong("lastModTime");
		} else {
			defaultItem = settings.getInt("defaultItem", 0);
			lastSearchString = settings.getString("lastSearchString", "");
			lastFileName = settings.getString("lastFileName", "");
			lastModTime = settings.getLong("lastModTime", 0);
		}

		Intent i = getIntent();
		String action = i.getAction();
		String fileName = i.getStringExtra("org.petero.droidfish.pathname");
		if (action.equals("org.petero.droidfish.loadFile")) {
			pgnFile = new PGNFile(fileName);
			loadGame = true;
			showDialog(PROGRESS_DIALOG);
			final EditPGN lpgn = this;
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
		} else if (action.equals("org.petero.droidfish.loadFileNextGame") ||
				   action.equals("org.petero.droidfish.loadFilePrevGame")) {
			pgnFile = new PGNFile(fileName);
			loadGame = true;
			boolean next = action.equals("org.petero.droidfish.loadFileNextGame");
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
		} else if (action.equals("org.petero.droidfish.saveFile")) {
			loadGame = false;
			pgnToSave = i.getStringExtra("org.petero.droidfish.pgn");
			boolean silent = i.getBooleanExtra("org.petero.droidfish.silent", false);
			if (silent) { // Silently append to file
				PGNFile pgnFile2 = new PGNFile(fileName);
				pgnFile2.appendPGN(pgnToSave, null);
			} else {
				pgnFile = new PGNFile(fileName);
				showDialog(PROGRESS_DIALOG);
				final EditPGN lpgn = this;
				new Thread(new Runnable() {
					public void run() {
						readFile();
						runOnUiThread(new Runnable() {
							public void run() {
								if (gamesInFile.size() == 0) {
									pgnFile.appendPGN(pgnToSave, getApplicationContext());
									finish();
								} else {
									lpgn.showList();
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
		outState.putInt("defaultItem", defaultItem);
		outState.putString("lastSearchString", lastSearchString);
		outState.putString("lastFileName", lastFileName);
		outState.putLong("lastModTime", lastModTime);
	}

	@Override
	protected void onPause() {
		Editor editor = settings.edit();
		editor.putInt("defaultItem", defaultItem);
		editor.putString("lastSearchString", lastSearchString);
		editor.putString("lastFileName", lastFileName);
		editor.putLong("lastModTime", lastModTime);
		editor.commit();
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.edit_file_options_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.item_delete_file:
			removeDialog(DELETE_PGN_FILE_DIALOG);
			showDialog(DELETE_PGN_FILE_DIALOG);
			break;
		}
		return false;
	}

	private final void showList() {
		progress.dismiss();
		setContentView(R.layout.select_game);
		aa = new ArrayAdapter<GameInfo>(this, R.layout.select_game_list_item, gamesInFile);
		setListAdapter(aa);
		ListView lv = getListView();
		lv.setSelectionFromTop(defaultItem, 0);
		lv.setFastScrollEnabled(true);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
				selectedGi = aa.getItem(pos);
				if (loadGame) {
					defaultItem = pos;
					sendBackResult(selectedGi);
				} else {
					removeDialog(SAVE_GAME_DIALOG);
					showDialog(SAVE_GAME_DIALOG);
				}
			}
		});
		lv.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
				selectedGi = aa.getItem(pos);
				if (!selectedGi.isNull()) {
					removeDialog(DELETE_GAME_DIALOG);
					showDialog(DELETE_GAME_DIALOG);
				}
				return true;
			}
		});

//		lv.setTextFilterEnabled(true);
		filterText = (EditText)findViewById(R.id.select_game_filter);
		filterText.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) { }
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				aa.getFilter().filter(s);
				lastSearchString = s.toString();
			}
		});
		filterText.setText(lastSearchString);
		lv.requestFocus();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	final static int PROGRESS_DIALOG = 0;
	final static int DELETE_GAME_DIALOG = 1;
	final static int SAVE_GAME_DIALOG = 2;
	final static int DELETE_PGN_FILE_DIALOG = 3;

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case PROGRESS_DIALOG:
			progress = new ProgressDialog(this);
			progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progress.setTitle(R.string.reading_pgn_file);
			progress.setMessage(getString(R.string.please_wait));
			progress.setCancelable(false);
			return progress;
		case DELETE_GAME_DIALOG: {
			final GameInfo gi = selectedGi;
			selectedGi = null;
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Delete game?");
			String msg = gi.toString();
			builder.setMessage(msg);
			builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					deleteGame(gi);
					dialog.cancel();
				}
			});
			builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});
			AlertDialog alert = builder.create();
			return alert;
		}
		case SAVE_GAME_DIALOG: {
			final GameInfo gi = selectedGi;
			selectedGi = null;
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Save game?");
			final CharSequence[] items = { "Before Selected", "After Selected", "Replace Selected" };
			builder.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					GameInfo giToReplace;
					switch (item) {
					case 0: giToReplace = new GameInfo().setNull(gi.startPos); break;
					case 1: giToReplace = new GameInfo().setNull(gi.endPos); break;
					case 2: giToReplace = gi; break;
					default:
						finish(); return;
					}
					pgnFile.replacePGN(pgnToSave, giToReplace, getApplicationContext());
					finish();
				}
			});
			AlertDialog alert = builder.create();
			return alert;
		}
		case DELETE_PGN_FILE_DIALOG: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Delete file?");
			String name = new File(pgnFile.getName()).getName();
			String msg = String.format("Delete file %s?", name);
			builder.setMessage(msg);
			builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					pgnFile.delete();
					finish();
				}
			});
			builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});
			AlertDialog alert = builder.create();
			return alert;
		}
		default:
			return null;
		}
	}

	private final void readFile() {
		String fileName = pgnFile.getName();
		if (!fileName.equals(lastFileName))
			defaultItem = 0;
		long modTime = new File(fileName).lastModified();
		if (cacheValid && (modTime == lastModTime) && fileName.equals(lastFileName))
			return;
		lastModTime = modTime;
		lastFileName = fileName;
		pgnFile = new PGNFile(fileName);
		gamesInFile = pgnFile.getGameInfo(this, progress);
		cacheValid = true;
	}

	private final void sendBackResult(GameInfo gi) {
		String pgn = pgnFile.readOneGame(gi);
		if (pgn != null) {
			setResult(RESULT_OK, (new Intent()).setAction(pgn));
			finish();
		} else {
			setResult(RESULT_CANCELED);
			finish();
		}
	}

	private final void deleteGame(GameInfo gi) {
		if (pgnFile.deleteGame(gi, getApplicationContext(), gamesInFile)) {
			ListView lv = getListView();
			int pos = lv.pointToPosition(0,0);
			aa = new ArrayAdapter<GameInfo>(this, R.layout.select_game_list_item, gamesInFile);
			setListAdapter(aa);
			String s = filterText.getText().toString();
			aa.getFilter().filter(s);
			lv.setSelection(pos);
			// Update lastModTime, since current change has already been handled
			String fileName = pgnFile.getName();
			long modTime = new File(fileName).lastModified();
			lastModTime = modTime;
		}
	}
}

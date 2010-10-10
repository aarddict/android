/* This file is part of Aard Dictionary for Android <http://aarddict.org>.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License <http://www.gnu.org/licenses/gpl-3.0.txt>
 * for more details.
 * 
 * Copyright (C) 2010 Igor Tkach
 */

package aarddict.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import aarddict.VerifyProgressListener;
import aarddict.Volume;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.net.Uri;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

public final class DictionariesActivity extends BaseDictionaryActivity {

	private final static String TAG = DictionariesActivity.class.getName();

	private ListView listView;
	private Map<UUID, VerifyRecord> verifyData = new HashMap<UUID, VerifyRecord>();
	private DictListAdapter dataAdapter;
	
	private boolean aboutToFinish = false;
		
	@Override
	void onDictionaryServiceConnected() {
		Intent intent = getIntent();
		String action = intent.getAction();
				
        if (action != null && action.equals(Intent.ACTION_VIEW)) {
            final Uri data = intent.getData();
            Log.d(TAG, "Path: " + data.getPath());              
            if (data != null && data.getPath() != null) {
                Runnable r = new Runnable() {                   
                    public void run() {
                    	final String path = data.getPath();
                        Log.d(TAG, "opening: " + path);
                        final Map<File, Exception> errors = dictionaryService.open(new File(path));
                        runOnUiThread(new Runnable() {							
							public void run() {
								if (errors.size() == 0) {
									Toast.makeText(getApplicationContext(),
											getString(R.string.toastDictFileLoaded, path),
											Toast.LENGTH_LONG).show();
								}
								else {
									Toast.makeText(getApplicationContext(), 
											getString(R.string.toastDictFileFailed, path),
											Toast.LENGTH_LONG).show();									
								}
								finish();								
							}
						});
                    }
                };
                new Thread(r).start();                  
                Log.d(TAG, "started: " + data.getPath());
            }
        }        
		
		
		if (action != null && action.equals(ACTION_NO_DICTIONARIES)) {
			showNoDictionariesView();
		} else {
			super.onDictionaryServiceConnected();
		}
	}
	
	private void showNoDictionariesView() {
		TextView messageView = (TextView) findViewById(R.id.dictionariesMessageView);
		Button scanSDButton = (Button) findViewById(R.id.scanSDButton);		
		messageView.setVisibility(View.VISIBLE);
		scanSDButton.setVisibility(View.VISIBLE);
		listView.setVisibility(View.GONE);		
	}
	
	@Override
	void onDictionaryServiceReady() {
		if (aboutToFinish) {
			return;
		}
		Log.d(TAG, "service ready");
		
		if (dictionaryService.getDictionaries().isEmpty()) {
			showNoDictionariesView();
		} else {
			Intent intent = getIntent();
			String action = intent.getAction();
			Log.d(TAG, "Action: " + action);
			if (action != null && action.equals(ACTION_NO_DICTIONARIES)) {
				aboutToFinish = true;
				Intent next = new Intent();
				next.setClass(this, LookupActivity.class);
				Log.d(TAG, "Starting Lookup Activity");
				startActivity(next);
				finish();
			} else {
				TextView messageView = (TextView) findViewById(R.id.dictionariesMessageView);
				Button scanSDButton = (Button) findViewById(R.id.scanSDButton);				
				messageView.setVisibility(View.GONE);
				scanSDButton.setVisibility(View.GONE);
				listView.setVisibility(View.VISIBLE);
				dataAdapter = new DictListAdapter(dictionaryService
						.getVolumes());
				listView.setAdapter(dataAdapter);
				listView.setOnItemClickListener(dataAdapter);
				listView.setOnItemLongClickListener(dataAdapter);
			}
		}
	}

	@Override
	void initUI() {

		setContentView(R.layout.dictionaries);

		listView = (ListView) findViewById(R.id.dictionariesList);

		Button scanSDButton = (Button) findViewById(R.id.scanSDButton);
		scanSDButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				scandSDCard();
			}
		});

		TextView messageView = (TextView) findViewById(R.id.dictionariesMessageView);
		messageView.setMovementMethod(LinkMovementMethod.getInstance());
		messageView.setText(Html.fromHtml(getString(R.string.noDictionaries)));

		String appName = getString(R.string.appName);
		setTitle(getString(R.string.titleDictionariesActivity, appName));
		try {
			loadVerifyData();
		} catch (Exception e) {
			Log.e(TAG, "Failed to load verify data", e);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (dataAdapter != null) {
			dataAdapter.destroy();
		}
	}

	final class DictListAdapter extends BaseAdapter implements
			AdapterView.OnItemClickListener,
			AdapterView.OnItemLongClickListener

	{
		LayoutInflater inflater;
		List<List<Volume>> volumes;
		Timer timer = new Timer();
		long TIME_UPDATE_PERIOD = 60 * 1000;

		@SuppressWarnings("unchecked")
		public DictListAdapter(Map<UUID, List<Volume>> volumes) {
			this.volumes = new ArrayList();
			this.volumes.addAll(volumes.values());
			inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			timer.scheduleAtFixedRate(new TimerTask() {
				public void run() {
					updateView();
				}
			}, TIME_UPDATE_PERIOD, TIME_UPDATE_PERIOD);
		}

		public int getCount() {
			return volumes.size();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public void destroy() {
			timer.cancel();
		}

		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			showDetail(position);
		}

		private void showDetail(int position) {
			Intent i = new Intent(DictionariesActivity.this,
					DictionaryInfoActivity.class);
			i.putExtra("volumeId", volumes.get(position).get(0).getId());
			startActivity(i);
		}

		final class ProgressListener implements VerifyProgressListener {

			boolean proceed = true;
			ProgressDialog progressDialog;
			int max;
			int verifiedCount = 0;

			ProgressListener(ProgressDialog progressDialog, int max) {
				this.progressDialog = progressDialog;
				this.max = max;
			}

			public boolean updateProgress(final Volume d, final double progress) {
				runOnUiThread(new Runnable() {
					public void run() {
						CharSequence m = getTitle(d, true);
						progressDialog.setMessage(m);
						progressDialog
								.setProgress((int) (100 * progress / max));
					}
				});
				return proceed;
			}

			public void verified(final Volume d, final boolean ok) {
				verifiedCount++;
				Log.i(TAG, String.format("Verified %s: %s",
						d.getDisplayTitle(), (ok ? "ok" : "corrupted")));
				if (!ok) {
					recordVerifyData(d.getDictionaryId(), ok);
					progressDialog.dismiss();
					CharSequence message = getString(R.string.msgDictCorruped,
							getTitle(d, true));
					showError(message);
				} else {
					runOnUiThread(new Runnable() {
						public void run() {
							Toast.makeText(
									DictionariesActivity.this,
									getString(R.string.msgDictOk, getTitle(d,
											true)), Toast.LENGTH_SHORT).show();
						}
					});
					if (verifiedCount == max) {
						recordVerifyData(d.getDictionaryId(), ok);
						progressDialog.dismiss();
					}
				}
			}
		}

		private void recordVerifyData(UUID uuid, boolean ok) {
			VerifyRecord record = new VerifyRecord();
			record.uuid = uuid;
			record.ok = ok;
			record.date = new Date();
			verifyData.put(record.uuid, record);
			try {
				saveVerifyData();
			} catch (Exception e) {
				Log.e(TAG, "Failed to save verify data", e);
			}
			updateView();
		}

		private void updateView() {
			runOnUiThread(new Runnable() {
				public void run() {
					notifyDataSetChanged();
				}
			});
		}

		private void showError(final CharSequence message) {
			runOnUiThread(new Runnable() {
				public void run() {
					AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
							DictionariesActivity.this);
					dialogBuilder.setTitle(R.string.titleError).setMessage(
							message).setNeutralButton(R.string.btnDismiss,
							new OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									dialog.dismiss();
								}
							});
					dialogBuilder.show();
				}
			});
		}

		public boolean onItemLongClick(AdapterView<?> parent, View view,
				int position, long id) {
			verify(position);
			return true;
		}

		private void verify(int position) {
			final List<Volume> allDictVols = volumes.get(position);
			final ProgressDialog progressDialog = new ProgressDialog(
					DictionariesActivity.this);
			progressDialog.setIndeterminate(false);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setTitle(R.string.titleVerifying);
			progressDialog.setMessage(getTitle(allDictVols.get(0), false));
			progressDialog.setCancelable(true);
			final ProgressListener progressListener = new ProgressListener(
					progressDialog, allDictVols.size());

			Runnable verify = new Runnable() {
				public void run() {
					for (Volume d : allDictVols) {
						try {
							d.verify(progressListener);
						} catch (Exception e) {
							Log.e(TAG, "There was an error verifying volume "
									+ d.getId(), e);
							progressListener.proceed = false;
							progressDialog.dismiss();
							showError(getString(R.string.msgErrorVerifying, d
									.getDisplayTitle(), e.getLocalizedMessage()));
						}
					}
				}
			};

			progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
					getString(R.string.btnCancel), new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							progressListener.proceed = false;
						}
					});
			progressDialog.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					progressListener.proceed = false;
				}
			});
			Thread t = new Thread(verify);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
			progressDialog.show();
		}

		CharSequence getTitle(Volume d, boolean withVol) {
			StringBuilder s = new StringBuilder(d.getDisplayTitle(withVol));
			if (d.metadata.version != null) {
				s.append(" ").append(d.metadata.version);
			}
			return s;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			List<Volume> allDictVols = volumes.get(position);
			int volCount = allDictVols.size();
			Volume d = allDictVols.get(0);

			TwoLineListItem view = (convertView != null) ? (TwoLineListItem) convertView
					: createView(parent);

			view.getText1().setText(getTitle(d, false));

			Resources r = getResources();
			String articleStr = r.getQuantityString(R.plurals.articles,
					d.metadata.article_count, d.metadata.article_count);
			String totalVolumesStr = r.getQuantityString(R.plurals.volumes,
					d.header.of, d.header.of);
			String volumesStr = r.getQuantityString(R.plurals.volumes,
					volCount, volCount);
			String shortInfo = r.getString(R.string.shortDictInfo, articleStr,
					totalVolumesStr, volumesStr);
			if (verifyData.containsKey(d.getDictionaryId())) {
				VerifyRecord record = verifyData.get(d.getDictionaryId());
				CharSequence dateStr = DateUtils
						.getRelativeTimeSpanString(record.date.getTime());
				String resultStr = getString(record.ok ? R.string.verifyOk
						: R.string.verifyCorrupted);
				view.getText2().setText(
						getString(R.string.msgDataIntegrityVerified, shortInfo,
								dateStr, resultStr));
			} else {
				view.getText2().setText(
						getString(R.string.msgDataIntegrityNotVerified,
								shortInfo));
			}
			return view;
		}

		private TwoLineListItem createView(ViewGroup parent) {
			TwoLineListItem item = (TwoLineListItem) inflater.inflate(
					android.R.layout.simple_list_item_2, parent, false);
			return item;
		}

	}

	final static int MENU_INFO = 1;
	final static int MENU_VERIFY = 2;
	final static int MENU_REFRESH = 3;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_INFO, 0, R.string.mnDictDetails).setIcon(
				android.R.drawable.ic_menu_info_details);
		menu.add(0, MENU_VERIFY, 1, R.string.mnDictVerify).setIcon(
				android.R.drawable.ic_menu_manage);
		menu.add(0, MENU_REFRESH, 2, R.string.mnDictRefresh).setIcon(
				R.drawable.ic_menu_refresh);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		int selected = listView.getSelectedItemPosition();
		boolean validSelection = selected != ListView.INVALID_POSITION;
		menu.getItem(0).setEnabled(validSelection);
		menu.getItem(1).setEnabled(validSelection);
		menu.getItem(2).setEnabled(true);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int selected = listView.getSelectedItemPosition();
		boolean validSelection = selected != ListView.INVALID_POSITION;
		switch (item.getItemId()) {
		case MENU_INFO:
			if (validSelection) {
				dataAdapter.showDetail(selected);
			}
			break;
		case MENU_VERIFY:
			if (validSelection) {
				dataAdapter.verify(selected);
			}
			break;
		case MENU_REFRESH:
			scandSDCard();
			break;
		}
		return true;
	}

	private void scandSDCard() {
		new Thread(new Runnable() {
			public void run() {
				dictionaryService.refresh();
				runOnUiThread(new Runnable() {
					public void run() {
						onDictionaryServiceReady();
					}
				});
			}
		}).start();
	}

	void saveVerifyData() throws IOException {
		File verifyDir = getDir("verify", 0);
		File verifyFile = new File(verifyDir, "verifydata");
		FileOutputStream fout = new FileOutputStream(verifyFile);
		ObjectOutputStream oout = new ObjectOutputStream(fout);
		oout.writeObject(verifyData);
	}

	@SuppressWarnings("unchecked")
	void loadVerifyData() throws IOException, ClassNotFoundException {
		File verifyDir = getDir("verify", 0);
		File verifyFile = new File(verifyDir, "verifydata");
		if (verifyFile.exists()) {
			FileInputStream fin = new FileInputStream(verifyFile);
			ObjectInputStream oin = new ObjectInputStream(fin);
			verifyData = (Map<UUID, VerifyRecord>) oin.readObject();
		}
	}
}

package aarddict.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import aarddict.Volume;
import aarddict.VerifyProgressListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

public class DictionariesActivity extends Activity {

	private final static String TAG = DictionariesActivity.class.getName();
	
	final Handler       handler = new Handler();
	ListView listView;
    DictionaryService 	dictionaryService;    
    
    public static class VerifyRecord implements Serializable {
    	public UUID uuid;
    	public Date date;
    	public boolean ok;
    }
    
    Map<UUID, VerifyRecord> verifyData = new HashMap(); 
    
    ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	dictionaryService = ((DictionaryService.LocalBinder)service).getService();
        	Log.d(TAG, "Service connected: " + dictionaryService);
        	init();
        }

        public void onServiceDisconnected(ComponentName className) {
        	Log.d(TAG, "Service disconnected: " + dictionaryService);
        	dictionaryService = null;
            Toast.makeText(DictionariesActivity.this, "Dictionary service disconnected, quitting...",
                    Toast.LENGTH_LONG).show();
            DictionariesActivity.this.finish();
        }
    };

	private DictListAdapter dataAdapter;    

    private void init() {
    	dataAdapter = new DictListAdapter(dictionaryService.getVolumes());
    	listView.setAdapter(dataAdapter);
    	listView.setOnItemClickListener(dataAdapter);    	
    	listView.setOnItemLongClickListener(dataAdapter);
    }
    
    
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().requestFeature(Window.FEATURE_LEFT_ICON);
        
        listView = new ListView(this);
        
        setContentView(listView);
        setTitle("Dictionaries");        
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.aarddict);
        try {
			loadVerifyData();
		} catch (Exception e) {
			Log.e(TAG, "Failed to load verify data", e);
		}
        Intent dictServiceIntent = new Intent(this, DictionaryService.class);                        
        bindService(dictServiceIntent, connection, 0);        
    }
    
    protected void onDestroy() {
        super.onDestroy();
        dataAdapter.destroy();
        unbindService(connection);
    }

    class DictListAdapter extends BaseAdapter 
    		implements AdapterView.OnItemClickListener,
    		AdapterView.OnItemLongClickListener
    
    {

		LayoutInflater inflater;    	
		List<List<Volume>> volumes;
		Timer timer = new Timer();
		long TIME_UPDATE_PERIOD = 60*1000;

		@SuppressWarnings("unchecked")
        public DictListAdapter(Map<UUID, List<Volume>> volumes) {
			this.volumes = new ArrayList();
			this.volumes.addAll(volumes.values());
            inflater = (LayoutInflater) getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
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
        
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        	Intent i = new Intent(DictionariesActivity.this, DictionaryInfoActivity.class);
        	i.putExtra("volumeId", volumes.get(position).get(0).getId());
        	startActivity(i);
        }

        class ProgressListener implements VerifyProgressListener {

        	boolean proceed = true;
        	ProgressDialog progressDialog;
        	int max;
        	int verifiedCount = 0;
        	
        	ProgressListener(ProgressDialog progressDialog, int max) {
        		this.progressDialog = progressDialog;
        		this.max = max;
        	}
        	
			@Override
			public boolean updateProgress(final Volume d, final double progress) {
				handler.post(new Runnable() {
					public void run() {
						CharSequence m = getTitle(d, true);
						progressDialog.setMessage(m);
						progressDialog.setProgress((int)(100*progress/max));
					}
				});
				return proceed;
			}

			@Override
			public void verified(final Volume d, final boolean ok) {
				verifiedCount++;
				Log.i(TAG, String.format("Verified %s: %s", d.getDisplayTitle(), (ok ? "ok" : "corrupted")));
				if (!ok) {
					recordVerifyData(d.getDictionaryId(), ok);					
					progressDialog.dismiss();
					CharSequence message = String.format("%s is corrupted", getTitle(d, true));					
					showError(message);					
				} else {
					handler.post(new Runnable() {
						public void run() {
							Toast.makeText(DictionariesActivity.this, 
									String.format("%s is ok", getTitle(d, true)), 
									Toast.LENGTH_SHORT).show();
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
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to save verify data", e);
			}
			updateView();
        }
        
        private void updateView() {
			handler.post(new Runnable() {
				public void run() {
					notifyDataSetChanged();
				}
			});        	
        }
        
		private void showError(final CharSequence message) {
			handler.post(new Runnable() {
				public void run() {
			        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(DictionariesActivity.this);
			        dialogBuilder.setTitle("Error").setMessage(message).setNeutralButton("Dismiss", new OnClickListener() {            
			            @Override
			            public void onClick(DialogInterface dialog, int which) {
			                dialog.dismiss();
			            }
			        });
			        dialogBuilder.show();						
				}
			});
		}
		
		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			final List<Volume> allDictVols = volumes.get(position);
			final ProgressDialog progressDialog = new ProgressDialog(DictionariesActivity.this);
			progressDialog.setIndeterminate(false);
	        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	        progressDialog.setTitle("Verifying");
	        progressDialog.setMessage(getTitle(allDictVols.get(0), false));
	        progressDialog.setCancelable(true);
			final ProgressListener progressListener = new ProgressListener(progressDialog, allDictVols.size());	        
	        
			Runnable verify = new Runnable() {																			
				@Override
				public void run() {
					for (Volume d : allDictVols) {						
						try {
							d.verify(progressListener);
						} catch (Exception e) {
							Log.e(TAG, "There was an error verifying volume " + d.getId(), e);
							progressListener.proceed = false;
							progressDialog.dismiss();
							showError(String.format("Error encountered while verifying %s: %s", d.getDisplayTitle(), e.getLocalizedMessage()));
						}
					}
				}
			};

	        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					progressListener.proceed = false;					
				}
			});			
	        progressDialog.setOnCancelListener(new OnCancelListener() {
				
				@Override
				public void onCancel(DialogInterface dialog) {
					progressListener.proceed = false;										
				}
			});
	        Thread t = new Thread(verify);
	        t.setPriority(Thread.MIN_PRIORITY);
	        t.start();
	        progressDialog.show();
			return true;
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
        	
            TwoLineListItem view = (convertView != null) ? (TwoLineListItem) convertView :
                createView(parent);
                        
            view.getText1().setText(getTitle(d, false));
            
            Resources r = getResources();
			String articleStr = r.getQuantityString(R.plurals.articles, d.metadata.article_count, d.metadata.article_count);            
            String totalVolumesStr = r.getQuantityString(R.plurals.volumes, d.header.of, d.header.of);
            String volumesStr = r.getQuantityString(R.plurals.volumes, volCount, volCount);
            String shortInfo = r.getString(R.string.short_dict_info, articleStr, totalVolumesStr, volumesStr);
            if (verifyData.containsKey(d.getDictionaryId())) {
            	VerifyRecord record = verifyData.get(d.getDictionaryId()); 
            	CharSequence dateStr = DateUtils.getRelativeTimeSpanString(record.date.getTime());
            	String resultStr = record.ok ? "ok" : "corrupted";
            	view.getText2().setText(String.format("%s\nData integrity verified %s: %s", shortInfo, dateStr, resultStr));
            }
            else {
            	view.getText2().setText(shortInfo+"\nData integrity not verified");
            }            
        	return view;
        }
        
        private TwoLineListItem createView(ViewGroup parent) {
            TwoLineListItem item = (TwoLineListItem) inflater.inflate(
                    android.R.layout.simple_list_item_2, parent, false);
            return item;
        }
        
    }   

    void saveVerifyData() throws IOException {
    	File verifyDir = getDir("verify", 0);
    	File verifyFile = new File(verifyDir, "verifydata");
    	FileOutputStream fout = new FileOutputStream(verifyFile);
    	ObjectOutputStream oout = new ObjectOutputStream(fout);
    	oout.writeObject(verifyData);
    }

    void loadVerifyData() throws IOException, ClassNotFoundException {
    	File verifyDir = getDir("verify", 0);
    	File verifyFile = new File(verifyDir, "verifydata");
    	if (verifyFile.exists()) {
    		FileInputStream fin = new FileInputStream(verifyFile);
    		ObjectInputStream oin = new ObjectInputStream(fin);
    		verifyData  = (Map<UUID, VerifyRecord>)oin.readObject();
    	}
    }    
}

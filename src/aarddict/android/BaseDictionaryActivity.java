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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

abstract class BaseDictionaryActivity extends Activity {
    
    private final static String         TAG        = BaseDictionaryActivity.class
                                                   .getName();
    
    protected final static String ACTION_NO_DICTIONARIES = "aarddict.android.ACTION_NO_DICTIONARIES";
    
    protected BroadcastReceiver   broadcastReceiver;    
    protected DictionaryService dictionaryService;
    
    
    private ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            dictionaryService = ((DictionaryService.LocalBinder)service).getService();
            Log.d(TAG, "Service connected: " + dictionaryService);            
            onDictionaryServiceConnected();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "Service disconnected: " + dictionaryService);
            dictionaryService = null;
            Toast.makeText(BaseDictionaryActivity.this, "Dictionary service disconnected, quitting...",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    };

    private void registerProgressReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DictionaryService.DICT_OPEN_FAILED);
        intentFilter.addAction(DictionaryService.DISCOVERY_STARTED);
        intentFilter.addAction(DictionaryService.DISCOVERY_FINISHED);
        intentFilter.addAction(DictionaryService.OPEN_FINISHED);
        intentFilter.addAction(DictionaryService.OPEN_STARTED);
        intentFilter.addAction(DictionaryService.OPENED_DICT);
                       
        broadcastReceiver = new BroadcastReceiver() {
        
            ProgressDialog discoveryProgress;
            ProgressDialog openProgress;
            
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if (a.equals(DictionaryService.DISCOVERY_STARTED)) {
                    Log.d(TAG, "dictionary disconvery started");
                    if (discoveryProgress == null) {
                        discoveryProgress = new DiscoveryProgressDialog(context);
                    }
                    discoveryProgress.show();
                } else
                if (a.equals(DictionaryService.DISCOVERY_FINISHED)) {
                    Log.d(TAG, "dictionary discovery finished");
                    if (discoveryProgress != null) {
                        discoveryProgress.dismiss();
                        discoveryProgress = null;
                    }                   
                } else
                if (a.equals(DictionaryService.OPEN_STARTED)) { 
                    Log.d(TAG, "dictionary open started");
                    int count = intent.getIntExtra("count", 0);
                    if (openProgress == null) {
                        openProgress = new OpeningProgressDialog(context);
                    }
                    openProgress.setMax(count);
                    openProgress.show();
                } else
                if (a.equals(DictionaryService.DICT_OPEN_FAILED) || 
                        a.equals(DictionaryService.OPENED_DICT)) {
                    if (openProgress != null) {
                        openProgress.incrementProgressBy(1); 
                    }
                } else
                if (a.equals(DictionaryService.OPEN_FINISHED)) {
                    if (openProgress != null) {                     
                        openProgress.dismiss();
                        openProgress = null;
                    }
                    onDictionaryOpenFinished();
                }                               
            }
        };
        registerReceiver(broadcastReceiver, intentFilter);        
    }
          
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_LEFT_ICON);        
        registerProgressReceiver();
        initUI();
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.aarddict);
        Intent dictServiceIntent = new Intent(this, DictionaryService.class);
        startService(dictServiceIntent);
        bindService(dictServiceIntent, connection, 0);        
    };
            
    abstract void initUI();
    
    void onDictionaryServiceConnected() {    	    	
		if (dictionaryService.getDictionaries().isEmpty()) {
	        new Thread(new Runnable() {				
				public void run() {
					dictionaryService.openDictionaries();
					Log.d(TAG, 
							String.format("After openDictionaries() we have %d dictionaries", 
									dictionaryService.getDictionaries().size()));
					if (dictionaryService.getDictionaries().isEmpty()) {
						runOnUiThread(new Runnable() {							
							public void run() {
								Intent next = new Intent();
								next.setAction(ACTION_NO_DICTIONARIES);
								next.setClass(getApplicationContext(), DictionariesActivity.class);
								Log.d(TAG, "No dictionaries, starting Dictionaries activity");
								startActivity(next);
								finish();
							}
						});
					} else {
						runOnUiThread(new Runnable() {								
							public void run() {
								onDictionaryServiceReady();
							}
						});    						
					}
				}
			}).start();
		}
		else {
			onDictionaryServiceReady();
		}
    };
    
    abstract void onDictionaryServiceReady();
    void onDictionaryOpenFinished(){};
            
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
        unbindService(connection);
    }    
}

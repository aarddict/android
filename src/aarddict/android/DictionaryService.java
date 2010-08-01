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
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import aarddict.Article;
import aarddict.ArticleNotFound;
import aarddict.Entry;
import aarddict.Library;
import aarddict.Metadata;
import aarddict.RedirectTooManyLevels;
import aarddict.Volume;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.util.Log;

public final class DictionaryService extends Service {
		
    public class LocalBinder extends Binder {
    	DictionaryService getService() {
            return DictionaryService.this;
        }
    }	
	
	private final static String TAG = "aarddict.android.DictionaryService";
	
	public final static String DISCOVERY_STARTED = TAG + ".DISCOVERY_STARTED";
	public final static String DISCOVERY_FINISHED = TAG + ".DISCOVERY_FINISHED";
	public final static String OPEN_STARTED = TAG + ".OPEN_STARTED";
	public final static String OPENED_DICT = TAG + ".OPENED_DICT";
	public final static String CLOSED_DICT = TAG + ".CLOSED_DICT";
	public final static String DICT_OPEN_FAILED = TAG + ".DICT_OPEN_FAILED";
	public final static String OPEN_FINISHED = TAG + ".OPEN_FINISHED";
	
	private boolean started;
	
	Library library;		
    FilenameFilter fileFilter = new FilenameFilter() {
        public boolean accept(File dir, String filename) {

            return filename.toLowerCase().endsWith(
                    ".aar") || new File(dir, filename).isDirectory();
        }
    };
	
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    private final IBinder binder = new LocalBinder();

    private BroadcastReceiver broadcastReceiver;    

	@Override
	public void onCreate() {
		Log.d(TAG, "On create");
		library = new Library();
		loadAddedFileList();
		broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Uri path = intent.getData();
                Log.d(TAG, String.format("action: %s, path: %s", action, path));
                if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    startInit();
                }
                else {
                    stopSelf();
                }
            }		    
		};
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addDataScheme("file");
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        intentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		registerReceiver(broadcastReceiver, intentFilter);
	}

	
	@Override
	synchronized public void onStart(Intent intent, int flags) {
	    if (!started)
	        startInit();
	}	
	
    synchronized public void refresh() {
        startInit();
    }   
	
	
	synchronized private void startInit() {
        Thread t = new Thread(new Runnable() {
            public void run() {                
                Log.d(TAG, "starting service");             
                long t0 = System.currentTimeMillis();
                init();
                Log.d(TAG, "service start took " + (System.currentTimeMillis() - t0));
                started = true;
            };
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();                  
	}
	
	synchronized private void init() {
		List<File> candidates = discover();
		for (String path : addedFiles) {
		    candidates.add(new File(path));
		}
		open(candidates);		
	}
	
	List<String> addedFiles = new ArrayList<String>();
	
	synchronized public Map<File, Exception> open(File file) {
	    Map<File, Exception> errors = open(Arrays.asList(new File[]{file}));
	    if (errors.isEmpty()) {
	        addedFiles.add(file.getAbsolutePath());
	        saveAddedFileList();
	    }
		return errors;
	}
	
	private final class DeleteObserver extends FileObserver {
	    
	    private Set<String> dictFilesToWatch;
        private String dir;
	    
	    DeleteObserver(String dir) {
	        super(dir, DELETE);
	        dictFilesToWatch = new HashSet<String>();
	        this.dir = dir;
	    }
	    
	    public void add(String pathToWatch) {
	        Log.d(TAG, String.format("Watch file %s in %s", pathToWatch, dir));
	        dictFilesToWatch.add(pathToWatch);
	    }
	    
	    @Override
	    public void onEvent(int event, String path) {
            if ((event & FileObserver.DELETE) != 0) {
                Log.d(TAG, String.format("Received file event %s: %s", event, path));
                if (dictFilesToWatch.contains(path)) {
                    Log.d(TAG, String.format("Dictionary file %s in %s has been deleted, stopping service", path, dir));
                    if (addedFiles.remove(new File(dir, path).getAbsolutePath()))                    
                        saveAddedFileList();
                    stopSelf();
                }                
            }	        	        
	    }
	    
	}
	
	private Map<String, DeleteObserver> deleteObservers = new HashMap<String, DeleteObserver>();
	
	private DeleteObserver getDeleteObserver(File file) {
	    File parent = file.getParentFile();
	    String dir = parent.getAbsolutePath();
	    DeleteObserver observer = deleteObservers.get(dir);
	    if (observer == null) {
	        observer = new DeleteObserver(dir);
	        observer.startWatching();
	        deleteObservers.put(dir, observer);
	    }
	    return observer;
	}
	
    synchronized private Map<File, Exception> open(List<File> files) {
        Map<File, Exception> errors = new HashMap<File, Exception>();
        if (files.size() == 0) {
            return errors;
        }
    	Intent notifyOpenStarted = new Intent(OPEN_STARTED);
    	notifyOpenStarted.putExtra("count", files.size());
    	sendBroadcast(notifyOpenStarted);
    	Thread.yield();
    	
    	File cacheDir = getCacheDir();
    	File metaCacheDir = new File(cacheDir, "metadata");
    	if (!metaCacheDir.exists()) {
	    	if (!metaCacheDir.mkdir()) {
	    		Log.w(TAG, "Failed to create metadata cache directory");
	    	}
    	}
    	
        Map<UUID, Metadata> knownMeta = new HashMap<UUID, Metadata>();                            
        for (int i = 0;  i < files.size(); i++) {
        	File file = files.get(i);
        	Volume d = null;
            try {
            	Log.d(TAG, "Opening " + file.getName());
                d = new Volume(file, metaCacheDir, knownMeta);
                Volume existing = library.getVolume(d.getId());
                if (existing == null) {
                	Log.d(TAG, "Dictionary " + d.getId() + " is not in current collection");
                	library.add(d);
                	DeleteObserver observer = getDeleteObserver(file);
                	observer.add(file.getName());
                }
                else {
                	Log.d(TAG, "Dictionary " + d.getId() + " is already open");
                }
                Intent notifyOpened = new Intent(OPENED_DICT);
                notifyOpened.putExtra("title", d.getDisplayTitle());
                notifyOpened.putExtra("count", files.size());
                notifyOpened.putExtra("i", i);
                sendBroadcast(notifyOpened);
                Thread.yield();
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to open " + file, e);
                Intent notifyFailed = new Intent(DICT_OPEN_FAILED);
                notifyFailed.putExtra("file", file.getAbsolutePath());
                notifyFailed.putExtra("count", files.size());
                notifyFailed.putExtra("i", i);
                sendBroadcast(notifyFailed);  
                Thread.yield();
                if (addedFiles.remove(file.getAbsolutePath()))                    
                    saveAddedFileList();                
                errors.put(file, e);
            }
        }        
    	sendBroadcast(new Intent(OPEN_FINISHED));
    	Thread.yield();
        return errors;
    }	
	
	@Override
	public void onDestroy() {
		super.onDestroy();		
		unregisterReceiver(broadcastReceiver);
        for (Volume d : library) {
            try {
                d.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Failed to close " + d, e);
            }
        }
        library.clear();
        for (DeleteObserver observer : deleteObservers.values()) {
            observer.stopWatching();
        }
        Log.i(TAG, "destroyed");
	}
	
    public List<File> discover() {
		sendBroadcast(new Intent(DISCOVERY_STARTED));
		Thread.yield();
        File extStorage = Environment.getExternalStorageDirectory();
        List<File> result = new ArrayList<File>();
        if (extStorage != null) {
        	result.addAll(scanDir(extStorage));
        }
        Intent intent = new Intent(DISCOVERY_FINISHED);
        intent.putExtra("count", result.size());
        sendBroadcast(intent);
        Thread.yield();
        return result;
    }

    private List<File> scanDir(File dir) {
        List<File> candidates = new ArrayList<File>();
        File[] files = dir.listFiles(fileFilter);
        if (files != null) {
	        for (int i = 0; i < files.length; i++) {
	        	File file = files[i];
	            if (file.isDirectory()) {
	                candidates.addAll(scanDir(file));
	            }
	            else {
	                candidates.add(file);
	            }
	        }
        }
        return candidates;
    }

    public void setPreferred(String volumeId) {
    	library.makeFirst(volumeId);    	
    }
    
    public Iterator<Entry> lookup(CharSequence word) {
        return library.bestMatch(word.toString());
    }
    
    public Iterator<Entry> followLink(CharSequence word, String fromVolumeId) throws ArticleNotFound {
        return library.followLink(word.toString(), fromVolumeId);
    }    

    public Article redirect(Article article) throws RedirectTooManyLevels, ArticleNotFound, IOException {
        return library.redirect(article);
    }

    public Volume getVolume(String volumeId) {
        return library.getVolume(volumeId);
    }
    
    public Library getDictionaries() {
    	return library;
    }
    
    public CharSequence getDisplayTitle(String volumeId) {
    	return library.getVolume(volumeId).getDisplayTitle();
    }
    
    @SuppressWarnings("unchecked")
    public Map<UUID, List<Volume>> getVolumes() {
    	Map<UUID, List<Volume>> result = new LinkedHashMap();
    	for (Volume d : library) {
    		UUID dictionaryId = d.getDictionaryId();
			if (!result.containsKey(dictionaryId)) {
				result.put(dictionaryId, new ArrayList<Volume>());
			}
			result.get(dictionaryId).add(d);
    	}    	    	
    	return result;
    }


	public Article getArticle(Entry entry) throws IOException {
		return library.getArticle(entry);
	}
	
    void saveAddedFileList() {        
        try {
            File dir = getDir("addedfiles", 0);
            File file = new File(dir, "list");
            FileOutputStream fout = new FileOutputStream(file);
            ObjectOutputStream oout = new ObjectOutputStream(fout);
            oout.writeObject(addedFiles);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to save added file list", e);
        }        
    }

    @SuppressWarnings("unchecked")
    void loadAddedFileList() {
        try {
            File dir = getDir("addedfiles", 0);
            File file = new File(dir, "list");
            if (file.exists()) {
                FileInputStream fin = new FileInputStream(file);
                ObjectInputStream oin = new ObjectInputStream(fin);
                List<String> data  = (List<String>)oin.readObject();
                addedFiles.addAll(data); 
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to load added file list", e);
        }        
    }    	
}

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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import aarddict.Article;
import aarddict.ArticleNotFound;
import aarddict.Entry;
import aarddict.Header;
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

    private Library             library;

    private Set<String>         excludedScanDirs   = new HashSet<String>() {
                                                       {
                                                           add("/proc");
                                                           add("/dev");
                                                           add("/etc");
                                                           add("/sys");
                                                           add("/acct");
                                                           add("/cache");
                                                       }
                                                   };

    private FilenameFilter fileFilter = new FilenameFilter() {
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
		loadDictFileList();
		broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Uri path = intent.getData();
                Log.d(TAG, String.format("action: %s, path: %s", action, path));
                stopSelf();
            }		    
		};
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addDataScheme("file");
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        intentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
		registerReceiver(broadcastReceiver, intentFilter);
	}

	@Override
	public void onStart(Intent intent, int startId) {
		String action = intent == null ? null : intent.getAction();
		if (action != null && action.equals(Intent.ACTION_VIEW)) {
			final Uri data = intent.getData();
			Log.d(TAG, "Path: " + data.getPath());              
			if (data != null && data.getPath() != null) {
				Runnable r = new Runnable() {                   
					public void run() {
						Log.d(TAG, "opening: " + data.getPath());
						open(new File(data.getPath()));                       
					}
				};
				new Thread(r).start();                  
			}
		}        
	}
		
	synchronized public void openDictionaries() {
		Log.d(TAG, "opening dictionaries");
		long t0 = System.currentTimeMillis();        		
		List<File> candidates = new ArrayList<File>();
		for (String path : dictionaryFileNames) {
			candidates.add(new File(path));
		}
		open(candidates);
		Log.d(TAG, "dictionaries opened in " + (System.currentTimeMillis() - t0));
	}	
	
	
    synchronized public void refresh() {
    	Log.d(TAG, "starting dictionary discovery");             
    	long t0 = System.currentTimeMillis();
    	List<File> candidates = discover();    	
    	Map<File, Exception> errors = open(candidates);    	
    	for (File file : candidates) {
    		String absolutePath = file.getAbsolutePath();
    		if (!errors.containsKey(file)) {        		
    			dictionaryFileNames.add(absolutePath);    			
    		}
    		else {
    			Log.w(TAG, "Failed to open file " + absolutePath, errors.get(file));
    		}
    	}
    	saveDictFileList();    	    	    	
    	Log.d(TAG, "dictionary discovery took " + (System.currentTimeMillis() - t0));    	
    }   	
		
	private Set<String> dictionaryFileNames = new LinkedHashSet<String>();
	
	synchronized public Map<File, Exception> open(File file) {					
		Map<File, Exception> errors = open(Arrays.asList(new File[]{file}));
		if (errors.size()  == 0 && 
				!dictionaryFileNames.contains(file.getAbsoluteFile())) {
			saveDictFileList();
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
                    if (dictionaryFileNames.remove(new File(dir, path).getAbsolutePath()))                    
                        saveDictFileList();
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
        File scanRoot = new File ("/");
        List<File> result = new ArrayList<File>();
        result.addAll(scanDir(scanRoot));
        Intent intent = new Intent(DISCOVERY_FINISHED);
        intent.putExtra("count", result.size());
        sendBroadcast(intent);
        Thread.yield();
        return result;
    }
    
    private List<File> scanDir(File dir) {
        String absolutePath = dir.getAbsolutePath();
        if (excludedScanDirs.contains(absolutePath)) {
            Log.d(TAG, String.format("%s is excluded", absolutePath));
            return Collections.EMPTY_LIST;
        }
        boolean symlink = false;
        try {
            symlink = isSymlink(dir);
        } catch (IOException e) {
            Log.e(TAG,
                    String.format("Failed to check if %s is symlink",
                            dir.getAbsolutePath()));
        }

        if (symlink) {
            Log.d(TAG, String.format("%s is a symlink", absolutePath));
            return Collections.EMPTY_LIST;
        }

        if (dir.isHidden()) {
            Log.d(TAG, String.format("%s is hidden", absolutePath));
            return Collections.EMPTY_LIST;
        }
        Log.d(TAG, "Scanning " + absolutePath);
        List<File> candidates = new ArrayList<File>();
        File[] files = dir.listFiles(fileFilter);
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (file.isDirectory()) {
                    candidates.addAll(scanDir(file));
                } else {
                    if (!file.isHidden() && file.isFile()) {
                        candidates.add(file);
                    }
                }
            }
        }
        return candidates;
    }

    static boolean isSymlink(File file) throws IOException {
        File fileInCanonicalDir = null;
        if (file.getParent() == null) {
            fileInCanonicalDir = file;
        } else {
            File canonicalDir = file.getParentFile().getCanonicalFile();
            fileInCanonicalDir = new File(canonicalDir, file.getName());
        }
        if (fileInCanonicalDir.getCanonicalFile().equals(
                fileInCanonicalDir.getAbsoluteFile())) {
            return false;
        } else {
            return true;
        }
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
	
    void saveDictFileList() {        
        try {
            File dir = getDir(DICTDIR, 0);
            File file = new File(dir, DICTFILE);
            FileOutputStream fout = new FileOutputStream(file);
            ObjectOutputStream oout = new ObjectOutputStream(fout);
            oout.writeObject(new ArrayList<String>(dictionaryFileNames));
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to save dictionary file list", e);
        }        
    }

    private final static String DICTDIR = "dicts";
    private final static String DICTFILE = "dicts.list";
    
    @SuppressWarnings("unchecked")
    void loadDictFileList() {
        try {
            File dir = getDir(DICTDIR, 0);
            File file = new File(dir, DICTFILE);
            if (file.exists()) {
                FileInputStream fin = new FileInputStream(file);
                ObjectInputStream oin = new ObjectInputStream(fin);
                List<String> data  = (List<String>)oin.readObject();
                dictionaryFileNames.addAll(data); 
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to load dictionary file list", e);
        }        
    }    	
}

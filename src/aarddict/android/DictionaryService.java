package aarddict.android;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import aarddict.Article;
import aarddict.Collection;
import aarddict.Volume;
import aarddict.Entry;
import aarddict.Metadata;
import aarddict.RedirectError;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

public class DictionaryService extends Service {
		
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
	public final static String DICT_OPEN_FAILED = TAG + ".DICT_OPEN_FAILED";
	public final static String OPEN_FINISHED = TAG + ".OPEN_FINISHED";
	
	Collection dicts;
	
	private boolean started = false;
	private boolean starting = false;
	
    FilenameFilter fileFilter = new FilenameFilter() {

        @Override
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

	@Override
	public void onCreate() {
		Log.d(TAG, "On create");
		dicts = new Collection();
	}

	
	@Override
	synchronized public void onStart(Intent intent, int flags) {
		if (!started && !starting) {
			starting = true;
			Thread t = new Thread(new Runnable() {
	        	public void run() {                
	                Log.d(TAG, "starting service");        		
	                long t0 = System.currentTimeMillis();
	                init();
	                Log.d(TAG, "service start took " + (System.currentTimeMillis() - t0));
	                starting = false;
	                started = true;	                
	        	};
	        });
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();					
		}
	}	
	
	private void init() {
		List<File> candidates = discover();
		open(candidates);		
	}
	
	public Map<File, Exception> open(File file) {
		return open(Arrays.asList(new File[]{file}));
	}
	
    synchronized public Map<File, Exception> open(List<File> files) {
    	
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
                    
        Map<File, Exception> errors = new HashMap<File, Exception>();
        for (int i = 0;  i < files.size(); i++) {
        	File file = files.get(i);
        	Volume d = null;
            try {
            	Log.d(TAG, "Opening " + file.getName());
                d = new Volume(file, metaCacheDir, knownMeta);
                Volume existing = dicts.getDictionary(d.getId());
                if (existing == null) {
                	Log.d(TAG, "Dictionary " + d.getId() + " is not in current collection");
                	dicts.add(d);
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
        for (Volume d : dicts) {
            try {
                d.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Failed to close " + d, e);
            }
        }
        dicts.clear();	
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
        for (File file : dir.listFiles(fileFilter)) {
            if (file.isDirectory()) {
                candidates.addAll(scanDir(file));
            }
            else {
                candidates.add(file);
            }
        }
        return candidates;
    }

    public void setPreferred(String volumeId) {
    	dicts.makeFirst(volumeId);    	
    }
    
    public Iterator<Entry> lookup(CharSequence word) {
        return dicts.bestMatch(word.toString());
    }
    
    public Iterator<Entry> followLink(CharSequence word, String fromVolumeId) {
        return dicts.followLink(word.toString(), fromVolumeId);
    }    

    public Article redirect(Article article) throws RedirectError, IOException {
        return dicts.redirect(article);
    }

    public Volume getDictionary(String volumeId) {
        return dicts.getDictionary(volumeId);
    }
    
    public Collection getDictionaries() {
    	return dicts;
    }
    
    public CharSequence getDisplayTitle(String volumeId) {
    	return dicts.getDictionary(volumeId).getDisplayTitle();
    }
    
    @SuppressWarnings("unchecked")
    public Map<UUID, List<Volume>> getVolumes() {
    	Map<UUID, List<Volume>> result = new LinkedHashMap();
    	for (Volume d : dicts) {
    		UUID dictionaryId = d.getDictionaryId();
			if (!result.containsKey(dictionaryId)) {
				result.put(dictionaryId, new ArrayList<Volume>());
			}
			result.get(dictionaryId).add(d);
    	}    	    	
    	return result;
    }


	public Article getArticle(Entry entry) throws IOException {
		return dicts.getArticle(entry);
	}
}

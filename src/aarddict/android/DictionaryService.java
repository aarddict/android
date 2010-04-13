package aarddict.android;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONException;

import aarddict.Dictionary;
import aarddict.Dictionary.RedirectError;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
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
	
	Dictionary.Collection dicts;
	
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
		dicts = new Dictionary.Collection();
	}

	@Override
	synchronized public int onStartCommand(Intent intent, int flags, int startId) {
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
		return START_STICKY;
	}
	
	private void init() {
		List<File> candidates = discover();
		open(candidates);		
	}
	
    public Map<File, Exception> open(List<File> files) {
    	
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
    	
        Map<UUID, Dictionary.Metadata> knownMeta = new HashMap<UUID, Dictionary.Metadata>();
                    
        Map<File, Exception> errors = new HashMap<File, Exception>();
        for (int i = 0;  i < files.size(); i++) {
        	File file = files.get(i);
        	Dictionary d = null;
            try {
                d = new Dictionary(file, metaCacheDir, knownMeta);
                dicts.add(d);
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
        for (Dictionary d : dicts) {
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
        String sdCardPath = "/sdcard";
        File f = new File(sdCardPath);
        List<File> result = scanDir(f);
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
        
    public Iterator<Dictionary.Entry> lookup(CharSequence word) {
        return dicts.bestMatch(word.toString());
    }

    public Dictionary.Article redirect(Dictionary.Article article) throws RedirectError, IOException, JSONException {
        return dicts.redirect(article);
    }

    public Dictionary getDictionary(String dictionaryId) {
        return dicts.getDictionary(dictionaryId);
    }    
}

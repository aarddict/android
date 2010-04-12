package aarddict.android;

import static java.lang.String.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

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

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
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
    	
        Map<String, JSONObject> metaCache = new HashMap<String, JSONObject>();
        File[] metaFiles = metaCacheDir.listFiles();
        for (File metaFile : metaFiles) {
        	long t0 = System.currentTimeMillis();
        	if (!metaCache.containsKey(metaFile.getName())) {
	        	try {
	        		JSONObject meta = new JSONObject(readFile(metaFile.getAbsolutePath()));
	        		metaCache.put(metaFile.getName(), meta);
	        		Log.d(TAG, format("Loaded meta for %s from cache in %s", metaFile.getName(), (System.currentTimeMillis() - t0)));
	        	}
	        	catch(Exception e) {
	        		Log.e(TAG, "Failed to restore meta from cache for " + metaFile.getName(), e);
	        	}
        	}
        }
    	
        Map<File, Exception> errors = new HashMap<File, Exception>();
        for (int i = 0;  i < files.size(); i++) {
        	File file = files.get(i);
        	Dictionary d = null;
            try {
                d = new Dictionary(file, metaCache);
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
            if (d != null) {
	            if (!metaCache.containsKey(d.getDictionaryId().toString())) {
	            	Log.d(TAG, format("Adding metadata for %s to cache", d.getDictionaryId()));                	
	            	metaCache.put(d.getDictionaryId().toString(), d.metadata);
	            	File metaFile = new File(metaCacheDir, d.getDictionaryId().toString());
	            	try {
	            		writeFile(metaFile.getAbsolutePath(), d.metadata.toString());
	            	}
	            	catch (Exception e) {
	            		Log.e(TAG, format("Failed to save metadata for %s in cache file", metaFile.getAbsoluteFile()), e);
	            	}
	            }	            
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
    
    private String readFile(String name) throws IOException {
    	InputStream fstream = new FileInputStream(name);
        final char[] buffer = new char[0x1000];
        StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(fstream, "UTF-8");
        int read;
        do {
          read = in.read(buffer, 0, buffer.length);
          if (read>0) {
            out.append(buffer, 0, read);
          }
        } while (read>=0);
        return out.toString();
    }
    
    private void writeFile(String name, String text) throws IOException {
        FileWriter fstream = new FileWriter(name);
        BufferedWriter out = new BufferedWriter(fstream);
        out.write(text);
        fstream.close();
        out.close();
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

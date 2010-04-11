package aarddict.android;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONException;

import aarddict.Dictionary;
import aarddict.Dictionary.RedirectError;
import android.util.Log;


public class Dictionaries {

    private final static String TAG = "aarddict.Dictionaries";
    
    private static Dictionaries instance;

    private Dictionary.Collection     dicts;

    private Dictionaries() {
        this.dicts = new Dictionary.Collection();
    }

    FilenameFilter fileFilter = new FilenameFilter() {

                                  @Override
                                  public boolean accept(File dir, String filename) {

                                      return filename.toLowerCase().endsWith(
                                              ".aar") || new File(dir, filename).isDirectory();
                                  }
                              };

    public List<File> discover() {
        String sdCardPath = "/sdcard";
        File f = new File(sdCardPath);
        return scanDir(f);
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
    
    public Map<File, Exception> open(List<File> files) {
        Map<File, Exception> errors = new HashMap<File, Exception>();
        for (File file : files) {
            try {
                Dictionary d = new Dictionary(file);
                dicts.add(d);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to open " + file, e);
                errors.put(file, e);
            }
        }
        return errors;
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
        
    public static synchronized Dictionaries getInstance() {
        if (instance == null) {
            instance = new Dictionaries();
        }
        return instance;
    }
    
    public synchronized void close() {
        instance = null;
        for (Dictionary d : dicts) {
            try {
                d.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Failed to close " + d, e);
            }
        }
        dicts.clear();
    }
}

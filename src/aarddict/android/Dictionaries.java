package aarddict.android;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONException;

import aarddict.Dictionary;
import aarddict.Dictionary.RedirectError;


public class Dictionaries {

    private final static Dictionaries instance = new Dictionaries();

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

    public List<String> discover() {
        String sdCardPath = "/sdcard";
        File f = new File(sdCardPath);
        return scanDir(f);
    }

    private List<String> scanDir(File dir) {
        List<String> errors = new ArrayList<String>();
        for (File file : dir.listFiles(fileFilter)) {
            if (file.isDirectory()) {
                errors.addAll(scanDir(file));
            }
            else {
                Dictionary d;
                try {
                    d = new Dictionary(file);
                    this.dicts.add(d);
                }
                catch (Exception e) {
                    errors.add(String.format("%s %s", file.getAbsolutePath(),
                            e.getMessage()));
                }
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
    
    public static Dictionaries getInstance() {
        return instance;
    }
}

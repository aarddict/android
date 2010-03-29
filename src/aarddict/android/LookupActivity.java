package aarddict.android;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Iterator;

import aarddict.Dictionary;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class LookupActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        File f = new File("/sdcard");
        FilenameFilter fileFilter = new FilenameFilter() {			
			@Override
			public boolean accept(File dir, String filename) {
				return filename.toLowerCase().endsWith(".aar");
			}
		};
		    
		Dictionary.Collection dicts = new Dictionary.Collection();
		
        for (String name : f.list(fileFilter)) {
        	
        	Dictionary d;
			try {
				d = new Dictionary("/sdcard/"+name);
				dicts.add(d);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}        	        
        }
        
        Iterator<Dictionary.Entry> dictEntries = dicts.bestMatch("a");
        StringBuilder s = new StringBuilder();
        
        for (;dictEntries.hasNext();) {
        	Dictionary.Entry entry = dictEntries.next(); 
        	s.append(entry.title + "\n");
        }
        
        tv.setText(s.toString());        
        setContentView(tv);
    }
}

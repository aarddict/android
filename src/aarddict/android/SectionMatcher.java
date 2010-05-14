package aarddict.android;

import java.util.Comparator;

import aarddict.Entry;
import aarddict.EntryComparators;
import android.util.Log;


public class SectionMatcher {
    
    final static String TAG = "aarddict.SectionMatcher";
        
    public SectionMatcher() {
    }
    
    public boolean match(String section, String candidate, int strength) {        
        Comparator<Entry> c = EntryComparators.ALL[strength];
        Entry e1 = new Entry(null, section.trim());
        Entry e2 = new Entry(null, candidate.trim());
        boolean result = c.compare(e1, e2) == 0;
        Log.d(TAG, String.format("Match section <%s> candidate <%s> strength <%s> match? %s", section, candidate, strength, result));
        return result;
    }                

    public int getNumberOfComparators() {
        return EntryComparators.ALL.length;
    }
}

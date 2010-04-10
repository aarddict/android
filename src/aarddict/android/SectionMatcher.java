package aarddict.android;

import java.util.Comparator;

import aarddict.Dictionary;
import android.util.Log;


public class SectionMatcher {
    
    final static String TAG = "aarddict.SectionMatcher";
        
    public SectionMatcher() {
    }
    
    public boolean match(String section, String candidate, int strength) {        
        Comparator<Dictionary.Entry> c = Dictionary.comparators[strength];
        Dictionary.Entry e1 = new Dictionary.Entry(null, section.trim());
        Dictionary.Entry e2 = new Dictionary.Entry(null, candidate.trim());
        boolean result = c.compare(e1, e2) == 0;
        Log.d(TAG, String.format("Match section <%s> candidate <%s> strength <%s> match? %s", section, candidate, strength, result));
        return result;
    }                

    public int getNumberOfComparators() {
        return Dictionary.comparators.length;
    }
}

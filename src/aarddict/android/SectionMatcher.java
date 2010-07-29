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

import java.util.Comparator;

import aarddict.Entry;
import aarddict.EntryComparators;
import android.util.Log;


final class SectionMatcher {
    
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

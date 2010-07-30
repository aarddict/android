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

package aarddict;

import java.util.Comparator;
import java.util.Locale;

import com.ibm.icu.text.Collator;

public class EntryComparator implements Comparator<Entry> {

	final static Locale ROOT_LOCALE = new Locale("", "", "");
	
    Collator collator;

    EntryComparator(int strength) {
        collator = Collator.getInstance(ROOT_LOCALE);
        collator.setStrength(strength);
    }

    public int compare(Entry e1, Entry e2) {
        return collator.compare(e1.title, e2.title);
    }
}
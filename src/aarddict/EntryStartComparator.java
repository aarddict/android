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

public final class EntryStartComparator extends EntryComparator {

    EntryStartComparator(int strength) {
        super(strength);
    }

    @Override
    public int compare(Entry e1, Entry e2) {
        String k2 = e2.title;
        String k1 = k2.length() < e1.title.length() ? e1.title.substring(0,
                k2.length()) : e1.title;
        int result = collator.compare(k1, k2);
        return result;
    }
}
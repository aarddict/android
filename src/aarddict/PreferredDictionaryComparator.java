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
import java.util.UUID;

final class PreferredDictionaryComparator implements Comparator<Volume> {
	
	private final UUID preferred;
	
	PreferredDictionaryComparator(UUID preferred) {
		this.preferred = preferred;
	}
	
	public int compare(Volume d1, Volume d2) {
		UUID id1 = d1.getDictionaryId();
		UUID id2 = d2.getDictionaryId();
		if (id1.equals(id2)) {
			if (id1.equals(preferred)) {
				return d1.header.volume - d2.header.volume; 
			}
		}
		else if (id1.equals(preferred)) {
			return -1;
		}
		if (id2.equals(preferred)) {
			return 1;
		}						
		return 0;
	}    	
}
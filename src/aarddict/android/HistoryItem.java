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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import aarddict.Article;
import aarddict.Entry;

final class HistoryItem implements Serializable {
	List<Entry> entries;
	int 		entryIndex;
	Article 	article;
	
	HistoryItem(Entry entry) {
		this.entries = new ArrayList<Entry>();
		this.entries.add(entry);
		this.entryIndex = -1;
	}		
	
	HistoryItem(List<Entry> entries) {
		this.entries = entries;
		this.entryIndex = -1;
	}		
			
	HistoryItem(HistoryItem that) {
		this.entries = that.entries;
		this.entryIndex = that.entryIndex;
		if (that.article != null) {
			this.article = new Article(that.article);
		}
	}		
	
	boolean hasNext() {
		return entryIndex < entries.size() - 1; 
	}
	
	Entry next() {
		entryIndex ++;
		return current();
	}
	
	Entry current() {
		return entries.get(entryIndex);
	}	
}
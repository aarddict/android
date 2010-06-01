/**
 * 
 */
package aarddict.android;

import java.util.ArrayList;
import java.util.List;

import aarddict.Article;
import aarddict.Entry;

final class HistoryItem {
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
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import android.util.Log;

public final class Library extends ArrayList<Volume> {

	int maxRedirectLevels = 5;

	private final static String TAG = Library.class.getName();
	
	public Iterator<Entry> followLink(final String word, String fromVolumeId) throws ArticleNotFound {
		Log.d(TAG, String.format("Follow link \"%s\", %s", word,
				fromVolumeId));
		Volume fromDict = getVolume(fromVolumeId);
		Metadata fromMeta = fromDict.metadata;

		LookupWord lookupWord = LookupWord.splitWord(word);
		Log.d(TAG, lookupWord.toString());
		String nameSpace = lookupWord.nameSpace;

		Log.d(TAG, String.format("Name space: %s", nameSpace));			
		Map<String, String> interwikiMap = fromMeta.getInterwikiMap();
		String nsServerUrl = interwikiMap.get(nameSpace);
		List<UUID> matchingDicts = findMatchingDicts(nsServerUrl);
		if (matchingDicts.isEmpty())
		    matchingDicts.add(fromDict.getDictionaryId());

        if (nsServerUrl == null) {
            //namespace did not resolve into server url, 
            //maybe it's not a name space, just article title with ":" in it
            lookupWord.mergeNameSpace();
        }           		
		
        Comparator<Entry>[] comparators = EntryComparators.ALL_FULL;
        
        if (lookupWord.word != null) {
            if (lookupWord.word.length() == 1)
                comparators = EntryComparators.EXACT;
            else
                if (lookupWord.word.length() == 2)
                    comparators = EntryComparators.EXACT_IGNORE_CASE;
        }           
        
        final List<Volume> dicts = new ArrayList<Volume>(this);                
        for (int i = 0; i < matchingDicts.size(); i++) {
            UUID target = matchingDicts.get(i);
            Comparator<Volume> c = new PreferredDictionaryComparator(target);
            Collections.sort(dicts.subList(i, dicts.size()), c);
        }
        
        MatchIterator result = new MatchIterator(dicts, comparators, lookupWord);
        if (result.hasNext()) {
            return result;
        }
        else {
            throw new ArticleNotFound(lookupWord);
        }
	}

	private List<UUID> findMatchingDicts(String serverUrl) {
		Log.d(TAG, "Looking for dictionary with server url "
				+ serverUrl);
		Set<UUID> seen = new HashSet<UUID>();
		List<UUID> result = new ArrayList<UUID>();
		if (serverUrl == null) {
	        Log.d(TAG, "Server url is null");		    
			return result;
		}
		for (Volume d : this) {
			String articleURLTemplate = d.getArticleURLTemplate();
			Log.d(TAG, "Looking at article url template: "
					+ articleURLTemplate);
			if (articleURLTemplate != null
					&& serverUrl.equals(articleURLTemplate)) {
				Log.d(TAG, String.format(
						"Dictionary with server url %s found: %s", serverUrl, d
								.getDictionaryId()));
				if (!seen.contains(d.getDictionaryId()))
				    result.add(d.getDictionaryId());
			}
		}
		if (result.isEmpty()) {
    		Log.d(TAG, String.format(
    				"Dictionary with server url %s not found", serverUrl));
		}
		return result;
	}

	public Iterator<Entry> bestMatch(String word) {
		LookupWord lookupWord = LookupWord.splitWord(word);
		//best match is used with human input, 
		//assume ":" is never used as namespace separator
		lookupWord.mergeNameSpace();
		return new MatchIterator(EntryComparators.ALL, this, lookupWord);
	}

	public Article getArticle(Entry e) throws IOException {
		Volume d = getVolume(e.volumeId);
		Article a = d.readArticle(e.articlePointer);
		a.title = e.title;
		a.section = e.section;
		return a;
	}

	Article redirect(Article article, int level) 
	    throws RedirectTooManyLevels, ArticleNotFound, IOException {
		if (level > maxRedirectLevels) {
			throw new RedirectTooManyLevels();
		}

		if (!article.isRedirect()) {
			return article;
		}

	    Iterator<Entry> result = followLink(article.getRedirect(), article.volumeId);
	    Entry redirectEntry = result.next();
	    Article redirectArticle = getArticle(redirectEntry);
	    return redirect(redirectArticle, level + 1);
	}

	public Article redirect(Article article) 
	    throws RedirectTooManyLevels, ArticleNotFound, IOException {
		Article result = redirect(article, 0);
		if (result != article) {
			result.redirectedFromTitle = article.title;
		}
		return result;
	}

	public Volume getVolume(String volumeId) {

		for (Volume d : this) {
			if (d.sha1sum.equals(volumeId)) {
				return d;
			}
		}
		return null;
	}

	public void makeFirst(String volumeId) {
		Volume d = getVolume(volumeId);
		if (d != null) {
			Comparator<Volume> c = new PreferredDictionaryComparator(d
					.getDictionaryId());
			Collections.sort(this, c);
		}
	}
}
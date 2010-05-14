/**
 * 
 */
package aarddict;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import android.util.Log;

public final class Collection extends ArrayList<Dictionary> {

    int maxFromVol = 50;
    int maxRedirectLevels = 5;
    
    public Iterator<Entry> followLink(final String word, String fromVolumeId) {
    	Log.d(Dictionary.TAG, String.format("Follow link \"%s\", %s", word, fromVolumeId));
    	Dictionary fromDict =  getDictionary(fromVolumeId);
    	UUID target = fromDict.getDictionaryId();
    	Metadata fromMeta = fromDict.metadata;
    	
    	LookupWord parts = LookupWord.splitWord(word);
    	Log.d(Dictionary.TAG, parts.toString());
    	String nameSpace = parts.nameSpace;
    	
    	if (fromMeta != null && nameSpace != null) {
    		Log.d(Dictionary.TAG, String.format("Name space: %s", nameSpace));
    		if (fromMeta.siteinfo != null) {
    			Log.d(Dictionary.TAG, "Siteinfo not null");
    			List interwiki = (List)fromMeta.siteinfo.get("interwikimap");        			
    			if (interwiki != null) {
    				Log.d(Dictionary.TAG, "Interwiki map not null");
    				for (Object item : interwiki) {
    					Map interwikiItem = (Map)item;
    					String prefix = (String)interwikiItem.get("prefix");
    					Log.d(Dictionary.TAG, "Analyzing prefix " + prefix);
    					if (prefix != null && prefix.equals(nameSpace)) {
    						Log.d(Dictionary.TAG, "Matching prefix found: " + prefix);
    						target = findMatchingDict((String)interwikiItem.get("url"));
    						break;
    					}
    				}
    			}
    		}
    	}
    	
    	final List<Dictionary> dicts = new ArrayList<Dictionary>(this);
    	//This is supposed to move volumes of target dict to first positions
    	//leaving everything else in place, preferred dictionary
    	//volumes coming next (if preferred and target dictionaries are different)
    	Comparator<Dictionary> c = new PreferredDictionaryComparator(target);
    	Collections.sort(dicts, c);

    	return new Iterator<Entry>() {

    		Entry                 next;
    		Set<Entry>            seen            = new HashSet<Entry>();
    		List<Iterator<Entry>> iterators       = new ArrayList<Iterator<Entry>>();
    		
    		{
    			//Unlike in best match lookup, first look in same dict using 
    			//comparators of diminishing strength
    			for (Dictionary vol : dicts) {
    				//Iterate through comparators with step 2 to skip word start comparators
    				for (int i = 0; i < EntryComparators.comparators.length; i = i + 2) {                        
    					iterators.add(vol.lookup(word, EntryComparators.comparators[i]));
    				}        	
    			}                		
    			prepareNext();	
    		}
    		
            private void prepareNext() {
                if (!iterators.isEmpty()) {
                    Iterator<Entry> i = iterators.get(0);
                    if (i.hasNext()) {
                        next = i.next();
                        Log.d(Dictionary.TAG, "follow link: next " + next.title);
                        if (!seen.contains(next)) {
                            seen.add(next);
                        }
                        else {
                            next = null;
                            prepareNext();
                        }
                    }
                    else {
                        iterators.remove(0);
                        prepareNext();
                    }
                }
                else {
                	Log.d(Dictionary.TAG, "follow link: no next");
                    next = null;
                }
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Entry next() {
                Entry current = next;
                prepareNext();
                return current;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
    	};        	
    }
    
    private UUID findMatchingDict(String serverUrl) {
    	Log.d(Dictionary.TAG, "Looking for dictionary with server url " + serverUrl);
    	if (serverUrl == null) 
    		return null;        	
    	for (Dictionary d : this) {
        	String articleURLTemplate = d.getArticleURLTemplate();
        	Log.d(Dictionary.TAG, "Looking at article url template: " + articleURLTemplate);
        	if (articleURLTemplate != null && serverUrl.equals(articleURLTemplate)) {
        		Log.d(Dictionary.TAG, String.format("Dictionary with server url %s found: %s", serverUrl, d.getDictionaryId()));
        		return d.getDictionaryId();
        	}
    	}
    	Log.d(Dictionary.TAG, String.format("Dictionary with server url %s not found", serverUrl));
    	return null;
	}

	public Iterator<Entry> bestMatch(final String word, UUID ... dictUUIDs) {
        final Set<UUID> dictUUIDSet = new HashSet<UUID>();
        dictUUIDSet.addAll(Arrays.asList(dictUUIDs));            
        return new Iterator<Entry>() {

            Entry                 next;
            int                   currentVolCount = 0;
            Set<Entry>            seen            = new HashSet<Entry>();
            List<Iterator<Entry>> iterators       = new ArrayList<Iterator<Entry>>();                
            {
                for (Comparator<Entry> c : EntryComparators.comparators) {
                    for (Dictionary vol : Collection.this) {
                        if (dictUUIDSet.size() == 0 || dictUUIDSet.contains(vol.header.uuid)) { 
                            iterators.add(vol.lookup(word, c));
                        }
                    }
                }
                prepareNext();
            }

            private void prepareNext() {
                if (!iterators.isEmpty()) {
                    Iterator<Entry> i = iterators.get(0);
                    if (i.hasNext() && currentVolCount <= maxFromVol) {
                        next = i.next();
                        if (!seen.contains(next)) {
                            seen.add(next);
                            currentVolCount++;
                        }
                        else {
                            next = null;
                            prepareNext();
                        }
                    }
                    else {
                        currentVolCount = 0;
                        iterators.remove(0);
                        prepareNext();
                    }
                }
                else {
                    next = null;
                }
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Entry next() {
                Entry current = next;
                prepareNext();
                return current;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
    
	public Article getArticle(Entry e) throws IOException {
		Dictionary d = getDictionary(e.volumeId);
		Article a = d.readArticle(e.articlePointer);
		a.title = e.title;
		a.section = e.section;
		return a;
	}
    
    Article redirect(Article article, int level) throws RedirectError, 
                                                        IOException 
                                                         {            
        if (level > maxRedirectLevels) {
            throw new RedirectTooManyLevels();
        }
        
        if (!article.isRedirect()) {
            return article;
        }
        
        Iterator<Entry> result = bestMatch(article.getRedirect(), 
                                           article.dictionaryUUID);
        if (result.hasNext()) {
            Entry redirectEntry = result.next();
            Article redirectArticle = getArticle(redirectEntry);
            return redirect(redirectArticle, level+1);
        }
        else {
            throw new RedirectNotFound();
        }
    }
    
    public Article redirect(Article article) throws RedirectError, 
                                                    IOException 
                                                     {
        return redirect(article, 0);
    }
    
    public Dictionary getDictionary(String volumeId) {
    	        	
        for (Dictionary d : this) {
            if (d.sha1sum.equals(volumeId)) {
                return d;
            }
        }
        return null;            
    }
                    
    public void makeFirst(String volumeId) {
    	Dictionary d = getDictionary(volumeId);
    	if (d != null) {
    		Comparator<Dictionary> c = new PreferredDictionaryComparator(d.getDictionaryId()); 
			Collections.sort(this, c);
    	}
    }
}
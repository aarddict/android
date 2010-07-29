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

import java.net.URI;
import java.net.URISyntaxException;

import android.util.Log;

public class LookupWord {

	final static String TAG = LookupWord.class.getName(); 
	
	public String nameSpace;
	public String word;
	public String section;
		
	public LookupWord() {		
	}
	
	public LookupWord(String nameSpace, String word, String section) {
		this.nameSpace = nameSpace;
		this.word = word;
		this.section = section;
	}	
	
	void mergeNameSpace() {
	    if (!isEmpty(nameSpace)) {
	        word = nameSpace + ":" + word;
	        nameSpace = null;
	    }	    
	}
	
    public static LookupWord splitWord(String word) {
        if (word == null || word.equals("") || word.equals("#")) {
            return new LookupWord();
        }
		try {
			return splitWordAsURI(word);
		} catch (URISyntaxException e) {
			Log.d(TAG, "Word is not proper URI: " + word);
			return splitWordSimple(word);
		}		                
    }

    static LookupWord splitWordAsURI(String word) throws URISyntaxException {
		URI uri = new URI(word);
		String nameSpace = uri.getScheme();
		String lookupWord = uri.getSchemeSpecificPart();
		lookupWord = lookupWord.replace("_", " ");
		String section = uri.getFragment();
		return new LookupWord(nameSpace, lookupWord, section);     	
    }
    
    static LookupWord splitWordSimple(String word) {    
        String[] parts = word.split("#", 2);
        String section = parts.length == 1 ? null : parts[1];
        String nsWord = (!isEmpty(parts[0]) || !isEmpty(section)) ? parts[0] : word;
        String[] nsParts = nsWord.split(":", 2);      
        String lookupWord = nsParts.length == 1 ? nsParts[0] : nsParts[1];
        lookupWord = lookupWord.replace("_", " ");
        String nameSpace = nsParts.length == 1 ? null : nsParts[0];
        return new LookupWord(nameSpace, lookupWord, section);			    	
    }
	
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        if (!isEmpty(nameSpace)) {
            s.append(nameSpace);
            s.append(":");
        }               
        s.append(word == null ? "" : word);
        if (!isEmpty(section)) {
            s.append("#");
            s.append(section);
        }
    	return s.toString();    	
    }
    
    public boolean isEmpty() {
    	return isEmpty(word) && isEmpty(section); 
    }
    
    static boolean isEmpty(String s) {
        return s == null || s.equals("");
    }    
}

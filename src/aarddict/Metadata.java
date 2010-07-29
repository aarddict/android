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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;

public final class Metadata {

    private final static String TAG = Metadata.class.getName();

    public String               title;
    public String               version;
    public int                  update;
    public String               description;
    public String               copyright;
    public String               license;
    public String               source;
    public String               lang;
    public String               sitelang;
    public String               aardtools;
    public String               ver;
    public int                  article_count;
    public String               article_format;
    public String               article_language;
    public String               index_language;
    public String               name;
    public String               mwlib;
    public String[]             language_links;
    public Map<String, Object>  siteinfo;

    private Map<String, String> interwikiMap;

    @SuppressWarnings("unchecked")
    Map<String, String> getInterwikiMap() {
        if (interwikiMap == null) {
            interwikiMap = new HashMap<String, String>();
            if (siteinfo != null) {
                Log.d(TAG, "Siteinfo not null");
                List interwiki = (List) siteinfo.get("interwikimap");
                if (interwiki != null) {
                    Log.d(TAG, "Interwiki map not null");
                    for (Object item : interwiki) {
                        Map interwikiItem = (Map) item;
                        String prefix = (String) interwikiItem.get("prefix");
                        String url = (String) interwikiItem.get("url");
                        interwikiMap.put(prefix, url);
                    }
                }
            }
        }
        return interwikiMap;
    }
}
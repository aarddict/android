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
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public final class Article implements Serializable {

    public UUID     dictionaryUUID;  
    public String   volumeId;
    public String   title;
    public String   section;
    public long     pointer;
    private String  redirect;
    public String   text;
	public String   redirectedFromTitle;

    public Article() {            
    }
    
    public Article(Article that) {
        this.dictionaryUUID = that.dictionaryUUID;
        this.volumeId = that.volumeId;
        this.title = that.title;
        this.section = that.section;
        this.pointer = that.pointer;
        this.redirect = that.redirect;
        this.text = that.text;
        this.redirectedFromTitle = that.redirectedFromTitle;
    }

    @SuppressWarnings("unchecked")
    static Article fromJsonStr(String serializedArticle) throws IOException {
    	Object[] articleTuple = Volume.mapper.readValue(serializedArticle, Object[].class);
        Article article = new Article();
        article.text = String.valueOf(articleTuple[0]);
        if (articleTuple.length == 3) {
        	Map metadata = (Map)articleTuple[2];                
            if (metadata.containsKey("r")) {
                article.redirect = String.valueOf(metadata.get("r"));
            }
            else if (metadata.containsKey("redirect")) {
                article.redirect = String.valueOf(metadata.get("redirect"));
            }
        }            
        return article;
    }

    public String getRedirect() {
        if (this.redirect != null && this.section != null) {
            return this.redirect + "#" + this.section;
        }
        return this.redirect;
    }
    
    public boolean isRedirect() {
        return this.redirect != null;
    }
    
    public boolean equalsIgnoreSection(Article other) {
        return volumeId.equals(other.volumeId) && pointer == other.pointer;
    }
    
    public boolean sectionEquals(Article other) {
        return (section == null && other.section == null) || 
            (section !=null && other.section != null && section.equals(other.section));
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (pointer ^ (pointer >>> 32));
		result = prime * result + ((section == null) ? 0 : section.hashCode());
		result = prime * result
				+ ((volumeId == null) ? 0 : volumeId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Article other = (Article) obj;
		if (pointer != other.pointer)
			return false;
		if (section == null) {
			if (other.section != null)
				return false;
		} else if (!section.equals(other.section))
			return false;
		if (volumeId == null) {
			if (other.volumeId != null)
				return false;
		} else if (!volumeId.equals(other.volumeId))
			return false;
		return true;
	}

    
}
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

import java.io.Serializable;

/**
 * @author itkach
 */
public final class Entry implements Serializable {

    public String     title;
    public String     section;
    public long       articlePointer;
    public String 	  volumeId;

    public Entry(String volumeId, String title) {
        this(volumeId, title, -1);
    }

    public  Entry(String volumeId, String title, long articlePointer) {
        this.volumeId = volumeId;
        this.title = title == null ? "" : title;
        this.articlePointer = articlePointer;
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + (int) (articlePointer ^ (articlePointer >>> 32));
        result = prime * result + ((section == null) ? 0 : section.hashCode());
        result = prime * result + ((title == null) ? 0 : title.hashCode());
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
        Entry other = (Entry) obj;
        if (articlePointer != other.articlePointer)
            return false;
        if (section == null) {
            if (other.section != null)
                return false;
        } else if (!section.equals(other.section))
            return false;
        if (title == null) {
            if (other.title != null)
                return false;
        } else if (!title.equals(other.title))
            return false;
        if (volumeId == null) {
            if (other.volumeId != null)
                return false;
        } else if (!volumeId.equals(other.volumeId))
            return false;
        return true;
    }

}
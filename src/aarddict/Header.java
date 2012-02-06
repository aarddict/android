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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Header {

    public final String signature;
    public final String sha1sum;
    public final UUID   uuid;
    public final int    version;
    public final int    volume;
    public final int    of;
    public final long   metaLength;
    public final long   indexCount;
    public final long   articleOffset;
    public final String index1ItemFormat;
    public final String keyLengthFormat;
    public final String articleLengthFormat;
    public final long   index1Offset;
    public final long   index2Offset;
    public final int    index1ItemSize;
    public final char   keyPointerSpec;
    public final char   articlePointerSpec;
    public final char   keyLengthSpec;
    public final char   articleLengthSpec;

    Header(RandomAccessFile file) throws IOException {
        int specLen = 0;
        this.signature = file.readUTF8(4);
        specLen += 4;

        this.sha1sum = file.readUTF8(40);
        specLen += 40;

        this.version = file.readUnsignedShort();
        specLen += 2;

        this.uuid = file.readUUID();
        specLen += 16;

        this.volume = file.readUnsignedShort();
        specLen += 2;

        this.of = file.readUnsignedShort();
        specLen += 2;

        this.metaLength = file.readUnsignedInt();
        specLen += 4;

        this.indexCount = file.readUnsignedInt();
        specLen += 4;

        this.articleOffset = file.readUnsignedInt();
        specLen += 4;

        this.index1ItemFormat = file.readUTF8(4);
        specLen += 4;

        this.keyLengthFormat = file.readUTF8(2);
        specLen += 2;
        this.keyLengthSpec = this.keyLengthFormat.charAt(1);

        this.articleLengthFormat = file.readUTF8(2);
        specLen += 2;
        this.articleLengthSpec = this.articleLengthFormat.charAt(1);
        
        this.index1ItemSize = calcSize(this.index1ItemFormat);
        
        this.index1Offset = specLen + this.metaLength;
        this.index2Offset = this.index1Offset + this.indexCount*this.index1ItemSize;
        this.keyPointerSpec = this.index1ItemFormat.charAt(1);
        this.articlePointerSpec = this.index1ItemFormat.charAt(2);
    }
    
    static Map<Character, Integer> structSizes = new HashMap<Character, Integer>() {
                                                   {
                                                       put('h', 2);
                                                       put('H', 2);
                                                       put('i', 4);
                                                       put('I', 4);
                                                       put('l', 4);
                                                       put('L', 4);
                                                       put('q', 8);
                                                       put('Q', 8);
                                                   }
                                               };
    
    static int calcSize(String structSpec) {
        int size = 0;
        int length = structSpec.length();
        //ignore byte order spec at index 0
        for (int i = 1; i < length; i++) {
            char c = structSpec.charAt(i);
            Integer unitSize = structSizes.get(c);
            if (unitSize != null) {
                size += unitSize;
            }
        }
        return size;
    }
}
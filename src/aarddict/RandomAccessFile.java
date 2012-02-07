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

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

final class RandomAccessFile extends java.io.RandomAccessFile {

    public RandomAccessFile(File file, String mode) throws FileNotFoundException {
        super(file, mode);
    }

    public RandomAccessFile(String fileName, String mode) throws FileNotFoundException {
        super(fileName, mode);
    }
    
    public final long readUnsignedInt() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        int ch3 = this.read();
        int ch4 = this.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((long) (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0)) & 0xFFFFFFFFL;
    }

    public final long readUnsignedLong() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        int ch3 = this.read();
        int ch4 = this.read();
        int ch5 = this.read();
        int ch6 = this.read();
        int ch7 = this.read();
        int ch8 = this.read();
        if ((ch1 | ch2 | ch3 | ch4 | ch5 | ch6 | ch7 | ch8) < 0)
            throw new EOFException();
        return ((long) (ch1 << 56) + (ch2 << 48) + (ch3 << 40) + (ch4 << 32) + (ch5 << 24) + (ch6 << 16) + (ch7 << 8) + (ch8 << 0)) & 0xFFFFFFFFFFFFFFFFL;
    }
    
    public final String readUTF8(int length) throws IOException {
        byte[] s = new byte[length];
        this.read(s);
        return Volume.utf8(s);
    }

    public final UUID readUUID() throws IOException {
        byte[] s = new byte[16];
        this.read(s);
        return Volume.uuid(s);
    }
    
    public final long readSpec(char spec) throws IOException {
        if (spec == 'L' || spec == 'I') {
            return readUnsignedInt();
        }
        if (spec == 'Q') {
            return readUnsignedLong();
        }
        if (spec == 'H') {
            return readUnsignedShort();
        }
        if (spec == 'l' || spec == 'i') {
            return readInt();
        }
        if (spec == 'q') {
            return readLong();
        }
        if (spec == 'h') {
            return this.readShort();
        }
        throw new IOException("Unsupported spec character " + spec);
    }

}
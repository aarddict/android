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

}
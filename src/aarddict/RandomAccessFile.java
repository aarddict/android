/**
 * 
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
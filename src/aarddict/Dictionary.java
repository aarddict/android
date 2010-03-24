package aarddict;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class Dictionary {

	final static Charset ASCII = Charset.forName("ascii");
	final static Charset UTF8 = Charset.forName("utf8");

	class RandomAccessFile extends java.io.RandomAccessFile {

		public RandomAccessFile(File file, String mode)
				throws FileNotFoundException {
			super(file, mode);
		}

		public RandomAccessFile(String fileName, String mode)
				throws FileNotFoundException {
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

	}

	public Dictionary(String fileName) throws IOException {
		RandomAccessFile file = new RandomAccessFile(fileName, "r");
		byte[] signature = new byte[4];
		file.read(signature);
		byte[] sha1sum = new byte[40];
		file.read(sha1sum);
		int version = file.readUnsignedShort();
		byte[] uuid = new byte[16];
		file.read(uuid);
		int volume = file.readUnsignedShort();
		int of = file.readUnsignedShort();
		long meta_length = file.readUnsignedInt();
		long index_count = file.readUnsignedInt();
		long article_offset = file.readUnsignedInt();
		byte[] index1_item_format = new byte[4];
		file.read(index1_item_format);
		byte[] key_length_format = new byte[2];
		file.read(key_length_format);
		byte[] article_length_format = new byte[2];
		file.read(article_length_format);

		byte[] rawMeta = new byte[(int) meta_length];
		file.read(rawMeta);
		BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(
				new ByteArrayInputStream(rawMeta));

		int n = 0;
		List<Integer> metaBytes = new ArrayList<Integer>();
		while (-1 != (n = bzIn.read())) {
			metaBytes.add(n);
		}
		bzIn.close();

		byte[] decompressedMeta = new byte[metaBytes.size()];
		for (int i = metaBytes.size() - 1; i > -1; i--) {
			decompressedMeta[i] = metaBytes.get(i).byteValue();
		}

		String s = String
				.format(
						"signature: %s\nsha1: %s\nversion: %d\nuuid: %s\nvolume: %d of %d\nmeta length: %d\nindex_count: %d\narticle offset: %d\n index1_item_format: %s\nkey_length_format: %s\narticle_length_format: %s\n\nmetadata:\n%s",
						new String(signature, ASCII),
						new String(sha1sum, ASCII), version, toUUID(uuid),
						volume, of, meta_length, index_count, article_offset,
						new String(index1_item_format, ASCII), new String(
								key_length_format, ASCII), new String(
								article_length_format, ASCII), new String(
								decompressedMeta, UTF8));

		System.out.println(s);

	}

	static UUID toUUID(byte[] data) {
		long msb = 0;
		long lsb = 0;
		assert data.length == 16;
		for (int i = 0; i < 8; i++)
			msb = (msb << 8) | (data[i] & 0xff);
		for (int i = 8; i < 16; i++)
			lsb = (lsb << 8) | (data[i] & 0xff);
		return new UUID(msb, lsb);
	}

	public static void main(String[] args) throws IOException {
		new Dictionary(args[0]);
	}
}

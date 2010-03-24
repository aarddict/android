package aarddict;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class Dictionary {

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
		
		String metadataStr = decompress(rawMeta);
		
		String s = String
				.format(
						"signature: %s\nsha1: %s\nversion: %d\nuuid: %s\nvolume: %d of %d\nmeta length: %d\nindex_count: %d\narticle offset: %d\n index1_item_format: %s\nkey_length_format: %s\narticle_length_format: %s\n\nmetadata:\n%s",
						new String(signature, UTF8),
						new String(sha1sum, UTF8), version, toUUID(uuid),
						volume, of, meta_length, index_count, article_offset,
						new String(index1_item_format, UTF8), new String(
								key_length_format, UTF8), new String(
								article_length_format, UTF8), metadataStr);

		System.out.println(s);

	}

	static String decompress(byte[] bytes) {
		String decompressed = null;
		try {
			decompressed = decompressGz(bytes);
		}
		catch (IOException e1) {
			try {
				decompressed = decompressBz2(bytes);
			}
			catch (IOException e2) {
				decompressed = new String(bytes, UTF8);
			}
		}		
		return decompressed;
	}
	
	static String decompressGz(byte[] bytes) throws IOException {
		GzipCompressorInputStream in = new GzipCompressorInputStream(
				new ByteArrayInputStream(bytes));
		return readUTF8(in);
	}

	static String decompressBz2(byte[] bytes) throws IOException {
		BZip2CompressorInputStream in = new BZip2CompressorInputStream(
				new ByteArrayInputStream(bytes));
		return readUTF8(in);
	}
	
	static String readUTF8(InputStream inputStream) throws IOException {
		int n = 0;
		List<Integer> bytesList = new ArrayList<Integer>();
		try {
			while (-1 != (n = inputStream.read())) {
				bytesList.add(n);
			}
		} finally {
			inputStream.close();
		}
		byte[] bytes = new byte[bytesList.size()];
		for (int i = bytesList.size() - 1; i > -1; i--) {
			bytes[i] = bytesList.get(i).byteValue();
		}
		return new String(bytes, UTF8);
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

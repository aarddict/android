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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

		public final String readUTF8(int length) throws IOException {
			byte[] s = new byte[length];
			this.read(s);
			return utf8(s);			
		}

		public final UUID readUUID() throws IOException {
			byte[] s = new byte[16];
			this.read(s);
			return uuid(s);			
		}
		
	}

	class Header {
		
		public final String signature;
		public final String sha1sum;
		public final UUID uuid;
		public final int version;
		public final int volume;
		public final int of;
		public final long metaLength;
		public final long indexCount;
		public final long articleOffset;
		public final String index1ItemFormat;
		public final String keyLengthFormat;
		public final String articleLengthFormat;
		
		Header(RandomAccessFile file) throws IOException {
			this.signature = file.readUTF8(4);
			this.sha1sum = file.readUTF8(40);
			this.version = file.readUnsignedShort();
			this.uuid = file.readUUID();
			this.volume = file.readUnsignedShort();
			this.of = file.readUnsignedShort();
			this.metaLength = file.readUnsignedInt();
			this.indexCount = file.readUnsignedInt();
			this.articleOffset = file.readUnsignedInt();	
			this.index1ItemFormat = file.readUTF8(4);
			this.keyLengthFormat = file.readUTF8(2);
			this.articleLengthFormat = file.readUTF8(2);
		}
	}

	public JsonObject metadata;
	public Header header;
	
	public Dictionary(String fileName) throws IOException {		
		RandomAccessFile file = new RandomAccessFile(fileName, "r");
		this.header = new Header(file);
		byte[] rawMeta = new byte[(int) header.metaLength];
		file.read(rawMeta);
		
		String metadataStr = decompress(rawMeta);
		
		JsonParser json = new JsonParser();
		this.metadata = json.parse(metadataStr).getAsJsonObject();		
	}

	static String utf8(byte[] signature) {
		return new String(signature, UTF8);
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
				decompressed = utf8(bytes);
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
		return utf8(bytes);
	}

	static UUID uuid(byte[] data) {
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
		Dictionary d = new Dictionary(args[0]);
		Header header = d.header; 
		String s = String
		.format(
				"signature: %s\nsha1: %s\nversion: %d\nuuid: %s\nvolume: %d of %d\nmeta length: %d\nindex_count: %d\narticle offset: %d\n index1_item_format: %s\nkey_length_format: %s\narticle_length_format: %s",
				header.signature,
				header.sha1sum, header.version, header.uuid,
				header.volume, header.of, header.metaLength, 
				header.indexCount, header.articleOffset,
				header.index1ItemFormat, header.keyLengthFormat, 
				header.articleLengthFormat);

		System.out.println(s);	
		System.out.println(d.metadata);
	}
}

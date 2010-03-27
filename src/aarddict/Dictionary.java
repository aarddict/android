package aarddict;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.AbstractList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.ibm.icu.text.Collator;


public class Dictionary extends AbstractList<Dictionary.Entry> {

    final static Charset UTF8 = Charset.forName("utf8");

    final static Locale  ROOT = new Locale("", "", "");

    static class RandomAccessFile extends java.io.RandomAccessFile {

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
            return utf8(s);
        }

        public final UUID readUUID() throws IOException {
            byte[] s = new byte[16];
            this.read(s);
            return uuid(s);
        }

    }

    static class Header {

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

            this.articleLengthFormat = file.readUTF8(2);
            specLen += 2;

            this.index1Offset = specLen + this.metaLength;
            this.index2Offset = this.index1Offset + this.indexCount * 8;
        }
    }

    static class IndexItem {

        long keyPointer;
        long articlePointer;
    }

    static class Article {

        Dictionary        dictionary;
        String            title;
        String            section;
        long              position;
        String            redirect;
        String            text;

        static JsonParser json = new JsonParser();

        static Article fromJsonStr(String serializedArticle) {
            JsonElement je = json.parse(serializedArticle);
            JsonArray articleTuple = je.getAsJsonArray();
            Article article = new Article();
            article.text = articleTuple.get(0).getAsString();
            if (articleTuple.size() == 3) {
                JsonObject metadata = articleTuple.get(2).getAsJsonObject();
                if (metadata.has("r")) {
                    article.redirect = metadata.get("r").getAsString();
                }
                else if (metadata.has("redirect")) {
                    article.redirect = metadata.get("redirect").getAsString();
                }
            }
            return article;
        }

        String getRedirect() {
            if (this.section != null) {
                return this.redirect + "#" + this.section;
            }
            else {
                return this.section;
            }
        }
    }

    static class Entry {

        String     title;
        long       articlePointer;
        Dictionary dictionary;

        Entry(Dictionary dictionary, String title) {
            this(dictionary, title, -1);
        }

        Entry(Dictionary dictionary, String title, long articlePointer) {
            this.dictionary = dictionary;
            this.title = title;
            this.articlePointer = articlePointer;
        }


        public Article getArticle() throws IOException {
            System.out.println(String.format("Reading article \"%s\"", this.title));
            Article a = dictionary.readArticle(articlePointer);
            a.title = this.title;
            return a;
        }

        @Override
        public String toString() {
            return title;
        }
    }
    
    JsonObject       metadata;
    Header           header;
    RandomAccessFile file;

    public Dictionary(String fileName) throws IOException {
        RandomAccessFile file = new RandomAccessFile(fileName, "r");
        this.file = file;
        this.header = new Header(file);
        byte[] rawMeta = new byte[(int) header.metaLength];
        file.read(rawMeta);

        String metadataStr = decompress(rawMeta);

        JsonParser json = new JsonParser();
        this.metadata = json.parse(metadataStr).getAsJsonObject();
    }

    IndexItem readIndexItem(long i) throws IOException {
        long pos = this.header.index1Offset + i * 8;
        this.file.seek(pos);
        IndexItem indexItem = new IndexItem();
        indexItem.keyPointer = this.file.readUnsignedInt();
        indexItem.articlePointer = this.file.readUnsignedInt();
        return indexItem;
    }

    String readKey(long pointer) throws IOException {
        long pos = this.header.index2Offset + pointer;
        this.file.seek(pos);
        int keyLength = this.file.readUnsignedShort();
        return this.file.readUTF8(keyLength);
    }

    Article readArticle(long pointer) throws IOException {
        long pos = this.header.articleOffset + pointer;
        this.file.seek(pos);
        long articleLength = this.file.readUnsignedInt();

        byte[] articleBytes = new byte[(int) articleLength];
        this.file.read(articleBytes);
        String serializedArticle = decompress(articleBytes);
        try {
            return Article.fromJsonStr(serializedArticle);
        }
        catch (JsonParseException e) {
            System.err.println(String.format("Failed to deserialize:\n%s", serializedArticle));
            throw e;
        }
    }
    
    public Iterator<Entry> lookup(final String word, final int strength, boolean startsWith) {

        final Comparator<Entry> c;

        final Collator collator = Collator.getInstance(ROOT);
        collator.setStrength(strength);        

        final Entry lookupEntry = new Entry(this, word);

        if (!startsWith) {
            c = new Comparator<Entry>() {
                @Override
                public int compare(Entry e1, Entry e2) {
                    int result = collator.compare(e1.title, e2.title);
                    return result;
                }
            };
        }
        else {
            c = new Comparator<Entry>() {

                @Override
                public int compare(Entry e1, Entry e2) {
                    String k2 = e2.title;
                    String k1 = k2.length() < e1.title.length() ? e1.title.substring(0, k2.length()) : e1.title;
                    int result = collator.compare(k1, k2);
                    return result;
                }
            };
        }

        final int initialIndex = binarySearch(this, lookupEntry, c);
        Iterator<Entry> iterator = new Iterator<Entry>() {

            int   index = initialIndex;
            Entry nextEntry;

            {
                prepareNext();
            }

            private void prepareNext() {                
                Entry matchedEntry = get(index);
                if (0 == c.compare(matchedEntry, lookupEntry)) {
                    nextEntry = matchedEntry;
                }
                else {
                    nextEntry = null;
                }
                index++;
            }

            @Override
            public boolean hasNext() {
                return nextEntry != null && index < header.indexCount - 1;
            }

            @Override
            public Entry next() {
                Entry current = nextEntry;                
                prepareNext();
                return current;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };

        return iterator;
    }

    @Override
    public Entry get(int index) {
        try {
            IndexItem indexItem = readIndexItem(index);
            String title = readKey(indexItem.keyPointer);
            return new Entry(this, title, indexItem.articlePointer);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int size() {
        return (int) header.indexCount;
    }

    public void close() throws IOException {
        file.close();
    };

    static String utf8(byte[] signature) {
        return new String(signature, UTF8);
    }

    static String decompress(byte[] bytes) {
        if (bytes.length == 0) return "";
        try {
            return decompressZlib(bytes);
        }
        catch (Exception e1) {
            try {
                return decompressBz2(bytes);
            }
            catch (IOException e2) {
                return utf8(bytes);
            }
        }
    }

    static String decompressZlib(byte[] bytes) throws IOException, DataFormatException {
        Inflater decompressor = new Inflater();
        decompressor.setInput(bytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[1024];
            while (!decompressor.finished()) {
                int count = decompressor.inflate(buf);
                out.write(buf, 0, count);
            }
        }
        finally {
            out.close();
        }
        return utf8(out.toByteArray());
    }

    static String decompressBz2(byte[] bytes) throws IOException {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(new ByteArrayInputStream(bytes));
        int n = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        try {
            while (-1 != (n = in.read(buf))) {
                out.write(buf, 0, n);
            }
        }
        finally {
            in.close();
            out.close();
        }
        return utf8(out.toByteArray());
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

    static <T> int binarySearch(List<? extends T> l, T key, Comparator<? super T> c) {
        int lo = 0;
        int hi = l.size();
        while (lo < hi) {
            int mid = (lo + hi)/2;
            T midVal = l.get(mid);
            int cmp = c.compare(midVal, key);
            if (cmp < 0) {
                lo = mid + 1;
            }
            else {
                hi = mid;
            }
        }
        return lo;
    }
    
    public static void main(String[] args) throws IOException {
        Dictionary d = new Dictionary(args[0]);
        
        Header header = d.header;
        String s =
        String.format("signature: %s\nsha1: %s\nversion: %d\nuuid: %s\nvolume: %d of %d\nmeta length: %d\nindex_count: %d\narticle offset: %d\nindex1_item_format: %s\nkey_length_format: %s\narticle_length_format: %s\nindex1 offset: %d\nindex 2 offset: %d",
        header.signature, header.sha1sum, header.version, header.uuid,
        header.volume, header.of, header.metaLength, header.indexCount,
        header.articleOffset, header.index1ItemFormat,
        header.keyLengthFormat, header.articleLengthFormat,
        header.index1Offset, header.index2Offset);
        System.out.println(d.metadata);
        System.out.println(s);        
        
        int count = 0;
        for (Iterator<Entry> result = d.lookup("A B", Collator.PRIMARY, true); result.hasNext(); count++) {
            try {
                Entry entry = result.next();
                System.out.println(entry.title);
                Article a = entry.getArticle();
                System.out.println(String.format("%s (redirect? %s) \n----------------------\n%s\n===================",
                a.title, a.redirect, a.text));
                if (count > 30)
                    break;
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

package aarddict;

import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.codehaus.jackson.map.ObjectMapper;

import android.util.Log;

import com.ibm.icu.text.Collator;


public class Dictionary extends AbstractList<Dictionary.Entry> {

	private final static String TAG = Dictionary.class.getName();
	
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

    public static class Header {

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

    public static class Article {

        public UUID     dictionaryUUID;  
        public String   volumeId;
        public String   title;
        public String   section;
        public long     pointer;
        private String  redirect;
        public String   text;

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
        }

        @SuppressWarnings("unchecked")
        static Article fromJsonStr(String serializedArticle) throws IOException {
        	Object[] articleTuple = mapper.readValue(serializedArticle, Object[].class);
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
        
        public boolean eqalsIgnoreSection(Article other) {
            return volumeId.equals(other.volumeId) && pointer == other.pointer;
        }
    }

    /**
     * @author itkach
     */
    public static class Entry {

        public String     title;
        public String     section;
        public long       articlePointer;
        public Dictionary dictionary;

        public Entry(Dictionary dictionary, String title) {
            this(dictionary, title, -1);
        }

        public  Entry(Dictionary dictionary, String title, long articlePointer) {
            this.dictionary = dictionary;
            this.title = title;
            this.articlePointer = articlePointer;
        }


        public Article getArticle() throws IOException {
            Article a = dictionary.readArticle(articlePointer);
            a.title = this.title;
            a.section = this.section;
            return a;
        }

        @Override
        public String toString() {
            return title;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (articlePointer ^ (articlePointer >>> 32));
            result = prime * result + ((dictionary == null) ? 0 : dictionary.hashCode());
            result = prime * result + ((title == null) ? 0 : title.hashCode());
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
            if (dictionary == null) {
                if (other.dictionary != null)
                    return false;
            }
            else if (!dictionary.equals(other.dictionary))
                return false;
            if (title == null) {
                if (other.title != null)
                    return false;
            }
            else if (!title.equals(other.title))
                return false;
            return true;
        }

    }

    public static class EntryComparator implements Comparator<Entry> {

        Collator collator;

        EntryComparator(int strength) {
            collator = Collator.getInstance(ROOT);
            collator.setStrength(strength);
        }

        @Override
        public int compare(Entry e1, Entry e2) {
            return collator.compare(e1.title, e2.title);
        }
    }

    public static class EntryStartComparator extends EntryComparator {

        EntryStartComparator(int strength) {
            super(strength);
        }

        @Override
        public int compare(Entry e1, Entry e2) {
            String k2 = e2.title;
            String k1 = k2.length() < e1.title.length() ? e1.title.substring(0,
                    k2.length()) : e1.title;
            int result = collator.compare(k1, k2);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    public static Comparator<Entry>[] comparators = new Comparator[] {
            new EntryComparator(Collator.QUATERNARY),
            new EntryStartComparator(Collator.QUATERNARY),
            new EntryComparator(Collator.TERTIARY),
            new EntryStartComparator(Collator.TERTIARY),
            new EntryComparator(Collator.SECONDARY),
            new EntryStartComparator(Collator.SECONDARY),
            new EntryComparator(Collator.PRIMARY),
            new EntryStartComparator(Collator.PRIMARY)};


    public static class RedirectError extends Exception {}
    public static class RedirectNotFound extends RedirectError {}
    public static class RedirectTooManyLevels extends RedirectError {}
    
    public static class Collection extends ArrayList<Dictionary> {

        int maxFromVol = 50;
        int maxRedirectLevels = 5;
        
        public Iterator<Entry> bestMatch(final String word, UUID ... dictUUIDs) {
            final Set<UUID> dictUUIDSet = new HashSet<UUID>();
            dictUUIDSet.addAll(Arrays.asList(dictUUIDs));            
            return new Iterator<Entry>() {

                Entry                 next;
                int                   currentVolCount = 0;
                Set<Entry>            seen            = new HashSet<Entry>();
                List<Iterator<Entry>> iterators       = new ArrayList<Iterator<Entry>>();                
                {
                    for (Comparator<Entry> c : comparators) {
                        for (Dictionary vol : Collection.this) {
                            if (dictUUIDSet.size() == 0 || dictUUIDSet.contains(vol.header.uuid)) { 
                                iterators.add(vol.lookup(word, c));
                            }
                        }
                    }
                    prepareNext();
                }

                private void prepareNext() {
                    if (!iterators.isEmpty()) {
                        Iterator<Entry> i = iterators.get(0);
                        if (i.hasNext() && currentVolCount <= maxFromVol) {
                            next = i.next();
                            if (!seen.contains(next)) {
                                seen.add(next);
                                currentVolCount++;
                            }
                            else {
                                next = null;
                                prepareNext();
                            }
                        }
                        else {
                            currentVolCount = 0;
                            iterators.remove(0);
                            prepareNext();
                        }
                    }
                    else {
                        next = null;
                    }
                }

                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public Entry next() {
                    Entry current = next;
                    prepareNext();
                    return current;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
        
        Article redirect(Article article, int level) throws RedirectError, 
                                                            IOException 
                                                             {            
            if (level > maxRedirectLevels) {
                throw new RedirectTooManyLevels();
            }
            
            if (!article.isRedirect()) {
                return article;
            }
            
            Iterator<Entry> result = bestMatch(article.getRedirect(), 
                                               article.dictionaryUUID);
            if (result.hasNext()) {
                Entry redirectEntry = result.next();
                Article redirectArticle = redirectEntry.getArticle();
                return redirect(redirectArticle, level+1);
            }
            else {
                throw new RedirectNotFound();
            }
        }
        
        public Article redirect(Article article) throws RedirectError, 
                                                        IOException 
                                                         {
            return redirect(article, 0);
        }
        
        public Dictionary getDictionary(String volumeId) {
            for (Dictionary d : this) {
                if (d.sha1sum.equals(volumeId)) {
                    return d;
                }
            }
            return null;            
        }
    }

    public Metadata  metadata;
    public Header           header;
    RandomAccessFile file;
    String           sha1sum;

	private File origFile;
    
    public static class Metadata {
		public String title;
		public String version;
		public int update;
		public String description;
		public String copyright;
		public String license;
		public String source;
		public String lang;
		public String sitelang;
		public String aardtools;
		public String ver;
		public int article_count;
		public String article_format;
		public String article_language;
		public String index_language;
		public String name;
		public String mwlib;
		public String[] language_links;
		public HashMap<String, Object> siteinfo;
    }
    
    static ObjectMapper mapper = new ObjectMapper();
    static {
    	mapper.getDeserializationConfig().set(org.codehaus.jackson.map.DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public Dictionary(File file, File cacheDir, Map<UUID, Metadata> knownMeta) throws IOException {
    	this.origFile = file;
        init(new RandomAccessFile(file, "r"), cacheDir, knownMeta);
    }
    
    private void init(RandomAccessFile file, File cacheDir, Map<UUID, Metadata> knownMeta) throws IOException {
        this.file = file;
        this.header = new Header(file);
        this.sha1sum = header.sha1sum;
        if (knownMeta.containsKey(header.uuid)) {
        	this.metadata = knownMeta.get(header.uuid);  
        } else {
            String uuidStr = header.uuid.toString();            
            File metadataCacheFile = new File(cacheDir, uuidStr);
            if (metadataCacheFile.exists()) {
            	try {
            		long t0 = System.currentTimeMillis();
            		this.metadata = mapper.readValue(metadataCacheFile, Metadata.class);
            		knownMeta.put(header.uuid, this.metadata);
            		Log.d(TAG, format("Loaded meta for %s from cache in %s", metadataCacheFile.getName(), (System.currentTimeMillis() - t0)));
            	}
	        	catch(Exception e) {
	        		Log.e(TAG, format("Failed to restore meta from cache file %s ", metadataCacheFile.getName()), e);
	        	}            	
            }
            if (this.metadata == null) {
            	long t0 = System.currentTimeMillis();
            	byte[] rawMeta = new byte[(int) header.metaLength];
            	file.read(rawMeta);
            	String metadataStr = decompress(rawMeta);
            	this.metadata = mapper.readValue(metadataStr, Metadata.class);
            	Log.d(TAG, format("Read meta for in %s", header.uuid, (System.currentTimeMillis() - t0)));
            	knownMeta.put(header.uuid, this.metadata);
            	try {
            		mapper.writeValue(metadataCacheFile, this.metadata);
            		Log.d(TAG, format("Wrote metadata to cache file %s", metadataCacheFile.getName()));
            	}
            	catch (IOException e) {
            		Log.e(TAG, format("Failed to write metadata to cache file %s", metadataCacheFile.getName()), e);
            	}            	
            }                    
        }        
    }

    public String getId() {
        return sha1sum;
    }
    
    public UUID getDictionaryId() {
    	return header.uuid;
    }
    
    @Override
    public int hashCode() {
        return sha1sum.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        Dictionary other = (Dictionary) obj;
        if (sha1sum == null) {
            if (other.sha1sum != null)
                return false;
        }
        else if (!sha1sum.equals(other.sha1sum))
            return false;
        return true;
    }

    public String toString() {
        return String.format("%s %s/%s(%s)", this.metadata.title, this.header.volume, 
                this.header.of, this.sha1sum);
    };
    
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
        Article a = Article.fromJsonStr(serializedArticle);
        a.dictionaryUUID = this.header.uuid;
        a.volumeId = this.header.sha1sum;
        a.pointer = pointer;
        return a;
    }

    static Iterator<Entry> EMPTY_ITERATOR = new ArrayList<Entry>().iterator();

    Iterator<Entry> lookup(final String word, final Comparator<Entry> comparator) {
        if (isBlank(word)) {
            return EMPTY_ITERATOR;
        }
        String[] parts = splitWord(word);
        final String lookupWord = parts[0];
        final String section = parts[1];
        final Entry lookupEntry = new Entry(this, lookupWord);
        final int initialIndex = binarySearch(this, lookupEntry, comparator);
        Iterator<Entry> iterator = new Iterator<Entry>() {

            int   index = initialIndex;
            Entry nextEntry;

            {
                prepareNext();
            }

            private void prepareNext() {
                Entry matchedEntry = get(index);
                nextEntry = (0 == comparator.compare(matchedEntry, lookupEntry)) ? matchedEntry : null;
                index++;
            }

            @Override
            public boolean hasNext() {
                return nextEntry != null && index < header.indexCount - 1;
            }

            @Override
            public Entry next() {
                Entry current = nextEntry;
                current.section = section;
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
    
    @SuppressWarnings("unchecked")
    public String getArticleURL(String title) {
    	if (metadata.siteinfo != null){
        	Map <String, Object> general = (Map <String, Object>)this.metadata.siteinfo.get("general");
        	if (general != null) {
        		Object server = general.get("server");
        		Object articlePath = general.get("articlepath");
        		if (server != null && articlePath != null) {
        			return server.toString() + articlePath.toString().replace("$1", title);
        		}		
        	}
    	}
    	Log.d("aarddict", "Not enough metadata to generate online url for title " + title);
    	return null;
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
        try {
            return new String(signature, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

    static String decompress(byte[] bytes) {
    	String type = null;
    	long t0 = System.currentTimeMillis();
        try {        	
            String result = decompressZlib(bytes);
            type = "zlib";
            return result;
        }
        catch (Exception e1) {
            try {
                String result = decompressBz2(bytes);
                type = "bz2";
                return result;
            }
            catch (IOException e2) {
                String result = utf8(bytes);
                type = "uncompressed";
                return result;
            }
        }
    	finally {
    		Log.d(TAG, "Decompressed " + type + " in " + (System.currentTimeMillis() - t0));
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
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length*5);
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
            int mid = (lo + hi) / 2;
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

    static String[] splitWord(String word) {
        if (word.equals("#")) {
            return new String[] {"", ""};
        }
        String[] parts = word.split("#", 2);
        String section = parts.length == 1 ? "" : parts[1];
        String lookupWord = (!isBlank(parts[0]) || !isBlank(section)) ? parts[0] : word;
        return new String[] {lookupWord, section};
    }

    static boolean isBlank(String s) {
        return s == null || s.equals("");
    }

    public CharSequence getDisplayTitle() {
    	return getDisplayTitle(true);
    }
    
    public CharSequence getDisplayTitle(boolean withVolumeNumber) {
        StringBuilder s = new StringBuilder(this.metadata.title);
        if (this.metadata.lang != null) {
            s.append(String.format(" (%s)", this.metadata.lang));
        }
        else {
            if (this.metadata.sitelang != null) {
                s.append(String.format(" (%s)", this.metadata.sitelang));
            }
            else {
                if (this.metadata.index_language != null && this.metadata.article_language != null) {
                    s.append(String.format(" (%s-%s)", this.metadata.index_language, this.metadata.article_language));
                }        	
            }            
        }
        if (this.header.of > 1 && withVolumeNumber) 
               s.append(String.format(" Vol. %s", this.header.volume));        
        return s.toString();
    }
    
    public static interface VerifyProgressListener {
    	boolean updateProgress(Dictionary d, double progress);
    	void verified(Dictionary d, boolean ok);
    }
    
    public void verify(VerifyProgressListener listener) throws IOException, NoSuchAlgorithmException {    	
    	FileInputStream fis = new FileInputStream(origFile);
    	fis.skip(44);
    	byte[] buff = new byte[1 << 16];    	
    	MessageDigest m = MessageDigest.getInstance("SHA-1");
    	int readCount;
    	long totalReadCount = 0;    	
    	double totalBytes = origFile.length() - 44;
    	boolean proceed = true;
    	while ((readCount=fis.read(buff)) != -1) {
    		m.update(buff, 0, readCount);
    		totalReadCount += readCount;
    		proceed = listener.updateProgress(this, totalReadCount/totalBytes);    		
    	}    	
    	fis.close();
    	if (proceed) {
    		BigInteger b = new BigInteger(1, m.digest());
    		String calculated = b.toString(16);
    		Log.d(TAG, "calculated: " + calculated + " actual: " + sha1sum);
    		listener.verified(this, calculated.equals(this.sha1sum));
    	}
    }    
}

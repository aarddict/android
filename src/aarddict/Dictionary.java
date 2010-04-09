package aarddict;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

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

    public static class Article {

        public Dictionary dictionary;
        public String     title;
        public String     section;
        public long       pointer;
        private String    redirect;
        public String     text;


        static Article fromJsonStr(String serializedArticle) throws JSONException {
            JSONArray articleTuple = new JSONArray(serializedArticle);
            Article article = new Article();
            article.text = articleTuple.getString(0);
            if (articleTuple.length() == 3) {
                JSONObject metadata = articleTuple.getJSONObject(2);
                if (metadata.has("r")) {
                    article.redirect = metadata.getString("r");
                }
                else if (metadata.has("redirect")) {
                    article.redirect = metadata.getString("redirect");
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
                
    }

    /**
     * @author itkach
     */
    public static class Entry {

        public String     title;
        public String     section;
        public long       articlePointer;
        public Dictionary dictionary;

        Entry(Dictionary dictionary, String title) {
            this(dictionary, title, -1);
        }

        Entry(Dictionary dictionary, String title, long articlePointer) {
            this.dictionary = dictionary;
            this.title = title;
            this.articlePointer = articlePointer;
        }


        public Article getArticle() throws IOException, JSONException {
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

    static class EntryComparator implements Comparator<Entry> {

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

    static class EntryStartComparator extends EntryComparator {

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
    static Comparator<Entry>[] comparators = new Comparator[] {
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
                                                            IOException, 
                                                            JSONException {            
            if (level > maxRedirectLevels) {
                throw new RedirectTooManyLevels();
            }
            
            Iterator<Entry> result = bestMatch(article.redirect, 
                                               article.dictionary.header.uuid);
            if (result.hasNext()) {
                Entry redirectEntry = result.next();
                Article redirectArticle = redirectEntry.getArticle();
                if (redirectArticle.getRedirect() != null) {
                    return redirect(redirectArticle, level+1);
                }
                else {
                    return redirectArticle;
                }
            }
            else {
                throw new RedirectNotFound();
            }
        }
        
        public Article redirect(Article article) throws RedirectError, 
                                                        IOException, 
                                                        JSONException {
            return redirect(article, 0);
        }
        
        public Dictionary getDictionary(String dictionaryId) {
            for (Dictionary d : this) {
                if (d.sha1sum.equals(dictionaryId)) {
                    return d;
                }
            }
            return null;            
        }
            
        public Article getArticle(String dictionaryId, long articlePointer) throws IOException, JSONException {
            Dictionary d = getDictionary(dictionaryId);
            return d == null ? null : d.readArticle(articlePointer); 
        }
        
        public String getArticleURL(String dictionaryId, String title) {
            Dictionary d = getDictionary(dictionaryId);
            return d == null ? null : d.getArticleURL(title); 
        }
        
    }

    JSONObject       metadata;
    Header           header;
    RandomAccessFile file;
    String           sha1sum;
    String           title;
    String           version;
    String           description;
    String           copyright;
    String           license;
    String           source;
    String           lang;
    String           sitelang;

    public Dictionary(String fileName) throws IOException, JSONException {
        init(new RandomAccessFile(fileName, "r"));
    }

    public Dictionary(File file) throws IOException, JSONException {
        init(new RandomAccessFile(file, "r"));
    }
    
    private void init(RandomAccessFile file) throws IOException, JSONException {
        this.file = file;
        this.header = new Header(file);
        this.sha1sum = header.sha1sum;
        byte[] rawMeta = new byte[(int) header.metaLength];
        file.read(rawMeta);

        String metadataStr = decompress(rawMeta);
        this.metadata = new JSONObject(metadataStr);
        this.title = this.metadata.getString("title");
        this.version = this.metadata.getString("version");
        this.description = this.metadata.getString("description");
        this.copyright = this.metadata.getString("copyright");
        this.license = this.metadata.getString("license");
        this.source = this.metadata.getString("source");

        this.lang = this.metadata.getString("lang");
        this.sitelang = this.metadata.getString("sitelang");
    }

    public String getId() {
        return sha1sum;
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
        return String.format("%s %s/%s(%s)", this.title, this.header.volume, 
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

    Article readArticle(long pointer) throws IOException, JSONException {
        long pos = this.header.articleOffset + pointer;
        this.file.seek(pos);
        long articleLength = this.file.readUnsignedInt();

        byte[] articleBytes = new byte[(int) articleLength];
        this.file.read(articleBytes);
        String serializedArticle = decompress(articleBytes);
        Article a = Article.fromJsonStr(serializedArticle);
        a.dictionary = this;
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

//    try:
//        siteinfo = dictionary.metadata['siteinfo']
//    except KeyError:
//        logging.debug('No site info in dictionary %r', dictionary_key)
//        if 'lang' in dictionary.metadata and 'sitelang' in dictionary.metadata:
//            url = u'http://%s.wikipedia.org/wiki/%s' % (dictionary.metadata['lang'],
//                                                        article_title)
//            return url
//    else:
//        try:
//            general = siteinfo['general']
//            server = general['server']
//            articlepath = general['articlepath']
//        except KeyError:
//            logging.debug('Site info for %s is incomplete', dictionary_key)
//        else:
//            url = ''.join((server, articlepath.replace(u'$1', article_title)))
//            return url
    
    
    public String getArticleURL(String title) {
        try {
            JSONObject siteinfo = this.metadata.getJSONObject("siteinfo");
            JSONObject general = siteinfo.getJSONObject("general");
            String server = general.getString("server");
            String articlePath = general.getString("articlepath");
            return server + articlePath.replace("$1", title); 
        }
        catch (JSONException e) {
            Log.d("aarddict", "Failed to obtain url for title " + title, e);
            return null;
        }        
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
        if (bytes.length == 0)
            return "";
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
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
                new ByteArrayInputStream(bytes));
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
        StringBuilder s = new StringBuilder(this.title);
        if (this.lang != null) {
            s.append(String.format(" (%s)", this.lang));
        }
        else {
            if (this.sitelang != null) {
                s.append(String.format(" (%s)", this.sitelang));
            }
        }
        if (this.header.of > 1) 
               s.append(String.format(" Vol. %s", this.header.volume));        
        return s.toString();
    }
}

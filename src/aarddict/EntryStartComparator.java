/**
 * 
 */
package aarddict;

public final class EntryStartComparator extends EntryComparator {

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
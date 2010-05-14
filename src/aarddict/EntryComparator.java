/**
 * 
 */
package aarddict;

import java.util.Comparator;

import com.ibm.icu.text.Collator;

public class EntryComparator implements Comparator<Entry> {

    Collator collator;

    EntryComparator(int strength) {
        collator = Collator.getInstance(Dictionary.ROOT);
        collator.setStrength(strength);
    }

    @Override
    public int compare(Entry e1, Entry e2) {
        return collator.compare(e1.title, e2.title);
    }
}
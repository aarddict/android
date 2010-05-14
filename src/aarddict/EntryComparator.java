/**
 * 
 */
package aarddict;

import java.util.Comparator;
import java.util.Locale;

import com.ibm.icu.text.Collator;

public class EntryComparator implements Comparator<Entry> {

	final static Locale ROOT_LOCALE = new Locale("", "", "");
	
    Collator collator;

    EntryComparator(int strength) {
        collator = Collator.getInstance(ROOT_LOCALE);
        collator.setStrength(strength);
    }

    @Override
    public int compare(Entry e1, Entry e2) {
        return collator.compare(e1.title, e2.title);
    }
}
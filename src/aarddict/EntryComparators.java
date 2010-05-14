package aarddict;

import java.util.Comparator;

import com.ibm.icu.text.Collator;

public class EntryComparators {
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

}

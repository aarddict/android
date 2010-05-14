package aarddict;

import java.util.Comparator;

import com.ibm.icu.text.Collator;

public class EntryComparators {
	
	final static EntryComparator FULL1 = new EntryComparator(Collator.PRIMARY);
	final static EntryComparator FULL2 = new EntryComparator(Collator.SECONDARY);
	final static EntryComparator FULL3 = new EntryComparator(Collator.TERTIARY);
	final static EntryComparator FULL4 = new EntryComparator(Collator.QUATERNARY);

	final static EntryStartComparator START1 = new EntryStartComparator(Collator.PRIMARY);
	final static EntryStartComparator START2 = new EntryStartComparator(Collator.SECONDARY);
	final static EntryStartComparator START3 = new EntryStartComparator(Collator.TERTIARY);
	final static EntryStartComparator START4 = new EntryStartComparator(Collator.QUATERNARY);
	
	@SuppressWarnings("unchecked")
	public static Comparator<Entry>[] ALL = new Comparator[] {
		FULL4, START4, FULL3, START3, FULL2, START2, FULL1, START1
	};
	
	@SuppressWarnings("unchecked")
	public static Comparator<Entry>[] FULL_WORD = new Comparator[] {
		FULL4, FULL3, FULL2, FULL1
	};	
}

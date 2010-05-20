package aarddict;

import java.util.Comparator;

import com.ibm.icu.text.Collator;

public class EntryComparators {
	
	final static EntryComparator FULL1 = new EntryComparator(Collator.PRIMARY);
	final static EntryComparator FULL2 = new EntryComparator(Collator.SECONDARY);
	final static EntryComparator FULL3 = new EntryComparator(Collator.TERTIARY);

	final static EntryStartComparator START1 = new EntryStartComparator(Collator.PRIMARY);
	final static EntryStartComparator START2 = new EntryStartComparator(Collator.SECONDARY);
	final static EntryStartComparator START3 = new EntryStartComparator(Collator.TERTIARY);
	
	@SuppressWarnings("unchecked")
	public static Comparator<Entry>[] ALL = new Comparator[] {
		FULL3, FULL2, FULL1, START3, START2, START1
	};
	
	@SuppressWarnings("unchecked")
	public static Comparator<Entry>[] ALL_FULL = new Comparator[] {
		FULL3, FULL2, FULL1
	};

	@SuppressWarnings("unchecked")
	public static Comparator<Entry>[] EXACT_IGNORE_CASE = new Comparator[] {
		FULL3, FULL2
	};		
	
	@SuppressWarnings("unchecked")
	public static Comparator<Entry>[] EXACT = new Comparator[] {
		FULL3
	};		
}

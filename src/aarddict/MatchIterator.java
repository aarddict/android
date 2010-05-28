package aarddict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class MatchIterator implements Iterator<Entry> {
	
	public static int MAX_FROM_VOL = 50;
	
    Entry                 next;
    int                   currentVolCount = 0;
    Set<Entry>            seen            = new HashSet<Entry>();
    List<Iterator<Entry>> iterators       = new ArrayList<Iterator<Entry>>();                

    MatchIterator(Iterable<Volume> dictionaries, Comparator<Entry>[] comparators, LookupWord word) {
    	for (Volume vol : dictionaries) {
    		for (Comparator<Entry> c : comparators) {
            	iterators.add(vol.lookup(word, c));
            }
        }
        prepareNext();    	
    }
    
    
    MatchIterator(Comparator<Entry>[] comparators, Iterable<Volume> dictionaries, LookupWord word) {
        for (Comparator<Entry> c : comparators) {
            for (Volume vol : dictionaries) {
            	iterators.add(vol.lookup(word, c));
            }
        }
        prepareNext();    	
    }
    
    private void prepareNext() {
        if (!iterators.isEmpty()) {
            Iterator<Entry> i = iterators.get(0);
            if (i.hasNext() && currentVolCount <= MAX_FROM_VOL) {
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
}

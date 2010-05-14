/**
 * 
 */
package aarddict;

import java.util.Comparator;
import java.util.UUID;

final class PreferredDictionaryComparator implements Comparator<Dictionary> {
	
	private final UUID preferred;
	
	PreferredDictionaryComparator(UUID preferred) {
		this.preferred = preferred;
	}
	
	public int compare(Dictionary d1, Dictionary d2) {
		UUID id1 = d1.getDictionaryId();
		UUID id2 = d2.getDictionaryId();
		if (id1.equals(id2)) {
			if (id1.equals(preferred)) {
				return d1.header.volume - d2.header.volume; 
			}
		}
		else if (id1.equals(preferred)) {
			return -1;
		}
		if (id2.equals(preferred)) {
			return 1;
		}						
		return 0;
	}    	
}
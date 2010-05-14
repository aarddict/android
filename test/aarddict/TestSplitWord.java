package aarddict;

import java.net.URISyntaxException;

import junit.framework.TestCase;

public class TestSplitWord extends TestCase {

	public void testSimpleSplitPlainWord() {
		String[] result = Dictionary.splitWordSimple("abc");
		assertEquals("abc", result[0]);
		assertNull(result[1]);
		assertNull(result[2]);
	}

	public void testSimpleSplitWithSection() {
		String[] result = Dictionary.splitWordSimple("abc#def");
		assertEquals("abc", result[0]);
		assertEquals("def", result[1]);
		assertNull(result[2]);
	}

	public void testSimpleSplitWithNS() {
		String[] result = Dictionary.splitWordSimple("w:abc");
		assertEquals("abc", result[0]);
		assertEquals("w", result[2]);
		assertNull(result[1]);
	}
	
	public void testSimpleSplitWithSectionAndNS() {
		String[] result = Dictionary.splitWordSimple("w:abc#def");
		assertEquals("abc", result[0]);
		assertEquals("def", result[1]);
		assertEquals("w", result[2]);
	}		
	
	public void testURISplitPlainWord() throws URISyntaxException {
		String[] result = Dictionary.splitWordAsURI("abc");
		assertEquals("abc", result[0]);
		assertNull(result[1]);
		assertNull(result[2]);
	}
	
	public void testURISplitWithSection() throws URISyntaxException {
		String[] result = Dictionary.splitWordAsURI("abc#def");
		assertEquals("abc", result[0]);
		assertEquals("def", result[1]);
		assertNull(result[2]);
	}

	public void testURISplitWithNS() throws URISyntaxException {
		String[] result = Dictionary.splitWordAsURI("w:abc");
		assertEquals("abc", result[0]);
		assertEquals("w", result[2]);
		assertNull(result[1]);
	}
	
	public void testURISplitWithSectionAndNS() throws URISyntaxException {
		String[] result = Dictionary.splitWordAsURI("w:abc#def");
		assertEquals("abc", result[0]);
		assertEquals("def", result[1]);
		assertEquals("w", result[2]);
	}		

	public void testURISplitWithURLEncoding() throws URISyntaxException {
		String[] result = Dictionary.splitWordAsURI("w:abc%20123%2F456#def%20ghi");
		assertEquals("abc 123/456", result[0]);
		assertEquals("def ghi", result[1]);
		assertEquals("w", result[2]);
	}		

	public void testURISplitWithBadURLEncoding(){
		try {
			Dictionary.splitWordAsURI("w:ab c");
		} catch (URISyntaxException e) {
			return;
		}
		fail();
	}			
}

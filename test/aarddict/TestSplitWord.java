/* This file is part of Aard Dictionary for Android <http://aarddict.org>.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License <http://www.gnu.org/licenses/gpl-3.0.txt>
 * for more details.
 * 
 * Copyright (C) 2010 Igor Tkach
*/

package aarddict;

import java.net.URISyntaxException;

import junit.framework.TestCase;

public class TestSplitWord extends TestCase {

	public void testSimpleSplitPlainWord() {
		LookupWord result = LookupWord.splitWordSimple("abc");
		assertEquals("abc", result.word);
		assertNull(result.section);
		assertNull(result.nameSpace);
	}

	public void testSimpleSplitWithSection() {
		LookupWord result = LookupWord.splitWordSimple("abc#def");
		assertEquals("abc", result.word);
		assertEquals("def", result.section);
		assertNull(result.nameSpace);
	}

	public void testSimpleSplitWithNS() {
		LookupWord result = LookupWord.splitWordSimple("w:abc");
		assertEquals("abc", result.word);
		assertEquals("w", result.nameSpace);
		assertNull(result.section);
	}
	
	public void testSimpleSplitWithSectionAndNS() {
		LookupWord result = LookupWord.splitWordSimple("w:abc#def");
		assertEquals("abc", result.word);
		assertEquals("def", result.section);
		assertEquals("w", result.nameSpace);
	}		
	
	public void testSimpleSplitWithUnderscore() throws URISyntaxException {
		LookupWord result = LookupWord.splitWordSimple("w_1:a_b#c_d");
		assertEquals("a b", result.word);
		assertEquals("c_d", result.section);
		assertEquals("w_1", result.nameSpace);
	}			
	
	
	public void testURISplitPlainWord() throws URISyntaxException {
		LookupWord result = LookupWord.splitWordAsURI("abc");
		assertEquals("abc", result.word);
		assertNull(result.section);
		assertNull(result.nameSpace);
	}
	
	public void testURISplitWithSection() throws URISyntaxException {
		LookupWord result = LookupWord.splitWordAsURI("abc#def");
		assertEquals("abc", result.word);
		assertEquals("def", result.section);
		assertNull(result.nameSpace);
	}

	public void testURISplitWithNS() throws URISyntaxException {
		LookupWord result = LookupWord.splitWordAsURI("w:abc");
		assertEquals("abc", result.word);
		assertEquals("w", result.nameSpace);
		assertNull(result.section);
	}
	
	public void testURISplitWithSectionAndNS() throws URISyntaxException {
		LookupWord result = LookupWord.splitWordAsURI("w:abc#def");
		assertEquals("abc", result.word);
		assertEquals("def", result.section);
		assertEquals("w", result.nameSpace);
	}		

	public void testURISplitWithURLEncoding() throws URISyntaxException {
		LookupWord result = LookupWord.splitWordAsURI("w:abc%20123%2F456#def%20ghi");
		assertEquals("abc 123/456", result.word);
		assertEquals("def ghi", result.section);
		assertEquals("w", result.nameSpace);
	}		

	public void testURISplitWithBadURLEncoding(){
		try {
			LookupWord.splitWordAsURI("w:ab c");
		} catch (URISyntaxException e) {
			return;
		}
		fail();
	}			
	
	public void testURISplitWithUnderscore() throws URISyntaxException {
		LookupWord result = LookupWord.splitWordAsURI("w:a_b#c_d");
		assertEquals("a b", result.word);
		assertEquals("c_d", result.section);
		assertEquals("w", result.nameSpace);
	}			
}

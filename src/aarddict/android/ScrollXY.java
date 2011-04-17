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
 * Copyright (C) 2011 Igor Tkach
*/

package aarddict.android;

import java.io.Serializable;

public class ScrollXY implements Serializable {
	int x;
	int y;
	
	public ScrollXY() {
	}
	
	public ScrollXY(int x, int y) {
		this.x = x;
		this.y = y;
	}	
	
	@Override
	public String toString() {
		return String.format("ScrollXY(%d, %d)", x, y);
	}
}

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

package aarddict.android;

import android.app.ProgressDialog;
import android.content.Context;

final class DiscoveryProgressDialog extends ProgressDialog {        
    DiscoveryProgressDialog(Context context) {
        super(context);
        setCancelable(false);
        setIndeterminate(true);
        setProgressStyle(ProgressDialog.STYLE_SPINNER);
        setMessage(context.getString(R.string.msgLooking));            
    }        
}
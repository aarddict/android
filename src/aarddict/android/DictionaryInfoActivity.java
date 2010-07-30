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

import aarddict.Metadata;
import aarddict.Volume;
import android.content.Intent;
import android.text.util.Linkify;
import android.view.View;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.TabHost.TabContentFactory;

public final class DictionaryInfoActivity extends BaseDictionaryActivity implements TabContentFactory {
	
    private TabHost tabs;
    
	@Override
	void initUI() {
        setContentView(R.layout.dict_info);
        tabs = (TabHost)findViewById(android.R.id.tabhost);
        tabs.setup();        
        setTitle(R.string.titleDictionaryInfoActivity);        
	}	
	
	@Override
	void onDictionaryServiceReady() {
		tabs.addTab(tabs.newTabSpec("d").setIndicator(getString(R.string.tabDescription)).setContent(this));
		tabs.addTab(tabs.newTabSpec("c").setIndicator(getString(R.string.tabCopyright)).setContent(this));
		tabs.addTab(tabs.newTabSpec("s").setIndicator(getString(R.string.tabSource)).setContent(this));
		tabs.addTab(tabs.newTabSpec("l").setIndicator(getString(R.string.tabLicense)).setContent(this));
	}

	public View createTabContent(String tag) {
		Intent intent = getIntent();
		String volumeId = intent.getStringExtra("volumeId");
		Volume d = dictionaryService.getVolume(volumeId);
		setTitle(new StringBuilder(d.getDisplayTitle(false)).append(" ").append(d.metadata.version));
		Metadata m = d.metadata;
		TextView textView = new TextView(this);
		textView.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
		CharSequence text = "";
		if (tag.equals("d")) {	
			text = m.description;
		}
		else 
		if (tag.equals("c")) {	
			text = m.copyright;
		}
		else
		if (tag.equals("s")) {	
			text = m.source;
		}
		else
		if (tag.equals("l")) {	
			text = m.license;
			textView.setHorizontallyScrolling(true);
		}		
		textView.setText(text);
		return textView;
	}
}

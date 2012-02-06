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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import aarddict.Entry;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TwoLineListItem;

public class LookupActivity extends BaseDictionaryActivity {
    
    private final static String TAG     = LookupActivity.class.getName();
    
    private Timer          	timer;
    private ListView       	listView;
    private Iterator<Entry> empty = new ArrayList<Entry>().iterator();
    
    void updateTitle() {
    	int dictCount = dictionaryService.getVolumes().size();
    	Resources r = getResources();
		String dictionaries = r.getQuantityString(R.plurals.dictionaries, dictCount);
    	String appName = r.getString(R.string.appName);
    	String mainTitle = r.getString(R.string.titleLookupActivity, appName, String.format(dictionaries, dictCount));
    	setTitle(mainTitle);    	
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
    }
        
    private void updateWordListUI(final Iterator<Entry> results) {        
        runOnUiThread(new Runnable() {
            public void run() {
                TextView messageView = (TextView)findViewById(R.id.messageView);
                if (!results.hasNext()) {                	
                    Editable text = editText.getText();
                    if (text != null && !text.toString().equals("")) {                    	
                    	messageView.setText(Html.fromHtml(getString(R.string.nothingFound)));
                    	messageView.setVisibility(View.VISIBLE);
                    }
                    else {
                    	messageView.setVisibility(View.GONE);
                    }
                }
                else {
                	messageView.setVisibility(View.GONE);
                }
                WordAdapter wordAdapter = new WordAdapter(results);
                listView.setAdapter(wordAdapter);        
                listView.setOnItemClickListener(wordAdapter);                
                setProgressBarIndeterminateVisibility(false);
            }
        });                
    }    

    final Runnable updateProgress = new Runnable() {
        public void run() {
            setProgressBarIndeterminateVisibility(true);
        }
    };        
    
    private void doLookup(CharSequence word) {
        if (dictionaryService == null)
            return;
        word = trimLeft(word.toString());
        if (word.equals("")) {
        	Log.d(TAG, "Nothing to look up");
        	updateWordListUI(empty);
        	return;
        }
        runOnUiThread(updateProgress);
        long t0 = System.currentTimeMillis();
        try {
            Iterator<Entry> results = dictionaryService.lookup(word);
            Log.d(TAG, "Looked up " + word + " in "
                    + (System.currentTimeMillis() - t0));
            updateWordListUI(results);
        } catch (Exception e) {
            StringBuilder msgBuilder = new StringBuilder(
                    "There was an error while looking up ").append("\"")
                    .append(word).append("\"");
            if (e.getMessage() != null) {
                msgBuilder.append(": ").append(e.getMessage());
            }
            final String msg = msgBuilder.toString();
            Log.e(TAG, msg, e);
        }
    }
    
    private void launchWord(Entry theWord) {
        Intent next = new Intent();
        next.setClass(this, ArticleViewActivity.class);
        next.putExtra("word", theWord.title);        
        next.putExtra("section", theWord.section);
        next.putExtra("volumeId", theWord.volumeId);
        next.putExtra("articlePointer", theWord.articlePointer);
        startActivity(next);
    }

    
    final class WordAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

        private final List<Entry>    words;
        private final LayoutInflater mInflater;
        private int                  itemCount;
        private Iterator<Entry>      results;
        private boolean              displayMore;

        public WordAdapter(Iterator<Entry> results) {
            this.results = results;                        
            this.words = new ArrayList<Entry>();                        
            loadBatch();
            mInflater = (LayoutInflater) LookupActivity.this.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
        }

        private void loadBatch() {
            int count = 0;                
            while (results.hasNext() && count < 20) {
                count++;
                words.add(results.next());
            }                                        
            displayMore = results.hasNext();
            itemCount = words.size();              
        }
        
        public int getCount() {
            return itemCount;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
            if (displayMore && position == itemCount - 1) {
            	loadMore(position);
            }            
            TwoLineListItem view = (convertView != null) ? (TwoLineListItem) convertView :
                    createView(parent);            
            bindView(view, words.get(position));
            return view;
        }
        
        private int loadingMoreForPos;

        private void loadMore(int forPos) {
        	if (loadingMoreForPos == forPos) {
        		return;
        	}
        	loadingMoreForPos = forPos;
        	new Thread(new Runnable() {				
        		public void run() {
        			loadBatch();
        			runOnUiThread(new Runnable() {						
        				public void run() {
        					notifyDataSetChanged();
        				}
        			});
        		}
        	}).start();        	        	
        }        
        
        private TwoLineListItem createView(ViewGroup parent) {
        	TwoLineListItem item;
        	if (DeviceInfo.EINK_SCREEN)
        		item = (TwoLineListItem) mInflater.inflate(
                        R.layout.eink_simple_list_item_2, parent, false);
        	else
                item = (TwoLineListItem) mInflater.inflate(
                        android.R.layout.simple_list_item_2, parent, false);
            item.getText2().setSingleLine();
            item.getText2().setEllipsize(TextUtils.TruncateAt.END);
            return item;
        }

        private void bindView(TwoLineListItem view, Entry word) {
            view.getText1().setText(word.title);
            view.getText2().setText(dictionaryService.getDisplayTitle(word.volumeId));
        }

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        	launchWord(words.get(position));
        }
    }
        
    
    final static int MENU_DICT_INFO = 1;
    final static int MENU_ABOUT = 2;
    final static int MENU_DICT_REFRESH = 3;    
    private EditText editText;

    private TextWatcher textWatcher;
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_DICT_INFO, 0, R.string.mnInfo).setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_ABOUT, 0, R.string.mnAbout).setIcon(R.drawable.ic_menu_aarddict);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_DICT_INFO:
            startActivity(new Intent(this, DictionariesActivity.class));        	
            break;
        case MENU_ABOUT:
            showAbout();
            break;            
        }
        return true;
    }

	private void showAbout() {        
        PackageManager manager = getPackageManager();
        String versionName = "";
        try {
			PackageInfo info = manager.getPackageInfo(getPackageName(), 0);
			versionName = info.versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Failed to load package info for " + getPackageName(), e) ;
		}        
		
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, 
                    LinearLayout.LayoutParams.FILL_PARENT, 1));
        layout.setPadding(10, 10, 10, 10);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.aarddict);
        logo.setPadding(0, 0, 20, 0);
        logo.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT));
        TextView textView = new TextView(this);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setLineSpacing(2f, 1);
        textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT));
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setText(Html.fromHtml(getString(R.string.about, getString(R.string.appName), versionName)));

        ImageView btnFlattr = new ImageView(this);
        btnFlattr.setImageResource(R.drawable.flattr);
        btnFlattr.setPadding(5, 5, 5, 5);
        btnFlattr.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, 
        												ViewGroup.LayoutParams.WRAP_CONTENT));
        btnFlattr.setOnClickListener(new View.OnClickListener() {			
			public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
                        Uri.parse(getString(R.string.flattrUrl))); 
                startActivity(browserIntent);
			}
		});
        
        LinearLayout textViewLayout = new LinearLayout(this);
        textViewLayout.setOrientation(LinearLayout.VERTICAL);
        textViewLayout.setPadding(0, 0, 0, 10);
        textViewLayout.addView(textView);      
        textViewLayout.addView(btnFlattr);
        
        layout.addView(logo);
        layout.addView(textViewLayout);
        
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.titleAbout).setView(layout).setNeutralButton(R.string.btnDismiss, new OnClickListener() {            
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialogBuilder.show();
	}
	
    @Override
    void onDictionaryServiceReady() {    	
    	updateTitle();
    	Intent intent = getIntent();
        if (intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_SEARCH)) {
            final String word = intent.getStringExtra("query");
            editText.setText(word);

            try {
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.d(TAG, "running lookup task for " + word + " in " + Thread.currentThread());
                        doLookup(word);
                    }
                }, 0);
            }
            catch(IllegalStateException e) {
                Log.e(TAG, "Failed to schedule lookup task", e);
            }
        }
        else {            
            textWatcher.afterTextChanged(editText.getText());
        }
    }
    
    @Override
    void onDictionaryOpenFinished() {
    	onDictionaryServiceReady();
    }
        
    @Override
    void initUI() {
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        if (DeviceInfo.EINK_SCREEN)
        {
        	setContentView(R.layout.eink_lookup);
            listView = (ListView)findViewById(R.id.einkLookupResult);
        }
        else
        {
        	setContentView(R.layout.lookup);
            listView = (ListView)findViewById(R.id.lookupResult);
        }
        
        timer = new Timer();
                                                             
        editText = (EditText)findViewById(R.id.wordInput);
                        
        textWatcher = new TextWatcher() {
            
            TimerTask currentLookupTask;
            
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
            }
            
            public void afterTextChanged(Editable s) {
                if (currentLookupTask != null) {
                    currentLookupTask.cancel();
                }                                        
                                
                final Editable textToLookup = s;                 
                currentLookupTask = new TimerTask() {                    
                    @Override
                    public void run() {
                        Log.d(TAG, "running lookup task for " + textToLookup + " in " + Thread.currentThread());
                        if (textToLookup.equals(editText.getText())) {
                            doLookup(textToLookup);
                        }
                    }
                };
                try {
                	timer.schedule(currentLookupTask, 600);                	
                }
                catch(IllegalStateException e) {
                	//this may happen if orientation changes while loading                	
                	Log.d(TAG, "Failed to schedule lookup task", e);
                }
            }
        };
        editText.addTextChangedListener(textWatcher);
        
        editText.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
            	 if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
            		 InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            		 inputManager.hideSoftInputFromWindow(editText.getApplicationWindowToken(), 0);
            		 return true;
            	 }
            	 return false;
            }
        });        

        editText.setInputType(InputType.TYPE_CLASS_TEXT);
                
        Button btnClear = (Button)findViewById(R.id.clearButton);
        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                editText.setText("");
                editText.requestFocus();
                InputMethodManager inputMgr = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMgr.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
        });  
        
    }
    
    static String trimLeft(String s) {
        return s.replaceAll("^\\s+", "");
    }
}

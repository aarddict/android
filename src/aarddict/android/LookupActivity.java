package aarddict.android;

import java.io.File;
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
import android.text.util.Linkify;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TwoLineListItem;

public final class LookupActivity extends BaseDictionaryActivity {
    
    private final static String TAG     = LookupActivity.class.getName();
    
    private Timer          timer;
    private ListView       listView;
    private MessageAdapter msgGetDicts;
    private MessageAdapter msgNothingFound;
    private MessageAdapter msgNoDicts;
    
    void updateTitle() {
    	int dictCount = dictionaryService.getVolumes().size();
    	Resources r = getResources();
		String dictionaries = r.getQuantityString(R.plurals.dictionaries, dictCount);
    	String appName = r.getString(R.string.app_name);
    	String mainTitle = r.getString(R.string.main_title);
    	setTitle(String.format(mainTitle, appName, String.format(dictionaries, dictCount)));
    	if (dictCount == 0) {
    	    listView.setAdapter(msgNoDicts);
    	}
    	else {
    	    listView.setAdapter(msgGetDicts);
    	}
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
    }
    
    private void updateWordListUI(final Iterator<Entry> results) {        
        runOnUiThread(new Runnable() {
            public void run() {                              
                if (!results.hasNext()) {
                    Editable text = editText.getText();
                    if (text != null && !text.toString().equals("")) {
                        listView.setAdapter(msgNothingFound);
                    }
                    else {
                        if (dictionaryService.getDictionaries().isEmpty()) {
                            listView.setAdapter(msgNoDicts);
                        } else {
                            listView.setAdapter(msgGetDicts);
                        }
                    }                
                }
                else {
                    WordAdapter wordAdapter = new WordAdapter(results);
                    listView.setAdapter(wordAdapter);        
                    listView.setOnItemClickListener(wordAdapter);
                }
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
        next.putExtra("volumeId", theWord.getVolumeId());
        next.putExtra("articlePointer", theWord.articlePointer);
        startActivity(next);
    }
    
    
    final static class MessageAdapter extends BaseAdapter {

        private LinearLayout layout;
        private TextView     textView;
                               
        public MessageAdapter(Context context, int resId) {
            layout = new LinearLayout(context);
            
            textView = new TextView(context);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setLineSpacing(2f, 1);
            textView.setMovementMethod(LinkMovementMethod.getInstance());            
            textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
            textView.setText(Html.fromHtml(context.getString(resId)));
            layout.setGravity(Gravity.CENTER);
            layout.addView(textView);                                                
        }
                
        public int getCount() {
            return 1;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
                return layout;
        }
    }
    
    final class WordAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

        private final List<Entry>    words;
        private final LayoutInflater mInflater;
        private int                  itemCount;
        private LinearLayout         more;
        private Iterator<Entry>      results;
        private boolean              displayMore;

        public WordAdapter(Iterator<Entry> results) {
            this.results = results;                        
            this.words = new ArrayList<Entry>();                        
            loadBatch();
            mInflater = (LayoutInflater) LookupActivity.this.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);

            more = new LinearLayout(mInflater.getContext());
            ImageView moreImage = new ImageView(mInflater.getContext());
            moreImage.setImageResource(android.R.drawable.ic_menu_more);
            more.setGravity(Gravity.CENTER);
            more.addView(moreImage);            
        }

        private void loadBatch() {
            int count = 0;                
            while (results.hasNext() && count < 20) {
                count++;
                words.add(results.next());
            }                                        
            displayMore = results.hasNext();
            itemCount = displayMore  ? words.size() + 1 : words.size();              
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

        @Override
        public int getViewTypeCount() {
            return displayMore ? 2 : 1;
        }
        
        @Override
        public int getItemViewType(int position) {
            if (displayMore)
                return position == itemCount - 1 ? 1 : 0;
            else
                return 0;
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
            if (displayMore && position == itemCount - 1) {
                return more;
            }
            TwoLineListItem view = (convertView != null) ? (TwoLineListItem) convertView :
                    createView(parent);            
            bindView(view, words.get(position));
            return view;
        }

        private TwoLineListItem createView(ViewGroup parent) {
            TwoLineListItem item = (TwoLineListItem) mInflater.inflate(
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
            if (displayMore && position == itemCount - 1) {
                loadBatch();
                notifyDataSetChanged();
            }            
            else {
                launchWord(words.get(position));
            }
        }
    }
    
    
    final static int MENU_DICT_INFO = 1;
    final static int MENU_ABOUT = 2;
    final static int MENU_DICT_REFRESH = 3;    
    private EditText editText;
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_DICT_REFRESH, 0, "Refresh").setIcon(R.drawable.ic_menu_refresh);
        menu.add(0, MENU_DICT_INFO, 0, "Info").setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_ABOUT, 0, "About").setIcon(R.drawable.ic_menu_aarddict);
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
        case MENU_DICT_REFRESH:
            if (dictionaryService != null)
                dictionaryService.refresh();
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
		StringBuilder message = new StringBuilder().
		append(getString(R.string.app_name)).
        append(" ").
        append(versionName).
        append("\n").
        append("(C) 2010 Igor Tkach").append("\n").append("http://aarddict.org");
		
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
        textView.setAutoLinkMask(Linkify.WEB_URLS);
        textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT));
        textView.setText(message);
        
        layout.addView(logo);
        layout.addView(textView);
		
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle("About").setView(layout).setNeutralButton("Dismiss", new OnClickListener() {            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialogBuilder.show();
	}

    @Override
    void onDictionaryServiceReady() {
        updateTitle();
        final Intent intent = getIntent();
        if (intent.getAction().equals(Intent.ACTION_VIEW)) {
            final Uri data = intent.getData();
            Log.d(TAG, "Path: " + data.getPath());              
            if (data != null && data.getPath() != null) {
                Runnable r = new Runnable() {                   
                    @Override
                    public void run() {
                        Log.d(TAG, "opening: " + data.getPath());
                        dictionaryService.open(new File(data.getPath()));                       
                    }
                };
                new Thread(r).start();                  
                Log.d(TAG, "started: " + data.getPath());
            }
        }        
    }

    @Override
    void onDictionaryOpenFinished() {
        updateTitle();        
    }

    @Override
    void initUI() {
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        setContentView(R.layout.lookup);
        
        timer = new Timer();
                                             
        msgGetDicts = new MessageAdapter(this, R.string.getMoreDictionaries);
        msgNoDicts = new MessageAdapter(this, R.string.welcome);
        msgNothingFound = new MessageAdapter(this, R.string.nothingFound);
                
        listView = (ListView)findViewById(R.id.lookupResult);
        
        listView.setAdapter(msgGetDicts);
        
        editText = (EditText)findViewById(R.id.wordInput);
        editText.addTextChangedListener(new TextWatcher() {
            
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
                timer.schedule(currentLookupTask, 600);
            }
        });
        
        editText.setHint("Start typing");
        editText.setInputType(InputType.TYPE_CLASS_TEXT | 
                InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        editText.setSelectAllOnFocus(true);
                
        ImageButton btnClear = (ImageButton)findViewById(R.id.clearButton);
        
        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                editText.setText("");
                editText.requestFocus();
                InputMethodManager inputMgr = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMgr.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
        });        
    }    
}

package aarddict.android;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import aarddict.Dictionary;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.TwoLineListItem;

public class LookupActivity extends Activity {
    
    final static String TAG     = "aarddict.LookupActivity";
    Timer               timer;
    ListView            listView;
    final Handler       handler = new Handler();
        
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        timer = new Timer();
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, 
                    LinearLayout.LayoutParams.FILL_PARENT, 1));
                     
        listView = new ListView(this);
        EditText editText = new EditText(this){
            
            TimerTask currentLookupTask;
            
            @Override
            public boolean onKeyUp(int keyCode, KeyEvent event) {
                if (currentLookupTask != null) {
                    currentLookupTask.cancel();
                }
                
                final CharSequence textToLookup = getText(); 
                
                currentLookupTask = new TimerTask() {                    
                    @Override
                    public void run() {
                        Log.d(TAG, "running lookup task for " + textToLookup + " in " + Thread.currentThread());
                        if (textToLookup.equals(getText())) {
                            doLookup(textToLookup);
                        }
                    }
                };                 
                timer.schedule(currentLookupTask, 600);                
                return true;
            }
        };
        editText.setHint("Start typing");
        editText.setInputType(InputType.TYPE_CLASS_TEXT | 
                              InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE |
                              InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | 
                              InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
                        
      layout.addView(editText);                        
        layout.addView(listView);        
        setContentView(layout);
        openDictionaries();
    }
    
    void openDictionaries() {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage("Looking for dictionaries");
        Runnable open = new Runnable() {            
            @Override
            public void run() {
                while (!progressDialog.isShowing()) {
                    Thread.yield();
                    try {
                        Thread.sleep(10);
                    }
                    catch (InterruptedException e) {
                        Log.e(TAG, "Sombody interrupted my slumber", e);
                    }
                }
                Dictionaries.getInstance().discover();
                handler.post(new Runnable() {                    
                    @Override
                    public void run() {
                        progressDialog.cancel();
                    }
                });
            }
        };
        Thread t = new Thread(open);
        t.start();
        progressDialog.show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
        Dictionaries.getInstance().close();
    }
    
    private void updateWordListUI(WordAdapter wordAdapter) {
        Log.d(TAG, "updating word list in " + Thread.currentThread());        
        listView.setAdapter(wordAdapter);
        listView.setOnItemClickListener(wordAdapter);
    }    
    
    private void doLookup(CharSequence word) {        
        Iterator<Dictionary.Entry> results = Dictionaries.getInstance().lookup(word);
        List<Dictionary.Entry> words = new ArrayList<Dictionary.Entry>();
        int count = 0;                
        while (results.hasNext() && count < 20) {
            count++;
            words.add(results.next());
        }                
        final WordAdapter wordAdapter = new WordAdapter(words);
        final Runnable updateWordList = new Runnable() {            
            @Override
            public void run() {
                updateWordListUI(wordAdapter);
            }

        };        
        handler.post(updateWordList);
    }
    
    private void launchWord(Dictionary.Entry theWord) {
        Intent next = new Intent();
        next.setClass(this, ArticleViewActivity.class);                       
        next.putExtra("word", theWord.title);        
        next.putExtra("dictionaryId", theWord.dictionary.getId());
        next.putExtra("articlePointer", theWord.articlePointer);
        startActivity(next);
    }
    
    
    class WordAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

        private final List<Dictionary.Entry> words;
        private final LayoutInflater mInflater;

        public WordAdapter(List<Dictionary.Entry> words) {
            this.words = words;
            mInflater = (LayoutInflater) LookupActivity.this.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
        }

        public int getCount() {
            return words.size();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
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

        private void bindView(TwoLineListItem view, Dictionary.Entry word) {
            view.getText1().setText(word.title);
            view.getText2().setText(word.dictionary.getDisplayTitle());
        }

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            launchWord(words.get(position));
        }
    }
    
}

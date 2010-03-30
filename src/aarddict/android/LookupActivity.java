package aarddict.android;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import aarddict.Dictionary;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
import android.widget.TextView;
import android.widget.TwoLineListItem;
import android.widget.TextView.OnEditorActionListener;

public class LookupActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                
        Dictionaries.getInstance().discover();
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, 
                    LinearLayout.LayoutParams.FILL_PARENT, 1));
        
        final ListView listView = new ListView(this);
        
        EditText editText = new EditText(this);
        editText.setHint("Type word");
        editText.setInputType(InputType.TYPE_CLASS_TEXT | 
                              InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE |
                              InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | 
                              InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);         
        editText.setOnEditorActionListener(new OnEditorActionListener() {            
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                CharSequence text = v.getText();
                Iterator<Dictionary.Entry> results = Dictionaries.getInstance().lookup(text);
                List<Dictionary.Entry> words = new ArrayList<Dictionary.Entry>();
                int count = 0;                
                while (results.hasNext() && count < 20) {
                    count++;
                    words.add(results.next());
                }                
                WordAdapter wordAdapter = new WordAdapter(words);
                listView.setAdapter(wordAdapter);
                listView.setOnItemClickListener(wordAdapter);
                return true;
            }
        });
        
        layout.addView(editText);                        
        layout.addView(listView);
        
        setContentView(layout);
    }
    
    private void launchWord(Dictionary.Entry theWord) {
        Intent next = new Intent();
        next.setClass(this, ArticleViewActivity.class);
                        
        next.putExtra("word", theWord.title);        
        try {            
            Dictionary.Article a = theWord.getArticle();            
            if (a.getRedirect() != null) {
                    a = Dictionaries.getInstance().redirect(a);
            }                        
            next.putExtra("definition", a.text);                                    
        }
        catch (Exception e) {
            Log.e("aarddict.lookup", "Failed to read article " + theWord, e);
            next.putExtra("definition", Log.getStackTraceString(e));
        }
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

package aarddict.android;

import aarddict.Dictionary;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class DictionariesActivity extends Activity {

	private final static String TAG = DictionariesActivity.class.getName();
	
	ListView listView;
    DictionaryService 	dictionaryService;    
    ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	dictionaryService = ((DictionaryService.LocalBinder)service).getService();
        	Log.d(TAG, "Service connected: " + dictionaryService);
        	init();
        }

        public void onServiceDisconnected(ComponentName className) {
        	Log.d(TAG, "Service disconnected: " + dictionaryService);
        	dictionaryService = null;
            Toast.makeText(DictionariesActivity.this, "Dictionary service disconnected, quitting...",
                    Toast.LENGTH_LONG).show();
            DictionariesActivity.this.finish();
        }
    };    

    private void init() {
    	listView.setAdapter(new DictListAdapter(dictionaryService.getDictionaries()));
    }
    
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().requestFeature(Window.FEATURE_LEFT_ICON);
        
        listView = new ListView(this);
        
        setContentView(listView);
        
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.aarddict);
        
        
        Intent dictServiceIntent = new Intent(this, DictionaryService.class);                        
        bindService(dictServiceIntent, connection, 0);
    }
    
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }

    class DictListAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

    	Dictionary.Collection dicts;    	

        public DictListAdapter(Dictionary.Collection dicts) {
        	this.dicts = dicts;
        }
        
        public int getCount() {
            return dicts.size();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }
        
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
        	TextView renderer;
        	CharSequence title = dicts.get(position).getDisplayTitle();
        	if (convertView == null) {
        		renderer = new TextView(DictionariesActivity.this);
        		renderer.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.FILL_PARENT, 
        				ListView.LayoutParams.FILL_PARENT));
        		renderer.setPadding(10, 10, 10, 10);
        		renderer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);        		        		
        	} else {
        		renderer = (TextView)convertView; 
        	}        	
        	renderer.setText(title);
        	return renderer;
        }
    }
    
    
}

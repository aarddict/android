package aarddict.android;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import aarddict.Dictionary;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

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
    	listView.setAdapter(new DictListAdapter(dictionaryService.getVolumes()));    	    
    }
    
    
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().requestFeature(Window.FEATURE_LEFT_ICON);
        
        listView = new ListView(this);
        
        setContentView(listView);
        setTitle("Dictionaries");        
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.aarddict);
                
        Intent dictServiceIntent = new Intent(this, DictionaryService.class);                        
        bindService(dictServiceIntent, connection, 0);
    }
    
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }

    class DictListAdapter extends BaseAdapter 
    		implements AdapterView.OnItemClickListener,
    		AdapterView.OnItemLongClickListener
    
    {

		LayoutInflater inflater;    	
		List<List<Dictionary>> volumes;

        public DictListAdapter(Map<UUID, List<Dictionary>> volumes) {
			this.volumes = new ArrayList();
			this.volumes.addAll(volumes.values());
            inflater = (LayoutInflater) getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);        	
        }
        
        public int getCount() {
            return volumes.size();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }
        
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        }

		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			return false;
		}
        
        public View getView(int position, View convertView, ViewGroup parent) {
        	List<Dictionary> allDictVols = volumes.get(position);
        	int volCount = allDictVols.size();
        	Dictionary d = allDictVols.get(0);
        	
            TwoLineListItem view = (convertView != null) ? (TwoLineListItem) convertView :
                createView(parent);
                        
            view.getText1().setText(new StringBuilder(d.getDisplayTitle(false)).append(" ").append(d.metadata.version));
            
            Resources r = getResources();
			String articleStr = r.getQuantityString(R.plurals.articles, d.metadata.article_count, d.metadata.article_count);            
            String totalVolumesStr = r.getQuantityString(R.plurals.volumes, d.header.of, d.header.of);
            String volumesStr = r.getQuantityString(R.plurals.volumes, volCount, volCount);
            String shortInfo = r.getString(R.string.short_dict_info, articleStr, totalVolumesStr, volumesStr);
            view.getText2().setText(shortInfo);
        	return view;
        }
        
        private TwoLineListItem createView(ViewGroup parent) {
            TwoLineListItem item = (TwoLineListItem) inflater.inflate(
                    android.R.layout.simple_list_item_2, parent, false);
            return item;
        }
        

    }    
}

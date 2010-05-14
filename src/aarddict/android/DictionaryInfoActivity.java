package aarddict.android;

import aarddict.Dictionary;
import aarddict.Metadata;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TabHost.TabContentFactory;

public class DictionaryInfoActivity extends Activity implements TabContentFactory {

	private final static String TAG = DictionaryInfoActivity.class.getName();
	
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
            Toast.makeText(DictionaryInfoActivity.this, "Dictionary service disconnected, quitting...",
                    Toast.LENGTH_LONG).show();
            DictionaryInfoActivity.this.finish();
        }
    };    	
	
    TabHost tabs;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.dict_info);
        tabs = (TabHost)findViewById(android.R.id.tabhost);
        tabs.setup();        
        setTitle("Dictionary Info");        
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.aarddict);
                
        Intent dictServiceIntent = new Intent(this, DictionaryService.class);                        
        bindService(dictServiceIntent, connection, 0);		
	}	
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(connection);
	}
	
	private void init() {
		tabs.addTab(tabs.newTabSpec("d").setIndicator("Description").setContent(this));
		tabs.addTab(tabs.newTabSpec("c").setIndicator("Copyright").setContent(this));
		tabs.addTab(tabs.newTabSpec("s").setIndicator("Source").setContent(this));
		tabs.addTab(tabs.newTabSpec("l").setIndicator("License").setContent(this));
	}

	@Override
	public View createTabContent(String tag) {
		Intent intent = getIntent();
		String volumeId = intent.getStringExtra("volumeId");
		Dictionary d = dictionaryService.getDictionary(volumeId);
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

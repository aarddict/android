package aarddict.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import aarddict.Article;
import aarddict.Entry;
import aarddict.RedirectNotFound;
import aarddict.RedirectTooManyLevels;
import aarddict.Volume;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;


public class ArticleViewActivity extends Activity {

    private final static String TAG = "aarddict.ArticleViewActivity";
    private WebView articleView;
    private String sharedCSS;
    private String mediawikiSharedCSS;
    private String mediawikiMonobookCSS;
    private String js;
        
    private List<Article> backItems; 
    private List<Article> forwardItems;
    
    DictionaryService 	dictionaryService;
    ServiceConnection 	connection;
    
    Timer               timer;
    TimerTask 			currentTask;
    Iterator<Entry>     currentIterator;
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadAssets();

        timer = new Timer();
        
        backItems = new LinkedList<Article>();
        forwardItems = new LinkedList<Article>();
        
        getWindow().requestFeature(Window.FEATURE_PROGRESS);        
        getWindow().requestFeature(Window.FEATURE_LEFT_ICON);
        
        articleView = new WebView(this) {
        };
        
        articleView.getSettings().setBuiltInZoomControls(true);
        articleView.getSettings().setJavaScriptEnabled(true);
        
        articleView.addJavascriptInterface(new SectionMatcher(), "matcher");
        
        articleView.setWebChromeClient(new WebChromeClient(){
//            @Override
            public void onConsoleMessage(String message, int lineNumber, String sourceID) {
                Log.d(TAG + ".js", String.format("%d [%s]: %s", lineNumber, sourceID, message));
            }
            
            @Override
            public boolean onJsAlert(WebView view, String url, String message,
            		JsResult result) {            	
            	Log.d(TAG + ".js", String.format("[%s]: %s", url, message));
            	result.cancel();
            	return true;
            }
            
            public void onProgressChanged(WebView view, int newProgress) {
                Log.d(TAG, "Progress: " + newProgress);
                setProgress(5000 + newProgress * 50);                
            }
        });
                       
        articleView.setWebViewClient(new WebViewClient() {
                    	        	
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page finished: " + url);
                currentTask = null;
                String section = null;
                
                if (url.contains("#")) {
                    String[] parts = url.split("#", 2);
                    section = parts[1];
                    if (backItems.size() > 0) {
                        Article current = backItems.get(backItems.size() - 1);
                        Article a = new Article(current);
                        a.section = section;
                        backItems.add(a);
                    }
                }
                else if (backItems.size() > 0) {
                    Article current = backItems.get(backItems.size() - 1);
                    section = current.section;
                }
                
                if (section != null && !section.trim().equals("")) {
                    goToSection(section);
                }     
                
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, final String url) {
                Log.d(TAG, "URL clicked: " + url);
                String urlLower = url.toLowerCase(); 
                if (urlLower.startsWith("http://") ||
                    urlLower.startsWith("https://") ||
                    urlLower.startsWith("ftp://") ||
                    urlLower.startsWith("sftp://") ||
                    urlLower.startsWith("mailto:")) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
                                                Uri.parse(url)); 
                    startActivity(browserIntent);                                         
                }
                else {
                	if (currentTask == null) {
                		currentTask = new TimerTask() {							
							public void run() {
								try {
									Article currentArticle = backItems.get(backItems.size() - 1);
									currentIterator = dictionaryService.followLink(url, currentArticle.volumeId);
		    						runOnUiThread(new Runnable() {
										public void run() {
						                    if (currentIterator.hasNext()) {
						                        Entry entry = currentIterator.next();
						                        showArticle(entry);
						                    }                
						                    else {					                    	
						                        showMessage(String.format("Article \"%s\" not found", url));
						                    }                										
										}
									});	
								}
								catch (Exception e) {
									StringBuilder msgBuilder = new StringBuilder("There was an error following link ")
									.append("\"").append(url).append("\"");
									if (e.getMessage() != null) {
										msgBuilder.append(": ").append(e.getMessage());
									}
									
									final String msg = msgBuilder.toString(); 
									Log.e(TAG, msg, e);
									runOnUiThread(new Runnable() {
										public void run() {
											showError(msg);											
										}
									});									
								}
							}
						};
						timer.schedule(currentTask, 0);
                	}                	
                }
                return true;
            }
        });        
                        
        setContentView(articleView);
        setProgressBarVisibility(true);
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.aarddict);
        
        connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
            	dictionaryService = ((DictionaryService.LocalBinder)service).getService();
                Intent intent = getIntent();
                String word = intent.getStringExtra("word");                
                String section = intent.getStringExtra("section");
                String volumeId = intent.getStringExtra("volumeId");
                long articlePointer = intent.getLongExtra("articlePointer", -1);
                dictionaryService.setPreferred(volumeId);
            	showArticle(volumeId, articlePointer, word, section);
            } 

            public void onServiceDisconnected(ComponentName className) {
            	dictionaryService = null;
                Toast.makeText(ArticleViewActivity.this, "Dictionary service disconnected, quitting...",
                        Toast.LENGTH_LONG).show();
                ArticleViewActivity.this.finish();
            }
        };                
        
        Intent dictServiceIntent = new Intent(this, DictionaryService.class);
        bindService(dictServiceIntent, connection, 0);                                
    }

    private void goToSection(String section) {
        articleView.loadUrl(String.format("javascript:scrollToMatch(\"%s\")", section));
    }    
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (goBack()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean goBack() {
    	if (currentTask != null) {
    		return true;
    	}
        if (backItems.size() > 1) {
            Article current = backItems.remove(backItems.size() - 1); 
            forwardItems.add(0, current);
            Article prev = backItems.remove(backItems.size() - 1);
            
            if (prev.eqalsIgnoreSection(current)) {
                backItems.add(prev);
                goToSection(prev.section);
            }   
            else {
                showArticle(prev);
            }
            return true;            
        }
        return false;
    }
    
    private boolean goForward() {
    	if (currentTask != null) {
    		return true;
    	}    	
        if (forwardItems.size() > 0){              
            Article next = forwardItems.remove(0);
            Article current = backItems.get(backItems.size() - 1);
            if (next.eqalsIgnoreSection(current)) {
                backItems.add(next);
                goToSection(next.section);                
            } else {
                showArticle(next);
            }
            return true;
        }
        return false;
    }

    private void nextArticle() {    	
    	if (!goForward() && currentIterator != null && currentIterator.hasNext()) {
    		Entry entry = currentIterator.next();
    		showArticle(entry);
    	}
    }
        
    @Override
    public boolean onSearchRequested() {
        finish();
        return true;
    }
    
    final static int MENU_BACK = 1;
    final static int MENU_VIEW_ONLINE = 2;
    final static int MENU_NEW_LOOKUP = 3;
    final static int MENU_NEXT = 4;
    
    private MenuItem miViewOnline; 
    private MenuItem miNextArticle;
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_BACK, 0, "Back").setIcon(android.R.drawable.ic_menu_revert);     
        miNextArticle = menu.add(0, MENU_NEXT, 0, "Next").setIcon(android.R.drawable.ic_media_next);
        miViewOnline = menu.add(0, MENU_VIEW_ONLINE, 0, "View Online").setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, MENU_NEW_LOOKUP, 0, "New Lookup").setIcon(android.R.drawable.ic_menu_search);        
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	boolean enableViewOnline = false;
        if (this.backItems.size() > 0) {
            Article current = this.backItems.get(this.backItems.size() - 1);
            Volume d = dictionaryService.getVolume(current.volumeId);
            enableViewOnline = d.getArticleURLTemplate() != null;
        }    	    
    	miViewOnline.setEnabled(enableViewOnline);
    	miNextArticle.setEnabled(forwardItems.size() > 0 || (currentIterator != null && currentIterator.hasNext()));
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_BACK:
            if (!goBack()) {
                finish();
            };
            break;
        case MENU_VIEW_ONLINE:
            viewOnline();
            break;            
        case MENU_NEW_LOOKUP:
            onSearchRequested();
            break;
        case MENU_NEXT:
            nextArticle();
            break;                                    
        }
        return true;
    }
    
    private void viewOnline() {
        if (this.backItems.size() > 0) {            
            Article current = this.backItems.get(this.backItems.size() - 1);
            Volume d = dictionaryService.getVolume(current.volumeId);
            String url = d == null ? null : d.getArticleURL(current.title);
            if (url != null) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
                        Uri.parse(url)); 
                startActivity(browserIntent);                                         
            }
        }
    }
    
    private void showArticle(String volumeId, long articlePointer, String word, String section) {
        Log.d(TAG, "word: " + word);
        Log.d(TAG, "dictionaryId: " + volumeId);
        Log.d(TAG, "articlePointer: " + articlePointer);
        Log.d(TAG, "section: " + section);
                
        Volume d = dictionaryService.getVolume(volumeId);
        if (d == null) {
            showError(String.format("Dictionary %s not found", volumeId));
            return;
        }
        
        Entry entry = new Entry(d.getId(), word, articlePointer);
        entry.section = section;
        showArticle(entry);
    }    
    
    private void showArticle(final Entry entry) {
    	forwardItems.clear();    	
    	setTitle(entry);
    	setProgress(500);
    	currentTask = new TimerTask() {
			public void run() {
		        try {
			        final Article a = dictionaryService.getArticle(entry);
					runOnUiThread( new Runnable() {							
						public void run() {
							showArticle(a);							
						}
					});
		        }
		        catch (Exception e) {
					runOnUiThread( new Runnable() {							
						public void run() {
							showError(String.format("There was an error loading article \"%s\"", entry.title));
						}
					});				        					            
		        }
			}
    	};
    	timer.schedule(currentTask, 0);
    }
    
    private void showArticle(Article a) {
        try {
            a = dictionaryService.redirect(a);
        }            
        catch (RedirectNotFound e) {        	
        	setProgress(10000);
        	if (!backItems.isEmpty()) {
        		setTitle(backItems.get(0));
        	}
            showMessage(String.format("Redirect \"%s\" not found", a.getRedirect()));
            return;
        }
        catch (RedirectTooManyLevels e) {
        	setProgress(10000);
        	if (!backItems.isEmpty()) {
        		setTitle(backItems.get(0));
        	}        	
            showMessage(String.format("Too many redirects for \"%s\"", a.getRedirect()));
            return;
        }
        catch (Exception e) {
        	currentTask = null;
        	if (!backItems.isEmpty()) {
        		setTitle(backItems.get(0));
        	}        	
            showError(String.format("There was an error loading article \"%s\"", a.title));
            return;
        }
        backItems.add(a);
        setProgress(5000);
        setTitle(a);
        Log.d(TAG, "Show article: " + a.text);        
        articleView.loadDataWithBaseURL("", wrap(a.text), "text/html", "utf-8", null);
    }
    
    private void showMessage(String message) {
    	currentTask = null;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (backItems.size() == 0) {
            finish();
        }        
    }

    private void showError(String message) {
    	currentTask = null;
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle("Error").setMessage(message).setNeutralButton("Dismiss", new OnClickListener() {            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (backItems.size() == 0) {
                    finish();
                }
            }
        });
        dialogBuilder.show();
    }
    
    private void setTitle(Article a) {
    	setTitle(a.title, dictionaryService.getDisplayTitle(a.volumeId));
    }
    
    private void setTitle(Entry e) {
    	setTitle(e.title, dictionaryService.getDisplayTitle(e.volumeId));
    }    
    
    private void setTitle(CharSequence articleTitle, CharSequence dictTitle) {
    	setTitle(String.format("%s - %s", articleTitle, dictTitle));
    }        
    
    private String wrap(String articleText) {
        return new StringBuilder("<html>")
        .append("<head>")
        .append(this.sharedCSS)
        .append(this.mediawikiSharedCSS)
        .append(this.mediawikiMonobookCSS)
        .append(this.js)
        .append("</head>")
        .append("<body>")
        .append("<div id=\"globalWrapper\">")        
        .append(articleText)
        .append("</div>")
        .append("</body>")
        .append("</html>")
        .toString();
    }
    
    private String wrapCSS(String css) {
        return String.format("<style type=\"text/css\">%s</style>", css);
    }

    private String wrapJS(String js) {
        return String.format("<script type=\"text/javascript\">%s</script>", js);
    }
    
    private void loadAssets() {
        try {
            this.sharedCSS = wrapCSS(readFile("shared.css"));
            this.mediawikiSharedCSS = wrapCSS(readFile("mediawiki_shared.css"));
            this.mediawikiMonobookCSS = wrapCSS(readFile("mediawiki_monobook.css"));
            this.js = wrapJS(readFile("aar.js"));
        }
        catch (IOException e) {
            Log.e(TAG, "Failed to load assets", e);
        }        
    }
    
    private String readFile(String name) throws IOException {
        final char[] buffer = new char[0x1000];
        StringBuilder out = new StringBuilder();
        InputStream is = getResources().getAssets().open(name);
        Reader in = new InputStreamReader(is, "UTF-8");
        int read;
        do {
          read = in.read(buffer, 0, buffer.length);
          if (read>0) {
            out.append(buffer, 0, read);
          }
        } while (read>=0);
        return out.toString();
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	timer.cancel();
    	currentIterator = null;
    	currentTask = null;
    	unbindService(connection);  
    }
}

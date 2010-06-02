package aarddict.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import aarddict.Article;
import aarddict.Entry;
import aarddict.LookupWord;
import aarddict.RedirectNotFound;
import aarddict.RedirectTooManyLevels;
import aarddict.Volume;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;


public final class ArticleViewActivity extends BaseDictionaryActivity {

    private final static String TAG = ArticleViewActivity.class.getName();
    
    private WebView             articleView;
    private String              sharedCSS;
    private String              mediawikiSharedCSS;
    private String              mediawikiMonobookCSS;
    private String              js;

    private List<HistoryItem>   backItems;
    private Timer               timer;
    private TimerTask           currentTask;
    
	private final class ArticleGestureListener extends SimpleOnGestureListener {
	    
	    private static final int SWIPE_MIN_DISTANCE = 200;
	    private static final int SWIPE_MAX_OFF_PATH = 150;
	    private static final int SWIPE_THRESHOLD_VELOCITY = 500;
	    
	    @Override
	    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
                    return false;
                if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    goBack();
                    return true;
                }  else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    nextArticle();                    
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "error while handling fling event", e);
            }
            return false;	        
	    }		    
	    
	    @Override
	    public void onLongPress(MotionEvent e) {
	        finish();
	    }
	}
	
    @Override
    void initUI() {

        loadAssets();

        timer = new Timer();
        
        backItems = Collections.synchronizedList(new LinkedList<HistoryItem>());
        
        getWindow().requestFeature(Window.FEATURE_PROGRESS);        
        
        gestureDetector = new GestureDetector(this, new ArticleGestureListener());
                        
        articleView = new WebView(this);        
        articleView.getSettings().setJavaScriptEnabled(true);
        
        articleView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });
        articleView.addJavascriptInterface(new SectionMatcher(), "matcher");
        
        articleView.setWebChromeClient(new WebChromeClient(){
            
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
                	LookupWord lookupWord = LookupWord.splitWord(url);                    
                    section = lookupWord.section;
                    if (backItems.size() > 0) {
                    	HistoryItem currentHistoryItem = backItems.get(backItems.size() - 1); 
                        HistoryItem h = new HistoryItem(currentHistoryItem);
                        h.article.section = section;
                        backItems.add(h);
                    }
                }
                else if (backItems.size() > 0) {
                    Article current = backItems.get(backItems.size() - 1).article;
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
									Article currentArticle = backItems.get(backItems.size() - 1).article;
									Iterator<Entry> currentIterator = dictionaryService.followLink(url, currentArticle.volumeId);
									List<Entry> result = new ArrayList<Entry>();
									while (currentIterator.hasNext() && result.size() < 20) {
										result.add(currentIterator.next());
									}									
									HistoryItem item = new HistoryItem(result);
									if (item.hasNext()) {
										showNext(item);
									}
									else {
										showMessage(String.format("Article \"%s\" not found", url));
									}
								}								
								catch (Exception e) {
									StringBuilder msgBuilder = new StringBuilder("There was an error following link ")
									.append("\"").append(url).append("\"");
									if (e.getMessage() != null) {
										msgBuilder.append(": ").append(e.getMessage());
									}									
									final String msg = msgBuilder.toString(); 
									Log.e(TAG, msg, e);
									showError(msg);
								}
							}
						};
						try {
						    timer.schedule(currentTask, 0);
						}
						catch (Exception e) {
						    Log.d(TAG, "Failed to schedule task", e);
						}
                	}                	
                }
                return true;
            }
        });        
                        
        setContentView(articleView);
        setProgressBarVisibility(true);
    }

    private void goToSection(String section) {
    	Log.d(TAG, "Go to section " + section);
    	if (section == null || section.trim().equals("")) {
    		articleView.scrollTo(0, 0);
    	}
    	else {
    		articleView.loadUrl(String.format("javascript:scrollToMatch(\"%s\")", section));
    	}
    }    
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                goBack();   
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                zoomIn();
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                zoomOut();
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }
        return true;
    }

    
    private boolean zoomIn() {        
        return articleView.zoomIn();
    }
    
    private boolean zoomOut() {
        return articleView.zoomOut();
    }
        
    private void goBack() {
        if (backItems.size() == 1) {
            finish();
        }        
    	if (currentTask != null) {
    		return;
    	}
        if (backItems.size() > 1) {
            HistoryItem current = backItems.remove(backItems.size() - 1); 
            HistoryItem prev = backItems.get(backItems.size() - 1);
            
            Article prevArticle = prev.article; 
            if (prevArticle.eqalsIgnoreSection(current.article)) {
            	resetTitleToCurrent();
            	if (!prevArticle.sectionEquals(current.article)) { 
            	    goToSection(prevArticle.section);
            	}
            }   
            else {
            	showCurrentArticle();
            }
        }
    }
            
    private void nextArticle() {
    	HistoryItem current = backItems.get(backItems.size() - 1);
    	if (current.hasNext()) {
    		showNext(current);
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
    final static int MENU_ZOOM_IN = 5;
    final static int MENU_ZOOM_OUT = 6;
    
    private MenuItem miViewOnline; 
    private MenuItem miNextArticle;
    private GestureDetector gestureDetector;
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_BACK, 0, "Back").setIcon(android.R.drawable.ic_menu_revert);     
        miNextArticle = menu.add(0, MENU_NEXT, 0, "Next").setIcon(android.R.drawable.ic_media_next);
        miViewOnline = menu.add(0, MENU_VIEW_ONLINE, 0, "View Online").setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, MENU_NEW_LOOKUP, 0, "New Lookup").setIcon(android.R.drawable.ic_menu_search);        
        menu.add(0, MENU_ZOOM_OUT, 0, "Zoom Out").setIcon(R.drawable.ic_menu_zoom_out);
        menu.add(0, MENU_ZOOM_IN, 0, "Zoom In").setIcon(R.drawable.ic_menu_zoom_in);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	boolean enableViewOnline = false;
    	boolean enableNextArticle = false;
        if (this.backItems.size() > 0) {
            HistoryItem historyItem = backItems.get(backItems.size() - 1);
            Article current = historyItem.article;
            Volume d = dictionaryService.getVolume(current.volumeId);
            enableViewOnline = d.getArticleURLTemplate() != null;            
            enableNextArticle = historyItem.hasNext();
        }    	    
    	miViewOnline.setEnabled(enableViewOnline);
    	miNextArticle.setEnabled(enableNextArticle);
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_BACK:
            goBack();
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
        case MENU_ZOOM_IN:
            zoomIn();
            break;
        case MENU_ZOOM_OUT:
            zoomOut();
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }
        
    private void viewOnline() {
        if (this.backItems.size() > 0) {            
            Article current = this.backItems.get(this.backItems.size() - 1).article;
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
        HistoryItem item = new HistoryItem(entry);
        showNext(item);
    }    
        
    private void showNext(HistoryItem item_) {
    	final HistoryItem item = new HistoryItem(item_);
    	final Entry entry = item.next();
    	runOnUiThread(new Runnable() {
			public void run() {
				setTitle(item);
				setProgress(500);
			}
		});    	
    	currentTask = new TimerTask() {
			public void run() {
		        try {
			        Article a = dictionaryService.getArticle(entry);			        			        
			        try {
			            a = dictionaryService.redirect(a);
			            item.article = new Article(a);
			        }            
			        catch (RedirectNotFound e) {
			            showMessage(String.format("Redirect \"%s\" not found", a.getRedirect()));
			            return;
			        }
			        catch (RedirectTooManyLevels e) {
			            showMessage(String.format("Too many redirects for \"%s\"", a.getRedirect()));
			            return;
			        }
			        catch (Exception e) {
			        	Log.e(TAG, "Redirect failed", e);
			            showError(String.format("There was an error loading article \"%s\"", a.title));
			            return;
			        }
			        
			        HistoryItem oldCurrent = null;
			        if (!backItems.isEmpty())
			        	oldCurrent = backItems.get(backItems.size() - 1);
			        
			        backItems.add(item);
			        
			        if (oldCurrent != null) {
			        	HistoryItem newCurrent = item;
			            if (newCurrent.article.eqalsIgnoreSection(oldCurrent.article)) {
			                
			            	final String section = oldCurrent.article.sectionEquals(newCurrent.article) ? null : newCurrent.article.section;
			            	
			            	runOnUiThread(new Runnable() {								
								public void run() {
									resetTitleToCurrent();
									if (section != null)
									    goToSection(section);
									setProgress(10000);
									currentTask = null;
								}
							});			                
			            }   
			            else {
			            	showCurrentArticle();
			            }			        	
			        }
			        else {
			        	showCurrentArticle();
			        }			        			        							
		        }
		        catch (Exception e) {
		        	String msg = String.format("There was an error loading article \"%s\"", entry.title); 
		        	Log.e(TAG, msg, e);
		        	showError(msg);
		        }
			}
    	};
    	try {
    	    timer.schedule(currentTask, 0);
    	}
    	catch (Exception e) {
    	    Log.d(TAG, "Failed to schedule task", e);
    	}
    }
        
    private void showCurrentArticle() {
    	runOnUiThread(new Runnable() {			
			public void run() {		        
		        setProgress(5000);
		        resetTitleToCurrent();		       
		        Article a = backItems.get(backItems.size() - 1).article;
		        Log.d(TAG, "Show article: " + a.text);        
		        articleView.loadDataWithBaseURL("", wrap(a.text), "text/html", "utf-8", null);
			}
		});
    }
    
    private void showMessage(final String message) {
    	runOnUiThread(new Runnable() {
			public void run() {
		    	currentTask = null;
		    	setProgress(10000);
		    	resetTitleToCurrent();
		        Toast.makeText(ArticleViewActivity.this, message, Toast.LENGTH_LONG).show();
		        if (backItems.isEmpty()) {
		            finish();
		        }        				
			}
		});
    }

    private void showError(final String message) {
    	runOnUiThread(new Runnable() {
			public void run() {
		    	currentTask = null;
		    	setProgress(10000);
		    	resetTitleToCurrent();
		        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(ArticleViewActivity.this);
		        dialogBuilder.setTitle("Error").setMessage(message).setNeutralButton("Dismiss", new OnClickListener() {            
		            @Override
		            public void onClick(DialogInterface dialog, int which) {
		                dialog.dismiss();
		                if (backItems.isEmpty()) {
		                    finish();
		                }
		            }
		        });
		        dialogBuilder.show();
			}
		});    	
    }
    
        
    private void setTitle(CharSequence articleTitle, CharSequence dictTitle) {
    	setTitle(String.format("%s - %s", articleTitle, dictTitle));
    }        
    
    private void resetTitleToCurrent() {    	    		
    	if (!backItems.isEmpty()) {
    		HistoryItem current = backItems.get(backItems.size() - 1);
    		setTitle(current);
    	}
    }
    
    private void setTitle(HistoryItem item) {		
		StringBuilder title = new StringBuilder();
		if (item.entries.size() > 1) {
			title
			.append(item.entryIndex + 1)
			.append("/")
			.append(item.entries.size())
			.append(" ");
		}
		Entry entry = item.current();
		title.append(entry.title);
		setTitle(title, dictionaryService.getDisplayTitle(entry.volumeId));    	
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
    }


    @Override
    void onDictionaryServiceReady() {
        Intent intent = getIntent();
        String word = intent.getStringExtra("word");                
        String section = intent.getStringExtra("section");
        String volumeId = intent.getStringExtra("volumeId");
        long articlePointer = intent.getLongExtra("articlePointer", -1);
        dictionaryService.setPreferred(volumeId);
        showArticle(volumeId, articlePointer, word, section);
    }
}

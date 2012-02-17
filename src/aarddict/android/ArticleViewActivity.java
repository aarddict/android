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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import aarddict.Article;
import aarddict.ArticleNotFound;
import aarddict.Entry;
import aarddict.LookupWord;
import aarddict.RedirectTooManyLevels;
import aarddict.Volume;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;


public class ArticleViewActivity extends BaseDictionaryActivity {

    private final static String TAG = ArticleViewActivity.class.getName();

    public static final int NOOK_KEY_PREV_LEFT = 92;
    public static final int NOOK_KEY_NEXT_LEFT = 93;

    public static final int NOOK_KEY_PREV_RIGHT = 94;
    public static final int NOOK_KEY_NEXT_RIGHT = 95;
    
    private ArticleView         articleView;
    private String              sharedCSS;
    private String              mediawikiSharedCSS;
    private String              mediawikiMonobookCSS;
    private String              js;

    private List<HistoryItem>   backItems;
    private Timer               timer;
    private TimerTask           currentTask;
    private TimerTask           currentHideNextButtonTask;
    private AlphaAnimation 		fadeOutAnimation;
    private boolean 			useAnimation = false;        
    
	private Map<Article, ScrollXY> scrollPositionsH;
	private Map<Article, ScrollXY> scrollPositionsV;
	private boolean                saveScrollPos = true;

	private static final String FONTSIZE_START = "<div align=\"justify\"><font size=\"";
	private static final String FONTSIZE_MIDDLE = "\">";
	private static final String FONTSIZE_END = "</font></div>";
	private int fontsize;
    
    static class AnimationAdapter implements AnimationListener {
		public void onAnimationEnd(Animation animation) {}
		public void onAnimationRepeat(Animation animation) {}
		public void onAnimationStart(Animation animation) {}    	
    }
    
    @Override
    void initUI() {
    	this.scrollPositionsH = Collections.synchronizedMap(new HashMap<Article, ScrollXY>());
    	this.scrollPositionsV = Collections.synchronizedMap(new HashMap<Article, ScrollXY>());
        loadAssets();                

        if (DeviceInfo.EINK_SCREEN)	{
			useAnimation = false;
			N2EpdController.setGL16Mode(2);  // force full screen refresh when changing articles

	    	setContentView(R.layout.eink_article_view);
	        articleView = (ArticleView)findViewById(R.id.EinkArticleView);
		}
		// Setup animations only on non-eink screens
		else
		{
	        //Animation is broken before 2.1 - animation listener notified,
	        //only sometimes so we can't use it
	        try {
	        	useAnimation = Integer.parseInt(Build.VERSION.SDK) > 6;
	        }        
	        catch (Exception e) {
	        	Log.w(TAG, "Failed to parse SDK version string as int: " + Build.VERSION.SDK);	
	        }

	        fadeOutAnimation = new AlphaAnimation(1f, 0f);
	        fadeOutAnimation.setDuration(600);
	        fadeOutAnimation.setAnimationListener(new AnimationAdapter() {
	        	public void onAnimationEnd(Animation animation) {
	        		Button nextButton = (Button)findViewById(R.id.NextButton);
	        		nextButton.setVisibility(Button.GONE);
	        	}
	        });

	    	getWindow().requestFeature(Window.FEATURE_PROGRESS);
	    	setContentView(R.layout.article_view);
	    	articleView = (ArticleView)findViewById(R.id.ArticleView);    
		}

        Log.d(TAG, "Build.VERSION.SDK: " + Build.VERSION.SDK);
        Log.d(TAG, "use animation? " + useAnimation);

        timer = new Timer();

		fontsize = 3;
        
        backItems = Collections.synchronizedList(new LinkedList<HistoryItem>());        
        
        articleView.setOnScrollListener(new ArticleView.ScrollListener(){
			public void onScroll(int l, int t, int oldl, int oldt) {
				saveScrollPos(l, t);
			}        	
        });
        
    	articleView.getSettings().setJavaScriptEnabled(true);
        
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
                if (!restoreScrollPos()) {
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
									try {
									    Iterator<Entry> currentIterator = dictionaryService.followLink(url, currentArticle.volumeId);
	                                    List<Entry> result = new ArrayList<Entry>();
	                                    while (currentIterator.hasNext() && result.size() < 20) {
	                                        result.add(currentIterator.next());
	                                    }                                   
	                                    showNext(new HistoryItem(result));									    
									}
									catch (ArticleNotFound e) {
									    showMessage(getString(R.string.msgArticleNotFound, e.word.toString()));
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
        final Button nextButton = (Button)findViewById(R.id.NextButton);
        nextButton.getBackground().setAlpha(180);
        nextButton.setOnClickListener(new View.OnClickListener() {			
			public void onClick(View v) {
				if (nextButton.getVisibility() == View.VISIBLE) {
					updateNextButtonVisibility();
					nextArticle();
					updateNextButtonVisibility();
				}											
			}
		});
		articleView.setOnTouchListener(
			new View.OnTouchListener() {				
				public boolean onTouch(View v, MotionEvent event) {
					updateNextButtonVisibility();
					return false;
				}
			}
		);
        setProgressBarVisibility(true);
    }

    private void scrollTo(ScrollXY s) {
    	scrollTo(s.x, s.y);
    }
    
    private void scrollTo(int x, int y) {
    	saveScrollPos = false; 
    	Log.d(TAG, "Scroll to " + x + ", " + y);
    	articleView.scrollTo(x, y);
    	saveScrollPos = true;
    }
    
    private void goToSection(String section) {
    	Log.d(TAG, "Go to section " + section);
    	if (section == null || section.trim().equals("")) {
    		scrollTo(0, 0);
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
		    case NOOK_KEY_PREV_LEFT:
		    case NOOK_KEY_PREV_RIGHT:
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (!articleView.pageUp(false)) {
                    goBack();
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
		    case NOOK_KEY_NEXT_LEFT:
		    case NOOK_KEY_NEXT_RIGHT:
                if (!articleView.pageDown(false)) {
                    nextArticle();
                };
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }
        return true;
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
    	//eat key ups corresponding to key downs so that volume keys don't beep
    switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
        case KeyEvent.KEYCODE_VOLUME_UP:
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            break;
        default:
            return super.onKeyDown(keyCode, event);
        }
        return true;
    }
        
    private boolean zoomIn() {        
		fontsize++;
		showCurrentArticle();
		return true;
    }
    
    private boolean zoomOut() {
        if (fontsize == 1) return false;
		fontsize--;
		showCurrentArticle();
		return true;
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
            if (prevArticle.equalsIgnoreSection(current.article)) {
            	resetTitleToCurrent();
            	if (!prevArticle.sectionEquals(current.article) && !restoreScrollPos()) {
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
        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_SEARCH)) {
	    	Intent next = new Intent();
	        next.setClass(this, LookupActivity.class);
	    	next.setAction(Intent.ACTION_SEARCH);
	      	next.putExtra(SearchManager.QUERY, intent.getStringExtra("query"));
	      	startActivity(next);
        }
        finish();
        return true;
    }
    
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    	if (keyCode == KeyEvent.KEYCODE_SEARCH){
    		finish();
    		return true;
    	}
    	return super.onKeyLongPress(keyCode, event);
    }

    
    final static int MENU_VIEW_ONLINE = 1;
    final static int MENU_NEW_LOOKUP = 2;
    final static int MENU_ZOOM_IN = 3;
    final static int MENU_ZOOM_OUT = 4;
    
    private MenuItem miViewOnline; 
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        miViewOnline = menu.add(0, MENU_VIEW_ONLINE, 0, R.string.mnViewOnline).setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, MENU_NEW_LOOKUP, 0, R.string.mnNewLookup).setIcon(android.R.drawable.ic_menu_search);        
        menu.add(0, MENU_ZOOM_OUT, 0, R.string.mnZoomOut).setIcon(R.drawable.ic_menu_zoom_out);
        menu.add(0, MENU_ZOOM_IN, 0, R.string.mnZoomIn).setIcon(R.drawable.ic_menu_zoom_in);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	boolean enableViewOnline = false;
        if (this.backItems.size() > 0) {
            HistoryItem historyItem = backItems.get(backItems.size() - 1);
            Article current = historyItem.article;
            Volume d = dictionaryService.getVolume(current.volumeId);
            enableViewOnline = d.getArticleURLTemplate() != null;            
        }    	    
    	miViewOnline.setEnabled(enableViewOnline);
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_VIEW_ONLINE:
            viewOnline();
            break;
        case MENU_NEW_LOOKUP:
            onSearchRequested();
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
        Entry entry = new Entry(d.getId(), word, articlePointer);
        entry.section = section;        
        this.showArticle(entry);
    }
    
    private void showArticle(Entry entry) {        
        List<Entry> result = new ArrayList<Entry>();
        result.add(entry);
        
        try {
            Iterator<Entry> currentIterator = dictionaryService.followLink(entry.title, entry.volumeId);            
            while (currentIterator.hasNext() && result.size() < 20) {
                Entry next = currentIterator.next();
                if (!next.equals(entry)) {
                    result.add(next);
                }
            }                                                                                    
        }
        catch (ArticleNotFound e) {
            Log.d(TAG, String.format("Article \"%s\" not found - unexpected", e.word));
        }        
        showNext(new HistoryItem(result));
    }    

    private Map<Article, ScrollXY> getScrollPositions() {
		int orientation = getWindowManager().getDefaultDisplay().getOrientation();
		switch (orientation) {
			case Surface.ROTATION_0:
			case Surface.ROTATION_180:
				return scrollPositionsV;
			default:
				return scrollPositionsH;
		}
    }
    
    private void saveScrollPos(int x, int y) {
    	if (!saveScrollPos) {
    		//Log.d(TAG, "Not saving scroll position (disabled)");
    		return;
    	}
    	if (backItems.size() > 0) {
	    	Article a = backItems.get(backItems.size() - 1).article;
	    	Map<Article, ScrollXY> positions = getScrollPositions();
	    	ScrollXY s = positions.get(a);
	    	if (s == null) {
		    	s = new ScrollXY(x, y);
		    	positions.put(a, s);
	    	}
	    	else {
	    		s.x = x;
	    		s.y = y;
	    	}
	    	//Log.d(TAG, String.format("Saving scroll position %s for %s", s, a.title));
	    	getScrollPositions().put(a, s);    	
    	}
    }
    
    private boolean restoreScrollPos() {
    	if (backItems.size() > 0) { 
	    	Article a = backItems.get(backItems.size() - 1).article;    	
	    	ScrollXY s = getScrollPositions().get(a);
	    	if (s == null) {
	    		return false;
	    	}
	    	scrollTo(s);
	    	return true;
    	}
    	return false;
    }
    
    private void showNext(HistoryItem item_) {
    	final HistoryItem item = new HistoryItem(item_);
    	final Entry entry = item.next();
    	runOnUiThread(new Runnable() {
			public void run() {
				setTitle(item);
				setProgress(1000);
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
			        catch (ArticleNotFound e) {
			            showMessage(getString(R.string.msgRedirectNotFound, e.word.toString()));
			            return;
			        }
			        catch (RedirectTooManyLevels e) {
			            showMessage(getString(R.string.msgTooManyRedirects, a.getRedirect()));
			            return;
			        }
			        catch (Exception e) {
			        	Log.e(TAG, "Redirect failed", e);
			            showError(getString(R.string.msgErrorLoadingArticle, a.title));
			            return;
			        }
			        
			        HistoryItem oldCurrent = null;
			        if (!backItems.isEmpty())
			        	oldCurrent = backItems.get(backItems.size() - 1);
			        
			        backItems.add(item);
			        
			        if (oldCurrent != null) {
			        	HistoryItem newCurrent = item;
			            if (newCurrent.article.equalsIgnoreSection(oldCurrent.article)) {
			                
			            	final String section = oldCurrent.article.sectionEquals(newCurrent.article) ? null : newCurrent.article.section;
			            	
			            	runOnUiThread(new Runnable() {								
								public void run() {
									resetTitleToCurrent();
									if (section != null) {
									    goToSection(section);
									}
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
		            String msg = getString(R.string.msgErrorLoadingArticle, entry.title);
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
		        articleView.loadDataWithBaseURL("", new StringBuilder(FONTSIZE_START).append(fontsize).append(FONTSIZE_MIDDLE).append(wrap(a.text)).append(FONTSIZE_END).toString(), "text/html", "utf-8", null);
			}
		});
    }
        
    private void updateNextButtonVisibility() {
    	if (currentHideNextButtonTask != null) {
    		currentHideNextButtonTask.cancel();
    		currentHideNextButtonTask = null;
    	}
    	boolean hasNextArticle = false;
        if (backItems.size() > 0) {
            HistoryItem historyItem = backItems.get(backItems.size() - 1);
            hasNextArticle = historyItem.hasNext();
        }
        final Button nextButton = (Button)findViewById(R.id.NextButton);
        if (hasNextArticle) {
        	if (nextButton.getVisibility() == View.GONE){
        		nextButton.setVisibility(View.VISIBLE);
        	}        	
        	currentHideNextButtonTask = new TimerTask() {			
        		@Override
        		public void run() {
        			runOnUiThread(new Runnable() {					
        				public void run() {
        	        		if (useAnimation) {
        	        			nextButton.startAnimation(fadeOutAnimation);
        	        		}
        	        		else {
        	        			nextButton.setVisibility(View.GONE);
        	        		}
        					currentHideNextButtonTask = null;
        				}
        			});			
        		}
        	}; 
        	try {
        		timer.schedule(currentHideNextButtonTask, 1800);        		
        	}
        	catch (IllegalStateException e) {
            	//this may happen if orientation changes while users touches screen               	
            	Log.d(TAG, "Failed to schedule \"Next\" button hide", e);        		
        	}
        }        
        else {
        	nextButton.setVisibility(View.GONE);
        }
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
		        dialogBuilder.setTitle(R.string.titleError).setMessage(message).setNeutralButton(R.string.btnDismiss, new OnClickListener() {            
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
    	setTitle(getString(R.string.titleArticleViewActivity, articleTitle, dictTitle));
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
    protected void onPause() {
    	super.onPause();
    	SharedPreferences prefs = getPreferences(MODE_PRIVATE);
    	Editor e = prefs.edit();
    	e.putFloat("articleView.scale", articleView.getScale());
    	boolean success = e.commit();
    	if (!success) {
    		Log.w(TAG, "Failed to save article view scale pref");
    	}
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	SharedPreferences prefs = getPreferences(MODE_PRIVATE);    	
    	float scale = prefs.getFloat("articleView.scale", 1.0f);
    	int initialScale = Math.round(scale*100);
    	Log.d(TAG, "Setting initial article view scale to " + initialScale);
    	articleView.setInitialScale(initialScale);
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	timer.cancel();
    	scrollPositionsH.clear();
    	scrollPositionsV.clear();
    	backItems.clear();
    }

    @Override
    void onDictionaryServiceReady() {
    	if (this.backItems.isEmpty()) {
	        final Intent intent = getIntent();	        
	        if (intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_SEARCH)) {
	            final String word = intent.getStringExtra("query");
	            
	            if (currentTask != null) {
	                currentTask.cancel();	                
	            }
	            
	            currentTask = new TimerTask() {                    
                    @Override
                    public void run() {
                        setProgress(500);                                      
                        Log.d(TAG, "intent.getDataString(): " + intent.getDataString());
                        Iterator<Entry> results = dictionaryService.lookup(word);
                        Log.d(TAG, "Looked up " + word );
                        if (results.hasNext()) {
                            currentTask = null;
                            Entry entry = results.next();
                            showArticle(entry);
                        }
                        else {
                        	onSearchRequested();
                        }
                    }
                };
	            
                try {
                    timer.schedule(currentTask, 0);
                }
                catch (Exception e) {
                    Log.d(TAG, "Failed to schedule task", e);
                    showError(getString(R.string.msgErrorLoadingArticle, word));
                }	            
	        }
	        else {
	            String word = intent.getStringExtra("word");                
	            String section = intent.getStringExtra("section");	        
	            String volumeId = intent.getStringExtra("volumeId");
	            long articlePointer = intent.getLongExtra("articlePointer", -1);
	            dictionaryService.setPreferred(volumeId);
	            showArticle(volumeId, articlePointer, word, section);
	        }
    	}
    	else {
    		showCurrentArticle();    		
    	}
    }    
    
    @SuppressWarnings("unchecked")
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	outState.putSerializable("backItems", new LinkedList(backItems));
    	outState.putSerializable("scrollPositionsH", new HashMap(scrollPositionsH));
    	outState.putSerializable("scrollPositionsV", new HashMap(scrollPositionsV));    	
    }
    
    @SuppressWarnings("unchecked")
	@Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
    	super.onRestoreInstanceState(savedInstanceState);
    	backItems = Collections.synchronizedList((List)savedInstanceState.getSerializable("backItems"));
    	scrollPositionsH = Collections.synchronizedMap((Map)savedInstanceState.getSerializable("scrollPositionsH"));
    	scrollPositionsV = Collections.synchronizedMap((Map)savedInstanceState.getSerializable("scrollPositionsV"));
    }	
}

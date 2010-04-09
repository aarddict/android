package aarddict.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import aarddict.Dictionary;
import aarddict.Dictionary.RedirectNotFound;
import aarddict.Dictionary.RedirectTooManyLevels;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;


public class ArticleViewActivity extends Activity {

    private final static String TAG = "aarddict.ArticleViewActivity";
    private WebView articleView;
    private String sharedCSS;
    private String mediawikiSharedCSS;
    private String mediawikiMonobookCSS;
    private String js;
    
    class HistoryItem {
        public String dictionaryId;
        public long articlePointer;
        public String word;
        public String section;
                
        public HistoryItem(String dictionaryId, long articlePointer, String word, String section) {
            this.dictionaryId = dictionaryId;
            this.articlePointer = articlePointer;
            this.word = word;
            this.section = section;
        }
    }
    
    private List<HistoryItem> backItems; 
    private List<HistoryItem> forwardItems;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadAssets();

        backItems = new LinkedList<HistoryItem>();
        forwardItems = new LinkedList<HistoryItem>();
        
        getWindow().requestFeature(Window.FEATURE_PROGRESS);        
                
        articleView = new WebView(this);        
        articleView.getSettings().setBuiltInZoomControls(true);
        articleView.getSettings().setJavaScriptEnabled(true);
        
        articleView.addJavascriptInterface(new SectionMatcher(), "matcher");
        
        articleView.setWebChromeClient(new WebChromeClient(){
            @Override
            public void onConsoleMessage(String message, int lineNumber, String sourceID) {
                Log.d(TAG + ".js", String.format("%d [%s]: %s", lineNumber, sourceID, message));
            }
            
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                Log.d(TAG, "Progress: " + newProgress);
                setProgress(newProgress * 1000);
            }
        });
                       
        articleView.setWebViewClient(new WebViewClient() {
            
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page finished: " + url);
                String section = null;
                
                if (url.contains("#")) {
                    String[] parts = url.split("#", 2);
                    section = parts[1];
                }
                else if (backItems.size() > 0) {
                    HistoryItem current = backItems.get(backItems.size() - 1);
                    section = current.section;
                }
                
                if (section != null && !section.trim().equals("")) {
                    articleView.loadUrl(String.format("javascript:scrollToMatch(\"%s\")", section));
                }     
                
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
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
                    return true;
                }
                Iterator<Dictionary.Entry> a = Dictionaries.getInstance().lookup(url);
                if (a.hasNext()) {
                    Dictionary.Entry entry = a.next();
                    try {
                        showArticle(view, entry.getArticle(), true);
                    }
                    catch (Exception e) {
                        showError(view, String.format("There was an error loading article <em>%s</em>", entry.title));
                    }                    
                }                
                else {
                    showMessage(view, String.format("Article <em>%s</em> not found", url) );
                }
                return true;
            }
        });        
                
        Intent intent = getIntent();
        String word = intent.getStringExtra("word");                
        String section = intent.getStringExtra("section");
        String dictionaryId = intent.getStringExtra("dictionaryId");
        long articlePointer = intent.getLongExtra("articlePointer", -1);
        
        setContentView(articleView);
        setProgressBarVisibility(true);
        showArticle(dictionaryId, articlePointer, word, section, true);
    }

    private void showArticle(String dictionaryId, long articlePointer, String word, String section, boolean clearForward) {
        Log.d(TAG, "word: " + word);
        Log.d(TAG, "dictionaryId: " + dictionaryId);
        Log.d(TAG, "articlePointer: " + articlePointer);
        Log.d(TAG, "section: " + section);
        if (articlePointer > -1) {
            try {
                Dictionary.Article article = Dictionaries.getInstance().getArticle(dictionaryId, articlePointer);
                if (article == null) {
                    showMessage(articleView, String.format("Article <em>%s</em> not found", word));                    
                }
                else {
                    article.title = word;
                    article.section = section;                    
                    showArticle(articleView, article, clearForward);
                }
            }
            catch (Exception e){
                showError(articleView, String.format("There was an error loading article <em>%s</em>", word));
            }
        }
        else {
            showError(articleView, String.format("Invalid article pointer for article <em>%s</em> in dictionary %s", word, dictionaryId));
        }
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
        if (backItems.size() > 1) {
            HistoryItem current = backItems.remove(backItems.size() - 1); 
            forwardItems.add(0, current);
            HistoryItem prev = backItems.remove(backItems.size() - 1);
            showArticle(prev.dictionaryId, prev.articlePointer, prev.word, prev.section, false);
            return true;            
        }
        return false;
    }
    
    private boolean goForward() {
        if (forwardItems.size() > 0){  
            HistoryItem next = forwardItems.remove(0);
            showArticle(next.dictionaryId, next.articlePointer, next.word, next.section, false);
        }
        return false;
    }

    @Override
    public boolean onSearchRequested() {
        finish();
        return true;
    }
    
    final static int MENU_BACK = 1;
    final static int MENU_FORWARD = 2;
    final static int MENU_VIEW_ONLINE = 3;
    final static int MENU_NEW_LOOKUP = 4;
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_BACK, 0, "Back").setIcon(android.R.drawable.ic_media_previous);        
        menu.add(0, MENU_FORWARD, 0, "Forward").setIcon(android.R.drawable.ic_media_next);
        menu.add(0, MENU_VIEW_ONLINE, 0, "View Online").setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, MENU_NEW_LOOKUP, 0, "New Lookup").setIcon(android.R.drawable.ic_menu_search);
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
        case MENU_FORWARD:
            goForward();
            break;
        case MENU_VIEW_ONLINE:
            viewOnline();
            break;            
        case MENU_NEW_LOOKUP:
            onSearchRequested();
            break;                        
        }
        return true;
    }
    
    private void viewOnline() {
        if (this.backItems.size() > 0) {            
            HistoryItem current = this.backItems.get(this.backItems.size() - 1);
            String url = Dictionaries.getInstance().getArticleURL(current.dictionaryId, current.word);
            if (url != null) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
                        Uri.parse(url)); 
                startActivity(browserIntent);                                         
            }
        }
    }
    
    private void showArticle(WebView view, String articleText, String section) {
        Log.d(TAG, "Show article: " + articleText);        
        view.loadDataWithBaseURL("", wrap(articleText), "text/html", "utf-8", null);
    }

    private void showArticle(WebView view, Dictionary.Article a, boolean clearForward) {
        if (a.getRedirect() != null) {
            try {
                a = Dictionaries.getInstance().redirect(a);
            }            
            catch (RedirectNotFound e) {
                showMessage(view, String.format("Redirect <em>%s</em> not found", a.getRedirect()));
            }
            catch (RedirectTooManyLevels e) {
                showMessage(view, String.format("Too many redirects for <em>%s</em>", a.getRedirect()));
            }            
            catch (Exception e) {
                showError(view, String.format("There was an error following redirect <em>%s</em>", a.getRedirect()));
            }
        }
        if (clearForward) {
            forwardItems.clear();
        }
        backItems.add(new HistoryItem(a.dictionary.getId(), a.pointer, a.title, a.section));
        setProgress(0);
        setTitle(a.title);
        showArticle(view, a.text, a.section);
    }
    
    private void showMessage(WebView view, String message) {
        view.loadDataWithBaseURL("", message, "text/html", "utf-8", null);        
    }

    private void showError(WebView view, String message) {
        showMessage(view, String.format("<span style=\"color: red;\">%s</span>", message)); 
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
}

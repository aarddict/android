package aarddict.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;

import aarddict.Dictionary;
import aarddict.Dictionary.RedirectNotFound;
import aarddict.Dictionary.RedirectTooManyLevels;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;


public class ArticleViewActivity extends Activity {

    private final static String TAG = "aarddict.ArticleViewActivity";
    private WebView articleView;
    private String sharedCSS;
    private String mediawikiSharedCSS;
    private String mediawikiMonobookCSS;
    private String js;    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadAssets();
                        
        articleView = new WebView(this);        
        articleView.getSettings().setBuiltInZoomControls(true);
        articleView.getSettings().setJavaScriptEnabled(true);        
        articleView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
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
                        showArticle(view, entry.getArticle());
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
        setContentView(articleView);
        
        Intent intent = getIntent();
        String word = intent.getStringExtra("word");        
        Log.d(TAG, "word: " + word);
        
        getWindow().setTitle(word);        
        String dictionaryId = intent.getStringExtra("dictionaryId");
        Log.d(TAG, "dictionaryId: " + dictionaryId);
        long articlePointer = intent.getLongExtra("articlePointer", -1);
        Log.d(TAG, "articlePointer: " + articlePointer);
        if (articlePointer > -1) {
            try {
                Dictionary.Article article = Dictionaries.getInstance().getArticle(dictionaryId, articlePointer);
                if (article == null) {
                    showMessage(articleView, String.format("Article <em>%s</em> not found", word) );
                }
                else {
                    showArticle(articleView, article);
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

    private void showArticle(WebView view, String articleText) {
        view.loadDataWithBaseURL("", wrap(articleText), "text/html", "utf-8", null);        
    }

    private void showArticle(WebView view, Dictionary.Article a) {
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
        showArticle(view, a.text);
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

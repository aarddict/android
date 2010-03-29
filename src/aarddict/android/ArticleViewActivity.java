package aarddict.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;


public class ArticleViewActivity extends Activity {

    private WebView articleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        
        Intent intent = getIntent();

        String word = intent.getStringExtra("word");
        String definition = intent.getStringExtra("definition");
        
        articleView = new WebView(this);
        articleView.loadData(definition, "text/html", "UTF-8");        
        setContentView(articleView);
    }
    
}

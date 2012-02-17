package aarddict.android;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

public class EinkArticleView extends ArticleView {

	public EinkArticleView(Context context) {
		super(context);
	}
	public EinkArticleView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	public EinkArticleView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	private ArticleView articleView;
	private boolean partial;
	public static int HSCROLL_SIZE;

	@Override
	protected void onDraw (Canvas canvas) {
		if (partial) EinkScreen.PrepareController(this, false); // partial refresh
		else EinkScreen.ResetController(1, this);

		super.onDraw(canvas);
		partial = false;
	}
	
    public void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);

        articleView = (ArticleView)findViewById(R.id.EinkArticleView);
        HSCROLL_SIZE = articleView.getHeight() - 20;
    }

    public boolean pageUp(boolean top) {
        int cury = articleView.getScrollY();
        if (cury == 0) { return false; }
        int newy = cury - HSCROLL_SIZE;
        if (newy < 0) {
          newy = 0;
        }
        articleView.scrollTo(0, newy);

		partial = true;
        return true;
    }

    public boolean pageDown(boolean bottom) {
    	int cury = articleView.getScrollY();
    	int hmax = 0;
    	if (HSCROLL_SIZE < articleView.getContentHeight() )
    		hmax = (int) (articleView.getContentHeight() * articleView.getScale()) - HSCROLL_SIZE; 
        if (cury == hmax) { return false; }        
        int newy = cury + HSCROLL_SIZE;
        if (newy > hmax) {
          newy = hmax;
        }
        if (cury != newy) {
          articleView.scrollTo(0, newy);
        }

		partial = true;
        return true;
    }
}

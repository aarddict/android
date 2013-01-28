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

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

class ArticleView extends WebView {

    private boolean scrollToArmed = false;
    boolean mustBeArmedToScroll = true;

        interface ScrollListener {
                void onScroll(int l, int t, int oldl, int oldt);
        }

        private ScrollListener scrollListener;

        public ArticleView(Context context) {
                super(context);
        }

        public ArticleView(Context context, AttributeSet attrs) {
                super(context, attrs);
        }

        public ArticleView(Context context, AttributeSet attrs, int defStyle) {
                super(context, attrs, defStyle);
        }

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                if (scrollListener != null) {
                        scrollListener.onScroll(l, t, oldl, oldt);
                }
        }

        public void setOnScrollListener(ScrollListener l) {
                this.scrollListener = l;
        }

        @Override
        public void scrollTo(int x, int y) {
            //On Android 4.1 and 4.2 WebViewClassic.setNewPicture()
            //ultimately calls scrollTo(0, 0). This happens
            //after page load is finished and after ArticleViewActivity restored
            //scroll position. Overriding scrollTo() to do nothing
            //reliably prevents this. However, this also prevents programmatic
            //scrolling from JavaScript, so now JavaScript must call armScroll()
            //before calling any scrolling methods
            if (!mustBeArmedToScroll || scrollToArmed) {
                super.scrollTo(x, y);
                scrollToArmed = false;
            }
        }

        @JavascriptInterface
        public void armScroll() {
            this.scrollToArmed = true;
        }

        public void realScrollTo(int x, int y) {
            super.scrollTo(x, y);
        }

}

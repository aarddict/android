/**
 * 
 */
package aarddict.android;

import android.app.ProgressDialog;
import android.content.Context;

final class OpeningProgressDialog extends ProgressDialog {        
    OpeningProgressDialog(Context context) {
        super(context);
        setCancelable(false);
        setIndeterminate(false);
        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        setProgress(0);
        setMessage(context.getString(R.string.msgLoading));
    }        
}
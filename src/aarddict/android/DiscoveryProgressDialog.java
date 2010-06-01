/**
 * 
 */
package aarddict.android;

import android.app.ProgressDialog;
import android.content.Context;

final class DiscoveryProgressDialog extends ProgressDialog {        
    DiscoveryProgressDialog(Context context) {
        super(context);
        setCancelable(false);
        setIndeterminate(true);
        setProgressStyle(ProgressDialog.STYLE_SPINNER);
        setMessage("Looking for dictionaries...");            
    }        
}
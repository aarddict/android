package aarddict.android;

import android.os.Build;
import android.util.Log;

public class DeviceInfo {
	public final static String MANUFACTURER;
	public final static String MODEL;
	public final static String DEVICE;
	
	public final static boolean EINK_SCREEN;	
	public final static boolean EINK_NOOK;
	public final static boolean EINK_SONY;

	static {
        MANUFACTURER = getBuildField("MANUFACTURER");
        MODEL = getBuildField("MODEL");
        DEVICE = getBuildField("DEVICE");

		EINK_NOOK = MANUFACTURER.toLowerCase().contentEquals("barnesandnoble") && MODEL.contentEquals("NOOK") && DEVICE.toLowerCase().contentEquals("zoom2");
		EINK_SONY = MANUFACTURER.toLowerCase().contentEquals("sony") && MODEL.contentEquals("PRS-T1");
		EINK_SCREEN = EINK_SONY || EINK_NOOK;
	}
	private static String getBuildField(String fieldName) {
       try {
          return (String)Build.class.getField(fieldName).get(null);
       } catch (Exception e) {
          Log.d("aarddict", "Exception while trying to check Build." + fieldName);
          return "";
       }
    }
}
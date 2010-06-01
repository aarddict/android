/**
 * 
 */
package aarddict.android;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

final class VerifyRecord implements Serializable {
	public UUID uuid;
	public Date date;
	public boolean ok;
}
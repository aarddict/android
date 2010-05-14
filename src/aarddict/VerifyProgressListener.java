/**
 * 
 */
package aarddict;

public interface VerifyProgressListener {
	boolean updateProgress(Volume d, double progress);
	void verified(Volume d, boolean ok);
}
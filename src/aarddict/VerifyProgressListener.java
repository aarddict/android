/**
 * 
 */
package aarddict;

public interface VerifyProgressListener {
	boolean updateProgress(Dictionary d, double progress);
	void verified(Dictionary d, boolean ok);
}
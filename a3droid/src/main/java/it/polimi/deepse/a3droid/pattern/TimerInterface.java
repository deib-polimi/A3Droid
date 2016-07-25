package it.polimi.deepse.a3droid.pattern;

/**The method to manage timeout firings.*/
public interface TimerInterface {

	/**
	 * Called by a Timer to notify its timeout firing.
	 * @param reason It indicates which timeout fired. The taken action will depend on this.
	 */
	void handleTimeEvent(int reason, Object object);
}

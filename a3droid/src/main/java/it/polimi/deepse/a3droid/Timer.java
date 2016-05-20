package it.polimi.deepse.a3droid;

/**This class is used in A3ChannelInterface and in Service, which implement the interface "TimerInterface".
 * After a 2 seconds timeout, it calls TimerInterface.timerFired(int), to notify the timeout fired.
 * @author Francesco
 *
 */
public class Timer extends Thread {

	/**The TimerInterface to notify at timeout firing time.*/
	private TimerInterface channel;
	
	/**It indicates why the timeout is needed.
	 * It is passed in timerFired(int), in order for the TimerInterface to know which timeout fired.
	 */
	private int reason;

	/**The time to wait before timer firing.*/
	private int timeToWait;

	private boolean abort;
	
	/**
	 * @param channel The TimerInterface to notify at timeout firing time.
	 * @param reason It indicates why the timeout is needed on "channel".
	 */
	public Timer(TimerInterface channel, int reason){
		super();
		this.channel = channel;
		this.reason = reason;
		this.abort = false;
		timeToWait = 2000;
	}
	
	/**
	 * @param timerInterface The TimerInterface to notify at timeout firing time.
	 * @param reason It indicates why the timeout is needed on "channel".
	 * @param timeout The time to wait before timer firing.
	 */
	public Timer(TimerInterface timerInterface, int reason, int timeout) {
		// TODO Auto-generated constructor stub
		this(timerInterface, reason);
		timeToWait = timeout;
	}

	public void abort(){
		this.abort = true;
	}

	/**
	 * It notify timeout firing after a 2s timeout.
	 */
	@Override
	public void run(){
		try {
			sleep(timeToWait);
			if(!abort)
				channel.timerFired(reason);
		} catch (Exception e) {
			e.printStackTrace();
			// TODO Auto-generated catch block
		}
	}
}

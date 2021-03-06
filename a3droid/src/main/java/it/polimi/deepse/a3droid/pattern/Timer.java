package it.polimi.deepse.a3droid.pattern;

/**This class is used in A3GroupChannelInterface and in Service, which implement the interface "TimerInterface".
 * After a 2 seconds timeout, it calls TimerInterface.handleTimeEvent(int), to notify the timeout fired.
 * @author Francesco
 *
 */
public class Timer extends Thread {

	/**The TimerInterface to notify at timeout firing time.*/
	private TimerInterface channel;

	/**It indicates why the timeout is needed.
	 * It is passed in handleTimeEvent(int), in order for the TimerInterface to know which timeout fired.
	 */
	private int reason;

	/**The time to wait before timer firing.*/
	private int timeToWait;

	private Object object = null;

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

	/**
	 * @param timerInterface The TimerInterface to notify at timeout firing time.
	 * @param reason It indicates why the timeout is needed on "channel".
	 * @param timeout The time to wait before timer firing.
	 */
	public Timer(TimerInterface timerInterface, int reason, int timeout, Object object) {
		// TODO Auto-generated constructor stub
		this(timerInterface, reason);
		timeToWait = timeout;
		this.object = object;
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
				channel.handleTimeEvent(reason, object);
		} catch (Exception e) {
			e.printStackTrace();
			// TODO Auto-generated catch block
		}
	}
}

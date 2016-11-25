package it.polimi.deepse.a3droid.android;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import it.polimi.deepse.a3droid.a3.events.A3GroupEvent;
import it.polimi.deepse.a3droid.a3.events.A3UIEvent;
import it.polimi.deepse.a3droid.a3.events.A3ErrorEvent;

public abstract class A3DroidActivity extends Activity{
	
	/**The device's UUID*/
	private String uuId;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.uuId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);

		// Set new global handler
		Thread.setDefaultUncaughtExceptionHandler(new ErrorReportExceptionHandler());
		EventBus.getDefault().register(this);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		EventBus.getDefault().unregister(this);
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onGroupEvent(A3GroupEvent event) {
		handleGroupEvent(event);
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onUIEvent(A3UIEvent event) {
		handleUIEvent(event);
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onErrorEvent(A3ErrorEvent event) {
		handleErrorEvent(event);
	}

	/**
	 * This method should be overriden by the application to receive group events
	 * @see A3UIEvent
	 * @param event
	 */
	public void handleGroupEvent(A3GroupEvent event){}

	/**
	 * This method should be overriden by the application to receive UI events
	 * @see A3UIEvent
	 * @param event
	 */
	public void handleUIEvent(A3UIEvent event){}

	/**
	 * This method should be overriden by the application to receive error events
	 * @see A3ErrorEvent
	 * @param event
	 */
	public void handleErrorEvent(A3ErrorEvent event){}



	// Error handler that redirects exception to the system default handler.
	public class ErrorReportExceptionHandler implements Thread.UncaughtExceptionHandler {

		//private final Thread.UncaughtExceptionHandler defaultHandler;

		public ErrorReportExceptionHandler() {
			//this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		}

		@Override
		public void uncaughtException(Thread thread, Throwable throwable) {
			throwable.printStackTrace();
			Toast.makeText(getApplication(), thread + " " + throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show();
		}
	}
	
	public String getUUID(){
		return uuId;
	}
}

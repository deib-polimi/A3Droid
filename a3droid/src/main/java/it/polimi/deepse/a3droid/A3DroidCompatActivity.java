package it.polimi.deepse.a3droid;

import android.os.Bundle;
import android.provider.Settings.Secure;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public abstract class A3DroidCompatActivity extends AppCompatActivity{
	
	/**The device's UUID*/
	private String uuId;

	/* Load the native alljoyn_java library. */
	/*static {
		System.loadLibrary("alljoyn_java");
	}*/

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.uuId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);

		//org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());

		// Set new global handler
		Thread.setDefaultUncaughtExceptionHandler(new ErrorReportExceptionHandler());
	}

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

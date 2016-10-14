package it.polimi.deepse.a3droid.a3;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import it.polimi.deepse.a3droid.pattern.Observer;

/**
 * TODO: Describe
 */
public abstract class A3Bus extends Service implements Observer, A3BusInterface{

    protected static final String TAG = "a3droid.a3.A3Bus";

    /**
     * We don't use the bindery to communiate between any client and this
     * service so we return null.
     */
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        return null;
    }

    /**
     * Our onCreate() method is called by the Android appliation framework
     * when the service is first created.  We spin up a background thread
     * to handle any long-lived requests (pretty much all AllJoyn calls that
     * involve communication with remote processes) that need to be done and
     * insinuate ourselves into the list of observers of the model so we can
     * get event notifications.
     */
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        application = (A3Application)getApplication();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
        builder.setPriority(Notification.PRIORITY_MIN);


        Log.i(TAG, "onCreate(): startForeground()");
        startForeground(NOTIFICATION_ID, builder.build());
    }

    //protected static final int NOTIFICATION_ID = 0xdefaced;
    protected static final int NOTIFICATION_ID   = 0xa3d701d;

    /**
     * A reference to a descendant of the Android Application class that is
     * acting as the Model of our MVC-based application.
     */
    protected A3Application application = null;

    /**
     * The session with a service has been lost.
     */
    public enum A3GroupState {
        IDLE,
        ELECTION,
        ACTIVE,
        STACK,
        UNSTACK,
        PEER,
        MERGE,
        SPLIT
    }
}

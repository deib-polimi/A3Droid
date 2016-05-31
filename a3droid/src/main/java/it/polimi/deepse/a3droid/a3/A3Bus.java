package it.polimi.deepse.a3droid.a3;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import it.polimi.deepse.a3droid.pattern.Observer;

/**
 * Created by seadev on 5/20/16.
 */
public abstract class A3Bus extends Service implements Observer{

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
        a3Application = (A3Application)getApplication();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
        builder.setPriority(Notification.PRIORITY_MIN);


        Log.i(TAG, "onCreate(): startForeground()");
        startForeground(NOTIFICATION_ID, builder.build());
    }

    //protected static final int NOTIFICATION_ID = 0xdefaced;
    protected static final int NOTIFICATION_ID   = 0xa3d701d;

    /**
     * A reference to a descendent of the Android Application class that is
     * acting as the Model of our MVC-based application.
     */
    protected A3Application a3Application = null;

    /**
     * Enumeration of the states of the AllJoyn bus attachment.  This
     * lets us make a note to ourselves regarding where we are in the process
     * of preparing and tearing down the fundamental connection to the AllJoyn
     * bus.
     *
     * This should really be a more protected think, but for the sample we want
     * to show the user the states we are running through.  Because we are
     * really making a data hiding exception, and because we trust ourselves,
     * we don't go to any effort to prevent the UI from changing our state out
     * from under us.
     *
     * There are separate variables describing the states of the client
     * ("use") and service ("host") pieces.
     */
    public static enum BusState {
        DISCONNECTED,    /** The bus attachment is not connected to the AllJoyn bus */
        CONNECTED,        /** The  bus attachment is connected to the AllJoyn bus */
        DISCOVERING        /** The bus attachment is discovering remote attachments hosting chat channels */
    }

    /**
     * Enumeration of the states of a hosted chat channel.  This lets us make a
     * note to ourselves regarding where we are in the process of preparing
     * and tearing down the AllJoyn pieces responsible for providing the chat
     * service.  In order to be out of the IDLE state, the BusAttachment state
     * must be at least CONNECTED.
     */
    public static enum ServiceState {
        IDLE,            /** There is no hosted chat channel */
        REGISTERED,       /** The service has been registered to the bus */
        NAMED,            /** The well-known name for the channel has been successfully acquired */
        BOUND,            /** A session port has been bound for the channel */
        ADVERTISED,        /** The bus attachment has advertised itself as hosting an chat channel */
        CONNECTED       /** At least one remote device has connected to a session on the channel */
    }

    /**
     * Enumeration of the states of a hosted chat channel.  This lets us make a
     * note to ourselves regarding where we are in the process of preparing
     * and tearing down the AllJoyn pieces responsible for providing the chat
     * service.  In order to be out of the IDLE state, the BusAttachment state
     * must be at least CONNECTED.
     */
    public static enum ChannelState {
        IDLE,            /** There is no used chat channel */
        REGISTERED,       /** The channel has been registered to the bus */
        JOINED,            /** The session for the channel has been successfully joined */
    }

}

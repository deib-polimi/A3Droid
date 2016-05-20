package it.polimi.deepse.a3droid.a3;

import android.app.Application;

import java.util.ArrayList;
import java.util.List;

import it.polimi.deepse.a3droid.bus.alljoyn.AlljoynBus;

/**
 * Created by seadev on 5/20/16.
 */
public class A3Application extends Application {

    protected static final String TAG = "a3droid.A3Application";
    public static String PACKAGE_NAME;

    @Override
    public void onCreate() {
        super.onCreate();
        PACKAGE_NAME = getApplicationContext().getPackageName();
    }

    /**
     * This is the method that AllJoyn Service calls to tell us that an error
     * has happened.  We are provided a module, which corresponds to the high-
     * level "hunk" of code where the error happened, and a descriptive string
     * that we do not interpret.
     *
     * We expect the user interface code to sort out the best activity to tell
     * the user about the error (by calling getErrorModule) and then to call in
     * to get the string.
     */
    public synchronized void alljoynError(Module m, String s) {
        mModule = m;
        mErrorString = s;
        //notifyObservers(ALLJOYN_ERROR_EVENT);
    }

    /**
     * Return the high-level module that caught the last AllJoyn error.
     */
    public Module getErrorModule() {
        return mModule;
    }

    /**
     * The high-level module that caught the last AllJoyn error.
     */
    protected Module mModule = Module.NONE;

    /**
     * Enumeration of the high-level moudules in the system.  There is one
     * value per module.
     */
    public static enum Module {
        NONE,
        GENERAL,
        USE,
        HOST
    }

    /**
     * Return the error string stored when the last AllJoyn error happened.
     */
    public String getErrorString() {
        return mErrorString;
    }

    /**
     * The string representing the last AllJoyn error that happened in the
     * AllJoyn Service.
     */
    protected String mErrorString = "ER_OK";

    /**
     * The object we use in notifications to indicate that an AllJoyn error has
     * happened.
     */
    public static final String ALLJOYN_ERROR_EVENT = "ALLJOYN_ERROR_EVENT";

    /**
     * The channels list is the list of all well-known names that correspond to
     * channels we might conceivably be interested in.  We expect that the
     * "use" GUID will allow the local user to have this list displayed in a
     * "join channel" dialog, whereupon she will choose one.  This will
     * eventually result in a joinSession call out from the AllJoyn Service
     */
    protected List<String> mChannels = new ArrayList<String>();

    /**
     * Set the status of the "host" channel.  The AllJoyn Service part of the
     * appliciation is expected to make this call to set the status to reflect
     * the status of the underlying AllJoyn session.
     */
    public synchronized void setServiceState(AlljoynBus.ServiceState state) {
        mServiceState = state;
        //notifyObservers(HOST_CHANNEL_STATE_CHANGED_EVENT);
    }

    /**
     * Get the state of the "use" channel.
     */
    public synchronized AlljoynBus.ServiceState getServiceState() {
        return mServiceState;
    }
    /**
     * The "host" state which reflects the state of the part of the system
     * related to hosting an chat channel.  In a "real" application this kind
     * of detail probably isn't appropriate, but we want to do so for this
     * sample.
     */
    protected AlljoynBus.ServiceState mServiceState = AlljoynBus.ServiceState.IDLE;

    /**
     * Set the status of the "use" channel.  The AllJoyn Service part of the
     * appliciation is expected to make this call to set the status to reflect
     * the status of the underlying AllJoyn session.
     */
    public synchronized void setChannelState(AlljoynBus.ChannelState state) {
        mChannelState = state;
        //notifyObservers(USE_CHANNEL_STATE_CHANGED_EVENT);
    }

    /**
     * Get the state of the "use" channel.
     */
    public synchronized AlljoynBus.ChannelState getChannelState() {
        return mChannelState;
    }

    /**
     * The "use" state which reflects the state of the part of the system
     * related to using a remotely hosted chat channel.  In a "real" application
     * this kind of detail probably isn't appropriate, but we want to do so for
     * this sample.
     */
    protected AlljoynBus.ChannelState mChannelState = AlljoynBus.ChannelState.IDLE;

    /**
     * Set the name part of the "host" channel.  Since we are going to "use" a
     * channel that is implemented remotely and discovered through an AllJoyn
     * FoundAdvertisedName, this must come from a list of advertised names.
     * These names are our channels, and so we expect the GUI to choose from
     * among the list of channels it retrieves from getFoundChannels().
     *
     * Since we are talking about user-level interactions here, we are talking
     * about the final segment of a well-known name representing a channel at
     * this point.
     */
    public synchronized void setGroupName(String name) {
        groupName = name;
        //notifyObservers(HOST_CHANNEL_STATE_CHANGED_EVENT);
    }

    /**
     * Get the name part of the "use" channel.
     */
    public synchronized String getGroupName() {
        return groupName;
    }

    /**
     * The name of the "host" channel which the user has selected.
     */
    protected String groupName;
}

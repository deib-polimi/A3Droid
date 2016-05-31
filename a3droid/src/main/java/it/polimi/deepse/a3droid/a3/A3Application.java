package it.polimi.deepse.a3droid.a3;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import it.polimi.deepse.a3droid.bus.alljoyn.AlljoynBus;
import it.polimi.deepse.a3droid.pattern.Observable;
import it.polimi.deepse.a3droid.pattern.Observer;

/**
 * Created by seadev on 5/20/16.
 */
public class A3Application extends Application implements Observable{

    protected static final String TAG = "a3droid.A3Application";

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        super.onCreate();
        PACKAGE_NAME = getApplicationContext().getPackageName();
        Intent intent = new Intent(this, AlljoynBus.class);
        mRunningService = startService(intent);
        if (mRunningService == null)
            Log.i(TAG, "onCreate(): failed to startService()");
    }

    public static String PACKAGE_NAME;
    private ComponentName mRunningService = null;

    /**
     * Since our application is "rooted" in this class derived from Appliation
     * and we have a long-running service, we can't just call finish in one of
     * the Activities.  We have to orchestrate it from here.  We send an event
     * notification out to all of our obsservers which tells them to exit.
     *
     * Note that as a result of the notification, all of the observers will
     * stop -- as they should.  One of the things that will stop is the AllJoyn
     * Service.  Notice that it is started in the onCreate() method of the
     * Application.  As noted in the Android documentation, the Application
     * class never gets torn down, nor does it provide a way to tear itself
     * down.  Thus, if the Chat application is ever run again, we need to have
     * a way of detecting the case where it is "re-run" and then "re-start"
     * the service.
     */
    public void quit() {
        notifyObservers(APPLICATION_QUIT_EVENT);
        mRunningService = null;
    }

    public static final String APPLICATION_QUIT_EVENT = "APPLICATION_QUIT_EVENT";

    /**
     * Application components call this method to indicate that they are alive
     * and may have need of the AllJoyn Service.  This is required because the
     * Android Application class doesn't have an end to its lifecycle other
     * than through "kill -9".  See quit().
     */
    public void checkin() {
        Log.i(TAG, "checkin()");
        if (mRunningService == null) {
            Log.i(TAG, "checkin():  Starting the AllJoynService");
            Intent intent = new Intent(this, AlljoynBus.class);
            mRunningService = startService(intent);
            if (mRunningService == null) {
                Log.i(TAG, "checkin(): failed to startService()");
            }
        }
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
    public synchronized void busError(Module m, String s) {
        mModule = m;
        mErrorString = s;
        notifyObservers(ALLJOYN_ERROR_EVENT);
    }

    /**
     * The object we use in notifications to indicate that an AllJoyn error has
     * happened.
     */
    public static final String ALLJOYN_ERROR_EVENT = "ALLJOYN_ERROR_EVENT";

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
     * Called from the AllJoyn Service when it gets a FoundAdvertisedName.  We
     * know by construction that the advertised name will correspond to an chat
     * channel.  Note that the channel here is the complete well-known name of
     * the bus attachment advertising the channel.  In most other places it is
     * simply the channel name, which is the final segment of the well-known
     * name.
     */
    public synchronized void addFoundGroup(String channel) {
        Log.i(TAG, "addFoundGroup(" + channel + ")");
        removeFoundGroup(channel);
        mGroups.add(channel);
        Log.i(TAG, "addFoundGroup(): added " + channel);
    }

    /**
     * Called from the AllJoyn Service when it gets a LostAdvertisedName.  We
     * know by construction that the advertised name will correspond to an chat
     * channel.
     */
    public synchronized void removeFoundGroup(String channel) {
        Log.i(TAG, "removeFoundGroup(" + channel + ")");

        for (Iterator<String> i = mGroups.iterator(); i.hasNext();) {
            String string = i.next();
            if (string.equals(channel)) {
                Log.i(TAG, "removeFoundGroup(): removed " + channel);
                i.remove();
            }
        }
    }

    /**
     * Whenever the user is asked for a channel to join, it nees the list of
     * channels found via FoundAdvertisedName.  This method provides that
     * list.  Since we have no idea how or when the caller is going to access
     * or change the list, and we are deeply paranoid, we provide a deep copy.
     */
    public synchronized List<String> getFoundChannels() {
        Log.i(TAG, "getFoundChannels()");
        List<String> clone = new ArrayList<String>(mGroups.size());
        for (String string : mGroups) {
            Log.i(TAG, "getFoundChannels(): added " + string);
            clone.add(new String(string));
        }
        return clone;
    }

    /**
     * The channels list is the list of all well-known names that correspond to
     * channels we might conceivably be interested in.  We expect that the
     * "use" GUID will allow the local user to have this list displayed in a
     * "join channel" dialog, whereupon she will choose one.  This will
     * eventually result in a joinSession call out from the AllJoyn Service
     */
    private List<String> mGroups = new ArrayList<String>();

    /**
     * This object is really the model of a model-view-controller architecture.
     * The observer/observed design pattern is used to notify view-controller
     * objects when the model has changed.  The observed object is this object,
     * the model.  Observers correspond to the view-controllers which in this
     * case are the Android Activities (corresponding to the use tab and the
     * hsot tab) and the Android Service that does all of the AllJoyn work.
     * When an observer wants to register for change notifications, it calls
     * here.
     */
    public synchronized void addObserver(Observer obs) {
        Log.i(TAG, "addObserver(" + obs + ")");
        if (mObservers.indexOf(obs) < 0) {
            mObservers.add(obs);
        }
    }

    /**
     * When an observer wants to unregister to stop receiving change
     * notifications, it calls here.
     */
    public synchronized void deleteObserver(Observer obs) {
        Log.i(TAG, "deleteObserver(" + obs + ")");
        mObservers.remove(obs);
    }

    public synchronized List<Observer> getObservers(){
        return mObservers;
    }

    /**
     * This object is really the model of a model-view-controller architecture.
     * The observer/observed design pattern is used to notify view-controller
     * objects when the model has changed.  The observed object is this object,
     * the model.  Observers correspond to the view-controllers which in this
     * case are the Android Activities (corresponding to the use tab and the
     * hsot tab) and the Android Service that does all of the AllJoyn work.
     * When the model (this object) wants to notify its observers that some
     * interesting event has happened, it calles here and provides an object
     * that identifies what has happened.  To keep things obvious, we pass a
     * descriptive string which is then sent to all observers.  They can decide
     * to act or not based on the content of the string.
     */
    private void notifyObservers(Object arg) {
        Log.i(TAG, "notifyObservers(" + arg + ")");
        for (Observer obs : mObservers) {
            Log.i(TAG, "notify observer = " + obs);
            obs.update(this, arg);
        }
    }

    /**
     * The observers list is the list of all objects that have registered with
     * us as observers in order to get notifications of interesting events.
     */
    private List<Observer> mObservers = new ArrayList<Observer>();
}

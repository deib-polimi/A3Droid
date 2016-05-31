package it.polimi.deepse.a3droid.a3;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.pattern.Observable;
import it.polimi.deepse.a3droid.pattern.Observer;

/**
 * Created by seadev on 5/20/16.
 */
public abstract class A3Channel implements A3ChannelInterface, Observable {

    protected static final String TAG = "a3droid.A3Channel";

    public A3Channel(String groupName, A3Application application){
        this.setGroupName(groupName);
        addObservers(application.getObservers());
    }

    public void connect(){
        notifyObservers(A3Channel.CONNECT_EVENT);
    }

    public void disconnect(){
        notifyObservers(A3Channel.DISCONNECT_EVENT);
        clearObservers();
    }

    public void joinGroup(){
        notifyObservers(A3Channel.JOIN_CHANNEL_EVENT);
    }

    public void createGroup(){
        notifyObservers(A3Channel.START_SERVICE_EVENT);
    }

    /** A3ChannelInterface **/

    @Override
    public void receiveUnicast(A3Message message) {
        Log.i(TAG, "UNICAST : " + message.object + " TO " + message.addresses);
    }

    @Override
    public void receiveMulticast(A3Message message) {
        Log.i(TAG, "MULTICAST : " + message.object + " TO " + message.addresses);
    }

    @Override
    public void receiveBroadcast(A3Message message) {
        Log.i(TAG, "BROADCAST : " + message.object);
    }

    /**
     * Set the status of the "host" channel.  The AllJoyn Service part of the
     * appliciation is expected to make this call to set the status to reflect
     * the status of the underlying AllJoyn session.
     */
    public synchronized void setServiceState(A3Bus.ServiceState state) {
        mServiceState = state;
        notifyObservers(SERVICE_STATE_CHANGED_EVENT);
    }

    /**
     * Get the state of the "use" channel.
     */
    public synchronized A3Bus.ServiceState getServiceState() {
        return mServiceState;
    }

    /**
     * The object we use in notifications to indicate that the state of the
     * "host" channel or its name has changed.
     */
    public static final String SERVICE_STATE_CHANGED_EVENT = "SERVICE_STATE_CHANGED_EVENT";

    /**
     * The "host" state which reflects the state of the part of the system
     * related to hosting an chat channel.  In a "real" application this kind
     * of detail probably isn't appropriate, but we want to do so for this
     * sample.
     */
    protected A3Bus.ServiceState mServiceState = A3Bus.ServiceState.IDLE;

    /**
     * Set the status of the "use" channel.  The AllJoyn Service part of the
     * appliciation is expected to make this call to set the status to reflect
     * the status of the underlying AllJoyn session.
     */
    public synchronized void setChannelState(A3Bus.ChannelState state) {
        mChannelState = state;
        notifyObservers(CHANNEL_STATE_CHANGED_EVENT);
    }

    /**
     * The object we use in notifications to indicate that the state of the
     * "use" channel or its name has changed.
     */
    public static final String CHANNEL_STATE_CHANGED_EVENT = "CHANNEL_STATE_CHANGED_EVENT";

    /**
     * Get the state of the "use" channel.
     */
    public synchronized A3Bus.ChannelState getChannelState() {
        return mChannelState;
    }

    /**
     * The "use" state which reflects the state of the part of the system
     * related to using a remotely hosted chat channel.  In a "real" application
     * this kind of detail probably isn't appropriate, but we want to do so for
     * this sample.
     */
    protected A3Bus.ChannelState mChannelState = A3Bus.ChannelState.IDLE;

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
    private synchronized void setGroupName(String name) {
        groupName = name;
        notifyObservers(SERVICE_STATE_CHANGED_EVENT);
    }

    public synchronized void setBusState(A3Bus.BusState state) {
        mBusState = state;
        //TODO
        //notifyObservers(CHANNEL_STATE_CHANGED_EVENT);
    }

    public A3Bus.BusState getBusState() {
        return mBusState;
    }

    /**
     * The state of the AllJoyn bus attachment.
     */
    protected A3Bus.BusState mBusState = A3Bus.BusState.DISCONNECTED;

    /**
     * Get the name part of the "use" channel.
     */
    public synchronized String getGroupName() {
        return groupName;
    }

    /**
     * The name of the "host" channel which the user has selected.
     */
    protected String groupName = null;

    /**
     * Get the channel id
     */
    public synchronized void setChannelId(String id) {
        this.channelId = id;
        //notifyObservers(SERVICE_STATE_CHANGED_EVENT);
    }

    /**
     * Get the name part of the "use" channel.
     */
    public synchronized String getChannelId() {
        return channelId;
    }

    /**
     * The channel id uniquely identifies the channel
     */
    protected String channelId = null;

    public int getSessionId(){
        return mSessionId;
    }

    public void setSessionId(int sessionId){
        mSessionId = sessionId;
    }

    /**
     * The session identifier that the application
     * provides for remote devices.  Set to -1 if not connected.
     */
    int mSessionId = -1;

    /**
     * This is the method that the "use" tab user interface calls when the
     * user indicates that she wants to join a channel.  The channel name
     * must have been previously set with a call to setUseChannelName().
     * The "use" channel is the channel that we talk about in the "Use" tab.
     * Since it's a remote channel in a remote bus attachment, we need to tell
     * the AllJoyn Service to go join the corresponding session.
     */
    public synchronized void useJoinChannel() {
        //clearHistory();
        notifyObservers(CHANNEL_STATE_CHANGED_EVENT);
        notifyObservers(JOIN_CHANNEL_EVENT);
    }

    /**
     * The object we use in notifications to indicate that a channel must be setup.
     */
    public static final String CONNECT_EVENT = "CONNECT_EVENT";

    /**
     * The object we use in notifications to indicate that a channel must be setup.
     */
    public static final String DISCONNECT_EVENT = "DISCONNECT_EVENT";

    /**
     * The object we use in notifications to indicate that user has requested
     * that we join a channel in the "use" tab.
     */
    public static final String JOIN_CHANNEL_EVENT = "JOIN_CHANNEL_EVENT";

    /**
     * This is the method that the "use" tab user interface calls when the
     * user indicates that she wants to leave a channel.  Since we're
     * talking about a remote channel corresponding to a session with a
     * remote bus attachment, we needto tell the AllJoyn Service to leave
     * the corresponding session.
     */
    public synchronized void useLeaveChannel() {
        notifyObservers(CHANNEL_STATE_CHANGED_EVENT);
        notifyObservers(USE_LEAVE_CHANNEL_EVENT);
    }

    /**
     * The object we use in notifications to indicate that user has requested
     * that we leave a channel in the "use" tab.
     */
    public static final String USE_LEAVE_CHANNEL_EVENT = "USE_LEAVE_CHANNEL_EVENT";

    /**
     * This is the method that the "host" tab user interface calls when the
     * user indicates that she wants to start hosting a channel.
     */
    public synchronized void startGroupService() {
        notifyObservers(SERVICE_STATE_CHANGED_EVENT);
        notifyObservers(START_SERVICE_EVENT);
    }

    /**
     * The object we use in notifications to indicate that user has requested
     * that we initialize the host channel parameters in the "use" tab.
     */
    public static final String START_SERVICE_EVENT = "START_SERVICE_EVENT";

    /**
     * This is the method that the "host" tab user interface calls when the
     * user indicates that she wants to stop hosting a channel.
     */
    public synchronized void hostStopChannel() {
        notifyObservers(SERVICE_STATE_CHANGED_EVENT);
        notifyObservers(STOP_SERVICE_EVENT);
    }

    /**
     * The object we use in notifications to indicate that user has requested
     * that we initialize the host channel parameters in the "use" tab.
     */
    public static final String STOP_SERVICE_EVENT = "STOP_SERVICE_EVENT";

    /**
     * The object we use in notifications to indicate that the the user has
     * entered a message and it is queued to be sent to the outside world.
     */
    public static final String OUTBOUND_CHANGED_EVENT = "OUTBOUND_CHANGED_EVENT";

    /** Observable **/
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

    public synchronized void addObservers(List<Observer> observers) {
        Log.i(TAG, "addObservers(" + observers + ")");
        mObservers.addAll(observers);
    }

    /**
     * When an observer wants to unregister to stop receiving change
     * notifications, it calls here.
     */
    public synchronized void deleteObserver(Observer obs) {
        Log.i(TAG, "deleteObserver(" + obs + ")");
        mObservers.remove(obs);
    }

    /**
     * When an observer wants to unregister to stop receiving change
     * notifications, it calls here.
     */
    public synchronized void clearObservers() {
        Log.i(TAG, "clearObservers()");
        mObservers.clear();
    }

    protected void notifyObservers(Object arg) {
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

    public synchronized A3Message getOutboundItem() {
        if (mOutbound.isEmpty()) {
            return null;
        } else {
            return mOutbound.remove(0);
        }
    }

    /**
     * Whenever the local user types a message for distribution to the channel
     * it calls newLocalMessage.  We are called to queue up the message and
     * send a notification to all of our observers indicating that the we have
     * something ready to go out.  We expect that the AllJoyn Service will
     * eventually respond by calling back in here to get items off of the queue
     * and send them down the session corresponding to the channel.
     */
    public void addOutboundItem(A3Message message) {
        mOutbound.add(message);
        notifyObservers(OUTBOUND_CHANGED_EVENT);
    }

    /**
     * The outbound list is the list of all messages that have been originated
     * by our local user and are designed for the outside world.
     */
    private List<A3Message> mOutbound = new ArrayList<A3Message>();

    public static final int BROADCAST_MSG = 0;
    public static final int UNICAST_MSG = 1;
    public static final int MULTICAST_MSG = 2;
}

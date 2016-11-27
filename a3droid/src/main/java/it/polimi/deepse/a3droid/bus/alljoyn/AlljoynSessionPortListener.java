package it.polimi.deepse.a3droid.bus.alljoyn;

import android.util.Log;

import org.alljoyn.bus.ProxyBusObject;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.SignalEmitter;

/**
 * TODO
 */
public class AlljoynSessionPortListener extends SessionPortListener {

    private static final String TAG = "AJSessionPortListener";
    private final AlljoynGroupChannel channel;

    public AlljoynSessionPortListener(AlljoynGroupChannel channel){
        this.channel = channel;
    }

    /**
     * This method is called when a client tries to join the session
     * we have bound.  It asks us if we want to accept the client into
     * our session.
     *
     * In the class documentation for the SessionPortListener note that
     * it is a requirement for this method to be multithread safe.
     * Since we never access any shared state, this requirement is met.
     */
    public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
        Log.i(TAG, "SessionPortListener.acceptSessionJoiner(" + sessionPort + ", " + joiner + ", " + sessionOpts.toString() + ")");

                /*
                 * Accept anyone who can get our contact port correct.
                 */
        return sessionPort == AlljoynBus.CONTACT_PORT;
    }

    /**
     * If we return true in acceptSessionJoiner, we admit a new client
     * into our session.  The session does not really exist until a
     * client joins, at which time the session is created and a session
     * ID is assigned.  This method communicates to us that this event
     * has happened, and provides the new session ID for us to use.
     *
     * In the class documentation for the SessionPortListener note that
     * it is a requirement for this method to be multithread safe.
     * Since we never access any shared state, this requirement is met.
     *
     * See comments in joinSession for why the hosted chat interface is
     * created here.
     */
    public void sessionJoined(short sessionPort, int sessionId, String joiner) {
        Log.i(TAG, "SessionPortListener.sessionJoined(" + sessionPort + ", " + sessionId + ", " + joiner + ")");
        //channel.setSessionId(sessionId);

        ProxyBusObject mProxyObj;
        mProxyObj = channel.getBus().getProxyBusObject(AlljoynBus.SERVICE_PATH + "." + channel.getGroupName() + channel.getGroupNameSuffix(), AlljoynBus.OBJECT_PATH,
                sessionId, new Class<?>[]{AlljoynServiceInterface.class});
        channel.setServiceInterface(mProxyObj.getInterface(AlljoynServiceInterface.class), false);

        SignalEmitter emitter = new SignalEmitter(channel.getService(), sessionId, SignalEmitter.GlobalBroadcast.Off);
        channel.getService().setServiceSignalEmitterInterface(emitter.getInterface(AlljoynServiceInterface.class));
    }
}

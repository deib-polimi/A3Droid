package it.polimi.deepse.a3droid.bus.alljoyn;

import android.util.Log;

import org.alljoyn.bus.SessionListener;

import it.polimi.deepse.a3droid.a3.A3Application;

/**
 * Class implementing Alljoyn SessionListener logic responsible for handling session events.
 * @author Danilo F. Mendonça
 */
public class AlljoynSessionListener extends SessionListener{

    private static final String TAG = "a3droid.bus.AlljoynSL";

    public AlljoynSessionListener(A3Application application, AlljoynChannel channel){
        this.a3Application = application;
        this.channel = channel;
    }

    A3Application a3Application = null;
    AlljoynChannel channel = null;

    /**
     * This method is called when the last remote participant in the
     * chat session leaves.
     * In the class documentation for the BusListener note that it is a
     * requirement for this method to be multithread safe.  This is
     * accomplished by the use of a monitor on the ChatApplication as
     * exemplified by the synchronized attribute of the removeFoundGroup
     * method there.
     */
    @Override
    public void sessionLost(int sessionId, int reason) {
        Log.i(TAG, "AlljoynSessionListener.sessionLost(sessionId=" + sessionId + ",reason=" + reason + ")");
        a3Application.busError(A3Application.Module.USE, "The session has been lost");
        channel.setSessionId(-1);
        channel.handleEvent(AlljoynBus.AlljoynEvent.SESSION_LOST, reason);
    }

    @Override
    public void sessionMemberAdded(int sessionId, String uniqueName) {
        Log.i(TAG, "AlljoynSessionListener.sessionMemberAdded(sessionId=" + sessionId + ",uniqueName=" + uniqueName + ")");
        channel.handleEvent(AlljoynBus.AlljoynEvent.MEMBER_JOINED, uniqueName);
    }

    @Override
    public void sessionMemberRemoved(int sessionId, String uniqueName) {
        Log.i(TAG, "AlljoynSessionListener.sessionMemberRemoved(sessionId=" + sessionId + ",uniqueName=" + uniqueName + ")");
        channel.handleEvent(AlljoynBus.AlljoynEvent.MEMBER_LEFT, uniqueName);
    }
}

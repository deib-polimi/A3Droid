package it.polimi.deepse.a3droid.bus.alljoyn;

import android.util.Log;

import org.alljoyn.bus.SessionListener;
import org.greenrobot.eventbus.EventBus;

import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.bus.alljoyn.events.AlljoynEvent;

/**
 * Class implementing Alljoyn SessionListener logic responsible for handling session events.
 * @author Danilo F. Mendonça
 */
public class AlljoynSessionListener extends SessionListener{

    private static final String TAG = "a3droid.bus.AlljoynSL";
    private A3Application a3Application = null;
    private AlljoynGroupChannel channel = null;

    public AlljoynSessionListener(A3Application application, AlljoynGroupChannel channel){
        this.a3Application = application;
        this.channel = channel;
    }

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
        a3Application.removeFoundGroup(channel.getGroupName(), channel.getGroupNameSuffix());

        channel.setSessionId(-1);
        EventBus.getDefault().post(new AlljoynEvent(AlljoynEvent.AlljoynEventType.SESSION_LOST, channel.getGroupName(), reason));
    }

    @Override
    public void sessionMemberAdded(int sessionId, String uniqueName) {
        Log.i(TAG, "AlljoynSessionListener.sessionMemberAdded(sessionId=" + sessionId + ",uniqueName=" + uniqueName + ")");
        EventBus.getDefault().post(new AlljoynEvent(AlljoynEvent.AlljoynEventType.MEMBER_JOINED, channel.getGroupName(), uniqueName));
    }

    @Override
    public void sessionMemberRemoved(int sessionId, String uniqueName) {
        Log.i(TAG, "AlljoynSessionListener.sessionMemberRemoved(sessionId=" + sessionId + ",uniqueName=" + uniqueName + ")");
        EventBus.getDefault().post(new AlljoynEvent(AlljoynEvent.AlljoynEventType.MEMBER_LEFT, channel.getGroupName(), uniqueName));
    }
}

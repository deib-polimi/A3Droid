package it.polimi.deepse.a3droid.a3;

import android.os.Handler;
import android.os.Message;

import it.polimi.deepse.a3droid.pattern.Timer;
import it.polimi.deepse.a3droid.pattern.TimerInterface;
import it.polimi.deepse.a3droid.utility.RandomWait;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class A3EventHandler extends Handler implements TimerInterface{

    private A3Application application;
    private A3Channel channel;
    private RandomWait randomWait = new RandomWait();

    public A3EventHandler(A3Application application, A3Channel channel){
        this.application = application;
        this.channel = channel;
    }

    public void handleMessage(Message msg) {
        handleEvent(A3Bus.A3Event.values()[msg.what], msg.obj);
    }

    public void handleEvent(A3Bus.A3Event event, Object obj){
        switch (event) {
            case GROUP_CREATED:
                //A node also needs to join the group it has created
                channel.setGroupState(A3Bus.A3GroupState.CREATED);
                channel.joinGroup();
                break;
            case GROUP_DESTROYED:
                channel.setGroupState(A3Bus.A3GroupState.IDLE);
                break;
            case GROUP_LOST:
                channel.setGroupState(A3Bus.A3GroupState.IDLE);
                channel.deactivateActiveRole();
                break;
            case GROUP_JOINED:
                channel.setGroupState(A3Bus.A3GroupState.JOINT);
                new Timer(this, WAIT_AND_QUERY_ROLE_EVENT, WAIT_AND_QUERY_ROLE_FIXED_TIME_1).start();
                break;
            case GROUP_LEFT:
                channel.setGroupState(A3Bus.A3GroupState.CREATED);
                channel.deactivateActiveRole();
                break;
            case MEMBER_JOINED:
            case MEMBER_LEFT:
                notifyView(event, (String) obj);
                break;
            case SUPERVISOR_LEFT:
                channel.setGroupState(A3Bus.A3GroupState.CREATED);
                handleSupervisorLeftEvent();
                break;
            default:
                break;
        }
    }

    private void handleSupervisorLeftEvent(){
        channel.deactivateActiveRole();
        channel.clearSupervisor();
        new Timer(this, WAIT_AND_QUERY_ROLE_EVENT,
                randomWait.next(WAIT_AND_QUERY_ROLE_FIXED_TIME_2, WAIT_AND_QUERY_ROLE_RANDOM_TIME)
        ).start();
    }

    public void handleTimeEvent(int reason){
        switch (reason){
            case WAIT_AND_QUERY_ROLE_EVENT:
                channel.queryRole();
                break;
            default:
                break;
        }
    }

    private void notifyView(A3Bus.A3Event event, String memberId){
        Message msg = channel.getView().obtainMessage();
        msg.what = event.ordinal();
        msg.obj = memberId;
        channel.getView().sendMessage(msg);
    }

    private static final int WAIT_AND_QUERY_ROLE_EVENT = 0;
    /** Used as fixed time after a GROUP_JOINED event **/
    private static final int WAIT_AND_QUERY_ROLE_FIXED_TIME_1 = 3000;
    /** Used as fixed time part after a MEMBER_LEFT event **/
    private static final int WAIT_AND_QUERY_ROLE_FIXED_TIME_2 = 0;
    /** Used as random time part after a MEMBER_LEFT event **/
    private static final int WAIT_AND_QUERY_ROLE_RANDOM_TIME = 1000;
}

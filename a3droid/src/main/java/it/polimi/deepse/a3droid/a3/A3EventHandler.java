package it.polimi.deepse.a3droid.a3;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import it.polimi.deepse.a3droid.pattern.Timer;
import it.polimi.deepse.a3droid.pattern.TimerInterface;
import it.polimi.deepse.a3droid.utility.RandomWait;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class A3EventHandler extends HandlerThread implements TimerInterface{

    private Handler mHandler;
    private A3Application application;
    private A3GroupChannel channel;

    public A3EventHandler(A3Application application, A3GroupChannel channel){
        super("A3EventHandler_" + channel.getGroupName());
        this.application = application;
        this.channel = channel;
        start();
    }

    /**
     * The session with a service has been lost.
     */
    public enum A3Event {
        GROUP_CREATED,
        GROUP_DESTROYED,
        GROUP_LOST,
        GROUP_JOINED,
        GROUP_LEFT,
        MEMBER_LEFT,
        MEMBER_JOINED,
        SUPERVISOR_LEFT,
        SUPERVISOR_ELECTED,
        STACK_STARTED,
        STACK_FINISHED,
        REVERSE_STACK_STARTED,
        REVERSE_STACK_FINISHED,
        MERGE_STARTED,
        MERGE_FINISHED,
        SPLIT_STARTED,
        SPLIT_FINISHED
    }

    public Message obtainMessage() {
        return mHandler.obtainMessage();
    }

    public void sendMessage(Message msg) {
        mHandler.sendMessage(msg);
    }



    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        mHandler = new Handler(getLooper()) {

            @Override
            public void handleMessage(Message msg) {
                handleEvent(A3Event.values()[msg.what], msg.obj);
            }
        };
    }

    public void handleEvent(A3Event event, Object obj) {
        switch (event) {
            case GROUP_CREATED:
                //A node also needs to join the group it has created
                channel.joinGroup();
                break;
            case GROUP_DESTROYED:
            case GROUP_LOST:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.IDLE);
                channel.deactivateActiveRole();
                break;
            case GROUP_JOINED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ELECTION);
                new Timer(this, WAIT_AND_QUERY_ROLE_EVENT, WAIT_AND_QUERY_ROLE_FIXED_TIME_1).start();
                break;
            case GROUP_LEFT:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.IDLE);
                channel.deactivateActiveRole();
                break;
            case MEMBER_JOINED:
            case MEMBER_LEFT:
                notifyView(event, (String) obj);
                break;
            case SUPERVISOR_LEFT:
                if (channel.getGroupState() == A3GroupDescriptor.A3GroupState.ACTIVE) {
                    channel.setGroupState(A3GroupDescriptor.A3GroupState.ELECTION);
                    handleSupervisorLeftEvent();
                }
                break;
            case SUPERVISOR_ELECTED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            case STACK_STARTED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.STACK);
                break;
            case STACK_FINISHED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            case REVERSE_STACK_STARTED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.REVERSE_STACK);
                break;
            case REVERSE_STACK_FINISHED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            case MERGE_STARTED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.MERGE);
                break;
            case MERGE_FINISHED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            case SPLIT_STARTED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.SPLIT);
                break;
            case SPLIT_FINISHED:
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            default:
                break;
        }
    }

    private void handleSupervisorLeftEvent() {
        channel.deactivateActiveRole();
        channel.clearSupervisor();
        new Timer(this, WAIT_AND_QUERY_ROLE_EVENT,
                randomWait.next(WAIT_AND_QUERY_ROLE_FIXED_TIME_2, WAIT_AND_QUERY_ROLE_RANDOM_TIME)
        ).start();
    }

    public void handleTimeEvent(int reason, Object object) {
        switch (reason) {
            case WAIT_AND_QUERY_ROLE_EVENT:
                channel.queryRole();
                break;
            default:
                break;
        }
    }

    private RandomWait randomWait = new RandomWait();

    private static final int WAIT_AND_QUERY_ROLE_EVENT = 0;
    /** Used as fixed time after a GROUP_JOINED event **/
    private static final int WAIT_AND_QUERY_ROLE_FIXED_TIME_1 = 3000;
    /** Used as fixed time part after a MEMBER_LEFT event **/
    private static final int WAIT_AND_QUERY_ROLE_FIXED_TIME_2 = 0;
    /** Used as random time part after a MEMBER_LEFT event **/
    private static final int WAIT_AND_QUERY_ROLE_RANDOM_TIME = 1000;

    private void notifyView(A3Event event, String memberId) {
        Message msg = channel.getView().obtainMessage();
        msg.what = event.ordinal();
        msg.obj = memberId;
        channel.getView().sendMessage(msg);
    }
}

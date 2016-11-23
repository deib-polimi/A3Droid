package it.polimi.deepse.a3droid.a3;

import org.greenrobot.eventbus.EventBus;

import it.polimi.deepse.a3droid.a3.events.A3GroupEvent;
import it.polimi.deepse.a3droid.pattern.Timer;
import it.polimi.deepse.a3droid.pattern.TimerInterface;
import it.polimi.deepse.a3droid.utility.RandomWait;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class A3EventHandler implements TimerInterface{

    private A3Application application;
    private A3GroupChannel channel;

    public A3EventHandler(A3Application application, A3GroupChannel channel){
        this.application = application;
        this.channel = channel;
    }

    public void handleEvent(A3GroupEvent.A3GroupEventType event, Object obj) {

        switch (event) {
            case GROUP_CREATED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.joinGroup();
                break;
            case GROUP_DESTROYED:
            case GROUP_LOST:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.IDLE);
                channel.deactivateActiveRole();
                break;
            case GROUP_JOINED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ELECTION);
                channel.getGroupView().addGroupMember(channel.getChannelId());
                new Timer(this, WAIT_AND_QUERY_ROLE_EVENT, WAIT_AND_QUERY_ROLE_FIXED_TIME_1).start();
                break;
            case GROUP_LEFT:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.IDLE);
                channel.deactivateActiveRole();
                break;
            case GROUP_STATE_CHANGED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event, obj));
                break;
            case MEMBER_JOINED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event, obj));
                channel.getGroupView().addGroupMember((String) obj);
                break;
            case MEMBER_LEFT:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event, obj));
                channel.getGroupView().removeGroupMember((String) obj);
                break;
            case SUPERVISOR_LEFT:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                new Timer(this, WAIT_AND_HANDLE_SUPERVISOR_LEFT_EVENT,
                        randomWait.next(WAIT_AND_HANDLE_SUPERVISOR_LEFT_FIXED_TIME, WAIT_AND_HANDLE_SUPERVISOR_LEFT_RANDOM_TIME)
                ).start();
                break;
            case SUPERVISOR_ELECTED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            case STACK_STARTED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.STACK);
                break;
            case STACK_FINISHED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            case REVERSE_STACK_STARTED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.REVERSE_STACK);
                break;
            case REVERSE_STACK_FINISHED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            case MERGE_STARTED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.MERGE);
                break;
            case MERGE_FINISHED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            case SPLIT_STARTED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.SPLIT);
                break;
            case SPLIT_FINISHED:
                EventBus.getDefault().post(new A3GroupEvent(channel.getGroupName(), event));
                channel.setGroupState(A3GroupDescriptor.A3GroupState.ACTIVE);
                break;
            default:
                break;
        }
    }

    private void handleSupervisorLeftEvent() {
        if(channel.getGroupState().equals(A3GroupDescriptor.A3GroupState.ACTIVE)) {
            channel.setGroupState(A3GroupDescriptor.A3GroupState.ELECTION);
            channel.deactivateActiveRole();
            channel.clearSupervisorId();
            new Timer(this, WAIT_AND_QUERY_ROLE_EVENT,
                    randomWait.next(WAIT_AND_QUERY_ROLE_FIXED_TIME_2, WAIT_AND_QUERY_ROLE_RANDOM_TIME)
            ).start();
        }
    }

    public void handleTimeEvent(int reason, Object object) {
        switch (reason) {
            case WAIT_AND_HANDLE_SUPERVISOR_LEFT_EVENT:
                handleSupervisorLeftEvent();
                break;
            case WAIT_AND_QUERY_ROLE_EVENT:
                channel.queryRole();
                break;
            default:
                break;
        }
    }

    private RandomWait randomWait = new RandomWait();

    private static final int WAIT_AND_HANDLE_SUPERVISOR_LEFT_EVENT = 0;
    private static final int WAIT_AND_HANDLE_SUPERVISOR_LEFT_FIXED_TIME = 500;
    private static final int WAIT_AND_HANDLE_SUPERVISOR_LEFT_RANDOM_TIME = 500;

    private static final int WAIT_AND_QUERY_ROLE_EVENT = 1;
    /** Used as fixed time after a GROUP_JOINED event **/
    private static final int WAIT_AND_QUERY_ROLE_FIXED_TIME_1 = 3000;
    /** Used as fixed time part after a MEMBER_LEFT event **/
    private static final int WAIT_AND_QUERY_ROLE_FIXED_TIME_2 = 0;
    /** Used as random time part after a MEMBER_LEFT event **/
    private static final int WAIT_AND_QUERY_ROLE_RANDOM_TIME = 1000;
}

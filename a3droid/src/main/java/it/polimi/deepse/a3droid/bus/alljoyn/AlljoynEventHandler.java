package it.polimi.deepse.a3droid.bus.alljoyn;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3GroupDescriptor;
import it.polimi.deepse.a3droid.a3.events.A3GroupEvent;
import it.polimi.deepse.a3droid.bus.alljoyn.events.AlljoynDuplicatedSessionEvent;
import it.polimi.deepse.a3droid.bus.alljoyn.events.AlljoynEvent;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class AlljoynEventHandler{

    private final String TAG;
    private A3Application application;
    private AlljoynGroupChannel channel;

    public AlljoynEventHandler(A3Application application, AlljoynGroupChannel channel){
        TAG = "AlljoynEventHandler#" + channel.getGroupName();
        this.application = application;
        this.channel = channel;
        EventBus.getDefault().register(this);
    }

    public void quit(){
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void handleEvent(AlljoynEvent event){
        if(event.groupName.equals(channel.getGroupName()))
            handleEvent(event.type, event.arg);
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void handleEvent(AlljoynDuplicatedSessionEvent event){
        if(event.groupName.equals(channel.getGroupName()))
            handleDuplicatedSessionEvent();
    }

    private void handleDuplicatedSessionEvent() {
        Log.i(TAG, "handleDuplicatedSessionEvent()");
        channel.setChannelState(AlljoynGroupChannel.AlljoynChannelState.REGISTERED);
        channel.handleEvent(A3GroupEvent.A3GroupEventType.GROUP_DUPLICATED);
        channel.reconnect();
    }

    private void handleSessionLostEvent(){
        Log.i(TAG, "handleSessionLostEvent()");
        if(channel.getGroupState().equals(A3GroupDescriptor.A3GroupState.ELECTION) ||
            channel.getGroupState().equals(A3GroupDescriptor.A3GroupState.ACTIVE)) {
            channel.setChannelState(AlljoynGroupChannel.AlljoynChannelState.REGISTERED);
            channel.handleEvent(A3GroupEvent.A3GroupEventType.GROUP_LOST);
            channel.reconnect();
        }
    }

    public void handleEvent(AlljoynEvent.AlljoynEventType event, Object arg){
        switch (event){
            case SESSION_BOUND:
                channel.handleEvent(A3GroupEvent.A3GroupEventType.GROUP_CREATED);
                channel.setServiceState(AlljoynService.AlljoynServiceState.ADVERTISED);
                break;
            case SESSION_DESTROYED:
                channel.handleEvent(A3GroupEvent.A3GroupEventType.GROUP_DESTROYED);
                channel.setServiceState(AlljoynService.AlljoynServiceState.BOUND);
                break;
            case SESSION_JOINED:
                channel.handleEvent(A3GroupEvent.A3GroupEventType.GROUP_JOINED);
                channel.setChannelState(AlljoynGroupChannel.AlljoynChannelState.JOINT);
                break;
            case SESSION_LOST:
                handleSessionLostEvent();
                break;
            case SESSION_LEFT:
                channel.handleEvent(A3GroupEvent.A3GroupEventType.GROUP_LEFT);
                channel.setChannelState(AlljoynGroupChannel.AlljoynChannelState.REGISTERED);
                break;
            case MEMBER_JOINED:
                channel.handleEvent(A3GroupEvent.A3GroupEventType.MEMBER_JOINED, arg);
                break;
            case MEMBER_LEFT:
                channel.handleEvent(A3GroupEvent.A3GroupEventType.MEMBER_LEFT, arg);
                break;
            default:
                break;
        }
    }
}

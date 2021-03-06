package it.polimi.deepse.a3droid.bus.alljoyn;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3GroupChannel;
import it.polimi.deepse.a3droid.a3.A3GroupDescriptor;
import it.polimi.deepse.a3droid.a3.events.A3GroupEvent;
import it.polimi.deepse.a3droid.bus.alljoyn.events.AlljoynDuplicatedSessionEvent;
import it.polimi.deepse.a3droid.pattern.Timer;
import it.polimi.deepse.a3droid.pattern.TimerInterface;
import it.polimi.deepse.a3droid.utility.RandomWait;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class AlljoynEventHandler implements TimerInterface{

    private final String TAG;
    private A3Application application;
    private AlljoynGroupChannel channel;
    private RandomWait randomWait = new RandomWait();

    public AlljoynEventHandler(A3Application application, AlljoynGroupChannel channel){
        TAG = "AlljoynEventHandler#" + channel.getGroupName();
        this.application = application;
        this.channel = channel;
        EventBus.getDefault().register(this);
    }

    public void quit(){
        EventBus.getDefault().unregister(this);
    }

    /**
     * The session with a service has been lost.
     */
    public enum AlljoynEvent {
        SESSION_BOUND,
        SESSION_ADVERTISED,
        SESSION_DESTROYED,
        SESSION_JOINED,
        SESSION_LEFT,
        SESSION_LOST,
        MEMBER_JOINED,
        MEMBER_LEFT
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void handleEvent(AlljoynDuplicatedSessionEvent event){
        if(event.groupName.equals(channel.getGroupName()))
            handleDuplicatedSessionEvent();
    }

    private void handleDuplicatedSessionEvent() {
        Log.i(TAG, "handleDuplicatedSessionEvent()");
        if(channel.getChannelState().equals(AlljoynBus.AlljoynChannelState.JOINT)) {
            channel.setChannelState(AlljoynBus.AlljoynChannelState.REGISTERED);
            channel.handleEvent(A3GroupEvent.A3GroupEventType.GROUP_DUPLICATED);
            new Timer(this, WAIT_AND_RECONNECT_EVENT, randomWait.next(WAIT_AND_RECONNECT_FT, WAIT_AND_RECONNECT_RT)).start();
        }
    }

    private void handleSessionLostEvent(){
        Log.i(TAG, "handleSessionLostEvent()");
        if(channel.getChannelState().equals(AlljoynBus.AlljoynChannelState.JOINT)) {
            channel.setChannelState(AlljoynBus.AlljoynChannelState.REGISTERED);
            channel.handleEvent(A3GroupEvent.A3GroupEventType.GROUP_LOST);
            new Timer(this, WAIT_AND_RECONNECT_EVENT, randomWait.next(WAIT_AND_RECONNECT_FT, WAIT_AND_RECONNECT_RT)).start();
        }
    }

    public void handleEvent(AlljoynEvent event, Object arg){
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
                channel.setChannelState(AlljoynBus.AlljoynChannelState.JOINT);
                break;
            case SESSION_LOST:
                handleSessionLostEvent();
                break;
            case SESSION_LEFT:
                channel.handleEvent(A3GroupEvent.A3GroupEventType.GROUP_LEFT);
                channel.setChannelState(AlljoynBus.AlljoynChannelState.REGISTERED);
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

    public void handleTimeEvent(int why, Object object){
        switch (why){
            case WAIT_AND_RECONNECT_EVENT:
                channel.reconnect();
                break;
            default:
                break;
        }
    }

    private static final int WAIT_AND_RECONNECT_EVENT = 0;
    private static final int WAIT_AND_RECONNECT_FT = 0;
    private static final int WAIT_AND_RECONNECT_RT = 2000;
}

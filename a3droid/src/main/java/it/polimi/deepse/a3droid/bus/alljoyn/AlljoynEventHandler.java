package it.polimi.deepse.a3droid.bus.alljoyn;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3Bus;
import it.polimi.deepse.a3droid.a3.A3EventHandler;
import it.polimi.deepse.a3droid.pattern.Timer;
import it.polimi.deepse.a3droid.pattern.TimerInterface;
import it.polimi.deepse.a3droid.utility.RandomWait;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class AlljoynEventHandler implements TimerInterface{

    private Handler mHandler;
    private A3Application application;
    private AlljoynGroupChannel channel;
    private RandomWait randomWait = new RandomWait();

    public AlljoynEventHandler(A3Application application, AlljoynGroupChannel channel){
        this.application = application;
        this.channel = channel;
    }

    /**
     * The session with a service has been lost.
     */
    public enum AlljoynEvent {
        SESSION_CREATED,
        SESSION_DESTROYED,
        SESSION_JOINED,
        SESSION_LEFT,
        SESSION_LOST,
        MEMBER_JOINED,
        MEMBER_LEFT
    }

    public void handleEvent(AlljoynEvent event, Object arg){
        switch (event){
            case SESSION_CREATED:
                channel.handleEvent(A3EventHandler.A3Event.GROUP_CREATED);
                channel.setServiceState(AlljoynService.AlljoynServiceState.ADVERTISED);
                break;
            case SESSION_DESTROYED:
                channel.handleEvent(A3EventHandler.A3Event.GROUP_DESTROYED);
                channel.setServiceState(AlljoynService.AlljoynServiceState.BOUND);
                break;
            case SESSION_JOINED:
                channel.handleEvent(A3EventHandler.A3Event.GROUP_JOINED);
                channel.setChannelState(AlljoynBus.AlljoynChannelState.JOINT);
                break;
            case SESSION_LOST:
                channel.handleEvent(A3EventHandler.A3Event.GROUP_LOST);
                channel.setChannelState(AlljoynBus.AlljoynChannelState.REGISTERED);
                new Timer(this, WAIT_AND_RECONNECT_EVENT, randomWait.next(WAIT_AND_RECONNECT_FT, WAIT_AND_RECONNECT_RT)).start();
                break;
            case SESSION_LEFT:
                channel.handleEvent(A3EventHandler.A3Event.GROUP_LEFT);
                channel.setChannelState(AlljoynBus.AlljoynChannelState.REGISTERED);
                break;
            case MEMBER_JOINED:
                channel.handleEvent(A3EventHandler.A3Event.MEMBER_JOINED, arg);
                break;
            case MEMBER_LEFT:
                channel.handleEvent(A3EventHandler.A3Event.MEMBER_LEFT, arg);
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

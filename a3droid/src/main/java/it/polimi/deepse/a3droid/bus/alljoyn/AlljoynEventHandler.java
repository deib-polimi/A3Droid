package it.polimi.deepse.a3droid.bus.alljoyn;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3Bus;
import it.polimi.deepse.a3droid.pattern.Timer;
import it.polimi.deepse.a3droid.pattern.TimerInterface;
import it.polimi.deepse.a3droid.utility.RandomWait;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class AlljoynEventHandler extends HandlerThread implements TimerInterface{

    private Handler mHandler;
    private A3Application application;
    private AlljoynChannel channel;
    private RandomWait randomWait = new RandomWait();

    public AlljoynEventHandler(A3Application application, AlljoynChannel channel){
        super("AlljoynEventHandler_" + channel.getGroupName());
        this.application = application;
        this.channel = channel;
        start();
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
                handleEvent(AlljoynBus.AlljoynEvent.values()[msg.what], msg.obj);
            }
        };
    }

    public void handleEvent(AlljoynBus.AlljoynEvent event, Object arg){
        switch (event){
            case SESSION_CREATED:
                channel.handleEvent(A3Bus.A3Event.GROUP_CREATED);
                channel.setServiceState(AlljoynBus.AlljoynServiceState.ADVERTISED);
                break;
            case SESSION_DESTROYED:
                channel.handleEvent(A3Bus.A3Event.GROUP_DESTROYED);
                channel.setServiceState(AlljoynBus.AlljoynServiceState.BOUND);
                break;
            case SESSION_JOINED:
                channel.handleEvent(A3Bus.A3Event.GROUP_JOINED);
                channel.setChannelState(AlljoynBus.AlljoynChannelState.JOINT);
                break;
            case SESSION_LOST:
                channel.handleEvent(A3Bus.A3Event.GROUP_LOST);
                channel.setChannelState(AlljoynBus.AlljoynChannelState.REGISTERED);
                new Timer(this, WAIT_AND_RECONNECT_EVENT, randomWait.next(WAIT_AND_RECONNECT_FT, WAIT_AND_RECONNECT_RT)).start();
                break;
            case SESSION_LEFT:
                channel.handleEvent(A3Bus.A3Event.GROUP_LEFT);
                channel.setChannelState(AlljoynBus.AlljoynChannelState.REGISTERED);
                break;
            case MEMBER_JOINED:
                channel.handleEvent(A3Bus.A3Event.MEMBER_JOINED, arg);
                break;
            case MEMBER_LEFT:
                channel.handleEvent(A3Bus.A3Event.MEMBER_LEFT, arg);
                break;
            default:
                break;
        }
    }

    public void handleTimeEvent(int why){
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

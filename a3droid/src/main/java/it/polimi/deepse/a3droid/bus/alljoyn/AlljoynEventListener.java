package it.polimi.deepse.a3droid.bus.alljoyn;

import android.os.Handler;
import android.os.Message;

import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.pattern.RandomWait;
import it.polimi.deepse.a3droid.pattern.Timer;
import it.polimi.deepse.a3droid.pattern.TimerInterface;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class AlljoynEventListener extends Handler implements TimerInterface{

    private A3Application application;
    private AlljoynChannel channel;
    private RandomWait randomWait = new RandomWait();

    public AlljoynEventListener(A3Application application, AlljoynChannel channel){
        this.application = application;
        this.channel = channel;
    }

    public void handleMessage(Message msg) {
        handleEvent((AlljoynBus.AlljoynEvent) msg.obj, msg.arg1);
    }

    public void handleEvent(AlljoynBus.AlljoynEvent event, int arg){
        switch (event){
            case SESSION_LOST:
                new Timer(this, WAIT_AND_RECONNECT_EVENT, randomWait.next(WAIT_AND_RECONNECT_FT, WAIT_AND_RECONNECT_RT));
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

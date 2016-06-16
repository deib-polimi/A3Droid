package it.polimi.deepse.a3droid.bus.alljoyn;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.Status;

import java.util.HashMap;
import java.util.Map;

import it.polimi.deepse.a3droid.a3.A3Bus;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupCreateDuplicationException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupCreateException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupJoinException;
import it.polimi.deepse.a3droid.pattern.Fibonacci;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class AlljoynErrorHandler extends Handler {

    private AlljoynChannel channel;
    private Map<A3Bus.ChannelState, Integer> channelRetries;
    private Map<A3Bus.ServiceState, Integer> serviceRetries;
    private static final int MAX_CHANNEL_RETRIES = 3;
    private static final int MAX_SERVICE_RETRIES = 3;

    public AlljoynErrorHandler(Looper looper, AlljoynChannel channel){
        super(looper);
        this.channel = channel;
        channelRetries = new HashMap<A3Bus.ChannelState, Integer>();
        serviceRetries = new HashMap<A3Bus.ServiceState, Integer>();
    }

    public void handleMessage(Message msg) {
        switch (msg.what){
            case CHANNEL:
                handleChannelError((Status) msg.obj);
                break;
            case SERVICE:
                handleServiceError((Status) msg.obj);
                break;
            case BUS:
                handleBusError((BusException) msg.obj);
                break;
            default:
                break;
        }
    }

    public void handleBusError(BusException ex){
        switch (channel.getChannelState()){
            case REGISTERED:

                break;
            case JOINED:

                break;
            default:
                break;
        }
    }

    public void handleChannelError(Status alljoynStatus){
        switch (channel.getChannelState()){
            case IDLE:
                
                break;
            case REGISTERED:
                switch (alljoynStatus){
                    case ALLJOYN_JOINSESSION_REPLY_UNREACHABLE:
                    case ALLJOYN_JOINSESSION_REPLY_CONNECT_FAILED:
                    case ALLJOYN_JOINSESSION_REPLY_FAILED:
                    case ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED:
                        if(channelRetries.get(A3Bus.ChannelState.REGISTERED) > 0) {
                            waitToRetry(channelRetries.get(A3Bus.ChannelState.REGISTERED));
                            incChannelRetries(A3Bus.ChannelState.REGISTERED);
                            channel.joinGroup();
                        }else
                            channel.handleError(new A3GroupJoinException(alljoynStatus.toString()));
                        break;
                    default:
                        break;
                }
                break;
            case JOINED:

                break;
            default:
                break;
        }
    }

    public void handleServiceError(Status alljoynStatus){
        switch (channel.getServiceState()){
            case REGISTERED:
                switch (alljoynStatus){
                    case BUS_NOT_CONNECTED:
                        if(serviceRetries.get(A3Bus.ServiceState.REGISTERED) > 0) {
                            waitToRetry(serviceRetries.get(A3Bus.ServiceState.REGISTERED));
                            incServiceRetries(A3Bus.ServiceState.REGISTERED);
                            channel.joinGroup();
                        }else
                            channel.handleError(new A3GroupCreateException(alljoynStatus.toString()));
                        break;
                    default:
                        break;
                }
                break;
            case NAMED:
                switch (alljoynStatus){
                    case BUS_NOT_CONNECTED:
                    case ALLJOYN_BINDSESSIONPORT_REPLY_FAILED:
                        if(serviceRetries.get(A3Bus.ServiceState.NAMED) > 0) {
                                waitToRetry(serviceRetries.get(A3Bus.ServiceState.NAMED));
                                incServiceRetries(A3Bus.ServiceState.NAMED);
                                channel.joinGroup();
                        }else {
                                channel.handleError(new A3GroupCreateException(alljoynStatus.toString()));
                        }
                        break;
                    case ALLJOYN_BINDSESSIONPORT_REPLY_ALREADY_EXISTS:
                        channel.handleError(new A3GroupCreateDuplicationException(alljoynStatus.toString()));
                        break;
                    case ALLJOYN_BINDSESSIONPORT_REPLY_INVALID_OPTS:
                        //TODO: Should this exception ever exist?
                        channel.handleError(new A3GroupCreateException(alljoynStatus.toString()));
                        break;
                    default:
                        break;
                }
                break;
            case BOUND:
                switch (alljoynStatus){
                    case BUS_NOT_CONNECTED:
                    case ALLJOYN_ADVERTISENAME_REPLY_FAILED:
                    case ALLJOYN_ADVERTISENAME_REPLY_TRANSPORT_NOT_AVAILABLE:
                        if(serviceRetries.get(A3Bus.ServiceState.BOUND) > 0) {
                                waitToRetry(serviceRetries.get(A3Bus.ServiceState.BOUND));
                                incServiceRetries(A3Bus.ServiceState.BOUND);
                                channel.joinGroup();
                        }else {
                                channel.handleError(new A3GroupCreateException(alljoynStatus.toString()));
                        }
                        break;
                    case ALLJOYN_ADVERTISENAME_REPLY_ALREADY_ADVERTISING:
                        channel.handleError(new A3GroupCreateDuplicationException(alljoynStatus.toString()));
                        break;
                    default:
                        break;
                }
                break;
            case ADVERTISED:

                break;
            default:
                break;
        }
    }

    private void incChannelRetries(A3Bus.ChannelState state){
        assert ((channelRetries.get(state) + 1) < MAX_CHANNEL_RETRIES);
        channelRetries.put(state, channelRetries.get(state) + 1);
    }

    private void incServiceRetries(A3Bus.ServiceState state){
        assert ((serviceRetries.get(state) + 1) < MAX_SERVICE_RETRIES);
        serviceRetries.put(state, serviceRetries.get(state) + 1);
    }

    public void resetChannelRetry(A3Bus.ChannelState state){
        channelRetries.put(state, 0);
    }

    public void resetServiceRetry(A3Bus.ServiceState state){
        serviceRetries.put(state, 0);
    }

    private void waitToRetry(int retry){
        try {
            this.wait(Fibonacci.fib(retry) * 250);//250, 500, 750, 1250...
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static final int CHANNEL = 0;    /** Error occurred in a channel setup operation */
    public static final int SERVICE  = 1;   /** Error occurred in a service setup operation */
    public static final int BUS  = 2;       /** Error occurred in the bus */
}

package it.polimi.deepse.a3droid.bus.alljoyn;

import android.util.Log;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.Status;

import java.util.HashMap;
import java.util.Map;

import it.polimi.deepse.a3droid.a3.exceptions.A3GroupCreationException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupDuplicationException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupJoinException;
import it.polimi.deepse.a3droid.a3.exceptions.A3MessageDeliveryException;
import it.polimi.deepse.a3droid.utility.Fibonacci;

/**
 * Handles three types of error: from service setup, from channel setup and from the bus.
 * Whenever an error cannot be handled by Alljoyn layer, it is scaled to A3 layer.
 */
public class AlljoynErrorHandler{

    protected final String TAG;

    private AlljoynGroupChannel channel;
    private Map<AlljoynBus.AlljoynChannelState, Integer> channelRetries;
    private Map<AlljoynService.AlljoynServiceState, Integer> serviceRetries;
    private static final int MAX_CHANNEL_RETRIES = 3;
    private static final int MAX_SERVICE_RETRIES = 3;


    public AlljoynErrorHandler(AlljoynGroupChannel channel){
        TAG = "AlljoynErrorHandler";
        this.channel = channel;
        channelRetries = new HashMap<>();
        resetChannelRetry();
        serviceRetries = new HashMap<>();
        resetServiceRetry();
    }

    public void handleError(int errorSource, Object arg) {
        switch (errorSource){
            case CHANNEL:
                handleChannelError((Status) arg);
                break;
            case SERVICE:
                handleServiceError((Status) arg);
                break;
            case BUS:
                handleBusError((BusException) arg);
                break;
            default:
                break;
        }
    }

    public void handleBusError(BusException ex){
        switch (channel.getBusState()){
            case DISCONNECTED:
                break;
            case CONNECTED:
                //TODO: Handle message send error
                channel.handleError(new A3MessageDeliveryException(ex.getMessage()));
                break;
            case DISCOVERING:
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
                Log.i(TAG, "Alljoyn service error at REGISTERED state");
                switch (alljoynStatus){
                    case ALLJOYN_JOINSESSION_REPLY_UNREACHABLE:
                    case ALLJOYN_JOINSESSION_REPLY_CONNECT_FAILED:
                    case ALLJOYN_JOINSESSION_REPLY_FAILED:
                    case ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED:
                        if(channelRetries.get(AlljoynBus.AlljoynChannelState.REGISTERED) < MAX_CHANNEL_RETRIES) {
                            waitToRetry(channelRetries.get(AlljoynBus.AlljoynChannelState.REGISTERED));
                            incChannelRetries(AlljoynBus.AlljoynChannelState.REGISTERED);
                            channel.joinGroup();
                        }else
                            channel.handleError(new A3GroupJoinException(alljoynStatus.toString()));
                        break;
                    default:
                        break;
                }
                break;
            case JOINT:
                Log.i(TAG, "Alljoyn service error at JOINT state");
                break;
            default:
                break;
        }
    }

    public void handleServiceError(Status alljoynStatus){
        switch (channel.getServiceState()){
            case REGISTERED:
                Log.i(TAG, "Alljoyn service error at REGISTERED state");
                switch (alljoynStatus){
                    case DBUS_REQUEST_NAME_REPLY_EXISTS:
                        channel.handleError(new A3GroupDuplicationException(alljoynStatus.toString()));
                        break;
                    case BUS_NOT_CONNECTED:
                        if(serviceRetries.get(AlljoynService.AlljoynServiceState.REGISTERED) < MAX_SERVICE_RETRIES) {
                            waitToRetry(serviceRetries.get(AlljoynService.AlljoynServiceState.REGISTERED));
                            incServiceRetries(AlljoynService.AlljoynServiceState.REGISTERED);
                            channel.createGroup();
                        }else
                            channel.handleError(new A3GroupCreationException(alljoynStatus.toString()));
                        break;
                    default:
                        break;
                }
                break;
            case NAMED:
                Log.i(TAG, "Alljoyn service error at NAMED state");
                switch (alljoynStatus){
                    case BUS_NOT_CONNECTED:
                    case ALLJOYN_BINDSESSIONPORT_REPLY_FAILED:
                        if(serviceRetries.get(AlljoynService.AlljoynServiceState.NAMED) < MAX_SERVICE_RETRIES) {
                                waitToRetry(serviceRetries.get(AlljoynService.AlljoynServiceState.NAMED));
                                incServiceRetries(AlljoynService.AlljoynServiceState.NAMED);
                                channel.createGroup();
                        }else {
                                channel.handleError(new A3GroupCreationException(alljoynStatus.toString()));
                        }
                        break;
                    case ALLJOYN_BINDSESSIONPORT_REPLY_ALREADY_EXISTS:
                        channel.handleError(new A3GroupDuplicationException(alljoynStatus.toString()));
                        break;
                    case ALLJOYN_BINDSESSIONPORT_REPLY_INVALID_OPTS:
                        channel.handleError(new A3GroupCreationException(alljoynStatus.toString()));
                        break;
                    default:
                        break;
                }
                break;
            case BOUND:
                Log.i(TAG, "Alljoyn service error at BOUND state");
                switch (alljoynStatus){
                    case BUS_NOT_CONNECTED:
                    case ALLJOYN_ADVERTISENAME_REPLY_FAILED:
                    case ALLJOYN_ADVERTISENAME_REPLY_TRANSPORT_NOT_AVAILABLE:
                        if(serviceRetries.get(AlljoynService.AlljoynServiceState.BOUND) < MAX_SERVICE_RETRIES) {
                                waitToRetry(serviceRetries.get(AlljoynService.AlljoynServiceState.BOUND));
                                incServiceRetries(AlljoynService.AlljoynServiceState.BOUND);
                                channel.createGroup();
                        }else {
                                channel.handleError(new A3GroupCreationException(alljoynStatus.toString()));
                        }
                        break;
                    case ALLJOYN_ADVERTISENAME_REPLY_ALREADY_ADVERTISING:
                        channel.handleError(new A3GroupDuplicationException(alljoynStatus.toString()));
                        break;
                    default:
                        break;
                }
                break;
            case ADVERTISED:
                Log.i(TAG, "Alljoyn service error at ADVERTISED state");
                break;
            default:
                break;
        }
    }

    private void incChannelRetries(AlljoynBus.AlljoynChannelState state){
        assert ((channelRetries.get(state) + 1) < MAX_CHANNEL_RETRIES);
        channelRetries.put(state, channelRetries.get(state) + 1);
    }

    private void incServiceRetries(AlljoynService.AlljoynServiceState state){
        assert ((serviceRetries.get(state) + 1) < MAX_SERVICE_RETRIES);
        serviceRetries.put(state, serviceRetries.get(state) + 1);
    }

    public void resetChannelRetry(){
        for(AlljoynBus.AlljoynChannelState state : AlljoynBus.AlljoynChannelState.values())
            channelRetries.put(state, 0);
    }

    public void resetServiceRetry(){
        for(AlljoynService.AlljoynServiceState state : AlljoynService.AlljoynServiceState.values())
            serviceRetries.put(state, 0);
    }

    private void waitToRetry(int retry){
        try {
            synchronized (this) {
                this.wait(Fibonacci.fib(retry) * 250);//250, 500, 750, 1250...
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static final int CHANNEL = 0;    /** Error occurred in a channel setup operation */
    public static final int SERVICE  = 1;   /** Error occurred in a service setup operation */
    public static final int BUS  = 2;       /** Error occurred in the bus */
}

package it.polimi.deepse.a3droid.bus.alljoyn;

import android.os.HandlerThread;
import android.os.Message;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3Bus;
import it.polimi.deepse.a3droid.a3.A3Channel;

/**
 * TODO: Describe
 */
public class AlljoynChannel extends A3Channel implements BusObject {

    private boolean hosting = false;
    private AlljoynErrorHandler errorHandler;

    public AlljoynChannel(String groupName, A3Application application){
        super(groupName, application);
        setService(new AlljoynService(groupName));
        HandlerThread busThread = new HandlerThread("BackgroundHandler");
        busThread.start();
        errorHandler = new AlljoynErrorHandler(busThread.getLooper(), this);
    }

    @Override
    public void createGroup(){
        this.hosting = true;
        super.createGroup();
    }

    @Override
    public void joinGroup(){
        this.hosting = false;
        super.joinGroup();
    }

    public void handleEvent(String event, Object arg){
        if(event.equals(AlljoynBus.SESSION_LOST_EVENT))
            if(!this.hosting)
    }

    /**
     * Handles Alljoyn error events. If an A3Exception is trowed, it was not possible to handle the
     * error at Alljoyn layer and the A3Exception is passed to the A3 layer to be handled by A3Channel.
     * @param status
     */
    public void handleError(Status status, int errorSide){
        assert (errorSide == AlljoynErrorHandler.CHANNEL ||
                errorSide == AlljoynErrorHandler.SERVICE);
        Message msg = errorHandler.obtainMessage(errorSide);
        msg.obj = status;
        errorHandler.sendMessage(msg);
    }

    /**
     * Handles Alljoyn errors. If an A3Exception is trowed, it was not possible to handle the
     * error at Alljoyn layer and the A3Exception is passed to the A3 layer to be handled by A3Channel.
     * @param ex
     */
    public void handleError(BusException ex, int errorSide){
        assert (errorSide == AlljoynErrorHandler.CHANNEL ||
                errorSide == AlljoynErrorHandler.SERVICE);
        Message msg = errorHandler.obtainMessage(errorSide);
        msg.obj = ex;
        errorHandler.sendMessage(msg);
    }

    /** Methods to send messages through service interface **/
    @Override
    public void sendUnicast(A3Message message) throws BusException {
        getServiceInterface().sendUnicast(message);
    }

    @Override
    public void sendMulticast(A3Message message) throws BusException {
        getServiceInterface().sendMulticast(message);
    }

    @Override
    public void sendBroadcast(A3Message message) throws BusException {
        getServiceInterface().sendBroadcast(message);
    }

    /**
     * The signal handlers for messages received from the AllJoyn bus
     *
     * Since the messages sent on a channel will be sent using a bus
     * signal, we need to provide a signal handler to receive those signals.
     * This is it.  Note that the name of the signal handler has the first
     * letter capitalized to conform with the DBus convention for signal
     * handler names.
     */
    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH + ".AlljoynServiceInterface", signal = "ReceiveUnicast")
    public void ReceiveUnicast(A3Message message) throws BusException {
        if(isAddressed(message.addresses))
            receiveUnicast(message);
    }

    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH + ".AlljoynServiceInterface", signal = "ReceiveMultiCast")
    public void ReceiveMultiCast(A3Message message) throws BusException {
        if(isAddressed(message.addresses))
            receiveMulticast(message);
    }

    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH + ".AlljoynServiceInterface", signal = "ReceiveBroadcast")
    public void ReceiveBroadcast(A3Message message) throws BusException {
        receiveBroadcast(message);
    }

    public BusAttachment getBus(){
        return mBus;
    }

    public void setBus(BusAttachment bus){
        mBus = bus;
    }

    /**
     * The bus attachment is the object that provides AllJoyn services to Java
     * clients.  Pretty much all communiation with AllJoyn is going to go through
     * this obejct.
     */
    private BusAttachment mBus  = new BusAttachment(AlljoynBus.SERVICE_PATH, BusAttachment.RemoteMessage.Receive);

    /** Service interface used to create signals in the bus **/
    public AlljoynServiceInterface getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(AlljoynServiceInterface serviceInterface, boolean proxied) {
        if(!proxied && this.hosting){
            this.serviceInterface = serviceInterface;
        }else
            if(!hosting)
                this.serviceInterface = serviceInterface;
    }

    /**
     * This interface is used for calling bus methods in the Service, not signals
     */
    private AlljoynServiceInterface serviceInterface;

    /** Service class instance used to create signals in the bus **/
    public AlljoynService getService() {
        return service;
    }

    private void setService(AlljoynService service) {
        this.service = service;
    }

    private AlljoynService service;

    /** Utilitary methods **/
    private boolean isAddressed(String[] addresses){
        String channelId = getChannelId();
        for(String address : addresses)
            if(address.equals(channelId))
                return true;
        return false;
    }

    /**
     * Set the status of the "host" channel.  The AllJoyn Service part of the
     * appliciation is expected to make this call to set the status to reflect
     * the status of the underlying AllJoyn session.
     */
    public synchronized void setServiceState(AlljoynBus.ServiceState state) {
        mServiceState = state;
        notifyObservers(SERVICE_STATE_CHANGED_EVENT);
    }

    /**
     * Get the state of the "use" channel.
     */
    public synchronized AlljoynBus.ServiceState getServiceState() {
        return mServiceState;
    }

    /**
     * The object we use in notifications to indicate that the state of the
     * "host" channel or its name has changed.
     */
    public static final String SERVICE_STATE_CHANGED_EVENT = "SERVICE_STATE_CHANGED_EVENT";

    /**
     * The "host" state which reflects the state of the part of the system
     * related to hosting an chat channel.  In a "real" application this kind
     * of detail probably isn't appropriate, but we want to do so for this
     * sample.
     */
    protected AlljoynBus.ServiceState mServiceState = AlljoynBus.ServiceState.IDLE;

    /**
     * Set the status of the "use" channel.  The AllJoyn Service part of the
     * appliciation is expected to make this call to set the status to reflect
     * the status of the underlying AllJoyn session.
     */
    public synchronized void setChannelState(AlljoynBus.ChannelState state) {
        mChannelState = state;
        notifyObservers(CHANNEL_STATE_CHANGED_EVENT);
    }

    /**
     * The object we use in notifications to indicate that the state of the
     * "use" channel or its name has changed.
     */
    public static final String CHANNEL_STATE_CHANGED_EVENT = "CHANNEL_STATE_CHANGED_EVENT";

    /**
     * Get the state of the "use" channel.
     */
    public synchronized AlljoynBus.ChannelState getChannelState() {
        return mChannelState;
    }

    /**
     * The "use" state which reflects the state of the part of the system
     * related to using a remotely hosted chat channel.  In a "real" application
     * this kind of detail probably isn't appropriate, but we want to do so for
     * this sample.
     */
    protected AlljoynBus.ChannelState mChannelState = AlljoynBus.ChannelState.IDLE;

    public synchronized void setBusState(AlljoynBus.BusState state) {
        mBusState = state;
        //TODO
        //notifyObservers(CHANNEL_STATE_CHANGED_EVENT);
    }

    public AlljoynBus.BusState getBusState() {
        return mBusState;
    }

    /**
     * The state of the AllJoyn bus attachment.
     */
    protected AlljoynBus.BusState mBusState = AlljoynBus.BusState.DISCONNECTED;

}

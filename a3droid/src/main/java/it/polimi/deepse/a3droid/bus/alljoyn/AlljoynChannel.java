package it.polimi.deepse.a3droid.bus.alljoyn;

import android.os.Message;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.GroupDescriptor;
import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3Channel;
import it.polimi.deepse.a3droid.a3.A3FollowerRole;
import it.polimi.deepse.a3droid.a3.A3Node;
import it.polimi.deepse.a3droid.a3.A3SupervisorRole;

/**
 * TODO: Describe
 */
public class AlljoynChannel extends A3Channel implements BusObject {

    private boolean hosting = false;
    private AlljoynErrorHandler errorHandler;
    private AlljoynEventHandler eventHandler;

    public AlljoynChannel(A3Application application,
                          A3Node node,
                          GroupDescriptor descriptor,
                          String groupName,
                          boolean hasFollowerRole,
                          boolean hasSupervisorRole){
        super(application, node, descriptor, groupName, hasFollowerRole, hasSupervisorRole);
        assert(application != null);
        assert(descriptor != null);
        setService(new AlljoynService(descriptor.getName()));
        errorHandler = new AlljoynErrorHandler(this);
        eventHandler = new AlljoynEventHandler(application, this);
    }

    /**
     * Connects to the alljoyn bus and either joins a group or created if it hasn't been found.
     */
    @Override
    public void connect(A3FollowerRole a3FollowerRole, A3SupervisorRole a3SupervisorRole){
        super.connect(a3FollowerRole, a3SupervisorRole);
        if(application.isGroupFound(groupName))
            joinGroup();
        else
            createGroup();
    }

    /**
     * Leaves a group and destroy it if hosting, them disconnects from the alljoyn bus.
     */
    @Override
    public void disconnect(){
        leaveGroup();
        if(hosting)
            destroyGroup();
        super.disconnect();
        //TODO: clean the channel resources, will you?
    }

    @Override
    public void reconnect(){
        disconnect();
        connect(followerRole, supervisorRole);
    }

    @Override
    public void createGroup(){
        this.hosting = true;
        super.createGroup();
    }

    @Override
    public void destroyGroup(){
        this.hosting = false;
        super.destroyGroup();
    }

    @Override
    public void joinGroup(){
        this.hosting = false;
        super.joinGroup();
    }

    @Override
    public void leaveGroup(){
        super.leaveGroup();
    }

    public void handleEvent(AlljoynBus.AlljoynEvent event, int arg){
        Message msg = eventHandler.obtainMessage();
        msg.obj = event;
        msg.arg1 = arg;
        eventHandler.sendMessage(msg);
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
        assert (errorSide == AlljoynErrorHandler.BUS);
        Message msg = errorHandler.obtainMessage(errorSide);
        msg.obj = ex;
        errorHandler.sendMessage(msg);
    }

    /** Methods to send application messages through service interface **/
    @Override
    public void sendUnicast(A3Message message) throws BusException {
        message.senderAddress = channelId;
        getServiceInterface().sendUnicast(message);
    }

    @Override
    public void sendMulticast(A3Message message) throws BusException {
        message.senderAddress = channelId;
        getServiceInterface().sendMulticast(message);
    }

    @Override
    public void sendBroadcast(A3Message message) throws BusException {
        message.senderAddress = channelId;
        getServiceInterface().sendBroadcast(message);
    }

    /** Methods to send control messages through service interface **/
    @Override
    public void sendControl(A3Message message) throws BusException{
        message.senderAddress = channelId;
        getServiceInterface().sendControl(message);
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

    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH + ".AlljoynServiceInterface", signal = "ReceiveControl")
    public void ReceiveControl(A3Message message) throws BusException {
        if(message.addresses.length == 0 || isAddressed(message.addresses))
            receiveControl(message);
    }

    public boolean isHosting() {
        return hosting;
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

    public void setServiceInterface(AlljoynServiceInterface serviceInterface, boolean remote) {
        if(!remote && this.hosting){
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
    public synchronized void setServiceState(AlljoynBus.AlljoynServiceState state) {
        mServiceState = state;
        notifyObservers(SERVICE_STATE_CHANGED_EVENT);
    }

    /**
     * Get the state of the "use" channel.
     */
    public synchronized AlljoynBus.AlljoynServiceState getServiceState() {
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
    protected AlljoynBus.AlljoynServiceState mServiceState = AlljoynBus.AlljoynServiceState.IDLE;

    /**
     * Set the status of the "use" channel.  The AllJoyn Service part of the
     * appliciation is expected to make this call to set the status to reflect
     * the status of the underlying AllJoyn session.
     */
    public synchronized void setChannelState(AlljoynBus.AlljoynChannelState state) {
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
    public synchronized AlljoynBus.AlljoynChannelState getChannelState() {
        return mChannelState;
    }

    /**
     * Get the state of the "use" channel.
     */
    public boolean isConnected() {
        return getChannelState().equals(AlljoynBus.AlljoynChannelState.JOINED);
    }

    /**
     * The "use" state which reflects the state of the part of the system
     * related to using a remotely hosted chat channel.  In a "real" application
     * this kind of detail probably isn't appropriate, but we want to do so for
     * this sample.
     */
    protected AlljoynBus.AlljoynChannelState mChannelState = AlljoynBus.AlljoynChannelState.IDLE;

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

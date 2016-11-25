package it.polimi.deepse.a3droid.bus.alljoyn;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;

import it.polimi.deepse.a3droid.a3.A3GroupChannel;
import it.polimi.deepse.a3droid.a3.A3Message;
import it.polimi.deepse.a3droid.a3.A3GroupDescriptor;
import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3FollowerRole;
import it.polimi.deepse.a3droid.a3.A3Node;
import it.polimi.deepse.a3droid.a3.A3SupervisorRole;

/**
 * TODO: Describe
 */
public class AlljoynGroupChannel extends A3GroupChannel {

    private boolean hosting = false;
    private AlljoynErrorHandler errorHandler;
    private AlljoynEventHandler eventHandler;

    /**
     * The handler used by this thread to receive and process messages
     */
    private Handler mHandler;

    public AlljoynGroupChannel(A3Application application,
                               A3Node node,
                               String groupName,
                               A3GroupDescriptor descriptor,
                               A3FollowerRole a3FollowerRole,
                               A3SupervisorRole a3SupervisorRole){
        super(application, node, groupName, descriptor, a3FollowerRole, a3SupervisorRole);
        assert(application != null);
        assert(descriptor != null);
        setService(new AlljoynService(descriptor.getGroupName()));
        start();
    }

    /**
     * Connects to the alljoyn bus and either joins a group or created if it hasn't been found.
     */
    @Override
    public void connect(){
        Message message = mHandler.obtainMessage(HANDLE_CONNECT_EVENT);
        mHandler.sendMessage(message);
    }

    /**
     * Leaves a group and destroy it if hosting, them disconnects from the alljoyn bus.
     */
    @Override
    public void disconnect(){
        Message message = mHandler.obtainMessage(HANDLE_DISCONNECT_EVENT);
        mHandler.sendMessage(message);
    }

    @Override
    public void reconnect(){
        super.reconnect();
    }

    @Override
    public void createGroup(){
        Message message = mHandler.obtainMessage(HANDLE_CREATE_GROUP_EVENT);
        mHandler.sendMessage(message);
    }

    @Override
    public void destroyGroup(){
        Message message = mHandler.obtainMessage(HANDLE_DESTROY_GROUP_EVENT);
        mHandler.sendMessage(message);
    }

    @Override
    public void joinGroup(){
        Message message = mHandler.obtainMessage(HANDLE_JOIN_GROUP_EVENT);
        mHandler.sendMessage(message);
    }

    @Override
    public void leaveGroup(){
        Message message = mHandler.obtainMessage(HANDLE_LEAVE_GROUP_EVENT);
        mHandler.sendMessage(message);
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

    public void handleEvent(AlljoynEventHandler.AlljoynEvent event, Object arg){
        eventHandler.handleEvent(event, arg);
    }

    /**
     * Handles Alljoyn CHANNEL and SERVICE error events. If an A3Exception is trowed, it was not
     * possible to handle the error at Alljoyn layer and the A3Exception is passed to the A3 layer
     * to be handled by A3GroupChannel.
     * @param status the (error) status returned by Alljoyn
     * @param errorSide identifies where the error occurred, i.e., at channel, service or bus setup
     */
    public void handleError(Status status, int errorSide){
        assert (errorSide == AlljoynErrorHandler.CHANNEL ||
                errorSide == AlljoynErrorHandler.SERVICE ||
                errorSide == AlljoynErrorHandler.BUS);
        errorHandler.handleError(errorSide, status);
    }

    /**
     * Handles Alljoyn BUS errors. If an A3Exception is trowed, it was not possible to handle the
     * error at Alljoyn layer and the A3Exception is passed to the A3 layer to be handled by
     * A3GroupChannel.
     * @param ex the exception trowed by the bus
     */
    public void handleError(BusException ex, int errorSide){
        assert (errorSide == AlljoynErrorHandler.BUS);
        errorHandler.handleError(errorSide, ex);
    }

    public void prepareHandler(){
        mHandler = new Handler(getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case HANDLE_CONNECT_EVENT:
                        doConnect();
                        break;
                    case HANDLE_DISCONNECT_EVENT:
                        waitBeforeDisconnection();
                        break;
                    case HANDLE_CREATE_GROUP_EVENT:
                        doCreateGroup();
                        break;
                    case HANDLE_DESTROY_GROUP_EVENT:
                        doDestroyGroup();
                        break;
                    case HANDLE_JOIN_GROUP_EVENT:
                        doJoinGroup();
                        break;
                    case HANDLE_LEAVE_GROUP_EVENT:
                        doLeaveGroup();
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private static final int HANDLE_CONNECT_EVENT = 1;
    private static final int HANDLE_DISCONNECT_EVENT = 2;
    private static final int HANDLE_CREATE_GROUP_EVENT = 3;
    private static final int HANDLE_DESTROY_GROUP_EVENT = 4;
    private static final int HANDLE_JOIN_GROUP_EVENT = 5;
    private static final int HANDLE_LEAVE_GROUP_EVENT = 6;

    /**
     * Connects to the alljoyn bus and either joins a group or created if it hasn't been found.
     */
    private void doConnect(){
        super.connect();
        initializeHandlers();
        if(application.isGroupFound(groupName))
            doJoinGroup();
        else
            doCreateGroup();
    }

    /**
     * Gives time to messages to be sent before disconnecting whenever the channel state is
     * still JOINT
     */
    private void waitBeforeDisconnection(){
        while(getChannelState() == AlljoynBus.AlljoynChannelState.JOINT &&
                !isOutboundEmpty()) {
            try {
                synchronized (this) {
                    Log.i(TAG, "waitBeforeDisconnection(): waiting for outbound to be clear");
                    this.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        doDisconnect();
    }

    /**
     * Leaves a group and destroy it if hosting, them disconnects from the alljoyn bus.
     */
    private void doDisconnect(){
        finalizeHandlers();
        doLeaveGroup();
        if(hosting)
            doDestroyGroup();
        super.disconnect();
    }

    private void doCreateGroup(){
        this.hosting = true;
        super.createGroup();
    }

    private void doDestroyGroup(){
        this.hosting = false;
        super.destroyGroup();
    }

    private void doJoinGroup(){
        super.joinGroup();
    }

    private void doLeaveGroup(){
        super.leaveGroup();
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

    private void initializeHandlers(){
        errorHandler = new AlljoynErrorHandler(this);
        eventHandler = new AlljoynEventHandler(application, this);
    }

    private void finalizeHandlers(){
        eventHandler.quit();
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
    public synchronized void setServiceState(AlljoynService.AlljoynServiceState state) {
        mServiceState = state;
        notifyObservers(SERVICE_STATE_CHANGED_EVENT);
    }

    /**
     * Get the state of the "host" channel.
     */
    public synchronized AlljoynService.AlljoynServiceState getServiceState() {
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
    private AlljoynService.AlljoynServiceState mServiceState = AlljoynService.AlljoynServiceState.IDLE;

    /**
     * Set the status of the "use" channel.  The AllJoyn Service part of the
     * appliciation is expected to make this call to set the status to reflect
     * the status of the underlying AllJoyn session.
     */
    public void setChannelState(AlljoynBus.AlljoynChannelState state) {
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
        return getChannelState().equals(AlljoynBus.AlljoynChannelState.JOINT);
    }

    /**
     * The "use" state which reflects the state of the part of the system
     * related to using a remotely hosted chat channel.  In a "real" application
     * this kind of detail probably isn't appropriate, but we want to do so for
     * this sample.
     */
    private AlljoynBus.AlljoynChannelState mChannelState = AlljoynBus.AlljoynChannelState.IDLE;

    public synchronized void setBusState(AlljoynBus.BusState state) {
        mBusState = state;
        notifyObservers(BUS_STATE_CHANGED_EVENT);
    }

    /**
     * The object we use in notifications to indicate that the state of the
     * "use" channel or its name has changed.
     */
    public static final String BUS_STATE_CHANGED_EVENT = "BUS_STATE_CHANGED_EVENT";

    public AlljoynBus.BusState getBusState() {
        return mBusState;
    }

    /**
     * The state of the AllJoyn bus attachment.
     */
    private AlljoynBus.BusState mBusState = AlljoynBus.BusState.DISCONNECTED;

}

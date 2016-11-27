package it.polimi.deepse.a3droid.bus.alljoyn;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.annotation.BusMethod;

import it.polimi.deepse.a3droid.a3.A3Message;

/**
 * Our chat messages are going to be Bus Signals multicast out onto an
 * associated session.  In order to send signals, we need to define an
 * AllJoyn bus object that will allow us to instantiate a signal emmiter.
 * TODO: Describe
 */
class AlljoynService implements BusObject, AlljoynServiceInterface {

    public AlljoynService(String groupName, String groupNameSuffix){
        setGroupName(groupName);
        this.groupNameSuffix = groupNameSuffix;
    }

    //TODO: add group management methods
    /** Service methods handled by this instance**/
    @Override
    @BusMethod(signature = "(sisayas)", replySignature = "b")
    public boolean sendUnicast(A3Message message) throws BusException {
        this.serviceSignalEmitterInterface.ReceiveUnicast(message);
        return true;
    }

    @Override
    @BusMethod(signature = "(sisayas)", replySignature = "b")
    public boolean sendMulticast(A3Message message) throws BusException {
        this.serviceSignalEmitterInterface.ReceiveMultiCast(message);
        return true;
    }

    @Override
    @BusMethod(signature = "(sisayas)", replySignature = "b")
    public boolean sendBroadcast(A3Message message) throws BusException {
        this.serviceSignalEmitterInterface.ReceiveBroadcast(message);
        return true;
    }

    @Override
    @BusMethod(signature = "(sisayas)", replySignature = "b")
    public boolean sendControl(A3Message message) throws BusException {
        this.serviceSignalEmitterInterface.ReceiveControl(message);
        return true;
    }

    /** Bellow methods are empty because they are handled by BusSignalHandler methods at @link AlljoynGroupChannel class**/
    public void ReceiveUnicast(A3Message message) throws BusException {}

    public void ReceiveMultiCast(A3Message message) throws BusException {}

    public void ReceiveBroadcast(A3Message message) throws BusException {}

    public void ReceiveControl(A3Message message) throws BusException {}

    private synchronized void setGroupName(String name) {
        groupName = name;
    }

    public synchronized String getGroupName() {
        return groupName;
    }

    protected String groupName;

    public synchronized String getGroupNameSuffix() {
        return groupNameSuffix;
    }

    private final String groupNameSuffix;

    public void setServiceSignalEmitterInterface(AlljoynServiceInterface serviceSignalEmitterInterface) {
        this.serviceSignalEmitterInterface = serviceSignalEmitterInterface;
    }

    /**
     * This interface is used for emitting bus signals in the bus, not calling methods
     */
    private AlljoynServiceInterface serviceSignalEmitterInterface;

    /**
     * Enumeration of the states of a hosted chat channel.  This lets us make a
     * note to ourselve
     A3DiscoveryDescriptor discoveryDescriptor = new A3DiscoveryDescriptor();
     mDiscoveryChannel = new AlljoynGroupChannel((A3Application) getApplication(), null,
     discoveryDescriptor);
     mBackgroundHandler.connect(mDiscoveryChannel);
     mBackgroundHandler.startDiscovery(mDiscoveryChannel);
     }s regarding where we are in the process of preparing
     * and tearing down the AllJoyn pieces responsible for providing the chat
     * service.  In order to be out of the IDLE state, the BusAttachment state
     * must be at least CONNECTED.
     */
    public enum AlljoynServiceState {
        IDLE, /**
         * There is no hosted chat channel
         */
        REGISTERED, /**
         * The service has been registered to the bus
         */
        NAMED, /**
         * The well-known name for the channel has been successfully acquired
         */
        BOUND, /**
         * A session port has been bound for the channel
         */
        ADVERTISED,        /** The bus attachment has advertised itself as hosting an chat channel */
    }
}
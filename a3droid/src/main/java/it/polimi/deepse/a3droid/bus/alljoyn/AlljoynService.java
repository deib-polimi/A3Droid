package it.polimi.deepse.a3droid.bus.alljoyn;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.annotation.BusMethod;

import it.polimi.deepse.a3droid.A3Message;

/**
 * Our chat messages are going to be Bus Signals multicast out onto an
 * associated session.  In order to send signals, we need to define an
 * AllJoyn bus object that will allow us to instantiate a signal emmiter.
 * TODO: Describe
 */
class AlljoynService implements BusObject, AlljoynServiceInterface {

    public AlljoynService(String groupName){
        setGroupName(groupName);
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

    /** Bellow methods are empty because they are handled by BusSignalHandler methods at @link AlljoynChannel class**/
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

    public void setServiceSignalEmitterInterface(AlljoynServiceInterface serviceSignalEmitterInterface) {
        this.serviceSignalEmitterInterface = serviceSignalEmitterInterface;
    }

    /**
     * This interface is used for emitting bus signals in the bus, not calling methods
     */
    private AlljoynServiceInterface serviceSignalEmitterInterface;
}
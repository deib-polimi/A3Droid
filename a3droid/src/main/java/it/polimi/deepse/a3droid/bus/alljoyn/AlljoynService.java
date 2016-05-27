package it.polimi.deepse.a3droid.bus.alljoyn;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;

import it.polimi.deepse.a3droid.A3Message;

/**
 * Our chat messages are going to be Bus Signals multicast out onto an
 * associated session.  In order to send signals, we need to define an
 * AllJoyn bus object that will allow us to instantiate a signal emmiter.
 */
class AlljoynService implements BusObject, AlljoynServiceInterface {

    public AlljoynService(String groupName){
        setGroupName(groupName);
    }

    /** Service methods handled by this instance**/
    //TODO: change these methods to group management methods, since communication uses signals
    /*@Override
    public boolean sendToSupervisor(A3Message message) throws BusException {
        return true;
    }

    @Override
    public boolean sendUnicast(A3Message message) throws BusException {
        return true;
    }

    @Override
    public boolean sendMultiCast(A3Message message) throws BusException {
        return true;
    }

    @Override
    public boolean sendBroadcast(A3Message message) throws BusException {
        return true;
    }*/

    /** Bellow methods are empty because they are handled by BusSignalHandler methods at @link AlljoynChannel class**/
    public void ReceiveUnicast(A3Message message, String address) throws BusException {}

    public void ReceiveMultiCast(A3Message message, String [] addresses) throws BusException {}

    public void ReceiveBroadcast(A3Message message) throws BusException {}

    /**
     * Set the group name, used as part of the service name.  Since we are going to "use" a
     * channel that is implemented remotely and discovered through an AllJoyn
     * FoundAdvertisedName, this must come from a list of advertised names.
     * These names are our channels, and so we expect the GUI to choose from
     * among the list of channels it retrieves from getFoundChannels().
     *
     * Since we are talking about user-level interactions here, we are talking
     * about the final segment of a well-known name representing a channel at
     * this point.
     */
    private synchronized void setGroupName(String name) {
        groupName = name;
        //notifyObservers(SERVICE_STATE_CHANGED_EVENT);
    }

    /**
     * Get the group name used as part of the service name.
     */
    public synchronized String getGroupName() {
        return groupName;
    }

    /**
     * The group name.
     */
    protected String groupName = null;
}
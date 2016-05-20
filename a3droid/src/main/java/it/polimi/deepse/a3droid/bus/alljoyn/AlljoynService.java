package it.polimi.deepse.a3droid.bus.alljoyn;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;

import it.polimi.deepse.a3droid.A3Message;

/**
 * Our chat messages are going to be Bus Signals multicast out onto an
 * associated session.  In order to send signals, we need to define an
 * AllJoyn bus object that will allow us to instantiate a signal emmiter.
 */
class AlljoynService implements AlljoynServiceInterface, BusObject {


    @Override
    public void sendToSupervisor(A3Message message) throws BusException{

    }

    @Override
    public void sendUnicast(A3Message message, String address) throws BusException{

    }

    @Override
    public void sendMultiCast(A3Message message) throws BusException{

    }

    @Override
    public void sendBroadcast(A3Message message) throws BusException{

    }


    /** Bellow methods are empty because they are handled by BusSignalHandler methods at @link AlljoynChannel class**/
    @Override
    public void ReceiveUnicast(A3Message message) throws BusException {}

    @Override
    public void ReceiveMultiCast(A3Message message) throws BusException {}

    @Override
    public void ReceiveBroadcast(A3Message message) throws BusException {}
}
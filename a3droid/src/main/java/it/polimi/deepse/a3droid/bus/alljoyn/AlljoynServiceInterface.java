package it.polimi.deepse.a3droid.bus.alljoyn;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.annotation.BusSignal;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.a3.A3ServiceInterface;

/**
 * Created by seadev on 5/20/16.
 */
public interface AlljoynServiceInterface extends A3ServiceInterface{

    /*
     * The BusSignal annotation signifies that this function should be used as
     * part of the AllJoyn interface.  The runtime is smart enough to figure
     * out that this is a used as a signal emitter and is only called to send
     * signals and not to receive signals.
     */
    @BusSignal
    void sendToSupervisor(A3Message message) throws BusException;

    @BusSignal
    void sendUnicast(A3Message message) throws BusException;

    @BusSignal
    void sendMultiCast(A3Message message) throws BusException;

    @BusSignal
    void sendBroadcast(A3Message message) throws BusException;

}

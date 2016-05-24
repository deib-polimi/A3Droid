package it.polimi.deepse.a3droid.bus.alljoyn;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.annotation.BusSignalHandler;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.a3.A3Channel;

/**
 * Created by seadev on 5/20/16.
 */
public class AlljoynChannel extends A3Channel{

    /**
     * The signal handler for messages received from the AllJoyn bus.
     *
     * Since the messages sent on a channel will be sent using a bus
     * signal, we need to provide a signal handler to receive those signals.
     * This is it.  Note that the name of the signal handler has the first
     * letter capitalized to conform with the DBus convention for signal
     * handler names.
     */
    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH, signal = "ReceiveUnicast")
    public void ReceiveUnicast(A3Message message) throws BusException {
        receiveUnicast(message);
    }

    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH, signal = "ReceiveMultiCast")
    public void ReceiveMultiCast(A3Message message) throws BusException {
        receiveMulticast(message);
    }

    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH, signal = "ReceiveBroadcast")
    public void ReceiveBroadcast(A3Message message) throws BusException {
        receiveBroadcast(message);
    }
}

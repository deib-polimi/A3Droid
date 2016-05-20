package it.polimi.deepse.a3droid.bus.alljoyn;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.annotation.BusMethod;
import org.alljoyn.bus.annotation.BusSignal;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.a3.A3ServiceInterface;

/**
 */
public interface AlljoynServiceInterface extends A3ServiceInterface{

    /*
     * The BusMethod annotation signifies that this function should be used as part of the AllJoyn
     * interface.  The runtime is smart enough to figure out what the input and output of the method,
     * but to improve performance the signature representing an A3Message has been defined.
     * All methods that use the BusMethod annotation can throw a BusException and should indicate
     * this fact.
     * Bus methods are called from nodes that have joined the service session and executed by the
     * service implementation.
     */
    @BusMethod(signature = "(sisay)", replySignature = "b")
    void sendToSupervisor(A3Message message) throws BusException;

    @BusMethod(signature = "(sisay)s", replySignature = "b")
    void sendUnicast(A3Message message, String address) throws BusException;

    @BusMethod(signature = "(sisay)", replySignature = "b")
    void sendMultiCast(A3Message message) throws BusException;

    @BusMethod(signature = "(sisay)", replySignature = "b")
    void sendBroadcast(A3Message message) throws BusException;

    /*
     * The BusSignal annotation signifies that this function should be used as
     * part of the AllJoyn interface.  The runtime is smart enough to figure
     * out that this is a used as a signal emitter and is only called to send
     * signals and not to receive signals.
     * Bus signals are called by the service and will be listened by all interfaces that declared
     * the corresponding bus signal handlers, in our case the AlljoynChannel class.
     */
    @BusSignal(signature = "(sisay)")
    void ReceiveUnicast(A3Message message) throws BusException;

    @BusSignal(signature = "(sisay)")
    void ReceiveMultiCast(A3Message message) throws BusException;

    @BusSignal(signature = "(sisay)")
    void ReceiveBroadcast(A3Message message) throws BusException;

}

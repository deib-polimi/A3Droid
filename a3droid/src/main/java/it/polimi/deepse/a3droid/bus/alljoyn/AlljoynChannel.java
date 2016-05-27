package it.polimi.deepse.a3droid.bus.alljoyn;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.annotation.BusSignalHandler;

import java.util.ArrayList;
import java.util.List;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.a3.A3Channel;
import it.polimi.deepse.a3droid.pattern.Observer;

/**
 * Created by seadev on 5/20/16.
 */
public class AlljoynChannel extends A3Channel{

    public AlljoynChannel(String groupName, List<Observer> observers){
        super(groupName);
        setService(new AlljoynService(groupName));
        addObservers(observers);
    }

    /** Methods to send messages through service interface **/
    @Override
    public void sendUnicast(A3Message message, String address) throws BusException {
        getServiceInterface().ReceiveUnicast(message, address);
    }

    @Override
    public void sendMulticast(A3Message message, String ... addresses) throws BusException {
        getServiceInterface().ReceiveMultiCast(message, addresses);
    }

    @Override
    public void sendBroadcast(A3Message message) throws BusException {
        getServiceInterface().ReceiveBroadcast(message);
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
    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH, signal = "ReceiveUnicast")
    public void ReceiveUnicast(A3Message message, String address) throws BusException {
        if(address.equals(getChannelId()))
            receiveUnicast(message, address);
    }

    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH, signal = "ReceiveMultiCast")
    public void ReceiveMultiCast(A3Message message, String [] addresses) throws BusException {
        if(isInMulticast(addresses))
            receiveMulticast(message, addresses);
    }

    @BusSignalHandler(iface = AlljoynBus.SERVICE_PATH, signal = "ReceiveBroadcast")
    public void ReceiveBroadcast(A3Message message) throws BusException {
        receiveBroadcast(message);
    }

    /** Service interface used to create signals in the bus **/
    public AlljoynServiceInterface getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(AlljoynServiceInterface serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

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
    private boolean isInMulticast(String [] addresses){
        String channelId = getChannelId();
        for(String address : addresses)
            if(address.equals(channelId))
                return true;
        return false;
    }
}

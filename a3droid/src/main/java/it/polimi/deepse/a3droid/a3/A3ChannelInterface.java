package it.polimi.deepse.a3droid.a3;

import org.alljoyn.bus.BusException;

import it.polimi.deepse.a3droid.A3Message;

/**
 * Created by seadev on 5/20/16.
 */
public interface A3ChannelInterface {

    void joinGroup();

    void createGroup();

    void sendUnicast(A3Message message) throws Exception;

    void sendMulticast(A3Message message) throws Exception;

    void sendBroadcast(A3Message message) throws Exception;

    void receiveUnicast(A3Message message);

    void receiveMulticast(A3Message message);

    void receiveBroadcast(A3Message message);
}

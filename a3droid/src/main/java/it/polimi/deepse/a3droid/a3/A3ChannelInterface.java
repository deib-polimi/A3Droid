package it.polimi.deepse.a3droid.a3;

import it.polimi.deepse.a3droid.A3Message;

/**
 * TODO: Describe
 */
public interface A3ChannelInterface {

    void connect(A3FollowerRole a3FollowerRole, A3SupervisorRole a3SupervisorRole);

    void disconnect();

    void reconnect();

    void joinGroup();

    void createGroup();

    boolean isConnected();

    void sendUnicast(A3Message message) throws Exception;

    void sendMulticast(A3Message message) throws Exception;

    void sendBroadcast(A3Message message) throws Exception;

    void sendControl(A3Message message) throws Exception;

    void receiveUnicast(A3Message message);

    void receiveMulticast(A3Message message);

    void receiveBroadcast(A3Message message);

    void receiveControl(A3Message message);
}

package it.polimi.deepse.a3droid.a3;

import it.polimi.deepse.a3droid.a3.exceptions.A3ChannelNotFoundException;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationParameters;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationRole;
import it.polimi.deepse.a3droid.a3.exceptions.A3NoGroupDescriptionException;

/**
 *
 */
public interface A3NodeInterface {

    void sendUnicast(A3Message message, String groupName, String address);

    void sendMulticast(A3Message message, String groupName, String ... addresses);

    void sendBroadcast(A3Message message, String groupName);

    void sendToSupervisor(A3Message message, String groupName);

    void stack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException, A3InvalidOperationParameters, A3InvalidOperationRole, A3ChannelNotFoundException;

    void reverseStack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException, A3InvalidOperationParameters, A3InvalidOperationRole, A3ChannelNotFoundException;

    void merge(String newGroupName, String oldGroupName) throws A3NoGroupDescriptionException, A3InvalidOperationParameters, A3InvalidOperationRole, A3ChannelNotFoundException;

    void split(String groupName, int nodesToTransfer) throws A3InvalidOperationRole, A3InvalidOperationParameters, A3ChannelNotFoundException, A3NoGroupDescriptionException;

    void groupStateChangeListener(String groupName, A3GroupDescriptor.A3GroupState oldState, A3GroupDescriptor.A3GroupState newState);

    A3GroupDescriptor getGroupDescriptor(String groupName) throws A3NoGroupDescriptionException;

    A3TopologyControl getTopologyControl();

    /** TODO refactor this, use the class as waiter **/
    Object waiter = new Object();
}

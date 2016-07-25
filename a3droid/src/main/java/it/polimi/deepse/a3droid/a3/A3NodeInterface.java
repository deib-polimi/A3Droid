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

    void stackReply(String parentGroupName, String childGroupName, boolean ok, boolean disconnect) throws A3ChannelNotFoundException;

    boolean performSupervisorStack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException, A3ChannelNotFoundException;

    void reverseStack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException, A3InvalidOperationParameters, A3InvalidOperationRole, A3ChannelNotFoundException;

    void reverseStackReply(String parentGroupName, String childGroupName, boolean ok, boolean disconnect) throws A3ChannelNotFoundException;

    boolean performSupervisorReverseStack(String parentGroupName, String childGroupName) throws A3ChannelNotFoundException;

    void merge(String newGroupName, String oldGroupName) throws A3NoGroupDescriptionException, A3InvalidOperationParameters, A3InvalidOperationRole, A3ChannelNotFoundException;

    boolean performSupervisorMerge(String newGroupName, String oldGroupName) throws A3NoGroupDescriptionException, A3ChannelNotFoundException;

    boolean performMerge(String newGroupName, String oldGroupName) throws A3NoGroupDescriptionException, A3ChannelNotFoundException;

    void mergeReply(String parentGroupName, String childGroupName, boolean ok, boolean disconnect) throws A3ChannelNotFoundException;

    Object waiter = new Object();
}

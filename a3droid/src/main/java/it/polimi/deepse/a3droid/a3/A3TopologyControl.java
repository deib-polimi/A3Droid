package it.polimi.deepse.a3droid.a3;

import android.util.Log;

import java.util.ArrayList;

import it.polimi.deepse.a3droid.a3.exceptions.A3ChannelNotFoundException;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationParameters;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationRole;
import it.polimi.deepse.a3droid.a3.exceptions.A3NoGroupDescriptionException;

/**
 * Created by danilo on 17/10/16.
 */

public class A3TopologyControl {

    protected static final String TAG = "A3TopologyControl";

    A3Node node;

    protected A3TopologyControl (A3Node node){
        this.node = node;
    }

    /**
     * Either request the stack operation to the child group's supervisor or
     * perform the stack operation itself if this is the child group's supervisor
     * @param parentGroupName
     * @param childGroupName
     * @throws A3InvalidOperationRole
     * @throws A3NoGroupDescriptionException
     * @throws A3ChannelNotFoundException
     */
    protected boolean stack(String parentGroupName, String childGroupName) throws
            A3InvalidOperationRole, A3NoGroupDescriptionException, A3ChannelNotFoundException, A3InvalidOperationParameters {
        Log.i(TAG, "stack(" + parentGroupName + ", " + childGroupName + ")");
        if (node.isSupervisor(childGroupName))
            return performSupervisorStack(parentGroupName, childGroupName);
        else
            return requestStack(parentGroupName, childGroupName);
    }

    /**
     * If this node has the proper roles,
     * this method creates a hierarchical relationship between the specified groups.
     * This happens by connecting this node to the parent group
     * and by adding the latter to the hierarchy of the child group.
     *
     * @param parentGroupName The name of the parent group.
     * @param childGroupName The name of the son group.
     * @return true, if "parentGroupName" became parent of "childGroupName", false otherwise.
     */
    private boolean performSupervisorStack(String parentGroupName, String childGroupName) throws
            A3NoGroupDescriptionException, A3ChannelNotFoundException {
        Log.i(TAG, "performSupervisorStack(" + parentGroupName + ", " + childGroupName + ")");
        if(node.connect(parentGroupName)) {
            A3GroupChannel channel = node.getChannel(childGroupName);
            channel.notifyHierarchyAdd(parentGroupName);
            stackReply(parentGroupName, childGroupName, true, false);
            return true;
        }else
            return false;
    }

    /**
     *
     * @param parentGroupName
     * @param childGroupName
     * @throws A3NoGroupDescriptionException
     * @throws A3ChannelNotFoundException
     */
    private boolean requestStack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
        if (node.connectAndWaitForActivation(childGroupName)) {
            A3GroupChannel channel;
            channel = node.getChannel(childGroupName);
            channel.requestStack(parentGroupName);
            return true;
        } else {
            stackReply(parentGroupName, childGroupName, false, true);
            return false;
        }
    }

    /**
     * It notifies this node with the result of a requestStack operation.
     *
     * @param parentGroupName The name of the parent group.
     * @param childGroupName The name of the child group.
     * @param result true if the requestStack operation was successful, false otherwise.
     */
    protected void stackReply(String parentGroupName, String childGroupName,
                           boolean result, boolean disconnect) throws A3ChannelNotFoundException {
        Log.i(TAG, "stackReply(" + parentGroupName + ", " + childGroupName + ", " + result + "): ");
        if(disconnect)
            node.disconnect(childGroupName);
    }

    /**
     *
     * @param parentGroupName
     * @param childGroupName
     * @throws A3NoGroupDescriptionException
     * @throws A3InvalidOperationParameters
     * @throws A3InvalidOperationRole
     * @throws A3ChannelNotFoundException
     */
    protected boolean reverseStack(String parentGroupName, String childGroupName) throws
            A3NoGroupDescriptionException,
            A3InvalidOperationParameters, A3InvalidOperationRole, A3ChannelNotFoundException {
        if (node.isSupervisor(childGroupName)) {
            return performSupervisorReverseStack(parentGroupName, childGroupName);
        } else
            return requestReverseStack(parentGroupName, childGroupName);
    }

    /**
     * It destroys the hierarchical relationship between the two specified groups,
     * by telling all the nodes of the child group to remove the parent group from their hierarchies
     * and by disconnecting the channel to the parent group if it isn't connected for other reasons.
     *
     * @param parentGroupName The name of the group to disconnect from.
     * @param childGroupName The name of the group to disconnect from group "parentGroupName".
     */
    private boolean performSupervisorReverseStack(String parentGroupName, String childGroupName) throws
            A3ChannelNotFoundException {
        assert(!parentGroupName.isEmpty());
        assert(!childGroupName.isEmpty());
        A3GroupChannel channel = node.getChannel(childGroupName);
        channel.notifyHierarchyRemove(parentGroupName);
        node.disconnect(parentGroupName);
        reverseStackReply(parentGroupName, childGroupName, true, false);
        return true;
    }

    /**
     *
     * @param parentGroupName
     * @param childGroupName
     * @throws A3NoGroupDescriptionException
     * @throws A3ChannelNotFoundException
     */
    private boolean requestReverseStack(String parentGroupName, String childGroupName) throws
            A3NoGroupDescriptionException, A3ChannelNotFoundException {
        if (node.connectAndWaitForActivation(childGroupName)) {
            A3GroupChannel channel = node.getChannel(childGroupName);
            channel.requestReverseStack(parentGroupName);
            return true;
        } else {
            reverseStackReply(parentGroupName, childGroupName, false, true);
            return false;
        }
    }

    /**
     * It notifies this node with the result of a reverse requestStack operation.
     *
     * @param parentGroupName The name of the parent group.
     * @param childGroupName The name of the child group.
     * @param ok true if the reverse requestStack operation was successful, false otherwise.
     */
    protected void reverseStackReply(String parentGroupName, String childGroupName, boolean ok, boolean disconnect) throws A3ChannelNotFoundException {
        Log.i(TAG, "reverseStackReply(" + parentGroupName + ", " + childGroupName + ", " + ok + ")");
        if(disconnect)
            node.disconnect(childGroupName);
    }

    /**
     * Either request the stack operation to the child group's supervinotifySplitsor or
     * perform the stack operation itself if this is the child group's supervisor
     * @param destinationGroupName
     * @param sourceGroupName
     * @throws A3InvalidOperationRole
     * @throws A3NoGroupDescriptionException
     * @throws A3ChannelNotFoundException
     */
    protected boolean merge(String destinationGroupName, String sourceGroupName)
            throws A3InvalidOperationRole, A3NoGroupDescriptionException, A3ChannelNotFoundException, A3InvalidOperationParameters {
        if (node.isSupervisor(sourceGroupName))
            return performSupervisorMerge(destinationGroupName, sourceGroupName);
        else
            return requestMerge(destinationGroupName, sourceGroupName);
    }

    /**
     *
     * @param destinationGroupName
     * @param sourceGroupName
     * @throws A3NoGroupDescriptionException
     * @throws A3ChannelNotFoundException
     */
    private boolean requestMerge(String destinationGroupName, String sourceGroupName)
            throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
        if (node.connectAndWaitForActivation(sourceGroupName)) {
            A3GroupChannel channel = node.getChannel(sourceGroupName);
            channel.requestMerge(destinationGroupName);
            return true;
        } else {
            mergeReply(destinationGroupName, sourceGroupName, false, true);
            return false;
        }
    }

    /**
     * It disconnects the hierarchy above the old group before the supervisor notifies the merge.
     *
     * @param destinationGroupName The name of the group to joinGroup to.
     * @param sourceGroupName The name of the group to disconnect from.
     */
    private synchronized boolean performSupervisorMerge(String destinationGroupName,
                                                       String sourceGroupName)
            throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
        disconnectFromHierarchyAbove(sourceGroupName);
        A3GroupChannel sourceGroupChannel = node.getChannel(sourceGroupName);
        sourceGroupChannel.notifyMerge(destinationGroupName);
        return true;
    }

    /**
     * It disconnects this node from the group "sourceGroupName" and connects it to the group
     * "destinationGroupName"
     *
     * @param destinationGroupName The name of the group to joinGroup to.
     * @param sourceGroupName The name of the group to disconnect from.
     */
    protected synchronized boolean performMerge(String destinationGroupName, String sourceGroupName)
            throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
        /**
         * TODO: replace this by a passive/control membership which won't receive this msg
         * If it is a supervisor of destination group, it ignores this request, as it is only
         * connected to request the merge and will be disconnected once a reply arrives.
         * However, if a source group supervisor node is already connected to the destination group,
         * it wouldn't disconnect from the source group, which justifies the second verification part.
         */
        if(!node.isSupervisor(destinationGroupName) || node.isSupervisor(sourceGroupName)) {
            disconnectHierarchyBellow(sourceGroupName);
            node.connectAndWaitForActivation(destinationGroupName);
            mergeReply(destinationGroupName, sourceGroupName, true, true);
            return true;
        }else
            return false;
    }

    /**
     * It notifies this node with the result of a merge operation.
     *
     * @param destinationGroupName The name of the group to which nodes should be transferred to
     * @param originGroupName The name of the group from which the groups will be transferred from
     * @param ok true if the merge operation was successful, false otherwise
     */
    protected void mergeReply(String destinationGroupName, String originGroupName, boolean ok, boolean disconnect) throws A3ChannelNotFoundException {
        Log.i(TAG, "mergeReply(" + destinationGroupName + ", " + originGroupName + ", " + ok + ")");
        if(disconnect)
            node.disconnect(originGroupName);
    }

    /** Disconnects this node from the group hierarchy above oldGroupName group, if any **/
    private void disconnectFromHierarchyAbove(String oldGroupName) throws A3ChannelNotFoundException {
        ArrayList<String> oldHierarchy = node.getChannel(oldGroupName).getHierarchyView().getHierarchy();
        for (String s : oldHierarchy) {
            node.disconnect(s);
        }
    }

    /** Notifies the nodes from the hierarchy bellow the oldGroupName group to disconnect from it **/
    private void disconnectHierarchyBellow(String oldGroupName){
        for (A3GroupChannel c : node.getChannels()) {
            if (c.isSupervisor() && c.getHierarchyView().getHierarchy().contains(oldGroupName)) {
                c.notifyHierarchyRemove(oldGroupName);
                //disconnect(oldGroupName); TODO
            }
        }
    }

    /** If this node is the supervisor of the group "groupName",
     * this method splits a new group from group "groupName"
     * and transfers there the specified number of nodes previously in "groupName".
     * Such nodes are selected randomly, but the supervisor of group "groupName" can't be transfered.
     * The new group has name "groupName_n", with integer n.
     *
     * @param groupName The name of the group whose nodes must be transfered in the new group.
     * @param nodesToTransfer The number of nodes to transfer from group "groupName" to the new group.
     */
    protected void split(String groupName, int nodesToTransfer) throws
            A3InvalidOperationRole, A3InvalidOperationParameters, A3ChannelNotFoundException,
            A3NoGroupDescriptionException {
        Log.i(TAG, "split(" + groupName + ", " + nodesToTransfer + ")");
        performSupervisorSplit(groupName, nodesToTransfer);
    }

    /**
     * @param groupName
     * @param nodesToTransfer
     */
    private synchronized boolean performSupervisorSplit(String groupName, int nodesToTransfer) throws
            A3NoGroupDescriptionException, A3ChannelNotFoundException {
        A3GroupChannel channel = node.getChannel(groupName);
        channel.notifySplitRandomly(nodesToTransfer);
        return true;
    }
}

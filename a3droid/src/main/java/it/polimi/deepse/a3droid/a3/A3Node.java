package it.polimi.deepse.a3droid.a3;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import it.polimi.deepse.a3droid.a3.exceptions.A3ChannelNotFoundException;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationParameters;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationRole;
import it.polimi.deepse.a3droid.a3.exceptions.A3NoGroupDescriptionException;
import it.polimi.deepse.a3droid.bus.alljoyn.AlljoynChannel;

/**
 * This class represent a device, with the roles it can play in each group.
 * It contains the methods to join and create a group, to disconnect from them,
 * to send messages to their members and to manage groups hierarchy.
 * @author Danilo F. M.
 *
 */
public class A3Node implements A3NodeInterface{

    protected static final String TAG = "a3droid.A3Node";

    private A3Application application;

    public A3Node(A3Application application,
                  ArrayList<A3GroupDescriptor> a3GroupDescriptors,
                  ArrayList<String> roles){
        this.application = application;
        this.a3GroupDescriptors = a3GroupDescriptors;
        this.roles = roles;
    }

    /**
     * @param groupName
     * @return
     * @throws A3NoGroupDescriptionException
     */
    protected synchronized boolean connectAndWait(String groupName) throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
        boolean result = connect(groupName);
        A3Channel channel = getChannel(groupName);
        waitForState(channel, A3Bus.A3GroupState.ACTIVE);
        return result;
    }

    /**
     *
     * @param groupName name of the group to be connected with
     * @return true if connection succeeded
     * @throws A3NoGroupDescriptionException
     */
    public synchronized boolean connect(String groupName) throws A3NoGroupDescriptionException {
        if(this.isConnectedForApplication(groupName))
            return true;

        A3GroupDescriptor descriptor = getGroupDescriptor(groupName);
        if(descriptor != null) {
            A3SupervisorRole supervisorRole = getRole(descriptor.getSupervisorRoleId(), A3SupervisorRole.class);
            A3FollowerRole followerRole = getRole(descriptor.getFollowerRoleId(), A3FollowerRole.class);
            A3Channel channel = new AlljoynChannel(application, this, descriptor);
            channel.connect(followerRole, supervisorRole);
            addChannel(channel);
            return true;
        }else{
            throw new A3NoGroupDescriptionException("This node has no descriptor for the group " + groupName);
        }
    }

    /**
     *
     * @param groupName
     * @throws A3ChannelNotFoundException
     */
    public void disconnect(String groupName) throws A3ChannelNotFoundException {
        A3Channel channel;
        channel = getChannel(groupName);
        channel.disconnect();
        removeChannel(channel);
    }

    /**
     *
     * @param channel
     * @param state
     */
    private void waitForState(A3Channel channel, A3Bus.A3GroupState state){
        synchronized (waiter) {
            while (channel.getGroupState().compareTo(state) < 0) {
                try {
                    waiter.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * It tries to create a hierarchical relationship between the specified groups.
     * This is possible only if this node is the supervisor of at least one of the two groups
     * and if it has the right roles to joinGroup to the other.
     * If this node is connected to the child group,
     * the effects of this method are the ones of "performSupervisorStack(parentGroupName, childGroupName)".
     * If this node is connected to the parent group,
     * it connects to the child group, if it can,
     * and sends it the order to execute "performSupervisorStack(parentGroupName, childGroupName)".
     * The success or the failure of the requestStack operation
     * is notified by method "stackReply(String, String, boolean)".
     *
     * @param parentGroupName The name of the parent group.
     * @param childGroupName The name of the son group.
     */
    public void stack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException,
            A3InvalidOperationParameters, A3InvalidOperationRole, A3ChannelNotFoundException {
        Log.i(TAG, "stack(" + parentGroupName + ", " + childGroupName + ")");
        validateGroupNameParameters(parentGroupName, childGroupName);
        validateOneSupervisorRole(parentGroupName, childGroupName);
        decideStack(parentGroupName, childGroupName);
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
    private void decideStack(String parentGroupName, String childGroupName) throws
            A3InvalidOperationRole, A3NoGroupDescriptionException, A3ChannelNotFoundException, A3InvalidOperationParameters {
        if (isSupervisor(childGroupName))
            performSupervisorStack(parentGroupName, childGroupName);
        else
            requestStack(parentGroupName, childGroupName);
    }

    /**
     *
     * @param parentGroupName
     * @param childGroupName
     * @throws A3NoGroupDescriptionException
     * @throws A3ChannelNotFoundException
     */
    private void requestStack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
        if (connectAndWait(childGroupName)) {
            A3Channel channel;
            channel = getChannel(childGroupName);
            channel.requestStack(parentGroupName);
        } else
            stackReply(parentGroupName, childGroupName, false, true);
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
    public boolean performSupervisorStack(String parentGroupName, String childGroupName) throws
            A3NoGroupDescriptionException, A3ChannelNotFoundException {
        Log.i(TAG, "performSupervisorStack(" + parentGroupName + ", " + childGroupName + ")");
        assert(!parentGroupName.isEmpty());
        assert(!childGroupName.isEmpty());
        if(connect(parentGroupName)) {
            A3Channel channel = getChannel(childGroupName);
            channel.notifyHierarchyAdd(parentGroupName);
            stackReply(parentGroupName, childGroupName, true, false);
            return true;
        }else
            return false;
    }

    /**
     * It notifies this node with the result of a requestStack operation.
     *
     * @param parentGroupName The name of the parent group.
     * @param childGroupName The name of the child group.
     * @param result true if the requestStack operation was successful, false otherwise.
     */
    public void stackReply(String parentGroupName, String childGroupName,
                           boolean result, boolean disconnect) throws A3ChannelNotFoundException {
        Log.i(TAG, "stackReply(" + parentGroupName + ", " + childGroupName + ", " + result + "): ");
        if(disconnect)
            disconnect(childGroupName);
    }

    /**
     * It tries to destroy the hierarchical relationship between the two specified groups.
     * This is only possible if this node is the supervisor of at least one of the two groups.
     * If this node is the supervisor of the group "childGroupName",
     * the effects of this method are the ones of method "performSupervisorReverseStack(parentGroupName, childGroupName)".
     * If this node is the supervisor of the group "parentGroupName",
     * and if it has the right roles to joinGroup to the group "childGroupName",
     * this node send the latter the order to execute "performSupervisorReverseStack(parentGroupName, childGroupName)".
     * Such operation is always possible, so notifying its result is unuseful.
     *
     * @param parentGroupName The name of the group to disconnect from.
     * @param childGroupName The name of the group to disconnect from group "parentGroupName".
     */
    public void reverseStack(String parentGroupName, String childGroupName) throws
            A3NoGroupDescriptionException,
        A3InvalidOperationParameters, A3InvalidOperationRole, A3ChannelNotFoundException {
        validateGroupNameParameters(parentGroupName, childGroupName);
        validateOneSupervisorRole(parentGroupName, childGroupName);
        decideReverseStack(parentGroupName, childGroupName);
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
    private void decideReverseStack(String parentGroupName, String childGroupName) throws
            A3NoGroupDescriptionException,
            A3InvalidOperationParameters, A3InvalidOperationRole, A3ChannelNotFoundException {
        if (isSupervisor(childGroupName)) {
            performSupervisorReverseStack(parentGroupName, childGroupName);
        } else
            requestReverseStack(parentGroupName, childGroupName);
    }

    /**
     * It destroys the hierarchical relationship between the two specified groups,
     * by telling all the nodes of the child group to remove the parent group from their hierarchies
     * and by disconnecting the channel to the parent group if it isn't connected for other reasons.
     *
     * @param parentGroupName The name of the group to disconnect from.
     * @param childGroupName The name of the group to disconnect from group "parentGroupName".
     */
    public boolean performSupervisorReverseStack(String parentGroupName, String childGroupName) throws
            A3ChannelNotFoundException {
        assert(!parentGroupName.isEmpty());
        assert(!childGroupName.isEmpty());
        A3Channel channel = getChannel(childGroupName);
        channel.notifyHierarchyRemove(parentGroupName);
        disconnect(parentGroupName);
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
    private void requestReverseStack(String parentGroupName, String childGroupName) throws
            A3NoGroupDescriptionException, A3ChannelNotFoundException {
        if (connectAndWait(childGroupName)) {
            A3Channel channel = getChannel(childGroupName);
            channel.requestReverseStack(parentGroupName);
        } else
            reverseStackReply(parentGroupName, childGroupName, false, false);
    }

    /**
     * It notifies this node with the result of a reverse requestStack operation.
     *
     * @param parentGroupName The name of the parent group.
     * @param childGroupName The name of the child group.
     * @param ok true if the reverse requestStack operation was successful, false otherwise.
     */
    public void reverseStackReply(String parentGroupName, String childGroupName, boolean ok, boolean disconnect) throws A3ChannelNotFoundException {
        Log.i(TAG, "reverseStackReply(" + parentGroupName + ", " + childGroupName + ", " + ok + ")");
        if(disconnect)
            disconnect(childGroupName);
    }

    /**
     * It transfers the nodes in group "sourceGroupName" to group "destinationGroupName" and destroys group "sourceGroupName".
     * The nodes which don't have the right roles to joinGroup to "destinationGroupName"
     * won't be there after this operation.
     *
     * @param destinationGroupName The name of the group which will receive the nodes in group "sourceGroupName".
     * @param sourceGroupName The group whose nodes should be transferred to "destinationGroupName". It is destroyed.
     * @throws A3NoGroupDescriptionException
     * @throws A3InvalidOperationParameters
     * @throws A3InvalidOperationRole
     * @throws A3ChannelNotFoundException
     */
    public void merge(String destinationGroupName, String sourceGroupName)
            throws A3NoGroupDescriptionException, A3InvalidOperationParameters,
            A3InvalidOperationRole, A3ChannelNotFoundException {
        validateGroupNameParameters(destinationGroupName, sourceGroupName);
        validateOneSupervisorRole(destinationGroupName, sourceGroupName);
        decideMerge(destinationGroupName, sourceGroupName);
    }

    /**
     * Either request the stack operation to the child group's supervisor or
     * perform the stack operation itself if this is the child group's supervisor
     * @param destinationGroupName
     * @param sourceGroupName
     * @throws A3InvalidOperationRole
     * @throws A3NoGroupDescriptionException
     * @throws A3ChannelNotFoundException
     */
    private void decideMerge(String destinationGroupName, String sourceGroupName)
            throws A3InvalidOperationRole, A3NoGroupDescriptionException, A3ChannelNotFoundException, A3InvalidOperationParameters {
        if (isSupervisor(sourceGroupName))
            performSupervisorMerge(destinationGroupName, sourceGroupName);
        else
            requestMerge(destinationGroupName, sourceGroupName);
    }

    /**
     *
     * @param destinationGroupName
     * @param sourceGroupName
     * @throws A3NoGroupDescriptionException
     * @throws A3ChannelNotFoundException
     */
    private void requestMerge(String destinationGroupName, String sourceGroupName)
        throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
        if (connectAndWait(sourceGroupName)) {
            A3Channel channel = getChannel(sourceGroupName);
            channel.requestMerge(destinationGroupName);
        } else
            disconnect(sourceGroupName);
    }

    /**
     * It disconnects the hierarchy above the old group before the supervisor notifies the merge
     * if it has the right roles.
     *
     * @param destinationGroupName The name of the group to joinGroup to.
     * @param sourceGroupName The name of the group to disconnect from.
     */
    public synchronized boolean performSupervisorMerge(String destinationGroupName,
                                                       String sourceGroupName)
            throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
        disconnectFromHierarchyAbove(sourceGroupName);
        A3Channel oldGroupChannel = getChannel(sourceGroupName);
        oldGroupChannel.notifyMerge(destinationGroupName);
        return true;
    }

    /**
     * It disconnects this node from the group "oldGroupName" and connects it to the group "newGroupName",
     * if it has the right roles.
     *
     * @param destinationGroupName The name of the group to joinGroup to.
     * @param sourceGroupName The name of the group to disconnect from.
     */
    public synchronized boolean performMerge(String destinationGroupName, String sourceGroupName)
            throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
            disconnectFromHieararchyBellow(sourceGroupName);
        mergeReply(destinationGroupName, sourceGroupName, connectAndWait(destinationGroupName), true);
        return true;
    }

    /** A3Hierarchy above the old group **/
    private void disconnectFromHierarchyAbove(String oldGroupName) throws A3ChannelNotFoundException {
        ArrayList<String> oldHierarchy = getChannel(oldGroupName).getHierarchy().getHierarchy();
        for (String s : oldHierarchy) {
            disconnect(s);
        }
    }

    /** A3Hierarchy bellow the old group **/
    private void disconnectFromHieararchyBellow(String oldGroupName){
        for (A3Channel c : mChannels) {
            if (c.isSupervisor() && c.getHierarchy().getHierarchy().contains(oldGroupName)) {
                c.notifyHierarchyRemove(oldGroupName);
                //disconnect(oldGroupName); TODO
            }
        }
    }

    /**
     * It notifies this node with the result of a merge operation.
     *
     * @param destinationGroupName The name of the group to which nodes should be transferred to
     * @param originGroupName The name of the group from which the groups will be transferred from
     * @param ok true if the merge operation was successful, false otherwise
     */
    public void mergeReply(String destinationGroupName, String originGroupName, boolean ok, boolean disconnect) throws A3ChannelNotFoundException {
        Log.i(TAG, "mergeReply(" + destinationGroupName + ", " + originGroupName + ", " + ok + ")");
        if(disconnect)
            disconnect(originGroupName);
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
    public void split(String groupName, int nodesToTransfer) throws
            A3InvalidOperationRole, A3InvalidOperationParameters, A3ChannelNotFoundException,
            A3NoGroupDescriptionException {
        Log.i(TAG, "split(" + groupName + ", " + nodesToTransfer + ")");
        validateGroupNameParameters(groupName);
        validateOneSupervisorRole(groupName);
        performSupervisorSplit(groupName, nodesToTransfer);
    }

    /**
     * @param groupName
     * @param nodesToTransfer
     */
    public synchronized boolean performSupervisorSplit(String groupName, int nodesToTransfer) throws
            A3NoGroupDescriptionException, A3ChannelNotFoundException {
        A3Channel channel = getChannel(groupName);
        channel.notifyNewSubgroup();
        channel.notifySplitRandomly(nodesToTransfer);
        return true;
    }

    /**
     * Check if group names are not null nor empty
     * @param groupNames
     */
    private void validateGroupNameParameters(String ... groupNames) throws A3InvalidOperationParameters {
        for(String groupName : groupNames)
            if(groupName == null || groupName.equals(""))
                throw new A3InvalidOperationParameters("Stack operation requires non empty parent/child group names.");
    }

    /**
     * Check if this node is a supervisor of at least one of the groups
     * @param groupNames
     */
    private void validateOneSupervisorRole(String... groupNames) throws A3InvalidOperationParameters {
        for(String groupName : groupNames)
            if(isSupervisor(groupName))
                return;

        throw new A3InvalidOperationParameters("Operation requires one supervision role");
    }

    /** Communication methods **/
    public void sendUnicast(A3Message message, String groupName, String address){
        try {
            A3Channel channel = getChannel(groupName);
            message.addresses = new String [] {address};
            channel.addOutboundItem(message, A3Channel.UNICAST_MSG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMulticast(A3Message message, String groupName, String ... addresses){
        try {
            A3Channel channel = getChannel(groupName);
            message.addresses = addresses;
            channel.addOutboundItem(message, A3Channel.MULTICAST_MSG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBroadcast(A3Message message, String groupName){
        try {
            A3Channel channel = getChannel(groupName);
            channel.addOutboundItem(message, A3Channel.BROADCAST_MSG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendToSupervisor(A3Message message, String groupName){
        try {
            A3Channel channel = getChannel(groupName);
            message.addresses = new String [] {channel.getSupervisorId()};
            channel.addOutboundItem(message, A3Channel.UNICAST_MSG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Called by the user interface to determine if the channel "groupName" is used by the application or not.
     * TODO: Not yet checking if connected 'for application'
     * @param groupName The name of the target channel.
     * @return true, if the channel "groupName" is used by the application, false otherwise.
     */
    public boolean isConnectedForApplication(String groupName) {
        A3Channel channel;
        try {
            channel = getChannel(groupName);
            return channel.isConnected();
        } catch (Exception e) {
            return false;
        }

    }

    /**Determines if this node is the supervisor of the specified group.
     *
     * @param groupName The name of the group.
     * @return true This node is the supervisor of the group "groupName", false otherwise.
     */
    public boolean isSupervisor(String groupName){

        A3Channel channel;

        try {
            channel = getChannel(groupName);
        } catch (Exception e) {
            return false;
        }
        return channel.isSupervisor();
    }

    /**Looks for a group descriptor in the "a3GroupDescriptors" list.
     *
     * @param groupName The name of the group whose descriptor is requested.
     * @return The descriptor of the group "groupName".
     */
    public A3GroupDescriptor getGroupDescriptor(String groupName) throws A3NoGroupDescriptionException {
        A3GroupDescriptor descriptor;
        String name;

        synchronized(a3GroupDescriptors){
            for(int i = 0; i < a3GroupDescriptors.size(); i++){
                descriptor = a3GroupDescriptors.get(i);
                name = descriptor.getGroupName();

				/* Groups splitted by main groups have the same descriptor as their main groups
				 * and their names are extensions of the main group names.
				 */
                if(groupName.equals(name) || groupName.startsWith(name + "_"))
                    return descriptor;
            }
        }
        throw new A3NoGroupDescriptionException("NO GROUP WITH NAME " + groupName + ".");
    }

    public ArrayList<A3GroupDescriptor> getA3GroupDescriptors(){
        return a3GroupDescriptors;
    }

    /**The list of the descriptors of the groups that can be present in the system.
     * The groups splitted by other groups have their same descriptors.
     */
    private final ArrayList<A3GroupDescriptor> a3GroupDescriptors;

    /**Looks for a role in the "roles" list.
     *
     * @param roleId The className of the role to look for.
     * @return The role with "roleId" as className.
     * @throws Exception No role has "roleId" as className.
     */
    public <T extends A3Role> T getRole(String roleId, Class<T> type){
        String role;

        synchronized(roles){
            for(int i = 0; i < roles.size(); i++){
                role = roles.get(i);
                if(role.equals(roleId))
                    try {
                        A3Role a3Role = (A3Role) Class.forName(roleId).getConstructor().newInstance();
                        a3Role.setNode(this);
                        return type.cast(a3Role);
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
            }
        }
        return null;
        //throw new Exception("NO ROLE WITH NAME " + roleId + ".");
    }

    /**The list of roles this node can assume.
     * I suppose that it can't change at runtime.
     */
    private final ArrayList<String> roles;

    /**Looks for a channel in the "mChannels" list.
     *
     * @param groupName The name of the group to communicate with (i.e. to which the channel is connected).
     * @return The channel connected to the group "groupName".
     * @throws Exception No channel is connected to the group "groupName".
     */
    public synchronized A3Channel getChannel(String groupName) throws A3ChannelNotFoundException {

        A3Channel channel;
        for(int i = 0; i < mChannels.size(); i++){
            channel = mChannels.get(i);

            if(channel.getGroupName().equals(groupName))
                return channel;
        }
        throw new A3ChannelNotFoundException("A3Channel for group " + groupName + "is not in this group's channel list");
    }

    public String getUID(){
        return application.getUID();
    }

    public synchronized void addChannel(A3Channel channel){
        mChannels.add(channel);
    }

    public synchronized void removeChannel(A3Channel channel){
        mChannels.remove(channel);
    }

    /**The list of the mChannels to communicate with the groups this node is connected to.
     * There are also mChannels that are disconnected because they are in "wait" group.
     * In such case, a channel to the group "wait" is connected and in this list.*/
    private ArrayList<A3Channel> mChannels = new ArrayList<>();

    @Override
    public int hashCode() {
        int hash = 0;
        for(A3GroupDescriptor g : a3GroupDescriptors)
            hash += g.hashCode();
        for(String r : roles)
            hash += r.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o){
        return o instanceof A3Node && (o == this || this.a3GroupDescriptors.equals(((A3Node) o).getA3GroupDescriptors()) &&
                this.roles.equals(((A3Node) o).roles));
    }
}

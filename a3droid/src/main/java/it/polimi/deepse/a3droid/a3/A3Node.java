package it.polimi.deepse.a3droid.a3;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import it.polimi.deepse.a3droid.a3.exceptions.A3ChannelNotFoundException;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationParameters;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationRole;
import it.polimi.deepse.a3droid.a3.exceptions.A3NoGroupDescriptionException;
import it.polimi.deepse.a3droid.bus.alljoyn.AlljoynGroupChannel;

/**
 * This class represent a device, with the roles it can play in each group.
 * It contains the methods to join and create a group, to disconnect from them,
 * to send messages to their members and to manage groups hierarchy.
 * @author Danilo F. M.
 *
 */
public class A3Node implements A3NodeInterface{

    protected static final String TAG = "a3droid.A3Node";

    /**
     * A singleton method for creating of fetching an existing node, which is identified by the
     * group descriptors and roles
     * @see A3Node#hashCode()
     * @see A3GroupDescriptor#hashCode()
     * @param groupDescriptors a list of A3GroupDescriptor instances for the node to be created
     * @param roles a list of A3Role instances for the node to be created
     * @return the created node or the existing node instance
     */
    public static A3Node createNode(A3Application application, ArrayList<A3GroupDescriptor> groupDescriptors,
                             ArrayList<String> roles){
        if(application.isNodeCreated(groupDescriptors, roles))
            return application.getNode(groupDescriptors, roles);
        else
            return application.createNode(groupDescriptors, roles);
    }

    /**
     *
     * @param application
     * @param a3GroupDescriptors
     * @param roles
     */
    protected A3Node(A3Application application,
                     ArrayList<A3GroupDescriptor> a3GroupDescriptors,
                     ArrayList<String> roles){
        this.application = application;
        this.a3GroupDescriptors = a3GroupDescriptors;
        this.roles = roles;
        this.topologyControl = new A3TopologyControl(this);
    }

    /** A reference to the android application object **/
    private A3Application application;

    private A3TopologyControl topologyControl;

    /**
     * Try to connect to a group
     * @param groupName name of the group to be connected with
     * @return true if connection succeeded. Does not imply the group state is ACTIVE
     * @throws A3NoGroupDescriptionException
     */
    public synchronized boolean connect(String groupName) throws A3NoGroupDescriptionException {
        if(this.isConnectedForApplication(groupName))
            return true;

        A3GroupDescriptor descriptor = getGroupDescriptor(groupName);
        if(descriptor != null) {
            A3SupervisorRole supervisorRole = getRole(descriptor.getSupervisorRoleId(), A3SupervisorRole.class);
            A3FollowerRole followerRole = getRole(descriptor.getFollowerRoleId(), A3FollowerRole.class);
            A3GroupChannel channel = new AlljoynGroupChannel(application, this, descriptor);
            channel.connect(followerRole, supervisorRole);
            addChannel(channel);
            return true;
        }else{
            throw new A3NoGroupDescriptionException("This node has no descriptor for the group " + groupName);
        }
    }

    /**
     * Try to connect to a group and wait for its state to be ACTIVE
     * @see A3GroupDescriptor.A3GroupState
     * @param groupName
     * @return true if the node has connected to the group
     * @throws A3NoGroupDescriptionException
     */
    public synchronized boolean connectAndWait(String groupName) throws A3NoGroupDescriptionException, A3ChannelNotFoundException {
        boolean result = connect(groupName);
        A3GroupChannel channel = getChannel(groupName);
        waitForState(channel, A3GroupDescriptor.A3GroupState.ACTIVE);
        return result;
    }

    /**
     * Disconnects this node from a group and removes the corresponding channel
     * @param groupName
     * @throws A3ChannelNotFoundException
     */
    public void disconnect(String groupName) throws A3ChannelNotFoundException {
        A3GroupChannel channel;
        channel = getChannel(groupName);
        channel.disconnect();
        removeChannel(channel);
    }

    /**
     *
     * @param channel
     * @param state
     */
    private void waitForState(A3GroupChannel channel, A3GroupDescriptor.A3GroupState state){
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
        topologyControl.stack(parentGroupName, childGroupName);
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
        topologyControl.reverseStack(parentGroupName, childGroupName);
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
        topologyControl.merge(destinationGroupName, sourceGroupName);
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
        topologyControl.split(groupName, nodesToTransfer);
    }

    @Override
    public void groupStateChangeListener(String groupName, A3GroupDescriptor.A3GroupState oldState, A3GroupDescriptor.A3GroupState newState) {}

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
            A3GroupChannel channel = getChannel(groupName);
            message.addresses = new String [] {address};
            channel.addOutboundItem(message, A3GroupChannel.UNICAST_MSG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMulticast(A3Message message, String groupName, String ... addresses){
        try {
            A3GroupChannel channel = getChannel(groupName);
            message.addresses = addresses;
            channel.addOutboundItem(message, A3GroupChannel.MULTICAST_MSG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBroadcast(A3Message message, String groupName){
        try {
            A3GroupChannel channel = getChannel(groupName);
            channel.addOutboundItem(message, A3GroupChannel.BROADCAST_MSG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendToSupervisor(A3Message message, String groupName){
        try {
            A3GroupChannel channel = getChannel(groupName);
            message.addresses = new String [] {channel.getSupervisorId()};
            channel.addOutboundItem(message, A3GroupChannel.UNICAST_MSG);
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
        A3GroupChannel channel;
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

        A3GroupChannel channel;

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
    public synchronized A3GroupChannel getChannel(String groupName) throws A3ChannelNotFoundException {

        A3GroupChannel channel;
        for(int i = 0; i < mChannels.size(); i++){
            channel = mChannels.get(i);

            if(channel.getGroupName().equals(groupName))
                return channel;
        }
        throw new A3ChannelNotFoundException("A3GroupChannel for group " + groupName + "is not in this group's channel list");
    }

    public synchronized ArrayList<A3GroupChannel> getChannels(){
        return mChannels;
    }

    public String getUID(){
        return application.getUID();
    }

    public synchronized void addChannel(A3GroupChannel channel){
        mChannels.add(channel);
    }

    public synchronized void removeChannel(A3GroupChannel channel){
        mChannels.remove(channel);
    }

    /**The list of the mChannels to communicate with the groups this node is connected to.
     * There are also mChannels that are disconnected because they are in "wait" group.
     * In such case, a channel to the group "wait" is connected and in this list.*/
    private ArrayList<A3GroupChannel> mChannels = new ArrayList<>();

    public A3TopologyControl getTopologyControl(){
        return topologyControl;
    }

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

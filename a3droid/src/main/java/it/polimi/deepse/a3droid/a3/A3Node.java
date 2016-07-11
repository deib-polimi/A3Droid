package it.polimi.deepse.a3droid.a3;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.GroupDescriptor;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationParameters;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationRole;
import it.polimi.deepse.a3droid.a3.exceptions.A3NoGroupDescriptionException;
import it.polimi.deepse.a3droid.bus.alljoyn.AlljoynChannel;

/**
 * This class represent a device, with the roles it can play in each group.
 * It contains the methods to join and create a group, to disconnect from them,
 * to send messages to their members and to manage groups hierarchy.
 * @author Danilo F. M. (refactored)
 * @author Francesco (original)
 *
 */
public class A3Node implements A3NodeInterface{

    protected static final String TAG = "a3droid.A3Node";

    private A3Application application;

    public A3Node(A3Application application,
                  ArrayList<GroupDescriptor> groupDescriptors,
                  ArrayList<String> roles){
        this.application = application;
        this.groupDescriptors = groupDescriptors;
        this.roles = roles;
    }

    public boolean connect(String groupName) throws A3NoGroupDescriptionException {
        if(this.isConnectedForApplication(groupName))
            return true;

        GroupDescriptor descriptor = getGroupDescriptor(groupName);
        boolean hasFollowerRole = false, hasSupervisorRole = false;
        A3FollowerRole followerRole = null;
        A3SupervisorRole supervisorRole = null;
        if(descriptor != null) {
            followerRole = (A3FollowerRole)getRole(descriptor.getFollowerRoleId(), A3FollowerRole.class);
            if(followerRole != null) {
                hasFollowerRole = true;
                followerRole.setNode(this);
            }

            supervisorRole = (A3SupervisorRole)getRole(descriptor.getSupervisorRoleId(), A3SupervisorRole.class);
            if(supervisorRole != null) {
                hasSupervisorRole = true;
                supervisorRole.setNode(this);
            }
            int myFF = descriptor.getSupervisorFitnessFunction();
            A3Channel channel = new AlljoynChannel(application,
                    this, descriptor, groupName,
                    hasFollowerRole, hasSupervisorRole);
            channel.connect(followerRole, supervisorRole);
            addChannel(channel);
            return true;
        }else{
            throw new A3NoGroupDescriptionException("This node has no descriptor for the group " + groupName);
        }
    }

    public void disconnect(String groupName){
        A3Channel channel = null;
        try{
            channel = getChannel(groupName);
            channel.disconnect();
            removeChannel(channel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * It tries to create a hierarchical relationship between the specified groups.
     * This is possible only if this node is the supervisor of at least one of the two groups
     * and if it has the right roles to joinGroup to the other.
     * If this node is connected to the child group,
     * the effects of this method are the ones of "actualStack(parentGroupName, childGroupName)".
     * If this node is connected to the parent group,
     * it connects to the child group, if it can,
     * and sends it the order to execute "actualStack(parentGroupName, childGroupName)".
     * The success or the failure of the requestStack operation
     * is notified by method "stackReply(String, String, boolean)".
     *
     * @param parentGroupName The name of the parent group.
     * @param childGroupName The name of the son group.
     */
    public void stack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException,
            A3InvalidOperationParameters, A3InvalidOperationRole {
        Log.i(TAG, "stack(" + parentGroupName + ", " + childGroupName + ")");
        if(parentGroupName != null && childGroupName != null &&
                !(parentGroupName.equals("") || childGroupName.equals(""))){
            if(isSupervisor(parentGroupName) || isSupervisor(childGroupName)) {
                boolean disconnect = true;
                if (isSupervisor(childGroupName)) {
                    //TODO I'm not disconnecting the childGroup supervisor anymore. Should I?
                    disconnect = false;
                    stackReply(parentGroupName, childGroupName, actualStack(parentGroupName, childGroupName), disconnect);
                } else if (isSupervisor(parentGroupName)) {
                    //TODO not connected for application, but for control
                    //TODO should we use a control channel for all groups?
                    //TODO maybe the discovery channel could become control channel
                    if (connect(childGroupName)) {
                        A3Channel channel = null;
                        try {
                            channel = getChannel(childGroupName);
                            channel.requestStack(parentGroupName);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else
                        stackReply(parentGroupName, childGroupName, false, disconnect);
                } else {
                    stackReply(parentGroupName, childGroupName, false, disconnect);
                }
            }else
                throw new A3InvalidOperationRole("Stack operation requires a node to be the supervisor of one of the groups.");
        }
        else
            throw new A3InvalidOperationParameters("Stack operation requires non empty parent/child group names.");
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
    public boolean actualStack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException{
        Log.i(TAG, "actualStack(" + parentGroupName + ", " + childGroupName + ")");
        assert(!parentGroupName.isEmpty());
        assert(!childGroupName.isEmpty());
        if(connect(parentGroupName)) {
            A3Channel channel = null;
            try {
                channel = getChannel(childGroupName);
                channel.requestHierarchyAdd(parentGroupName);
                return true;//TODO implement real verification of the result
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
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
    public void stackReply(String parentGroupName, String childGroupName, boolean result, boolean disconnect) {
        Log.i(TAG, "stackReply(" + parentGroupName + ", " + childGroupName + ", " + result + "): ");
        if(disconnect)
            disconnect(childGroupName);
    }

    /**
     * It tries to destroy the hierarchical relationship between the two specified groups.
     * This is only possible if this node is the supervisor of at least one of the two groups.
     * If this node is the supervisor of the group "childGroupName",
     * the effects of this method are the ones of method "actualReverseStack(parentGroupName, childGroupName)".
     * If this node is the supervisor of the group "parentGroupName",
     * and if it has the right roles to joinGroup to the group "childGroupName",
     * this node send the latter the order to execute "actualReverseStack(parentGroupName, childGroupName)".
     * Such operation is always possible, so notifying its result is unuseful.
     *
     * @param parentGroupName The name of the group to disconnect from.
     * @param childGroupName The name of the group to disconnect from group "parentGroupName".
     */
    public void reverseStack(String parentGroupName, String childGroupName){
        boolean ok = false;
        if(!(parentGroupName.equals("") || childGroupName.equals(""))){

            if(isSupervisor(parentGroupName)){
                try {
                    ok = connect(childGroupName);
                    if(ok){
                        try{
                            A3Channel channel = getChannel(childGroupName);
                            channel.requestReverseStack(parentGroupName);
                            disconnect(childGroupName);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else
                        disconnect(childGroupName);
                } catch (Exception e) {}
            } else if(isSupervisor(childGroupName)){
                    actualReverseStack(parentGroupName, childGroupName);
                    ok = true;
                }
            }
        reverseStackReply(parentGroupName, childGroupName, ok);
    }

    /**
     * It destroys the hierarchical relationship between the two specified groups,
     * by telling all the nodes of the child group to remove the parent group from their hierarchies
     * and by disconnecting the channel to the parent group if it isn't connected for other reasons.
     *
     * @param parentGroupName The name of the group to disconnect from.
     * @param childGroupName The name of the group to disconnect from group "parentGroupName".
     */
    public void actualReverseStack(String parentGroupName, String childGroupName) {
        assert(!parentGroupName.isEmpty());
        assert(!childGroupName.isEmpty());
        try{
            A3Channel channel = getChannel(childGroupName);
            channel.requestHierarchyRemove(parentGroupName);
            disconnect(parentGroupName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * It notifies this node with the result of a reverse requestStack operation.
     *
     * @param parentGroupName The name of the parent group.
     * @param childGroupName The name of the child group.
     * @param ok true if the reverse requestStack operation was successful, false otherwise.
     */
    private void reverseStackReply(String parentGroupName, String childGroupName, boolean ok) {
        Log.i(TAG, "reverseStackReply(" + parentGroupName + ", " + childGroupName + ", " + ok + ")");
    }

    /**
     * It transfers the nodes in group "groupName2" to group "groupName1" and destroys group "groupName2".
     * The nodes which don't have the right roles to joinGroup to "groupName1"
     * won't be there after this operation.
     *
     * @param groupName1 The name of the group which will receive the nodes in group "groupName2".
     * @param groupName2 The group whose nodes should be transferred to "groupName1". It is destroyed.
     */
    public void merge(String groupName1, String groupName2) throws A3NoGroupDescriptionException, A3InvalidOperationParameters {

        boolean ok = false;
        if(groupName1 != null && groupName2 != null &&
                !(groupName1.equals("") || groupName2.equals(""))){
            try {
                A3Channel channel;
                if(isSupervisor(groupName1)){
                    if(connect(groupName2)){
                        channel = getChannel(groupName2);
                        channel.requestMerge(groupName1);
                        ok = true;
                        disconnect(groupName2);
                    }else
                        disconnect(groupName2);
                }else if(isSupervisor(groupName2)){
                    channel = getChannel(groupName2);
                    channel.requestMerge(groupName1);
                    ok = true;
                    /** I don't need to execute "disconnect(groupName2, false);" here,
                     * because I will disconnect from group "groupName2"
                     * when I will receive message "CONTROL_MERGE groupName2".
                     **/
                }
                mergeReply(groupName1, groupName2, ok);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else
            throw new A3InvalidOperationParameters("Merge operation requires non empty parent/child group names");
    }

    /**
     * It disconnects this node from the group "oldGroupName" and connects it to the group "newGroupName",
     * if it has the right roles.
     *
     * @param newGroupName The name of the group to joinGroup to.
     * @param oldGroupName The name of the group to disconnect from.
     */
    public void actualMerge(String newGroupName, String oldGroupName) throws A3NoGroupDescriptionException {

        if(isSupervisor(oldGroupName)){
            try {
                ArrayList<String> oldHierarchy = getChannel(oldGroupName).getHierarchy().getHierarchy();
                synchronized(oldHierarchy){
                    for(String s : oldHierarchy){
                        disconnect(s);
                    }
                }
            } catch (Exception e) {}
        }

        disconnect(oldGroupName);

        synchronized(mChannels){
            for(A3Channel c : mChannels){
                if(c.isSupervisor() && c.getHierarchy().getHierarchy().contains(oldGroupName)){
                    c.requestHierarchyRemove(oldGroupName);
                    disconnect(oldGroupName);
                }
            }
        }
        connect(newGroupName);
    }

    /**
     * It notifies this node with the result of a merge operation.
     *
     * @param groupName1 The name of the group in which nodes in group "groupName2" were transfered.
     * @param groupName2 The name of the destroyed group.
     * @param ok true if the merge operation was successful, false otherwise.
     */
    private void mergeReply(String groupName1, String groupName2, boolean ok) {
        Log.i(TAG, "mergeReply(" + groupName1 + ", " + groupName2 + ", " + ok + ")");
    }

    /**If this node is the supervisor of the group "groupName",
     * this method splits a new group from group "groupName"
     * and transfers there the specified number of nodes previously in "groupName".
     * Such nodes are selected randomly, but the supervisor of group "groupName" can't be transfered.
     * The new group has name "groupName_n", with integer n.
     *
     * @param groupName The name of the group whose nodes must be transfered in the new group.
     * @param nodesToTransfer The number of nodes to transfer from group "groupName" to the new group.
     */
    public void split(String groupName, int nodesToTransfer) throws A3InvalidOperationRole {

        if(isSupervisor(groupName)){

            A3Channel channel;
            try{
                channel = getChannel(groupName);
                channel.requestSplit(nodesToTransfer);
            }catch(Exception e){}
        } else
            throw new A3InvalidOperationRole("Split failed: I'm not the supervisor.");
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

    /**Called by the user interface to determine if the channel "groupName" is used by the application or not.
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

    /**Looks for a group descriptor in the "groupDescriptors" list.
     *
     * @param groupName The name of the group whose descriptor is requested.
     * @return The descriptor of the group "groupName".
     * @throws Exception The descriptor of the group "groupName" doesn't exist,
     * i.e. the group "groupName" and its subgroups don't exist in the system.
     */
    public GroupDescriptor getGroupDescriptor(String groupName){
        GroupDescriptor descriptor;
        String name;

        synchronized(groupDescriptors){
            for(int i = 0; i < groupDescriptors.size(); i++){
                descriptor = groupDescriptors.get(i);
                name = descriptor.getName();

				/* Groups splitted by main groups have the same descriptor as their main groups
				 * and their names are extensions of the main group names.
				 */
                if(groupName.equals(name) || groupName.startsWith(name + "_"))
                    return descriptor;
            }
        }
        return null;
        //throw new Exception("NO GROUP WITH NAME " + groupName + ".");
    }

    public ArrayList<GroupDescriptor> getGroupDescriptors(){
        return groupDescriptors;
    }

    /**The list of the descriptors of the groups that can be present in the system.
     * The groups splitted by other groups have their same descriptors.
     */
    private final ArrayList<GroupDescriptor> groupDescriptors;

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
                        return type.cast(a3Role);
                    } catch (InstantiationException e) {
                    } catch (IllegalAccessException e) {
                    } catch (InvocationTargetException e) {
                    } catch (NoSuchMethodException e) {
                    } catch (ClassNotFoundException e) {
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
    public synchronized A3Channel getChannel(String groupName) throws Exception {

        A3Channel channel;
        for(int i = 0; i < mChannels.size(); i++){
            channel = mChannels.get(i);

            if(channel.getGroupName().equals(groupName))
                return channel;
        }
        throw new Exception("NO CHANNEL WITH NAME " + groupName + ".");
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
    private ArrayList<A3Channel> mChannels = new ArrayList<A3Channel>();

    @Override
    public int hashCode() {
        int hash = 0;
        for(GroupDescriptor g : groupDescriptors)
            hash += g.hashCode();
        for(String r : roles)
            hash += r.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o){
        if (!(o instanceof A3Node))
            return false;
        if (o == this)
            return true;

        return this.groupDescriptors.equals(((A3Node) o).getGroupDescriptors()) &&
                this.roles.equals(((A3Node) o).roles);
    }
}

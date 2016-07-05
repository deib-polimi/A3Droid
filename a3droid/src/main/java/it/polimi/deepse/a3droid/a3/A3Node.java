package it.polimi.deepse.a3droid.a3;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.GroupDescriptor;
import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationParameters;
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
    public void stack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException, A3InvalidOperationParameters {
        Log.i(TAG, "stack(" + parentGroupName + ", " + childGroupName + ")");
        if(parentGroupName == null || childGroupName == null ||
                !(parentGroupName.equals("") || childGroupName.equals(""))){
            if(isSupervisor(childGroupName))
                actualStack(parentGroupName, childGroupName);
                //stackReply(parentGroupName, childGroupName, actualStack(parentGroupName, childGroupName));
            else{
                if(isSupervisor(parentGroupName)){
                    //TODO not connected for application, but for control
                    //TODO should we use a control channel for all groups?
                    //TODO maybe the discovery channel could become control channel
                    if(connect(childGroupName)){
                        A3Channel channel = null;
                        try{
                            channel = getChannel(childGroupName);
                            channel.requestStack(parentGroupName);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    else
                        stackReply(parentGroupName, childGroupName, false);
                }else{
                    stackReply(parentGroupName, childGroupName, false);
                }
            }
        }
        else
            throw new A3InvalidOperationParameters("stack operation requires non empty parent/child group names");
            //stackReply(parentGroupName, childGroupName, false);
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
                channel.notifyStack(parentGroupName);
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
    public void stackReply(String parentGroupName, String childGroupName, boolean result) {
        Log.i(TAG, "stackReply(" + parentGroupName + ", " + childGroupName + ", " + result + "): ");
        disconnect(childGroupName);
    }

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

}

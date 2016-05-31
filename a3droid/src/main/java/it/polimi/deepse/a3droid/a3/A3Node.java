package it.polimi.deepse.a3droid.a3;

import java.util.ArrayList;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.bus.alljoyn.AlljoynChannel;

/**
 * This class represent a device, with the roles it can play in each group.
 * It contains the methods to join and create a group, to disconnect from them,
 * to send messages to their members and to manage groups hierarchy.
 * @author Danilo F. M. (refactored)
 * @author Francesco (original)
 *
 */
public class A3Node {

    public A3Node(A3Application application){
        this.application = application;
    }

    private A3Application application;

    public void connect(String groupName){
        A3Channel channel = new AlljoynChannel(groupName, application);
        channel.connect();
        if(application.getFoundChannels().contains(groupName))
            channel.joinGroup();
        else
            channel.createGroup();

        addChannel(channel);
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

    public void sendUnicast(A3Message message, String groupName, String address){
        try {
            A3Channel channel = getChannel(groupName);
            message.addresses = new String [] {address};
            message.reason = A3Channel.UNICAST_MSG;
            channel.addOutboundItem(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMulticast(A3Message message, String groupName, String ... addresses){
        try {
            A3Channel channel = getChannel(groupName);
            message.addresses = addresses;
            message.reason = A3Channel.MULTICAST_MSG;
            channel.addOutboundItem(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBroadcast(A3Message message, String groupName){
        try {
            A3Channel channel = getChannel(groupName);
            message.reason = A3Channel.BROADCAST_MSG;
            channel.addOutboundItem(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**Called by the user interface to determine if the channel "groupName" is used by the application or not.
     * TODO: Not yet checking 'for application'
     * @param groupName The name of the target channel.
     * @return true, if the channel "groupName" is used by the application, false otherwise.
     */
    public boolean isConnectedForApplication(String groupName) {
        A3Channel channel;
        try {
            channel = getChannel(groupName);
            return true;//channel.isConnectedForApplication();
        } catch (Exception e) {
            // TODO Remove exception in getChannel?
            return false;
        }

    }

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

package it.polimi.deepse.a3droid.a3;

import java.util.ArrayList;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.bus.alljoyn.AlljoynChannel;

/**
 * TODO: describe
 */
public class A3Node {

    public A3Node(A3Application application){
        this.application = application;
        channels = new ArrayList<A3Channel>();
    }

    private A3Application application;

    public void connect(String groupName){
        A3Channel channel = new AlljoynChannel(groupName, application.getObservers());
        if(application.getFoundChannels().contains(groupName))
            channel.joinGroup();
        else
            channel.createGroup();

        channels.add(channel);
    }

    public void sendUnicast(A3Message message, String groupName, String address){
        try {
            A3Channel channel = getChannel(groupName);
            channel.sendUnicast(message, address);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMulticast(A3Message message, String groupName, String ... addresses){
        try {
            A3Channel channel = getChannel(groupName);
            channel.sendMulticast(message, addresses);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBroadcast(A3Message message, String groupName){
        try {
            A3Channel channel = getChannel(groupName);
            channel.sendBroadcast(message);
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

    /**Looks for a channel in the "channels" list.
     *
     * @param groupName The name of the group to communicate with (i.e. to which the channel is connected).
     * @return The channel connected to the group "groupName".
     * @throws Exception No channel is connected to the group "groupName".
     */
    public A3Channel getChannel(String groupName) throws Exception {

        A3Channel channel;

        synchronized(channels){
            for(int i = 0; i < channels.size(); i++){
                channel = channels.get(i);

                if(channel.getGroupName().equals(groupName))
                    return channel;
            }
        }
        throw new Exception("NO CHANNEL WITH NAME " + groupName + ".");
    }

    /**The list of the channels to communicate with the groups this node is connected to.
     * There are also channels that are disconnected because they are in "wait" group.
     * In such case, a channel to the group "wait" is connected and in this list.*/
    private ArrayList<A3Channel> channels;

}

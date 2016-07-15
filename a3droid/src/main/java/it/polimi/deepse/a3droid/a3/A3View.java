package it.polimi.deepse.a3droid.a3;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import java.util.ArrayList;

import it.polimi.deepse.a3droid.TimerInterface;

/**This class resides on a Service.
 * It manages the list of the channels currently in the group (called "view")
 * and all the messages and the callbacks to update it runtime.
 * @author Francesco
 *
 */
public class A3View extends HandlerThread implements TimerInterface{

	/**The Service on which this View resides.*/
	private A3Channel channel;

	/**The list of the channel that are currently part of the group.*/
	private ArrayList<String> groupMembers;

	/**The list of the group members that will substitute "groupMembers" after a view update.*/
	private ArrayList<String> temporaryView;

	/**Indicates if a view update is ongoing or not.*/
	private boolean temporaryViewIsActive;

	/**The number of the channels in the group.*/
	private int numberOfNodes;

	private Handler mHandler;

	/**The Service on which this View resides
	 * @param channel this view's channel
	 */
	public A3View(A3Channel channel) {
		super("View_" + channel.getGroupName());
		this.channel = channel;
		groupMembers = new ArrayList<>();
		temporaryViewIsActive = false;
		numberOfNodes = 0;
		start();
	}

	public Message obtainMessage() {
		return mHandler.obtainMessage();
	}

	public void sendMessage(Message msg) {
		mHandler.sendMessage(msg);
	}

	@Override
	protected void onLooperPrepared() {
		super.onLooperPrepared();

		mHandler = new Handler(getLooper()) {

			/**This method is used to manage callbacks.
			 * This is done in another thread in order not to block the bus.
			 */
			public void handleMessage(Message msg) {

				A3Bus.A3Event event = A3Bus.A3Event.values()[msg.what];
				String memberName = (String) msg.obj;
				switch(event){
					case MEMBER_JOINED:
						addGroupMember(memberName);
						break;

					case MEMBER_LEFT:
						removeGroupMember(memberName);
						break;

					default: break;
				}
			}
		};
	}

	/**
	 * It adds the channel "memberName" to the group members' list, because it joined the group.
	 * If a view update is ongoing, the channel's address is added to the temporary view too.
	 * If the channel is the first channel joining the group, it is the new supervisor
	 * and it is notified about it.
	 * @param memberName The address of the channel that joined the group.
	 */
	private synchronized void addGroupMember(String memberName) {

		groupMembers.add(memberName);

		if(temporaryViewIsActive)
			temporaryView.add(memberName);
		numberOfNodes = numberOfNodes + 1;
	}

	/**
	 * It removes the channel "memberName" from the list of the group members, because it left the group.
	 * It triggers a supervisor election if "memberName" was the supervisor of the group,
	 * or the group destruction if no nodes are present in the group anymore.
	 * If a view update is ongoing, the channel is removed from the temporary view too.
	 * @param memberName The address of the channel which left the group.
	 */
	public synchronized void removeGroupMember(String memberName) {

		groupMembers.remove(memberName);

		if(temporaryViewIsActive){
			temporaryView.remove(memberName);
		}
		numberOfNodes = numberOfNodes - 1;

		String supervisorId = channel.getSupervisorId();
		if(supervisorId != null && supervisorId.equals(memberName))
			channel.handleEvent(A3Bus.A3Event.SUPERVISOR_LEFT);
	}

	/**
	 * @return The string representation of the list of the group members, in the form "[member1, member2, ...]".
	 */
	public synchronized String getView() {
		return groupMembers.toString();
	}

	/**
	 * TODO
	 * @return true if the address of the channel "address" is currently alone in the view
	 * false otherwise.
	 */
	public synchronized boolean isViewEmpty(){
		return groupMembers.isEmpty();
	}

	/**
	 * TODO
	 * @param address The address of the channel whose presence in the group has to be checked.
	 * @return true if the address of the channel "address" is currently alone in the view
	 * false otherwise.
	 */
	public synchronized boolean isAloneInView(String address){
		return (groupMembers.contains(address) && groupMembers.size() == 1);
	}

	/**
	 * It determines if the specified channel is currently in the list of the group members or not.
	 * If a view update is ongoing, the check is performed on the temporary view too.
	 * @param address The address of the channel whose presence in the group has to be checked.
	 * @return true if the address of the channel "address" is currently in the (temporary) view,
	 * false otherwise.
	 */
	public synchronized boolean isInView(String address){
		return groupMembers.contains(address) || temporaryViewIsActive && temporaryView.contains(address);
	}

	@Override
	public void timerFired(int reason) {
		temporaryViewIsActive = false;
		groupMembers = temporaryView;
	}

	public int getNumberOfNodes() {
		return numberOfNodes;
	}
}

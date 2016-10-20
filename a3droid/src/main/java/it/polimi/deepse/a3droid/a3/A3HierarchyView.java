package it.polimi.deepse.a3droid.a3;

import java.util.ArrayList;


/**
 * This class is the list of the groups to which the supervisor of a group must be connected
 * to maintain hierarchical relationship with them.
 * It is replicated on each channel in the group, in order to retreive the hierarchy after a Service's fault.
 * @author Francesco
 *
 */
public class A3HierarchyView {

	/**The list of the parent group names.*/
	private ArrayList<String> hierarchy;
	
	/**The channel this hierarchy belongs to.*/
	private A3GroupChannel channel;

	/**
	 * Only useful to determine the right name of a new splitted group.
	 * Groups splitted by the group "group" are named "group_1", "group_2", ..., "group_numberOfSplittedGroups".
	 * If a group "group" exists, and also its groups "group_0", "group_1" and "group_3" exist,
	 * but group "group_2" doesn't exist, an eventual new splitted group will be named "group_4".
	 */
	private int numberOfSplitGroups;

	/**
	 * @param a3channel The channel this hierarchy belongs to.
	 */
	public A3HierarchyView(A3GroupChannel a3channel){
		channel = a3channel;
		hierarchy = new ArrayList<String>();
		numberOfSplitGroups = 0;
	}

	/**
	 * Handles the incoming messages about managing hierarchy.
	 * @param message The incoming message.
	 */
	public void onMessage(A3Message message){
		try{
			switch(message.reason){
				case A3Constants.CONTROL_HIERARCHY_REPLY:

					/* This message is like
					 * "senderAddress Constants.CONTROL_HIERARCHY_REPLY numberOfSubgroups name1 name2 ..."
					 * or "Constants.CONTROL_HIERARCHY_REPLY numberOfSubgroups", if the hierarchy is empty.
					 *
					 * If I receive this message, I am the channel.
					 * This message is the supervisor's answer to the hierarchy request I made when I joined the session:
					 * I set my hierarchy as the one received by the supervisor.
					 */
					hierarchy = new ArrayList<String>();

					if(!message.object.equals("")){

						String[] splittedHierarchy = message.object.split(A3Constants.SEPARATOR);
						numberOfSplitGroups = Integer.valueOf(splittedHierarchy[0]);

						for(int i = 1; i < splittedHierarchy.length; i++)
							hierarchy.add(splittedHierarchy[i]);
					}

					break;

				case A3Constants.CONTROL_ADD_TO_HIERARCHY:

					/* This message is like
					 * "senderAddress Constants.CONTROL_ADD_TO_HIERARCHY groupName".
					 *
					 * It is sent by the node on which the supervisor resides,
					 * to notify that my group has another parent group.
					 *
					 * I add the new parent group's information to my hierarchy.
					 */
					synchronized(hierarchy){
						hierarchy.add(message.object);
					}
					break;

				case A3Constants.CONTROL_REMOVE_FROM_HIERARCHY:

					/* This message is like
					 * "senderAddress Constants.CONTROL_REMOVE_FROM_HIERARCHY groupName".
					 *
					 * It is sent by the node on which the supervisor resides,
					 * to notify that the group "groupName" is no longer parent of my group.
					 *
					 * I remove the "groupName" group's information from my hierarchy.
					 */
					synchronized(hierarchy){
						hierarchy.remove(message.object);
					}
					break;
				case A3Constants.CONTROL_INCREASE_SUBGROUPS:
					this.incrementSubgroupsCounter();
					break;
				default:
					break;
			}
		} catch (Exception e) {}
	}

	/**
	 * Creates the string representation of the type A3HierarchyView.
	 * The obtained string is like "numberOfSubgroups name1 name2 ..." or "numberOfSubgroups".
	 */
	@Override
	public synchronized String toString(){

		String result = String.valueOf(numberOfSplitGroups);
		if(!hierarchy.isEmpty()){
			result = result + A3Constants.SEPARATOR + hierarchy.get(0);
			for(int i = 1; i < hierarchy.size(); i ++){
				result = result + A3Constants.SEPARATOR + hierarchy.get(i);
			}
		}
		return result;
	}

	public synchronized ArrayList<String> getHierarchy() {
		return hierarchy;
	}

	public synchronized void incrementSubgroupsCounter(){
		numberOfSplitGroups++;
	}
	
	public synchronized int getSubgroupsCounter(){
		return numberOfSplitGroups;
	}
}

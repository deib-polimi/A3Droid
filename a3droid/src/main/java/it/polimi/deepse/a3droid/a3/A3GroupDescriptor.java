package it.polimi.deepse.a3droid.a3;

/**
 * This class contains the names of the group and of its roles.
 * It must be extended in order to define the fitness functions to use in supervisor election operation.
 * The default implementations of requestSplit operation fitness function raise an exception to warn that they are not defined.
 * @author Francesco
 *
 */
public abstract class A3GroupDescriptor {
	
	/**The name of the group this descriptor describe.*/
	private String name;
	
	/**The role the supervisor must play in group "name".*/
	private String supervisorRoleId;
	
	/**The role a follower must play in group "name".*/
	private String followerRoleId;
	
	/**
	 * 
	 * @param name The name of the group this descriptor describe.
	 * @param supervisorRoleId The role the supervisor must play in group "name".
	 * @param followerRoleId The role a follower must play in group "name".
	 */
	public A3GroupDescriptor(String name, String supervisorRoleId, String followerRoleId){
		this.name = name;
		this.supervisorRoleId = supervisorRoleId;
		this.followerRoleId = followerRoleId;
	}
	
	/**To override in order to determine the value of an integer fitness function used for requestSplit.
	 * 
	 * @return Nothing (default implementation). It should return the value of an integer fitness function.
	 * @throws Exception The integer fitness function is not implemented (default implementation).
	 */
	public int getIntegerSplitFitnessFunction() throws Exception {
		throw new Exception("Split failed: integer fitness function not implemented.");
	}

	/**To override in order to determine the value of a boolean fitness function used for requestSplit.
	 * 
	 * @return Nothing (default implementation). It should return the value of a boolean fitness function.
	 * @throws Exception The integer fitness function is not implemented (default implementation).
	 */
	public boolean getBooleanSplitFitnessFunction() throws Exception {
		throw new Exception("Split failed: boolean fitness function not implemented.");
	}

	public abstract int getSupervisorFitnessFunction();

	/**
	 * Create the string representation of the type GroupInfo.
	 * The obtained string is like "name supervisorRoleId followerRoleId".
	 */
	@Override
	public String toString(){
		return name + A3Constants.SEPARATOR + supervisorRoleId + A3Constants.SEPARATOR + followerRoleId;
	}

	public String getGroupName() {
		return name;
	}

	public String getSupervisorRoleId() {
		return supervisorRoleId;
	}

	public String getFollowerRoleId() {
		return followerRoleId;
	}

	@Override
	public int hashCode() {
		return name.hashCode() + supervisorRoleId.hashCode() + followerRoleId.hashCode();
	}

	@Override
	public boolean equals(Object o){
		if (!(o instanceof A3GroupDescriptor))
			return false;
		if (o == this)
			return true;

		return this.name.equals(((A3GroupDescriptor) o).name) &&
				this.supervisorRoleId.equals(((A3GroupDescriptor) o).supervisorRoleId) &&
				this.followerRoleId.equals(((A3GroupDescriptor) o).followerRoleId);
	}

	/**
     * The session with a service has been lost.
     */
    public enum A3GroupState {
        IDLE,
        ELECTION,
        ACTIVE,
        STACK,
        REVERSE_STACK,
        MERGE,
        SPLIT
    }
}

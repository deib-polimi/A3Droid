package it.polimi.deepse.a3droid.a3;

/**This class represents the logic that will be executed on a follower.
 * It is needed only to distinguish between followers and supervisor.
 */
public abstract class A3FollowerRole extends A3Role{

	abstract void receiveApplicationMessage(A3Message message);
}

package it.polimi.deepse.a3droid.a3;

import java.util.ArrayList;
import java.util.Random;

/**
 * This class represents the logic executed on a supervisor.
 * It adds an AllJoyn signal receiver to the role logic, in order to receive messages from followers.
 * @author Francesco
 *
 */
public abstract class A3SupervisorRole extends A3Role{

    abstract void receiveApplicationMessage(A3Message message);
}

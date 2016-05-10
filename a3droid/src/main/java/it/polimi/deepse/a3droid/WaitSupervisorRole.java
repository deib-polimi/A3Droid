package it.polimi.deepse.a3droid;

/**
 * This class represents the role played by the supervisor of "wait" group.
 * There's no need to do anything.
 * @author Francesco
 *
 */
public class WaitSupervisorRole extends A3SupervisorRole {

	public WaitSupervisorRole() {
		super();
	}

	@Override
	public void logic() {
		showOnScreen("(WaitSupervisorRole)");
		active = false;
	}

	@Override
	public void receiveApplicationMessage(A3Message message) {
		showOnScreen(message.toString());
	}

	@Override
	public void onActivation() {}

	@Override
	public void memberAdded(String name) {}

	@Override
	public void memberRemoved(String name) {}

}

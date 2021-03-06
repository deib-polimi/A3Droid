package it.polimi.deepse.a3droid.a3;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;

import it.polimi.deepse.a3droid.a3.events.A3UIEvent;


/**
 * This class represents the role that the Node can play in a group.
 * A list of the roles a node can play resides on A3Node, it is fixed at node creation time and it can't change.
 * The A3Node constructor automatically sets the field "node" of the role to itself,
 * and the "className" field of the role to the canonical name of the role class.
 * 
 * There are two roles that can be played in a group by a node: the supervisor or a follower.
 * So a node, to joinGroup a group, must have both roles in its list.
 * If it has both of them, the node creates a channel and sets the roles of that channel to a clone of theirs:
 * cloning the roles is necessary in order to avoid that two channels with the same role block together
 * when deactivating only one of them.
 * When needed, the channel creates a new thread using the role and starts it.
 * 
 * The role className is transmitted in messages about operations between groups.
 * Being it the canonical name of the class, a role is uniquely identified.
 * When a node receives communication to joinGroup to a group with certain two roles,
 * it looks for them in its list, and if it finds them it connects to the group.
 * 
 * This class must be extended, so this solution solves the problem to instantiate the correct superclass.
 * The constructors of the superclasses must call this constructor by calling "super();"
 * and must contain only that instruction.
 * @author Francesco
 *
 */
public abstract class A3Role implements Runnable {

	protected static final String TAG = "a3droid.A3Role";

	/**It indicates if this role is currently active or not.*/
	private boolean active;

	/**The canonical name of this role class.*/
	protected String className;

	/**The node whose methods this role can call.*/
	protected A3Node node;

	/**The channel this role belongs to.*/
	private A3GroupChannel channel;

	private RoleMessageHandler messageHandler;
	/**
	 * Set this role as not active and the className ofA this role to its class canonical name.
	 */
	public A3Role(){
		super();
		active = false;
		className = getClass().getCanonicalName();
	}

	/**
	 * It is composed of an initialization part and a loop that is executed while this role is active.
	 * The initialization part must be defined in the abstract method "onActivation()".
	 * The logic in the loop must be defined in the abstract method "logic()".
	 */
	@Override
	public void run(){

		onActivation();

		/*while(active){
			logic();
		}*/
	}

	/**
	 * The initialization part executed before the beginning of the loop.
	 * This method must be seen as a constructor, since the real constructor must contain only "super()" instruction.
	 */
	public abstract void onActivation();

	public void onDeactivation(){}

	public void setActive(boolean active) {
		this.active = active;
		if(active)
			messageHandler = new RoleMessageHandler(this);
		else if(messageHandler != null) {
			quitHandler();
			onDeactivation();
		}

	}

	public boolean isActive(){
		return active;
	}

	public void sendUnicast(A3Message message, String address){
		try {
			message.addresses = new String [] {address};
			channel.addOutboundItem(message, A3GroupChannel.UNICAST_MSG);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendMulticast(A3Message message, String ... addresses){
		try {
			message.addresses = addresses;
			channel.addOutboundItem(message, A3GroupChannel.MULTICAST_MSG);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendBroadcast(A3Message message){
		try {
			channel.addOutboundItem(message, A3GroupChannel.BROADCAST_MSG);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendToSupervisor(A3Message message){
		try {
			message.addresses = new String [] {channel.getSupervisorId()};
			channel.addOutboundItem(message, A3GroupChannel.UNICAST_MSG);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * The logic that must be executed when receiving an application message.
	 * Control messages are handled by A3GroupChannel.
	 * @param message The received message.
	 */
	abstract void receiveApplicationMessage(A3Message message);

	/**It receives the incoming messages and passes them to another thread, releasing the channel.
	 *
	 * @param message The incoming message.
	 */
	public void handleMessage(A3Message message){
		if(isActive())
			forwardMessageToHandler(message);
	}

	private void forwardMessageToHandler(A3Message message) {
		Message msg = messageHandler.obtainMessage();
		msg.obj = message;
		messageHandler.sendMessage(msg);
	}

	public String getClassName(){
		return className;
	}

	public String getChannelId(){
		return channel.getChannelId();
	}
	public String getGroupName(){
		return channel.getGroupName();
	}
	public void setNode(A3Node node){
		this.node = node;
	}

	public void setChannel(A3GroupChannel a3channel) {
		channel = a3channel;
	}

	public A3GroupChannel getChannel() {
		return channel;
	}

	public void postUIEvent(int what, String message){
		EventBus.getDefault().post(new A3UIEvent(what, message));
	}

	private void quitHandler(){
		messageHandler.quitSafely();
	}

	/**
	 * This background thread is needed to not block the bus thread whenever a new message is
	 * received and must be sent to the role
	 **/
	private static class RoleMessageHandler extends HandlerThread {

		private final WeakReference<A3Role> mRole;
		private Handler mHandler;

		public RoleMessageHandler(A3Role role) {
			super("RoleMessageHandler_" + role.getGroupName());
			mRole = new WeakReference<>(role);
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

			final A3Role role = mRole.get();

			mHandler = new Handler(getLooper()) {
				@Override
				public void handleMessage(Message msg) {
					role.receiveApplicationMessage((A3Message) msg.obj);
				}
			};
		}
	}
}

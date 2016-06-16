package it.polimi.deepse.a3droid;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.ProxyBusObject;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;

import java.util.ArrayList;

/**
 * This class is the channel that lets the nodes communicate with each other. It
 * has the methods to receive broadcast messages and to send messages to the
 * supervisor of the group.
 * 
 * When a node needs to join a group, it creates a channel. The channel start
 * the discovery of the group and waits 2 seconds: if the group was found, then
 * the channel connects to it, otherwise the channel creates the group and
 * connects to it. If the group name is found, but the Service is not visible,
 * the channels becomes creates the group and connects to it.
 * 
 * Once connected, the channel must know if it is the supervisor or a follower:
 * if the channel is the supervisor, it receives communication within a 2
 * seconds timeout, and it soon sets itself as the supervisor. Otherwise the
 * channel sets itself as a follower after the timeout.
 * 
 * @author Francesco
 * 
 */
public class A3Channel extends Thread implements BusObject, TimerInterface,
		UserInterface {

	public static final int FIND_NAME_TIME = (int) (2000 + Math.random() * 1000);
	public static final int CONNECT_TIME = 2 * 1000;

	public static final int FIND_NAME_TIME_FIRED = 0;
	public static final int CONNECT_TIME_FIRED = 1;

	/** The name of the group to join. */
	private String groupNameNoSuffix;
	private String groupName;
	private String groupSuffix = "";

	/** The connection to the AllJoyn bus. */
	private BusAttachment mBus;

	/** The Service proxy to communicate with. */
	private ProxyBusObject mProxyObj;

	/** The interface used to communicate to the proxy. */
	private A3ServiceInterface serviceInterface;

	/** The id of the AllJoyn session this channel is joined. */
	private int mSessionId;

	/** It indicates if this channel is connected or not. */
	private boolean mIsConnected;

	/** The node this channel belongs to. */
	private A3Node node;

	/** The thread which manages session lost and timeout firing. */
	private CallbackThread callbackThread;

	/** The Service this channel eventually creates. */
	private Service service;

	/** The address of this channel. */
	private String myId;

	/** A findNameTimer. */
	private Timer findNameTimer;
	private Timer connectTimer;

	/** */
	private boolean discovered;

	/** The thread which handles the received messages. */
	private MessageHandler messageHandler;

	/** Indicates if the group name was found, but the Service wasn't visible. */
	private boolean inTransitionConditions;

	/** Indicates if this channel is currently the group supervisor or not. */
	private boolean isSupervisor;

	/** The receiver used to receive unicast messages from the Service. */
	private A3UnicastReceiver unicastReceiver;

	/** The logic that is executed when this channel is a follower. */
	private A3FollowerRole followerRole;

	/** The logic that is executed when this channel is the supervisor. */
	private A3SupervisorRole supervisorRole;

	/** The role that is currently active on this channel. */
	private A3Role activeRole;

	/** The list of message kinds this channel is interested in. */
	private Subscriptions subscriptions;

	/** The list of the messages waiting to be sent to the supervisor. */
	private MessageQueue queue;

	/** It indicates if the channel must reconnect or not. */
	private boolean reconnect;

	/** The list of the parent groups. */
	private Hierarchy hierarchy;

	/** The thread which passes the incoming messages to the active role. */
	private InputQueueHandler inputQueueHandler;

	/** The list of the incoming message. */
	private MessageQueue inputQueue;

	/** true if this channel can only act as a follower, false otherwise. */
	private boolean followerOnly;

	/** true if this channel can only act as a supervisor, false otherwise. */
	private boolean supervisorOnly;

	/** The user interface to interact to. */
	protected UserInterface ui;

	/** true if this channel is used by the application, false otherwise. */
	private boolean connectedForApplication;

	/** true if this channel is used by the system, false otherwise. */
	private int connectedForSystem;

	private boolean firstConnection;

	/** The descriptor of the group this channel is connected to. */
	private GroupDescriptor groupDescriptor;

	private Sender sender;
    private Thread senderThread;

	/**
	 * @param a3node
	 *            The node this channel belongs to.
	 * @param userInterface
	 *            The user interface to interact to.
	 * @param groupDescriptor
	 *            The descriptor of the group to joinGroup this channel to.
	 */
	public A3Channel(A3Node a3node, UserInterface userInterface,
			GroupDescriptor groupDescriptor) {

		super();
		start();
		mIsConnected = false;
		node = a3node;
		serviceInterface = null;
		service = null;
		myId = null;
		discovered = false;
		inTransitionConditions = false;
		isSupervisor = false;
		subscriptions = new Subscriptions(this);
		hierarchy = new Hierarchy(this);
		queue = new MessageQueue();
		inputQueue = new MessageQueue();
		ui = userInterface;
		connectedForApplication = false;
		connectedForSystem = 0;
		firstConnection = true;
		this.groupDescriptor = groupDescriptor;

		/*
		 * Thread that reads the first message in the queue and try to send it
		 * to the Service. If the transmission fails, the channel reconnects and
		 * the message is still available in the queue, otherwise such message
		 * is removed from the queue.
		 */
		/*
		 * sender = new Thread(){ public void run(){ A3Message message;
		 * while(true){ try{ message = queue.get(); if(send(message))
		 * queue.dequeue(); else if(reconnect) reconnect(); } catch (Exception
		 * e) {} } } }; sender.start();
		 */

		callbackThread = new CallbackThread();
		messageHandler = new MessageHandler();
		inputQueueHandler = new InputQueueHandler();
		inputQueueHandler.start();
	}

	/**
	 * Called by an A3Node when connecting the channel. Used to joinGroup to the
	 * AllJoyn bus and to start group discovery. It also sets the roles of this
	 * channel.
	 * 
	 * @param groupName
	 *            The name of the group to which to joinGroup this channel.
	 * @param a3FollowerRole
	 *            The logic that is executed when this channel is a follower.
	 * @param a3SupervisorRole
	 *            The logic that is executed when this channel is the
	 *            supervisor.
	 * @param supervisorOnly
	 * @param followerOnly
	 */
	public void connect(String groupName, A3FollowerRole a3FollowerRole,
			A3SupervisorRole a3SupervisorRole, boolean followerOnly,
			boolean supervisorOnly) {
		// TODO Auto-generated method stub

		this.groupNameNoSuffix = Constants.PREFIX + groupName;
		followerRole = a3FollowerRole;
		supervisorRole = a3SupervisorRole;
		this.followerOnly = followerOnly;
		this.supervisorOnly = supervisorOnly;
		setName(groupName);
		connect(groupName);

	}

	/** Used to joinGroup to the AllJoyn bus and to start group discovery. */
	public void connect(String group_name) {

		showOnScreen("Starting...");

		this.groupName = Constants.PREFIX + group_name;

		mBus = new BusAttachment(getClass().getPackage().getName(),
				BusAttachment.RemoteMessage.Receive);

		mBus.registerBusListener(new BusListener() {

			@Override
			public void foundAdvertisedName(String name, short transport,
					String namePrefix) {

				/*
				 * "name" can be a prefix of another group name: in this case, I
				 * must do nothing. If the group is duplicated, starting the
				 * merging procedure takes time and blocks the callbacks, so I
				 * manage this case in another thread.
				 */
				if (removeSuffix(name).equals(groupNameNoSuffix)) {
					discovered = true;
					groupSuffix = name.replace(groupNameNoSuffix, "");
					groupName = name;
				}
			}

			public void lostAdvertisedName(String name, short transport,
					String namePrefix) {
			}
		});

		Status status = mBus.connect();
		if (Status.OK != status) {
			showOnScreen("status = " + status + " dopo joinGroup()");
			return;
		}

		status = mBus.registerSignalHandlers(this);
		if (status != Status.OK) {
			showOnScreen("status = " + status
					+ " dopo registerSignalHandlers()");
			return;
		}

		// The discovery and the findNameTimer start.
		status = mBus.findAdvertisedName(groupNameNoSuffix + '.');
		if (Status.OK != status) {
			showOnScreen("status = " + status + " dopo findAdvertisedName()");
			return;
		}

		try {
			findNameTimer = new Timer(this, FIND_NAME_TIME_FIRED, (int) (FIND_NAME_TIME));
			findNameTimer.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * It is called when the timeout fires and the group name was discovered. It
	 * lets this channel join the AllJoyn session and joinGroup to the group.
	 */
	public void joinSession() {

		try {
			short contactPort = Constants.CONTACT_PORT;
			SessionOpts sessionOpts = new SessionOpts();
			sessionOpts.transports = SessionOpts.TRANSPORT_ANY;
			Mutable.IntegerValue sessionId = new Mutable.IntegerValue();

			Status status = mBus.joinSession(groupName, contactPort, sessionId,
					sessionOpts, new SessionListener() {

						@Override
						public void sessionLost(int sessionId, int reason) {

							Message msg = callbackThread.obtainMessage();
							msg.arg2 = Constants.SESSION_LOST;
							callbackThread.sendMessage(msg);
						}
					});

			if (status == Status.OK)
				onSessionJoined(sessionId);

			else {
				mIsConnected = false;
				showOnScreen("status = " + status + " dopo joinSession()");
				// The group name was found, but the Service is not visible.
				if (status == Status.ALLJOYN_JOINSESSION_REPLY_UNREACHABLE) {
					inTransitionConditions = true;
					createGroup();
					reconnect();
				} else {
					// If this channel is already joined.
					if (status == Status.ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED)
						onSessionJoined(sessionId);
					else {
						if (status == Status.ALLJOYN_JOINSESSION_REPLY_FAILED) {
							discovered = false;
							reconnect();
						} else
							reconnect();
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** It disconnect this channel from the group and the AllJoyn bus. */
	public void disconnect() {

		groupName = groupNameNoSuffix;

		if(findNameTimer != null)
			findNameTimer.abort();

		if(connectTimer != null)
			connectTimer.abort();

		/*
		 * The name of my UnicastReceiver is strictly based on my address in the
		 * group, so I must disconnect it when I disconnect.
		 */
		if(unicastReceiver != null)
			unicastReceiver.disconnect();

		//try {
			//sender.stop();
        if(sender != null)
            sender.terminate();
            /*senderThread.join();
		} catch (InterruptedException e) {
            e.printStackTrace();
        }*/

		/*
		 * I stop the logic of this channel, whatever it is. A supervisor only
		 * channel which discovers to be a follower at first connection doesn't
		 * have any active logic: that's why I ignore an error here.
		 */

		try{
			if(activeRole != null)
				activeRole.setActive(false);
		} catch (Exception ex) {
            ex.printStackTrace();
		}

		if (isSupervisor)
			isSupervisor = false;

		try {
			if (mIsConnected) {
				mBus.leaveSession(mSessionId);
				mIsConnected = false;
				firstConnection = true;
			}
		} catch (Exception ex) {
			showOnScreen("EXCEPTION IN A3ChannelInterface.disconnect(): "
					+ ex.getMessage());
		}

		try {
			mBus.disconnect();
			reset();
			showOnScreen("Disconnected.");
		} catch (Exception ex) {
            ex.printStackTrace();
		}
	}

	/**
	 * It resets the initial configuration of the channel, in order to reuse it
	 * next.
	 */
	private void reset() {
		// TODO Auto-generated method stub

		groupName = groupName.replace(groupSuffix, "");
		mIsConnected = false;
		serviceInterface = null;
		service = null;
		myId = null;
		discovered = false;
		isSupervisor = false;
	}

	/**
	 * It is used in order to reconnect this channel at the same group in case
	 * of errors, or when a duplicated group is found.
	 */
	private void reconnect() {
		// TODO Auto-generated method stub
		disconnect();
		connect(getGroupName());
	}

	/**
	 * It is called when the channel has just joined the AllJoyn session. It
	 * retreives the communication interface, connects the unicast receiver and
	 * starts the findNameTimer to wait for communication about the role this channel
	 * has in the group.
	 * 
	 * @param sessionId
	 *            The id of the AllJoyn session this channel is joined.
	 */
	private void onSessionJoined(Mutable.IntegerValue sessionId) {			

		// TODO Auto-generated method stub
		mProxyObj = mBus.getProxyBusObject(groupName, "/SimpleService",
				sessionId.value, new Class<?>[] { A3ServiceInterface.class });

		serviceInterface = mProxyObj.getInterface(A3ServiceInterface.class);

		mSessionId = sessionId.value;
		mIsConnected = true;
		if(myId == null)
			myId = mBus.getUniqueName();
		showOnScreen("Channel ID: " + myId);

		String id = String.valueOf(myId.hashCode());

		/*
		 * The name of my UnicastReceiver is strictly based on my address in the
		 * group, so I can create and joinGroup it only now that I know my
		 * address.
		 */
		unicastReceiver = new A3UnicastReceiver(groupName + "._" + id, this);
		unicastReceiver.connect();
		
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		// I transmit my subscriptions only if I am subscribed to receive
		// something.
		String message = subscriptions.toString();
		if (!message.equals("")) {
			A3Message subscriptionsMessage = new A3Message(
					Constants.SUBSCRIPTION, message);
			sendToSupervisor(subscriptionsMessage);
		}
		sendToSupervisor(new A3Message(Constants.GET_HIERARCHY, ""));

		try {
			sendToSupervisor(new A3Message(
					Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST, ""));
		} catch (Exception e) {
			e.printStackTrace();
		}
		// unblock();

		/*
		 * Thread that reads the first message in the queue and try to send it
		 * to the Service. If the transmission fails, the channel reconnects and
		 * the message is still available in the queue, otherwise such message
		 * is removed from the queue.
		 */
		sender = new Sender();
		senderThread = new Thread(sender);
        senderThread.start();
		showOnScreen("Connected.");
		try {
			connectTimer = new Timer(this, CONNECT_TIME_FIRED, CONNECT_TIME);
			connectTimer.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Called when this channels becomes follower. It deactivates the supervisor
	 * role (if it is active) and it activates follower role.
	 */
	private void becomeFollower() {
		// TODO Auto-generated method stub

		try {
			/*
			 * At my first connection time I'm neither the supervisor nor a
			 * follower: if I discover to be a follower, I don't have to
			 * deactivate the supervisor role. In other cases, this method is
			 * called only if I was the supervisor, so this "if" is always
			 * executed.
			 */
			if (isSupervisor) {
				mBus.unregisterSignalHandlers(supervisorRole);
				supervisorRole.setActive(false);
			}

			if (supervisorOnly) {
				disconnect();

				node.setWaiting(this);
			} else {
				isSupervisor = false;
				followerRole.setActive(true);
				activeRole = followerRole;
				new Thread(followerRole).start();

				synchronized (inputQueue) {
					inputQueue.notify();
				}

				node.setConnected(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Called when this channels becomes supervisor. It deactivates the follower
	 * role (if it is active) and it activates supervisor role.
	 */
	private void becomeSupervisor() {
		// TODO Auto-generated method stub

		// This method is always called when I am the supervisor, but I wasn't
		// it before.
		isSupervisor = true;
		try {
			if (!supervisorOnly && !firstConnection)
				followerRole.setActive(false);

			if (followerOnly) {
				disconnect();

				node.setWaiting(this);
			} else {
				firstConnection = false;
				supervisorRole.setActive(true);
				activeRole = supervisorRole;
				new Thread(supervisorRole).start();
				mBus.registerSignalHandlers(supervisorRole);

				synchronized (inputQueue) {
					inputQueue.notify();
				}

				node.setConnected(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Called by the Service when it sends a broadcast message. It passes the
	 * received messages to another thread, in order not to block the bus.
	 * 
	 * @param message
	 *            The received message.
	 */
	@BusSignalHandler(iface = Constants.PACKAGE_NAME + ".A3ServiceInterface", signal = "ReceiveBroadcast")
	public void ReceiveBroadcast(A3Message message) {
		
		Message msg = messageHandler.obtainMessage();
		msg.obj = message;
		messageHandler.sendMessage(msg);
	}

	/**
	 * It is called by the thread that handles the received messages.
	 * 
	 * @param message
	 *            The received message.
	 */
	private void onMessage(A3Message message) throws Exception {

		/*
		 * If the session was lost and now I receive a message, then I'm
		 * connected and I can retrive the information about the new session.
		 */
		if (!mIsConnected) {
			mIsConnected = true;
		}

		if (myId == null)
			myId = mBus.getUniqueName();

		switch (message.reason) {
		case Constants.NEW_SUPERVISOR:
			// The new supervisor was elected.
			
			if (message.object.equals("?")) {
				message = new A3Message(
						Constants.SUPERVISOR_FITNESS_FUNCTION_REPLY,
						String.valueOf(getSupervisorFitnessFunction()));
				sendToSupervisor(message);
			}

			else {
				if (message.object.equals(myId)) {
					if (!isSupervisor) {
						becomeSupervisor();
					}
				}

				else {
					if (isSupervisor || firstConnection) {
						firstConnection = false;
						becomeFollower();
					}
				}
			}
			break;

		case Constants.SUBSCRIPTION:
		case Constants.UNSUBSCRIPTION:
			subscriptions.onMessage(message);
			break;

		case Constants.HIERARCHY:
		case Constants.ADD_TO_HIERARCHY:
		case Constants.REMOVE_FROM_HIERARCHY:
			hierarchy.onMessage(message);
			break;

		case Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST:
			if (!followerOnly) {
				// I send the value of my fitness function to the Service, which
				// collects it.
				message = new A3Message(
						Constants.SUPERVISOR_FITNESS_FUNCTION_REPLY,
						String.valueOf(getSupervisorFitnessFunction()));
				sendToSupervisor(message);
			}
			break;

		case Constants.BOOLEAN_SPLIT_FITNESS_FUNCTION:
			// If my fitness function equals true, I transfer to the new group.
			hierarchy.incrementSubgroupsCounter();
			if (!isSupervisor && getBooleanSplitFitnessFunction())
				node.actualMerge(
						getGroupName() + "_" + hierarchy.getSubgroupsCounter(),
						groupName);
			break;

		case Constants.INTEGER_SPLIT_FITNESS_FUNCTION:
			// I send my integer split fitness function value to the Service.
			hierarchy.incrementSubgroupsCounter();
			if (!isSupervisor) {
				message = new A3Message(
						Constants.INTEGER_SPLIT_FITNESS_FUNCTION,
						String.valueOf(getIntegerSplitFitnessFunction()));
				sendToSupervisor(message);
			}
			break;

		case Constants.NEW_SPLITTED_GROUP:
			/*
			 * The supervisor triggered a random split command: a new group is
			 * created and I get notified of it.
			 */
			hierarchy.incrementSubgroupsCounter();
			break;

		case Constants.MERGE:
			// "senderAddress Constants.MERGE otherGroupName".
			node.actualMerge(message.object, getGroupName());
			break;

		case Constants.SPLIT:

			/*
			 * I will joinGroup to a group splitted by this group, which has the
			 * same roles of this group, so I don't need to check for right
			 * roles here.
			 */
			if (!isSupervisor)
				node.actualMerge(
						getGroupName() + "_" + hierarchy.getSubgroupsCounter(),
						getGroupName());

			break;

		case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST:

			try {
				sendToSupervisor(new A3Message(
						Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION,
						message.object
								+ Constants.A3_SEPARATOR
								+ String.valueOf(node
								.getSupervisorFitnessFunction(message.object))));
			} catch (Exception e) {
				e.printStackTrace();
				/*
				 * I can have this exception only if the channel to group
				 * "message.object" doesn't exist. In this case, I don't have to
				 * send back reply message, so I do nothing.
				 */
			}

			break;

		case Constants.WAIT_NEW_SUPERVISOR:
			// "senderAddress Constants.WAIT_NEW_SUPERVISOR groupName supervisorId".
			String[] splittedObject = ((String) message.object)
					.split(Constants.A3_SEPARATOR);

			A3Channel channel;

			try {
				channel = node.getChannel(splittedObject[0]);

				if (splittedObject[1].equals(myId)) {

					if (channel.followerOnly) {
						channel.disconnect();
						node.setWaiting(channel);
					} else {
						channel.connect(splittedObject[0]);
						channel.becomeSupervisor();
						channel.sendToSupervisor(new A3Message(
								Constants.NEW_SUPERVISOR, ""));
					}
				}

				else {
					if ((channel.supervisorOnly)) {
						channel.disconnect();
						node.setWaiting(channel);
					} else {
						channel.connect(splittedObject[0]);
						channel.becomeFollower();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			break;

		case Constants.WAIT_MERGE:
			// "senderAddress Constants.WAIT_MERGE groupToJoin groupToDestroy".
			splittedObject = ((String) message.object)
					.split(Constants.A3_SEPARATOR);
			node.actualMerge(splittedObject[0], splittedObject[1]);
			break;
			
		//case Constants.MEMBER_REMOVED:
			//node.memberRemoved((String) message.object);
			//break;
			
		default:
			// I pass the message to the active role.
			inputQueue.enqueue(message);
			break;
		}
	}

	/**
	 * It puts a message in the queue of the messages directed to the
	 * supervisor.
	 * 
	 * @param msg
	 *            The message to be sent.
	 */
	public void sendToSupervisor(A3Message msg) {
		queue.enqueue(msg);
	}

	/**
	 * Sends a message to the Service and, form there, to the supervisor. If the
	 * transmission is unsuccessful, this channel reconnects, and a view update
	 * starts. If the channel isn't connected, the sender thread is blocked.
	 * 
	 * @param msg
	 *            The message to send.
	 */
	public boolean send(A3Message msg) {
		boolean inServiceView = false;
		boolean sent = false;

		synchronized (this) {
			while (!mIsConnected) {
				try {
					showOnScreen("Not connected, waiting");
					wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		try {

			msg.senderAddress = myId;

			if (mIsConnected && serviceInterface != null) {
				inServiceView = serviceInterface.sendToSupervisor(msg);

				if (!inServiceView) {
					sent = false;
					reconnect = true;
				}

				else {
					sent = true;
					reconnect = false;
					inTransitionConditions = false;
					inServiceView = true;
				}
			} else {
				sent = false;
				reconnect = false;
			}

		} catch (BusException ex) {
			ex.printStackTrace();
			if (ex.getMessage().equals("org.alljoyn.Bus.Exiting")) {
				//try {
					//sender.stop();
                    sender.terminate();
                    /*senderThread.join();
				} catch (Exception exx) {
					ex.printStackTrace();
				}*/
			}else {
				sent = false;
				reconnect = true;
			}
		}
		return sent;
	}

	/**
	 * Sends a message to the Service and, form there, to all members of the
	 * group. Such operation is possible only if this channel is the supervisor.
	 * If the transmission is unsuccesful, this channel reconnects, and a view
	 * update starts.
	 * 
	 * @param message
	 *            The message to send to every member of the group.
	 */
	public void sendBroadcast(A3Message message) {

		boolean ok = false;

		if (isSupervisor) {
			message.senderAddress = myId;
			try {
				ok = serviceInterface.sendBroadcast(message);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (!ok)
				reconnect();
		} else
			showOnScreen("Sending failed: I'm not the supervisor.");
	}

	/**
	 * Sends a message to the Service and, form there, to the specified member
	 * of the group. Such operation is possible only if this channel is the
	 * supervisor. If the transmission is unsuccesful, this channel reconnects,
	 * and a view update starts.
	 * 
	 * @param message
	 *            The message to send.
	 * @param receiverAddress
	 *            The address of the channel that must receive the message.
	 */
	public void sendUnicast(A3Message message, String receiverAddress) {

		boolean ok = false;

		if (isSupervisor) {
			message.senderAddress = myId;
			try {
				ok = serviceInterface.sendUnicast(message, receiverAddress);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (!ok)
				reconnect();
		} else
			showOnScreen("Sending failed: I'm not the supervisor.");
	}

	/**
	 * Sends a message to the Service and, form there, to the members of the
	 * group which are interested in receiving it, basing on the reason of the
	 * message. Such operation is possible only if this channel is the
	 * supervisor. If the transmission is unsuccesful, this channel reconnects,
	 * and a view update starts.
	 * 
	 * @param message
	 *            The message to send.
	 */
	public void sendMulticast(A3Message message) {

		boolean ok = false;

		if (isSupervisor) {
			message.senderAddress = myId;
			try {
				ok = serviceInterface.sendMulticast(message);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (!ok)
				reconnect();
		} else
			showOnScreen("Sending failed: I'm not the supervisor.");
	}

	/**
	 * Sends a message to the Service and, form there, to the members of the
	 * group specified in "destinations". This results in calling
	 * "sendUnicast(message, destination)" on the Service for every destination.
	 * Such operation is possible only if this channel is the supervisor. If the
	 * transmission is unsuccesful, this channel reconnects, and a view update
	 * starts.
	 * 
	 * @param message
	 *            The message to send.
	 * @param destinations
	 *            The members of the group that must receive the message.
	 */
	public void sendMulticast(A3Message message, ArrayList<String> destinations) {

		boolean ok = true;

		if (isSupervisor) {
			for (int i = 0; i < destinations.size() && ok; i++) {
				try {
					ok = serviceInterface.sendUnicast(message,
							destinations.get(i));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (!ok)
				reconnect();
		} else
			showOnScreen("Sending failed: I'm not the supervisor.");
	}

	/**
	 * It adds a new subscription to the list "mySubscriptions" used on the
	 * channel and notifies it to the Service.
	 * 
	 * @param reason
	 *            The subscription to add.
	 */
	public void subscribe(int reason) {
		try {
			subscriptions.subscribe(reason);
			A3Message subscriptionsMessage = new A3Message(
					Constants.SUBSCRIPTION, String.valueOf(reason));
			sendToSupervisor(subscriptionsMessage);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * It removes a subscription from the list "mySubscriptions" used on the
	 * channel and notifies it to the Service.
	 * 
	 * @param reason
	 *            The subscription to remove.
	 */
	public void unsubscribe(int reason) {
		// TODO Auto-generated method stub
		try {
			subscriptions.unsubscribe(reason);
			A3Message unsubscriptionsMessage = new A3Message(
					Constants.UNSUBSCRIPTION, String.valueOf(reason));
			sendToSupervisor(unsubscriptionsMessage);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void showOnScreen(String message) {
		// TODO Auto-generated method stub

		if (message.startsWith("("))
			ui.showOnScreen(message);
		else
			ui.showOnScreen("(" + getGroupName() + "): " + message);
	}

	/** It creates the Service when it doesn't exist. */
	private void createGroup() {
		// TODO Auto-generated method stub

		try {

			if (!mIsConnected) {
				service = new Service(groupName, node, inTransitionConditions);
				String noSuffix = groupName;
				groupName = service.connect();
				groupSuffix = groupName.replace(noSuffix, "");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void timerFired(int reason) {
		// TODO Auto-generated method stub
		Message msg = callbackThread.obtainMessage();
		msg.arg1 = reason;
		msg.arg2 = Constants.TIMER_FIRED;
		callbackThread.sendMessage(msg);
	}

	/** It unblocks the thread which sends the messages to the supervisor. */
	public synchronized void unblock() {
		// TODO Auto-generated method stub
		try {
			mIsConnected = true;
			notify();
		} catch (Exception ex) {
		}
	}

	/** This thread manages the incoming messages. */
	private class MessageHandler extends HandlerThread {

		private Handler mHandler;

		public MessageHandler() {
			super("MessageHandler_" + groupName);
			start();
		}

		public Message obtainMessage() {
			// TODO Auto-generated method stub
			return mHandler.obtainMessage();
		}

		public void sendMessage(Message msg) {
			// TODO Auto-generated method stub
			mHandler.sendMessage(msg);
		}

		@Override
		protected void onLooperPrepared() {
			super.onLooperPrepared();

			mHandler = new Handler(getLooper()) {

				@Override
				public void handleMessage(Message msg) {

					A3Message message = (A3Message) msg.obj;
					try {
						onMessage(message);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			};
		}
	}

	/** This thread manages session losing and timeout firing. */
	class CallbackThread extends HandlerThread {

		private Handler mHandler;

		public CallbackThread() {
			super("CallbackThread_" + groupName);
			start();
		}

		public Message obtainMessage() {
			// TODO Auto-generated method stub
			return mHandler.obtainMessage();
		}

		public void sendMessage(Message msg) {
			// TODO Auto-generated method stub
			mHandler.sendMessage(msg);
		}

		@Override
		protected void onLooperPrepared() {
			super.onLooperPrepared();

			mHandler = new Handler(getLooper()) {

				@Override
				public void handleMessage(Message msg) {

					switch (msg.arg2) {

					case Constants.SESSION_LOST:

						showOnScreen("Session lost: I reconnect.");
						reconnect();

						break;

					case Constants.TIMER_FIRED: {

						switch (msg.arg1) {
						case FIND_NAME_TIME_FIRED:
							mBus.cancelFindAdvertisedName(groupNameNoSuffix + '.');

							/*
							 * The group name wasn't found, so I must create the
							 * Service. If I create the group, I will probably
							 * be the supervisor: if I can only be a follower, I
							 * don't create the group.
							 */
							if (!discovered) {

								if (followerOnly) {

									node.setWaiting(A3Channel.this);
									return;
								} else
									createGroup();
							}

							try {
								joinSession();
							} catch (Exception e) {
								e.printStackTrace();
							}
							break;

						case CONNECT_TIME_FIRED:
							if (activeRole == null) {
								showOnScreen("Supervisor did no reply, retrying FFT");
								try {
									sendToSupervisor(new A3Message(
											Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST,
											""));
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
							break;
						default:
							break;
						}
					}
						break;
					default:
						break;
					}
				}
			};
		}
	}

	private class InputQueueHandler extends Thread {

		public InputQueueHandler() {
			super();
		}

		public void run() {

			A3Message message;

			while (true) {
				try {
					message = inputQueue.get();
					while (activeRole == null || activeRole.isActive())
						synchronized (inputQueue) {
							inputQueue.wait();
						}
					activeRole.onMessage(message);
					inputQueue.dequeue();
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
			}
		}
	}

	/**
	 * @return The value of an integer fitness function used for split, as
	 *         defined in the group descriptor class.
	 * @throws Exception
	 *             The integer fitness function is not implemented in the group
	 *             descriptor class.
	 */
	protected int getIntegerSplitFitnessFunction() throws Exception {
		// TODO Auto-generated method stub

		return groupDescriptor.getIntegerSplitFitnessFunction();
	}

	/**
	 * @return The value of the boolean fitness function used for split, as
	 *         defined in the group descriptor class.
	 * @throws Exception
	 *             The integer fitness function is not implemented in the group
	 *             descriptor class.
	 */
	protected boolean getBooleanSplitFitnessFunction() throws Exception {
		// TODO Auto-generated method stub
		return groupDescriptor.getBooleanSplitFitnessFunction();
	}

	/**
	 * @return The value of an integer fitness function used for supervisor
	 *         election, as defined in the group descriptor class.
	 * @throws Exception
	 *             The integer fitness function is not implemented in the group
	 *             descriptor class.
	 */
	protected int getSupervisorFitnessFunction() throws Exception {
		// TODO Auto-generated method stub
		if (!followerOnly)
			return groupDescriptor.getSupervisorFitnessFunction();
		throw new Exception("Cannot become supervisor.");
	}

	/**
	 * It starts a supervisor election in the group this channel belongs to.
	 * 
	 *            The name of the group in which to start the supervisor
	 *            election.
	 */
	public void startSupervisorElection() {
		// TODO Auto-generated method stub
		A3Message message = new A3Message(Constants.SUPERVISOR_ELECTION, "");
		sendToSupervisor(message);
	}

	/**
	 * It sends a message to the Service, in order for it to start a random
	 * split operation.
	 * 
	 * @param nodesToTransfer
	 *            The number of nodes to translate to the new group.
	 */
	public void split(int nodesToTransfer) {
		// TODO Auto-generated method stub

		A3Message message = new A3Message(Constants.SPLIT,
				String.valueOf(nodesToTransfer));
		sendToSupervisor(message);
	}

	/**
	 * It broadcasts a message, in order to collect the integer split fitness
	 * functions of the nodes. It is called only if this node is the supervisor.
	 * 
	 * @param nodesToTransfer
	 *            The number of nodes to translate to the new group.
	 */
	public void splitWithIntegerFitnessFunction(int nodesToTransfer)
			throws Exception {
		// TODO Auto-generated method stub

		try {
			// I'm sure I am the supervisor.
			supervisorRole.startSplit(nodesToTransfer);
			A3Message message = new A3Message(
					Constants.INTEGER_SPLIT_FITNESS_FUNCTION, "");
			sendBroadcast(message);
		} catch (Exception e) {
			throw new Exception(e.getLocalizedMessage());
		}
	}

	/**
	 * It broadcasts a message, in order to start a boolean split operation. It
	 * is called only if this node is the supervisor.
	 */
	public void splitWithBooleanFitnessFunction() throws Exception {
		// TODO Auto-generated method stub

		try {
			A3Message message = new A3Message(
					Constants.BOOLEAN_SPLIT_FITNESS_FUNCTION, "");
			sendBroadcast(message);
		} catch (Exception e) {
			throw new Exception(e.getLocalizedMessage());
		}
	}

	/*--- Getters and setters of interest ---*/
	public boolean isConnectedForApplication() {
		return connectedForApplication;
	}

	public void setConnectedForApplication(boolean connectedForApplication) {
		this.connectedForApplication = connectedForApplication;
	}

	public boolean isConnectedForSystem() {
		return connectedForSystem > 0;
	}

	public void setConnectedForSystem(boolean connectedForSystem) {
		if (connectedForSystem)
			this.connectedForSystem++;
		else
			this.connectedForSystem--;
	}

	public String getGroupName() {
		return groupNameNoSuffix.replace(Constants.PREFIX, "");
	}

	public static String removeSuffix(String name) {
		return name.replaceFirst("\\.G[A-Za-z0-9]+", "");
	}

	public Hierarchy getHierarchy() {
		return hierarchy;
	}

	public boolean isSupervisor() {
		return isSupervisor;
	}

	public String getChannelId() {
		return myId;
	}

	public Service getService() {
		return service;
	}

    public class Sender implements Runnable{

        private boolean running = true;

        public void terminate(){
            running = false;
        }

        @Override
        public void run() {
            A3Message message;
            while (running) {
                try {
                    message = queue.get();
                    if (send(message))
                        queue.dequeue();
                    else {
						showOnScreen("Fail to deliver msg");
						if (reconnect)
							reconnect();
					}
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}

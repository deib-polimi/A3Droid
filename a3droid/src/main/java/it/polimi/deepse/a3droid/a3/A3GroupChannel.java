package it.polimi.deepse.a3droid.a3;

import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import it.polimi.deepse.a3droid.a3.exceptions.A3ChannelNotFoundException;
import it.polimi.deepse.a3droid.a3.exceptions.A3Exception;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupCreationException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupDisconnectedException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupDuplicationException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupJoinException;
import it.polimi.deepse.a3droid.a3.exceptions.A3MessageDeliveryException;
import it.polimi.deepse.a3droid.pattern.Observable;
import it.polimi.deepse.a3droid.pattern.Observer;
import it.polimi.deepse.a3droid.pattern.Timer;
import it.polimi.deepse.a3droid.pattern.TimerInterface;

/**
 * This class implements the communication methods for sending and receiving both application
 * and control messages. It is composed of an event and error handlers, as well as group control,
 * view a and hierarchyControl classes which have their specific responsibilities. For each group a node
 * is connect to, a corresponding instance of a class extending A3GroupChannel must exist, i.e.,
 * a class from the bus framework used for group communication.
 * @see A3EventHandler
 * @see A3GroupControl
 * @see A3View
 * @see A3HierarchyControl
 */
public abstract class A3GroupChannel implements A3GroupChannelInterface, Observable, TimerInterface {

    protected static final String TAG = "A3GroupChannel";

    /** The A3 class extending Android Application class with middleware specific behavior **/
    protected A3Application application;

    /** The logic that is executed when this channel is a follower */
    protected A3FollowerRole followerRole;

    /** The logic that is executed when this channel is the supervisor */
    protected A3SupervisorRole supervisorRole;

    /** The current active role, since follower and supervisor roles activation are mutually exclusive **/
    protected A3Role activeRole;

    /** Handler class for A3 layer events **/
    private A3EventHandler eventHandler;

    /** A reference to the node which this channel belongs to **/
    private A3NodeInterface node;

    /** The descriptor instance of the group associated to this channel **/
    private A3GroupDescriptor groupDescriptor;

    public A3GroupChannel(A3Application application,
                          A3Node node,
                          A3GroupDescriptor descriptor){
        this.setGroupName(descriptor.getGroupName());
        this.application = application;
        this.hasFollowerRole = descriptor.getFollowerRoleId() != null;
        this.hasSupervisorRole = descriptor.getSupervisorRoleId() != null;
        this.node = node;
        this.groupDescriptor = descriptor;
    }

    /**
     *
     * @param followerRole
     * @param supervisorRole
     */
    public void connect(A3FollowerRole followerRole, A3SupervisorRole supervisorRole){
        addObservers(application.getObservers());
        initializeRoles(followerRole, supervisorRole);
        initializeHandlers();
        notifyObservers(A3GroupChannel.CONNECT_EVENT);
    }

    /**
     *
     * @param followerRole
     * @param supervisorRole
     */
    private void initializeRoles(A3FollowerRole followerRole, A3SupervisorRole supervisorRole){
        this.followerRole = followerRole;
        this.supervisorRole = supervisorRole;
        if(this.followerRole != null)
            this.followerRole.setChannel(this);
        if(this.supervisorRole != null)
            this.supervisorRole.setChannel(this);
    }

    /**
     *
     */
    public void disconnect(){
        deactivateActiveRole();
        notifyObservers(A3GroupChannel.DISCONNECT_EVENT);
        clearObservers();
    }

    public void joinGroup(){
        notifyObservers(A3GroupChannel.JOIN_CHANNEL_EVENT);
    }

    public void leaveGroup(){
        notifyObservers(A3GroupChannel.USE_LEAVE_CHANNEL_EVENT);
    }

    public void createGroup(){
        notifyObservers(A3GroupChannel.START_SERVICE_EVENT);
    }

    public void destroyGroup(){
        notifyObservers(A3GroupChannel.STOP_SERVICE_EVENT);
    }

    /**
     *
     */
    private void initializeHandlers(){
        eventHandler = new A3EventHandler(application, this);
        hierarchyControl = new A3HierarchyControl(this);
        view = new A3View(this);
        groupControl = new A3GroupControl(node, this);
    }

    /**
     *
     */
    private void quitHandlers(){
        eventHandler.quit();
        hierarchyControl = null;
        view.quit();
        groupControl.quit();
    }

    /**
     * Forward event to handler without argument
     * @param event
     */
    public void handleEvent(A3EventHandler.A3Event event){
        Message msg = eventHandler.obtainMessage();
        msg.what = event.ordinal();
        eventHandler.sendMessage(msg);
    }

    /**
     * Forward event to handler with argument
     * @param event
     * @param arg
     */
    public void handleEvent(A3EventHandler.A3Event event, Object arg){
        Message msg = eventHandler.obtainMessage();
        msg.what = event.ordinal();
        msg.obj = arg;
        eventHandler.sendMessage(msg);
    }

    /**
     *
     * @param ex
     */
    public void handleError(A3Exception ex){
        if(ex instanceof A3GroupDuplicationException){
            reconnect(); //TODO handle duplication
        }else if(ex instanceof A3GroupDisconnectedException){
            //TODO raise this to the application?
        }else if(ex instanceof A3GroupCreationException){
            //TODO raise this to the application?
        }else if(ex instanceof A3GroupJoinException){
            //TODO raise this to the application?
        }else if(ex instanceof A3MessageDeliveryException){
            //TODO raise this to the application?
        }
    }

    /**
     * Check for the condition to become a supervisor or to query for existing one.
     */
    protected void queryRole(){
        if((getView().isViewEmpty() ||
                getView().isAloneInView(channelId)
        ) &&
                hasSupervisorRole)
            becomeSupervisor();
        else
            querySupervisor();
    }

    /**
     * Called when this channels becomes supervisor. It deactivates the follower
     * role (if it is active) and it activates the supervisor role.
     */
    protected void becomeSupervisor() {
        Log.i(TAG, "becomeSupervisor()");
        assert (hasSupervisorRole);
        supervisor = true;
        supervisorId = channelId;
        if(hasFollowerRole)
            followerRole.setActive(false);
        activeRole = supervisorRole;
        if(!supervisorRole.isActive()) {
            supervisorRole.setActive(true);
            new Thread(supervisorRole).start();
        }
        notifyNewSupervisor();
    }

    /**
     * Called when this channels becomes a follower. It deactivates the supervisor
     * role (if it is active) and it activates the follower role.
     */
    protected void becomeFollower(){
        Log.i(TAG, "becomeFollower()");
        assert hasFollowerRole;
        assert supervisorId != null;
        supervisor = false;
        if(hasSupervisorRole)
            supervisorRole.setActive(false);
        activeRole = followerRole;
        if(!followerRole.isActive()) {
            followerRole.setActive(true);
            new Thread(followerRole).start();
        }
    }

    /**
     * Deactivates the current active role
     */
    protected void deactivateActiveRole(){
        if(isSupervisor())
            deactivateSupervisor();
        else if(hasFollowerRole)
            deactivateFollower();
    }

    /**
     * Removes the supervisor role by deactivating it and setting supervisor false
     */
    protected void deactivateSupervisor(){
        assert (hasSupervisorRole);
        supervisor = false;
        supervisorRole.setActive(false);
    }

    /**
     * Clears the information regarding the current supervisor ID
     */
    protected void clearSupervisor(){
        supervisorId = null;
    }

    /**
     * Removes the follower role by deactivating it and setting supervisor false
     */
    protected void deactivateFollower(){
        assert (hasFollowerRole);
        followerRole.setActive(false);
    }

    /** Supervisor election methods **/
    /**
     * Broadcasts the election of a new supervisor
     */
    private void notifyNewSupervisor(){
        A3Message m = new A3Message(A3Constants.CONTROL_NEW_SUPERVISOR, groupDescriptor.getSupervisorFitnessFunction() + "");
        enqueueControl(m);
    }

    /**
     * Notifies @address of the current supervisor
     * @param address the address to send the current supervisor id
     */
    protected void notifyCurrentSupervisor(String address){
        A3Message m = new A3Message(A3Constants.CONTROL_CURRENT_SUPERVISOR, groupDescriptor.getSupervisorFitnessFunction() + "");
        m.addresses = new String[]{address};
        enqueueControl(m);
    }

    /**
     * Broadcasts a query about the current supervisor
     */
    private void querySupervisor(){
        A3Message m = new A3Message(A3Constants.CONTROL_GET_SUPERVISOR, "");
        enqueueControl(m);
        supervisorQueryTimer = new Timer(this, SUPERVISOR_NOT_FOUND_EVENT, SUPERVISOR_NOT_FOUND_EVENT_TIMEOUT);
    }

    /**
     * Aborts the current supervisor query timer if it is active
     */
    protected synchronized void clearSupervisorQueryTimer(){
        if(supervisorQueryTimer != null)
            supervisorQueryTimer.abort();
    }

    /** Timer object for the supervisor query **/
    private Timer supervisorQueryTimer = null;

    /**
     * Sends a stack request to the supervisor of this channel's group
     * @param parentGroupName the group name that should receive the stack request
     */
    protected void requestStack(String parentGroupName){
        assert getSupervisorId() != null;
        enqueueControl(new A3Message(A3Constants.CONTROL_STACK_REQUEST, parentGroupName, new String[]{getSupervisorId()}));
    }

    /**
     * Send a reverse stack request to the supervisor of this channel's group
     * @param parentGroupName the group name that should receive the stack request
     */
    protected void requestReverseStack(String parentGroupName){
        assert getSupervisorId() != null;
        enqueueControl(new A3Message(A3Constants.CONTROL_REVERSE_STACK_REQUEST, parentGroupName, new String[]{getSupervisorId()}));
    }

    /**
     * Sends a merge request to the supervisor of this channel's group
     * @param receiverGroupName the name of the group to receive merged nodes
     */
    protected void requestMerge(String receiverGroupName){
        assert getSupervisorId() != null;
        enqueueControl(new A3Message(A3Constants.CONTROL_MERGE_REQUEST, receiverGroupName, new String[]{getSupervisorId()}));
    }

    /**
     * Replies to a stack request
     * @param result the boolean result indicating the success of the stack operation
     * @param address the address of the node that should receive the stack reply
     */
    protected void replyStack(String parentGroupName, boolean result, String address){
        enqueueControl(new A3Message(A3Constants.CONTROL_STACK_REPLY, groupName + A3Constants.SEPARATOR + result, new String[]{address}));
    }

    /**
     * Replies to a stack request
     * @param result the boolean result indicating the success of the stack operation
     * @param address the address of the node that should receive the stack reply
     */
    protected void replyReverseStack(String parentGroupName, boolean result, String address){
        enqueueControl(new A3Message(A3Constants.CONTROL_REVERSE_STACK_REPLY, groupName + A3Constants.SEPARATOR + result, new String[]{address}));
    }

    /**
     * Replies to a merge request
     * @param result the result of the merge operation
     * @param address the address of the node that should receive the merge reply
     */
    protected void replyMerge(String parentGroupName, boolean result, String address){
        enqueueControl(new A3Message(A3Constants.CONTROL_MERGE_REPLY, parentGroupName + A3Constants.SEPARATOR + result, new String[]{address}));
    }

    /**
     * Sends a broadcast request to add a given group to the hierarchyControl
     * @param parentGroupName The name of the group to be added to the hierarchyControl
     */
    protected void notifyHierarchyAdd(String parentGroupName){
        enqueueControl(new A3Message(A3Constants.CONTROL_ADD_TO_HIERARCHY, parentGroupName));
    }

    /**
     * Sends a broadcast request to remove a given group from the hierarchyControl
     * @param oldGroupName the name of the group to be removed from the hierarchyControl
     */
    protected void notifyHierarchyRemove(String oldGroupName){
        enqueueControl(new A3Message(A3Constants.CONTROL_REMOVE_FROM_HIERARCHY, oldGroupName));
    }

    /**
     *
     * @param nodesToTransfer
     * @throws A3ChannelNotFoundException
     */
    protected void notifySplitRandomly(int nodesToTransfer) throws
            A3ChannelNotFoundException {
        ArrayList<String> selectedNodes = new ArrayList<>();
        int numberOfNodes = getView().getNumberOfNodes();
        String[] splitView = getView().toString()
                .substring(1, getView().toString().length() - 1)
                .split(", ");
        Random randomNumberGenerator = new Random();
        String tempAddress;
        if (nodesToTransfer < numberOfNodes) {
            for (int i = 0; i < nodesToTransfer; i++) {
                do {
                    tempAddress = splitView[randomNumberGenerator
                            .nextInt(numberOfNodes)];
                } while (tempAddress.equals(getSupervisorId())
                        || selectedNodes.contains(tempAddress));
                selectedNodes.add(tempAddress);
            }

            for (String address : selectedNodes)
                enqueueControl(new A3Message(
                        A3Constants.CONTROL_SPLIT, "", new String[]{address}));
        }
    }

    /**
     * Sends a broadcast request for subgroup counter increment. Subgroup counter is used to create
     * new subgroups named after the original group's name with subgroup counter appended
     */
    protected void notifyNewSubgroup() {
        A3Message newGroupMessage = new A3Message(
                A3Constants.CONTROL_INCREASE_SUBGROUPS, "");
        enqueueControl(newGroupMessage);
    }

    /**
     * Sends a merge notification to this group nodes, which will initiate their merge procedure
     * @param receiverGroupName The destination group name, to which the nodes should be merged with
     */
    protected void notifyMerge(String receiverGroupName){
        enqueueControl(new A3Message(A3Constants.CONTROL_MERGE_NOTIFICATION, receiverGroupName));
    }

    /**
     * Sends a split request to the supervisor of this channel's group
     * @param nodesToTransfer The number of nodes to be transferred to the new group
     */
    public void notifySplit(int nodesToTransfer) {
        enqueueControl(new A3Message(A3Constants.CONTROL_SPLIT, String.valueOf(nodesToTransfer)));
    }

    /**
     * Handles events triggered after a certain amount of time past from a message been sent.
     * @param reason It indicates which timeout fired. The taken action will depend on this.
     */
    public void handleTimeEvent(int reason, Object object){
        switch (reason){
            case SUPERVISOR_NOT_FOUND_EVENT:
                handleSupervisorNotFoundEvent();
                break;
            default:
                break;
        }
    }

    private static final int SUPERVISOR_NOT_FOUND_EVENT = 0;
    private static final int SUPERVISOR_NOT_FOUND_EVENT_TIMEOUT = 2000;

    /**
     * Whenever an existing group without supervisor is joint by this node, it becomes the
     * supervisor if it has the role for it.
     */
    private void handleSupervisorNotFoundEvent(){
        if(supervisorId == null){
            if(hasSupervisorRole)
                becomeSupervisor();
        }
    }

    /**
     * Enqueue a control message
     * @param message the control message to be sent
     */
    protected void enqueueControl(A3Message message){
        try {
            addOutboundItem(message, A3GroupChannel.CONTROL_MSG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** A3GroupChannelInterface for application communication **/
    @Override
    public void receiveUnicast(A3Message message) {
        Log.i(TAG, "UNICAST : " + message.object + " TO " + message.getAddresses());
        activeRole.onMessage(message);
    }

    @Override
    public void receiveMulticast(A3Message message) {
        Log.i(TAG, "MULTICAST : " + message.object + " TO " + message.getAddresses());
        activeRole.onMessage(message);
    }

    @Override
    public void receiveBroadcast(A3Message message) {
        Log.i(TAG, "BROADCAST : " + message.object);
        activeRole.onMessage(message);
    }

    /** A3GroupChannelInterface for control **/
    @Override
    public void receiveControl(A3Message message) {
        Log.i(TAG, "CONTROL : " + message.object + " TO " + message.getAddresses());
        Message msg = groupControl.obtainMessage();
        msg.obj = message;
        groupControl.sendMessage(msg);
    }

    /**
     * Thread responsible for handling messages
     **/
    private A3GroupControl groupControl = null;

    /**
     * Set the name part of the "host" channel.  Since we are going to "use" a
     * channel that is implemented remotely and discovered through an AllJoyn
     * FoundAdvertisedName, this must come from a list of advertised names.
     * These names are our channels, and so we expect the GUI to choose from
     * among the list of channels it retrieves from getFoundGroups().
     *
     * Since we are talking about user-level interactions here, we are talking
     * about the final segment of a well-known name representing a channel at
     * this point.
     */
    private synchronized void setGroupName(String name) {
        groupName = name;
    }

    public synchronized String getGroupName() {
        return groupName;
    }

    /**
     * The name of the "host" channel which the user has selected.
     */
    protected String groupName = null;

    public synchronized void setChannelId(String id) {
        Log.i(TAG, "setChannelId(" + id + ")");
        this.channelId = id;
    }

    public synchronized String getChannelId() {
        return channelId;
    }

    /**
     * The channel id uniquely identifies the channel
     * The channel className uniquely identifies the channel
     */
    protected String channelId = null;

    public int getSessionId(){
        return sessionId;
    }

    public void setSessionId(int sessionId){
        this.sessionId = sessionId;
    }

    /**
     * The session identifier that the application
     * provides for remote devices.  Set to -1 if not connected.
     */
    int sessionId = -1;

    protected synchronized void setSupervisorId(String id){
        Log.i(TAG, "setSupervisorId(" + id + ")");
        this.supervisorId = id;
    }

    public synchronized String getSupervisorId() {
        return supervisorId;
    }

    private String supervisorId = null;

    public boolean isSupervisor(){
        return supervisor;
    }

    private boolean supervisor = false;

    public boolean hasSupervisorRole(){
        return hasSupervisorRole;
    }

    public boolean hasFollowerRole(){
        return hasFollowerRole;
    }

    private boolean hasSupervisorRole = false;
    private boolean hasFollowerRole = false;

    /**
     *
     * @return this channel's hierarchyControl instance
     */
    public A3HierarchyControl getHierarchyControl() {
        return hierarchyControl;
    }

    /** Stores the hierarchyControl on top of this group for this node **/
    private A3HierarchyControl hierarchyControl = null;

    /**
     *
     * @return this channel's view instance
     */
    public A3View getView(){
        return view;
    }

    /** The list of the group members and all the methods to manage it. */
    public A3View view = null;

    /**
     * The object we use in notifications to indicate that a channel must be setup.
     */
    public static final String CONNECT_EVENT = "CONNECT_EVENT";

    /**
     * The object we use in notifications to indicate that a channel must be setup.
     */
    public static final String DISCONNECT_EVENT = "DISCONNECT_EVENT";

    /**
     * The object we use in notifications to indicate that user has requested
     * that we join a channel in the "use" tab.
     */
    public static final String JOIN_CHANNEL_EVENT = "JOIN_CHANNEL_EVENT";

    /**
     * The object we use in notifications to indicate that user has requested
     * that we leave a channel in the "use" tab.
     */
    public static final String USE_LEAVE_CHANNEL_EVENT = "USE_LEAVE_CHANNEL_EVENT";

    /**
     * The object we use in notifications to indicate that user has requested
     * that we initialize the host channel parameters in the "use" tab.
     */
    public static final String START_SERVICE_EVENT = "START_SERVICE_EVENT";

    /**
     * The object we use in notifications to indicate that user has requested
     * that we initialize the host channel parameters in the "use" tab.
     */
    public static final String STOP_SERVICE_EVENT = "STOP_SERVICE_EVENT";

    /**
     * The object we use in notifications to indicate that the the user has
     * entered a message and it is queued to be sent to the outside world.
     */
    public static final String OUTBOUND_CHANGED_EVENT = "OUTBOUND_CHANGED_EVENT";

    /** Observable **/
    /**
     * This object is really the model of a model-view-controller architecture.
     * The observer/observed design pattern is used to notify view-controller
     * objects when the model has changed.  The observed object is this object,
     * the model.  Observers correspond to the view-controllers which in this
     * case are the Android Activities (corresponding to the use tab and the
     * hsot tab) and the Android Service that does all of the AllJoyn work.
     * When an observer wants to register for change notifications, it calls
     * here.
     */
    public synchronized void addObserver(Observer obs) {
        Log.i(TAG, "addObserver(" + obs + ")");
        if (mObservers.indexOf(obs) < 0) {
            mObservers.add(obs);
        }
    }

    /**
     *
     * @param observers
     */
    public synchronized void addObservers(List<Observer> observers) {
        Log.i(TAG, "addObservers(" + observers + ")");
        mObservers.addAll(observers);
    }

    /**
     * When an observer wants to unregister to stop receiving change
     * notifications, it calls here.
     */
    public synchronized void deleteObserver(Observer obs) {
        Log.i(TAG, "deleteObserver(" + obs + ")");
        mObservers.remove(obs);
    }

    /**
     * When an observer wants to unregister to stop receiving change
     * notifications, it calls here.
     */
    public synchronized void clearObservers() {
        Log.i(TAG, "clearObservers()");
        mObservers.clear();
    }

    /**
     *
     * @param arg
     */
    protected void notifyObservers(Object arg) {
        Log.i(TAG, "notifyObservers(" + arg + ")");
        for (Observer obs : mObservers) {
            Log.i(TAG, "notify observer = " + obs);
            obs.update(this, arg);
        }
    }

    /**
     * The observers list is the list of all objects that have registered with
     * us as observers in order to get notifications of interesting events.
     */
    private List<Observer> mObservers = new ArrayList<>();

    /**
     * @return an A3MessageItem containing a message and a type of message if
     * mOutbound is not empty
     */
    public synchronized A3MessageItem getOutboundItem() {
        if (mOutbound.isEmpty()) {
            return null;
        } else {
            return mOutbound.remove(0);
        }
    }

    /**
     *
     * @return true if this outbound is empty
     */
    public synchronized boolean isOutboundEmpty(){
        return mOutbound.isEmpty();
    }

    /**
     * Whenever the local user types a message for distribution to the channel
     * it calls newLocalMessage.  We are called to queue up the message and
     * send a notification to all of our observers indicating that the we have
     * something ready to go out.  We expect that the AllJoyn Service will
     * eventually respond by calling back in here to get items off of the queue
     * and send them down the session corresponding to the channel.
     */
    public void addOutboundItem(A3Message message, int type) {
        addOutboundItem(message, type);
        notifyObservers(OUTBOUND_CHANGED_EVENT);
    }

    /**
     * Adds a message to the outbound queue without notifying observers
     * @param message
     * @param type
     */
    public void addOutboundItemSilently(A3Message message, int type) {
        mOutbound.add(new A3MessageItem(message, type));
    }

    /**
     * The outbound list is the list of all messages that have been originated
     * by our local user and are designed for the outside world.
     */
    private List<A3MessageItem> mOutbound = new ArrayList<>();

    public A3Bus.A3GroupState getGroupState(){
        return mGroupState;
    }

    public void setGroupState(A3Bus.A3GroupState state){
        this.mGroupState = state;
        notifyObservers(GROUP_STATE_CHANGED_EVENT);
        synchronized (A3NodeInterface.waiter) {
            A3NodeInterface.waiter.notifyAll();
        }
    }

    private A3Bus.A3GroupState mGroupState = A3Bus.A3GroupState.IDLE;

    /**
     * The object we use in notifications to indicate that the state of the
     * "host" channel or its name has changed.
     */
    public static final String GROUP_STATE_CHANGED_EVENT = "GROUP_STATE_CHANGED_EVENT";

    public static final int BROADCAST_MSG = 0;
    public static final int UNICAST_MSG = 1;
    public static final int MULTICAST_MSG = 2;
    public static final int CONTROL_MSG = 3;
}

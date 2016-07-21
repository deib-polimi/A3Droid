package it.polimi.deepse.a3droid.a3;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import it.polimi.deepse.a3droid.a3.exceptions.A3Exception;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupCreateException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupDisconnectedException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupDuplicationException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupJoinException;
import it.polimi.deepse.a3droid.a3.exceptions.A3MessageDeliveryException;
import it.polimi.deepse.a3droid.a3.exceptions.A3NoGroupDescriptionException;
import it.polimi.deepse.a3droid.pattern.Observable;
import it.polimi.deepse.a3droid.pattern.Observer;
import it.polimi.deepse.a3droid.pattern.Timer;
import it.polimi.deepse.a3droid.pattern.TimerInterface;

/**
 * This is the A3 layer class responsible for group management, A3 application/control communication,
 * handling A3 events and errors, and other methods related to A3 layer only. The framework responsible
 * for the actual communication between nodes must extend this class and implement its logic in its
 * own layer.
 */
public abstract class A3Channel implements A3ChannelInterface, Observable, TimerInterface {

    protected static final String TAG = "a3droid.A3Channel";

    /** Indicate if this node has follower and supervisor roles **/
    private boolean hasSupervisorRole = false;
    private boolean hasFollowerRole = false;

    /** The logic that is executed when this channel is a follower. */
    protected A3FollowerRole followerRole;

    /** The logic that is executed when this channel is the supervisor. */
    protected A3SupervisorRole supervisorRole;

    protected A3Role activeRole;

    protected A3NodeInterface node;

    /** Handler class for A3 layer events **/
    private A3EventHandler eventHandler;

    private A3GroupDescriptor a3GroupDescriptor;

    public A3Channel(A3Application application,
                     A3Node node,
                     A3GroupDescriptor descriptor,
                     String groupName,
                     boolean hasFollowerRole,
                     boolean hasSupervisorRole){
        this.setGroupName(groupName);
        this.application = application;
        this.node = node;
        this.hasFollowerRole = hasFollowerRole;
        this.hasSupervisorRole = hasSupervisorRole;
        this.a3GroupDescriptor = descriptor;
        eventHandler = new A3EventHandler(application, this);
        hierarchy = new A3Hierarchy(this);
        view = new A3View(this);
        controlHandler = new RoleMessageHandler(this);
    }

    protected A3Application application;

    /** A3Channel methods **/

    public void connect(A3FollowerRole followerRole, A3SupervisorRole supervisorRole){
        this.followerRole = followerRole;
        this.supervisorRole = supervisorRole;
        if(this.followerRole != null)
            this.followerRole.setChannel(this);
        if(this.supervisorRole != null)
            this.supervisorRole.setChannel(this);
        addObservers(application.getObservers());
        notifyObservers(A3Channel.CONNECT_EVENT);
    }

    public void disconnect(){
        deactivateActiveRole();
        notifyObservers(A3Channel.DISCONNECT_EVENT);
        clearObservers();
    }

    public void joinGroup(){
        notifyObservers(A3Channel.JOIN_CHANNEL_EVENT);
    }

    public void leaveGroup(){
        notifyObservers(A3Channel.USE_LEAVE_CHANNEL_EVENT);
    }

    public void createGroup(){
        notifyObservers(A3Channel.START_SERVICE_EVENT);
    }

    public void destroyGroup(){
        notifyObservers(A3Channel.STOP_SERVICE_EVENT);
    }

    /** A3Channel methods used internally**/
    public void handleEvent(A3Bus.A3Event event){
        Message msg = eventHandler.obtainMessage();
        msg.what = event.ordinal();
        eventHandler.sendMessage(msg);
    }

    public void handleEvent(A3Bus.A3Event event, Object arg){
        Message msg = eventHandler.obtainMessage();
        msg.what = event.ordinal();
        msg.obj = arg;
        eventHandler.sendMessage(msg);
    }

    public void handleError(A3Exception ex){
        if(ex instanceof A3GroupDuplicationException){
            reconnect(); //TODO handle duplication
        }else if(ex instanceof A3GroupDisconnectedException){
            //TODO raise this to the application?
        }else if(ex instanceof A3GroupCreateException){
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
    private void becomeSupervisor() {
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
    private void becomeFollower(){
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
        A3Message m = new A3Message(A3Constants.CONTROL_NEW_SUPERVISOR, a3GroupDescriptor.getSupervisorFitnessFunction() + "");
        enqueueControl(m);
    }

    /**
     * Notifies @address of the current supervisor
     * @param address the address to send the current supervisor id
     */
    private void notifyCurrentSupervisor(String address){
        A3Message m = new A3Message(A3Constants.CONTROL_CURRENT_SUPERVISOR, a3GroupDescriptor.getSupervisorFitnessFunction() + "");
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
    private synchronized void clearSupervisorQueryTimer(){
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
    protected void replyStackRequest(String parentGroupName, boolean result, String address){
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
     * Sends a broadcast request to add a given group to the hierarchy
     * @param parentGroupName The name of the group to be added to the hierarchy
     */
    protected void notifyHierarchyAdd(String parentGroupName){
        enqueueControl(new A3Message(A3Constants.CONTROL_ADD_TO_HIERARCHY, parentGroupName));
    }

    /**
     * Sends a broadcast request to remove a given group from the hierarchy
     * @param oldGroupName the name of the group to be removed from the hierarchy
     */
    protected void notifyHierarchyRemove(String oldGroupName){
        enqueueControl(new A3Message(A3Constants.CONTROL_REMOVE_FROM_HIERARCHY, oldGroupName));
    }

    /**
     * Sends a broadcast request for subgroup counter increment. Subgroup counter is used to create
     * new subgroups named after the original group's name with subgroup counter appended
     */
    private void notifyNewSubgroup() {
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
    public void handleTimeEvent(int reason){
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
     * Enqueue a control message
     * @param message the control message to be sent
     */
    private void enqueueControl(A3Message message){
        try {
            addOutboundItem(message, A3Channel.CONTROL_MSG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** A3ChannelInterface for application communication **/
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

    /** A3ChannelInterface for control **/
    @Override
    public void receiveControl(A3Message message) {
        Log.i(TAG, "CONTROL : " + message.object + " TO " + message.getAddresses());
        Message msg = controlHandler.obtainMessage();
        msg.obj = message;
        controlHandler.sendMessage(msg);
    }

    /**
     * Thread responsible for handling messages
     **/
    private static class RoleMessageHandler extends HandlerThread {

        private final WeakReference<A3Channel> mChannel;
        private Handler mHandler;

        public RoleMessageHandler(A3Channel channel) {
            super("ControlRoleMessageHandler_" + channel.getGroupName());
            mChannel = new WeakReference<>(channel);
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

            final A3Channel channel = mChannel.get();

            mHandler = new Handler(getLooper()) {
                /**
                 * There are system messages whose management doesn't depend on the application:
                 * they are filtered and managed here.
                 * @param msg The incoming message.
                 */
                @Override
                public void handleMessage(Message msg) {

                    A3Message message = (A3Message) msg.obj;
                    switch (message.reason){
                        /** Supervisor election **/
                        case A3Constants.CONTROL_GET_SUPERVISOR:
                            channel.handleGetSupervisorQuery(message);
                            break;
                        case A3Constants.CONTROL_NEW_SUPERVISOR:
                            channel.handleNewSupervisorNotification(message);
                            break;
                        case A3Constants.CONTROL_CURRENT_SUPERVISOR:
                            channel.handleCurrentSupervisorReply(message);
                            break;
                        case A3Constants.CONTROL_NO_SUPERVISOR:
                            channel.handleNoSupervisorNotification(message);
                            break;
                        /** TCO operations **/
                        case A3Constants.CONTROL_STACK_REQUEST:
                            channel.handleStackRequest(message);
                            break;
                        case A3Constants.CONTROL_STACK_REPLY:
                            channel.handleStackReply(message);
                            break;
                        case A3Constants.CONTROL_REVERSE_STACK_REQUEST:
                            channel.handleReverseStackRequest(message);
                            break;
                        case A3Constants.CONTROL_REVERSE_STACK_REPLY:
                            channel.handleReverseStackReply(message);
                            break;
                        case A3Constants.CONTROL_GET_HIERARCHY:
                        case A3Constants.CONTROL_HIERARCHY_REPLY:
                        case A3Constants.CONTROL_ADD_TO_HIERARCHY:
                        case A3Constants.CONTROL_REMOVE_FROM_HIERARCHY:
                        case A3Constants.CONTROL_INCREASE_SUBGROUPS:
                            channel.getHierarchy().onMessage(message);
                            break;
                        case A3Constants.CONTROL_MERGE_REQUEST:
                            channel.handleMergeRequest(message);
                            break;
                        case A3Constants.CONTROL_MERGE_NOTIFICATION:
                            channel.handleMergeNotification(message);
                            break;
                        case A3Constants.CONTROL_MERGE_REPLY:
                            channel.handleMergeReply(message);
                            break;
                        case A3Constants.CONTROL_SPLIT:
                            channel.handleSplitNotification(message);
                            break;
                        default:
                            break;
                    }
                }
            };
        }
    }
    private RoleMessageHandler controlHandler = null;

    /**
     *
     * @param message
     */
    private void handleNewSupervisorNotification(A3Message message) {
        setSupervisorId(message.senderAddress);
        if(!channelId.equals(supervisorId)){
            float supervisorFF = Float.parseFloat(message.object);
            if(hasSupervisorRole){
                if (a3GroupDescriptor.getSupervisorFitnessFunction() > supervisorFF)
                    becomeSupervisor();
                else{
                    if(hasFollowerRole)
                        becomeFollower();
                    else if(supervisor)
                        deactivateSupervisor();
                }
            }else if(hasFollowerRole)
                becomeFollower();
        }
        handleEvent(A3Bus.A3Event.SUPERVISOR_ELECTED);
    }

    /**
     *
     * @param message
     */
    private void handleCurrentSupervisorReply(A3Message message){
        clearSupervisorQueryTimer();
        handleNewSupervisorNotification(message);
    }

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
     *
     * @param message
     */
    private void handleNoSupervisorNotification(A3Message message){
        if(hasSupervisorRole)
            becomeSupervisor();
    }

    /**
     *
     * @param message
     */
    private void handleGetSupervisorQuery(A3Message message){
        if(supervisor)
            notifyCurrentSupervisor(message.senderAddress);
        //else if(!message.senderAddress.equals(getChannelId()) && supervisorId == null)
          //  notifyNoSupervisor(message.senderAddress);
    }

    /**
     *
     * @param message
     */
    private void handleStackRequest(A3Message message){
        assert isSupervisor();
        try{
            handleEvent(A3Bus.A3Event.STACK_STARTED);
            boolean ok = node.actualStack(message.object, getGroupName());
            replyStack(message.object, ok, message.senderAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            handleEvent(A3Bus.A3Event.STACK_FINISHED);
        }
    }

    /**
     *
     * @param message
     */
    private void handleStackReply(A3Message message){
        String [] reply = message.object.split(A3Constants.SEPARATOR);
        node.stackReply(reply[0], getGroupName(), Boolean.valueOf(reply[1]), true);
    }

    /**
     *
     * @param message
     */
    private void handleReverseStackRequest(A3Message message){
        assert isSupervisor();
        boolean ok = node.actualReverseStack(message.object, getGroupName());
        replyStackRequest(message.object, ok, message.senderAddress);
    }

    /**
     *
     * @param message
     */
    private void handleReverseStackReply(A3Message message){
        String [] reply = message.object.split(A3Constants.SEPARATOR);
        node.reverseStackReply(reply[0], getGroupName(), Boolean.valueOf(reply[1]), true);
    }

    /**
     *
     * @param message
     */
    private void handleMergeRequest(A3Message message){
        assert isSupervisor();
        String receiverGroupName = message.object;
        replyMerge(receiverGroupName, true, message.senderAddress);
        notifyMerge(receiverGroupName);
    }

    /**
     *
     * @param message
     */
    private void handleMergeNotification(A3Message message) {
        try {
            handleEvent(A3Bus.A3Event.MERGE_STARTED);
            String receiverGroupName = message.object;
            node.actualMerge(receiverGroupName, getGroupName());
        } catch (A3NoGroupDescriptionException e) {
            e.printStackTrace();
        }finally {
            handleEvent(A3Bus.A3Event.MERGE_FINISHED);
        }
    }

    /**
     *
     * @param message
     */
    private void handleMergeReply(A3Message message){
        String [] reply = message.object.split(A3Constants.SEPARATOR);
        node.mergeReply(reply[0], getGroupName(), Boolean.valueOf(reply[1]), true);
    }

    /**
     *
     * @param message
     */
    private void handleSplitNotification(A3Message message){
        handleEvent(A3Bus.A3Event.SPLIT_STARTED);
        if(isSupervisor()) {
            // Random notifySplit operation.
            notifyNewSubgroup();
            int nodesToTransfer = Integer.valueOf(message.object);
            ArrayList<String> selectedNodes = new ArrayList<>();
            int numberOfNodes = view.getNumberOfNodes();
            String[] splitView = view.getView()
                    .substring(1, view.getView().length() - 1)
                    .split(", ");
            Random randomNumberGenerator = new Random();
            String tempAddress;

            /*
             * I can't move the supervisor in another group, so the
             * supervisor never sends its integer fitness function
             * value. I can't move more nodes than I have.
             */
            if (nodesToTransfer < numberOfNodes) {

                for (int i = 0; i < nodesToTransfer; i++) {

                    do {
                        tempAddress = splitView[randomNumberGenerator
                                .nextInt(numberOfNodes)];
                    } while (tempAddress.equals(supervisorId)
                            || selectedNodes.contains(tempAddress));

                    selectedNodes.add(tempAddress);
                }

                for (String address : selectedNodes)
                    enqueueControl(new A3Message(
                            A3Constants.CONTROL_SPLIT, "", new String[]{address}));
            }
        }else
            /*
			 * I will joinGroup to a group split from this group, which has the
			 * same roles of this group, so I don't need to check for right
			 * roles here.
			 */
            try {
                node.actualMerge(
                        getGroupName() + "_" + hierarchy.getSubgroupsCounter(),
                        getGroupName());
            } catch (A3NoGroupDescriptionException e) {
                e.printStackTrace();
            }
        handleEvent(A3Bus.A3Event.SPLIT_FINISHED);
    }

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

    /**
     * Get the name part of the "use" channel.
     */
    public synchronized String getGroupName() {
        return groupName;
    }

    /**
     * The name of the "host" channel which the user has selected.
     */
    protected String groupName = null;

    /**
     * Get the channel id
     */
    public synchronized void setChannelId(String id) {
        Log.i(TAG, "setChannelId(" + id + ")");
        this.channelId = id;
        //notifyObservers(SERVICE_STATE_CHANGED_EVENT);
    }

    /**
     * Get the name part of the "use" channel.
     */
    public synchronized String getChannelId() {
        return channelId;
    }

    /**
     * The channel id uniquely identifies the channel
     * The channel className uniquely identifies the channel
     */
    protected String channelId = null;

    public int getSessionId(){
        return mSessionId;
    }

    public void setSessionId(int sessionId){
        mSessionId = sessionId;
    }

    /**
     * The session identifier that the application
     * provides for remote devices.  Set to -1 if not connected.
     */
    int mSessionId = -1;

    private void setSupervisorId(String id){
        Log.i(TAG, "setSupervisorId(" + id + ")");
        this.supervisorId = id;
    }

    /**
     *
     * @return The current supervisor id
     */
    public String getSupervisorId() {
        return supervisorId;
    }

    /** The current supervisor id **/
    private String supervisorId = null;

    /**
     *
     * @return true if this node is the supervisor of this channel's group
     */
    public boolean isSupervisor(){
        return supervisor;
    }

    /** Indicates if this channel is a supervisor **/
    private boolean supervisor = false;

    /**
     *
     * @return this channel's hierarchy instance
     */
    public A3Hierarchy getHierarchy() {
        return hierarchy;
    }

    /** Stores the hierarchy on top of this group for this node **/
    private A3Hierarchy hierarchy = null;

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
     *
     * @return
     */
    public synchronized A3MessageItem getOutboundItem() {
        if (mOutbound.isEmpty()) {
            return null;
        } else {
            return mOutbound.remove(0);
        }
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
        addOutboundItem(message, type, true);
    }

    /**
     *
     * @param message
     * @param type
     * @param notify
     */
    public void addOutboundItem(A3Message message, int type, boolean notify) {
        mOutbound.add(new A3MessageItem(message, type));
        if(notify)
            notifyObservers(OUTBOUND_CHANGED_EVENT);
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

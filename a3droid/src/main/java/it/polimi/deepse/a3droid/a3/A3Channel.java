package it.polimi.deepse.a3droid.a3;

import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.Constants;
import it.polimi.deepse.a3droid.GroupDescriptor;
import it.polimi.deepse.a3droid.a3.exceptions.A3Exception;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupCreateException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupDisconnectedException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupDuplicationException;
import it.polimi.deepse.a3droid.a3.exceptions.A3GroupJoinException;
import it.polimi.deepse.a3droid.a3.exceptions.A3MessageDeliveryException;
import it.polimi.deepse.a3droid.a3.exceptions.A3NoGroupDescriptionException;
import it.polimi.deepse.a3droid.pattern.Observable;
import it.polimi.deepse.a3droid.pattern.Observer;

/**
 * This is the A3 layer class responsible for group management, A3 application/control communication,
 * handling A3 events and errors, and other methods related to A3 layer only. The framework responsible
 * for the actual communication between nodes must extend this class and implement its logic in its
 * own layer.
 */
public abstract class A3Channel implements A3ChannelInterface, Observable {

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

    private GroupDescriptor groupDescriptor;

    public A3Channel(A3Application application,
                     A3Node node,
                     GroupDescriptor descriptor,
                     String groupName,
                     boolean hasFollowerRole,
                     boolean hasSupervisorRole){
        this.setGroupName(groupName);
        this.application = application;
        this.node = node;
        this.groupDescriptor = descriptor;
        this.hasFollowerRole = hasFollowerRole;
        this.hasSupervisorRole = hasSupervisorRole;
        eventHandler = new A3EventHandler(application, this);
        hierarchy = new Hierarchy(this);
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
        msg.obj = event;
        eventHandler.sendMessage(msg);
    }

    public void handleError(A3Exception ex){
        if(ex instanceof A3GroupDuplicationException){
            reconnect();
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
        if((application.isGroupEmpty(groupName) ||
                application.isGroupMemberAlone(groupName, channelId) ||
                !application.isGroupMemberIn(groupName, supervisorId)
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
        assert (hasFollowerRole);
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
     * Removes the supervisor role by deactivating it and seting supervisor false.
     */
    protected void deactivateSupervisor(){
        assert (hasSupervisorRole);
        supervisor = false;
        supervisorRole.setActive(false);
    }

    /**
     * Removes the follower role by deactivating it and seting supervisor false.
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
     * @param address
     */
    private void notifyCurrentSupervisor(String address){
        A3Message m = new A3Message(A3Constants.CONTROL_CURRENT_SUPERVISOR, groupDescriptor.getSupervisorFitnessFunction() + "");
        m.addresses = new String[]{address};
        enqueueControl(m);
    }

    /**
     * Notifies @address of no existing supervisor
     * @param address
     */
    private void notifyNoSupervisor(String address){
        A3Message m = new A3Message(A3Constants.CONTROL_NO_SUPERVISOR, channelId);
        m.addresses = new String[]{address};
        enqueueControl(m);
    }

    /**
     * Broadcasts a query about the current supervisor
     */
    private void querySupervisor(){
        A3Message m = new A3Message(A3Constants.CONTROL_GET_SUPERVISOR, "");
        enqueueControl(m);
    }

    /** Stack methods **/

    /**
     * TODO: describe
     * @param parentGroupName
     */
    protected void requestStack(String parentGroupName){
        enqueueControl(new A3Message(A3Constants.CONTROL_STACK_REQUEST, parentGroupName, new String[]{getSupervisorId()}));
    }

    /**
     * TODO: describe
     * @param parentGroupName
     * @param result
     * @param address
     */
    protected void replyStack(String parentGroupName, boolean result, String address){
        enqueueControl(new A3Message(A3Constants.CONTROL_STACK_REPLY, groupName + Constants.A3_SEPARATOR + result, new String[]{address}));
    }

    /**
     * TODO: describe
     * @param parentGroupName The name of the parent group.
     * @return true, if "parentGroupName" became parent of "childGroupName", false otherwise.
     */
    protected void notifyStack(String parentGroupName){
        enqueueControl(new A3Message(A3Constants.CONTROL_ADD_TO_HIERARCHY, parentGroupName));
    }

    /** Merge methods **/

    /**
     * TODO describe
     * @param oldGroupName
     */
    protected void requestHierarchyRemove(String oldGroupName){
        enqueueControl(new A3Message(A3Constants.CONTROL_REMOVE_FROM_HIERARCHY, oldGroupName));
    }

    /**
     * TODO describe
     * @param receiverGroupName
     */
    protected void notifyMerge(String receiverGroupName){
        enqueueControl(new A3Message(A3Constants.CONTROL_MERGE, receiverGroupName));
    }

    /**
     * Enqueue a control message
     * @param message
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
        Log.i(TAG, "UNICAST : " + (String) message.object + " TO " + message.addresses);
        activeRole.onMessage(message);
    }

    @Override
    public void receiveMulticast(A3Message message) {
        Log.i(TAG, "MULTICAST : " + (String) message.object + " TO " + message.addresses);
        activeRole.onMessage(message);
    }

    @Override
    public void receiveBroadcast(A3Message message) {
        Log.i(TAG, "BROADCAST : " + (String) message.object);
        activeRole.onMessage(message);
    }

    /** A3ChannelInterface for control **/
    @Override
    public void receiveControl(A3Message message) {
        Log.i(TAG, "CONTROL : " + (String) message.object);
        switch (message.reason){
            /** Supervisor election **/
            case A3Constants.CONTROL_NEW_SUPERVISOR:
            case A3Constants.CONTROL_CURRENT_SUPERVISOR:
                handleCurrentSupervisorNotification(message);
                break;
            case A3Constants.CONTROL_NO_SUPERVISOR:
                handleNoSupervisorNotification(message);
                break;
            case A3Constants.CONTROL_GET_SUPERVISOR:
                handleGetSupervisorQuery(message);
                break;
            /** Stack operation **/
            case A3Constants.CONTROL_STACK_REQUEST:
                handleStackRequest(message);
                break;
            case A3Constants.CONTROL_STACK_REPLY:
                handleStackReply(message);
                break;
            case A3Constants.CONTROL_GET_HIERARCHY:
            case A3Constants.CONTROL_HIERARCHY_REPLY:
            case A3Constants.CONTROL_ADD_TO_HIERARCHY:
            case A3Constants.CONTROL_REMOVE_FROM_HIERARCHY:
                hierarchy.onMessage(message);
                break;
            case A3Constants.CONTROL_MERGE:
                handleMergeRequest(message);
                break;
            default:
                break;
        }
    }

    /**Supervisor election handlers**/
    private void handleCurrentSupervisorNotification(A3Message message){
        supervisorId = message.senderAddress;
        if(!channelId.equals(supervisorId)){
            float supervisorFF = Float.parseFloat(message.object);
            if(hasSupervisorRole){
                if (groupDescriptor.getSupervisorFitnessFunction() > supervisorFF)
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
    }

    private void handleNoSupervisorNotification(A3Message message){
        if(hasSupervisorRole)
            becomeSupervisor();
    }

    private void handleGetSupervisorQuery(A3Message message){
        if(supervisor)
            notifyCurrentSupervisor(message.senderAddress);
        //else if(!message.senderAddress.equals(getChannelId()) && supervisorId == null)
          //  notifyNoSupervisor(message.senderAddress);
    }

    /** Stack operation handlers **/
    private void handleStackRequest(A3Message message){
        try{
            boolean ok = node.actualStack(message.object, getGroupName());
            replyStack(message.object, ok, message.senderAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleStackReply(A3Message message){
        String [] reply = message.object.split(Constants.A3_SEPARATOR);
        node.stackReply(reply[0], getGroupName(), Boolean.valueOf(reply[1]), true);
    }

    /** Merge operation handlers **/
    private void handleMergeRequest(A3Message message){
        try {
            node.actualMerge(message.object, getGroupName());
        } catch (A3NoGroupDescriptionException e) {
            e.printStackTrace();
        }
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

    public String getSupervisorId() {
        return supervisorId;
    }

    /** The current supervisor id **/
    private String supervisorId = null;

    public boolean isSupervisor(){
        return supervisor;
    }

    /** Indicates if this channel is a supervisor **/
    private boolean supervisor = false;

    public Hierarchy getHierarchy() {
        return hierarchy;
    }

    /** Stores the hierarchy on top of this group for this node **/
    private Hierarchy hierarchy = null;

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
    private List<Observer> mObservers = new ArrayList<Observer>();

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

    public void addOutboundItem(A3Message message, int type, boolean notify) {
        mOutbound.add(new A3MessageItem(message, type));
        if(notify)
            notifyObservers(OUTBOUND_CHANGED_EVENT);
    }

    /**
     * The outbound list is the list of all messages that have been originated
     * by our local user and are designed for the outside world.
     */
    private List<A3MessageItem> mOutbound = new ArrayList<A3MessageItem>();

    public static final int BROADCAST_MSG = 0;
    public static final int UNICAST_MSG = 1;
    public static final int MULTICAST_MSG = 2;
    public static final int CONTROL_MSG = 3;
}

package it.polimi.deepse.a3droid.a3;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import it.polimi.deepse.a3droid.a3.exceptions.A3ChannelNotFoundException;
import it.polimi.deepse.a3droid.a3.exceptions.A3NoGroupDescriptionException;
import it.polimi.deepse.a3droid.pattern.*;
import it.polimi.deepse.a3droid.utility.RandomWait;

public class A3GroupControlHandler extends HandlerThread implements TimerInterface{

    protected String TAG;

    private final A3TopologyControl topologyControl;
    private final A3GroupChannel channel;
    private Handler mHandler;

    public A3GroupControlHandler(A3TopologyControl topologyControl, A3GroupChannel channel) {
        super("A3GroupControl_" + channel.getGroupName());
        TAG = "A3GroupControlHandler#" + channel.getGroupName();
        this.topologyControl = topologyControl;
        this.channel = channel;
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
                        handleGetSupervisorQuery(message);
                        break;
                    case A3Constants.CONTROL_NEW_SUPERVISOR:
                        handleNewSupervisorNotification(message);
                        break;
                    case A3Constants.CONTROL_CURRENT_SUPERVISOR:
                        handleCurrentSupervisorReply(message);
                        break;
                    case A3Constants.CONTROL_NO_SUPERVISOR:
                        handleNoSupervisorNotification(message);
                        break;
                    /** TCO operations **/
                    case A3Constants.CONTROL_STACK_REQUEST:
                        handleStackRequest(message);
                        break;
                    case A3Constants.CONTROL_STACK_REPLY:
                        handleStackReply(message);
                        break;
                    case A3Constants.CONTROL_REVERSE_STACK_REQUEST:
                        handleReverseStackRequest(message);
                        break;
                    case A3Constants.CONTROL_REVERSE_STACK_REPLY:
                        handleReverseStackReply(message);
                        break;
                    case A3Constants.CONTROL_GET_HIERARCHY:
                    case A3Constants.CONTROL_HIERARCHY_REPLY:
                    case A3Constants.CONTROL_ADD_TO_HIERARCHY:
                    case A3Constants.CONTROL_REMOVE_FROM_HIERARCHY:
                    case A3Constants.CONTROL_INCREASE_SUBGROUPS:
                        channel.getHierarchyView().onMessage(message);
                        break;
                    case A3Constants.CONTROL_MERGE_REQUEST:
                        handleMergeRequest(message);
                        break;
                    case A3Constants.CONTROL_MERGE_NOTIFICATION:
                        handleMergeNotification(message);
                        break;
                    case A3Constants.CONTROL_MERGE_REPLY:
                        handleMergeReply(message);
                        break;
                    case A3Constants.CONTROL_SPLIT:
                        handleSplitNotification(message);
                        break;
                    default:
                        break;
                }
            }
        };
    }

    /**
     *
     * @param message
     */
    private void handleNewSupervisorNotification(A3Message message) {
        Log.i(TAG, "handleNewSupervisorNotification(" + message + ")");
        channel.setSupervisorId(message.senderAddress);
        A3GroupDescriptor groupDescriptor = null;
        try {
            groupDescriptor = channel.getGroupDescriptor();
            if(!channel.getChannelId().equals(message.senderAddress)){
                float supervisorFF = Float.parseFloat(message.object);
                if(channel.hasSupervisorRole()){
                    if (groupDescriptor.getSupervisorFitnessFunction() > supervisorFF) {
                        channel.becomeSupervisor();
                    }
                    else{
                        if(channel.hasFollowerRole()) {
                            channel.becomeFollower();
                        }
                        else if(channel.isSupervisor()) {
                            channel.deactivateSupervisor();
                        }
                    }
                }else if(channel.hasFollowerRole()) {
                    channel.becomeFollower();
                }
            }
        } catch (A3NoGroupDescriptionException e) {
            e.printStackTrace();
        } finally {
            channel.handleEvent(A3EventHandler.A3Event.SUPERVISOR_ELECTED);
        }
    }

    /**
     *
     * @param message
     */
    private void handleCurrentSupervisorReply(A3Message message){
        Log.i(TAG, "handleCurrentSupervisorReply(" + message + ")");
        channel.clearSupervisorQueryTimer();
        handleNewSupervisorNotification(message);
    }

    /**
     *
     * @param message
     */
    private void handleNoSupervisorNotification(A3Message message){
        if(channel.hasSupervisorRole())
            channel.becomeSupervisor();
    }

    /**
     *
     * @param message
     */
    private void handleGetSupervisorQuery(A3Message message){
        if(channel.isSupervisor())
            channel.notifyCurrentSupervisor(message.senderAddress);
        //else if(!message.senderAddress.equals(getChannelId()) && supervisorId == null)
        //  notifyNoSupervisor(message.senderAddress);
    }

    /**
     *
     * @param message
     */
    private void handleStackRequest(A3Message message){
        assert channel.isSupervisor();//TODO things may change.. we should verify and perform some behavior if it is false
        String parentGroupName = message.object;
        boolean ok = false;
        try{
            channel.handleEvent(A3EventHandler.A3Event.STACK_STARTED);
            ok = topologyControl.performSupervisorStack(parentGroupName, channel.getGroupName());
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            channel.replyStack(parentGroupName, ok, message.senderAddress);
            channel.handleEvent(A3EventHandler.A3Event.STACK_FINISHED);
        }
    }

    /**
     *
     * @param message
     */
    private void handleStackReply(A3Message message){
        String [] reply = message.object.split(A3Constants.SEPARATOR);
        try {
            topologyControl.stackReply(reply[0], channel.getGroupName(), Boolean.valueOf(reply[1]), true);
        } catch (A3ChannelNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param message
     */
    private void handleReverseStackRequest(A3Message message){
        assert channel.isSupervisor();
        boolean ok = false;
        try {
            ok = topologyControl.performSupervisorReverseStack(message.object, channel.getGroupName());
        } catch (A3ChannelNotFoundException e) {
            e.printStackTrace();
        } finally {
            channel.replyReverseStack(message.object, ok, message.senderAddress);
        }
    }

    /**
     *
     * @param message
     */
    private void handleReverseStackReply(A3Message message){
        String [] reply = message.object.split(A3Constants.SEPARATOR);
        try {
            topologyControl.reverseStackReply(reply[0], channel.getGroupName(), Boolean.valueOf(reply[1]), true);
        } catch (A3ChannelNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Supervisor handler of a merge request
     * @param message
     */
    private void handleMergeRequest(A3Message message){
        assert channel.isSupervisor();
        String receiverGroupName = message.object;
        boolean ok = false;
        try {
            ok = topologyControl.performSupervisorMerge(receiverGroupName, channel.getGroupName());
        } catch (A3NoGroupDescriptionException e) {
            e.printStackTrace();
        } catch (A3ChannelNotFoundException e) {
            e.printStackTrace();
        } finally {
            channel.replyMerge(receiverGroupName, ok, message.senderAddress);
        }
    }

    /**
     *
     * @param message
     */
    private void handleMergeNotification(A3Message message) {
        channel.handleEvent(A3EventHandler.A3Event.MERGE_STARTED);
        String receiverGroupName = message.object;
        new Timer(this, WAIT_AND_MERGE_EVENT, randomWait.next(WAIT_AND_MERGE_FIXED_TIME, WAIT_AND_MERGE_RANDOM_TIME), receiverGroupName);
    }

    /**
     *
     * @param receiverGroupName
     */
    private void handleDelayedMerge(String receiverGroupName){
        try {
            topologyControl.performMerge(receiverGroupName, channel.getGroupName());
        } catch (A3NoGroupDescriptionException e) {
            e.printStackTrace();
        } catch (A3ChannelNotFoundException e) {
            e.printStackTrace();
        } finally {
            channel.handleEvent(A3EventHandler.A3Event.MERGE_FINISHED);
        }
    }

    private static final int WAIT_AND_MERGE_FIXED_TIME = 0;
    private static final int WAIT_AND_MERGE_RANDOM_TIME = 1000;

    /**
     *
     * @param message
     */
    private void handleMergeReply(A3Message message){
        String [] reply = message.object.split(A3Constants.SEPARATOR);
        try {
            topologyControl.mergeReply(reply[0], channel.getGroupName(), Boolean.valueOf(reply[1]), true);
        } catch (A3ChannelNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param message
     */
    private void handleSplitNotification(A3Message message){
        channel.handleEvent(A3EventHandler.A3Event.SPLIT_STARTED);
        new Timer(this, WAIT_AND_SPLIT_EVENT, randomWait.next(WAIT_AND_SPLIT_FIXED_TIME, WAIT_AND_SPLIT_RANDOM_TIME));
    }

    private static final int WAIT_AND_SPLIT_FIXED_TIME = 0;
    private static final int WAIT_AND_SPLIT_RANDOM_TIME = 1000;

    /**
     *
     */
    private void handleDelayedSplit(){
        try {
            topologyControl.performMerge(
                channel.getGroupName() + "_" +
                channel.getHierarchyView().getSubgroupsCounter(),
                channel.getGroupName()
            );
        } catch (A3NoGroupDescriptionException e) {
            e.printStackTrace();
        } catch (A3ChannelNotFoundException e) {
            e.printStackTrace();
        } finally {
            channel.handleEvent(A3EventHandler.A3Event.SPLIT_FINISHED);
        }
    }

    public void handleTimeEvent(int reason, Object object) {
        switch (reason) {
            case WAIT_AND_MERGE_EVENT:
                handleDelayedMerge((String) object);
                break;
            case WAIT_AND_SPLIT_EVENT:
                handleDelayedSplit();
                break;
            default:
                break;
        }
    }

    private static final int WAIT_AND_MERGE_EVENT = 0;
    private static final int WAIT_AND_SPLIT_EVENT = 1;

    private RandomWait randomWait = new RandomWait();
}

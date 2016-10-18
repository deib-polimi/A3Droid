package it.polimi.deepse.a3droid.bus.alljoyn;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.ProxyBusObject;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;

import it.polimi.deepse.a3droid.a3.A3GroupChannel;
import it.polimi.deepse.a3droid.a3.A3Message;
import it.polimi.deepse.a3droid.a3.A3GroupDescriptor;
import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3Bus;
import it.polimi.deepse.a3droid.a3.A3MessageItem;
import it.polimi.deepse.a3droid.pattern.Observable;

/**
 * TODO: Describe
 */
public class AlljoynBus extends A3Bus {

    private static final String TAG = "a3droid.bus.AlljoynBus";

    /*
     * Load the native alljoyn_java library.  The actual AllJoyn code is
     * written in C++ and the alljoyn_java library provides the language
     * bindings from Java to C++ and vice versa.
     */
    static {
        Log.i(TAG, "System.loadLibrary(\"alljoyn_java\")");
        System.loadLibrary("alljoyn_java");
    }

    /**
     * Our onCreate() method is called by the Android appliation framework
     * when the service is first created.  We spin up a background thread
     * to handle any long-lived requests (pretty much all AllJoyn calls that
     * involve communication with remote processes) that need to be done and
     * insinuate ourselves into the list of observers of the model so we can
     * get event notifications.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        application.addObserver(this);
        startBusThread();
        createDiscoveryChannel();
    }

    /**
     * The discovery channel is used for the single purpose of listening to the bus and
     * keep the list of available groups updated.
     */
    private void createDiscoveryChannel(){
        /*
         * We have an AllJoyn handler thread running at this time, so take
         * advantage of the fact to get connected to the bus and start finding
         * remote channel instances in the background while the rest of the app
         * is starting up.
         */
        A3DiscoveryDescriptor discoveryDescriptor = new A3DiscoveryDescriptor();
        mDiscoveryChannel = new AlljoynGroupChannel((A3Application) getApplication(), null,
                discoveryDescriptor);
        mBackgroundHandler.connect(mDiscoveryChannel);
        mBackgroundHandler.startDiscovery(mDiscoveryChannel);
    }

    /**
     * Our onDestroy() is called by the Android appliation framework when it
     * decides that our Service is no longer needed.  We tell our background
     * thread to exit andremove ourselves from the list of observers of the
     * model.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
        mBackgroundHandler.cancelDiscovery(mDiscoveryChannel);
        mBackgroundHandler.beforeDisconnectionWait(mDiscoveryChannel);
        stopBusThread();
        application.deleteObserver(this);
    }

    /**
     * This is the event handler for the Observable/Observed design pattern.
     * Whenever an interesting event happens in our appliation, the Model (the
     * source of the event) notifies registered observers, resulting in this
     * method being called since we registered as an Observer in onCreate().
     * <p/>
     * This method will be called in the context of the Model, which is, in
     * turn the context of an event source.  This will either be the single
     * Android application framework thread if the source is one of the
     * Activities of the application or the Service.  It could also be in the
     * context of the Service background thread.  Since the Android Application
     * framework is a fundamentally single threaded thing, we avoid multithread
     * issues and deadlocks by immediately getting this event into a separate
     * execution in the context of the Service message pump.
     * <p/>
     * We do this by taking the event from the calling component and queueing
     * it onto a "handler" in our Service and returning to the caller.  When
     * the calling componenet finishes what ever caused the event notification,
     * we expect the Android application framework to notice our pending
     * message and run our handler in the context of the single application
     * thread.
     * <p/>
     * In reality, both events are executed in the context of the single
     * Android thread.
     */
    public synchronized void update(Observable o, Object arg) {
        Log.i(TAG, "update(" + arg + ")");
        String qualifier = (String) arg;

        if (qualifier.equals(A3Application.APPLICATION_QUIT_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_APPLICATION_QUIT_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3GroupChannel.CONNECT_EVENT))
            this.connect((AlljoynGroupChannel) o);

        if (qualifier.equals(A3GroupChannel.DISCONNECT_EVENT)) {
            this.disconnect((AlljoynGroupChannel) o);
        }

        if (qualifier.equals(A3GroupChannel.JOIN_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_JOIN_CHANNEL_EVENT);
            message.obj = o;
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3GroupChannel.USE_LEAVE_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_LEAVE_CHANNEL_EVENT);
            message.obj = o;
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3GroupChannel.START_SERVICE_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_START_SERVICE_EVENT);
            message.obj = o;
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3GroupChannel.STOP_SERVICE_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_STOP_SERVICE_EVENT);
            message.obj = o;
            mHandler.sendMessage(message);
        }

        //In both cases, try to send a message if channel state is JOINT.
        if (qualifier.equals(A3GroupChannel.OUTBOUND_CHANGED_EVENT) ||
                qualifier.equals(AlljoynGroupChannel.CHANNEL_STATE_CHANGED_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_OUTBOUND_CHANGED_EVENT);
            message.obj = o;
            mHandler.sendMessage(message);
        }
    }

    @Override
    public void connect(A3GroupChannel channel) {
        Message message = mHandler.obtainMessage(HANDLE_CONNECT_EVENT);
        message.obj = channel;
        mHandler.sendMessage(message);
    }

    @Override
    public void disconnect(A3GroupChannel channel) {
        Message message = mHandler.obtainMessage(HANDLE_DISCONNECT_EVENT);
        message.obj = channel;
        mHandler.sendMessage(message);
    }

    /**
     * This is the Android Service message handler.  It runs in the context of the
     * main Android Service thread, which is also shared with Activities since
     * Android is a fundamentally single-threaded system.
     * <p/>
     * The important thing for us is to note that this thread cannot be blocked for
     * a significant amount of time or we risk the dreaded "force close" message.
     * We can run relatively short-lived operations here, but we need to run our
     * distributed system calls in a background thread.
     * <p/>
     * This handler serves translates from UI-related events into AllJoyn events
     * and decides whether functions can be handled in the context of the
     * Android main thread or if they must be dispatched to a background thread
     * which can take as much time as needed to accomplish a task.
     */
    public Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AlljoynGroupChannel channel = null;
            if (msg.obj != null)
                channel = (AlljoynGroupChannel) msg.obj;
            switch (msg.what) {
                case HANDLE_APPLICATION_QUIT_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): APPLICATION_QUIT_EVENT");
                    mBackgroundHandler.exit();
                    stopSelf();
                }
                break;
                case HANDLE_CONNECT_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_CONNECT_EVENT");
                    mBackgroundHandler.connect(channel);
                }
                break;
                case HANDLE_DISCONNECT_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_CONNECT_EVENT");
                    mBackgroundHandler.beforeDisconnectionWait(channel);
                }
                break;
                case HANDLE_JOIN_CHANNEL_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): JOIN_CHANNEL_EVENT");
                    switch (channel.getChannelState()) {
                        case IDLE:
                            mBackgroundHandler.addChannel(channel);
                        case REGISTERED:
                            mBackgroundHandler.joinSession(channel);
                        default:
                            break;
                    }
                }
                break;
                case HANDLE_LEAVE_CHANNEL_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): USE_LEAVE_CHANNEL_EVENT");
                    switch (channel.getChannelState()) {
                        case JOINT:
                            mBackgroundHandler.leaveSession(channel);
                        case REGISTERED:
                            mBackgroundHandler.removeChannel(channel);
                    }
                }
                break;
                case HANDLE_START_SERVICE_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): START_SERVICE_EVENT");
                    if(channel != null) {
                        switch (channel.getServiceState()) {
                            case IDLE:
                                mBackgroundHandler.addService(channel);
                            case REGISTERED:
                                mBackgroundHandler.requestName(channel);
                            case NAMED:
                                mBackgroundHandler.bindSession(channel);
                            case BOUND:
                                mBackgroundHandler.advertise(channel);
                            default:
                                break;
                        }
                    }
                }
                break;
                case HANDLE_STOP_SERVICE_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): STOP_SERVICE_EVENT");
                    switch (channel.getServiceState()) {
                        case ADVERTISED:
                            mBackgroundHandler.cancelAdvertise(channel);
                        case BOUND:
                            mBackgroundHandler.unbindSession(channel);
                        case NAMED:
                            mBackgroundHandler.releaseName(channel);
                        default:
                            break;
                    }
                }
                break;
                case HANDLE_OUTBOUND_CHANGED_EVENT: {
                    Log.i(TAG, "mHandler.handleMessage(): OUTBOUND_CHANGED_EVENT");
                    mBackgroundHandler.sendMessages(channel);
                }
                break;
                default:
                    break;
            }
        }
    };

    /**
     * Value for the HANDLE_APPLICATION_QUIT_EVENT case observer notification handler.
     */
    private static final int HANDLE_APPLICATION_QUIT_EVENT = 0;
    /**
     * Value for the HANDLE_CONNECT_EVENT case observer notification handler.
     */
    private static final int HANDLE_CONNECT_EVENT = 1;
    /**
     * Value for the HANDLE_CONNECT_EVENT case observer notification handler.
     */
    private static final int HANDLE_DISCONNECT_EVENT = 2;
    /**
     * Value for the HANDLE_JOIN_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_JOIN_CHANNEL_EVENT = 3;

    /**
     * Value for the HANDLE_LEAVE_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_LEAVE_CHANNEL_EVENT = 4;

    /**
     * Value for the HANDLE_HOST_INIT_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_HOST_INIT_CHANNEL_EVENT = 5;

    /**
     * Value for the HANDLE_START_SERVICE_EVENT case observer notification handler.
     */
    private static final int HANDLE_START_SERVICE_EVENT = 6;

    /**
     * Value for the HANDLE_STOP_SERVICE_EVENT case observer notification handler.
     */
    private static final int HANDLE_STOP_SERVICE_EVENT = 7;

    /**
     * Value for the HANDLE_OUTBOUND_CHANGED_EVENT case observer notification handler.
     */
    private static final int HANDLE_OUTBOUND_CHANGED_EVENT = 8;


    /**
     * This is the AllJoyn background thread handler class.  AllJoyn is a
     * distributed system and must therefore make calls to other devices over
     * networks.  These calls may take arbitrary amounts of time.  The Android
     * application framework is fundamentally single-threaded and so the main
     * Service thread that is executing in our component is the same thread as
     * the ones which appear to be executing the user interface code in the
     * other Activities.  We cannot block this thread while waiting for a
     * network to respond, so we need to run our calls in the context of a
     * background thread.  This is the class that provides that background
     * thread implementation.
     * <p/>
     * When we need to do some possibly long-lived task, we just pass a message
     * to an object implementing this class telling it what needs to be done.
     * There are two main parts to this class:  an external API and the actual
     * handler.  In order to make life easier for callers, we provide API
     * methods to deal with the actual message passing, and then when the
     * handler thread is executing the desired method, it calls out to an
     * implementation in the enclosing class.  For example, in order to perform
     * a joinGroup() operation in the background, the enclosing class calls
     * BackgroundHandler.joinGroup(); and the result is that the enclosing class
     * method doConnect() will be called in the context of the background
     * thread.
     */
    protected final class BackgroundHandler extends Handler {
        public BackgroundHandler(Looper looper) {
            super(looper);
        }

        /**
         * Exit the background handler thread.  This will be the last message
         * executed by an instance of the handler.
         */
        public void exit() {
            Log.i(TAG, "mBackgroundHandler.exit()");
            Message msg = mBackgroundHandler.obtainMessage(EXIT);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Connect the application to the Alljoyn bus attachment.  We expect
         * this method to be called in the context of the main Service thread.
         * All this method does is to dispatch a corresponding method in the
         * context of the service worker thread.
         */
        public void connect(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.connect()");
            Message msg = mBackgroundHandler.obtainMessage(CONNECT);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * TODO: describe
         */
        public void addChannel(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.addChannel()");
            Message msg = mBackgroundHandler.obtainMessage(ADD_CHANNEL);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * TODO: describe
         */
        public void removeChannel(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.removeChannel()");
            Message msg = mBackgroundHandler.obtainMessage(DEL_CHANNEL);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * TODO: describe
         */
        public void addService(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.addService()");
            Message msg = mBackgroundHandler.obtainMessage(ADD_SERVICE);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Gives time to messages to be sent before disconnecting whenever the channel state is
         * still JOINT
         */
        private void beforeDisconnectionWait(AlljoynGroupChannel channel){
            while(channel.getChannelState() == AlljoynChannelState.JOINT &&
                    !channel.isOutboundEmpty()) {
                try {
                    synchronized (this) {
                        this.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            disconnect(channel);
        }

        /**
         * Disonnect the application from the Alljoyn bus attachment.  We
         * expect this method to be called in the context of the main Service
         * thread.  All this method does is to dispatch a corresponding method
         * in the context of the service worker thread.
         */
        public void disconnect(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.disconnect()");
            Message msg = mBackgroundHandler.obtainMessage(DISCONNECT);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Start discovering remote instances of the application.  We expect
         * this method to be called in the context of the main Service thread.
         * All this method does is to dispatch a corresponding method in the
         * context of the service worker thread.
         */
        public void startDiscovery(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.startDiscovery()");
            Message msg = mBackgroundHandler.obtainMessage(START_DISCOVERY);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Stop discovering remote instances of the application.  We expect
         * this method to be called in the context of the main Service thread.
         * All this method does is to dispatch a corresponding method in the
         * context of the service worker thread.
         */
        public void cancelDiscovery(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.stopDiscovery()");
            Message msg = mBackgroundHandler.obtainMessage(CANCEL_DISCOVERY);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void requestName(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.requestName()");
            Message msg = mBackgroundHandler.obtainMessage(REQUEST_NAME);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void releaseName(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.releaseName()");
            Message msg = mBackgroundHandler.obtainMessage(RELEASE_NAME);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void bindSession(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.bindSession()");
            Message msg = mBackgroundHandler.obtainMessage(BIND_SESSION);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void unbindSession(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.unbindSession()");
            Message msg = mBackgroundHandler.obtainMessage(UNBIND_SESSION);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void advertise(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.advertise()");
            Message msg = mBackgroundHandler.obtainMessage(ADVERTISE);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void cancelAdvertise(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.cancelAdvertise()");
            Message msg = mBackgroundHandler.obtainMessage(CANCEL_ADVERTISE);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void joinSession(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.joinSession()");
            Message msg = mBackgroundHandler.obtainMessage(JOIN_SESSION);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void leaveSession(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.leaveSession()");
            Message msg = mBackgroundHandler.obtainMessage(LEAVE_SESSION);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void sendMessages(AlljoynGroupChannel channel) {
            Log.i(TAG, "mBackgroundHandler.sendMessages()");
            Message msg = mBackgroundHandler.obtainMessage(SEND_MESSAGES);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * The message handler for the worker thread that handles background
         * tasks for the AllJoyn bus.
         */
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CONNECT:
                    doConnect((AlljoynGroupChannel) msg.obj);
                    break;
                case ADD_CHANNEL:
                    doAddChannel((AlljoynGroupChannel) msg.obj);
                    break;
                case DEL_CHANNEL:
                    doRemoveChannel((AlljoynGroupChannel) msg.obj);
                    break;
                case ADD_SERVICE:
                    doAddService((AlljoynGroupChannel) msg.obj);
                    break;
                case DISCONNECT:
                    doDisconnect((AlljoynGroupChannel) msg.obj);
                    break;
                case START_DISCOVERY:
                    doStartDiscovery((AlljoynGroupChannel) msg.obj);
                    break;
                case CANCEL_DISCOVERY:
                    doStopDiscovery((AlljoynGroupChannel) msg.obj);
                    break;
                case REQUEST_NAME:
                    doRequestName((AlljoynGroupChannel) msg.obj);
                    break;
                case RELEASE_NAME:
                    doReleaseName((AlljoynGroupChannel) msg.obj);
                    break;
                case BIND_SESSION:
                    doBindSession((AlljoynGroupChannel) msg.obj);
                    break;
                case UNBIND_SESSION:
                    doUnbindSession((AlljoynGroupChannel) msg.obj);
                    break;
                case ADVERTISE:
                    doAdvertise((AlljoynGroupChannel) msg.obj);
                    break;
                case CANCEL_ADVERTISE:
                    doCancelAdvertise((AlljoynGroupChannel) msg.obj);
                    break;
                case JOIN_SESSION:
                    doJoinSession((AlljoynGroupChannel) msg.obj);
                    break;
                case LEAVE_SESSION:
                    doLeaveSession((AlljoynGroupChannel) msg.obj);
                    break;
                case SEND_MESSAGES:
                    doSendMessages((AlljoynGroupChannel) msg.obj);
                    break;
                case EXIT:
                    getLooper().quit();
                    break;
                default:
                    break;
            }
        }
    }

    private static final int EXIT = 1;
    private static final int CONNECT = 2;
    private static final int DISCONNECT = 3;
    private static final int START_DISCOVERY = 4;
    private static final int CANCEL_DISCOVERY = 5;
    private static final int REQUEST_NAME = 6;
    private static final int RELEASE_NAME = 7;
    private static final int BIND_SESSION = 8;
    private static final int UNBIND_SESSION = 9;
    private static final int ADVERTISE = 10;
    private static final int CANCEL_ADVERTISE = 11;
    private static final int JOIN_SESSION = 12;
    private static final int LEAVE_SESSION = 13;
    private static final int SEND_MESSAGES = 14;
    private static final int ADD_CHANNEL = 15;
    private static final int ADD_SERVICE = 16;
    private static final int DEL_CHANNEL = 17;

    /**
     * The instance of the AllJoyn background thread handler.  It is created
     * when Android decides the Service is needed and is called from the
     * onCreate() method.  When Android decides our Service is no longer
     * needed, it will call onDestroy(), which spins down the thread.
     */
    private BackgroundHandler mBackgroundHandler = null;

    /**
     * Since basically our whole reason for being is to spin up a thread to
     * handle long-lived remote operations, we provide this method to do so.
     */
    private void startBusThread() {
        HandlerThread busThread = new HandlerThread("AlljoynBusBackgroundHandler");
        busThread.start();
        mBackgroundHandler = new BackgroundHandler(busThread.getLooper());
    }

    /**
     * When Android decides that our Service is no longer needed, we need to
     * tear down the thread that is servicing our long-lived remote operations.
     * This method does so.
     */
    private void stopBusThread() {
        mBackgroundHandler.exit();
    }

    /**
     * The well-known name prefix which all bus attachments hosting a channel
     * will use.  The SERVICE_PATH and the channel name are composed to give
     * the well-known name a hosting bus attachment will request and
     * advertise.
     */
    public static final String SERVICE_PATH = "it.polimi.deepse.a3droid.bus.alljoyn";

    /**
     * The well-known session port used as the contact port for the chat service.
     */
    private static final short CONTACT_PORT = 27;

    /**
     * The object path used to identify the service "location" in the bus
     * attachment.
     */
    private static final String OBJECT_PATH = "/A3GroupService";

    private static AlljoynGroupChannel mDiscoveryChannel;

    /**
     * An instance of an AllJoyn bus listener that knows what to do with
     * foundAdvertisedName and lostAdvertisedName notifications.  Although
     * we often use the anonymous class idiom when talking to AllJoyn, the
     * bus listener works slightly differently and it is better to use an
     * explicitly declared class in this case.
     */
    private AlljoynBusListener mBusListener = new AlljoynBusListener(this);

    /**
     * Implementation of the functionality related to connecting our app
     * to the AllJoyn bus.  We expect that this method will only be called in
     * the context of the AllJoyn bus handler thread; and while we are in the
     * DISCONNECTED state.
     */
    private void doConnect(AlljoynGroupChannel channel) {
        Log.i(TAG, "doConnect()");
        BusAttachment mBus = channel.getBus();

        org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
        assert (channel.getBusState() == BusState.DISCONNECTED);
        mBus.useOSLogging(true);
        mBus.setDebugLevel("ALLJOYN_JAVA", 7);
        mBus.registerBusListener(mBusListener);

        Status status = mBus.connect();
        if (status == Status.OK) {
            channel.setBusState(BusState.CONNECTED);
        }else {
            application.busError(A3Application.Module.GENERAL, "Unable to joinGroup to the bus: (" + status + ")");
            channel.handleError(status, AlljoynErrorHandler.CHANNEL);
        }
    }

    private void doAddService(AlljoynGroupChannel channel) {

        //int stateRelation = channel.getBusState().compareTo(BusState.DISCONNECTED);
        //assert(stateRelation > 0);
        /*
         * To make a service available to other AllJoyn peers, first
         * register a BusObject with the BusAttachment at a specific
         * object path.  Our service is implemented by the AlljoynService
         * BusObject found at the "/a3GroupService" object path.
         */
        Status status = channel.getBus().registerBusObject(channel.getService(), OBJECT_PATH);
        if (Status.OK == status) {
            channel.setServiceState(AlljoynService.AlljoynServiceState.REGISTERED);
        } else {
            application.busError(A3Application.Module.HOST, "Unable to register the service bus object: (" + status + ")");
            channel.handleError(status, AlljoynErrorHandler.SERVICE);
        }
    }

    private void doAddChannel(AlljoynGroupChannel channel) {
        int stateRelation = channel.getBusState().compareTo(BusState.DISCONNECTED);
        assert (stateRelation > 0);
        Status status = channel.getBus().registerSignalHandlers(channel);
        if (status == Status.OK) {
            channel.setChannelState(AlljoynChannelState.REGISTERED);
        } else {
            application.busError(A3Application.Module.USE, "Unable to register channel signal handlers: (" + status + ")");
            channel.handleError(status, AlljoynErrorHandler.CHANNEL);
        }
    }

    private void doRemoveChannel(AlljoynGroupChannel channel) {
        int stateRelation = channel.getBusState().compareTo(BusState.DISCONNECTED);
        assert (stateRelation > 0);
        channel.getBus().unregisterSignalHandlers(channel);
        channel.setChannelState(AlljoynChannelState.IDLE);
    }

    /**
     * Implementation of the functionality related to disconnecting our app
     * from the AllJoyn bus.  We expect that this method will only be called
     * in the context of the AllJoyn bus handler thread.  We expect that this
     * method will only be called in the context of the AllJoyn bus handler
     * thread; and while we are in the CONNECTED state.
     */
    private boolean doDisconnect(AlljoynGroupChannel channel) {
        Log.i(TAG, "doDisonnect()");
        assert (channel.getBusState() == BusState.CONNECTED);
        channel.getBus().unregisterBusListener(mBusListener);
        channel.getBus().disconnect();
        channel.setBusState(BusState.DISCONNECTED);
        return true;
    }

    /**
     * Implementation of the functionality related to discovering remote apps
     * which are hosting chat channels.  We expect that this method will only
     * be called in the context of the AllJoyn bus handler thread; and while
     * we are in the CONNECTED state.  Since this is a core bit of functionalty
     * for the "use" side of the app, we always do this at startup.
     */
    private void doStartDiscovery(AlljoynGroupChannel channel) {
        Log.i(TAG, "doStartDiscovery()");
        assert (channel.getBusState() == BusState.CONNECTED);
        Status status = channel.getBus().findAdvertisedName(SERVICE_PATH);
        if (status == Status.OK) {
            channel.setBusState(BusState.DISCOVERING);
        } else {
            application.busError(A3Application.Module.USE, "Unable to start finding advertised names: (" + status + ")");
            channel.handleError(status, AlljoynErrorHandler.CHANNEL);
        }
    }

    /**
     * Implementation of the functionality related to stopping discovery of
     * remote apps which are hosting chat channels.
     */
    private void doStopDiscovery(AlljoynGroupChannel channel) {
        Log.i(TAG, "doStopDiscovery()");
        assert (channel.getBusState() == BusState.CONNECTED);
        channel.getBus().cancelFindAdvertisedName(SERVICE_PATH);
        channel.setBusState(BusState.CONNECTED);
    }

    /**
     * Implementation of the functionality related to requesting a well-known
     * name from an AllJoyn bus attachment.
     */
    private void doRequestName(AlljoynGroupChannel channel) {
        Log.i(TAG, "doRequestName()");

        /*
         * In order to request a name, the bus attachment must at least be
         * connected.
         */
        int stateRelation = channel.getBusState().compareTo(BusState.DISCONNECTED);
        assert (stateRelation >= 0);

        String wellKnownName = SERVICE_PATH + "." + channel.getGroupName();
        //TODO: check the needed FLAGS
        Status status = channel.getBus().requestName(wellKnownName, BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE);
        if (status == Status.OK) {
            channel.setServiceState(AlljoynService.AlljoynServiceState.NAMED);
        } else {
            application.busError(A3Application.Module.USE, "Unable to acquire well-known name: (" + status + ")");
            channel.handleError(status, AlljoynErrorHandler.SERVICE);
        }
    }

    /**
     * Implementation of the functionality related to releasing a well-known
     * name from an AllJoyn bus attachment.
     */
    private void doReleaseName(AlljoynGroupChannel channel) {
        Log.i(TAG, "doReleaseName()");

        int stateRelation = channel.getServiceState().compareTo(AlljoynService.AlljoynServiceState.NAMED);
        if (stateRelation >= 0) {
            /*
             * In order to release a name, the bus attachment must at least be
             * connected.
             */
            stateRelation = channel.getBusState().compareTo(BusState.DISCONNECTED);
            assert (stateRelation >= 0);

            /*
             * We need to progress monotonically down the hosted channel states
             * for sanity.
             */
            assert (channel.getServiceState() == AlljoynService.AlljoynServiceState.NAMED);

            /*
             * We depend on the user interface and model to work together to not
             * change the name out from under us while we are running.
             */
            //TODO: Replace random
            String wellKnownName = SERVICE_PATH + "." + channel.getGroupName();

            /*
             * There's not a lot we can do if the bus attachment refuses to release
             * the name.  It is not a fatal error, though, if it doesn't.  This is
             * because bus attachments can have multiple names.
             */
            channel.getBus().releaseName(wellKnownName);
            channel.setServiceState(AlljoynService.AlljoynServiceState.REGISTERED);
        }
    }

    /**
     * Implementation of the functionality related to binding a session port
     * to an AllJoyn bus attachment.
     */
    private void doBindSession(final AlljoynGroupChannel channel) {
        Log.i(TAG, "doBindSession()");

        Mutable.ShortValue contactPort = new Mutable.ShortValue(CONTACT_PORT);
        SessionOpts sessionOpts = new SessionOpts(SessionOpts.TRAFFIC_MESSAGES, true, SessionOpts.PROXIMITY_ANY, SessionOpts.TRANSPORT_ANY);

        Status status = channel.getBus().bindSessionPort(contactPort, sessionOpts, new SessionPortListener() {
            /**
             * This method is called when a client tries to join the session
             * we have bound.  It asks us if we want to accept the client into
             * our session.
             *
             * In the class documentation for the SessionPortListener note that
             * it is a requirement for this method to be multithread safe.
             * Since we never access any shared state, this requirement is met.
             */
            public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
                Log.i(TAG, "SessionPortListener.acceptSessionJoiner(" + sessionPort + ", " + joiner + ", " + sessionOpts.toString() + ")");

                /*
                 * Accept anyone who can get our contact port correct.
                 */
                return sessionPort == CONTACT_PORT;
            }

            /**
             * If we return true in acceptSessionJoiner, we admit a new client
             * into our session.  The session does not really exist until a
             * client joins, at which time the session is created and a session
             * ID is assigned.  This method communicates to us that this event
             * has happened, and provides the new session ID for us to use.
             *
             * In the class documentation for the SessionPortListener note that
             * it is a requirement for this method to be multithread safe.
             * Since we never access any shared state, this requirement is met.
             *
             * See comments in joinSession for why the hosted chat interface is
             * created here.
             */
            public void sessionJoined(short sessionPort, int sessionId, String joiner) {
                Log.i(TAG, "SessionPortListener.sessionJoined(" + sessionPort + ", " + sessionId + ", " + joiner + ")");
                //channel.setSessionId(sessionId);

                ProxyBusObject mProxyObj;
                mProxyObj = channel.getBus().getProxyBusObject(SERVICE_PATH + "." + channel.getGroupName(), OBJECT_PATH,
                        sessionId, new Class<?>[]{AlljoynServiceInterface.class});
                channel.setServiceInterface(mProxyObj.getInterface(AlljoynServiceInterface.class), false);

                SignalEmitter emitter = new SignalEmitter(channel.getService(), sessionId, SignalEmitter.GlobalBroadcast.Off);
                channel.getService().setServiceSignalEmitterInterface(emitter.getInterface(AlljoynServiceInterface.class));
            }
        });

        if (status == Status.OK) {
            channel.setServiceState(AlljoynService.AlljoynServiceState.BOUND);
        } else {
            application.busError(A3Application.Module.HOST, "Unable to bind session contact port: (" + status + ")");
            channel.handleError(status, AlljoynErrorHandler.SERVICE);
        }
    }

    /**
     * Implementation of the functionality related to un-binding a session port
     * from an AllJoyn bus attachment.
     */
    private void doUnbindSession(AlljoynGroupChannel channel) {
        Log.i(TAG, "doUnbindSession()");
        int stateRelation = channel.getServiceState().compareTo(AlljoynService.AlljoynServiceState.BOUND);
        if (stateRelation >= 0) {
            /*
             * There's not a lot we can do if the bus attachment refuses to unbind
             * our port.
             */
            channel.getBus().unbindSessionPort(CONTACT_PORT);
            channel.setServiceInterface(null, true);
            channel.setServiceState(AlljoynService.AlljoynServiceState.NAMED);
        }
    }

    /**
     * Implementation of the functionality related to advertising a service on
     * an AllJoyn bus attachment.
     */
    private void doAdvertise(AlljoynGroupChannel channel) {
        Log.i(TAG, "doAdvertise()");

        /*
         * We depend on the user interface and model to work together to not
         * change the name out from under us while we are running.
         */
        String wellKnownName = SERVICE_PATH + "." + channel.getGroupName();
        Status status = channel.getBus().advertiseName(wellKnownName, SessionOpts.TRANSPORT_ANY);

        if (status == Status.OK) {
            channel.handleEvent(AlljoynEventHandler.AlljoynEvent.SESSION_CREATED, null);
        } else {
            application.busError(A3Application.Module.HOST, "Unable to advertise well-known name: (" + status + ")");
            channel.handleError(status, AlljoynErrorHandler.SERVICE);
        }
    }

    /**
     * Implementation of the functionality related to canceling an advertisement
     * on an AllJoyn bus attachment.
     */
    private void doCancelAdvertise(AlljoynGroupChannel channel) {
        Log.i(TAG, "doCancelAdvertise()");

        int stateRelation = channel.getServiceState().compareTo(AlljoynService.AlljoynServiceState.ADVERTISED);
        if (stateRelation >= 0) {
            /*
             * We depend on the user interface and model to work together to not
             * change the name out from under us while we are running.
             */
            //TODO: Add RANDOM
            String wellKnownName = SERVICE_PATH + "." + channel.getGroupName();
            Status status = channel.getBus().cancelAdvertiseName(wellKnownName, SessionOpts.TRANSPORT_ANY);

            if (status != Status.OK) {
                application.busError(A3Application.Module.HOST, "Unable to cancel advertisement of well-known name: (" + status + ")");
                return;
            }

            /*
             * There's not a lot we can do if the bus attachment refuses to cancel
             * our advertisement, so we don't bother to even get the status.
             */
            channel.handleEvent(AlljoynEventHandler.AlljoynEvent.SESSION_DESTROYED, null);
        }
    }

    /**
     * Implementation of the functionality related to joining an existing
     * local or remote session.
     */
    private void doJoinSession(final AlljoynGroupChannel channel) {
        Log.i(TAG, "doJoinSession()");

        /*
         * We depend on the user interface and model to work together to provide
         * a reasonable name.
         */
        String wellKnownName = SERVICE_PATH + "." + channel.getGroupName();

        /*
         * The random and unique channel identifier is provided by the bus
         */
        final String channelId = channel.getBus().getUniqueName();
        channel.setChannelId(channelId);


        if (channel.getSessionId() == -1) {

            /*e
             * Since we can act as the host of a channel, we know what the other
             * side is expecting to see.
             */
            short contactPort = CONTACT_PORT;
            SessionOpts sessionOpts = new SessionOpts(SessionOpts.TRAFFIC_MESSAGES, true, SessionOpts.PROXIMITY_ANY, SessionOpts.TRANSPORT_ANY);
            Mutable.IntegerValue sessionId = new Mutable.IntegerValue();


            Status status = channel.getBus().joinSession(wellKnownName, contactPort, sessionId, sessionOpts, new AlljoynSessionListener(application, channel));

            if (status == Status.OK) {
                channel.setSessionId(sessionId.value);
                Log.i(TAG, "doJoinSession(): use sessionId is " + sessionId.value);
                //TODO: Used by a follower to send signals to the bus
                /** The Service proxy to communicate with. */
                ProxyBusObject mProxyObj;
                mProxyObj = channel.getBus().getProxyBusObject(SERVICE_PATH + "." + channel.getGroupName(), OBJECT_PATH,
                        channel.getSessionId(), new Class<?>[]{AlljoynServiceInterface.class});
                channel.setServiceInterface(mProxyObj.getInterface(AlljoynServiceInterface.class), true);

                //TODO: Original chat way of getting the service interface. But as a follower, channel.getService() won't be registered as object
                //SignalEmitter emitter = new SignalEmitter(channel.getService(), mUseSessionId, SignalEmitter.GlobalBroadcast.Off);
                //channel.setServiceSignalEmitterInterface(emitter.getInterface(AlljoynServiceInterface.class), true);

                postJoinWait(POS_CONNECTION_WAIT_TIME);
                channel.handleEvent(AlljoynEventHandler.AlljoynEvent.SESSION_JOINED, null);
            } else {
                application.busError(A3Application.Module.USE, "Unable to join chat session: (" + status + ")");
                channel.handleError(status, AlljoynErrorHandler.CHANNEL);
            }
        }
    }

    /** Additional time to avoid alljoyn error **/
    private static int POS_CONNECTION_WAIT_TIME = 1000;

    /**
     * Gives time to the Alljoyn bus to be properly set before communication starts, avoiding an
     * error noticed experimentally.
     */
    private void postJoinWait(int time){
        try {
            synchronized (this) {
                this.wait(time);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Implementation of the functionality related to joining an existing
     * remote session.
     */
    private void doLeaveSession(AlljoynGroupChannel channel) {
        Log.i(TAG, "doLeaveSession()");
        //TODO: Is this the right place for this verification? Why it is here?
        if (channel.getChannelState() == AlljoynChannelState.JOINT) {
            channel.getBus().leaveSession(channel.getSessionId());
            channel.setSessionId(-1);
            channel.handleEvent(AlljoynEventHandler.AlljoynEvent.SESSION_LEFT, null);
        }
    }

    /**
     * Implementation of the functionality related to sending messages out over
     * an existing remote session.  Note that we always send all of the
     * messages on the outbound queue, so there may be instances where this
     * method is called and we find nothing to send depending on the races.
     */
    private void doSendMessages(AlljoynGroupChannel channel) {
        Log.i(TAG, "doSendMessages()");

        A3MessageItem messageItem;
        while (channel.getChannelState().equals(AlljoynChannelState.JOINT)
                && (messageItem = channel.getOutboundItem()) != null) {
            A3Message message = messageItem.getMessage();
            Log.i(TAG, "doSendMessages(): sending message \"" + message + "\"");
            /*
             * If we are joined to a remote session, we send the message over
             * the mChatInterface.  If we are implicityly joined to a session
             * we are hosting, we send the message over the mHostChatInterface.
             * The mHostChatInterface may or may not exist since it is created
             * when the sessionJoined() callback is fired in the
             * SessionPortListener, so we have to check for it.
             */
            try {
                switch (messageItem.getType()) {
                    case A3GroupChannel.BROADCAST_MSG:
                        channel.sendBroadcast(message);
                        break;
                    case A3GroupChannel.UNICAST_MSG:
                        channel.sendUnicast(message);
                        break;
                    case A3GroupChannel.MULTICAST_MSG:
                        channel.sendMulticast(message);
                        break;
                    case A3GroupChannel.CONTROL_MSG:
                        channel.sendControl(message);
                        break;
                    default:
                        break;
                }
                if(channel.isOutboundEmpty())
                    notifyOuterClass();
            } catch (BusException ex) {
                notifyOuterClass();
                application.busError(A3Application.Module.USE, "Bus exception while sending message: (" + ex + ")");
                channel.addOutboundItemSilently(message, messageItem.getType());
                channel.handleError(ex, AlljoynErrorHandler.BUS);
                break;
            }
        }
    }

    private void notifyOuterClass(){
        synchronized (AlljoynBus.this) {
            AlljoynBus.this.notify();
        }
    }

    /**
     * Enumeration of the states of the AllJoyn bus attachment.  This
     * lets us make a note to ourselves regarding where we are in the process
     * of preparing and tearing down the fundamental connection to the AllJoyn
     * bus.
     * <p/>
     * This should really be a more protected think, but for the sample we want
     * to show the user the states we are running through.  Because we are
     * really making a data hiding exception, and because we trust ourselves,
     * we don't go to any effort to prevent the UI from changing our state out
     * from under us.
     * <p/>
     * There are separate variables describing the states of the client
     * ("use") and service ("host") pieces.
     */
    public enum BusState {
        DISCONNECTED, /**
         * The bus attachment is not connected to the AllJoyn bus
         */
        CONNECTED, /**
         * The  bus attachment is connected to the AllJoyn bus
         */
        DISCOVERING        /** The bus attachment is discovering remote attachments hosting chat channels */
    }

    /**
     * Enumeration of the states of a hosted chat channel.  This lets us make a
     * note to ourselves regarding where we are in the process of preparing
     * and tearing down the AllJoyn pieces responsible for providing the chat
     * service.  In order to be out of the IDLE state, the BusAttachment state
     * must be at least CONNECTED.
     */
    public enum AlljoynChannelState {
        IDLE, /**
         * There is no used chat channel
         */
        REGISTERED, /**
         * The channel has been registered to the bus
         */
        JOINT,            /** The session for the channel has been successfully joined */
    }

    public class A3DiscoveryDescriptor extends A3GroupDescriptor {
        public A3DiscoveryDescriptor() {
            super("DISCOVERY", null, null);
        }

        @Override
        public int getSupervisorFitnessFunction() {
            return 0;
        }
    }
}

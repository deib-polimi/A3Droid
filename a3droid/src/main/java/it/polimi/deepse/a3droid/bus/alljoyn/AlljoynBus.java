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
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;

import it.polimi.deepse.a3droid.A3Message;
import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3Bus;
import it.polimi.deepse.a3droid.a3.A3Channel;
import it.polimi.deepse.a3droid.pattern.Observable;

/**
 * Created by seadev on 5/17/16.
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
        a3Application.addObserver(this);
        startBusThread();

        /*
         * We have an AllJoyn handler thread running at this time, so take
         * advantage of the fact to get connected to the bus and start finding
         * remote channel instances in the background while the rest of the app
         * is starting up.
         */
        mDiscoveryChannel = new AlljoynChannel("DISCOVERY", (A3Application)getApplication());
        mBackgroundHandler.connect(mDiscoveryChannel);
        mBackgroundHandler.startDiscovery(mDiscoveryChannel);
    }

    /**
     * Our onDestroy() is called by the Android appliation framework when it
     * decides that our Service is no longer needed.  We tell our background
     * thread to exit andremove ourselves from the list of observers of the
     * model.
     */
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        mBackgroundHandler.cancelDiscovery(mDiscoveryChannel);
        mBackgroundHandler.disconnect(mDiscoveryChannel);
        stopBusThread();
        a3Application.deleteObserver(this);
    }

    /**
     * This is the event handler for the Observable/Observed design pattern.
     * Whenever an interesting event happens in our appliation, the Model (the
     * source of the event) notifies registered observers, resulting in this
     * method being called since we registered as an Observer in onCreate().
     *
     * This method will be called in the context of the Model, which is, in
     * turn the context of an event source.  This will either be the single
     * Android application framework thread if the source is one of the
     * Activities of the application or the Service.  It could also be in the
     * context of the Service background thread.  Since the Android Application
     * framework is a fundamentally single threaded thing, we avoid multithread
     * issues and deadlocks by immediately getting this event into a separate
     * execution in the context of the Service message pump.
     *
     * We do this by taking the event from the calling component and queueing
     * it onto a "handler" in our Service and returning to the caller.  When
     * the calling componenet finishes what ever caused the event notification,
     * we expect the Android application framework to notice our pending
     * message and run our handler in the context of the single application
     * thread.
     *
     * In reality, both events are executed in the context of the single
     * Android thread.
     */
    public synchronized void update(Observable o, Object arg) {
        Log.i(TAG, "update(" + arg + ")");
        String qualifier = (String)arg;

        if (qualifier.equals(A3Application.APPLICATION_QUIT_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_APPLICATION_QUIT_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3Channel.CONNECT_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_CONNECT_EVENT);
            message.obj = (AlljoynChannel) o;
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3Channel.DISCONNECT_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_DISCONNECT_EVENT);
            message.obj = (AlljoynChannel) o;
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3Channel.JOIN_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_JOIN_CHANNEL_EVENT);
            message.obj = (AlljoynChannel) o;
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3Channel.USE_LEAVE_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_LEAVE_CHANNEL_EVENT);
            message.obj = (AlljoynChannel) o;
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3Channel.START_SERVICE_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_START_SERVICE_EVENT);
            message.obj = (AlljoynChannel) o;
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3Channel.STOP_SERVICE_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_STOP_SERVICE_EVENT);
            message.obj = (AlljoynChannel) o;
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(A3Channel.OUTBOUND_CHANGED_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_OUTBOUND_CHANGED_EVENT);
            message.obj = (AlljoynChannel) o;
            mHandler.sendMessage(message);
        }
    }

    /**
     * This is the Android Service message handler.  It runs in the context of the
     * main Android Service thread, which is also shared with Activities since
     * Android is a fundamentally single-threaded system.
     *
     * The important thing for us is to note that this thread cannot be blocked for
     * a significant amount of time or we risk the dreaded "force close" message.
     * We can run relatively short-lived operations here, but we need to run our
     * distributed system calls in a background thread.
     *
     * This handler serves translates from UI-related events into AllJoyn events
     * and decides whether functions can be handled in the context of the
     * Android main thread or if they must be dispatched to a background thread
     * which can take as much time as needed to accomplish a task.
     */
    public  Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AlljoynChannel channel = (AlljoynChannel) msg.obj;
            switch (msg.what) {
                case HANDLE_APPLICATION_QUIT_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): APPLICATION_QUIT_EVENT");
                    mBackgroundHandler.exit();
                    stopSelf();
                }
                break;
                case HANDLE_CONNECT_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_CONNECT_EVENT");
                    mBackgroundHandler.connect(channel);
                }
                break;
                case HANDLE_DISCONNECT_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): HANDLE_CONNECT_EVENT");
                    mBackgroundHandler.leaveSession(channel);
                    mBackgroundHandler.cancelAdvertise(channel);
                    mBackgroundHandler.unbindSession(channel);
                    mBackgroundHandler.releaseName(channel);
                    mBackgroundHandler.disconnect(channel);
                }
                break;
                case HANDLE_JOIN_CHANNEL_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): JOIN_CHANNEL_EVENT");
                    mBackgroundHandler.addService(channel);
                    mBackgroundHandler.addChannel(channel);
                    mBackgroundHandler.joinSession(channel);
                }
                break;
                case HANDLE_LEAVE_CHANNEL_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): USE_LEAVE_CHANNEL_EVENT");
                    mBackgroundHandler.leaveSession(channel);
                }
                break;
                case HANDLE_START_SERVICE_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): START_SERVICE_EVENT");
                    mBackgroundHandler.addService(channel);
                    mBackgroundHandler.requestName(channel);
                    mBackgroundHandler.bindSession(channel);
                    mBackgroundHandler.advertise(channel);
                    //This channel also needs to be connected to the new service
                    mBackgroundHandler.addChannel(channel);
                    mBackgroundHandler.joinSession(channel);
                }
                break;
                case HANDLE_STOP_SERVICE_EVENT:
                {
                    Log.i(TAG, "mHandler.handleMessage(): STOP_SERVICE_EVENT");
                    mBackgroundHandler.cancelAdvertise(channel);
                    mBackgroundHandler.unbindSession(channel);
                    mBackgroundHandler.releaseName(channel);
                }
                break;
                case HANDLE_OUTBOUND_CHANGED_EVENT:
                {
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
     *
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
    private final class BackgroundHandler extends Handler {
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
        public void connect(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.connect()");
            Message msg = mBackgroundHandler.obtainMessage(CONNECT);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * TODO: describe
         */
        public void addChannel(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.addChannel()");
            Message msg = mBackgroundHandler.obtainMessage(ADD_CHANNEL);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * TODO: describe
         */
        public void addService(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.addService()");
            Message msg = mBackgroundHandler.obtainMessage(ADD_SERVICE);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Disonnect the application from the Alljoyn bus attachment.  We
         * expect this method to be called in the context of the main Service
         * thread.  All this method does is to dispatch a corresponding method
         * in the context of the service worker thread.
         */
        public void disconnect(AlljoynChannel channel) {
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
        public void startDiscovery(AlljoynChannel channel) {
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
        public void cancelDiscovery(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.stopDiscovery()");
            Message msg = mBackgroundHandler.obtainMessage(CANCEL_DISCOVERY);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void requestName(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.requestName()");
            Message msg = mBackgroundHandler.obtainMessage(REQUEST_NAME);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void releaseName(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.releaseName()");
            Message msg = mBackgroundHandler.obtainMessage(RELEASE_NAME);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void bindSession(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.bindSession()");
            Message msg = mBackgroundHandler.obtainMessage(BIND_SESSION);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void unbindSession(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.unbindSession()");
            Message msg = mBackgroundHandler.obtainMessage(UNBIND_SESSION);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void advertise(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.advertise()");
            Message msg = mBackgroundHandler.obtainMessage(ADVERTISE);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void cancelAdvertise(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.cancelAdvertise()");
            Message msg = mBackgroundHandler.obtainMessage(CANCEL_ADVERTISE);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void joinSession(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.joinSession()");
            Message msg = mBackgroundHandler.obtainMessage(JOIN_SESSION);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void leaveSession(AlljoynChannel channel) {
            Log.i(TAG, "mBackgroundHandler.leaveSession()");
            Message msg = mBackgroundHandler.obtainMessage(LEAVE_SESSION);
            msg.obj = channel;
            mBackgroundHandler.sendMessage(msg);
        }

        public void sendMessages(AlljoynChannel channel) {
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
                    doConnect((AlljoynChannel) msg.obj);
                    break;
                case ADD_CHANNEL:
                    doAddChannel((AlljoynChannel) msg.obj);
                    break;
                case ADD_SERVICE:
                    doAddService((AlljoynChannel) msg.obj);
                    break;
                case DISCONNECT:
                    doDisconnect((AlljoynChannel) msg.obj);
                    break;
                case START_DISCOVERY:
                    doStartDiscovery((AlljoynChannel) msg.obj);
                    break;
                case CANCEL_DISCOVERY:
                    doStopDiscovery((AlljoynChannel) msg.obj);
                    break;
                case REQUEST_NAME:
                    doRequestName((AlljoynChannel) msg.obj);
                    break;
                case RELEASE_NAME:
                    doReleaseName((AlljoynChannel) msg.obj);
                    break;
                case BIND_SESSION:
                    doBindSession((AlljoynChannel) msg.obj);
                    break;
                case UNBIND_SESSION:
                    doUnbindSession((AlljoynChannel) msg.obj);
                    break;
                case ADVERTISE:
                    doAdvertise((AlljoynChannel) msg.obj);
                    break;
                case CANCEL_ADVERTISE:
                    doCancelAdvertise((AlljoynChannel) msg.obj);
                    break;
                case JOIN_SESSION:
                    doJoinSession((AlljoynChannel) msg.obj);
                    break;
                case LEAVE_SESSION:
                    doLeaveSession((AlljoynChannel) msg.obj);
                    break;
                case SEND_MESSAGES:
                    doSendMessages((AlljoynChannel) msg.obj);
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
        HandlerThread busThread = new HandlerThread("BackgroundHandler");
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

    private static AlljoynChannel mDiscoveryChannel;

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
    private void doConnect(AlljoynChannel channel) {
        Log.i(TAG, "doConnect()");
        BusAttachment mBus = channel.getBus();

        org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
        assert(channel.getBusState() == BusState.DISCONNECTED);
        mBus.useOSLogging(true);
        mBus.setDebugLevel("ALLJOYN_JAVA", 7);
        mBus.registerBusListener(mBusListener);

        Status status = mBus.connect();
        if (status != Status.OK) {
            a3Application.busError(A3Application.Module.GENERAL, "Unable to joinGroup to the bus: (" + status + ")");
            return;
        }

        channel.setBusState(BusState.CONNECTED);
    }

    private void doAddService(AlljoynChannel channel){

        //int stateRelation = channel.getBusState().compareTo(BusState.DISCONNECTED);
        //assert(stateRelation > 0);
        /*
         * To make a service available to other AllJoyn peers, first
         * register a BusObject with the BusAttachment at a specific
         * object path.  Our service is implemented by the AlljoynService
         * BusObject found at the "/a3GroupService" object path.
         */
        Status status = channel.getBus().registerBusObject(channel.getService(), OBJECT_PATH);
        if (Status.OK != status) {
            a3Application.busError(A3Application.Module.HOST, "Unable to register the service bus object: (" + status + ")");
            return;
        }else{
            channel.setServiceState(ServiceState.REGISTERED);
        }
    }

    private void doAddChannel(AlljoynChannel channel){
        int stateRelation = channel.getBusState().compareTo(BusState.DISCONNECTED);
        assert(stateRelation > 0);
        Status status = channel.getBus().registerSignalHandlers(channel);
        if (status != Status.OK) {
            a3Application.busError(A3Application.Module.USE, "Unable to register channel signal handlers: (" + status + ")");
            return;
        }else{
            channel.setChannelState(ChannelState.REGISTERED);
        }
    }

    /**
     * Implementation of the functionality related to disconnecting our app
     * from the AllJoyn bus.  We expect that this method will only be called
     * in the context of the AllJoyn bus handler thread.  We expect that this
     * method will only be called in the context of the AllJoyn bus handler
     * thread; and while we are in the CONNECTED state.
     */
    private boolean doDisconnect(AlljoynChannel channel) {
        Log.i(TAG, "doDisonnect()");
        assert(channel.getBusState() == BusState.CONNECTED);
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
    private void doStartDiscovery(AlljoynChannel channel) {
        Log.i(TAG, "doStartDiscovery()");
        assert(channel.getBusState() == BusState.CONNECTED);
        Status status = channel.getBus().findAdvertisedName(SERVICE_PATH);
        if (status == Status.OK) {
            channel.setBusState(BusState.DISCOVERING);
            return;
        } else {
            a3Application.busError(A3Application.Module.USE, "Unable to start finding advertised names: (" + status + ")");
            return;
        }
    }

    /**
     * Implementation of the functionality related to stopping discovery of
     * remote apps which are hosting chat channels.
     */
    private void doStopDiscovery(AlljoynChannel channel) {
        Log.i(TAG, "doStopDiscovery()");
        assert(channel.getBusState() == BusState.CONNECTED);
        channel.getBus().cancelFindAdvertisedName(SERVICE_PATH);
        channel.setBusState(BusState.CONNECTED);
    }

    /**
     * Implementation of the functionality related to requesting a well-known
     * name from an AllJoyn bus attachment.
     */
    private void doRequestName(AlljoynChannel channel) {
        Log.i(TAG, "doRequestName()");

        /*
         * In order to request a name, the bus attachment must at least be
         * connected.
         */
        int stateRelation = channel.getBusState().compareTo(BusState.DISCONNECTED);
        assert (stateRelation >= 0);

        //TODO: use the group name
        String wellKnownName = SERVICE_PATH + "." + channel.getGroupName();
        //TODO: check the needed FLAGS
        Status status = channel.getBus().requestName(wellKnownName, BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE);
        if (status == Status.OK) {
            channel.setServiceState(ServiceState.NAMED);
        } else {
            a3Application.busError(A3Application.Module.USE, "Unable to acquire well-known name: (" + status + ")");
        }
    }

    /**
     * Implementation of the functionality related to releasing a well-known
     * name from an AllJoyn bus attachment.
     */
    private void doReleaseName(AlljoynChannel channel) {
        Log.i(TAG, "doReleaseName()");

        int stateRelation = channel.getServiceState().compareTo(ServiceState.NAMED);
        if(stateRelation >= 0) {
            /*
             * In order to release a name, the bus attachment must at least be
             * connected.
             */
            stateRelation = channel.getBusState().compareTo(BusState.DISCONNECTED);
            assert (stateRelation >= 0);
            assert (channel.getBusState() == BusState.CONNECTED || channel.getBusState() == BusState.DISCOVERING);

            /*
             * We need to progress monotonically down the hosted channel states
             * for sanity.
             */
            assert (channel.getServiceState() == ServiceState.NAMED);

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
            channel.setServiceState(ServiceState.REGISTERED);
        }
    }

    /**
     * Implementation of the functionality related to binding a session port
     * to an AllJoyn bus attachment.
     */
    private void doBindSession(final AlljoynChannel channel) {
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
                if (sessionPort == CONTACT_PORT) {
                    return true;
                }
                return false;
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
            public void sessionJoined(short sessionPort, int id, String joiner) {
                Log.i(TAG, "SessionPortListener.sessionJoined(" + sessionPort + ", " + id + ", " + joiner + ")");
                channel.setSessionId(id);

                ProxyBusObject mProxyObj;
                mProxyObj = channel.getBus().getProxyBusObject(SERVICE_PATH + "." + channel.getGroupName(), OBJECT_PATH,
                        id, new Class<?>[]{AlljoynServiceInterface.class});
                channel.setServiceInterface(mProxyObj.getInterface(AlljoynServiceInterface.class), false);

                SignalEmitter emitter = new SignalEmitter(channel.getService(), id, SignalEmitter.GlobalBroadcast.Off);
                channel.getService().setServiceInterface(emitter.getInterface(AlljoynServiceInterface.class));
            }
        });

        if (status == Status.OK) {
            channel.setServiceState(ServiceState.BOUND);
        } else {
            a3Application.busError(A3Application.Module.HOST, "Unable to bind session contact port: (" + status + ")");
            return;
        }
    }

    /**
     * Implementation of the functionality related to un-binding a session port
     * from an AllJoyn bus attachment.
     */
    private void doUnbindSession(AlljoynChannel channel) {
        Log.i(TAG, "doUnbindSession()");
        int stateRelation = channel.getServiceState().compareTo(ServiceState.BOUND);
        if(stateRelation >= 0) {
            /*
             * There's not a lot we can do if the bus attachment refuses to unbind
             * our port.
             */
            channel.getBus().unbindSessionPort(CONTACT_PORT);
            channel.setServiceInterface(null, true);
            channel.setServiceState(ServiceState.NAMED);
        }
    }

    /**
     * Implementation of the functionality related to advertising a service on
     * an AllJoyn bus attachment.
     */
    private void doAdvertise(AlljoynChannel channel) {
        Log.i(TAG, "doAdvertise()");

        /*
         * We depend on the user interface and model to work together to not
         * change the name out from under us while we are running.
         */
        String wellKnownName = SERVICE_PATH + "." + channel.getGroupName();
        Status status = channel.getBus().advertiseName(wellKnownName, SessionOpts.TRANSPORT_ANY);

        if (status == Status.OK) {
            channel.setServiceState(ServiceState.ADVERTISED);
        } else {
            a3Application.busError(A3Application.Module.HOST, "Unable to advertise well-known name: (" + status + ")");
            return;
        }
    }

    /**
     * Implementation of the functionality related to canceling an advertisement
     * on an AllJoyn bus attachment.
     */
    private void doCancelAdvertise(AlljoynChannel channel) {
        Log.i(TAG, "doCancelAdvertise()");

        int stateRelation = channel.getServiceState().compareTo(ServiceState.ADVERTISED);
        if(stateRelation >= 0) {
            /*
             * We depend on the user interface and model to work together to not
             * change the name out from under us while we are running.
             */
            //TODO: Add RANDOM
            String wellKnownName = SERVICE_PATH + "." + channel.getGroupName();
            Status status = channel.getBus().cancelAdvertiseName(wellKnownName, SessionOpts.TRANSPORT_ANY);

            if (status != Status.OK) {
                a3Application.busError(A3Application.Module.HOST, "Unable to cancel advertisement of well-known name: (" + status + ")");
                return;
            }

            /*
             * There's not a lot we can do if the bus attachment refuses to cancel
             * our advertisement, so we don't bother to even get the status.
             */
            channel.setServiceState(ServiceState.BOUND);
        }
    }

    /**
     * Implementation of the functionality related to joining an existing
     * local or remote session.
     */
    private void doJoinSession(final AlljoynChannel channel) {
        Log.i(TAG, "doJoinSession()");

        /*
         * We depend on the user interface and model to work together to provide
         * a reasonable name.
         */
        String wellKnownName = SERVICE_PATH + "." + channel.getGroupName();

        /*
         * The random and unique channel identifier is provided by the bus
         */
        String channelId = channel.getBus().getUniqueName();
        channel.setChannelId(channelId);

        /*e
         * Since we can act as the host of a channel, we know what the other
         * side is expecting to see.
         */
        short contactPort = CONTACT_PORT;
        SessionOpts sessionOpts = new SessionOpts(SessionOpts.TRAFFIC_MESSAGES, true, SessionOpts.PROXIMITY_ANY, SessionOpts.TRANSPORT_ANY);
        Mutable.IntegerValue sessionId = new Mutable.IntegerValue();


        Status status = channel.getBus().joinSession(wellKnownName, contactPort, sessionId, sessionOpts, new SessionListener() {
            /**
             * This method is called when the last remote participant in the
             * chat session leaves for some reason and we no longer have anyone
             * to chat with.
             *
             * In the class documentation for the BusListener note that it is a
             * requirement for this method to be multithread safe.  This is
             * accomplished by the use of a monitor on the ChatApplication as
             * exemplified by the synchronized attribute of the removeFoundGroup
             * method there.
             */
            public void sessionLost(int sessionId, int reason) {
                Log.i(TAG, "BusListener.sessionLost(sessionId=" + sessionId + ",reason=" + reason + ")");
                a3Application.busError(A3Application.Module.USE, "The session has been lost");
                channel.setChannelState(ChannelState.IDLE);
            }
        });

        if (status == Status.OK) {
            channel.setSessionId(sessionId.value);
            Log.i(TAG, "doJoinSession(): use sessionId is " + sessionId.value);
        } else {
            a3Application.busError(A3Application.Module.USE, "Unable to join chat session: (" + status + ")");
            return;
        }

        //TODO: Used by a follower to send signals to the bus
        /** The Service proxy to communicate with. */
        ProxyBusObject mProxyObj;
        mProxyObj = channel.getBus().getProxyBusObject(SERVICE_PATH + "." + channel.getGroupName(), OBJECT_PATH,
                sessionId.value, new Class<?>[]{AlljoynServiceInterface.class});
        channel.setServiceInterface(mProxyObj.getInterface(AlljoynServiceInterface.class), true);

        //TODO: Original chat way of getting the service interface. But as a follower, channel.getService() won't be registered as object
        //SignalEmitter emitter = new SignalEmitter(channel.getService(), mUseSessionId, SignalEmitter.GlobalBroadcast.Off);
        //channel.setServiceInterface(emitter.getInterface(AlljoynServiceInterface.class), true);

        channel.setChannelState(ChannelState.JOINED);
    }

    /**
     * Implementation of the functionality related to joining an existing
     * remote session.
     */
    private void doLeaveSession(AlljoynChannel channel) {
        Log.i(TAG, "doLeaveSession()");
        if(channel.getChannelState() == ChannelState.JOINED) {
            channel.getBus().leaveSession(channel.getSessionId());
            channel.setSessionId(-1);
            channel.setChannelState(ChannelState.REGISTERED);
        }
    }

    /**
     * Implementation of the functionality related to sending messages out over
     * an existing remote session.  Note that we always send all of the
     * messages on the outbound queue, so there may be instances where this
     * method is called and we find nothing to send depending on the races.
     */
    private void doSendMessages(AlljoynChannel channel) {
        Log.i(TAG, "doSendMessages()");

        A3Message message;
        while ((message = channel.getOutboundItem()) != null) {
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
                switch (message.reason){

                    case A3Channel.BROADCAST_MSG:
                        channel.sendBroadcast(message);
                        break;
                    case A3Channel.UNICAST_MSG:
                        channel.sendUnicast(message);
                        break;
                    case A3Channel.MULTICAST_MSG:
                        channel.sendMulticast(message);
                        break;
                    default:
                        break;
                }
            } catch (BusException ex) {
                a3Application.busError(A3Application.Module.USE, "Bus exception while sending message: (" + ex + ")");
            }
        }
    }
}

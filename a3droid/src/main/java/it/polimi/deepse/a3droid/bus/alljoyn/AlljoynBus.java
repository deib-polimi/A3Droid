package it.polimi.deepse.a3droid.bus.alljoyn;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.Status;

import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.a3.A3BusInterface;

/**
 * Created by seadev on 5/17/16.
 */
public class AlljoynBus extends Service implements A3BusInterface {

    private static final String TAG = "a3droid.bus.AlljoynBus";

    /**
     * We don't use the bindery to communiate between any client and this
     * service so we return null.
     */
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        return null;
    }

    /**
     * A reference to a descendent of the Android Application class that is
     * acting as the Model of our MVC-based application.
     */
    private A3Application a3Application = null;

    /**
     * The state of the AllJoyn bus attachment.
     */
    private BusState mBusState = BusState.DISCONNECTED;

    /**
     * The state of the AllJoyn components responsible for hosting an chat channel.
     */
    private ServiceState serviceState = ServiceState.IDLE;

    /**
     * The state of the AllJoyn components responsible for hosting an chat channel.
     */
    private ChannelState channelState = ChannelState.IDLE;

    /**
     * The bus attachment is the object that provides AllJoyn services to Java
     * clients.  Pretty much all communiation with AllJoyn is going to go through
     * this obejct.
     */
    private BusAttachment mBus  = new BusAttachment(A3Application.PACKAGE_NAME, BusAttachment.RemoteMessage.Receive);

    /**
     * The well-known name prefix which all bus attachments hosting a channel
     * will use.  The NAME_PREFIX and the channel name are composed to give
     * the well-known name a hosting bus attachment will request and
     * advertise.
     */
    private static final String NAME_PREFIX = "org.alljoyn.bus.samples.chat";

    /**
     * The well-known session port used as the contact port for the chat service.
     */
    private static final short CONTACT_PORT = 27;

    /**
     * The object path used to identify the service "location" in the bus
     * attachment.
     */
    private static final String OBJECT_PATH = "/a3DroidService";

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
    private void doConnect() {
        Log.i(TAG, "doConnect()");
        org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
        assert(mBusState == BusState.DISCONNECTED);
        mBus.useOSLogging(true);
        mBus.setDebugLevel("ALLJOYN_JAVA", 7);
        mBus.registerBusListener(mBusListener);

        /*
         * To make a service available to other AllJoyn peers, first
         * register a BusObject with the BusAttachment at a specific
         * object path.  Our service is implemented by the AlljoynService
         * BusObject found at the "/chatService" object path.
         */
        Status status = mBus.registerBusObject(mAlljoynService, OBJECT_PATH);
        if (Status.OK != status) {
            a3Application.alljoynError(A3Application.Module.HOST, "Unable to register the chat bus object: (" + status + ")");
            return;
        }

        status = mBus.connect();
        if (status != Status.OK) {
            a3Application.alljoynError(A3Application.Module.GENERAL, "Unable to connect to the bus: (" + status + ")");
            return;
        }

        status = mBus.registerSignalHandlers(this);
        if (status != Status.OK) {
            a3Application.alljoynError(A3Application.Module.GENERAL, "Unable to register signal handlers: (" + status + ")");
            return;
        }

        mBusState = BusState.CONNECTED;
    }

    /**
     * Implementation of the functionality related to disconnecting our app
     * from the AllJoyn bus.  We expect that this method will only be called
     * in the context of the AllJoyn bus handler thread.  We expect that this
     * method will only be called in the context of the AllJoyn bus handler
     * thread; and while we are in the CONNECTED state.
     */
    private boolean doDisconnect() {
        Log.i(TAG, "doDisonnect()");
        assert(mBusState == BusState.CONNECTED);
        mBus.unregisterBusListener(mBusListener);
        mBus.disconnect();
        mBusState = BusState.DISCONNECTED;
        return true;
    }

    /**
     * Implementation of the functionality related to discovering remote apps
     * which are hosting chat channels.  We expect that this method will only
     * be called in the context of the AllJoyn bus handler thread; and while
     * we are in the CONNECTED state.  Since this is a core bit of functionalty
     * for the "use" side of the app, we always do this at startup.
     */
    private void doStartDiscovery() {
        Log.i(TAG, "doStartDiscovery()");
        assert(mBusState == BusState.CONNECTED);
        Status status = mBus.findAdvertisedName(NAME_PREFIX);
        if (status == Status.OK) {
            mBusState = BusState.DISCOVERING;
            return;
        } else {
            a3Application.alljoynError(A3Application.Module.USE, "Unable to start finding advertised names: (" + status + ")");
            return;
        }
    }

    /**
     * Implementation of the functionality related to stopping discovery of
     * remote apps which are hosting chat channels.
     */
    private void doStopDiscovery() {
        Log.i(TAG, "doStopDiscovery()");
        assert(mBusState == BusState.CONNECTED);
        mBus.cancelFindAdvertisedName(NAME_PREFIX);
        mBusState = BusState.CONNECTED;
    }

    /**
     * Implementation of the functionality related to requesting a well-known
     * name from an AllJoyn bus attachment.
     */
    private void doRequestName() {
        Log.i(TAG, "doRequestName()");

        /*
         * In order to request a name, the bus attachment must at least be
         * connected.
         */
        int stateRelation = mBusState.compareTo(BusState.DISCONNECTED);
        assert (stateRelation >= 0);

        /*
         * We depend on the user interface and model to work together to not
         * get this process started until a valid name is set in the channel name.
         */
        //TODO: use random id bellow
        String wellKnownName = NAME_PREFIX + "." + "RANDOM";
        //TODO: check the needed FLAGS
        Status status = mBus.requestName(wellKnownName, BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE);
        if (status == Status.OK) {
            serviceState = ServiceState.NAMED;
            a3Application.setServiceState(serviceState);
        } else {
            a3Application.alljoynError(A3Application.Module.USE, "Unable to acquire well-known name: (" + status + ")");
        }
    }

    /**
     * The AlljoynService is the instance of an AllJoyn interface that is exported
     * on the bus and allows us to send signals implementing messages
     */
    private AlljoynService mAlljoynService = new AlljoynService();
}

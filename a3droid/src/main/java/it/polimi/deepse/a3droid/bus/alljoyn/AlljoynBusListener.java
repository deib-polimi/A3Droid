package it.polimi.deepse.a3droid.bus.alljoyn;

import android.util.Log;

import org.alljoyn.bus.BusListener;
import org.greenrobot.eventbus.EventBus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.polimi.deepse.a3droid.a3.A3Application;
import it.polimi.deepse.a3droid.bus.alljoyn.events.AlljoynDuplicatedSessionEvent;

/**
 * The BusListener is a class that listens to the AllJoyn bus for
 * notifications corresponding to the existence of events happening out on
 * the bus. We provide one implementation of our listener to the bus
 * attachment during the AlljoynBus.joinGroup().
 */
public class AlljoynBusListener extends BusListener {

    private final String TAG = "a3droid.BusListener";
    private final String UNIQUE_SUFFIX_PATTERN = "\\.G[A-Za-z0-9]+";
    private final Pattern suffixPattern = Pattern.compile(UNIQUE_SUFFIX_PATTERN);

    private AlljoynBus alljoynBus;

    public AlljoynBusListener(AlljoynBus alljoynBus){
        this.alljoynBus = alljoynBus;
    }

    /**
     * This method is called when AllJoyn discovers a remote attachment
     * that is hosting an chat channel.  We expect that since we only
     * do a findAdvertisedName looking for instances of the chat
     * well-known name prefix we will only find names that we know to
     * be interesting.  When we find a remote application that is
     * hosting a channel, we add its channel name it to the list of
     * available channels selectable by the user.
     *
     * In the class documentation for the BusListener note that it is a
     * requirement for this method to be multithread safe.  This is
     * accomplished by the use of a monitor on the A3Application as
     * exemplified by the synchronized attribute of the addFoundGroup
     * method there.
     */
    public synchronized void foundAdvertisedName(String fullName, short transport, String namePrefix) {
        Log.i(TAG, "foundAdvertisedName(" + fullName + "," + transport + "," + namePrefix + ")");
        A3Application application = (A3Application) alljoynBus.getApplication();
        String nameWithSuffix = removeServicePrefix(fullName);
        String name = removeUniqueSuffix(nameWithSuffix);
        String suffix = nameWithSuffix.replace(name, "");
        if(application.isGroupFound(name)){
            if (isFoundSessionSuffixSmaller(name, suffix))
                createDuplicatedSessionEvent(name, suffix);
        }else
            application.addFoundGroup(name, suffix);
    }

    private boolean isFoundSessionSuffixSmaller(String name, String suffix){
        A3Application application = (A3Application) alljoynBus.getApplication();
        return application.getGroupSuffix(name).compareTo(suffix) < 0;
    }

    private void createDuplicatedSessionEvent(String name, String suffix) {
        A3Application application = (A3Application) alljoynBus.getApplication();
        application.addFoundGroup(name, suffix);
        EventBus.getDefault().post(new AlljoynDuplicatedSessionEvent(name));
    }

    private String removeServicePrefix(String nameWithPrefix){
        return nameWithPrefix.replace(AlljoynBus.SERVICE_PATH + ".", "");
    }

    private String removeUniqueSuffix(String nameWithSuffix){
        return nameWithSuffix.replaceFirst(UNIQUE_SUFFIX_PATTERN, "");
    }

    private String getUniqueSuffix(String nameWithSuffix){
        Matcher m = suffixPattern.matcher(nameWithSuffix);
        return m.group();
    }

    /**
     * This method is called when AllJoyn decides that a remote bus
     * attachment that is hosting an chat channel is no longer available.
     * When we lose a remote application that is hosting a channel, we
     * remote its name from the list of available channels selectable
     * by the user.
     *
     * In the class documentation for the BusListener note that it is a
     * requirement for this method to be multithread safe.  This is
     * accomplished by the use of a monitor on the A3Application as
     * exemplified by the synchronized attribute of the removeFoundGroup
     * method there.
     */
    public synchronized void lostAdvertisedName(String fullName, short transport, String namePrefix) {
        Log.i(TAG, "mBusListener.lostAdvertisedName(" + fullName + "," + transport + "," + namePrefix + ")");
        A3Application application = (A3Application) alljoynBus.getApplication();
        String nameWithSuffix = removeServicePrefix(fullName);
        String name = removeUniqueSuffix(nameWithSuffix);
        String suffix = nameWithSuffix.replace(name, "");
        if(application.isGroupFound(name) && application.getGroupSuffix(name).equals(suffix))
            application.removeFoundGroup(name);
    }


}

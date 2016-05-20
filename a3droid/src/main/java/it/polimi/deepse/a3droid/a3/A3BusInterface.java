package it.polimi.deepse.a3droid.a3;

/**
 * Created by seadev on 5/20/16.
 */
public interface A3BusInterface {

    /**
     * Enumeration of the states of the AllJoyn bus attachment.  This
     * lets us make a note to ourselves regarding where we are in the process
     * of preparing and tearing down the fundamental connection to the AllJoyn
     * bus.
     *
     * This should really be a more private think, but for the sample we want
     * to show the user the states we are running through.  Because we are
     * really making a data hiding exception, and because we trust ourselves,
     * we don't go to any effort to prevent the UI from changing our state out
     * from under us.
     *
     * There are separate variables describing the states of the client
     * ("use") and service ("host") pieces.
     */
    public static enum BusState {
        DISCONNECTED,    /** The bus attachment is not connected to the AllJoyn bus */
        CONNECTED,        /** The  bus attachment is connected to the AllJoyn bus */
        DISCOVERING        /** The bus attachment is discovering remote attachments hosting chat channels */
    }

    /**
     * Enumeration of the states of a hosted chat channel.  This lets us make a
     * note to ourselves regarding where we are in the process of preparing
     * and tearing down the AllJoyn pieces responsible for providing the chat
     * service.  In order to be out of the IDLE state, the BusAttachment state
     * must be at least CONNECTED.
     */
    public static enum ServiceState {
        IDLE,            /** There is no hosted chat channel */
        NAMED,            /** The well-known name for the channel has been successfully acquired */
        BOUND,            /** A session port has been bound for the channel */
        ADVERTISED,        /** The bus attachment has advertised itself as hosting an chat channel */
        CONNECTED       /** At least one remote device has connected to a session on the channel */
    }

    /**
     * Enumeration of the states of a hosted chat channel.  This lets us make a
     * note to ourselves regarding where we are in the process of preparing
     * and tearing down the AllJoyn pieces responsible for providing the chat
     * service.  In order to be out of the IDLE state, the BusAttachment state
     * must be at least CONNECTED.
     */
    public static enum ChannelState {
        IDLE,            /** There is no used chat channel */
        JOINED,            /** The session for the channel has been successfully joined */
    }

}

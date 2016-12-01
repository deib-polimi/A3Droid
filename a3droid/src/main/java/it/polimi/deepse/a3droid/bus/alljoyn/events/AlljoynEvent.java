package it.polimi.deepse.a3droid.bus.alljoyn.events;

/**
 * TODO:
 */
public class AlljoynEvent {

    public final String groupName;
    public final AlljoynEventType type;
    public final Object arg;

    public AlljoynEvent(AlljoynEventType type, String groupName, Object arg){
        this.type = type;
        this.groupName = groupName;
        this.arg = arg;
    }

    /**
     * The session with a service has been lost.
     */
    public enum AlljoynEventType {
        SESSION_BOUND,
        SESSION_ADVERTISED,
        SESSION_DESTROYED,
        SESSION_JOINED,
        SESSION_LEFT,
        SESSION_LOST,
        MEMBER_JOINED,
        MEMBER_LEFT
    }
}

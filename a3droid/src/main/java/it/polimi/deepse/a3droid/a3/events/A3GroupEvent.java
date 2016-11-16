package it.polimi.deepse.a3droid.a3.events;

/**
 * TODO
 */
public class A3GroupEvent {

    public final String groupName;
    public final A3GroupEventType eventType;
    public final Object object;

    public enum A3GroupEventType {
        GROUP_CREATED,
        GROUP_DESTROYED,
        GROUP_LOST,
        GROUP_JOINED,
        GROUP_LEFT,
        GROUP_STATE_CHANGED,
        MEMBER_LEFT,
        MEMBER_JOINED,
        SUPERVISOR_LEFT,
        SUPERVISOR_ELECTED,
        STACK_STARTED,
        STACK_FINISHED,
        REVERSE_STACK_STARTED,
        REVERSE_STACK_FINISHED,
        MERGE_STARTED,
        MERGE_FINISHED,
        SPLIT_STARTED,
        SPLIT_FINISHED
    }

    public A3GroupEvent(String groupName, A3GroupEventType eventType){
        this.groupName = groupName;
        this.eventType = eventType;
        this.object = null;
    }

    public A3GroupEvent(String groupName, A3GroupEventType eventType, Object object){
        this.groupName = groupName;
        this.eventType = eventType;
        this.object = object;
    }

    public String toString(){
        return this.groupName + "_" + this.eventType.toString();
    }
}

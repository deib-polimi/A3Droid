package it.polimi.deepse.a3droid.bus.alljoyn.events;

/**
 * TODO
 */
public class AlljoynDuplicatedSessionEvent {

    public final String groupName;

    public AlljoynDuplicatedSessionEvent(String groupName){
        this.groupName = groupName;
    }

    public String toString(){
        return this.groupName;
    }
}

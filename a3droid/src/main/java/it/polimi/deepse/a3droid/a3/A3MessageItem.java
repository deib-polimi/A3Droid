package it.polimi.deepse.a3droid.a3;

import it.polimi.deepse.a3droid.A3Message;

/**
 *
 */
public class A3MessageItem {

    private A3Message message;
    private int type;

    public A3MessageItem(A3Message message, int type){
        this.message = message;
        this.type = type;
    }

    public A3Message getMessage() {
        return message;
    }

    public int getType() {
        return type;
    }
    
}
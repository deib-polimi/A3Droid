package it.polimi.deepse.a3droid.a3.events;

import it.polimi.deepse.a3droid.a3.exceptions.A3Exception;

/**
 * TODO
 */
public class A3ErrorEvent {

    public final A3Exception exception;

    public A3ErrorEvent(A3Exception exception){
        this.exception = exception;
    }
}

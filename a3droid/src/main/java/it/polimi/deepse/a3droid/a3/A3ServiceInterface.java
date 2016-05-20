package it.polimi.deepse.a3droid.a3;

import it.polimi.deepse.a3droid.A3Message;

/**
 *
 */
public interface A3ServiceInterface {

    void sendToSupervisor(A3Message message) throws Exception;

    void sendUnicast(A3Message message) throws Exception;

    void sendMultiCast(A3Message message) throws Exception;

    void sendBroadcast(A3Message message) throws Exception;
}

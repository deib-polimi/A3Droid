package it.polimi.deepse.a3droid.a3;

import it.polimi.deepse.a3droid.a3.exceptions.A3InvalidOperationParameters;
import it.polimi.deepse.a3droid.a3.exceptions.A3NoGroupDescriptionException;

/**
 *
 */
public interface A3NodeInterface {

    void stack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException, A3InvalidOperationParameters;

    void stackReply(String parentGroupName, String childGroupName, boolean ok, boolean disconnect);

    boolean actualStack(String parentGroupName, String childGroupName) throws A3NoGroupDescriptionException;

    void merge(String newGroupName, String oldGroupName) throws A3NoGroupDescriptionException, A3InvalidOperationParameters;

    void actualMerge(String newGroupName, String oldGroupName) throws A3NoGroupDescriptionException;
}

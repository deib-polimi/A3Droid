package it.polimi.deepse.a3droid.pattern;

public interface Observable {
    void addObserver(Observer obs);
    void deleteObserver(Observer obs);
}

package it.polimi.deepse.a3droid.pattern;

public interface Observable {
    public void addObserver(Observer obs);
    public void deleteObserver(Observer obs);
}

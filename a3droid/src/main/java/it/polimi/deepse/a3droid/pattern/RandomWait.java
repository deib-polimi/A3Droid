package it.polimi.deepse.a3droid.pattern;

import java.util.Random;

/**
 * TODO: Describe it
 */
public class RandomWait {

    Random randomGenerator = new Random();

    public int next(int fixed, int maxRandom){
        return fixed + randomGenerator.nextInt(maxRandom);
    }
}

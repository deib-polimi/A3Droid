package it.polimi.deepse.a3droid.a3;

import it.polimi.deepse.a3droid.bus.alljoyn.AlljoynBus;

/**
 * TODO:
 */
public class A3DiscoveryDescriptor extends A3GroupDescriptor {

    public static final String DISCOVERY_GROUP_NAME = "DISCOVERY";

    public A3DiscoveryDescriptor(AlljoynBus alljoynBus) {
        super(DISCOVERY_GROUP_NAME, null, null);
    }

    @Override
    public int getSupervisorFitnessFunction() {
        return 0;
    }
}

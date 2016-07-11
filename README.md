# A3Droid
A-3 middleware for Android platform.

The class diagram bellow encompasses all layers and classes currently implementated, i.e., both A3 and Alljoyn layers.
![State Machine for A3 Group](https://raw.githubusercontent.com/deib-polimi/A3Droid/master/docs/ClassDiagram.png)

## A-3 middleware layer

The state diagram bellow shows the different states of an A3Group, i.e., IDLE, CREATED, ELECTION and ACTIVE.
An A3Group is active only after a supervisor has been elected for that group. Until a supervisor is elected, A3Group remains at ELECTION state. Only when the supervisor is elected and the A3Group state evolves to ACTIVE, the active role (A3FollowerRole or A3SupervisorRole) will become active, i.e., its thread will run.
![State Machine for A3 Group](https://raw.githubusercontent.com/deib-polimi/A3Droid/master/docs/a3/A3GroupStateMachine.png)

## Alljoyn framework layer

The state machine diagram bellow shows the different states of an AlljoynService and an AlljoynChannel. 
![State Machine for A3 Group](https://raw.githubusercontent.com/deib-polimi/A3Droid/master/docs/alljoyn/AlljoynStateMachines.png)

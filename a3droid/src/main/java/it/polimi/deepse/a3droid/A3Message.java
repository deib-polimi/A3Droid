package it.polimi.deepse.a3droid;

import org.alljoyn.bus.annotation.Position;
import org.alljoyn.bus.annotation.Signature;

/**This class represents the messages that are exchanged by the nodes through the channels.
 * In order for AllJoyn to correctly marshal and unmarshal this data structure,
 * fields must be public and a constructor without parameters must exist.
 * Being the fields public, their getters and their setters are not needed.
 */
public class A3Message {

	/**The address of the channel which sends this message.*/
	@Position(0)
	@Signature("s")
	public String senderAddress;
	
	/**The kind of this message.*/
	@Position(1)
	@Signature("i")
	public int reason;
	
	/**The data in this message.*/
	@Position(2)
	@Signature("s")
	public String object;
	
	/**The extra data in this message.*/
	@Position(3)
	@Signature("ay")
	public byte[] bytes = new byte[0];

	/**The extra data in this message.*/
	@Position(4)
	@Signature("as")
	public String[] addresses = new String [0];

	/**This must exists because AllJoyn needs it, but is never used in these API.*/
	public A3Message(){}
	
	/**
	 * @param reason The kind of this message.
	 * @param object The data in this message.
	 */
	public A3Message(int reason, String object){
		this.reason = reason;
		this.object = object;
		this.bytes = new byte[0];
		this.senderAddress = "";
		this.addresses = new String [0];
	}
	
	/**
	 * @param reason The kind of this message.
	 * @param object The data in this message.
	 * @param extra The data in this message.
	 */
	public A3Message(int reason, String object, byte [] extra){
		assert(extra != null);
		this.reason = reason;
		this.object = object;
		this.bytes = extra;
		this.senderAddress = "";
		this.addresses = new String [0];
	}

	/**
	 * @param reason The kind of this message.
	 * @param object The data in this message.
	 * @param extra The data in this message.
	 * @param addresses The data in this message.
	 */
	public A3Message(int reason, String object, byte [] extra, String [] addresses){
		assert(extra != null);
		assert(addresses != null);
		this.reason = reason;
		this.object = object;
		this.bytes = extra;
		senderAddress = "";
		this.addresses = addresses;
	}
	
	@Override
	public String toString(){
		String reasonString;
		
		switch(reason){
		case Constants.NEW_SUPERVISOR: reasonString = "NEW_SUPERVISOR"; break;
		case Constants.SUBSCRIPTION: reasonString = "SUBSCRIPTION"; break;
		case Constants.UNSUBSCRIPTION: reasonString = "UNSUBSCRIPTION"; break;
		case Constants.ADD_TO_HIERARCHY: reasonString = "ADD_TO_HIERARCHY"; break;
		case Constants.REMOVE_FROM_HIERARCHY: reasonString = "REMOVE_FROM_HIERARCHY"; break;
		case Constants.HIERARCHY: reasonString = "HIERARCHY"; break;
		case Constants.PEERS_REQUEST: reasonString = "PEERS_REQUEST"; break;
		case Constants.HIERARCHY_REQUEST: reasonString = "HIERARCHY_REQUEST"; break;
		case Constants.REVERSE_STACK: reasonString = "REVERSE_STACK"; break;
		case Constants.STACK_REPLY: reasonString = "STACK_REPLY"; break;
		case Constants.PEERS_REPLY: reasonString = "PEERS_REPLY"; break;
		case Constants.HIERARCHY_REPLY: reasonString = "HIERARCHY_REPLY"; break;
		case Constants.GET_HIERARCHY: reasonString = "GET_HIERARCHY"; break;
		case Constants.STACK_REQUEST: reasonString = "STACK_REQUEST"; break;
		case Constants.MERGE: reasonString = "MERGE"; break;
		case Constants.SPLIT: reasonString = "SPLIT"; break;
		case Constants.SUPERVISOR_ELECTION: reasonString = "SUPERVISOR_ELECTION"; break;
		case Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST: reasonString = "SUPERVISOR_FITNESS_FUNCTION_REQUEST"; break;
		case Constants.BOOLEAN_SPLIT_FITNESS_FUNCTION: reasonString = "BOOLEAN_SPLIT_FITNESS_FUNCTION"; break;
		case Constants.INTEGER_SPLIT_FITNESS_FUNCTION: reasonString = "INTEGER_SPLIT_FITNESS_FUNCTION"; break;
		case Constants.NEW_SPLITTED_GROUP: reasonString = "NEW_SPLITTED_GROUP"; break;
		case Constants.NEW_GROUP: reasonString = "NEW_GROUP"; break;
		case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION: reasonString = "WAIT_SUPERVISOR_FITNESS_FUNCTION"; break;
		case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST: reasonString = "WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST"; break;
		case Constants.WAIT_NEW_SUPERVISOR: reasonString = "WAIT_NEW_SUPERVISOR"; break;
		case Constants.SUPERVISOR_FITNESS_FUNCTION_REPLY: reasonString = "SUPERVISOR_FITNESS_FUNCTION_REPLY"; break;
		default: reasonString = String.valueOf(reason); break;
		}
		return senderAddress + " " + reasonString + " " + object;
	}
}

package it.polimi.deepse.a3droid;

import android.os.Handler;
import android.os.Message;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.Status;

public class DiscoveryManager extends Thread {
	
	private String name;
	private String nameWithSuffix;
	private Service ui;
	private BusAttachment mBus;
	private CallbackThread callbackThread;
	private boolean isDuplicated;

	public DiscoveryManager(String nameToDiscover, String nameWithSuffix, Service service){
		name = nameToDiscover;
		this.nameWithSuffix = nameWithSuffix;
		ui = service;
		callbackThread = new CallbackThread();
		isDuplicated = false;
		start();
	}

	public void connect(){

		mBus = new BusAttachment(getClass().getPackage().getName(), BusAttachment.RemoteMessage.Receive);

		mBus.registerBusListener(new BusListener() {
			
			@Override
			public void foundAdvertisedName(String name, short transport, String namePrefix) {
			Message msg = callbackThread.obtainMessage();
			msg.obj = name;
			msg.arg1 = transport;
			//msg.arg2 = Constants.FOUND_NAME;
			callbackThread.sendMessage(msg);
			}
		});

		Status status = mBus.connect();
		if (Status.OK != status) {
			showOnScreen("STATUS = " + status + " DOPO CONNECT().");
			return;
		}

		/* Inizio il discovery, che continua finch� il canale � connesso.
		 * Attivo il timer, il quale, una volta scaduto, chiama il metodo handleTimeEvent().
		 * Allo scadere del tempo, se non sono ancora connesso a nessun gruppo creo il Service.
		 * A questo punto scopro il gruppo appena creato e mi ci connetto.
		 */
		status = mBus.findAdvertisedName(name + ".");
		if (Status.OK != status) {
			showOnScreen("STATUS = " + status + " DOPO FINDADVERTISEDNAME().");
			return;
		}
	}

	public void disconnect(){
		mBus.cancelFindAdvertisedName(name);
		mBus.disconnect();
	}
	
	private void showOnScreen(String string) {
		// TODO Auto-generated method stub
		ui.showOnScreen("(DiscoveryManager): " + string);
	}
	
	
	private class CallbackThread extends Handler {
		
		public CallbackThread() {
			super();
		}
	
		public void handleMessage(Message msg) {

			if(A3Channel.removeSuffix((String) msg.obj).equals(name)){
				if(isDuplicated) {
					ui.showOnScreen("Duplication for " + name + " found");
					ui.sendDuplicatedGroupSignal((int) (Math.random() * 100));
				}
						
				// Trovo sempre il nome del service che mi ha creato.
				isDuplicated = true;
			}
		}
	}
}

package de.pixart.messenger.services;

import android.content.Intent;
import android.util.Log;

import java.util.Map;

import de.pixart.messenger.Config;

public class PushMessageReceiver extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        if (!EventReceiver.hasEnabledAccounts(this)) {
            Log.d(Config.LOGTAG, "PushMessageReceiver ignored message because no accounts are enabled");
            return;
        }
        Map<String, String> data = message.getData();
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_FCM_MESSAGE_RECEIVED);
        intent.putExtra("account", data.get("account"));
        startService(intent);
    }
}

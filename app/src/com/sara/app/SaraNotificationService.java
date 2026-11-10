package com.sara.app;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Notification pora. Sudhu user Notification Access dile kaj kore. Data shudhu memory-te thake.
public class SaraNotificationService extends NotificationListenerService {

    static volatile boolean connected = false;
    static final List<String> recent = Collections.synchronizedList(new ArrayList<String>());

    @Override
    public void onListenerConnected() {
        connected = true;
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Bundle ex = sbn.getNotification().extras;
            CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
            String line = sbn.getPackageName() + " | " + (title == null ? "" : title)
                    + " | " + (text == null ? "" : text);
            synchronized (recent) {
                recent.add(0, line);
                while (recent.size() > 30) recent.remove(recent.size() - 1);
            }
        } catch (Exception e) {
            // ignore
        }
    }
}

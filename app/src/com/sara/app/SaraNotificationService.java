package com.sara.app;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

// Notifications are kept only in memory (last 30).
public class SaraNotificationService extends NotificationListenerService {

    static volatile boolean connected = false;
    static volatile SaraNotificationService instance;
    static final List<String> keys = new ArrayList<>();
    static final List<String> texts = new ArrayList<>();

    @Override
    public void onListenerConnected() {
        connected = true;
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        instance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Bundle ex = sbn.getNotification().extras;
            CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
            String line = sbn.getPackageName() + " | " + (title == null ? "" : title)
                    + " | " + (text == null ? "" : text);
            synchronized (keys) {
                keys.add(0, sbn.getKey());
                texts.add(0, line);
                while (keys.size() > 30) {
                    keys.remove(keys.size() - 1);
                    texts.remove(texts.size() - 1);
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    static void forget(String key) {
        synchronized (keys) {
            int i = keys.indexOf(key);
            if (i >= 0) {
                keys.remove(i);
                texts.remove(i);
            }
        }
    }
}

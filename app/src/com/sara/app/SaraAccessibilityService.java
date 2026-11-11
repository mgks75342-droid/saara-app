package com.sara.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

// Screen read, tap, swipe and global keys. Everything runs only when the user has enabled this service.
public class SaraAccessibilityService extends AccessibilityService {

    static volatile SaraAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // intentionally empty: content is read only on request
    }

    // ---- read ----
    // null = service off, "" = no text, else text (max 4000 chars)
    static String snapshot() {
        SaraAccessibilityService s = instance;
        if (s == null) return null;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        collect(root, sb, 0);
        return sb.length() > 4000 ? sb.substring(0, 4000) : sb.toString();
    }

    private static void collect(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 40 || sb.length() > 4000) return;
        CharSequence txt = node.getText();
        CharSequence cd = node.getContentDescription();
        if (txt != null && txt.length() > 0) {
            sb.append(txt).append('\n');
        } else if (cd != null && cd.length() > 0) {
            sb.append(cd).append('\n');
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), sb, depth + 1);
        }
    }

    // ---- global keys: "back", "home", "recents", "notifications" ----
    static String globalAction(String name) {
        SaraAccessibilityService s = instance;
        if (s == null) return "ERR:অ্যাক্সেসিবিলিটি চালু নেই";
        int a;
        switch (name) {
            case "back": a = GLOBAL_ACTION_BACK; break;
            case "home": a = GLOBAL_ACTION_HOME; break;
            case "recents": a = GLOBAL_ACTION_RECENTS; break;
            case "notifications": a = GLOBAL_ACTION_NOTIFICATIONS; break;
            default: return "ERR:অজানা কাজ";
        }
        return s.performGlobalAction(a) ? "OK" : "ERR:কাজটি হয়নি";
    }

    // ---- swipe: up, down, left, right ----
    static String swipe(String dir) {
        SaraAccessibilityService s = instance;
        if (s == null) return "ERR:অ্যাক্সেসিবিলিটি চালু নেই";
        DisplayMetrics dm = s.getResources().getDisplayMetrics();
        float w = dm.widthPixels, h = dm.heightPixels;
        float x1, y1, x2, y2;
        switch (dir) {
            case "up":    x1 = w / 2; y1 = h * 0.75f; x2 = w / 2; y2 = h * 0.25f; break;
            case "down":  x1 = w / 2; y1 = h * 0.25f; x2 = w / 2; y2 = h * 0.75f; break;
            case "left":  x1 = w * 0.8f; y1 = h / 2; x2 = w * 0.2f; y2 = h / 2; break;
            case "right": x1 = w * 0.2f; y1 = h / 2; x2 = w * 0.8f; y2 = h / 2; break;
            default: return "ERR:দিক হবে up, down, left বা right";
        }
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 300)).build();
        return s.dispatchGesture(g, null, null) ? "OK" : "ERR:সোয়াইপ হয়নি";
    }

    // ---- tap on text ----
    static String tapText(String text) {
        SaraAccessibilityService s = instance;
        if (s == null) return "ERR:অ্যাক্সেসিবিলিটি চালু নেই";
        if (text == null || text.trim().isEmpty()) return "ERR:কী লেখায় ট্যাপ করব বলুন";
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return "ERR:স্ক্রিন পড়া যাচ্ছে না";
        boolean ok = findAndClick(s, root, text.trim().toLowerCase(), 0);
        return ok ? "OK" : "ERR:স্ক্রিনে \"" + text + "\" পাওয়া যায়নি";
    }

    private static boolean findAndClick(SaraAccessibilityService s, AccessibilityNodeInfo node, String want, int depth) {
        if (node == null || depth > 40) return false;
        CharSequence txt = node.getText();
        CharSequence cd = node.getContentDescription();
        String t = (txt != null ? txt.toString() : (cd != null ? cd.toString() : "")).toLowerCase();
        if (!t.isEmpty() && t.contains(want)) {
            AccessibilityNodeInfo p = node;
            while (p != null) {
                if (p.isClickable()) return p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                p = p.getParent();
            }
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            return tap(s, r.centerX(), r.centerY());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findAndClick(s, node.getChild(i), want, depth + 1)) return true;
        }
        return false;
    }

    private static boolean tap(SaraAccessibilityService s, float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60)).build();
        return s.dispatchGesture(g, null, null);
    }

    // ---- type into the focused text box (appends) ----
    static String typeText(String text) {
        SaraAccessibilityService s = instance;
        if (s == null) return "ERR:অ্যাক্সেসিবিলিটি চালু নেই";
        if (text == null || text.isEmpty()) return "ERR:কী টাইপ করব বলুন";
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return "ERR:স্ক্রিন পড়া যাচ্ছে না";
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null) return "ERR:কোনো লেখার ঘর খোলা নেই। আগে ঘরটায় ট্যাপ করুন।";
        CharSequence old = f.getText();
        String merged = (old == null ? "" : old.toString()) + text;
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged);
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b) ? "OK" : "ERR:লিখতে পারিনি";
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}

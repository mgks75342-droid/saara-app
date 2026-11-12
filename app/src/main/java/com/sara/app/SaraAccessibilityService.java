package com.sara.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;

public class SaraAccessibilityService extends AccessibilityService {
    static volatile SaraAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { /* কিছু লাগবে না, শুধু কমান্ডের জন্য */ }

    @Override
    public void onInterrupt() { }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    /** Bridge থেকে ডাকা হয়। ফলাফল একটা ছোট বাংলা/ইংরেজি বার্তা। */
    String perform(String action, String target) {
        if (action == null) return "Action paoa jay nai.";
        switch (action) {
            case "back":          return doGlobal(GLOBAL_ACTION_BACK, "Back gelo.");
            case "home":          return doGlobal(GLOBAL_ACTION_HOME, "Home-e gelo.");
            case "recents":       return doGlobal(GLOBAL_ACTION_RECENTS, "Recent apps khola hoyeche.");
            case "notifications": return doGlobal(GLOBAL_ACTION_NOTIFICATIONS, "Notification khola hoyeche.");
            case "scroll_down":   return swipe(0.7f, 0.3f);
            case "scroll_up":     return swipe(0.3f, 0.7f);
            case "click_text":    return clickText(target);
            default:              return "Ei action ta jane na: " + action;
        }
    }

    private String doGlobal(int g, String ok) {
        return performGlobalAction(g) ? ok : "Ei kaaj ta hoy nai.";
    }

    private String swipe(float fromY, float toY) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float x = dm.widthPixels / 2f;
        Path p = new Path();
        p.moveTo(x, dm.heightPixels * fromY);
        p.lineTo(x, dm.heightPixels * toY);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, 300);
        boolean ok = dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
        return ok ? "Scroll korlam." : "Scroll hoy nai.";
    }

    private String clickText(String target) {
        if (target == null || target.trim().isEmpty()) return "Kon text e click korbo bolen.";
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "Screen-e kichu paoa jay nai.";
        String want = target.trim().toLowerCase(Locale.ROOT);
        AccessibilityNodeInfo hit = find(root, want);
        if (hit == null) return "'" + target + "' screen-e paoa jay nai.";
        // clickable parent khuje click
        AccessibilityNodeInfo n = hit;
        while (n != null && !n.isClickable()) n = n.getParent();
        boolean ok = (n != null) && n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return ok ? "'" + target + "' e click korlam." : "Click kora gelo na.";
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String want) {
        if (node == null) return null;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        if ((t != null && t.toString().toLowerCase(Locale.ROOT).contains(want))
                || (d != null && d.toString().toLowerCase(Locale.ROOT).contains(want))) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = find(node.getChild(i), want);
            if (r != null) return r;
        }
        return null;
    }
}

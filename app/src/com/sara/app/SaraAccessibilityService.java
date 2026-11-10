package com.sara.app;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

// Screen text is read only when the user asks (snapshot), not on every screen change.
// This keeps the phone fast.
public class SaraAccessibilityService extends AccessibilityService {

    static volatile SaraAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // intentionally empty
    }

    // Returns null if service is off, "" if no text, else the text (max 4000 chars).
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

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}

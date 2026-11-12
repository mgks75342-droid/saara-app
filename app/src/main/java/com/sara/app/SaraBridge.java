package com.sara.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import java.util.List;
import java.util.Locale;

/** JavaScript থেকে ডাকা হয় (window.SaraNative.xxx). */
public class SaraBridge {
    private final Context ctx;

    public SaraBridge(Context ctx) { this.ctx = ctx; }

    @JavascriptInterface
    public String openApp(String name) {
        if (name == null || name.trim().isEmpty()) return "App-er naam paoa jay nai.";
        String want = name.trim().toLowerCase(Locale.ROOT);

        PackageManager pm = ctx.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcher, 0);

        ResolveInfo exact = null, partial = null;
        for (ResolveInfo ri : apps) {
            String label = String.valueOf(ri.loadLabel(pm)).toLowerCase(Locale.ROOT);
            if (label.equals(want)) { exact = ri; break; }
            if (partial == null && (label.contains(want) || want.contains(label))) partial = ri;
        }
        ResolveInfo hit = exact != null ? exact : partial;
        if (hit == null) return "'" + name + "' naame kono app paoa jay nai.";

        Intent i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName);
        if (i == null) return "App khola jay nai.";
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return String.valueOf(hit.loadLabel(pm)) + " khola hoyeche.";
    }

    @JavascriptInterface
    public String phoneAction(String action, String target) {
        SaraAccessibilityService svc = SaraAccessibilityService.instance;
        if (svc == null) return "Accessibility chalu nai. Settings theke Sara ke on korun.";
        return svc.perform(action, target);
    }

    @JavascriptInterface
    public boolean isAccessibilityOn() {
        return SaraAccessibilityService.instance != null;
    }

    @JavascriptInterface
    public void openAccessibilitySettings() {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}

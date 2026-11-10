package com.sara.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// JavaScript (window.Android) theke call hoy. Sob file kaj sudhu external storage er bhetor.
public class SaraBridge {

    private final Activity act;

    public SaraBridge(Activity activity) {
        this.act = activity;
    }

    private File root() {
        return Environment.getExternalStorageDirectory();
    }

    // Path ta external storage er bhetore kina check kore. Baire hole null.
    private File safe(String path) {
        try {
            File f = (path == null || path.trim().isEmpty()) ? root() : new File(path.trim());
            File c = f.getCanonicalFile();
            String r = root().getCanonicalPath();
            if (c.getPath().equals(r) || c.getPath().startsWith(r + File.separator)) return c;
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private void open(final Intent i) {
        act.runOnUiThread(() -> {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(i);
            } catch (Exception e) {
                // setting screen na thakle chupchap bad
            }
        });
    }

    // ---- status ----
    @JavascriptInterface
    public String status() {
        JSONObject o = new JSONObject();
        try {
            o.put("accessibility", SaraAccessibilityService.instance != null);
            o.put("notifications", SaraNotificationService.connected);
            o.put("allFiles", Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager() : true);
            o.put("root", root().getAbsolutePath());
        } catch (Exception e) {
            // ignore
        }
        return o.toString();
    }

    // ---- settings screens ----
    @JavascriptInterface
    public void openAccessibility() {
        open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @JavascriptInterface
    public void openNotificationAccess() {
        open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    @JavascriptInterface
    public void openAllFilesAccess() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            i.setData(Uri.parse("package:" + act.getPackageName()));
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + act.getPackageName()));
        }
        open(i);
    }

    // ---- screen reading (needs accessibility service) ----
    @JavascriptInterface
    public String getScreenText() {
        if (SaraAccessibilityService.instance == null) {
            return "অ্যাক্সেসিবিলিটি সার্ভিস চালু নেই। আগে সেটিং থেকে চালু করুন।";
        }
        String t = SaraAccessibilityService.lastText;
        return (t == null || t.isEmpty()) ? "এখনো স্ক্রিনে কোনো লেখা পাওয়া যায়নি।" : t;
    }

    // ---- notifications (needs notification access) ----
    @JavascriptInterface
    public String getNotifications() {
        JSONArray arr = new JSONArray();
        synchronized (SaraNotificationService.recent) {
            for (String s : SaraNotificationService.recent) arr.put(s);
        }
        return arr.toString();
    }

    // ---- file manager ----
    // Returns JSON array string, or "ERR:..."
    @JavascriptInterface
    public String listFiles(String path) {
        File dir = safe(path);
        if (dir == null || !dir.isDirectory()) return "ERR:ফোল্ডার পাওয়া যায়নি বা অনুমোদিত নয়";
        File[] fs = dir.listFiles();
        if (fs == null) return "ERR:ফোল্ডার পড়া যাচ্ছে না। All files access দিন।";
        Arrays.sort(fs, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        JSONArray arr = new JSONArray();
        for (File f : fs) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", f.getName());
                o.put("path", f.getAbsolutePath());
                o.put("dir", f.isDirectory());
                o.put("size", f.length());
                arr.put(o);
            } catch (Exception e) {
                // skip
            }
        }
        return arr.toString();
    }

    // Returns "OK:<path>" or "ERR:..."
    @JavascriptInterface
    public String createFile(String path, String content) {
        File f = safe(path);
        if (f == null) return "ERR:অনুমোদিত নয়";
        if (f.exists()) return "ERR:এই নামে ফাইল আছে";
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            }
            return "OK:" + f.getAbsolutePath();
        } catch (IOException e) {
            return "ERR:" + e.getMessage();
        }
    }

    // Returns "OK" or "ERR:..."
    @JavascriptInterface
    public String deleteFile(String path) {
        File f = safe(path);
        if (f == null || f.equals(root())) return "ERR:অনুমোদিত নয়";
        if (!f.exists()) return "ERR:ফাইল নেই";
        if (f.isDirectory()) {
            String[] c = f.list();
            if (c != null && c.length > 0) return "ERR:ফোল্ডার খালি নয়";
        }
        return f.delete() ? "OK" : "ERR:মোছা যায়নি";
    }
}

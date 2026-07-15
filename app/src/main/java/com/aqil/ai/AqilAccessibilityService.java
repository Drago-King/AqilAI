package com.aqil.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.accessibility.AccessibilityEvent;

public class AqilAccessibilityService extends AccessibilityService {
    static AqilAccessibilityService instance;
    @Override public void onServiceConnected() { instance = this; startService(new Intent(this, AqilOverlayService.class)); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    public static boolean runCommand(String text) {
        if (instance == null || text == null) return false;
        String cmd = text.toLowerCase();
        if (cmd.contains("scroll down")) return instance.swipe(500, 1400, 500, 500);
        if (cmd.contains("scroll up")) return instance.swipe(500, 500, 500, 1400);
        if (cmd.contains("go back") || cmd.equals("back")) return instance.performGlobalAction(GLOBAL_ACTION_BACK);
        if (cmd.contains("home")) return instance.performGlobalAction(GLOBAL_ACTION_HOME);
        if (cmd.contains("recent")) return instance.performGlobalAction(GLOBAL_ACTION_RECENTS);
        if (cmd.contains("youtube")) return instance.openUri("https://www.youtube.com/results?search_query=" + Uri.encode(clean(cmd, "open", "play", "youtube")));
        if (cmd.contains("song") || cmd.contains("music")) return instance.openUri("https://www.youtube.com/results?search_query=" + Uri.encode(clean(cmd, "play", "song", "music")));
        if (cmd.contains("food") || cmd.contains("order")) return instance.openUri("https://www.google.com/search?q=" + Uri.encode(cmd));
        if (cmd.contains("fetch") || cmd.contains("image") || cmd.contains("certificate")) return instance.openGallerySearch(clean(cmd, "fetch", "image", "named"));
        if (cmd.startsWith("open ")) return instance.openUri("https://www.google.com/search?q=" + Uri.encode(cmd));
        return instance.openUri("https://www.google.com/search?q=" + Uri.encode(cmd));
    }

    private static String clean(String cmd, String... words) { for (String w: words) cmd = cmd.replace(w, " "); return cmd.trim(); }
    private boolean openUri(String uri) { Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true; }
    private boolean openGallerySearch(String query) {
        Intent i = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true;
    }
    private boolean swipe(float x1, float y1, float x2, float y2) { Path p = new Path(); p.moveTo(x1,y1); p.lineTo(x2,y2); dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,450)).build(), null, null); return true; }
}

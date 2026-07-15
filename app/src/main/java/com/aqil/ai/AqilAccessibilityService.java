package com.aqil.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

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
    if (cmd.contains("quick settings") || cmd.contains("flashlight") || cmd.contains("wi-fi") || cmd.contains("wifi")) return instance.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);  
    if (cmd.contains("notification")) return instance.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);  
    if (cmd.contains("screenshot") && Build.VERSION.SDK_INT >= 30) { instance.takeScreenshot(0, instance.getMainExecutor(), new TakeScreenshotCallback() { public void onSuccess(ScreenshotResult result) { } public void onFailure(int errorCode) { } }); return true; }  
    if (cmd.contains("camera")) return instance.openIntent(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));  
    if (cmd.contains("settings")) return instance.openIntent(new Intent(Settings.ACTION_SETTINGS));  
    if (cmd.contains("brightness")) return instance.openIntent(new Intent(Settings.ACTION_DISPLAY_SETTINGS));  
    if (cmd.contains("volume") || cmd.contains("sound")) return instance.openIntent(new Intent(Settings.ACTION_SOUND_SETTINGS));  
    if (cmd.contains("telegram")) return instance.openPackageOrSearch("org.telegram.messenger", "Telegram");  
    if (cmd.contains("whatsapp")) return instance.openPackageOrSearch("com.whatsapp", "WhatsApp");  
    if (cmd.contains("chrome")) return instance.openPackageOrSearch("com.android.chrome", "Chrome");  
    if (cmd.contains("youtube")) return instance.openUri("https://www.youtube.com/results?search_query=" + Uri.encode(clean(cmd, "open", "play", "youtube")));  
    if (cmd.contains("song") || cmd.contains("music")) return instance.openUri("https://www.youtube.com/results?search_query=" + Uri.encode(clean(cmd, "play", "song", "music")));  
    if (cmd.contains("food") || cmd.contains("order")) return instance.openUri("https://www.google.com/search?q=" + Uri.encode(cmd + " confirm before purchase"));  
    if (cmd.contains("fetch") || cmd.contains("image") || cmd.contains("certificate") || cmd.contains("gallery")) return instance.openGallerySearch();  
    if (cmd.startsWith("type ")) return instance.typeIntoFocused(cmd.substring(5));  
    if (cmd.startsWith("open ")) return instance.openUri("https://www.google.com/search?q=" + Uri.encode(cmd));  
    return instance.openUri("https://www.google.com/search?q=" + Uri.encode(cmd));  
}  

private static String clean(String cmd, String... words) { for (String w: words) cmd = cmd.replace(w, " "); return cmd.trim(); }  
private boolean openUri(String uri) { return openIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(uri))); }  
private boolean openIntent(Intent i) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true; }  
private boolean openPackageOrSearch(String packageName, String label) { Intent i = getPackageManager().getLaunchIntentForPackage(packageName); return i != null ? openIntent(i) : openUri("https://play.google.com/store/search?q=" + Uri.encode(label) + "&c=apps"); }  
private boolean openGallerySearch() { return openIntent(new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)); }  
private boolean typeIntoFocused(String value) { AccessibilityNodeInfo root = getRootInActiveWindow(); if (root == null) return false; AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT); if (focused == null) return false; android.os.Bundle b = new android.os.Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value); return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b); }  
private boolean swipe(float x1, float y1, float x2, float y2) { Path p = new Path(); p.moveTo(x1,y1); p.lineTo(x2,y2); dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,450)).build(), null, null); return true; }

}

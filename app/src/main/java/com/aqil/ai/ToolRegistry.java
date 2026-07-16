package com.aqil.ai;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

public class ToolRegistry {
public interface Callback { void onResult(String result); }
public static JSONArray schema() throws Exception {
JSONArray tools = new JSONArray();
add(tools, "accessibility_action", "Run Android accessibility/system actions like back, home, recent, scroll, open app, type, camera, settings, flashlight.");
add(tools, "gallery_search", "Search photos by filename, OCR text, and image labels. Returns candidate image URIs; requires user confirmation before sharing.");
add(tools, "whatsapp_prepare", "Open WhatsApp, search for a contact by name, open their chat, and (optionally) attach an image found via gallery_search. Input should be the contact name; pass image_uri separately if an image was already found. Never sends without the user's own final tap.");
add(tools, "camera", "Open the camera app.");
add(tools, "file_manager", "Open Android file picker or file manager intent.");
add(tools, "system_action", "Open settings, quick settings, notifications, brightness, sound, Wi-Fi controls.");
add(tools, "screen_agent", "For any multi-step on-screen task not covered by the other tools -- opening any app and navigating its menus, filling out a form, tapping through a flow you don't have a dedicated tool for. It looks at the actual screen and decides what to tap/type itself, one step at a time, and stops itself when done or stuck. Input is the goal in plain language, e.g. 'open Settings and turn on Wi-Fi' or 'open Instagram and like the first post'.");
return tools;
}
private static void add(JSONArray tools, String name, String description) throws Exception { tools.put(new JSONObject().put("name", name).put("description", description)); }
public static void execute(Context c, String tool, String input, Callback cb) { execute(c, tool, input, null, cb); }

public static void execute(Context c, String tool, String input, String imageUri, Callback cb) {
if (tool == null) { cb.onResult("No tool selected."); return; }
switch (tool) {
case "gallery_search": GallerySearchEngine.search(c, input, cb::onResult); break;
case "whatsapp_prepare": {
    Uri uri = imageUri != null && !imageUri.isEmpty() ? Uri.parse(imageUri) : null;
    boolean started = AqilAccessibilityService.startWhatsApp(input, uri);
    cb.onResult(started ? "Searching WhatsApp for \"" + input + "\"..." : "Accessibility service isn't connected -- enable it in Permissions first.");
    break;
}
case "screen_agent": {
    boolean started = AqilAccessibilityService.startScreenAgent(c, input, status -> cb.onResult(status));
    if (!started) cb.onResult("Accessibility service isn't connected -- enable it in Permissions first.");
    break;
}
case "camera": AqilAccessibilityService.runCommand("camera"); cb.onResult("Opened camera."); break;
case "file_manager": AqilAccessibilityService.runCommand("open files"); cb.onResult("Opened file manager/search."); break;
case "system_action":
case "accessibility_action": AqilAccessibilityService.runCommand(input); cb.onResult("Ran Android action: " + input); break;
default: cb.onResult("Unknown tool: " + tool);
}
}
}

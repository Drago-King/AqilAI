package com.aqil.ai;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

public class ToolRegistry {
public interface Callback { void onResult(String result); }
public static JSONArray schema() throws Exception {
JSONArray tools = new JSONArray();
add(tools, "accessibility_action", "Run Android accessibility/system actions like back, home, recent, scroll, open app, type, camera, settings, flashlight.");
add(tools, "gallery_search", "Search photos by filename, OCR text, and image labels. Returns candidate image URIs; requires user confirmation before sharing.");
add(tools, "whatsapp_prepare", "Open WhatsApp or prepare a WhatsApp workflow. Never send without explicit user confirmation.");
add(tools, "camera", "Open the camera app.");
add(tools, "file_manager", "Open Android file picker or file manager intent.");
add(tools, "system_action", "Open settings, quick settings, notifications, brightness, sound, Wi-Fi controls.");
return tools;
}
private static void add(JSONArray tools, String name, String description) throws Exception { tools.put(new JSONObject().put("name", name).put("description", description)); }
public static void execute(Context c, String tool, String input, Callback cb) {
if (tool == null) { cb.onResult("No tool selected."); return; }
switch (tool) {
case "gallery_search": GallerySearchEngine.search(c, input, cb::onResult); break;
case "whatsapp_prepare": AqilAccessibilityService.runCommand("whatsapp"); cb.onResult("Opened WhatsApp. Confirm recipient and message before sending."); break;
case "camera": AqilAccessibilityService.runCommand("camera"); cb.onResult("Opened camera."); break;
case "file_manager": AqilAccessibilityService.runCommand("open files"); cb.onResult("Opened file manager/search."); break;
case "system_action":
case "accessibility_action": AqilAccessibilityService.runCommand(input); cb.onResult("Ran Android action: " + input); break;
default: cb.onResult("Unknown tool: " + tool);
}
}
}

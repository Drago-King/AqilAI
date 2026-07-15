package com.aqil.ai;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AgentBrain {
public interface Callback { void onAnswer(String answer); }
public static final String PREFS = "aqil_agent";
public static final String[] PROVIDERS = {"OpenAI Compatible", "OpenRouter", "Groq", "Google Gemini", "Local (Ollama)"};
private static final String SYSTEM = "You are AQIL, a safe Android tool-calling assistant. Reason first. If a phone tool is needed, return ONLY JSON: {\"tool\":\"tool_name\",\"input\":\"short command/query\",\"confirm\":true/false}. Use confirm=true before sending messages, purchases, orders, or sharing files. If no tool is needed, answer normally. Available tools are provided in the user message.";

public static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }  
public static void saveProvider(Context c, String provider, String baseUrl, String model, String key) {  
    prefs(c).edit().putString("provider", provider).putString("base_url", trimSlash(baseUrl)).putString("model", model.trim()).putString("api_key", key.trim()).apply();  
}  
public static void saveVoice(Context c, boolean enabled, String elevenKey, String voiceId, String speed, String stability, String style) {  
    prefs(c).edit().putBoolean("tts_enabled", enabled).putString("eleven_key", elevenKey.trim()).putString("voice_id", voiceId.trim()).putString("voice_speed", speed.trim()).putString("voice_stability", stability.trim()).putString("voice_style", style.trim()).apply();  
}  
public static String get(Context c, String key, String fallback) { return prefs(c).getString(key, fallback); }  
public static boolean getBool(Context c, String key, boolean fallback) { return prefs(c).getBoolean(key, fallback); }  
public static void addHistory(Context c, String line) {  
    String old = get(c, "history", "");  
    prefs(c).edit().putString("history", (line + "\n" + old).trim()).apply();  
}  

public static void ask(Context context, String prompt, Callback callback) {  
    addHistory(context, "You: " + prompt);  
    new Thread(() -> {  
        String key = get(context, "api_key", "");  
        String base = get(context, "base_url", "https://openrouter.ai/api/v1");  
        String model = get(context, "model", "tencent/hy3:free");  
        String answer;  
        if (base.isEmpty() || model.isEmpty()) answer = "Provider is not configured. Open AI Providers and set Base URL + Model.";  
        else {  
            try { answer = callOpenAiCompatible(key, base, model, prompt); }  
            catch (Exception e) { answer = tryFallback(context, key, base, model, prompt, e); }  
        }  
        String finalAnswer = answer;  
        try {  
            JSONObject maybeTool = extractJson(answer);  
            if (maybeTool != null && maybeTool.has("tool")) {  
                if (maybeTool.optBoolean("confirm", false)) finalAnswer = "I found the required action, but need your confirmation before I continue: " + maybeTool.toString();  
                else { ToolRegistry.execute(context, maybeTool.optString("tool"), maybeTool.optString("input", prompt), result -> callback.onAnswer("Tool result: " + result)); return; }  
            }  
        } catch (Exception ignored) { }  
        addHistory(context, "AQIL: " + finalAnswer);  
        callback.onAnswer(finalAnswer);  
    }).start();  
}  

public static void test(Context context, Callback callback) { ask(context, "Reply with: Connected", callback); }  

public static String local(String prompt) {  
    String p = prompt == null ? "" : prompt.toLowerCase();  
    if (p.contains("whatsapp") && p.contains("send")) return "I can open WhatsApp and prepare the chat, but I will ask for confirmation before sending.";  
    if (p.contains("telegram")) return "Opening Telegram.";  
    if (p.contains("chrome")) return "Opening Chrome.";  
    if (p.contains("camera")) return "Opening Camera.";  
    if (p.contains("flashlight")) return "Opening quick settings for flashlight control.";  
    if (p.contains("brightness")) return "Opening display settings for brightness.";  
    if (p.contains("volume")) return "Adjusting volume where Android allows.";  
    if (p.contains("scroll")) return "Running scroll command.";  
    if (p.contains("youtube")) return "Opening YouTube search.";  
    if (p.contains("song") || p.contains("music")) return "Playing music through YouTube search.";  
    if (p.contains("certificate") || p.contains("image") || p.contains("gallery")) return "Opening gallery search workflow. OCR/object recognition is planned as an on-device module.";  
    return "Basic command mode ready. Configure an OpenAI-compatible provider for deeper reasoning.";  
}  

private static String callOpenAiCompatible(String key, String baseUrl, String model, String prompt) throws Exception {  
    URL url = new URL(trimSlash(baseUrl) + "/chat/completions");  
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();  
    conn.setRequestMethod("POST");  
    if (key != null && !key.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + key);  
    conn.setRequestProperty("Content-Type", "application/json");  
    conn.setRequestProperty("HTTP-Referer", "https://github.com/Drago-King/AqilAI");  
    conn.setRequestProperty("X-Title", "AQIL AI");  
    conn.setDoOutput(true);  
    JSONObject body = new JSONObject(); body.put("model", model);  
    JSONArray messages = new JSONArray();  
    messages.put(new JSONObject().put("role", "system").put("content", SYSTEM));  
    messages.put(new JSONObject().put("role", "user").put("content", "Tools: " + ToolRegistry.schema().toString() + "\nUser request: " + prompt));  
    body.put("messages", messages); body.put("stream", false);  
    try (OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }  
    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream()));  
    StringBuilder response = new StringBuilder(); String line; while ((line = reader.readLine()) != null) response.append(line);  
    if (conn.getResponseCode() >= 400) throw new Exception(response.toString());  
    JSONObject json = new JSONObject(response.toString());  
    return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");  
}  
private static String tryFallback(Context context, String key, String base, String model, String prompt, Exception original) {  
    String fb = get(context, "fallback_base_url", ""); String fm = get(context, "fallback_model", ""); String fk = get(context, "fallback_api_key", key);  
    if (!fb.isEmpty() && !fm.isEmpty() && !fb.equals(base)) { try { return callOpenAiCompatible(fk, fb, fm, prompt); } catch (Exception ignored) { } }  
    return local(prompt) + "\nProvider error: " + original.getMessage();  
}  
private static JSONObject extractJson(String text) {  
    if (text == null) return null; int start = text.indexOf('{'); int end = text.lastIndexOf('}'); if (start < 0 || end <= start) return null;  
    try { return new JSONObject(text.substring(start, end + 1)); } catch (Exception e) { return null; }  
}  
private static String trimSlash(String s) { if (s == null) return ""; s = s.trim(); while (s.endsWith("/")) s = s.substring(0, s.length() - 1); return s; }

}

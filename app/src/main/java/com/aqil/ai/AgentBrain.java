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
    private static final String PREFS = "aqil_agent";
    private static final String KEY_API = "api_key";
    private static final String SYSTEM = "You are AQIL, a concise Android phone-control agent. Return short natural text plus action hints when useful: scroll down, scroll up, back, home, recent, youtube, music, food, fetch image, or web search.";

    public static void saveApiKey(Context context, String key) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_API, key.trim()).apply();
    }

    public static String getApiKey(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API, "");
    }

    public static void ask(Context context, String prompt, Callback callback) {
        new Thread(() -> {
            String key = getApiKey(context);
            if (key.isEmpty()) { callback.onAnswer(local(prompt)); return; }
            try { callback.onAnswer(callOpenAi(key, prompt)); }
            catch (Exception e) { callback.onAnswer(local(prompt) + "\nAI cloud error: " + e.getMessage()); }
        }).start();
    }

    private static String local(String prompt) {
        String p = prompt == null ? "" : prompt.toLowerCase();
        if (p.contains("scroll")) return "Running scroll command.";
        if (p.contains("youtube")) return "Opening YouTube search.";
        if (p.contains("song") || p.contains("music")) return "Playing music through YouTube search.";
        if (p.contains("food") || p.contains("order")) return "Opening food/order search.";
        if (p.contains("certificate") || p.contains("image") || p.contains("gallery")) return "Opening gallery image browser.";
        if (p.contains("back") || p.contains("home") || p.contains("recent")) return "Running navigation command.";
        return "API key not set yet. I can still run basic phone commands, but add an OpenAI API key in AQIL settings for smarter reasoning.";
    }

    private static String callOpenAi(String key, String prompt) throws Exception {
        URL url = new URL("https://api.openai.com/v1/responses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        JSONObject body = new JSONObject();
        body.put("model", "gpt-4.1-mini");
        JSONArray input = new JSONArray();
        input.put(new JSONObject().put("role", "system").put("content", SYSTEM));
        input.put(new JSONObject().put("role", "user").put("content", prompt));
        body.put("input", input);
        try (OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream()));
        StringBuilder response = new StringBuilder(); String line;
        while ((line = reader.readLine()) != null) response.append(line);
        JSONObject json = new JSONObject(response.toString());
        if (json.has("output_text")) return json.getString("output_text");
        return json.toString();
    }
}

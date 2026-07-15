package com.aqil.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class VoiceEngine {
public interface Callback { void onStatus(String status); }
private static MediaPlayer player;
public static void stop() { try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) { } }
public static void speak(Context c, String text, Callback cb) {
stop();
new Thread(() -> {
try {
String key = AgentBrain.get(c, "eleven_key", ""); String voice = AgentBrain.get(c, "voice_id", "");
if (key.isEmpty() || voice.isEmpty()) { cb.onStatus("ElevenLabs key/voice missing."); return; }
URL url = new URL("https://api.elevenlabs.io/v1/text-to-speech/" + voice + "/stream");
HttpURLConnection conn = (HttpURLConnection) url.openConnection(); conn.setRequestMethod("POST"); conn.setDoOutput(true);
conn.setRequestProperty("xi-api-key", key); conn.setRequestProperty("Content-Type", "application/json"); conn.setRequestProperty("Accept", "audio/mpeg");
String body = "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"voice_settings\":{\"stability\":" + AgentBrain.get(c,"voice_stability","0.5") + ",\"similarity_boost\":" + AgentBrain.get(c,"voice_style","0.75") + "}}";
try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
if (conn.getResponseCode() >= 400) { cb.onStatus("Voice failed: HTTP " + conn.getResponseCode()); return; }
File out = new File(c.getCacheDir(), "aqil_voice.mp3"); try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) { byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) fos.write(buf,0,n); }
player = new MediaPlayer(); player.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build()); player.setDataSource(out.getAbsolutePath()); player.prepare(); player.start(); cb.onStatus("Speaking...");
} catch (Exception e) { cb.onStatus("Voice error: " + e.getMessage()); }
}).start();
}
}

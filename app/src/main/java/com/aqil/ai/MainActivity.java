package com.aqil.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {
private LinearLayout nav, content; private TextView status; private EditText chatInput; private SpeechRecognizer recognizer;
private final String[] pages = {"Home", "Chat", "Floating Assistant", "Voice", "AI Providers", "Permissions", "History", "About"};

@Override public void onCreate(Bundle b) { super.onCreate(b); requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_MEDIA_IMAGES}, 10); shell(); show("Home"); }  
@Override protected void onResume() { super.onResume(); if (Settings.canDrawOverlays(this)) startService(new Intent(this, AqilOverlayService.class)); }  

private void shell() {  
    if (nav != null) return; // shell already built once; must not rebuild/duplicate the nav on resume  
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.HORIZONTAL); root.setBackgroundResource(R.drawable.app_background);  
    nav = new LinearLayout(this); nav.setOrientation(LinearLayout.VERTICAL); nav.setPadding(dp(10), dp(24), dp(10), dp(12)); nav.setBackgroundColor(0x66000612);  
    root.addView(nav, new LinearLayout.LayoutParams(dp(118), ViewGroup.LayoutParams.MATCH_PARENT));  
    ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18), dp(24), dp(18), dp(36)); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));  
    ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_aqil_logo); nav.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));  
    for (String page : pages) { Button b = smallButton(page); b.setOnClickListener(v -> show(page)); nav.addView(b); }  
    setContentView(root);  
}  

private void show(String page) {  
    content.removeAllViews(); hero(page);  
    switch (page) {  
        case "Chat": chatPage(); break; case "AI Providers": providersPage(); break; case "Voice": voicePage(); break; case "Floating Assistant": floatingPage(); break; case "History": historyPage(); break; case "Permissions": permissionsPage(); break; case "Accessibility": accessibilityPage(); break; case "Automation": automationPage(); break; case "About": aboutPage(); break; default: homePage(page); break;  
    }  
}  

private void hero(String title) { content.addView(text(title, 34, R.color.aqil_text, Typeface.BOLD)); TextView sub = text("AQIL AI • premium Android assistant console", 15, R.color.aqil_gold, Typeface.BOLD); sub.setLetterSpacing(0.08f); content.addView(sub); }  
private void homePage(String page) { LinearLayout c = card(); c.addView(text("Assistant Control Center", 22, R.color.aqil_text, Typeface.BOLD)); c.addView(text("Use the drawer to configure providers, voice, floating assistant, permissions, automation, and chat. The app now has separated pages instead of one crowded screen.", 15, R.color.aqil_muted, Typeface.NORMAL)); c.addView(button("Start Floating Assistant", true, v -> enableOverlay())); c.addView(button("Open Chat", false, v -> show("Chat"))); content.addView(c); if (!page.equals("Home")) content.addView(info("This section is ready for deeper options as the agent grows.")); }  

private void chatPage() { LinearLayout c = card(); c.addView(text("Talk to AQIL", 22, R.color.aqil_text, Typeface.BOLD)); chatInput = input("Ask: Open Telegram, find my certificate, call Mom on WhatsApp..."); c.addView(chatInput); c.addView(button("Send", true, v -> run(chatInput.getText().toString()))); c.addView(button("Voice Input", false, v -> listen())); status = text("Ready", 15, R.color.aqil_gold, Typeface.BOLD); c.addView(status); content.addView(c); }  

private void providersPage() { LinearLayout c = card(); c.addView(text("AI Providers", 24, R.color.aqil_text, Typeface.BOLD)); c.addView(row("Subscription", "Early Access")); c.addView(row("Status", "Configurable"));  
    Spinner provider = new Spinner(this); provider.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, AgentBrain.PROVIDERS)); c.addView(label("Provider")); c.addView(provider);  
    EditText base = input("Base URL, e.g. https://openrouter.ai/api/v1"); base.setText(AgentBrain.get(this, "base_url", "https://openrouter.ai/api/v1")); c.addView(label("Base URL")); c.addView(base);  
    EditText model = input("AI Model, e.g. tencent/hy3:free"); model.setText(AgentBrain.get(this, "model", "tencent/hy3:free")); c.addView(label("AI Model")); c.addView(model);  
    EditText key = input("API Key"); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); key.setText(AgentBrain.get(this, "api_key", "")); c.addView(label("API Key")); c.addView(key);  
    EditText fallbackBase = input("Fallback Base URL optional"); fallbackBase.setText(AgentBrain.get(this, "fallback_base_url", "")); c.addView(label("Fallback Base URL")); c.addView(fallbackBase);  
    EditText fallbackModel = input("Fallback Model optional"); fallbackModel.setText(AgentBrain.get(this, "fallback_model", "")); c.addView(label("Fallback Model")); c.addView(fallbackModel);  
    EditText fallbackKey = input("Fallback API Key optional"); fallbackKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); fallbackKey.setText(AgentBrain.get(this, "fallback_api_key", "")); c.addView(label("Fallback API Key")); c.addView(fallbackKey);  
    TextView state = text("Not tested", 15, R.color.aqil_muted, Typeface.BOLD); c.addView(state);  
    c.addView(button("Save Provider", true, v -> { AgentBrain.saveProvider(this, provider.getSelectedItem().toString(), base.getText().toString(), model.getText().toString(), key.getText().toString()); AgentBrain.prefs(this).edit().putString("fallback_base_url", fallbackBase.getText().toString()).putString("fallback_model", fallbackModel.getText().toString()).putString("fallback_api_key", fallbackKey.getText().toString()).apply(); state.setText("Saved"); }));  
    c.addView(button("Test Connection", false, v -> { state.setText("Connecting..."); AgentBrain.saveProvider(this, provider.getSelectedItem().toString(), base.getText().toString(), model.getText().toString(), key.getText().toString()); AgentBrain.prefs(this).edit().putString("fallback_base_url", fallbackBase.getText().toString()).putString("fallback_model", fallbackModel.getText().toString()).putString("fallback_api_key", fallbackKey.getText().toString()).apply(); AgentBrain.test(this, ans -> runOnUiThread(() -> state.setText(ans.toLowerCase().contains("connected") ? "Connected" : "Failed: " + ans))); }));  
    content.addView(c); }  

private void voicePage() { LinearLayout c = card(); c.addView(text("Voice", 24, R.color.aqil_text, Typeface.BOLD)); CheckBox enabled = new CheckBox(this); enabled.setText("Enable TTS"); enabled.setTextColor(getColor(R.color.aqil_text)); enabled.setChecked(AgentBrain.getBool(this, "tts_enabled", false)); c.addView(enabled);  
    EditText eleven = input("ElevenLabs API Key"); eleven.setText(AgentBrain.get(this, "eleven_key", "")); EditText voice = input("Voice ID"); voice.setText(AgentBrain.get(this, "voice_id", "")); EditText speed = input("Voice Speed"); speed.setText(AgentBrain.get(this, "voice_speed", "1.0")); EditText stability = input("Voice Stability"); stability.setText(AgentBrain.get(this, "voice_stability", "0.5")); EditText style = input("Voice Style"); style.setText(AgentBrain.get(this, "voice_style", "0.0"));  
    c.addView(label("ElevenLabs API Key")); c.addView(eleven); c.addView(label("Voice ID")); c.addView(voice); c.addView(label("Voice Speed")); c.addView(speed); c.addView(label("Voice Stability")); c.addView(stability); c.addView(label("Voice Style")); c.addView(style);  
    TextView state = text("Voice not tested", 15, R.color.aqil_muted, Typeface.BOLD); c.addView(state); c.addView(button("Save Voice", true, v -> { AgentBrain.saveVoice(this, enabled.isChecked(), eleven.getText().toString(), voice.getText().toString(), speed.getText().toString(), stability.getText().toString(), style.getText().toString()); state.setText("Voice settings saved"); })); c.addView(button("Test Voice", false, v -> VoiceEngine.speak(this, "AQIL voice is ready.", msg -> runOnUiThread(() -> state.setText(msg))))); c.addView(button("Stop Speaking", false, v -> { VoiceEngine.stop(); state.setText("Stopped"); })); content.addView(c); }  

private void floatingPage() { LinearLayout c = card(); c.addView(text("Floating Assistant", 24, R.color.aqil_text, Typeface.BOLD)); c.addView(text("The AQIL bubble starts after overlay permission, restarts on app resume, can be dragged, expands into mic + keyboard, and long-press opens the app.", 15, R.color.aqil_muted, Typeface.NORMAL)); c.addView(button("Grant Overlay / Start Bubble", true, v -> enableOverlay())); content.addView(c); }  
private void historyPage() { content.addView(info(AgentBrain.get(this, "history", "No conversation history yet."))); }  
private void permissionsPage() { LinearLayout c = card(); c.addView(button("Accessibility Settings", true, v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))); c.addView(button("Overlay Permission", false, v -> enableOverlay())); c.addView(button("App Details", false, v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))); content.addView(c); }  
private void accessibilityPage() { content.addView(info("Supported: open apps, click/scroll/type basics, read active window content, back/home/recents, dialogs where Android exposes accessible controls. Enable Accessibility first.")); }  
private void automationPage() { content.addView(info("Safe automation requires confirmation before sending messages, ordering food, purchases, or sharing images. Gallery search uses MediaStore plus ML Kit OCR and image labeling, then requires confirmation before sharing.")); }  
private void aboutPage() { content.addView(info("AQIL AI v0.2.0\nBuilt as a privacy-first Android assistant starter with configurable providers and accessibility automation.")); }  

private void enableOverlay() { if (!Settings.canDrawOverlays(this)) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); else startService(new Intent(this, AqilOverlayService.class)); }  
private void run(String text) { if (status != null) status.setText("Thinking..."); AgentBrain.ask(this, text, a -> runOnUiThread(() -> { if (status != null) status.setText(a); if (AgentBrain.getBool(this, "tts_enabled", false)) VoiceEngine.speak(this, a, msg -> runOnUiThread(() -> { if (status != null) status.setText(msg + "\n" + a); })); })); }  
private void listen() { recognizer = SpeechRecognizer.createSpeechRecognizer(this); recognizer.setRecognitionListener(new RecognitionListener() { public void onReadyForSpeech(Bundle p) { if(status!=null)status.setText("Listening..."); } public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onError(int e){ if(status!=null)status.setText("Mic error: "+e); } public void onResults(Bundle r){ ArrayList<String> a=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(a!=null&&!a.isEmpty()){chatInput.setText(a.get(0)); run(a.get(0));}} public void onPartialResults(Bundle p){} public void onEvent(int t, Bundle p){} }); Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); recognizer.startListening(i); }  

private LinearLayout card() { LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setBackgroundResource(R.drawable.panel_card); p.setPadding(dp(18), dp(18), dp(18), dp(18)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(12), 0, dp(14)); p.setLayoutParams(lp); return p; }  
private TextView info(String s) { TextView t = text(s, 16, R.color.aqil_muted, Typeface.NORMAL); t.setBackgroundResource(R.drawable.panel_card); t.setPadding(dp(18), dp(18), dp(18), dp(18)); return t; }  
private TextView label(String s) { return text(s, 16, R.color.aqil_text, Typeface.BOLD); }  
private TextView row(String l, String r) { TextView t = text(l + "                                      " + r, 16, R.color.aqil_text, Typeface.BOLD); t.setBackgroundResource(R.drawable.row_divider); return t; }  
private EditText input(String h) { EditText e = new EditText(this); e.setHint(h); e.setTextColor(getColor(R.color.aqil_text)); e.setHintTextColor(getColor(R.color.aqil_muted)); e.setBackgroundResource(R.drawable.input_field); e.setPadding(dp(14), dp(12), dp(14), dp(12)); return e; }  
private TextView text(String v, int sp, int color, int style) { TextView t = new TextView(this); t.setText(v); t.setTextSize(sp); t.setTextColor(getColor(color)); t.setTypeface(Typeface.create("sans-serif", style)); t.setLineSpacing(0, 1.15f); t.setPadding(0, dp(5), 0, dp(7)); return t; }  
private Button button(String v, boolean primary, View.OnClickListener l) { Button b = new Button(this); b.setText(v); b.setAllCaps(false); b.setTextColor(getColor(primary ? R.color.aqil_bg : R.color.aqil_text)); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackgroundResource(primary ? R.drawable.primary_button : R.drawable.secondary_button); b.setOnClickListener(l); return b; }  
private Button smallButton(String v) { Button b = button(v, false, null); b.setTextSize(11); return b; }  
private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

}

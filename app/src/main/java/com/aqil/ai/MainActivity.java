package com.aqil.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private TextView status;
    private EditText command;
    private EditText apiKey;
    private SpeechRecognizer recognizer;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_MEDIA_IMAGES}, 10);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (Settings.canDrawOverlays(this)) startService(new Intent(this, AqilOverlayService.class));
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundResource(R.drawable.app_background);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22), dp(30), dp(22), dp(34)); scroll.addView(root);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_aqil_logo); LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(86), dp(86)); logoLp.gravity = Gravity.CENTER_HORIZONTAL; root.addView(logo, logoLp);
        TextView eyebrow = text("PRIVATE GEMINI-STYLE PHONE AGENT", 12, R.color.aqil_gold, Typeface.BOLD); eyebrow.setGravity(Gravity.CENTER); eyebrow.setLetterSpacing(0.16f); root.addView(eyebrow);
        TextView title = text("AQIL AI", 42, R.color.aqil_text, Typeface.BOLD); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView subtitle = text("Voice, keyboard, overlay, accessibility actions, and optional OpenAI-powered reasoning.", 16, R.color.aqil_muted, Typeface.NORMAL); subtitle.setGravity(Gravity.CENTER); subtitle.setPadding(0, 0, 0, dp(18)); root.addView(subtitle);

        LinearLayout setup = panel(); setup.addView(text("1. Connect permissions", 22, R.color.aqil_text, Typeface.BOLD));
        setup.addView(text("Accessibility controls taps, scrolls, back/home, and visible screen actions. Overlay shows the floating AQIL circle over every app.", 15, R.color.aqil_muted, Typeface.NORMAL));
        Button accessibility = button("Open Accessibility Settings", true); accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); setup.addView(accessibility);
        Button overlay = button("Enable / Start Floating Circle", false); overlay.setOnClickListener(v -> enableOverlay()); setup.addView(overlay); root.addView(setup);

        LinearLayout ai = panel(); ai.addView(text("2. Add AI brain", 22, R.color.aqil_text, Typeface.BOLD));
        ai.addView(text("Paste your OpenAI API key here. It stays in this app's private storage and unlocks smarter command reasoning.", 15, R.color.aqil_muted, Typeface.NORMAL));
        apiKey = input("OpenAI API key"); apiKey.setText(AgentBrain.getApiKey(this)); ai.addView(apiKey);
        Button save = button("Save API Key", true); save.setOnClickListener(v -> { AgentBrain.saveApiKey(this, apiKey.getText().toString()); say("API key saved. AQIL can now use AI reasoning."); }); ai.addView(save); root.addView(ai);

        LinearLayout console = panel(); console.addView(text("3. Command AQIL", 22, R.color.aqil_text, Typeface.BOLD));
        command = input("Type: play YouTube lo-fi, scroll down, fetch image community certificate..."); console.addView(command);
        Button run = button("Run Typed Command", true); run.setOnClickListener(v -> runCommand(command.getText().toString())); console.addView(run);
        Button mic = button("Speak Command", false); mic.setOnClickListener(v -> listen()); console.addView(mic);
        status = text("Ready. The floating circle also has mic + keyboard controls.", 15, R.color.aqil_gold, Typeface.BOLD); console.addView(status); root.addView(console);
        setContentView(scroll);
    }

    private void enableOverlay() {
        if (!Settings.canDrawOverlays(this)) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        else { startService(new Intent(this, AqilOverlayService.class)); say("Floating AQIL circle started."); }
    }

    private void runCommand(String text) {
        if (text == null || text.trim().isEmpty()) { say("Type or speak a command first."); return; }
        AqilAccessibilityService.runCommand(text);
        AgentBrain.ask(this, text, answer -> runOnUiThread(() -> say(answer)));
    }

    private void listen() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { say("Listening..."); } public void onBeginningOfSpeech() {} public void onRmsChanged(float rmsdB) {} public void onBufferReceived(byte[] buffer) {} public void onEndOfSpeech() {} public void onError(int error) { say("Mic error: " + error); }
            public void onResults(Bundle results) { ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (r != null && !r.isEmpty()) { command.setText(r.get(0)); runCommand(r.get(0)); } }
            public void onPartialResults(Bundle partialResults) {} public void onEvent(int eventType, Bundle params) {}
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); recognizer.startListening(i);
    }

    private LinearLayout panel() { LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setBackgroundResource(R.drawable.panel_card); p.setPadding(dp(20), dp(18), dp(20), dp(18)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, dp(18)); p.setLayoutParams(lp); return p; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextColor(getColor(R.color.aqil_text)); e.setHintTextColor(getColor(R.color.aqil_muted)); e.setSingleLine(false); e.setMinLines(1); e.setBackgroundResource(R.drawable.input_field); e.setPadding(dp(14), dp(12), dp(14), dp(12)); return e; }
    private TextView text(String value, int sp, int color, int style) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(getColor(color)); v.setTypeface(Typeface.create(Typeface.SERIF, style)); v.setLineSpacing(0, 1.12f); v.setPadding(0, dp(5), 0, dp(7)); return v; }
    private Button button(String value, boolean primary) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setTextColor(getColor(primary ? R.color.aqil_bg : R.color.aqil_text)); b.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD)); b.setTextSize(16); b.setBackgroundResource(primary ? R.drawable.primary_button : R.drawable.secondary_button); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); lp.setMargins(0, dp(12), 0, 0); b.setLayoutParams(lp); return b; }
    private void say(String message) { if (status != null) status.setText(message); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}

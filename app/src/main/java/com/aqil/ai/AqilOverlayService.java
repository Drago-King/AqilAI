package com.aqil.ai;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.IBinder;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.ArrayList;

public class AqilOverlayService extends Service {
    private WindowManager wm; private LinearLayout bubble; private long downAt; private SpeechRecognizer recognizer; private EditText keyboard;
    @Override public void onCreate() { super.onCreate(); if (Settings.canDrawOverlays(this)) showBubble(); }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void showBubble() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubble = new LinearLayout(this); bubble.setOrientation(LinearLayout.VERTICAL); bubble.setGravity(Gravity.CENTER); bubble.setBackgroundResource(com.aqil.ai.R.drawable.overlay_circle); bubble.setPadding(8, 8, 8, 8);
        Button aqil = new Button(this); aqil.setText("AQIL"); aqil.setAllCaps(false); aqil.setTextColor(getColor(R.color.aqil_bg)); aqil.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD)); aqil.setBackgroundColor(0x00000000); bubble.addView(aqil);
        keyboard = new EditText(this); keyboard.setHint("Ask AQIL anything"); keyboard.setTextColor(getColor(R.color.aqil_text)); keyboard.setHintTextColor(getColor(R.color.aqil_muted)); keyboard.setSingleLine(false); keyboard.setMinLines(1); keyboard.setBackgroundResource(R.drawable.input_field); keyboard.setVisibility(View.GONE); bubble.addView(keyboard);
        Button mic = new Button(this); mic.setText("Speak to AQIL"); mic.setAllCaps(false); mic.setTextColor(getColor(R.color.aqil_text)); mic.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD)); mic.setVisibility(View.GONE); mic.setBackgroundResource(R.drawable.secondary_button); bubble.addView(mic);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT); lp.gravity = Gravity.TOP | Gravity.START; lp.x = 24; lp.y = 300;
        bubble.setOnTouchListener((v,e) -> { if (e.getAction()==MotionEvent.ACTION_DOWN) downAt=System.currentTimeMillis(); if (e.getAction()==MotionEvent.ACTION_UP && System.currentTimeMillis()-downAt>650) { Intent i = new Intent(this, MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } return false; });
        aqil.setOnClickListener(v -> { int next = mic.getVisibility()==View.VISIBLE ? View.GONE : View.VISIBLE; bubble.setBackgroundResource(next == View.VISIBLE ? R.drawable.overlay_panel : R.drawable.overlay_circle); mic.setVisibility(next); keyboard.setVisibility(next); if (next == View.VISIBLE) { keyboard.requestFocus(); ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(keyboard, InputMethodManager.SHOW_IMPLICIT); } });
        mic.setOnClickListener(v -> listen());
        keyboard.setOnEditorActionListener((v, actionId, event) -> { runSmart(v.getText().toString()); return true; });
        wm.addView(bubble, lp);
    }

    private void listen() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) {} public void onBeginningOfSpeech() {} public void onRmsChanged(float rmsdB) {} public void onBufferReceived(byte[] buffer) {} public void onEndOfSpeech() {} public void onError(int error) {}
            public void onResults(Bundle results) { ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (r != null && !r.isEmpty()) runSmart(r.get(0)); }
            public void onPartialResults(Bundle partialResults) {} public void onEvent(int eventType, Bundle params) {}
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to AQIL"); recognizer.startListening(i);
    }

    private void runSmart(String text) {
        AqilAccessibilityService.runCommand(text);
        AgentBrain.ask(this, text, answer -> { if (keyboard != null) keyboard.post(() -> keyboard.setText(answer)); });
    }

    @Override public void onDestroy() { if (bubble != null) wm.removeView(bubble); if (recognizer != null) recognizer.destroy(); super.onDestroy(); }
}

package com.aqil.ai;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.ArrayList;

public class AqilOverlayService extends Service {
private WindowManager wm; private LinearLayout bubble; private WindowManager.LayoutParams lp; private SpeechRecognizer recognizer; private EditText keyboard;
private int startX, startY; private float touchX, touchY; private long downAt;
@Override public void onCreate() { super.onCreate(); if (Settings.canDrawOverlays(this)) showBubble(); }
@Override public int onStartCommand(Intent intent, int flags, int startId) { if (bubble == null && Settings.canDrawOverlays(this)) showBubble(); return START_STICKY; }
@Override public IBinder onBind(Intent intent) { return null; }

private void showBubble() {  
    if (bubble != null) return;  
    wm = (WindowManager) getSystemService(WINDOW_SERVICE);  
    bubble = new LinearLayout(this); bubble.setOrientation(LinearLayout.VERTICAL); bubble.setGravity(Gravity.CENTER); bubble.setBackgroundResource(R.drawable.overlay_circle); bubble.setPadding(10, 10, 10, 10);  
    Button aqil = new Button(this); aqil.setText("AQIL"); aqil.setAllCaps(false); aqil.setTextColor(getColor(R.color.aqil_bg)); aqil.setTypeface(Typeface.DEFAULT_BOLD); aqil.setBackgroundColor(0x00000000); bubble.addView(aqil);  
    keyboard = new EditText(this); keyboard.setHint("Ask AQIL anything"); keyboard.setTextColor(getColor(R.color.aqil_text)); keyboard.setHintTextColor(getColor(R.color.aqil_muted)); keyboard.setSingleLine(false); keyboard.setMinLines(1); keyboard.setBackgroundResource(R.drawable.input_field); keyboard.setVisibility(View.GONE); bubble.addView(keyboard);  
    Button mic = new Button(this); mic.setText("Speak"); mic.setAllCaps(false); mic.setTextColor(getColor(R.color.aqil_text)); mic.setTypeface(Typeface.DEFAULT_BOLD); mic.setVisibility(View.GONE); mic.setBackgroundResource(R.drawable.secondary_button); bubble.addView(mic);  
    lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);  
    lp.gravity = Gravity.TOP | Gravity.START; lp.x = AgentBrain.prefs(this).getInt("bubble_x", 24); lp.y = AgentBrain.prefs(this).getInt("bubble_y", 300);  
    bubble.setOnTouchListener((v,e) -> handleTouch(e));  
    aqil.setOnClickListener(v -> toggle(mic)); mic.setOnClickListener(v -> listen()); keyboard.setOnEditorActionListener((v, a, event) -> { runSmart(v.getText().toString()); return true; });  
    wm.addView(bubble, lp);  
}  

private boolean handleTouch(MotionEvent e) {  
    if (e.getAction() == MotionEvent.ACTION_DOWN) { downAt = System.currentTimeMillis(); startX = lp.x; startY = lp.y; touchX = e.getRawX(); touchY = e.getRawY(); return false; }  
    if (e.getAction() == MotionEvent.ACTION_MOVE) { lp.x = startX + (int)(e.getRawX() - touchX); lp.y = startY + (int)(e.getRawY() - touchY); wm.updateViewLayout(bubble, lp); return true; }  
    if (e.getAction() == MotionEvent.ACTION_UP) { AgentBrain.prefs(this).edit().putInt("bubble_x", lp.x).putInt("bubble_y", lp.y).apply(); if (System.currentTimeMillis() - downAt > 650) { Intent i = new Intent(this, MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true; } }  
    return false;  
}  

private void toggle(Button mic) {  
    int next = mic.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;  
    bubble.setBackgroundResource(next == View.VISIBLE ? R.drawable.overlay_panel : R.drawable.overlay_circle); mic.setVisibility(next); keyboard.setVisibility(next);  
    if (next == View.VISIBLE) { keyboard.requestFocus(); ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(keyboard, InputMethodManager.SHOW_IMPLICIT); }  
}  

private void listen() {  
    recognizer = SpeechRecognizer.createSpeechRecognizer(this);  
    recognizer.setRecognitionListener(new RecognitionListener() { public void onReadyForSpeech(Bundle p){} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onError(int e){} public void onResults(Bundle r){ ArrayList<String> a = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(a!=null&&!a.isEmpty()) runSmart(a.get(0)); } public void onPartialResults(Bundle p){} public void onEvent(int t, Bundle p){} });  
    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); recognizer.startListening(i);  
}  
private void runSmart(String text) { AqilAccessibilityService.runCommand(text); AgentBrain.ask(this, text, answer -> { if (keyboard != null) keyboard.post(() -> keyboard.setText(answer)); }); }  
@Override public void onDestroy() { if (bubble != null) wm.removeView(bubble); if (recognizer != null) recognizer.destroy(); bubble = null; super.onDestroy(); }

}

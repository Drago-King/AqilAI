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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

public class AqilOverlayService extends Service {
    private WindowManager wm;
    private FrameLayout root; // small circular bubble, always present
    private LinearLayout panel; // expanded panel, toggled visible
    private TextView icon;
    private EditText keyboard;
    private WindowManager.LayoutParams lp;
    private SpeechRecognizer recognizer;

    private static final int BUBBLE_SIZE_DP = 52;
    private static final int TAP_SLOP_DP = 12;
    private static final long LONG_PRESS_MS = 550;

    private float downRawX, downRawY; private int downX, downY; private long downAt; private boolean dragged;

    @Override public void onCreate() { super.onCreate(); if (Settings.canDrawOverlays(this)) show(); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { if (root == null && Settings.canDrawOverlays(this)) show(); return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void show() {
        if (root != null) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        root = new FrameLayout(this);
        int size = dp(BUBBLE_SIZE_DP);
        icon = new TextView(this);
        icon.setText("A");
        icon.setTextColor(getColor(R.color.aqil_bg));
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setTextSize(20);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundResource(R.drawable.overlay_circle);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(size, size);
        root.addView(icon, iconLp);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.overlay_panel);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setVisibility(View.GONE);

        keyboard = new EditText(this);
        keyboard.setHint("Ask AQIL anything");
        keyboard.setTextColor(getColor(R.color.aqil_text));
        keyboard.setHintTextColor(getColor(R.color.aqil_muted));
        keyboard.setMinWidth(dp(220));
        keyboard.setBackgroundResource(R.drawable.input_field);
        panel.addView(keyboard);

        TextView micRow = new TextView(this);
        micRow.setText("Tap to speak");
        micRow.setTextColor(getColor(R.color.aqil_muted));
        micRow.setPadding(0, dp(8), 0, 0);
        micRow.setOnClickListener(v -> listen());
        panel.addView(micRow);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.leftMargin = size + dp(8);
        root.addView(panel, panelLp);

        keyboard.setOnEditorActionListener((v, a, event) -> { runSmart(v.getText().toString()); return true; });

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = AgentBrain.prefs(this).getInt("bubble_x", 24);
        lp.y = AgentBrain.prefs(this).getInt("bubble_y", 300);

        icon.setOnTouchListener(this::handleTouch);
        wm.addView(root, lp);
    }

    private boolean handleTouch(View v, MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downAt = System.currentTimeMillis();
                downX = lp.x; downY = lp.y;
                downRawX = e.getRawX(); downRawY = e.getRawY();
                dragged = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                int dx = (int) (e.getRawX() - downRawX);
                int dy = (int) (e.getRawY() - downRawY);
                if (!dragged && (Math.abs(dx) > dp(TAP_SLOP_DP) || Math.abs(dy) > dp(TAP_SLOP_DP))) dragged = true;
                if (dragged) {
                    lp.x = downX + dx; lp.y = downY + dy;
                    wm.updateViewLayout(root, lp);
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (dragged) {
                    AgentBrain.prefs(this).edit().putInt("bubble_x", lp.x).putInt("bubble_y", lp.y).apply();
                } else if (System.currentTimeMillis() - downAt > LONG_PRESS_MS) {
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } else {
                    togglePanel();
                }
                return true;
            }
        }
        return false;
    }

    private void togglePanel() {
        boolean showing = panel.getVisibility() == View.VISIBLE;
        panel.setVisibility(showing ? View.GONE : View.VISIBLE);
        lp.flags = showing
                ? (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                : WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        wm.updateViewLayout(root, lp);
        if (!showing) {
            keyboard.requestFocus();
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(keyboard, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void listen() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) {}
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float r) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() {}
            public void onError(int e) {}
            public void onResults(Bundle r) {
                ArrayList<String> a = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (a != null && !a.isEmpty()) runSmart(a.get(0));
            }
            public void onPartialResults(Bundle p) {}
            public void onEvent(int t, Bundle p) {}
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizer.startListening(i);
    }

    private void runSmart(String text) {
        AgentBrain.ask(this, text, answer -> { if (keyboard != null) keyboard.post(() -> keyboard.setText(answer)); });
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onDestroy() {
        if (root != null) wm.removeView(root);
        if (recognizer != null) recognizer.destroy();
        root = null;
        super.onDestroy();
    }
}

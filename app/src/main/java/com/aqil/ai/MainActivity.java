package com.aqil.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_MEDIA_IMAGES}, 10);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.app_background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(34), dp(22), dp(34));
        scroll.addView(root);

        TextView eyebrow = text("PRIVATE PHONE AGENT", 13, R.color.aqil_gold, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.18f);
        root.addView(eyebrow);

        TextView title = text("AQIL AI", 42, R.color.aqil_text, Typeface.BOLD);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        root.addView(title);

        TextView subtitle = text("Classic dark command center for hands-free phone control.", 17, R.color.aqil_muted, Typeface.NORMAL);
        subtitle.setPadding(0, 0, 0, dp(22));
        root.addView(subtitle);

        LinearLayout card = panel();
        card.addView(text("Setup", 22, R.color.aqil_text, Typeface.BOLD));
        card.addView(text("Enable Accessibility so AQIL can read screens, tap, go back, go home, and scroll. Then enable the polished floating circle for mic and keyboard commands.", 15, R.color.aqil_muted, Typeface.NORMAL));
        Button accessibility = button("Enable Accessibility", true);
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        card.addView(accessibility);
        Button overlay = button("Enable Floating Circle", false);
        overlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            else startService(new Intent(this, AqilOverlayService.class));
        });
        card.addView(overlay);
        root.addView(card);

        LinearLayout examples = panel();
        examples.addView(text("Try saying", 22, R.color.aqil_text, Typeface.BOLD));
        examples.addView(text("• Open YouTube\n• Play relaxing music\n• Scroll down\n• Go back\n• Fetch image community certificate\n• Order food near me", 16, R.color.aqil_muted, Typeface.NORMAL));
        root.addView(examples);
        setContentView(scroll);
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.panel_card);
        panel.setPadding(dp(20), dp(18), dp(20), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(18));
        panel.setLayoutParams(lp);
        return panel;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(getColor(color));
        view.setTypeface(Typeface.create(Typeface.SERIF, style));
        view.setLineSpacing(0, 1.12f);
        view.setPadding(0, dp(5), 0, dp(7));
        return view;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(getColor(primary ? R.color.aqil_bg : R.color.aqil_text));
        button.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        button.setTextSize(16);
        button.setBackgroundResource(primary ? R.drawable.primary_button : R.drawable.secondary_button);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.setMargins(0, dp(12), 0, 0);
        button.setLayoutParams(lp);
        return button;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}

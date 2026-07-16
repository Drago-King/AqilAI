package com.aqil.ai;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** Small "Cancel task" pill shown at the top of the screen only while an automated
 *  flow (like the WhatsApp send flow or the screen agent) is actively running.
 *  Tapping it aborts the in-progress task. Hidden the rest of the time.
 *  Styled in a deliberately different, alert-like color (not the app's usual gold/purple)
 *  so it reads as "something is running, tap to stop" rather than blending into any app. */
public class TaskCancelBar {
    private static View barView;
    private static WindowManager wm;

    public interface CancelHandler { void onCancel(); }

    public static void show(Context context, String label, CancelHandler handler) {
        hide(); // never stack two bars
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        TextView bar = new TextView(context);
        bar.setText((label == null ? "AQIL is working" : label) + "   \u2715  Tap to cancel");
        bar.setTextColor(Color.WHITE);
        bar.setTypeface(Typeface.DEFAULT_BOLD);
        bar.setTextSize(13);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E53935")); // attention red -- deliberately not the app's gold/purple
        bg.setCornerRadius(dp(context, 22));
        bg.setStroke(dp(context, 1), Color.parseColor("#FFFFFF"));
        bar.setBackground(bg);

        int padH = dp(context, 18), padV = dp(context, 11);
        bar.setPadding(padH, padV, padH, padV);
        bar.setElevation(dp(context, 8));
        bar.setOnClickListener(v -> { if (handler != null) handler.onCancel(); hide(); });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(context, 70); // clear of status bar / camera cutout on most phones

        try {
            wm.addView(bar, lp);
            barView = bar;
        } catch (Exception e) {
            // Overlay permission missing or WindowManager rejected it -- surface this rather
            // than silently doing nothing, since a task then has no visible cancel affordance.
            AgentBrain.addHistory(context, "AQIL: Couldn't show the cancel button (" + e.getMessage() + ") -- check overlay permission in Permissions.");
        }
    }

    public static void hide() {
        if (barView != null && wm != null) {
            try { wm.removeView(barView); } catch (Exception ignored) { }
        }
        barView = null;
    }

    private static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
}

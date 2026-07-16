package com.aqil.ai;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** Small "Cancel task" pill shown at the top of the screen only while an automated
 *  flow (like the WhatsApp send flow) is actively running. Tapping it aborts the
 *  in-progress task. Hidden the rest of the time. */
public class TaskCancelBar {
    private static View barView;
    private static WindowManager wm;

    public interface CancelHandler { void onCancel(); }

    public static void show(Context context, String label, CancelHandler handler) {
        hide(); // never stack two bars
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        TextView bar = new TextView(context);
        bar.setText((label == null ? "Task running" : label) + "  \u2715 Cancel");
        bar.setTextColor(context.getColor(R.color.aqil_text));
        bar.setBackgroundResource(R.drawable.overlay_panel);
        int padH = dp(context, 16), padV = dp(context, 10);
        bar.setPadding(padH, padV, padH, padV);
        bar.setTextSize(13);
        bar.setOnClickListener(v -> { if (handler != null) handler.onCancel(); hide(); });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(context, 36);

        try { wm.addView(bar, lp); barView = bar; } catch (Exception ignored) { }
    }

    public static void hide() {
        if (barView != null && wm != null) {
            try { wm.removeView(barView); } catch (Exception ignored) { }
        }
        barView = null;
    }

    private static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
}

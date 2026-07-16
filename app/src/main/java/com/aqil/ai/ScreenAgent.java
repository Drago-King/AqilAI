package com.aqil.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * General-purpose "look at the screen and figure out what to tap" loop -- built to actually
 * DIAGNOSE failures rather than just retrying blindly and hoping a weak model notices on its own:
 *
 *  - Every action is tracked by a stable key (its label), not just a step number. If the exact
 *    same action fails to change anything twice, it's hard-blocked from being tried a third time --
 *    deterministically, in code, regardless of whether the AI "notices."
 *  - Failed actions are marked "[AVOID -- already failed]" directly in the next screen listing,
 *    so the warning is impossible to miss (not buried in a wall of history text).
 *  - Leaving the target app entirely (e.g. an accidental back landing on the home screen) is
 *    detected and called out explicitly, instead of silently continuing on the wrong app.
 *  - Status messages describe what actually happened and what's being checked next, not a bare
 *    "Thinking about step N" with no content.
 *  - Real coordinate-based gesture taps (dispatchGesture) PLUS a redundant ACTION_CLICK, since
 *    different widget types respond to different mechanisms and there's no cost to trying both.
 */
public class ScreenAgent {
    public interface Callback { void onStatus(String status); void onDone(); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_STEPS = 18;
    private static final int MAX_FAILS_PER_ACTION = 2; // 3rd identical failure is hard-blocked
    private static final long MIN_SETTLE_MS = 450;
    private static final long MAX_SETTLE_MS = 3000;
    private static final long CHANGE_POLL_MS = 200;
    private static volatile boolean cancelRequested = false;
    private static volatile boolean isRunning = false;
    private static Map<String, Integer> failCounts = new HashMap<>();
    private static String startPackage = null;

    private static final String AGENT_SYSTEM =
        "You control an Android phone one step at a time by choosing a numbered element from the " +
        "current screen. You will be given: the app currently open, a numbered list of tappable/typeable " +
        "elements (some marked [AVOID -- already failed], never choose those), the user's goal, and a " +
        "diagnostic log of what happened after each of your previous actions. " +
        "Reply with ONLY JSON, no other text, no markdown fences: " +
        "{\"action\":\"tap\"|\"type\"|\"scroll_down\"|\"scroll_up\"|\"back\"|\"done\"|\"give_up\"," +
        "\"index\":<number, only for tap/type>,\"text\":\"<only for type>\",\"reason\":\"<one short phrase " +
        "explaining your diagnosis, e.g. 'previous tap had no effect, trying the item below it instead'>\"}. " +
        "Use \"done\" only once the CURRENT screen elements clearly show the goal achieved -- not just that " +
        "you tapped something that seemed promising. If the log shows your last action had no effect, do " +
        "not repeat it -- diagnose why (wrong element? needs scrolling into view? screen still loading?) and " +
        "act on that diagnosis. If you ended up somewhere unexpected, use \"back\" and pick a different path " +
        "before giving up. Only use \"give_up\" if multiple different approaches have all failed. Never " +
        "invent an index that wasn't in the list, and never choose one marked [AVOID].";

    public static void runTask(AccessibilityService svc, Context context, String goal, Callback cb) {
        if (svc == null) { cb.onStatus("Accessibility service not connected."); cb.onDone(); return; }
        if (isRunning) { cb.onStatus("Already working on something -- cancel it first (tap the red bar) before starting another task."); cb.onDone(); return; }
        isRunning = true;
        cancelRequested = false;
        failCounts = new HashMap<>();
        AccessibilityNodeInfo root0 = svc.getRootInActiveWindow();
        startPackage = root0 != null && root0.getPackageName() != null ? root0.getPackageName().toString() : null;
        List<String> log = new ArrayList<>();
        TaskCancelBar.show(svc, "Working on: " + shorten(goal, 28), () -> cancelRequested = true);
        step(svc, context, goal, log, 0, signatureOf(svc), cb);
    }

    private static void step(AccessibilityService svc, Context context, String goal, List<String> log, int stepNum, String prevSignature, Callback cb) {
        if (cancelRequested) { finish(cb, "Cancelled."); return; }
        if (stepNum >= MAX_STEPS) { finish(cb, "Stopped after " + MAX_STEPS + " steps without finishing -- try breaking the goal into a smaller instruction."); return; }

        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) { finish(cb, "Couldn't read the screen right now."); return; }

        List<AccessibilityNodeInfo> elements = new ArrayList<>();
        collectInteractable(root, elements, 90);
        if (elements.isEmpty()) { finish(cb, "No interactable elements found on this screen."); return; }

        String appPkg = root.getPackageName() != null ? root.getPackageName().toString() : "unknown";
        boolean leftTargetApp = startPackage != null && !startPackage.equals(appPkg) && isLauncher(appPkg);

        StringBuilder listing = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            String label = describe(svc, elements.get(i));
            int fails = failCounts.getOrDefault(label, 0);
            listing.append('[').append(i).append("] ").append(label);
            if (fails >= MAX_FAILS_PER_ACTION) listing.append("  [AVOID -- already failed ").append(fails).append(" times]");
            listing.append('\n');
        }

        String logText = log.isEmpty() ? "(none yet)" : String.join("\n", log);
        String prompt = "Current app: " + appPkg + (leftTargetApp ? " (** you appear to have left the original app -- this may be a wrong turn **)" : "") +
                "\nGoal: " + goal + "\nDiagnostic log so far:\n" + logText +
                "\nCurrent screen elements:\n" + listing;

        cb.onStatus("Step " + (stepNum + 1) + ": reading the screen and deciding what to do next...");
        AgentBrain.decide(context, AGENT_SYSTEM, prompt, raw -> MAIN.post(() -> {
            if (cancelRequested) { finish(cb, "Cancelled."); return; }
            JSONObject decision = extractJson(raw);
            if (decision == null) { finish(cb, "The AI's response wasn't understandable JSON -- stopping for safety."); return; }
            String action = decision.optString("action", "give_up");
            String reason = decision.optString("reason", "");

            switch (action) {
                case "done":
                    finish(cb, "Done -- " + (reason.isEmpty() ? "goal reached." : reason));
                    return;
                case "give_up":
                    finish(cb, "Stopped -- " + (reason.isEmpty() ? "couldn't find a way forward." : reason));
                    return;
                case "scroll_down":
                case "scroll_up":
                    dispatchScroll(action.equals("scroll_down"));
                    afterAction(svc, context, goal, log, stepNum, prevSignature, action, reason, cb);
                    return;
                case "back":
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                    afterAction(svc, context, goal, log, stepNum, prevSignature, "back", reason, cb);
                    return;
                case "tap":
                case "type": {
                    int idx = decision.optInt("index", -1);
                    if (idx < 0 || idx >= elements.size()) { finish(cb, "The AI pointed at an element that doesn't exist -- stopping for safety."); return; }
                    AccessibilityNodeInfo target = elements.get(idx);
                    String label = describe(svc, target);
                    int fails = failCounts.getOrDefault(label, 0);
                    if (fails >= MAX_FAILS_PER_ACTION) {
                        finish(cb, "Stopped -- tried " + label + " " + fails + " times with no effect. This element likely isn't the right target; the task needs a different instruction.");
                        return;
                    }
                    if ("type".equals(action)) {
                        String text = decision.optString("text", "");
                        if (looksLikeParrotedMeta(text)) {
                            finish(cb, "The AI tried to type its own internal status text instead of real content -- stopping rather than typing garbage. Try again, or use a stronger model in AI Providers.");
                            return;
                        }
                        setText(svc, target, text);
                        cb.onStatus("Typing \"" + shorten(text, 30) + "\" into " + label + "...");
                        afterAction(svc, context, goal, log, stepNum, prevSignature, "type:" + label, reason, cb);
                    } else {
                        click(svc, target);
                        cb.onStatus("Tapping " + label + "...");
                        afterAction(svc, context, goal, log, stepNum, prevSignature, "tap:" + label, reason, cb);
                    }
                    return;
                }
                default:
                    finish(cb, "The AI returned an unrecognized action (\"" + action + "\") -- stopping for safety.");
            }
        }));
    }

    /** After performing an action, waits for the screen to actually change, records a clear
     *  diagnostic log line (not a bare "step N"), tracks per-action failure counts for the
     *  hard-block mechanism, and moves to the next step. */
    private static void afterAction(AccessibilityService svc, Context context, String goal, List<String> log, int stepNum, String prevSignature, String actionKey, String reason, Callback cb) {
        long start = System.currentTimeMillis();
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            long elapsed = System.currentTimeMillis() - start;
            String nowSignature = signatureOf(svc);
            boolean changed = !nowSignature.equals(prevSignature);
            if ((changed && elapsed >= MIN_SETTLE_MS) || elapsed >= MAX_SETTLE_MS) {
                if (actionKey.startsWith("tap:") || actionKey.startsWith("type:")) {
                    String label = actionKey.substring(actionKey.indexOf(':') + 1);
                    if (!changed) failCounts.merge(label, 1, Integer::sum);
                    else failCounts.remove(label); // it worked; forget any earlier near-miss on this label
                }
                String diagnosis = changed ? "screen updated as expected" : "no visible change -- this action likely did nothing";
                String entry = (stepNum + 1) + ". " + actionKey + (reason.isEmpty() ? "" : " (reasoning: " + reason + ")") + " -> " + diagnosis;
                List<String> nextLog = new ArrayList<>(log);
                nextLog.add(entry);
                if (nextLog.size() > 8) nextLog.remove(0); // keep the prompt bounded
                cb.onStatus(changed ? "That worked -- screen changed, moving to the next step." : "That tap/action had no visible effect -- re-checking the screen to try a different approach.");
                step(svc, context, goal, nextLog, stepNum + 1, nowSignature, cb);
            } else {
                MAIN.postDelayed(poll[0], CHANGE_POLL_MS);
            }
        };
        MAIN.postDelayed(poll[0], CHANGE_POLL_MS);
    }

    /** A cheap fingerprint of "what's on screen right now" -- package name plus the first
     *  handful of visible labels. Used only to detect change, not for anything semantic. */
    private static String signatureOf(AccessibilityService svc) {
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) return "empty";
        StringBuilder sb = new StringBuilder();
        sb.append(root.getPackageName()).append('|');
        List<AccessibilityNodeInfo> elements = new ArrayList<>();
        collectInteractable(root, elements, 20);
        for (AccessibilityNodeInfo n : elements) sb.append(bestLabel(n)).append(';');
        return sb.toString();
    }

    private static boolean isLauncher(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.US);
        return p.contains("launcher") || p.contains("home") || p.equals("com.sec.android.app.launcher") || p.equals("com.android.systemui");
    }

    private static void finish(Callback cb, String message) {
        isRunning = false;
        failCounts = new HashMap<>();
        startPackage = null;
        TaskCancelBar.hide();
        cb.onStatus(message);
        cb.onDone();
    }

    // ---------- screen reading ----------

    private static void collectInteractable(AccessibilityNodeInfo root, List<AccessibilityNodeInfo> out, int limit) {
        List<AccessibilityNodeInfo> stack = new ArrayList<>(); stack.add(root);
        while (!stack.isEmpty() && out.size() < limit) {
            AccessibilityNodeInfo n = stack.remove(stack.size() - 1);
            if (n == null) continue;
            if ((n.isClickable() || n.isEditable() || n.isCheckable()) && n.isVisibleToUser()) out.add(n);
            for (int i = 0; i < n.getChildCount(); i++) stack.add(n.getChild(i));
        }
    }

    /** Finds the best human-readable label for a node: its own text/content-description,
     *  or (for icon-only buttons with no label of their own) the first label found in its
     *  descendants -- this is what a plain icon button's inner ImageView often carries. */
    private static String bestLabel(AccessibilityNodeInfo n) {
        String own = ownLabel(n);
        if (own != null) return own;
        List<AccessibilityNodeInfo> stack = new ArrayList<>();
        for (int i = 0; i < n.getChildCount(); i++) stack.add(n.getChild(i));
        int scanned = 0;
        while (!stack.isEmpty() && scanned < 20) {
            AccessibilityNodeInfo c = stack.remove(0);
            if (c == null) continue;
            scanned++;
            String label = ownLabel(c);
            if (label != null) return label;
            for (int i = 0; i < c.getChildCount(); i++) stack.add(c.getChild(i));
        }
        return null;
    }

    private static String ownLabel(AccessibilityNodeInfo n) {
        CharSequence text = n.getText();
        if (text != null && text.length() > 0) return text.toString();
        CharSequence desc = n.getContentDescription();
        if (desc != null && desc.length() > 0) return desc.toString();
        return null;
    }

    private static String describe(AccessibilityService svc, AccessibilityNodeInfo n) {
        String label = bestLabel(n);
        if (label == null) label = "(unlabeled " + shortClassName(n) + ")";
        String kind = n.isEditable() ? "input" : n.isCheckable() ? "toggle" + (n.isChecked() ? " [on]" : " [off]") : "button";
        Rect r = new Rect(); n.getBoundsInScreen(r);
        String pos = positionHint(svc, r);
        return kind + " \"" + shorten(label, 40) + "\"" + (pos != null ? " (" + pos + ")" : "");
    }

    private static String shortClassName(AccessibilityNodeInfo n) {
        CharSequence cls = n.getClassName();
        if (cls == null) return "element";
        String s = cls.toString();
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    /** Rough on-screen position (top/middle/bottom, left/center/right) so the AI can reason
     *  about layout even without vision -- "bottom nav" style hints matter a lot in practice. */
    private static String positionHint(AccessibilityService svc, Rect r) {
        if (r.isEmpty()) return null;
        android.graphics.Point size = new android.graphics.Point();
        try {
            android.view.WindowManager wm = (android.view.WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getRealSize(size);
        } catch (Exception e) { return null; }
        if (size.x <= 0 || size.y <= 0) return null;
        int cx = r.centerX(), cy = r.centerY();
        String vert = cy < size.y / 3 ? "top" : cy < (size.y * 2) / 3 ? "middle" : "bottom";
        String horiz = cx < size.x / 3 ? "left" : cx < (size.x * 2) / 3 ? "center" : "right";
        return vert + "-" + horiz;
    }

    /** Catches a specific weak-model failure mode seen in practice: instead of real content,
     *  the model types back a fragment of its own status/reasoning that it saw echoed in the
     *  prompt's diagnostic log, instead of actual message/search content. */
    private static boolean looksLikeParrotedMeta(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String lower = text.toLowerCase(Locale.US);
        for (String tell : new String[]{"thinking about step", "screen updated", "no visible change", "tapping ", "typing \"", "reasoning:"})
            if (lower.contains(tell)) return true;
        return false;
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static void dispatchScroll(boolean down) {
        AqilAccessibilityService.runCommand(down ? "scroll down" : "scroll up");
    }

    /** Real coordinate-based tap AND a redundant ACTION_CLICK -- different widget types respond
     *  to different mechanisms (Compose UIs often need the real gesture; plain Views usually
     *  respond to either), and there's no real cost to firing both. */
    private static void click(AccessibilityService svc, AccessibilityNodeInfo node) {
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (!r.isEmpty() && r.width() > 0 && r.height() > 0) tapAt(svc, r.centerX(), r.centerY());
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private static void tapAt(AccessibilityService svc, int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(p, 0, 60);
        svc.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    /** Focuses the field with a real tap first (more reliable for custom/Compose text fields
     *  than ACTION_FOCUS alone), then sets the text via the accessibility action. */
    private static void setText(AccessibilityService svc, AccessibilityNodeInfo node, String value) {
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (!r.isEmpty() && r.width() > 0 && r.height() > 0) tapAt(svc, r.centerX(), r.centerY());
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private static JSONObject extractJson(String text) {
        if (text == null) return null;
        int start = text.indexOf('{'); int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try { return new JSONObject(text.substring(start, end + 1)); } catch (Exception e) { return null; }
    }
}

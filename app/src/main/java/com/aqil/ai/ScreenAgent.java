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
import java.util.List;

/**
 * General-purpose "look at the screen and figure out what to tap" loop.
 *
 * Two things make this reliable rather than just "smart-looking":
 *  1) Real coordinate-based gesture taps (dispatchGesture) as the PRIMARY tap
 *     mechanism, not the polite AccessibilityNodeInfo.ACTION_CLICK. Many modern
 *     apps (Jetpack Compose UIs especially -- YouTube, Instagram, etc.) don't
 *     reliably respond to ACTION_CLICK even though the node reports clickable=true.
 *     A synthetic touch at the node's actual on-screen coordinates is indistinguishable
 *     from a real finger tap and works far more consistently.
 *  2) Change-aware waiting: instead of a fixed delay after every action, it polls
 *     for the screen to actually change (new package, or a different set of visible
 *     labels) and moves on as soon as it does, up to a cap -- so it's both faster
 *     when things are quick and more patient when a screen is genuinely slow to load.
 */
public class ScreenAgent {
    public interface Callback { void onStatus(String status); void onDone(); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_STEPS = 18;
    private static final long MIN_SETTLE_MS = 450;
    private static final long MAX_SETTLE_MS = 3000;
    private static final long CHANGE_POLL_MS = 200;
    private static volatile boolean cancelRequested = false;

    private static final String AGENT_SYSTEM =
        "You control an Android phone one step at a time by choosing a numbered element from the " +
        "current screen. You will be given: the app currently open, a numbered list of tappable/typeable " +
        "elements with short labels and rough screen position, the user's goal, and a short history of what " +
        "you've already done and whether the screen changed afterward. " +
        "Reply with ONLY JSON, no other text, no markdown fences: " +
        "{\"action\":\"tap\"|\"type\"|\"scroll_down\"|\"scroll_up\"|\"back\"|\"done\"|\"give_up\"," +
        "\"index\":<number, only for tap/type>,\"text\":\"<only for type>\",\"reason\":\"<one short phrase>\"}. " +
        "Use \"done\" only once the CURRENT screen clearly shows the goal achieved (e.g. the right video is " +
        "actually playing, not just that you tapped something that looked promising) -- check the screen " +
        "elements before declaring done. If the current screen doesn't match what the goal implies (wrong " +
        "item opened, ended up somewhere unexpected), do not give up immediately: use \"back\" and try a " +
        "different element first. Only use \"give_up\" after that kind of correction has also failed, or the " +
        "same state repeats with no progress after 3+ tries. If the history shows the screen DID NOT change " +
        "after your last action, do not repeat the exact same tap -- try a nearby element or scroll instead. " +
        "Never invent an index that wasn't in the list. Prefer the fewest steps.";

    public static void runTask(AccessibilityService svc, Context context, String goal, Callback cb) {
        if (svc == null) { cb.onStatus("Accessibility service not connected."); cb.onDone(); return; }
        cancelRequested = false;
        List<String> history = new ArrayList<>();
        TaskCancelBar.show(svc, "Working on: " + shorten(goal, 28), () -> cancelRequested = true);
        step(svc, context, goal, history, 0, signatureOf(svc), cb);
    }

    private static void step(AccessibilityService svc, Context context, String goal, List<String> history, int stepNum, String prevSignature, Callback cb) {
        if (cancelRequested) { finish(cb, "Cancelled."); return; }
        if (stepNum >= MAX_STEPS) { finish(cb, "Stopped after " + MAX_STEPS + " steps without finishing -- try a smaller/simpler instruction."); return; }

        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) { finish(cb, "Couldn't read the screen right now."); return; }

        List<AccessibilityNodeInfo> elements = new ArrayList<>();
        collectInteractable(root, elements, 90);
        if (elements.isEmpty()) { finish(cb, "No interactable elements found on this screen."); return; }

        StringBuilder listing = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) listing.append('[').append(i).append("] ").append(describe(svc, elements.get(i))).append('\n');

        String appPkg = root.getPackageName() != null ? root.getPackageName().toString() : "unknown";
        String historyText = history.isEmpty() ? "(none yet)" : String.join("\n", history);
        String prompt = "Current app: " + appPkg + "\nGoal: " + goal + "\nSteps so far:\n" + historyText +
                "\nCurrent screen elements:\n" + listing;

        cb.onStatus("Thinking about step " + (stepNum + 1) + "...");
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
                    afterAction(svc, context, goal, history, stepNum, prevSignature, action, reason, cb);
                    return;
                case "back":
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                    afterAction(svc, context, goal, history, stepNum, prevSignature, "back", reason, cb);
                    return;
                case "tap":
                case "type": {
                    int idx = decision.optInt("index", -1);
                    if (idx < 0 || idx >= elements.size()) { finish(cb, "The AI pointed at an element that doesn't exist -- stopping for safety."); return; }
                    AccessibilityNodeInfo target = elements.get(idx);
                    String label = describe(svc, target);
                    if ("type".equals(action)) {
                        String text = decision.optString("text", "");
                        setText(svc, target, text);
                        cb.onStatus("Typing into " + label + "...");
                        afterAction(svc, context, goal, history, stepNum, prevSignature, "typed \"" + shorten(text, 24) + "\" into [" + idx + "] " + label, reason, cb);
                    } else {
                        click(svc, target);
                        cb.onStatus("Tapping " + label + "...");
                        afterAction(svc, context, goal, history, stepNum, prevSignature, "tapped [" + idx + "] " + label, reason, cb);
                    }
                    return;
                }
                default:
                    finish(cb, "The AI returned an unrecognized action (\"" + action + "\") -- stopping for safety.");
            }
        }));
    }

    /** After performing an action, wait for the screen to actually change (rather than a fixed
     *  guess-delay), record whether it did, and move to the next step. */
    private static void afterAction(AccessibilityService svc, Context context, String goal, List<String> history, int stepNum, String prevSignature, String actionDesc, String reason, Callback cb) {
        long start = System.currentTimeMillis();
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            long elapsed = System.currentTimeMillis() - start;
            String nowSignature = signatureOf(svc);
            boolean changed = !nowSignature.equals(prevSignature);
            if ((changed && elapsed >= MIN_SETTLE_MS) || elapsed >= MAX_SETTLE_MS) {
                String entry = (stepNum + 1) + ". " + actionDesc + (reason.isEmpty() ? "" : " (" + reason + ")") +
                        " -- screen " + (changed ? "changed" : "did not change");
                List<String> nextHistory = new ArrayList<>(history);
                nextHistory.add(entry);
                if (nextHistory.size() > 8) nextHistory.remove(0); // keep the prompt from growing unbounded
                step(svc, context, goal, nextHistory, stepNum + 1, nowSignature, cb);
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

    private static void finish(Callback cb, String message) {
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

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static void dispatchScroll(boolean down) {
        AqilAccessibilityService.runCommand(down ? "scroll down" : "scroll up");
    }

    /** Real coordinate-based tap -- the primary mechanism, since many modern (especially
     *  Compose-based) UIs don't reliably respond to the polite ACTION_CLICK even when the
     *  node reports clickable=true. Falls back to ACTION_CLICK only if bounds look invalid. */
    private static void click(AccessibilityService svc, AccessibilityNodeInfo node) {
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (!r.isEmpty() && r.width() > 0 && r.height() > 0) {
            tapAt(svc, r.centerX(), r.centerY());
        } else {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
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

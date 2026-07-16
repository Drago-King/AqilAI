package com.aqil.ai;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * General-purpose "look at the screen and figure out what to tap" loop.
 * Unlike WhatsAppAutomation (which is hardcoded to one app's layout), this reads
 * whatever is currently visible in ANY app, hands a short numbered list of the
 * interactable elements to the configured AI provider, asks it to pick the next
 * single action, performs that action, and repeats -- up to a step limit, or
 * until the AI reports the goal is done, or the user hits Cancel.
 *
 * This is inherently best-effort: it only knows what accessibility exposes as
 * text/content-description, so poorly-labelled apps (icon-only buttons with no
 * description) will be harder for it to work with.
 */
public class ScreenAgent {
    public interface Callback { void onStatus(String status); void onDone(); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_STEPS = 14;
    private static final long SETTLE_DELAY_MS = 900;
    private static volatile boolean cancelRequested = false;

    private static final String AGENT_SYSTEM =
        "You control an Android phone one step at a time by choosing a numbered element from the " +
        "current screen. You will be given: the app currently open, a numbered list of tappable/typeable " +
        "elements with short labels, the user's goal, and a short history of what you've already done. " +
        "Reply with ONLY JSON, no other text: " +
        "{\"action\":\"tap\"|\"type\"|\"scroll_down\"|\"scroll_up\"|\"back\"|\"done\"|\"give_up\"," +
        "\"index\":<number, only for tap/type>,\"text\":\"<only for type>\",\"reason\":\"<one short phrase>\"}. " +
        "Use \"done\" once the goal is clearly achieved. Use \"give_up\" if the same state repeats with no " +
        "progress after a few tries, or the goal seems impossible from here. Never invent an index that " +
        "wasn't in the list. Prefer the fewest steps possible.";

    public static void runTask(AccessibilityService svc, Context context, String goal, Callback cb) {
        if (svc == null) { cb.onStatus("Accessibility service not connected."); cb.onDone(); return; }
        cancelRequested = false;
        List<String> history = new ArrayList<>();
        TaskCancelBar.show(svc, "Working on: " + shorten(goal, 28), () -> cancelRequested = true);
        step(svc, context, goal, history, 0, cb);
    }

    private static void step(AccessibilityService svc, Context context, String goal, List<String> history, int stepNum, Callback cb) {
        if (cancelRequested) { finish(cb, "Cancelled."); return; }
        if (stepNum >= MAX_STEPS) { finish(cb, "Stopped after " + MAX_STEPS + " steps without finishing -- the task may need to be broken into smaller instructions."); return; }

        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) { finish(cb, "Couldn't read the screen right now."); return; }

        List<AccessibilityNodeInfo> elements = new ArrayList<>();
        collectInteractable(root, elements, 80);
        if (elements.isEmpty()) { finish(cb, "No interactable elements found on this screen."); return; }

        StringBuilder listing = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) listing.append('[').append(i).append("] ").append(describe(elements.get(i))).append('\n');

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
                    history.add((stepNum + 1) + ". " + action + (reason.isEmpty() ? "" : " (" + reason + ")"));
                    cb.onStatus("Scrolling...");
                    MAIN.postDelayed(() -> step(svc, context, goal, history, stepNum + 1, cb), SETTLE_DELAY_MS);
                    return;
                case "back":
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                    history.add((stepNum + 1) + ". back" + (reason.isEmpty() ? "" : " (" + reason + ")"));
                    MAIN.postDelayed(() -> step(svc, context, goal, history, stepNum + 1, cb), SETTLE_DELAY_MS);
                    return;
                case "tap":
                case "type": {
                    int idx = decision.optInt("index", -1);
                    if (idx < 0 || idx >= elements.size()) { finish(cb, "The AI pointed at an element that doesn't exist -- stopping for safety."); return; }
                    AccessibilityNodeInfo target = elements.get(idx);
                    if ("type".equals(action)) {
                        String text = decision.optString("text", "");
                        setText(target, text);
                        history.add((stepNum + 1) + ". typed \"" + shorten(text, 24) + "\" into [" + idx + "]" + (reason.isEmpty() ? "" : " (" + reason + ")"));
                        cb.onStatus("Typing into " + describe(target) + "...");
                    } else {
                        click(target);
                        history.add((stepNum + 1) + ". tapped [" + idx + "] " + describe(target) + (reason.isEmpty() ? "" : " (" + reason + ")"));
                        cb.onStatus("Tapping " + describe(target) + "...");
                    }
                    MAIN.postDelayed(() -> step(svc, context, goal, history, stepNum + 1, cb), SETTLE_DELAY_MS);
                    return;
                }
                default:
                    finish(cb, "The AI returned an unrecognized action (\"" + action + "\") -- stopping for safety.");
            }
        }));
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
            if ((n.isClickable() || n.isEditable()) && n.isVisibleToUser()) out.add(n);
            for (int i = 0; i < n.getChildCount(); i++) stack.add(n.getChild(i));
        }
    }

    private static String describe(AccessibilityNodeInfo n) {
        CharSequence text = n.getText();
        CharSequence desc = n.getContentDescription();
        String label = text != null && text.length() > 0 ? text.toString()
                : (desc != null && desc.length() > 0 ? desc.toString() : "(unlabeled)");
        String type = n.isEditable() ? "input" : "button";
        return type + " \"" + shorten(label, 40) + "\"";
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static void dispatchScroll(boolean down) {
        AqilAccessibilityService.runCommand(down ? "scroll down" : "scroll up");
    }

    private static void click(AccessibilityNodeInfo node) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); }

    private static void setText(AccessibilityNodeInfo node, String value) {
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

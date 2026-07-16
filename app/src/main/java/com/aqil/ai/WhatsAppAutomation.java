package com.aqil.ai;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drives WhatsApp's on-screen UI through the AccessibilityService's node tree,
 * instead of just launching the app. This is inherently fragile -- it depends on
 * matching visible text/content-descriptions, which can change between WhatsApp
 * versions. It deliberately stops short of tapping the final Send button so a
 * human always confirms before anything is actually sent.
 */
public class WhatsAppAutomation {
    public interface Callback { void onStatus(String status); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long POLL_INTERVAL_MS = 300;
    private static final long TIMEOUT_MS = 8000;
    private static volatile boolean cancelRequested = false;

    /** Opens WhatsApp, searches for contactHint, opens their chat, and then either types
     *  message text into the chat's compose field, attaches imageUri via the share sheet,
     *  or (if neither given) just leaves the chat open. Never taps Send. */
    public static void openChatAndPrepare(AccessibilityService svc, String contactHint, Uri imageUri, String message, Callback cb) {
        if (svc == null) { cb.onStatus("Accessibility service not connected."); return; }
        Intent launch = svc.getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if (launch == null) { cb.onStatus("Couldn't launch WhatsApp -- check it's installed and try again."); return; }

        cancelRequested = false;
        TaskCancelBar.show(svc, "Finding \"" + contactHint + "\" on WhatsApp", () -> cancelRequested = true);

        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        svc.startActivity(launch);

        waitFor(svc, TIMEOUT_MS, root -> findClickableByAny(root, "Search", "search"),
            searchNode -> {
                if (cancelled(cb)) return;
                click(searchNode);
                waitFor(svc, TIMEOUT_MS, root -> findEditable(root), editField -> {
                    if (cancelled(cb)) return;
                    setText(editField, contactHint);
                    waitFor(svc, TIMEOUT_MS, root -> findResultContaining(root, contactHint), resultNode -> {
                        if (cancelled(cb)) return;
                        click(resultNode);
                        cb.onStatus("Opened chat with a match for \"" + contactHint + "\".");
                        if (imageUri != null) {
                            MAIN.postDelayed(() -> { if (!cancelled(cb)) attachImageViaShare(svc, imageUri, contactHint, cb); }, 700);
                        } else if (message != null && !message.trim().isEmpty()) {
                            MAIN.postDelayed(() -> { if (!cancelled(cb)) typeMessageIntoChat(svc, message, cb); }, 700);
                        } else {
                            TaskCancelBar.hide();
                            cb.onStatus("Chat is open. Type your message and send it yourself, or ask me to type something.");
                        }
                    }, () -> { TaskCancelBar.hide(); cb.onStatus("Couldn't find a contact matching \"" + contactHint + "\" in the search results. Try a more exact name."); });
                }, () -> { TaskCancelBar.hide(); cb.onStatus("Couldn't find WhatsApp's search field -- its UI may have changed."); });
            },
            () -> { TaskCancelBar.hide(); cb.onStatus("Couldn't find WhatsApp's search button -- its UI may have changed."); });
    }

    /** Once inside a chat, finds its message-compose field and types the given text into it.
     *  Deliberately does not tap Send -- the user does that themselves. */
    private static void typeMessageIntoChat(AccessibilityService svc, String message, Callback cb) {
        waitFor(svc, TIMEOUT_MS, root -> findEditable(root), editField -> {
            if (cancelled(cb)) return;
            setText(editField, message);
            TaskCancelBar.hide();
            cb.onStatus("Typed the message into the chat -- review it and tap Send yourself when ready.");
        }, () -> { TaskCancelBar.hide(); cb.onStatus("Opened the chat but couldn't find the message box to type into -- you can type it yourself."); });
    }

    /** Checks the cancel flag; if set, reports it, hides the bar, and returns true so the
     *  caller can bail out of the current step immediately. */
    private static boolean cancelled(Callback cb) {
        if (!cancelRequested) return false;
        TaskCancelBar.hide();
        cb.onStatus("Task cancelled.");
        return true;
    }

    private static void attachImageViaShare(AccessibilityService svc, Uri imageUri, String contactHint, Callback cb) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        share.putExtra(Intent.EXTRA_STREAM, imageUri);
        share.setPackage("com.whatsapp");
        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            svc.startActivity(share);
        } catch (Exception e) {
            TaskCancelBar.hide();
            cb.onStatus("Could not open WhatsApp's share sheet: " + e.getMessage());
            return;
        }
        TaskCancelBar.hide();
        cb.onStatus("Opened WhatsApp's share screen for the image -- select \"" + contactHint + "\" and confirm to send.");
        // Deliberately not auto-selecting the contact or tapping Send on the forward screen:
        // that screen full-sends immediately, so it needs your explicit tap.
    }

    // ---------- node tree search helpers ----------

    private interface NodeFinder { AccessibilityNodeInfo find(AccessibilityNodeInfo root); }

    private static void waitFor(AccessibilityService svc, long timeoutMs, NodeFinder finder, java.util.function.Consumer<AccessibilityNodeInfo> onFound, Runnable onTimeout) {
        long start = System.currentTimeMillis();
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            if (cancelRequested) { onTimeout.run(); return; }
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            AccessibilityNodeInfo found = root != null ? finder.find(root) : null;
            if (found != null) { onFound.accept(found); return; }
            if (System.currentTimeMillis() - start > timeoutMs) { onTimeout.run(); return; }
            MAIN.postDelayed(poll[0], POLL_INTERVAL_MS);
        };
        MAIN.post(poll[0]);
    }

    private static AccessibilityNodeInfo findClickableByAny(AccessibilityNodeInfo root, String... labels) {
        List<AccessibilityNodeInfo> stack = new ArrayList<>(); stack.add(root);
        while (!stack.isEmpty()) {
            AccessibilityNodeInfo n = stack.remove(stack.size() - 1);
            if (n == null) continue;
            CharSequence desc = n.getContentDescription(); CharSequence text = n.getText();
            for (String label : labels) {
                if ((desc != null && desc.toString().toLowerCase(Locale.US).contains(label.toLowerCase(Locale.US))) ||
                    (text != null && text.toString().toLowerCase(Locale.US).contains(label.toLowerCase(Locale.US)))) {
                    AccessibilityNodeInfo clickable = nearestClickableAncestorOrSelf(n);
                    if (clickable != null) return clickable;
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) stack.add(n.getChild(i));
        }
        return null;
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> stack = new ArrayList<>(); stack.add(root);
        while (!stack.isEmpty()) {
            AccessibilityNodeInfo n = stack.remove(stack.size() - 1);
            if (n == null) continue;
            if (n.isEditable()) return n;
            for (int i = 0; i < n.getChildCount(); i++) stack.add(n.getChild(i));
        }
        return null;
    }

    private static AccessibilityNodeInfo findResultContaining(AccessibilityNodeInfo root, String hint) {
        String needle = hint.toLowerCase(Locale.US).trim();
        List<AccessibilityNodeInfo> stack = new ArrayList<>(); stack.add(root);
        while (!stack.isEmpty()) {
            AccessibilityNodeInfo n = stack.remove(stack.size() - 1);
            if (n == null) continue;
            CharSequence text = n.getText();
            if (text != null && text.toString().toLowerCase(Locale.US).contains(needle)) {
                AccessibilityNodeInfo clickable = nearestClickableAncestorOrSelf(n);
                if (clickable != null) return clickable;
            }
            for (int i = 0; i < n.getChildCount(); i++) stack.add(n.getChild(i));
        }
        return null;
    }

    private static AccessibilityNodeInfo nearestClickableAncestorOrSelf(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        int hops = 0;
        while (cur != null && hops < 6) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
            hops++;
        }
        return n; // fall back to the node itself; ACTION_CLICK often still works
    }

    private static void click(AccessibilityNodeInfo node) {
        if (node != null) node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private static void setText(AccessibilityNodeInfo node, String value) {
        if (node == null) return;
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }
}

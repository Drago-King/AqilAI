package com.aqil.ai;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class GallerySearchEngine {
public interface Callback { void onResult(String result); }
static class Candidate { Uri uri; String name; String ocr=""; String labels=""; Candidate(Uri u, String n){uri=u;name=n;} }

public static void search(Context context, String query, Callback cb) {  
    new Thread(() -> {  
        ArrayList<Candidate> candidates = load(context, query, 30);  
        if (candidates.isEmpty()) { cb.onResult("No gallery candidates found for: " + query); return; }  
        AtomicInteger pending = new AtomicInteger(candidates.size());  
        for (Candidate c : candidates) analyze(context, c, () -> { if (pending.decrementAndGet() == 0) cb.onResult(rank(candidates, query)); });  
    }).start();  
}  

private static ArrayList<Candidate> load(Context c, String q, int limit) {  
    ArrayList<Candidate> out = new ArrayList<>();  
    String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME};  
    try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {  
        if (cur == null) return out;  
        int idCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID); int nameCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);  
        while (cur.moveToNext() && out.size() < limit) {  
            long id = cur.getLong(idCol); String name = cur.getString(nameCol);  
            Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));  
            out.add(new Candidate(uri, name == null ? "image" : name));  
        }  
    } catch (Exception ignored) { }  
    return out;  
}  

private static void analyze(Context context, Candidate c, Runnable done) {  
    try {  
        InputImage image = InputImage.fromFilePath(context, c.uri);  
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).addOnSuccessListener(t -> c.ocr = t.getText()).addOnCompleteListener(task -> ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS).process(image).addOnSuccessListener(labels -> { StringBuilder sb = new StringBuilder(); for (com.google.mlkit.vision.label.ImageLabel l : labels) sb.append(l.getText()).append(' '); c.labels = sb.toString(); }).addOnCompleteListener(t2 -> done.run()));  
    } catch (Exception e) { done.run(); }  
}  

private static String rank(ArrayList<Candidate> list, String query) {  
    String q = query == null ? "" : query.toLowerCase(Locale.US); Candidate best = null; int bestScore = -1;  
    StringBuilder all = new StringBuilder("Gallery search results:\n");  
    for (Candidate c : list) { int score = score(c.name, q) + score(c.ocr, q) * 3 + score(c.labels, q) * 2; if (score > bestScore) { bestScore = score; best = c; } if (score > 0) all.append("\u2022 ").append(c.name).append(" | ").append(c.uri).append("\n"); }  
    if (best == null || bestScore <= 0) return "Scanned recent images with OCR + labels but no strong match was found.";  
    return "Best match: " + best.name + "\nURI: " + best.uri + "\nOCR: " + shortText(best.ocr) + "\nLabels: " + shortText(best.labels) + "\nAsk for confirmation before sending or sharing this file.\n\n" + all;  
}  
private static int score(String text, String query) { if (text == null || query.isEmpty()) return 0; text = text.toLowerCase(Locale.US); int s=0; for(String part: query.split("\\s+")) if(part.length()>2 && text.contains(part)) s++; return s; }  
private static String shortText(String s) { if (s == null) return ""; return s.length() > 180 ? s.substring(0,180) + "..." : s; }

}

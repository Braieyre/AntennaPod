package de.danoeh.antennapod.zipimport;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ImportHistory {
    private ImportHistory() { }

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("zip-import", Context.MODE_PRIVATE);
    }

    public static synchronized void record(Context context, String id, String name, String uri,
                                           String state, JSONObject report) throws IOException, JSONException {
        File file = file(context, id);
        JSONObject previous = file.isFile() ? new JSONObject(SeriesStore.readText(file)) : new JSONObject();
        JSONObject entry = new JSONObject(report.toString());
        entry.put("id", id);
        entry.put("name", name == null ? "" : name);
        entry.put("sourceUri", uri == null ? "" : uri);
        entry.put("state", state);
        entry.put("createdAt", previous.optLong("createdAt", System.currentTimeMillis()));
        entry.put("sourceState", previous.optString("sourceState", uri == null || uri.isEmpty() ? "none" : "exists"));
        SeriesStore.writeJson(file, entry);
    }

    public static synchronized List<JSONObject> list(Context context) throws IOException, JSONException {
        File directory = new File(SeriesStore.directory(context), "import-history");
        File legacy = new File(SeriesStore.directory(context), "last-report.json");
        if (!preferences(context).getBoolean("history_migrated", false)) {
            File[] recorded = directory.listFiles(file -> file.getName().endsWith(".json"));
            if (legacy.isFile() && (recorded == null || recorded.length == 0)) {
                JSONObject report = new JSONObject(SeriesStore.readText(legacy));
                record(context, "legacy", "", report.optString("sourceUri"), "complete", report);
            }
            preferences(context).edit().putBoolean("history_migrated", true).commit();
        }
        List<JSONObject> result = new ArrayList<>();
        File[] files = directory.listFiles(file -> file.getName().endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try {
                    result.add(new JSONObject(SeriesStore.readText(file)));
                } catch (IOException | JSONException error) {
                    continue;
                }
            }
        }
        result.sort((a, b) -> Long.compare(b.optLong("createdAt"), a.optLong("createdAt")));
        return result;
    }

    public static boolean consumeCompletion(Context context, String id) {
        SharedPreferences prefs = preferences(context);
        synchronized (ImportHistory.class) {
            boolean current = id.equals(prefs.getString("active_work", null));
            if (!current) {
                return false;
            }
            boolean consumed = prefs.getBoolean("consumed_" + id, false);
            SharedPreferences.Editor editor = prefs.edit().putBoolean("consumed_" + id, true);
            if (current) {
                editor.remove("active_work");
            }
            editor.commit();
            return current && !consumed;
        }
    }

    public static synchronized String inspectSource(Context context, JSONObject entry) throws IOException, JSONException {
        String state = entry.optString("sourceState", "none");
        if (state.equals("deleted") || state.equals("missing") || state.equals("none")) {
            return state;
        }
        String uri = entry.optString("sourceUri");
        if (uri.isEmpty()) {
            return updateSource(context, entry, "none");
        }
        try (Cursor cursor = context.getContentResolver().query(Uri.parse(uri),
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor == null) {
                return updateSource(context, entry, "unavailable");
            }
            if (!cursor.moveToFirst()) {
                return updateSource(context, entry, "missing");
            }
            try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(Uri.parse(uri), "r")) {
                if (descriptor == null) {
                    return updateSource(context, entry, "unavailable");
                }
            } catch (FileNotFoundException error) {
                return updateSource(context, entry, "missing");
            } catch (IOException error) {
                return updateSource(context, entry, "unavailable");
            }
            return state.equals("error") ? "error" : updateSource(context, entry, "exists");
        } catch (SecurityException error) {
            return updateSource(context, entry, "unavailable");
        } catch (RuntimeException error) {
            if (error.getCause() instanceof FileNotFoundException) {
                return updateSource(context, entry, "missing");
            }
            return updateSource(context, entry, "unavailable");
        }
    }

    public static synchronized String deleteSource(Context context, JSONObject entry) throws IOException, JSONException {
        String state = inspectSource(context, entry);
        if (state.equals("deleted") || state.equals("missing") || state.equals("none")) {
            return state;
        }
        try {
            if (DocumentsContract.deleteDocument(context.getContentResolver(), Uri.parse(entry.getString("sourceUri")))) {
                return updateSource(context, entry, "deleted");
            }
        } catch (FileNotFoundException error) {
            return updateSource(context, entry, "missing");
        } catch (SecurityException | IllegalArgumentException error) {
            return updateSource(context, entry, "error");
        }
        return updateSource(context, entry, "error");
    }

    private static String updateSource(Context context, JSONObject entry, String state) throws IOException, JSONException {
        entry.put("sourceState", state);
        SeriesStore.writeJson(file(context, entry.getString("id")), entry);
        return state;
    }

    private static File file(Context context, String id) {
        if (!id.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException(id);
        }
        return new File(new File(SeriesStore.directory(context), "import-history"), id + ".json");
    }
}

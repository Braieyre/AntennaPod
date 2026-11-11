package de.danoeh.antennapod.zipimport;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ZipImportWorker extends Worker {
    public static final String WORK_NAME = "zip-series-import";
    public static final String URI = "uri";
    public static final String NAME = "name";
    public static final String STAGE = "stage";
    public static final String CURRENT = "current";
    public static final String TOTAL = "total";
    public static final String IMPORTED = "imported";
    public static final String ERROR = "error";

    public ZipImportWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        File archive = new File(SeriesStore.directory(context), "incoming-" + getId() + ".zip");
        try {
            File parent = archive.getParentFile();
            if (parent == null || (!parent.mkdirs() && !parent.isDirectory())) {
                throw new IOException(archive.toString());
            }
            ImportHistory.list(context);
            String source = getInputData().getString(URI);
            if (source == null) {
                throw new IOException();
            }
            setProgressAsync(new Data.Builder().putString(STAGE, "copy").build());
            try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(source));
                 FileOutputStream output = new FileOutputStream(archive)) {
                if (input == null) {
                    throw new IOException(source);
                }
                byte[] bytes = new byte[64 * 1024];
                int read;
                long copied = 0;
                while ((read = input.read(bytes)) != -1) {
                    ZipArchiveScanner.checkCancelled(this::isStopped);
                    copied += read;
                    if (copied > ZipArchiveScanner.MAX_ARCHIVE_BYTES) {
                        throw new IOException(ZipArchiveScanner.Problem.LIMIT.name());
                    }
                    output.write(bytes, 0, read);
                }
            }
            String name = getInputData().getString(NAME);
            ZipSeriesImporter.Report report = ZipSeriesImporter.importArchive(context, archive,
                    name == null ? archive.getName() : name,
                    (stage, current, total, imported) -> setProgressAsync(new Data.Builder()
                            .putString(STAGE, stage).putInt(CURRENT, current).putInt(TOTAL, total)
                            .putInt(IMPORTED, imported).build()), this::isStopped);
            File reportFile = new File(SeriesStore.directory(context), "last-report.json");
            JSONObject reportJson = new JSONObject(SeriesStore.readText(reportFile));
            reportJson.put("sourceUri", source);
            SeriesStore.writeJson(reportFile, reportJson);
            ImportHistory.record(context, getId().toString(), name, source, "complete", reportJson);
            return Result.success(new Data.Builder().putInt(IMPORTED, report.imported())
                    .putLong("feedId", report.feedId()).build());
        } catch (Exception error) {
            String message = context.getString(R.string.zip_failed, error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
            try {
                JSONObject json = new JSONObject();
                json.put("imported", 0);
                json.put("issues", new JSONArray().put(message));
                SeriesStore.writeJson(new File(SeriesStore.directory(context), "last-report.json"), json);
                ImportHistory.record(context, getId().toString(), getInputData().getString(NAME),
                        getInputData().getString(URI), isStopped() ? "cancelled" : "failed", json);
            } catch (Exception ignored) {
                return Result.failure(new Data.Builder().putString(ERROR, message.substring(0,
                        Math.min(message.length(), 1500))).build());
            }
            return Result.failure(new Data.Builder().putString(ERROR, message.substring(0,
                    Math.min(message.length(), 1500))).build());
        } finally {
            if (archive.exists() && !archive.delete()) {
                archive.deleteOnExit();
            }
        }
    }
}

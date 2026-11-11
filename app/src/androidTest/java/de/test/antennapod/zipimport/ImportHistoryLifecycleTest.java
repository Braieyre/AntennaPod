package de.test.antennapod.zipimport;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.zipimport.ImportHistory;
import de.danoeh.antennapod.zipimport.SeriesStore;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.UUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ImportHistoryLifecycleTest {
    @Test
    public void completionIsCurrentTaskOnlyAndPersistentlyConsumed() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id = UUID.randomUUID().toString();
        ImportHistory.preferences(context).edit().putString("active_work", id).commit();
        assertFalse(ImportHistory.consumeCompletion(context, "historical-task"));
        assertEquals(id, ImportHistory.preferences(context).getString("active_work", ""));
        assertTrue(ImportHistory.consumeCompletion(context, id));
        assertFalse(ImportHistory.consumeCompletion(context, id));
        assertTrue(context.getSharedPreferences("zip-import", Context.MODE_PRIVATE).getBoolean("consumed_" + id, false));
    }

    @Test
    public void deletedAndMissingSourcesRemainTerminalOnReopenAndReimport() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (String state : new String[]{"deleted", "missing"}) {
            String id = UUID.randomUUID().toString();
            File file = new File(new File(SeriesStore.directory(context), "import-history"), id + ".json");
            try {
                JSONObject entry = new JSONObject().put("id", id).put("sourceUri", "content://absent/source")
                        .put("sourceState", state);
                SeriesStore.writeJson(file, entry);
                JSONObject reopened = new JSONObject(SeriesStore.readText(file));
                assertEquals(state, ImportHistory.inspectSource(context, reopened));
                assertEquals(state, ImportHistory.deleteSource(context, reopened));
                ImportHistory.record(context, id, "", "content://absent/source", "complete", new JSONObject());
                assertEquals(state, new JSONObject(SeriesStore.readText(file)).getString("sourceState"));
            } finally {
                file.delete();
            }
        }
    }

    @Test
    public void unsupportedSourceDeletePersistsErrorWithoutCrash() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id = UUID.randomUUID().toString();
        File file = new File(new File(SeriesStore.directory(context), "import-history"), id + ".json");
        try {
            ImportHistory.record(context, id, "", "file:///unsupported-test-only.zip", "complete", new JSONObject());
            JSONObject entry = new JSONObject(SeriesStore.readText(file));
            assertEquals("error", ImportHistory.deleteSource(context, entry));
            assertEquals("error", new JSONObject(SeriesStore.readText(file)).getString("sourceState"));
            assertEquals("error", ImportHistory.deleteSource(context, new JSONObject(SeriesStore.readText(file))));
        } finally {
            file.delete();
        }
    }

    @Test
    public void importedDateGuardPreservesTransientAndRssFeeds() {
        assertFalse(SeriesStore.isImported(null));
        Feed feed = new Feed(null, null);
        assertFalse(SeriesStore.isImported(feed));
        feed.setDownloadUrl("https://example.org/feed.xml");
        assertFalse(SeriesStore.isImported(feed));
        feed.setDownloadUrl(SeriesStore.PREFIX + "sample");
        assertTrue(SeriesStore.isImported(feed));
    }
}

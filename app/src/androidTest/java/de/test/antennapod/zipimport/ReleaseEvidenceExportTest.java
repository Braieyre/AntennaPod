package de.test.antennapod.zipimport;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.storage.database.DBReader;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ReleaseEvidenceExportTest {
    @Test
    public void exportPersistedAcceptanceAfterProcessRestart() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        assertEquals("com.homeo.mypodcasts", context.getPackageName());
        assertEquals(0, context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
        File file = new File(context.getFilesDir(), "acceptance-results.json");
        JSONObject results = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        JSONObject playback = results.getJSONObject("playback");
        var item = DBReader.getFeedItem(playback.getLong("savedItemId"));
        assertTrue(item.getMedia().getPosition() >= 20000);
        results.put("releaseNotDebuggable", true);
        results.put("positionAfterProcessRestartMs", item.getMedia().getPosition());
        Bundle status = new Bundle();
        status.putString("releaseEvidence", results.toString());
        instrumentation.sendStatus(0, status);
    }
}

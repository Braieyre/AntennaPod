package de.test.antennapod.zipimport;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ReleaseFixtureSeedTest {
    @Test
    public void installPrivateFixturesFromTestApkOnly() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        File directory = new File(instrumentation.getTargetContext().getFilesDir(), "test-fixtures");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        for (String name : List.of("kitchen-26.zip", "second-series.zip", "partial-invalid.zip",
                "corrupt.zip", "zip-slip.zip")) {
            try (var input = instrumentation.getContext().getAssets().open(name);
                    var output = new FileOutputStream(new File(directory, name))) {
                input.transferTo(output);
            }
        }
    }
}

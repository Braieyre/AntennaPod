package de.danoeh.antennapod.zipimport;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZipArchiveScannerTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void chinesePathsCompanionsAndNaturalOrder() throws Exception {
        File zip = archive("系列/02-支线/10-十.mp3", "系列/02-支线/2-二.mp3",
                "系列/01-主线/01-火候.mp3", "系列/01-主线/01-火候-封面.webp",
                "系列/01-主线/01-火候-文字稿.txt");
        ZipArchiveScanner.Scan scan = extract(zip);
        assertEquals("系列", scan.root().getName());
        assertEquals(Arrays.asList("01-主线", "02-支线"), scan.categories());
        assertEquals(3, scan.episodes().size());
        assertEquals("01-主线/01-火候", scan.episodes().get(0).key());
        assertEquals("2", scan.episodes().get(1).number());
        assertEquals("10", scan.episodes().get(2).number());
        assertTrue(scan.episodes().get(0).cover().isFile());
        assertTrue(scan.episodes().get(0).transcript().isFile());
    }

    @Test
    public void missingAudioAndEmptyCategoryAreReported() throws Exception {
        File zip = archive("系列/01-主线/01-缺失-文字稿.txt", "系列/02-空分类/", "系列/03-支线/A1-有效.mp3");
        ZipArchiveScanner.Scan scan = extract(zip);
        assertEquals(1, scan.episodes().size());
        assertTrue(scan.issues().stream().anyMatch(issue -> issue.problem() == ZipArchiveScanner.Problem.MISSING_AUDIO));
        assertTrue(scan.issues().stream().anyMatch(issue -> issue.problem() == ZipArchiveScanner.Problem.EMPTY_CATEGORY));
    }

    @Test
    public void traversalIsRejectedBeforeWritingOutside() throws Exception {
        File zip = archive("../escaped.mp3");
        File destination = temporary.newFolder();
        try {
            ZipArchiveScanner.extract(zip, destination, (current, total) -> { }, () -> false);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("UNSAFE_PATH"));
            assertFalse(new File(destination.getParentFile(), "escaped.mp3").exists());
        }
    }

    @Test
    public void unicodeEquivalentDuplicatesAreReported() throws Exception {
        File zip = archive("系列/01-分类/01-caf\u00e9.mp3", "系列/01-分类/01-cafe\u0301.mp3");
        ZipArchiveScanner.Scan scan = extract(zip);
        assertEquals(1, scan.episodes().size());
        assertTrue(scan.issues().stream().anyMatch(issue -> issue.problem() == ZipArchiveScanner.Problem.DUPLICATE_PATH));
    }

    @Test
    public void sameEpisodeNumberIsNotSilentlyDuplicated() throws Exception {
        ZipArchiveScanner.Scan scan = extract(archive("系列/01-主线/A1-一.mp3", "系列/01-主线/A1-另一个.mp3"));
        assertEquals(1, scan.episodes().size());
        assertTrue(scan.issues().stream().anyMatch(issue -> issue.problem() == ZipArchiveScanner.Problem.DUPLICATE_NUMBER));
    }

    @Test
    public void cancellationStopsBeforeFilesAreRead() throws Exception {
        try {
            ZipArchiveScanner.extract(archive("系列/分类/01-标题.mp3"), temporary.newFolder(),
                    (current, total) -> { }, () -> true);
            fail();
        } catch (java.io.InterruptedIOException expected) {
            assertTrue(true);
        }
    }

    @Test
    public void naturalSortPreservesPrerequisiteOrder() {
        List<String> order = new ArrayList<>(Arrays.asList("D4", "A10", "08", "A2", "02", "01", "B1"));
        Collections.sort(order, ZipArchiveScanner.NATURAL_ORDER);
        assertEquals(Arrays.asList("01", "02", "08", "A2", "A10", "B1", "D4"), order);
    }

    private File archive(String... paths) throws Exception {
        File archive = temporary.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive), StandardCharsets.UTF_8)) {
            for (String path : paths) {
                zip.putNextEntry(new ZipEntry(path));
                if (!path.endsWith("/")) {
                    zip.write("sample".getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        return archive;
    }

    private ZipArchiveScanner.Scan extract(File archive) throws Exception {
        return ZipArchiveScanner.extract(archive, temporary.newFolder(), (current, total) -> { }, () -> false);
    }
}

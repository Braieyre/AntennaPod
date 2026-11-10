package de.danoeh.antennapod.zipimport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.math.BigInteger;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipArchiveScanner {
    public static final long MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 512L * 1024 * 1024;
    public static final int MAX_TEXT_BYTES = 4 * 1024 * 1024;
    private static final Pattern NUMBER = Pattern.compile("[0-9]+|[^0-9]+");
    private static final Pattern EPISODE = Pattern.compile("([A-Za-z]*[0-9]+)-(.+)");
    public static final Comparator<String> NATURAL_ORDER = ZipArchiveScanner::compareNaturally;

    public enum Problem {
        UNSAFE_PATH, DUPLICATE_PATH, LIMIT, BAD_ENTRY, EMPTY_CATEGORY, MISSING_AUDIO,
        BAD_NAME, DUPLICATE_NUMBER, MISSING_COVER, BAD_COVER, MISSING_TRANSCRIPT,
        BAD_TRANSCRIPT, BAD_AUDIO, DURATION, MANIFEST, STRUCTURE
    }

    public record Issue(Problem problem, String path) { }

    public record Episode(String category, String number, String title, String stem, File audio,
                          File cover, File transcript) {
        public String key() {
            return category + "/" + stem;
        }
    }

    public record Scan(File root, List<String> categories, List<Episode> episodes, List<Issue> issues) { }

    public interface Progress {
        void update(int extracted, int total);
    }

    public interface Cancellation {
        boolean isCancelled();
    }

    private ZipArchiveScanner() { }

    public static void checkCancelled(Cancellation cancellation) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancellation.isCancelled()) {
            throw new InterruptedIOException();
        }
    }

    public static Scan extract(File archive, File destination, Progress progress,
                               Cancellation cancellation) throws IOException {
        List<Issue> issues = new ArrayList<>();
        if (!destination.mkdirs() && !destination.isDirectory()) {
            throw new IOException(destination.toString());
        }
        if (archive.length() > MAX_ARCHIVE_BYTES) {
            throw new IOException(Problem.LIMIT.name());
        }
        long totalBytes = 0;
        Set<String> paths = new HashSet<>();
        try (ZipFile zip = new ZipFile(archive)) {
            if (zip.size() > 20000) {
                throw new IOException(Problem.LIMIT.name());
            }
            int total = 0;
            Enumeration<? extends ZipEntry> count = zip.entries();
            while (count.hasMoreElements()) {
                if (count.nextElement().getName().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                    total++;
                }
            }
            int extracted = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                checkCancelled(cancellation);
                ZipEntry entry = entries.nextElement();
                String name = Normalizer.normalize(entry.getName(), Normalizer.Form.NFC);
                if (name.startsWith("__MACOSX/") || name.endsWith(".DS_Store")) {
                    continue;
                }
                File output = new File(destination, name);
                String canonical = output.getCanonicalPath();
                if (name.startsWith("/") || name.contains("\\") || name.contains("\u0000")
                        || Arrays.asList(name.split("/")).contains("..")
                        || !canonical.startsWith(destination.getCanonicalPath() + File.separator)) {
                    throw new IOException(Problem.UNSAFE_PATH.name() + ": " + name);
                }
                if (!paths.add(canonical)) {
                    issues.add(new Issue(Problem.DUPLICATE_PATH, name));
                    continue;
                }
                if (entry.isDirectory()) {
                    if (!output.mkdirs() && !output.isDirectory()) {
                        issues.add(new Issue(Problem.BAD_ENTRY, name));
                    }
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                long limit = lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")
                        ? MAX_TEXT_BYTES : MAX_FILE_BYTES;
                if (entry.getSize() > limit || entry.getSize() > MAX_ARCHIVE_BYTES - totalBytes) {
                    throw new IOException(Problem.LIMIT.name() + ": " + name);
                }
                File parent = output.getParentFile();
                if (parent == null || (!parent.mkdirs() && !parent.isDirectory())) {
                    throw new IOException(name);
                }
                long size = 0;
                CRC32 crc = new CRC32();
                try (InputStream input = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(output)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        checkCancelled(cancellation);
                        size += read;
                        totalBytes += read;
                        if (size > limit || totalBytes > MAX_ARCHIVE_BYTES) {
                            throw new IOException(Problem.LIMIT.name());
                        }
                        crc.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                    }
                    if (entry.getCrc() != -1 && crc.getValue() != entry.getCrc()) {
                        throw new IOException(Problem.BAD_ENTRY.name());
                    }
                } catch (InterruptedIOException error) {
                    throw error;
                } catch (IOException error) {
                    if (size > limit || totalBytes > MAX_ARCHIVE_BYTES) {
                        throw error;
                    }
                    if (!output.delete() && output.exists()) {
                        throw error;
                    }
                    issues.add(new Issue(Problem.BAD_ENTRY, name));
                }
                if (lower.endsWith(".mp3")) {
                    progress.update(++extracted, total);
                }
            }
        }
        File root = destination;
        File[] top = visibleChildren(destination);
        if (top.length == 1 && top[0].isDirectory()) {
            for (File child : visibleChildren(top[0])) {
                if (child.isDirectory()) {
                    root = top[0];
                    break;
                }
            }
        }
        return scan(root, issues);
    }

    public static Scan scan(File root, List<Issue> initialIssues) {
        List<Issue> issues = new ArrayList<>(initialIssues);
        List<String> categories = new ArrayList<>();
        List<Episode> episodes = new ArrayList<>();
        for (File category : visibleChildren(root)) {
            if (!category.isDirectory()) {
                if (category.getName().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                    issues.add(new Issue(Problem.STRUCTURE, category.getName()));
                }
                continue;
            }
            categories.add(category.getName());
            Set<String> stems = new TreeSet<>(NATURAL_ORDER);
            for (File file : visibleChildren(category)) {
                String name = file.getName();
                if (file.isDirectory()) {
                    issues.add(new Issue(Problem.STRUCTURE, category.getName() + "/" + name));
                } else if (name.endsWith("-封面.webp")) {
                    stems.add(name.substring(0, name.length() - "-封面.webp".length()));
                } else if (name.endsWith("-文字稿.txt")) {
                    stems.add(name.substring(0, name.length() - "-文字稿.txt".length()));
                } else if (name.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                    stems.add(name.substring(0, name.length() - 4));
                }
            }
            if (stems.isEmpty()) {
                issues.add(new Issue(Problem.EMPTY_CATEGORY, category.getName()));
            }
            Set<String> numbers = new LinkedHashSet<>();
            for (String stem : stems) {
                String path = category.getName() + "/" + stem;
                Matcher matcher = EPISODE.matcher(stem);
                if (!matcher.matches()) {
                    issues.add(new Issue(Problem.BAD_NAME, path));
                    continue;
                }
                String number = matcher.group(1);
                if (!numbers.add(number.toUpperCase(Locale.ROOT))) {
                    issues.add(new Issue(Problem.DUPLICATE_NUMBER, path));
                    continue;
                }
                File audio = new File(category, stem + ".mp3");
                if (!audio.isFile() || audio.length() == 0) {
                    issues.add(new Issue(Problem.MISSING_AUDIO, path));
                    continue;
                }
                File cover = new File(category, stem + "-封面.webp");
                File transcript = new File(category, stem + "-文字稿.txt");
                if (!cover.isFile()) {
                    issues.add(new Issue(Problem.MISSING_COVER, path));
                }
                if (!transcript.isFile()) {
                    issues.add(new Issue(Problem.MISSING_TRANSCRIPT, path));
                }
                episodes.add(new Episode(category.getName(), number, matcher.group(2), stem,
                        audio, cover, transcript));
            }
        }
        return new Scan(root, categories, episodes, issues);
    }

    private static File[] visibleChildren(File file) {
        File[] children = file.listFiles(child -> !child.getName().startsWith(".")
                && !child.getName().equals("__MACOSX"));
        if (children == null) {
            return new File[0];
        }
        Arrays.sort(children, (a, b) -> NATURAL_ORDER.compare(a.getName(), b.getName()));
        return children;
    }

    private static int compareNaturally(String left, String right) {
        Matcher a = NUMBER.matcher(left);
        Matcher b = NUMBER.matcher(right);
        while (a.find() && b.find()) {
            String x = a.group();
            String y = b.group();
            int compare;
            if (Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))) {
                compare = new BigInteger(x).compareTo(new BigInteger(y));
            } else {
                compare = x.compareToIgnoreCase(y);
            }
            if (compare != 0) {
                return compare;
            }
        }
        return left.compareTo(right);
    }
}

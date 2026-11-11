package de.danoeh.antennapod.zipimport;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import org.json.JSONArray;
import org.json.JSONObject;
import de.danoeh.antennapod.R;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImportHistoryFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private String selected = "";

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        if (state != null) {
            selected = state.getString("selected", "");
        }
        LinearLayout root = PaperUi.column(requireContext());
        PaperUi.toolbar(this, root, R.string.paper_import_history, true);
        ScrollView scroll = new ScrollView(requireContext());
        content = PaperUi.column(requireContext());
        PaperUi.inset(content);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        load();
        return root;
    }

    private void load() {
        Context context = requireContext().getApplicationContext();
        LinearLayout target = content;
        executor.execute(() -> {
            try {
                List<JSONObject> entries = ImportHistory.list(context);
                for (JSONObject entry : entries) {
                    ImportHistory.inspectSource(context, entry);
                }
                target.post(() -> {
                    if (getView() == null || target != content) {
                        return;
                    }
                    content.removeAllViews();
                    if (entries.isEmpty()) {
                        content.addView(PaperUi.text(requireContext(), getString(R.string.paper_no_imports), 16, true));
                    }
                    for (JSONObject entry : entries) {
                        render(entry);
                    }
                });
            } catch (Exception error) {
                target.post(() -> {
                    if (isAdded() && target == content) {
                        content.removeAllViews();
                        content.addView(PaperUi.text(requireContext(), getString(R.string.zip_load_failed,
                                error.getMessage()), 16, true));
                    }
                });
            }
        });
    }

    private void render(JSONObject entry) {
        Context context = requireContext();
        String id = entry.optString("id");
        String name = entry.optString("name");
        if (name.isEmpty()) {
            name = getString(R.string.paper_previous_import);
        }
        MaterialButton button = PaperUi.button(context, R.string.paper_import_history, false);
        button.setText(name);
        button.setOnClickListener(view -> {
            selected = selected.equals(id) ? "" : id;
            load();
        });
        content.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        String summary = getString(entry.optString("state").equals("failed") ? R.string.paper_import_failed
                : entry.optString("state").equals("cancelled") ? R.string.paper_import_cancelled
                : R.string.zip_done, entry.optInt("imported"));
        content.addView(PaperUi.text(context, summary + " · " + DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT).format(new Date(entry.optLong("createdAt"))), 13, true));
        if (!selected.equals(id)) {
            return;
        }
        String source = entry.optString("sourceState", "none");
        int sourceLabel = switch (source) {
            case "deleted" -> R.string.paper_source_deleted;
            case "missing" -> R.string.paper_source_missing;
            case "error" -> R.string.paper_source_error;
            case "unavailable" -> R.string.paper_source_unavailable;
            case "none" -> R.string.paper_source_none;
            default -> R.string.paper_source_exists;
        };
        TextView label = PaperUi.text(context, getString(sourceLabel), 15, false);
        label.setPadding(0, PaperUi.dp(context, 18), 0, PaperUi.dp(context, 10));
        content.addView(label);
        JSONArray issues = entry.optJSONArray("issues");
        if (issues != null && issues.length() > 0) {
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < issues.length(); i++) {
                detail.append(issues.optString(i)).append('\n');
            }
            TextView text = PaperUi.text(context, detail.toString(), 14, true);
            text.setTextIsSelectable(true);
            content.addView(text);
        }
        if ((entry.optString("state").equals("failed") || entry.optString("state").equals("cancelled"))
                && (source.equals("exists") || source.equals("error"))) {
            MaterialButton retry = PaperUi.button(context, R.string.zip_retry, true);
            retry.setOnClickListener(view -> {
                retry.setEnabled(false);
                executor.execute(() -> {
                    try {
                        boolean accepted = SeriesLibraryFragment.enqueueIfIdle(context.getApplicationContext(),
                                entry.optString("sourceUri"), entry.optString("name"));
                        retry.post(() -> {
                            if (isAdded()) {
                                Snackbar.make(retry, accepted ? R.string.paper_import_reading : R.string.paper_import_busy,
                                        Snackbar.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception error) {
                        retry.post(() -> {
                            if (isAdded()) {
                                retry.setEnabled(true);
                                Snackbar.make(retry, getString(R.string.zip_failed, error.getMessage()),
                                        Snackbar.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            });
            content.addView(retry);
        }
        if (source.equals("exists") || source.equals("error")) {
            MaterialButton delete = PaperUi.button(context, R.string.zip_delete_source, false);
            delete.setOnClickListener(view -> new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.paper_delete_title).setMessage(R.string.paper_delete_message)
                    .setNegativeButton(R.string.paper_cancel, null)
                    .setPositiveButton(R.string.paper_delete, (dialog, which) -> delete(entry, delete)).show());
            content.addView(delete);
        }
    }

    private void delete(JSONObject entry, View button) {
        Context context = requireContext().getApplicationContext();
        button.setEnabled(false);
        executor.execute(() -> {
            try {
                ImportHistory.deleteSource(context, entry);
            } catch (Exception error) {
                entry.remove("sourceState");
            }
            button.post(() -> {
                if (isAdded() && getView() != null) {
                    load();
                }
            });
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        state.putString("selected", selected);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onDestroyView() {
        content = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}

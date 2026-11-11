package de.danoeh.antennapod.zipimport;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SeriesTranscriptFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView body;
    private TextView matches;
    private EditText search;
    private ScrollView scroll;
    private String text = "";
    private int selected;
    private final List<Integer> offsets = new ArrayList<>();

    public static SeriesTranscriptFragment newInstance(String title, String path) {
        SeriesTranscriptFragment fragment = new SeriesTranscriptFragment();
        Bundle arguments = new Bundle();
        arguments.putString("title", title);
        arguments.putString("path", path);
        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        LinearLayout root = PaperUi.column(requireContext());
        MaterialToolbar toolbar = PaperUi.toolbar(this, root, R.string.zip_transcript, true);
        toolbar.getMenu().add(R.string.paper_search).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        search = new EditText(requireContext());
        search.setSingleLine(true);
        search.setHint(R.string.zip_transcript_search);
        search.setPadding(PaperUi.dp(requireContext(), 22), PaperUi.dp(requireContext(), 12),
                PaperUi.dp(requireContext(), 22), PaperUi.dp(requireContext(), 12));
        root.addView(search);
        search.setVisibility(View.GONE);
        LinearLayout controls = new LinearLayout(requireContext());
        Button previous = PaperUi.button(requireContext(), R.string.zip_previous_match, false);
        controls.addView(previous);
        matches = new TextView(requireContext());
        controls.addView(matches, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button next = PaperUi.button(requireContext(), R.string.zip_next_match, false);
        controls.addView(next);
        root.addView(controls);
        controls.setVisibility(View.GONE);
        toolbar.setOnMenuItemClickListener(item -> {
            boolean open = search.getVisibility() != View.VISIBLE;
            search.setVisibility(open ? View.VISIBLE : View.GONE);
            controls.setVisibility(open ? View.VISIBLE : View.GONE);
            item.setTitle(open ? R.string.paper_done : R.string.paper_search);
            if (open) {
                search.requestFocus();
            } else {
                search.setText("");
            }
            return true;
        });
        scroll = new ScrollView(requireContext());
        body = new TextView(requireContext());
        body.setTextSize(18);
        body.setTextIsSelectable(true);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding);
        body.setLineSpacing(8, 1.15f);
        body.setText(R.string.zip_loading);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        previous.setOnClickListener(view -> move(-1));
        next.setOnClickListener(view -> move(1));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                selected = 0;
                highlight();
            }

            @Override
            public void afterTextChanged(Editable value) { }
        });
        String path = requireArguments().getString("path", "");
        Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            String result;
            try {
                result = path.isEmpty() ? context.getString(R.string.zip_none_transcript) : SeriesStore.readText(new File(path));
            } catch (Exception error) {
                result = context.getString(R.string.zip_load_failed, error.getMessage());
            }
            String loaded = result;
            root.post(() -> {
                if (getView() != root) {
                    return;
                }
                text = loaded;
                highlight();
            });
        });
        return root;
    }

    private void move(int direction) {
        if (!offsets.isEmpty()) {
            selected = (selected + direction + offsets.size()) % offsets.size();
            highlight();
        }
    }

    private void highlight() {
        offsets.clear();
        String keyword = search.getText().toString();
        SpannableString content = new SpannableString(text);
        if (!keyword.isEmpty()) {
            String haystack = text.toLowerCase(Locale.ROOT);
            String needle = keyword.toLowerCase(Locale.ROOT);
            int offset = 0;
            while ((offset = haystack.indexOf(needle, offset)) >= 0) {
                offsets.add(offset);
                offset += needle.length();
            }
            for (int i = 0; i < offsets.size(); i++) {
                content.setSpan(new BackgroundColorSpan(PaperUi.color(requireContext(), i == selected
                        ? R.attr.colorPrimaryContainer : R.attr.colorSecondaryContainer)),
                        offsets.get(i), offsets.get(i) + keyword.length(), 0);
            }
        }
        body.setText(content);
        body.setTextColor(PaperUi.color(requireContext(), android.R.attr.textColorPrimary));
        body.setBackgroundColor(PaperUi.color(requireContext(), android.R.attr.colorBackground));
        matches.setText(getString(R.string.zip_match_count, offsets.isEmpty() ? 0 : selected + 1, offsets.size()));
        if (!offsets.isEmpty()) {
            int activeOffset = offsets.get(selected);
            body.post(() -> {
                if (body.getLayout() != null && activeOffset <= body.length()) {
                    int line = body.getLayout().getLineForOffset(activeOffset);
                    scroll.smoothScrollTo(0, body.getLayout().getLineTop(line));
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}

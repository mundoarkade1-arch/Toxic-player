package com.asccine.tvbox;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Busca só de filmes e séries (até 100 resultados), com teclado próprio para TV.
 * Antes de buscar, a tela mostra o botão de digitar no MEIO e, embaixo, cartões pequenos com
 * os lançamentos de filmes e de séries. Depois de buscar, mostra o botão no alto e a grade.
 */
final class SearchScreen extends Screen {

    private static final int MAX_RESULTS = 100;
    private static final int SUGGESTIONS = 12;

    private final FrameLayout root;
    private final LinearLayout results;
    private final ScrollView idle;
    private final LinearLayout idleBox;
    private final TextView idleButton;
    private final TextView queryButton;
    private final TvGrid grid;
    private final CardAdapter adapter;
    private final TextView status;
    private Bg.Handle running;
    private int gen;
    private String lastQuery = "";

    SearchScreen(ShellActivity act) {
        super(act);
        root = new FrameLayout(act);
        root.setBackgroundColor(C.BG);

        // ----- Tela de resultados (grade) -----
        results = new LinearLayout(act);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setVisibility(View.GONE);
        root.addView(results, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(act, 36), Ui.dp(act, 22), Ui.dp(act, 36), Ui.dp(act, 6));
        head.addView(Ui.title(act, "Buscar", 34, C.TEXT));
        View sp = new View(act);
        head.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        queryButton = Ui.button(act, "Digitar para buscar", Ui.LIME);
        head.addView(queryButton, new LinearLayout.LayoutParams(Ui.dp(act, 420), -2));
        queryButton.setOnClickListener(v -> openKeyboard());
        results.addView(head, new LinearLayout.LayoutParams(-1, -2));

        grid = new TvGrid(act);
        adapter = new CardAdapter(act, grid);
        CardAdapter.setup(grid, adapter);
        results.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        grid.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < adapter.items.size()) {
                Nav.openDetail(act, adapter.items.get(pos), false);
            }
        });
        grid.setLongListener(pos -> {
            if (pos >= 0 && pos < adapter.items.size()) {
                act.itemMenu(adapter.items.get(pos), () -> run(lastQuery));
            }
        });

        // ----- Tela inicial: botão no meio + lançamentos em cartões pequenos -----
        idle = new ScrollView(act);
        idle.setVerticalScrollBarEnabled(false);
        idleBox = new LinearLayout(act);
        idleBox.setOrientation(LinearLayout.VERTICAL);
        idleBox.setGravity(Gravity.CENTER_HORIZONTAL);
        idleBox.setPadding(0, Ui.dp(act, 28), 0, Ui.dp(act, 20));
        idle.addView(idleBox, new FrameLayout.LayoutParams(-1, -2));
        root.addView(idle, new FrameLayout.LayoutParams(-1, -1));

        TextView idleTitle = Ui.title(act, Ui.highlight("Buscar", "Buscar", C.LIME), 40,
                C.TEXT);
        idleBox.addView(idleTitle, new LinearLayout.LayoutParams(-2, -2));
        idleButton = Ui.button(act, "Digitar para buscar", Ui.LIME);
        idleButton.setOnClickListener(v -> openKeyboard());
        LinearLayout.LayoutParams ibl = new LinearLayout.LayoutParams(Ui.dp(act, 520), -2);
        ibl.topMargin = Ui.dp(act, 14);
        idleBox.addView(idleButton, ibl);

        status = Ui.text(act, "", 20, C.TEXT2);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, -2);
        slp.gravity = Gravity.CENTER;
        root.addView(status, slp);
    }

    @Override
    View view() {
        return root;
    }

    @Override
    View firstFocus() {
        return idle.getVisibility() == View.VISIBLE ? idleButton : queryButton;
    }

    @Override
    boolean handleUp(View focused) {
        if (focused == grid) {
            queryButton.requestFocus();
            return true;
        }
        return false;
    }

    private void showResultsMode(boolean on) {
        results.setVisibility(on ? View.VISIBLE : View.GONE);
        idle.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    @Override
    void onShow() {
        status.setText("");
        showResultsMode(false);
        loadSuggestions();
        // Abre o teclado logo de cara (a pessoa veio aqui para buscar).
        root.post(this::openKeyboard);
    }

    @Override
    void onHide() {
        gen++;
        super.onHide();
    }

    /** Lançamentos de filmes e de séries (já guardados pela Início, então é rápido). */
    private void loadSuggestions() {
        addSuggestions(XtreamApi.Kind.MOVIE, "Filmes em lançamento");
        addSuggestions(XtreamApi.Kind.SERIES, "Séries em lançamento");
    }

    private void addSuggestions(final XtreamApi.Kind kind, final String title) {
        bg(() -> Catalog.recent(act, kind), new Bg.Done<List<XtreamApi.Item>>() {
            @Override
            public void ok(List<XtreamApi.Item> all) {
                List<XtreamApi.Item> some = new ArrayList<>();
                for (int i = 0; i < all.size() && some.size() < SUGGESTIONS; i++) {
                    some.add(all.get(i));
                }
                if (some.isEmpty()) {
                    return;
                }
                TextView h = Ui.title(act, title, 20, C.TEXT);
                LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
                hl.setMargins(Ui.dp(act, 36), Ui.dp(act, 26), Ui.dp(act, 36), 0);
                idleBox.addView(h, hl);
                idleBox.addView(smallRow(some), new LinearLayout.LayoutParams(-1, -2));
            }

            @Override
            public void fail(Exception e) {
                // sem sugestões: a tela continua útil só com o botão
            }
        });
    }

    /** Faixa de cartões pequenos (dá para ler o título, mas ocupa pouco). */
    private View smallRow(List<XtreamApi.Item> items) {
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.dp(act, 36), Ui.dp(act, 8), Ui.dp(act, 36), Ui.dp(act, 8));
        LinearLayout line = new LinearLayout(act);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setClipChildren(false);
        hs.addView(line, new FrameLayout.LayoutParams(-2, -2));
        for (final XtreamApi.Item it : items) {
            Card card = new Card(act, Ui.dp(act, 76), true);
            card.bind(it);
            card.setOnClickListener(v -> Nav.openDetail(act, it, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(card.cardW(),
                    card.cardH());
            lp.rightMargin = Ui.dp(act, 8);
            line.addView(card, lp);
        }
        return hs;
    }

    private void openKeyboard() {
        SearchKeyboard.show(act, "Buscar filmes e séries", "Buscar", 2, new SearchKeyboard.Listener() {
            @Override
            public void onSearch(String text) {
                run(text);
            }

            @Override
            public void onCancel() {
                (idle.getVisibility() == View.VISIBLE ? idleButton : queryButton).requestFocus();
            }
        });
    }

    private void run(final String text) {
        if (text == null || text.trim().length() < 2) {
            return;
        }
        final String q = text.trim();
        lastQuery = q;
        gen++;
        final int myGen = gen;
        if (running != null) {
            running.cancel();
        }
        adapter.items.clear();
        adapter.notifyDataSetChanged();
        showResultsMode(true);
        queryButton.setText("Busca: " + q);
        status.setText("Buscando...");
        status.setVisibility(View.VISIBLE);
        final int[] count = {0};
        running = bgH(h -> {
            Catalog.search(act, q, h, item -> {
                if (h.isCancelled()) {
                    return false;
                }
                count[0]++;
                final XtreamApi.Item found = item;
                Bg.ui(() -> {
                    if (myGen == gen) {
                        adapter.items.add(found);
                        adapter.notifyDataSetChanged();
                        status.setVisibility(View.GONE);
                    }
                });
                return count[0] < MAX_RESULTS;
            });
            return count[0];
        }, new Bg.Done<Integer>() {
            @Override
            public void ok(Integer n) {
                if (myGen != gen) {
                    return;
                }
                if (adapter.items.isEmpty()) {
                    status.setText("Nada encontrado para \"" + q + "\".");
                    status.setVisibility(View.VISIBLE);
                } else {
                    grid.requestFocus();
                }
            }

            @Override
            public void fail(Exception e) {
                if (myGen != gen) {
                    return;
                }
                if (adapter.items.isEmpty()) {
                    status.setText(errorText(e));
                    status.setVisibility(View.VISIBLE);
                }
            }
        });
    }
}

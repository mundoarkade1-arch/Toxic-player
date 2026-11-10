package com.asccine.tvbox;

import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Filmes ou Séries: seletor de categoria + grade de cartões grandes (40 por página). */
final class GridScreen extends Screen {

    private static final int MAX_ITEMS = 5000;

    private final XtreamApi.Kind kind;
    private final FrameLayout root;
    private final TextView catButton;
    private final TvGrid grid;
    private final CardAdapter adapter;
    private final TextView status;
    private final TextView retry;
    private String catId;
    private String catName = "Todos";
    private boolean allowAdult;
    private boolean loading;
    private boolean end;
    private int offset;
    private int gen;
    private List<Catalog.Cat> cats;

    GridScreen(ShellActivity act, XtreamApi.Kind kind) {
        super(act);
        this.kind = kind;
        root = new FrameLayout(act);
        root.setBackgroundColor(C.BG);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(act, 36), Ui.dp(act, 22), Ui.dp(act, 36), Ui.dp(act, 6));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));
        head.addView(Ui.title(act, kind == XtreamApi.Kind.MOVIE ? "Filmes" : "Séries", 34,
                C.TEXT));
        View sp = new View(act);
        head.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        catButton = Ui.button(act, "Categoria: Todos", Ui.DARK);
        head.addView(catButton, new LinearLayout.LayoutParams(Ui.dp(act, 380), -2));
        catButton.setOnClickListener(v -> chooseCategory());

        grid = new TvGrid(act);
        adapter = new CardAdapter(act, grid);
        CardAdapter.setup(grid, adapter);
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        grid.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < adapter.items.size()) {
                Nav.openDetail(act, adapter.items.get(pos), false);
            }
        });
        grid.setLongListener(pos -> {
            if (pos >= 0 && pos < adapter.items.size()) {
                act.itemMenu(adapter.items.get(pos), this::reload);
            }
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= adapter.items.size() - 12) {
                    loadMore();
                }
                grid.refreshCards();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        status = Ui.text(act, "Carregando...", 20, C.TEXT2);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, -2);
        slp.gravity = Gravity.CENTER;
        root.addView(status, slp);
        retry = Ui.button(act, "Tentar de novo", Ui.DARK);
        retry.setVisibility(View.GONE);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(Ui.dp(act, 260), -2);
        rlp.gravity = Gravity.CENTER;
        rlp.topMargin = Ui.dp(act, 90);
        root.addView(retry, rlp);
        retry.setOnClickListener(v -> reload());
    }

    @Override
    View view() {
        return root;
    }

    @Override
    View firstFocus() {
        return adapter.items.isEmpty() ? catButton : grid;
    }

    @Override
    boolean handleUp(View focused) {
        if (focused == grid) {
            catButton.requestFocus();
            return true;
        }
        return false;
    }

    @Override
    void onShow() {
        if (act.pendingKind == kind) {
            catId = act.pendingCat;
            catName = act.pendingCatName == null ? "Todos" : act.pendingCatName;
            catButton.setText("Categoria: " + catName);
        }
        act.pendingKind = null;
        act.pendingCat = null;
        act.pendingCatName = null;
        reload();
    }

    @Override
    void onHide() {
        gen++;
        super.onHide();
    }

    @Override
    void onResume() {
        // Um item pode ter sido bloqueado ou desbloqueado: limpa o que ficou na tela.
        List<XtreamApi.Item> keep = new ArrayList<>();
        for (XtreamApi.Item it : adapter.items) {
            if (!UserLists.isBlocked(act, it)) {
                keep.add(it);
            }
        }
        if (keep.size() != adapter.items.size()) {
            adapter.items.clear();
            adapter.items.addAll(keep);
            adapter.notifyDataSetChanged();
        }
    }

    private void reload() {
        gen++;
        adapter.items.clear();
        adapter.notifyDataSetChanged();
        offset = 0;
        end = false;
        loading = false;
        status.setText("Carregando...");
        status.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        loadMore();
    }

    private void loadMore() {
        if (loading || end || adapter.items.size() >= MAX_ITEMS) {
            return;
        }
        loading = true;
        final int myGen = gen;
        final String cat = catId;
        final int off = offset;
        final boolean adult = allowAdult;
        bg(() -> Catalog.page(act, kind, cat, off, adult), new Bg.Done<Catalog.Page>() {
            @Override
            public void ok(Catalog.Page page) {
                if (myGen != gen) {
                    return;
                }
                loading = false;
                offset += page.raw;
                end = page.end;
                adapter.items.addAll(page.items);
                adapter.notifyDataSetChanged();
                if (adapter.items.isEmpty()) {
                    if (end) {
                        status.setText("Nada por aqui.");
                        status.setVisibility(View.VISIBLE);
                    } else {
                        loadMore(); // a página veio toda bloqueada: pede a próxima
                    }
                } else {
                    status.setVisibility(View.GONE);
                    act.focusReady(GridScreen.this);
                }
            }

            @Override
            public void fail(Exception e) {
                if (myGen != gen) {
                    return;
                }
                loading = false;
                if (adapter.items.isEmpty()) {
                    status.setText(errorText(e));
                    status.setVisibility(View.VISIBLE);
                    retry.setVisibility(View.VISIBLE);
                    act.focusReady(GridScreen.this);
                }
            }
        });
    }

    // ---------- Categorias ----------

    private void chooseCategory() {
        if (cats != null) {
            showCategories();
            return;
        }
        Ui.toast(act, "Carregando categorias...");
        bg(() -> Catalog.categories(act, kind), new Bg.Done<List<Catalog.Cat>>() {
            @Override
            public void ok(List<Catalog.Cat> list) {
                cats = list;
                showCategories();
            }

            @Override
            public void fail(Exception e) {
                Ui.toast(act, errorText(e));
            }
        });
    }

    private void showCategories() {
        final String[] labels = new String[cats.size() + 1];
        labels[0] = "Todos";
        for (int i = 0; i < cats.size(); i++) {
            Catalog.Cat c = cats.get(i);
            boolean locked = c.adult && ParentalStore.isEnabled(act);
            labels[i + 1] = locked ? c.name + "  (com senha)" : c.name;
        }
        Dialogs.list(act, "Escolha a categoria", labels, which -> {
            if (which == 0) {
                applyCategory(null, "Todos", false);
                return;
            }
            final Catalog.Cat c = cats.get(which - 1);
            if (c.adult && ParentalStore.isEnabled(act)) {
                askPin(c);
            } else {
                applyCategory(c.id, c.name, false);
            }
        });
    }

    private void askPin(final Catalog.Cat c) {
        PinDialog.show(act, "Categoria adulta", "Digite a senha do controle parental.",
                new PinDialog.Listener() {
                    @Override
                    public void onPin(PinDialog d, String pin) {
                        if (ParentalStore.check(act, pin)) {
                            d.dismiss();
                            applyCategory(c.id, c.name, true);
                        } else {
                            long wait = ParentalStore.lockedSeconds(act);
                            d.reset();
                            d.setMessage(wait > 0 ? "Muitos erros. Espere " + wait + " s."
                                    : "Senha incorreta.", true);
                        }
                    }

                    @Override
                    public void onCancel() {
                    }
                });
    }

    private void applyCategory(String id, String name, boolean adult) {
        catId = id;
        catName = name;
        allowAdult = adult;
        catButton.setText("Categoria: " + name);
        reload();
    }
}

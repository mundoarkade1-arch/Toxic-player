package com.asccine.tvbox;

import android.content.Context;
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
 * Início: banner com 5 destaques, Continue assistindo, Top 10 de filmes e de séries,
 * favoritos e faixas por categoria. Tudo carrega aos poucos, em segundo plano.
 * O Top 10 de filmes e o de séries são calculados ao mesmo tempo (em paralelo) e o resultado
 * fica guardado em arquivo por 6 horas (ver Catalog.recent e HomeCache).
 */
final class HomeScreen extends Screen {

    private static boolean expiryChecked = false;

    private final Context ctx;
    private final ScrollView sv;
    private final LinearLayout box;
    private final HeroView hero;
    private final TextView status;
    private final TextView retry;
    private final LinearLayout slotBanner;
    private final LinearLayout slotContinue;
    private final LinearLayout slotTopMovies;
    private final LinearLayout slotTopSeries;
    private final LinearLayout slotFav;
    private final LinearLayout catBox;
    private volatile boolean dead;
    private int rowNumber = 1;
    private int topPending;
    private List<XtreamApi.Item> topMovies;
    private List<XtreamApi.Item> topSeries;

    HomeScreen(ShellActivity act) {
        super(act);
        ctx = act;
        sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setBackgroundColor(C.BG);
        box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        sv.addView(box, new FrameLayout.LayoutParams(-1, -2));

        hero = new HeroView(act, act, this, Math.round(Ui.screenH(act) * 0.52f));
        hero.setVisibility(View.GONE);
        box.addView(hero);

        status = Ui.text(act, "Carregando...", 20, C.TEXT2);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(act, 40), 0, Ui.dp(act, 12));
        box.addView(status, new LinearLayout.LayoutParams(-1, -2));
        retry = Ui.button(act, "Tentar de novo", Ui.DARK);
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> act.showScreen(Sidebar.HOME, true));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Ui.dp(act, 260), -2);
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(retry, rlp);

        slotBanner = slot();
        slotTopMovies = slot();
        slotTopSeries = slot();
        slotContinue = slot();
        slotFav = slot();
        catBox = new LinearLayout(act);
        catBox.setOrientation(LinearLayout.VERTICAL);
        catBox.setClipChildren(false);
        box.addView(catBox, new LinearLayout.LayoutParams(-1, -2));
        View end = new View(act);
        box.addView(end, new LinearLayout.LayoutParams(1, Ui.dp(act, 48)));
    }

    private LinearLayout slot() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setClipChildren(false);
        l.setVisibility(View.GONE);
        box.addView(l, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    @Override
    View view() {
        return sv;
    }

    @Override
    View firstFocus() {
        if (hero.getVisibility() == View.VISIBLE && hero.hasItems()) {
            return hero.button();
        }
        ArrayList<View> fs = new ArrayList<>();
        box.addFocusables(fs, View.FOCUS_DOWN);
        return fs.isEmpty() ? null : fs.get(0);
    }

    @Override
    void onShow() {
        loadLocal();
        loadTop();
        loadCategories();
        loadExpiry();
    }

    @Override
    void onResume() {
        if (dead) {
            return;
        }
        hero.resume();
        loadLocal();
    }

    @Override
    void onPause() {
        hero.pause();
    }

    @Override
    void onHide() {
        dead = true;
        hero.stop();
        super.onHide();
    }

    // ---------- Continue assistindo e favoritos (dados do aparelho) ----------

    private void loadLocal() {
        bg(() -> {
            List<Object> out = new ArrayList<>();
            out.add(ContinueStore.list(ctx));
            out.add(UserLists.favorites(ctx));
            return out;
        }, new Bg.Done<List<Object>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void ok(List<Object> r) {
                fillContinue((List<ContinueStore.Saved>) r.get(0));
                fillFavorites((List<XtreamApi.Item>) r.get(1));
            }

            @Override
            public void fail(Exception e) {
                // sem dados locais: segue sem essas faixas
            }
        });
    }

    private void fillContinue(List<ContinueStore.Saved> list) {
        slotContinue.removeAllViews();
        if (list.isEmpty()) {
            slotContinue.setVisibility(View.GONE);
            return;
        }
        slotContinue.setVisibility(View.VISIBLE);
        slotContinue.addView(header("Continue assistindo", null, false));
        HorizontalScrollView hs = rowScroller();
        LinearLayout line = rowLine(hs);
        int pw = Ui.rowPw(ctx);
        for (final ContinueStore.Saved s : list) {
            Card card = new Card(ctx, pw, true);
            card.bindRaw(s.name, s.icon, 0, 0);
            card.setOnClickListener(v -> {
                boolean ep = "episode".equals(s.kind);
                String key = Catalog.progressKey(ctx, ep, s.id, s.url);
                PlayerActivity.launch(act, s.url, s.name, key, s.kind, s.id, s.ext, s.icon,
                        s.seriesKey);
            });
            card.setOnLongClickListener(v -> {
                Dialogs.confirm(act, s.name, "Tirar do Continue assistindo?", "Tirar", () -> {
                    ContinueStore.remove(ctx, s.url);
                    loadLocal();
                });
                return true;
            });
            line.addView(card, rowLp(card));
        }
        slotContinue.addView(hs);
    }

    private void fillFavorites(List<XtreamApi.Item> list) {
        slotFav.removeAllViews();
        if (list.isEmpty()) {
            slotFav.setVisibility(View.GONE);
            return;
        }
        slotFav.setVisibility(View.VISIBLE);
        slotFav.addView(header("Meus favoritos", null, false));
        List<XtreamApi.Item> shown = list.subList(0, Math.min(10, list.size()));
        slotFav.addView(itemRow(shown, false, null));
    }

    // ---------- Banner e Top 10 ----------

    /** Top 10 de filmes e de séries: os dois ao mesmo tempo; cada um aparece quando fica pronto. */
    private void loadTop() {
        topPending = 2;
        bg(() -> Catalog.top10(ctx, XtreamApi.Kind.MOVIE), new Bg.Done<List<XtreamApi.Item>>() {
            @Override
            public void ok(List<XtreamApi.Item> movies) {
                topMovies = movies;
                fillTop(slotTopMovies, "Filmes", movies);
                topArrived();
            }

            @Override
            public void fail(Exception e) {
                topArrived();
            }
        });
        bg(() -> Catalog.top10(ctx, XtreamApi.Kind.SERIES), new Bg.Done<List<XtreamApi.Item>>() {
            @Override
            public void ok(List<XtreamApi.Item> series) {
                topSeries = series;
                fillTop(slotTopSeries, "Séries", series);
                topArrived();
            }

            @Override
            public void fail(Exception e) {
                topArrived();
            }
        });
    }

    /** Um dos Top 10 chegou: monta (ou atualiza) o banner com o que já se tem. */
    private void topArrived() {
        topPending--;
        List<XtreamApi.Item> list = Catalog.heroFrom(topMovies, topSeries);
        if (list.isEmpty()) {
            if (topPending <= 0) {
                showError();
            }
            return;
        }
        status.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        hero.setVisibility(View.VISIBLE);
        if (hero.hasItems()) {
            hero.replaceItems(list);
        } else {
            hero.setItems(list);
            hero.start();
        }
        act.focusReady(this);
    }

    private void fillTop(LinearLayout slot, String tag, List<XtreamApi.Item> items) {
        if (items.isEmpty()) {
            return;
        }
        status.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        slot.removeAllViews();
        slot.setVisibility(View.VISIBLE);
        slot.addView(topHeader(tag));
        slot.addView(itemRow(items, true, null));
        act.focusReady(this);
    }

    /** Título grande do Top 10: "Top 10 de hoje", rótulo limão grande e uma linha fina limão. */
    private View topHeader(String tag) {
        LinearLayout h = new LinearLayout(ctx);
        h.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.setMargins(Ui.dp(ctx, 36), Ui.dp(ctx, 30), Ui.dp(ctx, 36), Ui.dp(ctx, 4));
        h.setLayoutParams(hlp);

        LinearLayout line1 = new LinearLayout(ctx);
        line1.setOrientation(LinearLayout.HORIZONTAL);
        line1.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.title(ctx, Ui.highlight("Top 10 de hoje", "Top 10", C.LIME), 40, C.TEXT);
        t.setSingleLine(true);
        line1.addView(t, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.leftMargin = Ui.dp(ctx, 20);
        line1.addView(Ui.limeTagBig(ctx, tag, 22), glp);
        h.addView(line1, new LinearLayout.LayoutParams(-2, -2));

        View underline = new View(ctx);
        underline.setBackground(Ui.shape(C.LIME, Ui.dp(ctx, 2), 0, 0));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(Ui.dp(ctx, 200),
                Ui.dp(ctx, 3));
        ulp.topMargin = Ui.dp(ctx, 10);
        h.addView(underline, ulp);
        return h;
    }

    private void showError() {
        if (hero.hasItems() || catBox.getChildCount() > 0) {
            return;
        }
        status.setText(errorText(null));
        retry.setVisibility(View.VISIBLE);
    }

    // ---------- Faixas por categoria ----------

    private void loadCategories() {
        final int perKind = Catalog.isM3u(Catalog.creds(ctx)) ? 2 : 4;
        bg(() -> {
            XtreamApi.Kind[] kinds = {XtreamApi.Kind.MOVIE, XtreamApi.Kind.SERIES};
            List<List<Catalog.Cat>> cats = new ArrayList<>();
            for (XtreamApi.Kind k : kinds) {
                cats.add(Catalog.visibleCategories(ctx, k));
            }
            for (int i = 0; i < perKind; i++) {
                for (int k = 0; k < kinds.length; k++) {
                    if (dead || i >= cats.get(k).size()) {
                        continue;
                    }
                    final Catalog.Cat cat = cats.get(k).get(i);
                    final XtreamApi.Kind kind = kinds[k];
                    try {
                        final Catalog.Page page = Catalog.page(ctx, kind, cat.id, 0, false);
                        if (!page.items.isEmpty()) {
                            Bg.ui(() -> {
                                if (!dead) {
                                    addCategoryRow(kind, cat, page.items);
                                }
                            });
                        }
                    } catch (java.io.IOException ignored) {
                        // uma categoria que falha não derruba as outras
                    }
                }
            }
            return null;
        }, new Bg.Done<Object>() {
            @Override
            public void ok(Object v) {
            }

            @Override
            public void fail(Exception e) {
                showError();
            }
        });
    }

    private void addCategoryRow(final XtreamApi.Kind kind, final Catalog.Cat cat,
                                List<XtreamApi.Item> items) {
        status.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        List<XtreamApi.Item> shown = items.subList(0, Math.min(10, items.size()));
        String tag = kind == XtreamApi.Kind.MOVIE ? "Filmes" : "Séries";
        catBox.addView(header(cat.name, tag));
        catBox.addView(itemRow(shown, false, () -> act.openGrid(kind, cat.id, cat.name)));
        act.focusReady(this);
    }

    // ---------- Aviso de vencimento ----------

    private void loadExpiry() {
        if (expiryChecked || Catalog.isM3u(Catalog.creds(ctx))) {
            return;
        }
        expiryChecked = true;
        bg(() -> Catalog.daysToExpiry(ctx), new Bg.Done<Long>() {
            @Override
            public void ok(Long days) {
                if (days >= 0 && days <= 7) {
                    slotBanner.removeAllViews();
                    String t = days == 0 ? "Sua conta vence hoje."
                            : "Sua conta vence em " + days + (days == 1 ? " dia." : " dias.");
                    TextView b = Ui.text(ctx, t, 16, C.LIME);
                    b.setBackground(Ui.shape(C.SURFACE, Ui.dp(ctx, 12), C.LIME_40, Ui.dp(ctx, 1)));
                    b.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 8));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                    lp.setMargins(Ui.dp(ctx, 36), Ui.dp(ctx, 12), 0, 0);
                    slotBanner.addView(b, lp);
                    slotBanner.setVisibility(View.VISIBLE);
                    // O aviso fica logo abaixo do banner.
                    box.removeView(slotBanner);
                    box.addView(slotBanner, hero.getVisibility() == View.VISIBLE ? 1 : 0);
                }
            }

            @Override
            public void fail(Exception e) {
            }
        });
    }

    // ---------- Montagem das faixas ----------

    private View header(String title, String tag) {
        return header(title, tag, true);
    }

    private View header(String title, String tag, boolean numbered) {
        LinearLayout h = new LinearLayout(ctx);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(ctx, 36), Ui.dp(ctx, 22), Ui.dp(ctx, 36), Ui.dp(ctx, 6));
        h.setLayoutParams(lp);
        if (numbered) {
            h.addView(Ui.title(ctx, String.format(java.util.Locale.US, ".%02d", rowNumber++),
                    14, C.LIME));
        }
        TextView t = Ui.title(ctx, title, 26, C.TEXT);
        t.setSingleLine(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = Ui.dp(ctx, numbered ? 10 : 0);
        h.addView(t, tlp);
        if (tag != null) {
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
            glp.leftMargin = Ui.dp(ctx, 12);
            h.addView(Ui.limeTag(ctx, tag), glp);
        }
        return h;
    }

    private HorizontalScrollView rowScroller() {
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.dp(ctx, 36), Ui.dp(ctx, 8), Ui.dp(ctx, 36), Ui.dp(ctx, 8));
        return hs;
    }

    private LinearLayout rowLine(HorizontalScrollView hs) {
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setClipChildren(false);
        hs.addView(line, new FrameLayout.LayoutParams(-2, -2));
        return line;
    }

    private LinearLayout.LayoutParams rowLp(Card card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(card.cardW(), card.cardH());
        lp.rightMargin = Ui.dp(ctx, 8);
        return lp;
    }

    /** Uma faixa de cartões. top10: números de contorno. more: cartão "Ver mais". */
    private View itemRow(List<XtreamApi.Item> items, boolean top10, final Runnable more) {
        HorizontalScrollView hs = rowScroller();
        LinearLayout line = rowLine(hs);
        int pw = top10 ? Ui.topPw(ctx) : Ui.rowPw(ctx);
        for (int i = 0; i < items.size(); i++) {
            final XtreamApi.Item it = items.get(i);
            Card card = new Card(ctx, pw, true);
            card.bind(it);
            card.setOnClickListener(v -> Nav.openDetail(act, it, false));
            card.setOnLongClickListener(v -> {
                act.itemMenu(it, this::loadLocal);
                return true;
            });
            if (top10) {
                OutlineNumber num = new OutlineNumber(ctx, String.valueOf(i + 1), card.ph);
                int numW = num.textWidth();
                int shift = Math.round(numW * 0.55f);
                FrameLayout wrap = new FrameLayout(ctx);
                wrap.setClipChildren(false);
                wrap.addView(num, new FrameLayout.LayoutParams(numW, card.ph));
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(card.cardW(),
                        card.cardH());
                clp.leftMargin = shift;
                wrap.addView(card, clp);
                LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                        shift + card.cardW(), card.cardH());
                wlp.rightMargin = Ui.dp(ctx, 8);
                line.addView(wrap, wlp);
            } else {
                line.addView(card, rowLp(card));
            }
        }
        if (more != null) {
            line.addView(moreCard(pw, more));
        }
        return hs;
    }

    /** Cartão "Ver mais" no fim da faixa. */
    private View moreCard(int pw, final Runnable action) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setFocusable(true);
        c.setClickable(true);
        c.setBackground(Ui.focusBg(ctx, C.SURFACE, C.SURFACE_FOCUS, 16, C.LINE, Ui.dp(ctx, 1)));
        TextView t = Ui.title(ctx, "Ver mais", 18, C.LIME);
        t.setGravity(Gravity.CENTER);
        c.addView(t);
        c.addView(Ui.icon(ctx, R.drawable.ic_arrow_ne, 32, C.LIME));
        c.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(pw, pw * 3 / 2);
        lp.rightMargin = Ui.dp(ctx, 8);
        return wrapTop(c, lp);
    }

    private View wrapTop(View c, LinearLayout.LayoutParams lp) {
        c.setLayoutParams(lp);
        return c;
    }
}

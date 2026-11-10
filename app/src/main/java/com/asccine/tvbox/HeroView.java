package com.asccine.tvbox;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Banner grande do topo da Home: cinco destaques que giram sozinhos a cada ~8 s, em ciclo,
 * SEMPRE que o banner está visível na tela (mesmo com o foco na barra lateral ou em outra
 * faixa). Pausa só quando o banner sai da tela (rolou para baixo) ou a janela vai para trás.
 * Imagem larga com degradê escuro, selo FILME/SÉRIE, nota, sinopse e o botão "Assistir Agora".
 * Esquerda e direita (no botão) trocam de destaque e reiniciam a contagem dos 8 s.
 */
final class HeroView extends FrameLayout {

    private static final long ROTATE_MS = 8000L;

    private final ShellActivity act;
    private final Screen owner;
    private final ImageView backdrop;
    private final ImageView poster;
    private final TextView tag;
    private final TextView titleTv;
    private final TextView metaTv;
    private final TextView plotTv;
    private final TextView button;
    private final LinearLayout dots;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<XtreamApi.Item> items = new ArrayList<>();
    private final android.graphics.Rect visRect = new android.graphics.Rect();
    private int idx = -1;
    private int shownFor = -1;
    private boolean running;
    private boolean paused;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running || paused) {
                return;
            }
            if (items.size() > 1 && onScreen()) {
                show((idx + 1) % items.size());
            }
            handler.postDelayed(this, ROTATE_MS);
        }
    };

    /** O banner está (pelo menos pela metade) dentro da tela? */
    private boolean onScreen() {
        if (!isShown() || getWindowVisibility() != VISIBLE || getHeight() <= 0) {
            return false;
        }
        if (!getGlobalVisibleRect(visRect)) {
            return false;
        }
        return visRect.height() * 2 >= getHeight();
    }

    /** Recomeça a contagem de 8 s (depois de trocar o destaque com as setas). */
    private void restartTimer() {
        handler.removeCallbacks(tick);
        if (running && !paused) {
            handler.postDelayed(tick, ROTATE_MS);
        }
    }

    HeroView(Context c, ShellActivity act, Screen owner, int heightPx) {
        super(c);
        this.act = act;
        this.owner = owner;
        setLayoutParams(new LinearLayout.LayoutParams(-1, heightPx));
        setBackgroundColor(C.BG);
        // A foto do fundo nunca pode passar do banner (invadia o "Continue assistindo").
        setClipChildren(true);
        setClipToPadding(true);

        backdrop = new ImageView(c);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(backdrop, new LayoutParams(-1, -1));

        View scrimH = new View(c);
        scrimH.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF101010, 0xE6101010, 0x66101010, 0x00101010}));
        addView(scrimH, new LayoutParams(-1, -1));
        View scrimV = new View(c);
        scrimV.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xFF101010, 0x99101010, 0x00101010}));
        addView(scrimV, new LayoutParams(-1, -1));

        int ph = Math.round(heightPx * 0.74f);
        int pw = ph * 2 / 3;
        poster = new ImageView(c);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(Ui.shape(C.SURFACE, Ui.dp(c, 16), 0, 0));
        final float rad = Ui.dp(c, 16);
        poster.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
            }
        });
        poster.setClipToOutline(true);
        LayoutParams plp = new LayoutParams(pw, ph);
        plp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        plp.rightMargin = Ui.dp(c, 64);
        plp.bottomMargin = Ui.dp(c, 16);
        addView(poster, plp);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LayoutParams clp = new LayoutParams(Math.round(Ui.screenW(c) * 0.5f), -2);
        clp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        clp.leftMargin = Ui.dp(c, 36);
        clp.bottomMargin = Ui.dp(c, 24);
        addView(col, clp);

        tag = Ui.limeTag(c, "FILME");
        col.addView(tag, new LinearLayout.LayoutParams(-2, -2));

        titleTv = Ui.title(c, "", 40, C.TEXT);
        titleTv.setMaxLines(2);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(c, 12);
        col.addView(titleTv, tlp);

        metaTv = Ui.text(c, "", 18, C.TEXT);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.topMargin = Ui.dp(c, 10);
        col.addView(metaTv, mlp);

        plotTv = Ui.text(c, "", 17, C.TEXT2);
        plotTv.setMaxLines(3);
        plotTv.setEllipsize(TextUtils.TruncateAt.END);
        plotTv.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams ptlp = new LinearLayout.LayoutParams(-2, -2);
        ptlp.topMargin = Ui.dp(c, 10);
        col.addView(plotTv, ptlp);

        button = Ui.button(c, "Assistir Agora", Ui.PRIMARY);
        button.setTypeface(Ui.bold(c));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(c, 280), -2);
        blp.topMargin = Ui.dp(c, 18);
        col.addView(button, blp);

        dots = new LinearLayout(c);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams dlp = new LayoutParams(-2, -2);
        dlp.gravity = Gravity.BOTTOM | Gravity.START;
        dlp.leftMargin = Ui.dp(c, 36);
        dlp.bottomMargin = Ui.dp(c, 12);
        addView(dots, dlp);

        button.setOnClickListener(v -> {
            if (idx >= 0 && idx < items.size()) {
                Nav.openDetail(act, items.get(idx), true);
            }
        });
        button.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return true; // para cima no banner: não acontece nada
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN || items.size() < 2) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                show((idx + 1) % items.size());
                restartTimer();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (idx <= 0) {
                    return false; // no primeiro destaque: esquerda vai para o menu
                }
                show(idx - 1);
                restartTimer();
                return true;
            }
            return false;
        });
    }

    TextView button() {
        return button;
    }

    boolean hasItems() {
        return !items.isEmpty();
    }

    void setItems(List<XtreamApi.Item> list) {
        items.clear();
        items.addAll(list);
        idx = -1;
        buildDots();
        if (!items.isEmpty()) {
            show(0);
        }
        restartTimer();
    }

    /**
     * Troca a lista de destaques SEM mexer no que está na tela agora (usado quando chega o
     * segundo Top 10). O destaque atual continua em primeiro lugar e a contagem não zera.
     */
    void replaceItems(List<XtreamApi.Item> list) {
        if (items.isEmpty() || idx < 0 || idx >= items.size()) {
            setItems(list);
            return;
        }
        XtreamApi.Item cur = items.get(idx);
        String curKey = UserLists.keyOf(cur);
        List<XtreamApi.Item> next = new ArrayList<>();
        next.add(cur);
        for (XtreamApi.Item it : list) {
            if (next.size() >= 5) {
                break;
            }
            if (!UserLists.keyOf(it).equals(curKey)) {
                next.add(it);
            }
        }
        items.clear();
        items.addAll(next);
        idx = 0;
        shownFor = 0;
        buildDots();
        paintDots();
    }

    private void buildDots() {
        dots.removeAllViews();
        Context c = getContext();
        for (int i = 0; i < items.size(); i++) {
            View d = new View(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 8),
                    Ui.dp(c, 8));
            lp.rightMargin = Ui.dp(c, 6);
            dots.addView(d, lp);
        }
        paintDots();
    }

    private void paintDots() {
        Context c = getContext();
        for (int d = 0; d < dots.getChildCount(); d++) {
            View dot = dots.getChildAt(d);
            boolean on = d == idx;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.width = Ui.dp(c, on ? 28 : 8);
            dot.setLayoutParams(lp);
            dot.setBackground(Ui.shape(on ? C.LIME : C.TEXT3, Ui.dp(c, 4), 0, 0));
        }
    }

    /** Começa a girar. */
    void start() {
        running = true;
        paused = false;
        restartTimer();
    }

    /** Pausa (a janela foi para trás). */
    void pause() {
        paused = true;
        handler.removeCallbacks(tick);
    }

    /** Volta a girar (a janela voltou). */
    void resume() {
        if (running && paused) {
            paused = false;
            restartTimer();
        }
    }

    void stop() {
        running = false;
        paused = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void show(int i) {
        if (i < 0 || i >= items.size()) {
            return;
        }
        idx = i;
        final XtreamApi.Item it = items.get(i);
        Context c = getContext();
        boolean series = it.kind == XtreamApi.Kind.SERIES;
        tag.setText(series ? "SÉRIE" : "FILME");
        titleTv.setText(it.name);
        metaTv.setText(buildMeta("", it.rating, ""));
        plotTv.setText("");
        button.setText(series ? "Assistir T1 Ep. 1" : "Assistir Agora");
        ImageLoader.load(poster, it.icon, 480);
        backdrop.setImageDrawable(null);
        paintDots();
        final int mine = i;
        shownFor = i;
        owner.bg(() -> Catalog.details(c, it), new Bg.Done<XtreamApi.Details>() {
            @Override
            public void ok(XtreamApi.Details d) {
                if (shownFor != mine) {
                    return;
                }
                String img = !d.backdrop.isEmpty() ? d.backdrop : "";
                if (!img.isEmpty()) {
                    ImageLoader.load(backdrop, img, 1280);
                }
                plotTv.setText(d.plot.isEmpty() ? "Sem sinopse nesta lista." : d.plot);
                metaTv.setText(buildMeta(d.year, it.rating > 0 ? it.rating : parse(d.rating),
                        d.duration));
            }

            @Override
            public void fail(Exception e) {
                if (shownFor == mine) {
                    plotTv.setText("Sem sinopse nesta lista.");
                }
            }
        });
    }

    private static double parse(String s) {
        try {
            return s == null || s.isEmpty() ? 0 : Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String buildMeta(String year, double rating, String duration) {
        StringBuilder b = new StringBuilder();
        if (year != null && !year.isEmpty()) {
            b.append(year);
        }
        if (rating > 0) {
            if (b.length() > 0) {
                b.append("   \u2022   ");
            }
            b.append(String.format(java.util.Locale.US, "\u2605 %.1f", rating));
        }
        if (duration != null && !duration.isEmpty()) {
            if (b.length() > 0) {
                b.append("   \u2022   ");
            }
            b.append(duration);
        }
        return b.toString();
    }
}

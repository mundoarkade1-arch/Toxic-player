package com.asccine.tvbox;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Barra lateral fina (só ícones, 72dp). Quando recebe foco, mostra os nomes num painel que
 * fica POR CIMA do conteúdo, sem refazer o layout das telas (para não pesar).
 */
final class Sidebar extends LinearLayout {

    static final int PROFILE = 0;
    static final int FAVORITES = 1;
    static final int SEARCH = 2;
    static final int HOME = 3;
    static final int MOVIES = 4;
    static final int SERIES = 5;
    static final int EXPLORE = 6;
    static final int REFRESH = 7;
    static final int SETTINGS = 8;

    interface Listener {
        void onSelect(int id);

        /** Seta para a direita: voltar ao conteúdo. Devolve true se tratou. */
        boolean onRight();

        /** A pessoa mexeu na barra (cancela o foco automático no conteúdo). */
        void onTouched();
    }

    private static final class Row {
        final int id;
        final LinearLayout view;
        final View marker;
        final ImageView icon;
        final TextView label;
        final ImageView avatar;

        Row(int id, LinearLayout view, View marker, ImageView icon, TextView label,
            ImageView avatar) {
            this.id = id;
            this.view = view;
            this.marker = marker;
            this.icon = icon;
            this.label = label;
            this.avatar = avatar;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;
    private final int narrow;
    private final int wide;
    private int active = HOME;

    Sidebar(Context c, Listener listener, String profileName, int profileAvatar) {
        super(c);
        this.listener = listener;
        narrow = Ui.dp(c, 72);
        wide = Ui.dp(c, 250);
        setOrientation(VERTICAL);
        setBackgroundColor(C.BG);
        setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 18));
        setLayoutParams(new FrameLayout.LayoutParams(narrow, -1));

        addProfile(c, profileName, profileAvatar);
        addGap(c, 14);
        addRow(c, HOME, R.drawable.ic_home, "Início");
        addRow(c, SEARCH, R.drawable.ic_search, "Buscar");
        addRow(c, FAVORITES, R.drawable.ic_bookmark, "Favoritos");
        addRow(c, MOVIES, R.drawable.ic_film, "Filmes");
        addRow(c, SERIES, R.drawable.ic_tv, "Séries");
        addRow(c, EXPLORE, R.drawable.ic_grid, "Explorar");
        View spacer = new View(c);
        addView(spacer, new LayoutParams(1, 0, 1f));
        addRow(c, REFRESH, R.drawable.ic_refresh, "Atualizar");
        addRow(c, SETTINGS, R.drawable.ic_settings, "Ajustes");
        linkFocus();
        setActive(HOME);
    }

    /**
     * Foco explícito da barra: cada item aponta para o de cima e o de baixo (o primeiro aponta
     * para si mesmo em cima, o último em baixo). Assim o foco nunca sai da barra por engano
     * e não depende da busca automática de foco do Android.
     */
    private void linkFocus() {
        for (int i = 0; i < rows.size(); i++) {
            View v = rows.get(i).view;
            v.setId(View.generateViewId());
        }
        for (int i = 0; i < rows.size(); i++) {
            View v = rows.get(i).view;
            v.setNextFocusUpId(rows.get(Math.max(0, i - 1)).view.getId());
            v.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).view.getId());
            v.setNextFocusLeftId(v.getId());
        }
    }

    /** Foco no item da barra mais perto da altura y (em pixels da tela). */
    void focusNearest(int y) {
        View best = null;
        int bestD = Integer.MAX_VALUE;
        int[] loc = new int[2];
        for (Row r : rows) {
            r.view.getLocationOnScreen(loc);
            int d = Math.abs(loc[1] + r.view.getHeight() / 2 - y);
            if (d < bestD) {
                bestD = d;
                best = r.view;
            }
        }
        if (best != null) {
            best.requestFocus();
        }
    }

    /** Põe o foco no item vizinho (delta -1 = para cima, +1 = para baixo). Nas pontas, fica. */
    private void moveFocus(View from, int delta) {
        int at = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).view == from) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return;
        }
        int to = Math.max(0, Math.min(rows.size() - 1, at + delta));
        if (to != at) {
            rows.get(to).view.requestFocus();
        }
    }

    private void addGap(Context c, int dp) {
        addView(new View(c), new LayoutParams(1, Ui.dp(c, dp)));
    }

    private LinearLayout makeRow(Context c, int id) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(Ui.focusBg(c, 0x00000000, C.SURFACE_FOCUS, 14, 0x00000000, 0));
        LayoutParams lp = new LayoutParams(-1, Ui.dp(c, 52));
        lp.setMargins(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
        addView(row, lp);
        row.setOnClickListener(v -> listener.onSelect(id));
        row.setOnFocusChangeListener((v, has) -> post(this::syncExpanded));
        row.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_RIGHT && keyCode != KeyEvent.KEYCODE_DPAD_UP
                    && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                    && keyCode != KeyEvent.KEYCODE_DPAD_LEFT) {
                return false;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Sai da barra e vai para o primeiro item visível da tela atual.
                listener.onRight();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return true;
            }
            // Cima e baixo: só andam entre os itens da barra.
            listener.onTouched();
            moveFocus(v, keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
            return true;
        });
        return row;
    }

    private void addProfile(Context c, String name, int avatarIdx) {
        LinearLayout row = makeRow(c, PROFILE);
        View marker = new View(c);
        marker.setVisibility(INVISIBLE);
        row.addView(marker, new LayoutParams(Ui.dp(c, 3), Ui.dp(c, 3)));
        ImageView av = new ImageView(c);
        av.setImageResource(Profiles.avatarRes(avatarIdx));
        av.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LayoutParams alp = new LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
        alp.leftMargin = Ui.dp(c, 9);
        row.addView(av, alp);
        TextView label = Ui.text(c, name, 18, C.TEXT);
        label.setVisibility(GONE);
        LayoutParams llp = new LayoutParams(0, -2, 1f);
        llp.leftMargin = Ui.dp(c, 14);
        row.addView(label, llp);
        rows.add(new Row(PROFILE, row, marker, null, label, av));
    }

    private void addRow(Context c, int id, int iconRes, String text) {
        LinearLayout row = makeRow(c, id);
        View marker = new View(c);
        marker.setBackground(Ui.shape(C.LIME, Ui.dp(c, 2), 0, 0));
        marker.setVisibility(INVISIBLE);
        row.addView(marker, new LayoutParams(Ui.dp(c, 4), Ui.dp(c, 28)));
        ImageView icon = new ImageView(c);
        icon.setImageResource(iconRes);
        icon.setColorFilter(C.TEXT);
        LayoutParams ilp = new LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28));
        ilp.leftMargin = Ui.dp(c, 14);
        row.addView(icon, ilp);
        TextView label = Ui.text(c, text, 18, C.TEXT);
        label.setVisibility(GONE);
        LayoutParams llp = new LayoutParams(0, -2, 1f);
        llp.leftMargin = Ui.dp(c, 16);
        row.addView(label, llp);
        rows.add(new Row(id, row, marker, icon, label, null));
    }

    /** Marca o item da tela atual (barrinha e ícone em limão). */
    void setActive(int id) {
        active = id;
        for (Row r : rows) {
            boolean on = r.id == id;
            r.marker.setVisibility(on ? VISIBLE : INVISIBLE);
            if (r.icon != null) {
                r.icon.setColorFilter(on ? C.LIME : C.TEXT);
            }
            r.label.setTextColor(on ? C.LIME : C.TEXT);
        }
    }

    /** Põe o foco no item da tela atual. */
    void focusActive() {
        for (Row r : rows) {
            if (r.id == active) {
                r.view.requestFocus();
                return;
            }
        }
    }

    private void syncExpanded() {
        boolean open = hasFocus();
        for (Row r : rows) {
            r.label.setVisibility(open ? VISIBLE : GONE);
        }
        setBackgroundColor(open ? C.SURFACE : C.BG);
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        int w = open ? wide : narrow;
        if (lp != null && lp.width != w) {
            lp.width = w;
            setLayoutParams(lp);
        }
    }
}

package com.asccine.tvbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Perfis. Três modos (pelo Intent):
 * - normal: "Quem está assistindo?" (aparece TODA vez que o app abre);
 * - "manage": gerenciar perfis (vem dos Ajustes);
 * - "welcome": primeira vez, criar o primeiro perfil (escrever o nome ou usar "Visitante").
 * A lista (login) é única do aparelho: criar ou trocar de perfil nunca pede login.
 */
public class ProfilesActivity extends Activity {

    private boolean manage;
    private boolean welcome;
    private boolean fromSettings;
    private LinearLayout row;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AppPrefs.applyWindow(this);
        manage = getIntent().getBooleanExtra("manage", false);
        fromSettings = manage;
        welcome = getIntent().getBooleanExtra("welcome", false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(C.BG);
        setContentView(root);
        render();
    }

    private void render() {
        root.removeAllViews();
        if (welcome) {
            renderWelcome();
            return;
        }
        TextView title = Ui.title(this, manage ? Ui.highlight("Gerenciar perfis", "perfis", C.LIME)
                : Ui.highlight("Quem está assistindo?", "assistindo", C.LIME), 44, C.TEXT);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-2, -2));
        TextView sub = Ui.text(this, manage ? "ESCOLHA UM PERFIL PARA MUDAR"
                : "ESCOLHA UM PERFIL PARA COMEÇAR", 16, C.TEXT2);
        sub.setLetterSpacing(0.15f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(this, 10);
        root.addView(sub, slp);

        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = Ui.dp(this, 44);
        root.addView(row, rlp);

        List<Profiles.P> list = Profiles.list(this);
        String active = Profiles.currentId(this);
        View first = null;
        for (Profiles.P p : list) {
            View v = profileView(p, p.id.equals(active));
            row.addView(v);
            if (first == null || p.id.equals(active)) {
                first = v;
            }
        }
        if (list.size() < Profiles.MAX) {
            row.addView(addView());
        }
        if (first != null) {
            first.requestFocus();
        }
        if (manage) {
            TextView back = Ui.button(this, "Voltar", Ui.DARK);
            back.setOnClickListener(v -> leaveManage());
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 200), -2);
            blp.topMargin = Ui.dp(this, 40);
            root.addView(back, blp);
        } else {
            TextView edit = Ui.button(this, "Editar perfis", Ui.DARK);
            edit.setOnClickListener(v -> {
                manage = true;
                render();
            });
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(Ui.dp(this, 240), -2);
            elp.topMargin = Ui.dp(this, 40);
            root.addView(edit, elp);
        }
    }

    /** Sai do modo de edição: volta para os Ajustes ou para "Quem está assistindo?". */
    private void leaveManage() {
        if (fromSettings) {
            finish();
        } else {
            manage = false;
            render();
        }
    }

    @Override
    public void onBackPressed() {
        if (manage && !fromSettings && !welcome) {
            leaveManage();
        } else {
            super.onBackPressed();
        }
    }

    // ---------- Primeira vez: criar o primeiro perfil ----------

    private void renderWelcome() {
        TextView title = Ui.title(this, Ui.highlight("Crie seu perfil", "perfil", C.LIME), 44,
                C.TEXT);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-2, -2));
        TextView sub = Ui.text(this, "ESCREVA SEU NOME OU USE O PERFIL VISITANTE", 16, C.TEXT2);
        sub.setLetterSpacing(0.15f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(this, 10);
        root.addView(sub, slp);

        ImageView av = new ImageView(this);
        av.setImageResource(Profiles.avatarRes(0));
        av.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(this, 130),
                Ui.dp(this, 130));
        alp.topMargin = Ui.dp(this, 30);
        root.addView(av, alp);

        TextView write = Ui.button(this, "Escrever meu nome", Ui.PRIMARY);
        write.setTypeface(Ui.bold(this));
        write.setOnClickListener(v -> askFirstName());
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(Ui.dp(this, 420), -2);
        wlp.topMargin = Ui.dp(this, 30);
        root.addView(write, wlp);

        TextView guest = Ui.button(this, "Usar o perfil Visitante", Ui.DARK);
        guest.setOnClickListener(v -> {
            Profiles.markWelcomed(this);
            choose(Profiles.list(this).get(0));
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.dp(this, 420), -2);
        glp.topMargin = Ui.dp(this, 14);
        root.addView(guest, glp);
        write.requestFocus();
    }

    /** Escreve o nome, escolhe o avatar e transforma o perfil padrão no perfil da pessoa. */
    private void askFirstName() {
        SearchKeyboard.show(this, "Nome do seu perfil", "Confirmar", 1,
                new SearchKeyboard.Listener() {
                    @Override
                    public void onSearch(final String text) {
                        Dialogs.avatars(ProfilesActivity.this, text, 0, idx -> {
                            String id = Profiles.firstId(ProfilesActivity.this);
                            Profiles.update(ProfilesActivity.this, id, text, idx);
                            Profiles.markWelcomed(ProfilesActivity.this);
                            choose(Profiles.list(ProfilesActivity.this).get(0));
                        });
                    }

                    @Override
                    public void onCancel() {
                    }
                });
    }

    // ---------- Perfis ----------

    private View profileView(final Profiles.P p, boolean active) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setLongClickable(true);
        int pad = Ui.dp(this, 10);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(Ui.focusBg(this, 0x00000000, C.SURFACE, 22, 0x00000000, 0));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 190), -2);
        blp.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        box.setLayoutParams(blp);

        FrameLayout av = new FrameLayout(this);
        ImageView img = new ImageView(this);
        img.setImageResource(Profiles.avatarRes(p.avatar));
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        av.addView(img, new FrameLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 150)));
        if (active) {
            TextView badge = Ui.limeTag(this, "ATIVO \u2713");
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2);
            bl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            bl.bottomMargin = Ui.dp(this, 6);
            av.addView(badge, bl);
        }
        box.addView(av, new LinearLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 150)));
        TextView name = Ui.title(this, p.name, 20, C.TEXT);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = Ui.dp(this, 12);
        box.addView(name, nlp);

        box.setOnClickListener(v -> {
            if (manage) {
                manageMenu(p);
            } else {
                choose(p);
            }
        });
        box.setOnLongClickListener(v -> {
            manageMenu(p);
            return true;
        });
        return box;
    }

    private View addView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setFocusable(true);
        box.setClickable(true);
        int pad = Ui.dp(this, 10);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(Ui.focusBg(this, 0x00000000, C.SURFACE, 22, 0x00000000, 0));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 190), -2);
        blp.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        box.setLayoutParams(blp);
        TextView plus = Ui.title(this, "+", 56, C.LIME);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(Ui.shape(C.SURFACE, Ui.dp(this, 75), C.LIME_40, Ui.dp(this, 2)));
        box.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 150)));
        TextView t = Ui.title(this, "+ ADICIONAR", 16, C.TEXT2);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(this, 12);
        box.addView(t, tlp);
        box.setOnClickListener(v -> addProfile());
        return box;
    }

    /** Usa este perfil. A lista do aparelho já vale para todos: não pede login. */
    private void choose(Profiles.P p) {
        Profiles.setCurrent(this, p.id);
        boolean hasList = Catalog.creds(this) != null;
        Intent i = new Intent(this, hasList ? ShellActivity.class : LoginActivity.class);
        if (hasList) {
            // Se já havia uma tela principal aberta (vindo dos Ajustes), ela é refeita.
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        startActivity(i);
        finish();
    }

    private void addProfile() {
        SearchKeyboard.show(this, "Nome do novo perfil", "Confirmar", 1,
                new SearchKeyboard.Listener() {
                    @Override
                    public void onSearch(final String text) {
                        Dialogs.avatars(ProfilesActivity.this, text, 0, idx -> {
                            Profiles.P np = Profiles.add(ProfilesActivity.this, text, idx);
                            if (np == null) {
                                Ui.toast(ProfilesActivity.this, "O limite é de 5 perfis.");
                            }
                            render();
                        });
                    }

                    @Override
                    public void onCancel() {
                    }
                });
    }

    private void manageMenu(final Profiles.P p) {
        Dialogs.choose(this, p.name, new String[]{"Usar este perfil", "Mudar o nome",
                "Mudar o avatar", "Excluir perfil"}, i -> {
            if (i == 0) {
                choose(p);
            } else if (i == 1) {
                SearchKeyboard.show(this, "Novo nome", "Confirmar", 1,
                        new SearchKeyboard.Listener() {
                            @Override
                            public void onSearch(String text) {
                                Profiles.update(ProfilesActivity.this, p.id, text, p.avatar);
                                render();
                            }

                            @Override
                            public void onCancel() {
                            }
                        });
            } else if (i == 2) {
                Dialogs.avatars(this, p.name, p.avatar, idx -> {
                    Profiles.update(this, p.id, p.name, idx);
                    render();
                });
            } else {
                Dialogs.confirm(this, "Excluir " + p.name,
                        "Isso apaga os favoritos, os bloqueados, o Continue assistindo e o que "
                                + "este perfil já assistiu. A lista do aparelho continua.",
                        "Excluir", () -> {
                            if (!Profiles.remove(this, p.id)) {
                                Ui.toast(this, "Precisa existir pelo menos um perfil.");
                            }
                            render();
                        });
            }
        });
    }
}

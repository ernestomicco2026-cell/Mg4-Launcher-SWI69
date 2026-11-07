package com.mg4.launcher.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Conferma a tutto schermo nello stile del launcher: titolo, spiegazione, "Annulla" e pulsante principale. */
public final class Confirm {
    private Confirm() {}

    public static void show(FrameLayout root, Theme t, String title, String msg, String cancel, String ok, Runnable onOk) {
        show(root, t, title, msg, cancel, ok, onOk, null);
    }

    /** 0.4.2: come show, con un'azione anche sul tasto secondario (es. OK di un avviso da remoto). */
    public static void show(FrameLayout root, Theme t, String title, String msg, String cancel, String ok, Runnable onOk, Runnable onCancel) {
        Context c = root.getContext();
        FrameLayout dim = new FrameLayout(c);
        dim.setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        dim.setClickable(true);
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.round(t.panel, Ui.dp(c, 28)));
        card.setPadding(Ui.dp(c, 40), Ui.dp(c, 34), Ui.dp(c, 40), Ui.dp(c, 30));
        card.setClickable(true);
        card.addView(Ui.text(c, title, 30, t.text, Ui.MEDIUM));
        TextView m = Ui.text(c, msg, 20, t.sub, Ui.REGULAR);
        m.setLineSpacing(0, 1.15f);
        card.addView(m, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 16), 0, 0));
        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.END);
        btns.addView(button(c, t, cancel, false, v -> {
            ((ViewGroup) dim.getParent()).removeView(dim);
            if (onCancel != null) onCancel.run();
        }), Ui.lp(-2, Ui.tp(c, 60)));
        btns.addView(button(c, t, ok, true, v -> {
            ((ViewGroup) dim.getParent()).removeView(dim);
            onOk.run();
        }), Ui.margins(Ui.lp(-2, Ui.tp(c, 60)), Ui.dp(c, 12), 0, 0, 0));
        card.addView(btns, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 28), 0, 0));
        dim.addView(card, new FrameLayout.LayoutParams(Ui.dp(c, 980), -2, Gravity.CENTER));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    }

    public interface OnText {
        void text(String value);
    }

    /** Campo di testo (0.9.6: codice del paese dei loghi). La tastiera è quella del sistema. */
    public static void input(FrameLayout root, Theme t, String title, String msg, String initial, String hint,
                             String cancel, String ok, OnText onOk) {
        Context c = root.getContext();
        FrameLayout dim = new FrameLayout(c);
        dim.setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        dim.setClickable(true);
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.round(t.panel, Ui.dp(c, 28)));
        card.setPadding(Ui.dp(c, 40), Ui.dp(c, 34), Ui.dp(c, 40), Ui.dp(c, 30));
        card.setClickable(true);
        card.addView(Ui.text(c, title, 30, t.text, Ui.MEDIUM));
        TextView m = Ui.text(c, msg, 20, t.sub, Ui.REGULAR);
        m.setLineSpacing(0, 1.15f);
        card.addView(m, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 16), 0, 0));
        android.widget.EditText e = new android.widget.EditText(c);
        e.setSingleLine(true);
        e.setText(initial == null ? "" : initial);
        e.setHint(hint);
        e.setTextSize(26);
        e.setTextColor(t.text);
        e.setHintTextColor(t.sub);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 12));
        e.setBackground(Ui.round(t.chip, Ui.dp(c, 16)));
        e.setSelection(e.getText().length());
        card.addView(e, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 22), 0, 0));
        Runnable close = () -> {
            try {
                ((android.view.inputmethod.InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(e.getWindowToken(), 0);
            } catch (Throwable ignored) {
            }
            ((ViewGroup) dim.getParent()).removeView(dim);
        };
        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.END);
        btns.addView(button(c, t, cancel, false, v -> close.run()), Ui.lp(-2, Ui.tp(c, 60)));
        btns.addView(button(c, t, ok, true, v -> {
            close.run();
            onOk.text(e.getText().toString());
        }), Ui.margins(Ui.lp(-2, Ui.tp(c, 60)), Ui.dp(c, 12), 0, 0, 0));
        card.addView(btns, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 28), 0, 0));
        dim.addView(card, new FrameLayout.LayoutParams(Ui.dp(c, 980), -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        ((FrameLayout.LayoutParams) card.getLayoutParams()).topMargin = Ui.dp(c, 60);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        e.requestFocus();
        e.postDelayed(() -> {
            try {
                ((android.view.inputmethod.InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(e, 0);
            } catch (Throwable ignored) {
            }
        }, 200);
    }

    public interface OnPick {
        void picked(int index);
    }

    /**
     * Scelta tra pochi valori (es. limite di ricarica, corrente): titolo, spiegazione, un pulsante per valore
     * (quello attuale evidenziato) e "Annulla". Toccare un valore chiude e lo applica.
     */
    public static void pick(FrameLayout root, Theme t, String title, String msg, String[] options, int selected, String cancel, OnPick onPick) {
        Context c = root.getContext();
        FrameLayout dim = new FrameLayout(c);
        dim.setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        dim.setClickable(true);
        dim.setOnClickListener(v -> ((ViewGroup) dim.getParent()).removeView(dim));
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.round(t.panel, Ui.dp(c, 28)));
        card.setPadding(Ui.dp(c, 40), Ui.dp(c, 34), Ui.dp(c, 40), Ui.dp(c, 30));
        card.setClickable(true);
        card.addView(Ui.text(c, title, 30, t.text, Ui.MEDIUM));
        if (msg != null && !msg.isEmpty()) {
            TextView m = Ui.text(c, msg, 20, t.sub, Ui.REGULAR);
            m.setLineSpacing(0, 1.15f);
            card.addView(m, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 16), 0, 0));
        }
        LinearLayout opts = Ui.row(c);
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            TextView b = Ui.text(c, options[i], Ui.ts(24), on ? t.panel : t.text, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setBackground(on ? Ui.round(t.text, Ui.tp(c, 18)) : Ui.pressable(c, t.chip, 18 * Ui.K, t));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                ((ViewGroup) dim.getParent()).removeView(dim);
                if (idx != selected) onPick.picked(idx);
            });
            opts.addView(b, Ui.margins(Ui.lpWeight(0, Ui.tp(c, 84), 1), i == 0 ? 0 : Ui.dp(c, 12), 0, 0, 0));
        }
        card.addView(opts, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 26), 0, 0));
        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.END);
        btns.addView(button(c, t, cancel, false, v -> ((ViewGroup) dim.getParent()).removeView(dim)), Ui.lp(-2, Ui.tp(c, 60)));
        card.addView(btns, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 24), 0, 0));
        dim.addView(card, new FrameLayout.LayoutParams(Ui.dp(c, 1080), -2, Gravity.CENTER));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    }

    /** 0.4.2: scelta da un elenco lungo (es. l'app del pulsante in alto a destra): righe che scorrono. */
    public static void list(FrameLayout root, Theme t, String title, String[] options, String cancel, OnPick onPick) {
        Context c = root.getContext();
        FrameLayout dim = new FrameLayout(c);
        dim.setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        dim.setClickable(true);
        dim.setOnClickListener(v -> ((ViewGroup) dim.getParent()).removeView(dim));
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.round(t.panel, Ui.dp(c, 28)));
        card.setPadding(Ui.dp(c, 40), Ui.dp(c, 30), Ui.dp(c, 40), Ui.dp(c, 24));
        card.setClickable(true);
        card.addView(Ui.text(c, title, 28, t.text, Ui.MEDIUM));
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        LinearLayout items = Ui.column(c);
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView b = Ui.oneLine(Ui.text(c, options[i], Ui.ts(22), t.text, Ui.MEDIUM));
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setPadding(Ui.dp(c, 22), 0, Ui.dp(c, 22), 0);
            b.setBackground(Ui.pressable(c, t.chip, 16 * Ui.K, t));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                ((ViewGroup) dim.getParent()).removeView(dim);
                onPick.picked(idx);
            });
            items.addView(b, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.tp(c, 64)), 0, i == 0 ? 0 : Ui.dp(c, 8), 0, 0));
        }
        sv.addView(items);
        card.addView(sv, Ui.margins(new LinearLayout.LayoutParams(-1, Math.min(Ui.dp(c, 400), options.length * (Ui.tp(c, 64) + Ui.dp(c, 8)))), 0, Ui.dp(c, 18), 0, 0));
        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.END);
        btns.addView(button(c, t, cancel, false, v -> ((ViewGroup) dim.getParent()).removeView(dim)), Ui.lp(-2, Ui.tp(c, 60)));
        card.addView(btns, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 16), 0, 0));
        dim.addView(card, new FrameLayout.LayoutParams(Ui.dp(c, 820), -2, Gravity.CENTER));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    }

    public interface OnTime {
        void set(int hour, int minute);
    }

    /** 0.4.2 (punto 7d): scelta di un orario con pulsanti − / + per ore (1 h) e minuti (5 min). */
    public static void time(FrameLayout root, Theme t, String title, int hour, int minute, String cancel, String ok, OnTime onSet) {
        Context c = root.getContext();
        final int[] v = {Math.max(0, Math.min(23, hour)), Math.max(0, Math.min(59, minute)) / 5 * 5};
        FrameLayout dim = new FrameLayout(c);
        dim.setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        dim.setClickable(true);
        dim.setOnClickListener(x -> ((ViewGroup) dim.getParent()).removeView(dim));
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.round(t.panel, Ui.dp(c, 28)));
        card.setPadding(Ui.dp(c, 40), Ui.dp(c, 30), Ui.dp(c, 40), Ui.dp(c, 24));
        card.setClickable(true);
        card.addView(Ui.text(c, title, 28, t.text, Ui.MEDIUM));
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.CENTER);
        TextView[] tv = new TextView[2];
        for (int k = 0; k < 2; k++) {
            final int part = k;
            LinearLayout col = Ui.column(c);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView plus = Ui.text(c, "+", Ui.ts(34), t.text, Ui.MEDIUM);
            TextView minus = Ui.text(c, "−", Ui.ts(34), t.text, Ui.MEDIUM);
            tv[k] = Ui.text(c, "", Ui.ts(56), t.text, Ui.REGULAR);
            tv[k].setGravity(Gravity.CENTER);
            for (TextView b : new TextView[]{plus, minus}) {
                b.setGravity(Gravity.CENTER);
                b.setBackground(Ui.pressable(c, t.chip, 18 * Ui.K, t));
                b.setClickable(true);
            }
            View.OnClickListener step = x -> {
                int d = x == plus ? 1 : -1;
                if (part == 0) v[0] = (v[0] + d + 24) % 24;
                else v[1] = (v[1] + 5 * d + 60) % 60;
                tv[0].setText(String.format(java.util.Locale.ROOT, "%02d", v[0]));
                tv[1].setText(String.format(java.util.Locale.ROOT, "%02d", v[1]));
            };
            plus.setOnClickListener(step);
            minus.setOnClickListener(step);
            col.addView(plus, Ui.lp(Ui.tp(c, 120), Ui.tp(c, 64)));
            col.addView(tv[k], Ui.lp(Ui.tp(c, 120), Ui.tp(c, 90)));
            col.addView(minus, Ui.lp(Ui.tp(c, 120), Ui.tp(c, 64)));
            row.addView(col);
            if (k == 0) row.addView(Ui.text(c, ":", Ui.ts(56), t.text, Ui.REGULAR), Ui.margins(Ui.lp(-2, -2), Ui.dp(c, 18), 0, Ui.dp(c, 18), 0));
        }
        tv[0].setText(String.format(java.util.Locale.ROOT, "%02d", v[0]));
        tv[1].setText(String.format(java.util.Locale.ROOT, "%02d", v[1]));
        card.addView(row, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 20), 0, 0));
        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.END);
        btns.addView(button(c, t, cancel, false, x -> ((ViewGroup) dim.getParent()).removeView(dim)), Ui.lp(-2, Ui.tp(c, 60)));
        btns.addView(button(c, t, ok, true, x -> {
            ((ViewGroup) dim.getParent()).removeView(dim);
            onSet.set(v[0], v[1]);
        }), Ui.margins(Ui.lp(-2, Ui.tp(c, 60)), Ui.dp(c, 14), 0, 0, 0));
        card.addView(btns, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 20), 0, 0));
        dim.addView(card, new FrameLayout.LayoutParams(Ui.dp(c, 640), -2, Gravity.CENTER));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    }

    public interface OnMulti {
        /** Indici scelti; vuoto = prima voce ("Automatico"). */
        void chosen(java.util.List<Integer> indices);
    }

    /**
     * Scelta multipla su griglia (es. paesi dei loghi radio). La voce 0 è esclusiva ("Automatico"): sceglierla
     * deseleziona le altre e viceversa. "Annulla" e "OK".
     */
    public static void multi(FrameLayout root, Theme t, String title, String msg, String[] options, boolean[] checked,
                             String cancel, String ok, OnMulti onOk) {
        Context c = root.getContext();
        FrameLayout dim = new FrameLayout(c);
        dim.setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        dim.setClickable(true);
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.round(t.panel, Ui.dp(c, 28)));
        card.setPadding(Ui.dp(c, 40), Ui.dp(c, 34), Ui.dp(c, 40), Ui.dp(c, 30));
        card.setClickable(true);
        card.addView(Ui.text(c, title, 30, t.text, Ui.MEDIUM));
        if (msg != null && !msg.isEmpty()) {
            TextView m = Ui.text(c, msg, 18, t.sub, Ui.REGULAR);
            m.setLineSpacing(0, 1.15f);
            card.addView(m, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 12), 0, 0));
        }
        boolean[] on = checked.clone();
        TextView[] chips = new TextView[options.length];
        Runnable paint = () -> {
            for (int i = 0; i < chips.length; i++) {
                chips[i].setTextColor(on[i] ? t.panel : t.text);
                chips[i].setBackground(on[i] ? Ui.round(t.text, Ui.tp(c, 14)) : Ui.pressable(c, t.chip, 14 * Ui.K, t));
            }
        };
        LinearLayout grid = Ui.column(c);
        int cols = 5;
        LinearLayout row = null;
        for (int i = 0; i < options.length; i++) {
            if (i % cols == 0) {
                row = Ui.row(c);
                grid.addView(row, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, i == 0 ? 0 : Ui.dp(c, 10), 0, 0));
            }
            final int idx = i;
            TextView b = Ui.oneLine(Ui.text(c, options[i], Ui.ts(18), t.text, Ui.MEDIUM));
            b.setGravity(Gravity.CENTER);
            b.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
            b.setClickable(true);
            b.setOnClickListener(v -> {
                on[idx] = !on[idx];
                if (idx == 0 && on[0]) for (int k = 1; k < on.length; k++) on[k] = false;
                if (idx != 0 && on[idx]) on[0] = false;
                boolean any = false;
                for (boolean x : on) any |= x;
                if (!any) on[0] = true;
                paint.run();
            });
            chips[i] = b;
            row.addView(b, Ui.margins(Ui.lpWeight(0, Ui.tp(c, 60), 1), i % cols == 0 ? 0 : Ui.dp(c, 10), 0, 0, 0));
        }
        int rest = (cols - options.length % cols) % cols;
        for (int k = 0; k < rest && row != null; k++) row.addView(new View(c), Ui.margins(Ui.lpWeight(0, 1, 1), Ui.dp(c, 10), 0, 0, 0));
        paint.run();
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(grid);
        card.addView(sv, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(c, 420)), 0, Ui.dp(c, 22), 0, 0));
        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.END);
        btns.addView(button(c, t, cancel, false, v -> ((ViewGroup) dim.getParent()).removeView(dim)), Ui.lp(-2, Ui.tp(c, 60)));
        btns.addView(button(c, t, ok, true, v -> {
            ((ViewGroup) dim.getParent()).removeView(dim);
            java.util.List<Integer> out = new java.util.ArrayList<>();
            for (int i = 1; i < on.length; i++) if (on[i]) out.add(i);
            onOk.chosen(out);
        }), Ui.margins(Ui.lp(-2, Ui.tp(c, 60)), Ui.dp(c, 12), 0, 0, 0));
        card.addView(btns, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 24), 0, 0));
        dim.addView(card, new FrameLayout.LayoutParams(Ui.dp(c, 1180), -2, Gravity.CENTER));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Avviso con elenco puntato e un solo pulsante (popup "Novità"). */
    public static void notice(FrameLayout root, Theme t, String title, String subtitle, String[] items, String ok, Runnable onClose) {
        Context c = root.getContext();
        FrameLayout dim = new FrameLayout(c);
        dim.setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        dim.setClickable(true);
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.round(t.panel, Ui.dp(c, 28)));
        card.setPadding(Ui.dp(c, 44), Ui.dp(c, 36), Ui.dp(c, 44), Ui.dp(c, 30));
        card.setClickable(true);
        card.addView(Ui.text(c, title, 32, t.text, Ui.MEDIUM));
        if (subtitle != null && !subtitle.isEmpty()) {
            card.addView(Ui.text(c, subtitle, 18, t.sub, Ui.REGULAR), Ui.margins(new LinearLayout.LayoutParams(-2, -2), 0, Ui.dp(c, 8), 0, 0));
        }
        LinearLayout list = Ui.column(c);
        for (String it : items) {
            LinearLayout r = Ui.row(c);
            r.setGravity(Gravity.TOP);
            View dot = new View(c);
            dot.setBackground(Ui.round(t.text, Ui.dp(c, 4)));
            r.addView(dot, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)), 0, Ui.dp(c, 10), Ui.dp(c, 16), 0));
            TextView tv = Ui.text(c, it, 20, t.text, Ui.REGULAR);
            tv.setLineSpacing(0, 1.1f);
            r.addView(tv, Ui.lpWeight(0, -2, 1));
            list.addView(r, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 12), 0, 0));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        card.addView(sv, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 12), 0, 0));
        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.END);
        btns.addView(button(c, t, ok, true, v -> {
            ((ViewGroup) dim.getParent()).removeView(dim);
            if (onClose != null) onClose.run();
        }), Ui.lp(-2, Ui.tp(c, 60)));
        card.addView(btns, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 26), 0, 0));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 1000), -2, Gravity.CENTER);
        lp.topMargin = Ui.dp(c, 30);
        lp.bottomMargin = Ui.dp(c, 30);
        dim.addView(card, lp);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    }

    private static TextView button(Context c, Theme t, String text, boolean primary, View.OnClickListener l) {
        TextView b = Ui.text(c, text, Ui.ts(20), primary ? t.panel : t.text, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.tp(c, 28), 0, Ui.tp(c, 28), 0);
        b.setBackground(Ui.pressable(c, primary ? t.text : t.chip, 16 * Ui.K, t));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }
}

package kestrel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;

/** Everything drawn over the 3D view, at window resolution. */
final class Hud {
    private static final Color INK = new Color(250, 246, 236), SHADOW = new Color(0, 0, 0, 160), PANEL = new Color(12, 16, 24, 220),
            GOLD = new Color(242, 193, 78), QUEST = new Color(110, 180, 255), TEAL = new Color(126, 240, 200), RED = new Color(232, 80, 60), DIM = new Color(170, 176, 186),
            GREEN = new Color(94, 240, 138);
    private static final String[] ITEMS = {"Hunter's Bow", "Bombs", "Grapple Claw"};
    private final Game g;
    private final Renderer r;
    private final float[] tmp = new float[3];
    private Graphics2D g2;
    private float s;
    private int w, h, fontH = -1;
    private Font huge, big, mid, small;

    Hud(Game g, Renderer r) {
        this.g = g;
        this.r = r;
    }

    void draw(Graphics2D gfx, int width, int height) {
        g2 = gfx;
        w = width;
        h = height;
        s = h / 720f;
        if (fontH != h) {
            fontH = h;
            huge = new Font(Font.SERIF, Font.BOLD, (int) (62 * s));
            big = new Font(Font.SERIF, Font.BOLD, (int) (28 * s));
            mid = new Font(Font.SANS_SERIF, Font.BOLD, (int) (17 * s));
            small = new Font(Font.SANS_SERIF, Font.PLAIN, (int) (14 * s));
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (g.climate.lightning > 0 && g.dungeon == null) fill(new Color(255, 255, 255, (int) (110 * g.climate.lightning)), 0, 0, w, h);
        if (g.hurtFlash > 0) fill(new Color(200, 30, 20, (int) (80 * g.hurtFlash)), 0, 0, w, h);
        if (g.fade > 0) fill(new Color(0, 0, 0, (int) (255 * Math.min(1, g.fade))), 0, 0, w, h);
        switch (g.screen) {
            case TITLE -> title();
            case JOIN -> join();
            case END -> ending();
            default -> {
                if (g.coop != null) coop();
                playHud();
                switch (g.screen) {
                    case DIALOG -> dialog();
                    case FLUTE -> flute();
                    case MENU -> menu();
                    case MAP -> map();
                    case PAUSE -> pause();
                    case REST -> rest();
                    case LEVEL -> level();
                    case TRAVEL -> travel();
                    case GEAR -> gear();
                    case DEV -> devPack();
                    case OVER -> {
                        fill(new Color(20, 0, 0, (int) (200 * Math.min(1, g.overT))), 0, 0, w, h);
                        centered(huge, "You have fallen", h * .42f, INK);
                        if (g.overT > 1.5f) centered(mid, "Press Enter to rise again", h * .55f, GOLD);
                    }
                    default -> {}
                }
            }
        }
    }

    // ---- primitives ---------------------------------------------------------------------------

    private void fill(Color c, float x, float y, float fw, float fh) {
        g2.setColor(c);
        g2.fillRect((int) x, (int) y, (int) Math.ceil(fw), (int) Math.ceil(fh));
    }

    private void panel(float x, float y, float pw, float ph) {
        g2.setColor(PANEL);
        g2.fillRoundRect((int) x, (int) y, (int) pw, (int) ph, (int) (16 * s), (int) (16 * s));
        g2.setColor(new Color(242, 193, 78, 90));
        g2.setStroke(new BasicStroke(2 * s));
        g2.drawRoundRect((int) x, (int) y, (int) pw, (int) ph, (int) (16 * s), (int) (16 * s));
    }

    private void text(Font f, String t, float x, float y, Color c) {
        g2.setFont(f);
        g2.setColor(SHADOW);
        g2.drawString(t, x + 2 * s, y + 2 * s);
        g2.setColor(c);
        g2.drawString(t, x, y);
    }

    private float width(Font f, String t) {
        g2.setFont(f);
        return g2.getFontMetrics().stringWidth(t);
    }

    private void centered(Font f, String t, float y, Color c) { text(f, t, (w - width(f, t)) / 2, y, c); }

    private void diamond(float x, float y, float rad, float amount, Color c) {
        Path2D d = new Path2D.Float();
        d.moveTo(x, y - rad);
        d.lineTo(x + rad * .9f, y);
        d.lineTo(x, y + rad);
        d.lineTo(x - rad * .9f, y);
        d.closePath();
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fill(d);
        Shape clip = g2.getClip();
        g2.clipRect((int) (x - rad), (int) (y + rad - 2 * rad * amount), (int) (2 * rad), (int) (2 * rad * amount) + 1);
        g2.setColor(c);
        g2.fill(d);
        g2.setClip(clip);
        g2.setColor(INK);
        g2.setStroke(new BasicStroke(1.5f * s));
        g2.draw(d);
    }

    // ---- in-game HUD --------------------------------------------------------------------------

    private void playHud() {
        Hero hero = g.hero;
        Game.Gear gear = g.gear;
        int pips = (hero.maxHp + 3) / 4;
        for (int i = 0; i < pips; i++) {
            float x = 32 * s + (i % 10) * 28 * s, y = 34 * s + (i / 10) * 30 * s;
            boolean low = hero.hp <= 4 && (int) (g.time * 4) % 2 == 0;
            diamond(x, y, 12 * s, M.clamp((hero.hp - i * 4) / 4f, 0, 1), low ? new Color(255, 130, 100) : new Color(236, 78, 64));
        }
        float row = pips > 10 ? 30 * s : 0;
        bar(20 * s, 54 * s + row, 1.5f * hero.maxStamina * s, 7 * s, Math.max(0, hero.stamina) / hero.maxStamina, new Color(110, 214, 104));
        bar(20 * s, 66 * s + row, 1.5f * hero.maxFp * s, 7 * s, hero.fp / hero.maxFp, new Color(92, 152, 245));
        diamond(30 * s, 96 * s + row, 9 * s, 1, GREEN);
        String gl = Integer.toString(gear.gleams);
        text(mid, gl, 46 * s, 102 * s + row, INK);
        if (g.gainT > 0) text(small, "+" + g.gain, 56 * s + width(mid, gl), 102 * s + row, new Color(94, 240, 138, (int) (255 * M.clamp(g.gainT, 0, 1))));
        text(mid, "R  Draughts " + gear.flasks + "/" + gear.maxFlasks, 26 * s, h - (g.dungeon != null ? 56 : 28) * s, new Color(255, 162, 112));
        if (gear.spells != 0) {
            float bx = w - 270 * s, by = h - (g.dungeon == null ? 104 : 80) * s;
            panel(bx, by, 248 * s, 50 * s);
            text(mid, "C  " + Magic.NAME[gear.spell] + "  (" + Magic.COST[gear.spell] + ")", bx + 12 * s, by + 22 * s, new Color(150, 190, 255));
            text(small, "V  switch spell" + (gear.ascended ? "   ·   ASCENDED" : ""), bx + 12 * s, by + 40 * s, gear.ascended ? GOLD : DIM);
        }
        if (g.screen == Game.Screen.PLAY && g.messageT <= 0) {
            String goal = g.story.objective();
            float gw = width(mid, goal) + 70 * s;
            g2.setColor(new Color(12, 16, 24, 150));
            g2.fillRoundRect((int) ((w - gw) / 2), (int) (10 * s), (int) gw, (int) (30 * s), (int) (12 * s), (int) (12 * s));
            diamond((w - gw) / 2 + 22 * s, 25 * s, 7 * s, 1, GOLD);
            text(mid, goal, (w - gw) / 2 + 40 * s, 31 * s, INK);
            centered(small, "H for a hint", 56 * s, DIM);
        }
        float[] goalAt = new float[3];
        if (g.screen == Game.Screen.PLAY && g.story.objectiveTarget(goalAt)) marker(goalAt);
        if (g.screen == Game.Screen.PLAY)
            for (int q = 0; q < Story.QUESTS.length; q++) if (g.story.questTarget(q, goalAt)) questMarker(goalAt);
        if (g.horse.ridden && g.screen == Game.Screen.PLAY) centered(small, "Shift gallop  ·  Space jump  ·  click to swing  ·  E dismount  ·  G send Juniper away", h - 16 * s, DIM);
        if (g.tipT > 0 && g.tip != null && g.screen == Game.Screen.PLAY) {
            java.util.List<String> lines = wrap(g.tip, 560 * s);
            float th = lines.size() * 24 * s + 20 * s, tx = (w - 600 * s) / 2, ty = h * .62f - th;
            panel(tx, ty, 600 * s, th);
            for (int i = 0; i < lines.size(); i++) text(mid, lines.get(i), tx + 20 * s, ty + 28 * s + i * 24 * s, new Color(250, 246, 236, (int) (255 * M.clamp(g.tipT, 0, 1))));
        }
        if (gear.bow || gear.bombBag || gear.claw) {
            float bx = w - 236 * s, by = 22 * s;
            boolean tools = gear.bombBag || gear.claw;
            panel(bx, by, 214 * s, (tools ? 62 : 36) * s);
            text(mid, "Q  " + (gear.bow && gear.bowOut ? "Bow  x" + gear.arrows : Arms.WEAPON_SHORT[gear.weapon]), bx + 12 * s, by + 24 * s, INK);
            if (tools) text(small, "F  " + (gear.item == 1 ? "Bombs  x" + gear.bombs : gear.item == 2 ? "Grapple Claw" : "(X to pick)") + "     X switch", bx + 12 * s, by + 48 * s, DIM);
        }
        if (g.dungeon != null) {
            Dungeon d = g.dungeon;
            text(mid, "Keys " + d.keys + (d.bigKey ? "   ·   Great Key" : ""), 26 * s, h - 28 * s, GOLD);
        }
        if (g.messageT > 0 && g.screen == Game.Screen.PLAY) centered(big, g.message, 120 * s, new Color(250, 246, 236, (int) (255 * M.clamp(g.messageT, 0, 1))));
        if (g.prompt != null && g.screen == Game.Screen.PLAY) {
            float tw = width(mid, g.prompt), x = (w - tw) / 2 + 18 * s, y = h * .78f;
            panel(x - 46 * s, y - 25 * s, tw + 64 * s, 37 * s);
            g2.setColor(GOLD);
            g2.fillOval((int) (x - 38 * s), (int) (y - 19 * s), (int) (25 * s), (int) (25 * s));
            text(small, "E", x - 30 * s, y - 2 * s, Color.BLACK);
            text(mid, g.prompt, x, y, INK);
        }
        float[] bite = hero.st == Hero.State.AIM && hero.aimClaw ? g.clawTarget(hero) : null;
        if (bite != null && r.project(bite[0], bite[1], bite[2], tmp)) {
            float px = tmp[0] * w / r.w, py = tmp[1] * h / r.h, rad = (16 + M.sin(g.time * 8) * 2) * s;
            String what = bite[3] == 0 ? "let go to grapple" : bite[3] == 1 ? "let go to yank" : "let go to fetch";
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(3 * s));
            g2.drawOval((int) (px - rad), (int) (py - rad), (int) (rad * 2), (int) (rad * 2));
            text(small, what, px - width(small, what) / 2, py + rad + 18 * s, GOLD);
        } else if (hero.st == Hero.State.AIM && hero.aimClaw)
            centered(small, "Face a grapple post, a foe or something out of reach (range " + (int) g.clawRange() + " m)", h / 2f + 44 * s, DIM);
        if (hero.st == Hero.State.AIM) {
            g2.setColor(INK);
            g2.setStroke(new BasicStroke(2 * s));
            int cx = w / 2, cy = h / 2, k = (int) (8 * s);
            g2.drawOval(cx - k, cy - k, 2 * k, 2 * k);
            g2.fillOval(cx - 2, cy - 2, 4, 4);
        }
        Foe t = hero.target;
        float[] spot = new float[3];
        // The marker over the lock-on: a foe's weak point, a duelling friend, or (on a co-op friend's screen) where the host says.
        if (hero.locked && (hero.aimPoint(spot) ? r.project(spot[0], spot[1] + .55f, spot[2], tmp) : t != null && r.project(t.x, t.centerY() + t.radius() + .6f, t.z, tmp))) {
            float x = tmp[0] * w / r.w, y = tmp[1] * h / r.h, bob = M.sin(g.time * 6) * 4 * s;
            Polygon arrow = new Polygon();
            arrow.addPoint((int) x, (int) (y + bob));
            arrow.addPoint((int) (x - 9 * s), (int) (y - 14 * s + bob));
            arrow.addPoint((int) (x + 9 * s), (int) (y - 14 * s + bob));
            g2.setColor(GOLD);
            g2.fill(arrow);
            g2.setColor(Color.BLACK);
            g2.draw(arrow);
        }
        Foe b = g.boss;
        if (b != null && b.alive()) {
            float bw = w * .5f, x = (w - bw) / 2, y = h - 60 * s;
            fill(new Color(0, 0, 0, 160), x, y, bw, 10 * s);
            fill(new Color(200, 60, 90), x, y, bw * b.hp / b.maxHp, 10 * s);
        }
        if (g.dungeon == null) text(small, g.climate.clock() + "  ·  " + g.climate.name(), w - 150 * s, h - 22 * s, DIM);
    }

    /** A small blue diamond over a side-quest goal, shown only while it's in view and not too far. */
    private void questMarker(float[] p) {
        float dx = p[0] - r.cx, dy = p[1] - r.cy, dz = p[2] - r.cz;
        float vx = dx * r.rx + dz * r.rz, vy = dx * r.ux + dy * r.uy + dz * r.uz, vz = dx * r.fx + dy * r.fy + dz * r.fz;
        if (vz < 1 || vz > 400) return;
        float sx = (r.hw + r.foc * vx / vz) * w / r.w, sy = (r.hh - r.foc * vy / vz) * h / r.h;
        if (sx < 0 || sy < 0 || sx > w || sy > h) return;
        diamond(sx, sy + M.sin(g.time * 4 + p[0]) * 3 * s, 8 * s, 1, QUEST);
    }

    /** A gold diamond over the current goal, pinned to the screen edge (with an arrow) when it's out of view. */
    private void marker(float[] p) {
        float dx = p[0] - r.cx, dy = p[1] - r.cy, dz = p[2] - r.cz;
        float vx = dx * r.rx + dz * r.rz, vy = dx * r.ux + dy * r.uy + dz * r.uz, vz = dx * r.fx + dy * r.fy + dz * r.fz;
        float cx = w / 2f, cy = h / 2f, sx, sy;
        if (vz > .5f) {
            sx = (r.hw + r.foc * vx / vz) * w / r.w;
            sy = (r.hh - r.foc * vy / vz) * h / r.h;
        } else {
            sx = cx + (Math.abs(vx) < .01f ? 1 : vx) * 1e4f;
            sy = cy + 1e3f;
        }
        float mx = 46 * s, top = 92 * s, bottom = 110 * s;
        boolean off = vz <= .5f || sx < mx || sx > w - mx || sy < top || sy > h - bottom;
        if (off) {
            float ddx = sx - cx, ddy = sy - cy;
            float k = Math.min((w / 2f - mx) / Math.max(1e-3f, Math.abs(ddx)), ((ddy < 0 ? cy - top : h - bottom - cy)) / Math.max(1e-3f, Math.abs(ddy)));
            sx = cx + ddx * k;
            sy = cy + ddy * k;
            float l = Math.max(1e-3f, M.hypot(ddx, ddy)), ax = ddx / l, ay = ddy / l;
            Polygon tip = new Polygon();
            tip.addPoint((int) (sx + ax * 24 * s), (int) (sy + ay * 24 * s));
            tip.addPoint((int) (sx + ax * 14 * s - ay * 7 * s), (int) (sy + ay * 14 * s + ax * 7 * s));
            tip.addPoint((int) (sx + ax * 14 * s + ay * 7 * s), (int) (sy + ay * 14 * s - ax * 7 * s));
            g2.setColor(GOLD);
            g2.fill(tip);
        }
        float bob = off ? 0 : M.sin(g.time * 4) * 3 * s;
        diamond(sx, sy + bob, 11 * s, 1, GOLD);
        String dist = (int) M.dist(g.hero.x, g.hero.z, p[0], p[2]) + " m";
        text(small, dist, sx - width(small, dist) / 2, sy + 28 * s + bob, INK);
    }

    private void bar(float x, float y, float bw, float bh, float amount, Color c) {
        fill(new Color(0, 0, 0, 130), x - 2 * s, y - 2 * s, bw + 4 * s, bh + 4 * s);
        fill(c, x, y, bw * M.clamp(amount, 0, 1), bh);
    }

    private void rest() {
        fill(new Color(0, 0, 0, 120), 0, 0, w, h);
        String[] opts = g.story.restOptions();
        float pw = 360 * s, ph = 90 * s + opts.length * 34 * s, x = (w - pw) / 2, y = h * .3f;
        panel(x, y, pw, ph);
        text(big, "Lantern Shrine", x + 24 * s, y + 42 * s, GOLD);
        for (int i = 0; i < opts.length; i++) text(mid, (i == g.menuSel ? "▶  " : "    ") + opts[i], x + 24 * s, y + 86 * s + i * 34 * s, i == g.menuSel ? GOLD : INK);
        centered(small, "Rested: life, spirit and draughts restored. The monsters have returned.", y + ph + 26 * s, DIM);
    }

    private void level() {
        fill(new Color(0, 0, 0, 140), 0, 0, w, h);
        Game.Gear gear = g.gear;
        int cost = Story.levelCost(gear.level);
        float pw = 560 * s, ph = 330 * s, x = (w - pw) / 2, y = (h - ph) / 2;
        panel(x, y, pw, ph);
        text(big, "Level " + gear.level, x + 24 * s, y + 44 * s, INK);
        text(mid, "Gleams " + gear.gleams + "   ·   next level " + cost, x + 220 * s, y + 42 * s, gear.gleams >= cost ? GREEN : RED);
        for (int i = 0; i < Story.STATS.length; i++) {
            float ly = y + 92 * s + i * 40 * s;
            if (i == g.menuSel) fill(new Color(255, 255, 255, 30), x + 12 * s, ly - 24 * s, pw - 24 * s, 34 * s);
            text(mid, Story.STATS[i], x + 30 * s, ly, i == g.menuSel ? GOLD : INK);
            text(mid, Integer.toString(g.story.stat(i)), x + 200 * s, ly, INK);
            text(small, Story.STAT_HOW[i], x + 260 * s, ly, DIM);
        }
        text(small, "W/S choose  ·  E raise  ·  Esc back", x + 24 * s, y + ph - 18 * s, DIM);
    }

    private void travel() {
        fill(new Color(0, 0, 0, 140), 0, 0, w, h);
        java.util.List<Integer> spots = g.story.travelSpots();
        float pw = 420 * s, ph = 100 * s + Math.max(1, spots.size()) * 32 * s, x = (w - pw) / 2, y = h * .25f;
        panel(x, y, pw, ph);
        text(big, "Travel to...", x + 24 * s, y + 42 * s, GOLD);
        if (spots.isEmpty()) text(mid, "Rest at more shrines to travel between them.", x + 24 * s, y + 84 * s, DIM);
        for (int i = 0; i < spots.size(); i++)
            text(mid, (i == g.menuSel ? "▶  " : "    ") + Story.SHRINE_NAMES[spots.get(i)], x + 24 * s, y + 84 * s + i * 32 * s, i == g.menuSel ? GOLD : INK);
        text(small, "E travel  ·  Esc back", x + 24 * s, y + ph - 14 * s, DIM);
    }

    private void dialog() {
        float pw = Math.min(w - 80 * s, 820 * s), ph = 150 * s, x = (w - pw) / 2, y = h - ph - 30 * s;
        panel(x, y, pw, ph);
        text(mid, g.speaker, x + 24 * s, y + 30 * s, GOLD);
        String cur = g.pages[g.page].substring(0, Math.min((int) g.typed, g.pages[g.page].length()));
        float ly = y + 62 * s;
        for (String line : wrap(cur, pw - 48 * s)) {
            text(mid, line, x + 24 * s, ly, INK);
            ly += 26 * s;
        }
        if (g.options != null && g.typed >= g.pages[g.page].length()) {
            float oh = g.options.length * 30 * s + 20 * s, ox = x + pw - 330 * s, oy = y - oh - 10 * s;
            panel(ox, oy, 320 * s, oh);
            for (int i = 0; i < g.options.length; i++)
                text(mid, (i == g.menuSel ? "▶ " : "   ") + g.options[i], ox + 14 * s, oy + 32 * s + i * 30 * s, i == g.menuSel ? GOLD : INK);
        } else if (g.typed >= g.pages[g.page].length() && (int) (g.time * 3) % 2 == 0) text(mid, "▼", x + pw - 36 * s, y + ph - 16 * s, GOLD);
    }

    private java.util.List<String> wrap(String t, float max) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : t.split(" ")) {
            if (line.length() > 0 && width(mid, line + " " + word) > max) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        lines.add(line.toString());
        return lines;
    }

    private void flute() {
        float pw = 560 * s, ph = 230 * s, x = (w - pw) / 2, y = h - ph - 30 * s;
        panel(x, y, pw, ph);
        text(mid, "Windflute  -  keys 1 to 5 play notes  ·  T to stop", x + 22 * s, y + 30 * s, GOLD);
        for (int l = 0; l < 5; l++) fill(new Color(255, 255, 255, 40), x + 20 * s, y + 52 * s + l * 14 * s, pw - 40 * s, 2 * s);
        for (int i = 0; i < g.noteCount; i++) {
            int n = g.notes[i];
            float nx = x + 40 * s + i * 60 * s, ny = y + 52 * s + (4 - n) * 14 * s;
            g2.setColor(TEAL);
            g2.fillOval((int) (nx - 9 * s), (int) (ny - 7 * s), (int) (18 * s), (int) (14 * s));
            text(small, Integer.toString(n + 1), nx - 4 * s, ny + 22 * s, INK);
        }
        float ly = y + 150 * s;
        for (int k = 0; k < Story.SONGS.length; k++) {
            if ((g.gear.songs & 1 << k) == 0) continue;
            StringBuilder keys = new StringBuilder();
            for (int n : Story.SONGS[k]) keys.append(n + 1).append("  ");
            text(small, Story.SONG_NAMES[k], x + 24 * s, ly, INK);
            text(small, keys.toString(), x + 250 * s, ly, TEAL);
            ly += 22 * s;
        }
    }

    // ---- screens ------------------------------------------------------------------------------

    private void title() {
        fill(new Color(0, 0, 0, 80), 0, 0, w, h);
        centered(huge, "KESTREL  ISLE", h * .32f, INK);
        centered(big, "The Windflute", h * .32f + 46 * s, GOLD);
        String[] opts = g.story.titleOptions();
        for (int i = 0; i < opts.length; i++)
            centered(mid, (i == g.story.titleSel ? "▶  " : "") + opts[i], h * .6f + i * 34 * s, i == g.story.titleSel ? GOLD : INK);
        centered(small, "W/S choose  ·  Enter start", h * .6f + (opts.length * 34 + 22) * s, DIM);
        if (g.message != null && g.messageT > 0) centered(small, g.message, h * .93f, GOLD);
    }

    /** Typing a friend's address to join their island. */
    private void join() {
        fill(new Color(0, 0, 0, 150), 0, 0, w, h);
        centered(big, "Join a friend's island", h * .3f, INK);
        centered(small, "Type the address your friend's game shows after they choose Host co-op (like 192.168.1.20).", h * .3f + 40 * s, DIM);
        float bw = 420 * s, bx = (w - bw) / 2, by = h * .45f;
        panel(bx, by, bw, 50 * s);
        String caret = ((int) (g.time * 2) & 1) == 0 ? "|" : " ";
        centered(mid, g.joinAddress + caret, by + 32 * s, GOLD);
        centered(small, "Enter join  ·  Backspace erase  ·  Esc back", by + 90 * s, DIM);
        centered(small, "You bring your own hero and gear; what you find comes home to your save.", by + 116 * s, DIM);
        if (g.message != null && g.messageT > 0) centered(small, g.message, h * .8f, GOLD);
    }

    /** Co-op: who's here and how friends join, and a name over each other player's head. */
    private void coop() {
        Coop c = g.coop;
        StringBuilder who = new StringBuilder();
        for (Game.Player p : g.players) who.append(who.isEmpty() ? "" : ", ").append(p == g.me ? "you" : p.name);
        String line = c.hosting() ? "Co-op open at " + (c.addresses.isEmpty() ? "this computer" : String.join(" or ", c.addresses)) + "   ·   " + who
                : "Co-op on " + c.hostName + "'s island   ·   " + who;
        centered(small, line, h - 14 * s, new Color(150, 200, 255));
        if (c.joined() && c.hostScreen != Game.Screen.PLAY && c.hostScreen != Game.Screen.FLUTE && !Game.MENUS.contains(c.hostScreen))
            centered(mid, "The island waits while " + c.hostName + " talks with someone.", h * .2f, GOLD);
        float[] at = new float[3];
        for (Game.Player p : g.players) {
            if (p == g.me || !r.project(p.hero.x, p.hero.y + 2.15f, p.hero.z, at)) continue;
            float x = at[0] * w / r.w, y = at[1] * h / r.h;
            String tag = p.hero.dueling ? "⚔ " + p.name : p.name;
            int tw = g2.getFontMetrics(small).stringWidth(tag);
            fill(p.hero.dueling ? new Color(120, 20, 20, 170) : new Color(0, 0, 0, 120), x - tw / 2f - 6 * s, y - 15 * s, tw + 12 * s, 20 * s);
            text(small, tag, x - tw / 2f, y, p.hero.st == Hero.State.DEAD ? DIM : INK);
        }
        if (g.hero.dueling) centered(mid, "⚔ Ready to duel (P to stop)", h - 40 * s, new Color(255, 120, 110));
    }

    private void ending() {
        fill(new Color(0, 0, 0, (int) Math.min(220, g.overT * 80)), 0, 0, w, h);
        String[] lines = {"The Hollow King breaks apart into light, and Corvin the Keeper is at rest at last.",
                "Morning spills over Kestrel Isle. The lighthouse burns again, lit at dusk, as he asked.",
                "Maren hums the old songs on the well steps. Oskar's forge rings from dawn to dark.",
                "Tibb flies a kite over the great oak (low, mostly). Ansel's nets come up full.",
                "Hessa's sleds run north again. And Juniper still comes running when she hears her song.",
                "", "Thank you for playing.", "", "Kestrel Isle  ·  made in plain Java"};
        for (int i = 0; i < lines.length; i++) {
            float a = M.clamp(g.overT - i * .6f, 0, 1);
            centered(i == 6 ? big : mid, lines[i], h * .2f + i * 36 * s, new Color(250, 246, 236, (int) (255 * a)));
        }
        if (g.overT > 6) centered(small, "Press Enter to keep exploring", h * .88f, GOLD);
    }

    private void menu() {
        fill(new Color(0, 0, 0, 150), 0, 0, w, h);
        float pw = 840 * s, ph = 470 * s, x = (w - pw) / 2, y = (h - ph) / 2;
        panel(x, y, pw, ph);
        text(big, "Satchel", x + 24 * s, y + 44 * s, INK);
        Game.Gear gear = g.gear;
        boolean[] own = {gear.bow, gear.bombBag, gear.claw};
        int[] ammo = {gear.arrows, gear.bombs, -1};
        for (int i = 0; i < 3; i++) {
            float ix = x + 24 * s + i * 200 * s, iy = y + 70 * s;
            g2.setColor(i == gear.item && own[i] ? new Color(242, 193, 78, 60) : new Color(255, 255, 255, 18));
            g2.fillRoundRect((int) ix, (int) iy, (int) (188 * s), (int) (64 * s), 12, 12);
            text(mid, own[i] ? ITEMS[i] : "???", ix + 12 * s, iy + 28 * s, own[i] ? INK : DIM);
            if (own[i] && ammo[i] >= 0) text(small, "x" + ammo[i], ix + 12 * s, iy + 50 * s, DIM);
        }
        text(small, "A/D: choose the tool F uses  (Q swaps sword and bow)" + (gear.windcloak ? "   ·   Windcloak: Space in mid-air" : ""), x + 24 * s, y + 160 * s, DIM);
        text(mid, "Songs", x + 24 * s, y + 200 * s, GOLD);
        float ly = y + 228 * s;
        for (int k = 0; k < Story.SONGS.length; k++) {
            boolean known = (gear.songs & 1 << k) != 0;
            StringBuilder keys = new StringBuilder();
            for (int n : Story.SONGS[k]) keys.append(n + 1).append(' ');
            text(small, known ? Story.SONG_NAMES[k] + "   " + keys : "-", x + 24 * s, ly, known ? INK : DIM);
            ly += 22 * s;
        }
        text(mid, "Wardstones", x + 24 * s, y + 330 * s, GOLD);
        Color[] stone = {new Color(126, 240, 122), new Color(255, 138, 58), new Color(122, 216, 255), new Color(220, 246, 255)};
        for (int i = 0; i < 4; i++) {
            boolean got = (gear.stones & 1 << i) != 0;
            diamond(x + 44 * s + i * 120 * s, y + 368 * s, 16 * s, got ? 1 : 0, stone[i]);
            text(small, Story.STONES[i], x + 66 * s + i * 120 * s, y + 374 * s, got ? INK : DIM);
        }
        text(small, "Heart pieces " + gear.pieces % 4 + "/4   ·   Gleams " + gear.gleams, x + 24 * s, y + 420 * s, DIM);
        text(mid, "Requests", x + 540 * s, y + 200 * s, QUEST);
        float qy = y + 228 * s;
        int done = 0, open = 0;
        for (int q = 0; q < Story.QUESTS.length; q++) {
            int st = g.story.quest(q);
            if (st == 3) done++;
            if (st != 1 && st != 2) continue;
            open++;
            text(small, Story.QUESTS[q], x + 540 * s, qy, INK);
            for (String line : wrap(st == 2 ? Story.QUEST_BACK[q] : Story.QUEST_HOW[q], 270 * s)) text(small, line, x + 552 * s, qy += 18 * s, DIM);
            qy += 24 * s;
        }
        if (open == 0) text(small, done > 0 ? "Nothing open right now." : "Talk to people. Some of them need help.", x + 540 * s, qy, DIM);
        if (done > 0) text(small, "Completed: " + done + " of " + Story.QUESTS.length, x + 540 * s, y + 448 * s, DIM);
        text(small, "I: gear   ·   Tab to close", x + pw - 190 * s, y + ph - 18 * s, DIM);
    }

    /** The gear screen: weapons, outfits and cape dyes side by side, with what the selected one does. */
    private void gear() {
        fill(new Color(0, 0, 0, 160), 0, 0, w, h);
        float pw = 960 * s, ph = 540 * s, x = (w - pw) / 2, y = (h - ph) / 2;
        panel(x, y, pw, ph);
        text(big, "Gear", x + 24 * s, y + 44 * s, INK);
        Game.Gear gear = g.gear;
        String[][] names = {Arms.WEAPON, Arms.OUTFIT, Arms.DYE};
        String[] heads = {"Weapons", "Outfits", "Cape dyes"};
        int[] worn = {gear.weapon, gear.outfit, gear.dye};
        for (int c = 0; c < 3; c++) {
            float cx = x + 24 * s + c * 310 * s, cy = y + 92 * s;
            text(mid, heads[c], cx, cy, c == g.gearCol ? GOLD : DIM);
            for (int i = 0; i < names[c].length; i++) {
                float ry = cy + 18 * s + i * 34 * s;
                boolean own = g.owns(c, i), sel = c == g.gearCol && i == g.gearRow;
                if (sel) {
                    g2.setColor(new Color(242, 193, 78, 55));
                    g2.fillRoundRect((int) (cx - 8 * s), (int) ry, (int) (296 * s), (int) (30 * s), 10, 10);
                }
                if (c == 2 && own) {
                    int col = Arms.DYE_COL[i];
                    g2.setColor(new Color(col >> 16 & 255, col >> 8 & 255, col & 255));
                    g2.fillOval((int) (cx + 250 * s), (int) (ry + 7 * s), (int) (16 * s), (int) (16 * s));
                }
                if (worn[c] == i) diamond(cx + 4 * s, ry + 15 * s, 6 * s, 1, GOLD);
                text(small, own ? names[c][i] : "???", cx + 16 * s, ry + 21 * s, own ? (worn[c] == i ? GOLD : INK) : DIM);
            }
        }
        int c = g.gearCol, i = g.gearRow;
        boolean own = g.owns(c, i);
        String about = !own ? "Not found yet. " + (c == 0 ? Arms.WEAPON_WHERE[i] : c == 1 ? Arms.OUTFIT_WHERE[i] : Arms.DYE_WHERE[i])
                : c == 0 ? Arms.WEAPON_HOW[i] : c == 1 ? Arms.OUTFIT_HOW[i] : i == 0 ? "Each outfit's cape in its own colour." : "Dyes your cape.";
        float ay = y + 360 * s;
        text(mid, own ? names[c][i] : "???", x + 24 * s, ay, own ? INK : DIM);
        for (String line : wrap(about, pw - 48 * s)) text(small, line, x + 24 * s, ay += 24 * s, DIM);
        if (c == 0 && own) {
            String[] stat = {"Damage", "Reach", "Speed"};
            float[] val = {Arms.DMG[i] / 2.5f, Arms.REACH[i] / 3.7f, 1 / Arms.TIME[i] / 1.62f};
            for (int k = 0; k < 3; k++) {
                float sy = y + ph - 74 * s + k * 18 * s;
                text(small, stat[k], x + 24 * s, sy + 9 * s, DIM);
                fill(new Color(255, 255, 255, 30), x + 110 * s, sy, 200 * s, 10 * s);
                fill(GOLD, x + 110 * s, sy, 200 * s * M.clamp(val[k], .05f, 1), 10 * s);
            }
        }
        text(small, "W/S choose   ·   A/D switch column   ·   E equip   ·   I close", x + pw - 470 * s, y + ph - 18 * s, DIM);
    }

    /** The dev pack: every item in the game by tab; a gold diamond marks what the hero already has. */
    private void devPack() {
        fill(new Color(0, 0, 0, 160), 0, 0, w, h);
        float pw = 960 * s, ph = 540 * s, x = (w - pw) / 2, y = (h - ph) / 2;
        panel(x, y, pw, ph);
        text(big, "Dev pack", x + 24 * s, y + 44 * s, INK);
        java.util.List<java.util.List<Game.DevItem>> tabs = g.devItems();
        float tx = x + 24 * s;
        for (int t = 0; t < tabs.size(); t++) {
            text(small, Game.DEV_TABS[t], tx, y + 86 * s, t == g.devTab ? GOLD : DIM);
            tx += g2.getFontMetrics(small).stringWidth(Game.DEV_TABS[t]) + 22 * s;
        }
        java.util.List<Game.DevItem> tab = tabs.get(g.devTab);
        for (int i = 0; i < tab.size(); i++) {
            Game.DevItem it = tab.get(i);
            float ry = y + 104 * s + i * 32 * s;
            if (i == g.devRow) {
                g2.setColor(new Color(242, 193, 78, 55));
                g2.fillRoundRect((int) (x + 16 * s), (int) ry, (int) (430 * s), (int) (28 * s), 10, 10);
            }
            boolean has = it.has().getAsBoolean();
            if (has) diamond(x + 30 * s, ry + 14 * s, 6 * s, 1, GOLD);
            text(small, it.name(), x + 44 * s, ry + 20 * s, has ? GOLD : INK);
        }
        Game.DevItem sel = tab.get(g.devRow);
        text(mid, sel.name(), x + 480 * s, y + 130 * s, INK);
        text(small, sel.has().getAsBoolean() ? "Already in your inventory." : "Press E to put it in your inventory.", x + 480 * s, y + 158 * s, DIM);
        text(small, "A/D tab   ·   W/S choose   ·   E take   ·   F take the whole tab   ·   [ close", x + pw - 600 * s, y + ph - 18 * s, DIM);
    }

    private void map() {
        fill(new Color(8, 10, 14, 235), 0, 0, w, h);
        float size = h * .86f, x = (w - size) / 2, y = (h - size) / 2;
        g.world.reveal(World.MID, World.MID, 4000);
        g2.drawImage(g.world.mapImage, (int) x, (int) y, (int) size, (int) size, null);
        Places pl = g.world.places;
        mark(x, y, size, pl.villageX, pl.villageZ, "Brindle", INK);
        String[] names = {"Rootdeep Hollow", "Cinder Forge", "Tidewell Vault", "Hollow Crown", "Old altar", "Rimeholt", "Sunscour Temple"};
        for (Places.Gate gt : pl.gates) mark(x, y, size, gt.x, gt.z, names[gt.kind], gt.kind == Places.CROWN ? new Color(180, 120, 255) : GOLD);
        mark(x, y, size, pl.outpostX, pl.outpostZ, "Rangers' outpost", INK);
        mark(x, y, size, pl.campX, pl.campZ, "Nomad camp", INK);
        float px = x + g.hero.x / World.EXTENT * size, py = y + (1 - g.hero.z / World.EXTENT) * size, fx = M.sin(g.hero.yaw), fy = -M.cos(g.hero.yaw), k = 11 * s;
        Polygon arrow = new Polygon();
        arrow.addPoint((int) (px + fx * k), (int) (py + fy * k));
        arrow.addPoint((int) (px - fx * k * .6f - fy * k * .6f), (int) (py - fy * k * .6f + fx * k * .6f));
        arrow.addPoint((int) (px - fx * k * .6f + fy * k * .6f), (int) (py - fy * k * .6f - fx * k * .6f));
        g2.setColor(RED);
        g2.fill(arrow);
        text(mid, "Kestrel Isle", x + 14 * s, y + 28 * s, INK);
        text(small, "M to close", x + 14 * s, y + size - 14 * s, DIM);
    }

    private void mark(float x, float y, float size, float wx, float wz, String label, Color c) {
        float mx = x + wx / World.EXTENT * size, my = y + (1 - wz / World.EXTENT) * size;
        g2.setColor(c);
        g2.fillOval((int) (mx - 6 * s), (int) (my - 6 * s), (int) (12 * s), (int) (12 * s));
        text(small, label, mx + 10 * s, my + 5 * s, INK);
    }

    private void pause() {
        fill(new Color(0, 0, 0, 150), 0, 0, w, h);
        centered(huge, "Paused", h * .26f, INK);
        String[] lines = {"WASD move  ·  mouse or arrow keys turn the camera  ·  Space roll (uses stamina)", "Shift hold to lock on (Space then side-hops or back-flips)",
                "Click / J sword (combo; hold to charge a spin)  ·  K / right-click shield", "C cast spell  ·  V switch spell  ·  R drink an Ember Draught",
                "Q swap sword/bow  ·  F use tool  ·  X switch tool  ·  Z or mouse wheel: next lock-on spot  ·  T flute", "E talk, open, ride, rest  ·  G send Juniper away  ·  H hint  ·  Tab satchel  ·  M map  ·  P duel (co-op)", "Dev:   `  skip objective   ·   =  jump to it   ·   ]  +1000 gleams   ·   [  dev pack (any item)",
                "Enter / Esc resume   ·   Q quit"};
        for (int i = 0; i < lines.length; i++) centered(small, lines[i], h * .4f + i * 26 * s, i == 7 ? GOLD : INK);
    }
}

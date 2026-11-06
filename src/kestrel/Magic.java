package kestrel;

import java.util.ArrayList;
import java.util.List;

/** Spells: costs, casting, and the effects that outlive the cast (falling stars, thunder rings). */
final class Magic {
    static final int EMBER = 0, FROST = 1, THUNDER = 2, MEND = 3, STARFALL = 4;
    static final String[] NAME = {"Ember Bolt", "Frost Lance", "Thunder Ring", "Tidal Mend", "Starfall"};
    static final int[] COST = {10, 14, 24, 18, 45};
    static final String[] HOW = {"A bolt of fire. It also lights torches and wakes crystals.", "A piercing spear of ice that freezes lesser foes solid.",
            "A burst of lightning that strikes everything around you.", "Waves of light that mend your wounds.",
            "Calls the stars down on everything around your target."};

    private final Game g;
    final List<float[]> stars = new ArrayList<>();
    final List<float[]> rings = new ArrayList<>();
    private int pending = -1;

    Magic(Game g) { this.g = g; }

    /** Starts casting the selected spell; false if it can't be cast right now. */
    boolean begin(Hero h) {
        Game.Gear gear = g.gear;
        int s = gear.spell;
        if ((gear.spells & 1 << s) == 0) return false;
        if (h.fp < COST[s]) {
            g.say("Not enough spirit for " + NAME[s] + ".");
            g.sfx(Audio.CLICK);
            return false;
        }
        h.fp -= COST[s];
        pending = s;
        h.castAt = s == STARFALL ? 1.0f : .3f;
        h.setState(Hero.State.CAST);
        h.anim.restart(s == STARFALL ? Characters.H_STARFALL : Characters.H_CAST, .05f);
        g.sfx(Audio.CAST);
        return true;
    }

    void release(Hero h) {
        int s = pending;
        pending = -1;
        float mult = g.spellMult();
        switch (s) {
            case EMBER -> g.shootFriendly(Game.P_EMBER, h, 26, 2.2f * mult);
            case FROST -> g.shootFriendly(Game.P_FROST, h, 30, 1.7f * mult);
            case THUNDER -> {
                rings.add(new float[]{h.x, h.y, h.z, 0});
                for (Foe f : new ArrayList<>(g.foes))
                    if (f.alive() && M.dist(f.x, f.z, h.x, h.z) < 5.5f + f.radius()) f.damage(g, Foe.SPIN, 3 * mult, f.x, f.centerY(), f.z);
                g.areaHit(h.x, h.z, 5.5f);
                g.shake(.5f);
                g.sfx(Audio.THUNDER);
            }
            case MEND -> {
                h.hp = Math.min(h.maxHp, h.hp + 12);
                g.sparks(h.x, h.y + 1, h.z, 0x7af0d0, 26);
                g.sfx(Audio.HEART);
            }
            case STARFALL -> {
                float tx = h.x + M.sin(h.yaw) * 10, tz = h.z + M.cos(h.yaw) * 10;
                if (h.target != null && h.target.alive()) {
                    tx = h.target.x;
                    tz = h.target.z;
                }
                for (int i = 0; i < 9; i++) {
                    float a = i * 2.4f, r = i == 0 ? 0 : 2 + i % 3 * 2.2f, sx = tx + M.sin(a) * r, sz = tz + M.cos(a) * r;
                    stars.add(new float[]{sx, Math.max(g.level.floor(sx, sz), -2) + 34, sz, i * .14f});
                }
                g.sfx(Audio.STARFALL);
            }
            default -> {}
        }
    }

    void update(float dt) {
        for (int i = stars.size() - 1; i >= 0; i--) {
            float[] s = stars.get(i);
            if ((s[3] -= dt) > 0) continue;
            s[1] -= 46 * dt;
            float floor = Math.max(g.level.floor(s[0], s[2]), -2);
            if (s[1] <= floor + .3f) {
                stars.remove(i);
                impact(s[0], floor, s[2]);
            }
        }
        for (int i = rings.size() - 1; i >= 0; i--) if ((rings.get(i)[3] += dt) > .5f) rings.remove(i);
    }

    private void impact(float x, float y, float z) {
        float mult = g.spellMult();
        g.sparks(x, y + .5f, z, 0xfff0b0, 24);
        g.sparks(x, y + .5f, z, 0xffb050, 12);
        g.shake(.45f);
        g.sfx(Audio.BOOM);
        for (Foe f : new ArrayList<>(g.foes))
            if (f.alive() && M.dist(f.x, f.z, x, z) < 3.8f + f.radius()) f.damage(g, Foe.STAR, 5 * mult, f.x, f.centerY(), f.z);
        g.areaHit(x, z, 3.8f);
        if (g.dungeon == null) g.story.bombedField(x, z);
    }
}

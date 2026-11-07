package kestrel;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Things to do around the towns: job boards with repeatable work (hunts, bounties, deliveries, pearl diving, monster camps), homes to
 * buy and sleep in, carriages between settlements, fines to pay off a wanted level, and pockets to pick.
 */
final class Jobs {
    static final int NONE = 0, HUNT = 1, BOUNTY = 2, DELIVER = 3, PEARLS = 4, CLEAR = 5;
    static final int HOME_PRICE = 2500, FARE = 40, FINE = 150;

    /** A job on offer: what it is, how many (kills or pearls), where, the pay, and the notice pinned to the board. */
    record Offer(int kind, int goal, float x, float z, int pay, String text) {}

    private final Game g;
    /** The job pearls still on the sea floor, {x, y, z}. */
    final List<float[]> pearls = new ArrayList<>();

    // The pick menu (job boards, carriages): a title, a line per choice with a second line of detail, and what each does.
    String pickTitle;
    final List<String[]> pickLines = new ArrayList<>();
    final List<Runnable> pickActs = new ArrayList<>();

    Jobs(Game g) { this.g = g; }

    private static final String[] OUTLAWS = {"Black Corwen", "Mad Ysolde", "Gallows Pike", "Red Marta", "Silent Hob", "Wicklow the Knife", "Ash-Eyed Tam"};

    /** Today's three jobs on board b; they change each day, and after each job done. */
    List<Offer> offers(int b) {
        Places pl = g.world.places;
        float[] bd = pl.boards.get(b);
        Random r = new Random(b * 7919L + g.climate.day * 131L + g.gear.renown * 17L);
        List<Offer> out = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            int kind = 1 + r.nextInt(5);
            switch (kind) {
                case HUNT -> {
                    int n = 6 + r.nextInt(5);
                    out.add(new Offer(HUNT, n, 0, 0, n * 30, "Monster culling: bring down " + n + " monsters anywhere on the island."));
                }
                case BOUNTY -> {
                    float[] at = spot(r, bd, 180, 480);
                    String who = OUTLAWS[r.nextInt(OUTLAWS.length)];
                    out.add(new Offer(BOUNTY, 4, at[0], at[1], 450, "WANTED: " + who + " and gang, camped out in the wilds (" + where(bd, at) + "). Bring them down."));
                }
                case DELIVER -> {
                    int to = r.nextInt(pl.carriages.size());
                    float[] c = pl.carriages.get(to);
                    if (M.dist(c[0], c[2], bd[0], bd[2]) < 60) to = (to + 1) % pl.carriages.size();
                    c = pl.carriages.get(to);
                    int pay = 60 + (int) (M.dist(c[0], c[2], bd[0], bd[2]) * .25f);
                    out.add(new Offer(DELIVER, 1, c[0], c[2], pay, "Parcel to deliver to the carriage stand at " + pl.stops.get(to) + ". Handle with care."));
                }
                case PEARLS -> {
                    float[] sc = pl.schools.get(r.nextInt(pl.schools.size()));
                    out.add(new Offer(PEARLS, 3, sc[0], sc[2], 260, "Pearl diving: three fine pearls lie on a reef (" + where(bd, new float[]{sc[0], sc[2]}) + "). Hold your breath."));
                }
                default -> {
                    float[] at = spot(r, bd, 150, 420);
                    out.add(new Offer(CLEAR, 5, at[0], at[1], 300, "A monster nest is troubling the roads (" + where(bd, at) + "). Clear it out."));
                }
            }
        }
        return out;
    }

    /** "300 m north-east", from a board to a spot. */
    private static String where(float[] from, float[] to) {
        float dx = to[0] - from[0], dz = to[1] - from[2];
        String ns = dz > Math.abs(dx) * .4f ? "north" : dz < -Math.abs(dx) * .4f ? "south" : "", ew = dx > Math.abs(dz) * .4f ? "east" : dx < -Math.abs(dz) * .4f ? "west" : "";
        return Math.round(M.hypot(dx, dz) / 10) * 10 + " m " + ns + (ns.isEmpty() || ew.isEmpty() ? "" : "-") + ew;
    }

    /** Dry, open ground between near and far metres from a board, away from the towns. */
    private float[] spot(Random r, float[] from, float near, float far) {
        World w = g.world;
        for (int k = 0; k < 200; k++) {
            float a = r.nextFloat() * M.TAU, d = near + r.nextFloat() * (far - near), x = from[0] + M.sin(a) * d, z = from[2] + M.cos(a) * d;
            if (x < 80 || z < 80 || x > World.EXTENT - 80 || z > World.EXTENT - 80 || w.height(x, z) < 2 || w.slope(x, z) > .45f) continue;
            boolean town = false;
            for (Places.Town t : w.places.towns) town |= M.dist(x, z, t.x(), t.z()) < t.radius() + 20;
            if (!town && !w.places.inBuilding(x, z)) return new float[]{x, z};
        }
        return new float[]{from[0] + 200, from[2]};
    }

    void accept(Offer o) {
        Game.Gear gr = g.gear;
        gr.job = o.kind();
        gr.jobGoal = o.goal();
        gr.jobDone = 0;
        gr.jobX = (int) o.x();
        gr.jobZ = (int) o.z();
        gr.jobPay = o.pay();
        pearls.clear();
        g.screen = Game.Screen.PLAY;
        g.sfx(Audio.ITEM);
        g.say("Job taken: " + o.text());
    }

    // ---- every frame, on the open island ----------------------------------------------------------

    /** Offers near boards, homes and carriages, and the progress of whatever job is under way. */
    void fieldEvents() {
        Places pl = g.world.places;
        Hero h = g.hero;
        Game.Gear gr = g.gear;
        for (int b = 0; b < pl.boards.size(); b++) {
            float[] bd = pl.boards.get(b);
            float d = M.dist(h.x, h.z, bd[0], bd[2]);
            int board = b;
            if (d < 2.6f) g.offer(d, "Read the job board", () -> board(board));
            if (d < 12) g.tip("jobs", "A job board: hunts, bounties, deliveries and diving work, new every day. Read it (E) to take a job.");
        }
        for (int i = 0; i < pl.homes.size(); i++) {
            float[] hm = pl.homes.get(i);
            float d = M.dist(h.x, h.z, hm[0], hm[2]);
            if (d > 2.4f) continue;
            int bit = 1 << (int) hm[4];
            if ((gr.homes & bit) != 0) g.offer(d, "Sleep at home (rest, level up and save)", () -> g.story.rest(-1, hm[0], hm[1], hm[2]));
            else g.offer(d, "Buy this house (" + HOME_PRICE + " gleams)", () -> buyHome(bit));
        }
        for (int i = 0; i < pl.carriages.size(); i++) {
            float[] c = pl.carriages.get(i);
            float d = M.dist(h.x, h.z, c[0], c[2]);
            int from = i;
            if (d < 3.4f) g.offer(d, "Take the carriage", () -> carriage(from));
        }
        boolean nearFolk = false;
        for (Places.Npc n : pl.folk) nearFolk |= n.downT <= 0 && M.dist(h.x, h.z, n.x, n.z) < 3;
        if (nearFolk) g.tip("steal", "Townsfolk carry gleams. Press L right behind someone to pick their pocket; get caught and you're wanted.");
    }

    /** Every frame on the open island, whatever the hero is doing (diving for pearls included). */
    void progress() {
        Game.Gear gr = g.gear;
        Hero h = g.hero;
        switch (gr.job) {
            case DELIVER -> {
                if (M.dist(h.x, h.z, gr.jobX, gr.jobZ) < 7) finish("Parcel delivered.");
            }
            case PEARLS -> {
                if (pearls.isEmpty() && gr.jobDone == 0) {
                    // Laid out the first time they're needed (and again after loading a save).
                    Random r = new Random(gr.jobX * 31L + gr.jobZ);
                    for (int i = 0; i < 3; i++) {
                        float a = r.nextFloat() * M.TAU, d = 4 + r.nextFloat() * 10, x = gr.jobX + M.sin(a) * d, z = gr.jobZ + M.cos(a) * d;
                        pearls.add(new float[]{x, g.world.height(x, z) + .35f, z});
                    }
                }
                for (int i = pearls.size() - 1; i >= 0; i--) {
                    float[] p = pearls.get(i);
                    if (M.dist(h.x, h.z, p[0], p[2]) < 1.6f && Math.abs(h.y - p[1]) < 2) {
                        pearls.remove(i);
                        g.sfx(Audio.GLEAM);
                        if (++gr.jobDone >= gr.jobGoal) finish("Three fine pearls.");
                        else g.say("Pearl " + gr.jobDone + " of " + gr.jobGoal);
                    }
                }
            }
            case BOUNTY, CLEAR -> {
                // The camp is (re)filled whenever it's empty: after resting or loading, the survivors are back.
                boolean any = false;
                for (Foe f : g.foes) any |= f.job && f.alive();
                if (!any && gr.jobDone < gr.jobGoal && g.dungeon == null) camp(gr.jobGoal - gr.jobDone, gr.job == BOUNTY && gr.jobDone == 0);
            }
            default -> {}
        }
    }

    /** Puts the job's foes at its spot: an outlaw leader and gang, or a nest of monsters. */
    private void camp(int count, boolean leader) {
        Game.Gear gr = g.gear;
        for (int i = 0; i < count; i++) {
            float a = i * M.TAU / count, x = gr.jobX + M.sin(a) * (i == 0 ? 0 : 4), z = gr.jobZ + M.cos(a) * (i == 0 ? 0 : 4);
            Foe.Kind kind = gr.job == BOUNTY ? Foe.Kind.BANDIT : i % 3 == 0 ? Foe.Kind.IMP : i % 3 == 1 ? Foe.Kind.THORNLING : Foe.Kind.SLIME;
            Foe f = new Foe(kind, x, g.world.height(x, z), z);
            f.job = true;
            if (kind == Foe.Kind.BANDIT && i == 0 && leader) {
                f.bits = 1;
                f.hp = f.maxHp = f.maxHp * 2;
            }
            g.foes.add(f);
        }
    }

    /** A foe went down: hunts count any monster, bounties and camps count their own. */
    void foeDied(Foe f) {
        Game.Gear gr = g.gear;
        boolean counts = gr.job == HUNT ? f.kind != Foe.Kind.GUARD && f.kind != Foe.Kind.BANDIT : (gr.job == BOUNTY || gr.job == CLEAR) && f.job;
        if (!counts) return;
        if (++gr.jobDone >= gr.jobGoal) finish(gr.job == BOUNTY ? "The bounty is yours." : gr.job == CLEAR ? "The nest is cleared." : "That's the last of them.");
        else g.say("Job: " + gr.jobDone + " of " + gr.jobGoal);
    }

    private void finish(String what) {
        Game.Gear gr = g.gear;
        gr.gleams += gr.jobPay;
        gr.renown++;
        gr.job = NONE;
        pearls.clear();
        g.sfx(Audio.SECRET);
        g.say("JOB DONE  " + what + "  +" + gr.jobPay + " gleams   (renown " + gr.renown + ": " + title(gr.renown) + ")");
        g.story.save();
    }

    static String title(int renown) {
        return renown >= 40 ? "Legend of the Isle" : renown >= 25 ? "Hero of the Realm" : renown >= 12 ? "Trusted Hand" : renown >= 5 ? "Known Name" : "Newcomer";
    }

    /** Where the job wants you, for the marker on screen. */
    boolean target(float[] out) {
        Game.Gear gr = g.gear;
        if (gr.job == NONE || g.dungeon != null) return false;
        if (gr.job == HUNT) return false;
        if (gr.job == PEARLS && !pearls.isEmpty()) {
            float[] best = pearls.get(0);
            for (float[] p : pearls) if (M.dist(g.hero.x, g.hero.z, p[0], p[2]) < M.dist(g.hero.x, g.hero.z, best[0], best[2])) best = p;
            return put(out, best[0], best[1] + 1, best[2]);
        }
        if (gr.job == BOUNTY || gr.job == CLEAR)
            for (Foe f : g.foes) if (f.job && f.alive()) return put(out, f.x, f.centerY() + 1.5f, f.z);
        return put(out, gr.jobX, g.world.height(gr.jobX, gr.jobZ) + 3, gr.jobZ);
    }

    private static boolean put(float[] o, float x, float y, float z) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
        return true;
    }

    /** What the job is, for the quest log. */
    String describe() {
        Game.Gear gr = g.gear;
        return switch (gr.job) {
            case HUNT -> "Monster culling: " + gr.jobDone + " of " + gr.jobGoal + " brought down.";
            case BOUNTY -> "Bounty: " + gr.jobDone + " of " + gr.jobGoal + " of the gang down. Follow the marker.";
            case DELIVER -> "Deliver the parcel: follow the marker to the carriage stand.";
            case PEARLS -> "Pearl diving: " + gr.jobDone + " of " + gr.jobGoal + " pearls. Dive (Ctrl) where the marker points.";
            case CLEAR -> "Clear the nest: " + gr.jobDone + " of " + gr.jobGoal + " monsters down.";
            default -> null;
        } + (gr.job == NONE ? "" : "  Pay: " + gr.jobPay + " gleams.");
    }

    // ---- the menus ----------------------------------------------------------------------------

    private void open(String title) {
        pickTitle = title;
        pickLines.clear();
        pickActs.clear();
        g.menuSel = 0;
        g.screen = Game.Screen.PICK;
    }

    private void option(String line, String detail, Runnable act) {
        pickLines.add(new String[]{line, detail});
        pickActs.add(act);
    }

    void board(int b) {
        Game.Gear gr = g.gear;
        Hero h = g.hero;
        open("Job board  -  " + g.world.places.towns.get((int) g.world.places.boards.get(b)[3]).name());
        if (h.wanted > 0) {
            int fine = FINE * h.wanted;
            option("Pay your fine: " + fine + " gleams", "Clears your wanted level. The guard go back to their beat.", () -> {
                if (gr.gleams < fine) {
                    g.say("You can't afford the fine.");
                    return;
                }
                gr.gleams -= fine;
                h.wanted = 0;
                h.heat = 0;
                g.screen = Game.Screen.PLAY;
                g.sfx(Audio.GLEAM);
                g.say("Fine paid. You're no longer wanted.");
            });
        }
        if (gr.job != NONE) option("Give up your current job", describe(), () -> {
            gr.job = NONE;
            pearls.clear();
            for (Foe f : g.foes) f.job = false;
            g.screen = Game.Screen.PLAY;
            g.say("Job abandoned.");
        });
        for (Offer o : offers(b))
            option(name(o.kind()) + "  -  " + o.pay() + " gleams", o.text(), gr.job != NONE ? () -> g.say("Finish or give up your current job first.") : () -> accept(o));
    }

    private static String name(int kind) {
        return switch (kind) {
            case HUNT -> "Monster culling";
            case BOUNTY -> "Bounty";
            case DELIVER -> "Delivery";
            case PEARLS -> "Pearl diving";
            default -> "Clear a nest";
        };
    }

    private void carriage(int from) {
        Places pl = g.world.places;
        if (g.hero.wanted > 0) {
            g.say("The driver won't carry an outlaw. Lose the guard (or pay your fine) first.");
            return;
        }
        open("Carriage from " + pl.stops.get(from) + "  -  " + FARE + " gleams a ride");
        for (int i = 0; i < pl.carriages.size(); i++) {
            if (i == from) continue;
            float[] c = pl.carriages.get(i);
            int to = i;
            option(pl.stops.get(i), Math.round(M.dist(c[0], c[2], pl.carriages.get(from)[0], pl.carriages.get(from)[2]) / 10) * 10 + " m away", () -> ride(to));
        }
    }

    private void ride(int to) {
        Game.Gear gr = g.gear;
        if (gr.gleams < FARE) {
            g.say("The fare is " + FARE + " gleams.");
            return;
        }
        gr.gleams -= FARE;
        float[] c = g.world.places.carriages.get(to);
        float x = c[0] + M.cos(c[3]) * 2.5f, z = c[2] - M.sin(c[3]) * 2.5f;
        g.hero.place(x, g.world.height(x, z), z);
        g.climate.hour = (g.climate.hour + 1.5f) % 24;
        if (g.horse.summoned) g.horse.call(g);
        g.fade = 1;
        g.screen = Game.Screen.PLAY;
        g.say("The carriage sets you down at " + g.world.places.stops.get(to) + ".");
    }

    private void buyHome(int bit) {
        Game.Gear gr = g.gear;
        if (gr.gleams < HOME_PRICE) {
            g.say("This house costs " + HOME_PRICE + " gleams. Jobs from the board pay well.");
            return;
        }
        gr.gleams -= HOME_PRICE;
        gr.homes |= bit;
        g.sfx(Audio.ITEM);
        g.say("The house is yours! Sleep here any time to rest, level up and save, just like at a shrine.");
        g.story.save();
    }

    /** The pick menu: W/S choose, E take, Esc leave. */
    void pickMenu() {
        if (g.in.any(KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB) || pickActs.isEmpty()) {
            g.screen = Game.Screen.PLAY;
            return;
        }
        if (g.in.any(KeyEvent.VK_W, KeyEvent.VK_UP)) g.menuSel = Math.floorMod(g.menuSel - 1, pickActs.size());
        if (g.in.any(KeyEvent.VK_S, KeyEvent.VK_DOWN)) g.menuSel = Math.floorMod(g.menuSel + 1, pickActs.size());
        if (g.in.any(KeyEvent.VK_E, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) pickActs.get(Math.min(g.menuSel, pickActs.size() - 1)).run();
    }

    // ---- pickpocketing ---------------------------------------------------------------------------

    /** L: lift a few gleams from the townsperson in front of you; easier from behind, and a crime if they catch you. */
    void steal() {
        Hero h = g.hero;
        Places.Npc mark = null;
        float best = 1.8f;
        for (Places.Npc n : g.world.places.folk) {
            float d = M.dist(h.x, h.z, n.x, n.z);
            if (n.downT <= 0 && d < best) {
                best = d;
                mark = n;
            }
        }
        if (mark == null) return;
        if (mark.robT > 0) {
            g.say(mark.name + "'s pockets are empty. (You already took everything.)");
            return;
        }
        // From behind (where they aren't looking) it usually works; to their face it usually doesn't.
        boolean behind = M.cos(M.angleDiff(mark.yaw, M.atan2(h.x - mark.x, h.z - mark.z))) < 0;
        if (g.rng.nextFloat() < (behind ? .75f : .3f)) {
            int took = 8 + g.rng.nextInt(33);
            g.gear.gleams += took;
            mark.robT = 300;
            g.sfx(Audio.GLEAM);
            g.say("Pickpocketed " + took + " gleams from " + mark.name + (behind ? "." : ". Bold."));
        } else {
            mark.fleeT = 10;
            mark.robT = 300;
            g.say(mark.name + " caught you with your hand in their pocket!");
            g.crime(Game.ASSAULT);
        }
    }

    // ---- drawing ----------------------------------------------------------------------------------

    void draw(Renderer r, Xf xf, float t) {
        for (float[] p : pearls) {
            r.mesh(Models.PEARL, xf.identity().translate(p[0], p[1] + M.sin(t * 2 + p[0]) * .08f, p[2]).rotY(t));
            r.point(p[0], p[1] + .1f, p[2], .35f + M.sin(t * 4) * .05f, 0xf4f0ff, 120);
        }
    }
}

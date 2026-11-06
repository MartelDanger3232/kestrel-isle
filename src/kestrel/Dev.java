package kestrel;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Development tools: headless screenshots, a render benchmark and gameplay self-tests. */
final class Dev {
    private Dev() {}

    private static final String[] SHOTS = {"title", "village", "ride", "hero", "spell", "shrine", "forge", "boss", "north", "rimeholt", "wildlife", "night"};

    static void shots(File dir) throws Exception {
        Story.saving = false;
        dir.mkdirs();
        BufferedImage sheet = new BufferedImage(1280, (SHOTS.length + 1) / 2 * 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = sheet.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int i = 0; i < SHOTS.length; i++) {
            Game g = new Game(new Input(), new Audio(false));
            Renderer r = new Renderer(640, 360);
            Scene scene = new Scene(g, r);
            Hud hud = new Hud(g, r);
            setup(g, i);
            if (i == 3) scene.zoom = 2.3f;
            for (int k = 0; k < 4; k++) scene.render(1 / 60f);
            BufferedImage frame = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
            Graphics2D fg = frame.createGraphics();
            fg.drawImage(r.image, 0, 0, 1280, 720, null);
            hud.draw(fg, 1280, 720);
            fg.dispose();
            ImageIO.write(frame, "png", new File(dir, SHOTS[i] + ".png"));
            sg.drawImage(frame, (i % 2) * 640, (i / 2) * 360, 640, 360, null);
        }
        sg.dispose();
        ImageIO.write(sheet, "png", new File(dir, "sheet.png"));
        System.out.println("wrote " + dir);
    }

    /** Poses a fresh game for screenshot i. */
    private static void setup(Game g, int i) {
        Hero h = g.hero;
        Places pl = g.world.places;
        g.gear.sword = g.gear.shield = g.gear.flute = g.gear.bow = true;
        g.gear.songs = 7;
        g.gear.arrows = 20;
        g.time = 40 + i;
        g.screen = i == 0 ? Game.Screen.TITLE : Game.Screen.PLAY;
        g.climate.hour = new float[]{9, 10, 16.5f, 11, 17.8f, 13, 12, 12, 11, 12, 8.5f, 21.5f}[i];
        g.climate.update(0);
        switch (i) {
            case 0 -> {}
            case 1 -> {
                Places.Npc elder = pl.npcs.get(0);
                h.place(elder.x() + 1.2f, elder.y(), elder.z() - 3.2f);
                h.yaw = g.camYaw = M.atan2(elder.x() - h.x, elder.z() - h.z) + .5f;
                g.camPitch = -.2f;
                g.talk("Elder Maren", null, "There you are, Keeper's child. The lighthouse went dark last night, and the old songs have gone quiet.");
                g.typed = g.pages[0].length();
            }
            case 2 -> {
                g.story.flags.add("intro");
                g.gear.stones = 1;
                float x = pl.villageX + 34, z = pl.villageZ + 12;
                Horse horse = g.horse;
                horse.summoned = true;
                horse.x = x;
                horse.z = z;
                horse.y = g.world.height(x, z);
                horse.yaw = g.camYaw = 1.0f;
                horse.mount(g);
                horse.speed = 16;
                for (int k = 0; k < 30; k++) {
                    horse.phase += .09f;
                    h.riding(1 / 60f, g, horse);
                }
                g.camYaw = horse.yaw - .5f;
                g.camPitch = -.12f;
            }
            case 3 -> {
                float x = pl.villageX + 8, z = pl.villageZ - 4;
                h.place(x, g.world.height(x, z), z);
                h.yaw = 2.6f;
                g.camYaw = h.yaw + M.PI - .45f;
                g.camPitch = -.05f;
                g.gear.bowOut = true;
                g.in.force(KeyEvent.VK_J, true);
                h.setState(Hero.State.AIM);
                pump(g, .4f);
                g.camYaw = h.yaw - M.PI / 2 + .45f;
                g.camPitch = .02f;
            }
            case 4 -> {
                float x = pl.villageX + 30, z = pl.villageZ + 70;
                h.place(x, g.world.height(x, z), z);
                h.yaw = g.camYaw = .3f;
                g.gear.spells = 31;
                g.gear.spell = Magic.STARFALL;
                g.gear.ascended = true;
                h.fp = 99;
                for (int k = -1; k <= 1; k++) {
                    float fx = x + M.sin(.3f) * 11 + k * 3, fz = z + M.cos(.3f) * 11;
                    Foe f = new Foe(Foe.Kind.THORNLING, fx, g.world.height(fx, fz), fz);
                    g.foes.add(f);
                }
                g.magic.begin(h);
                pump(g, 1.35f);
                g.camPitch = -.2f;
            }
            case 5 -> {
                float[] sh = pl.shrines.get(0);
                h.place(sh[0] + 1.6f, sh[1], sh[2] - 1.6f);
                h.yaw = g.camYaw = M.atan2(sh[0] - h.x, sh[2] - h.z);
                g.gear.gleams = 340;
                g.story.rest(0, sh[0], sh[1], sh[2]);
                g.screen = Game.Screen.LEVEL;
                g.camPitch = -.2f;
            }
            case 6 -> {
                g.story.enterDungeon(1);
                Dungeon d = g.dungeon;
                h.place(d.cx(18), 0, d.cz(23));
                h.yaw = g.camYaw = 0;
                g.camPitch = -.42f;
                pump(g, .05f);
                g.messageT = 0;
            }
            case 8 -> {
                g.story.flags.add("intro");
                g.gear.stones = 7;
                float x = pl.outpostX - 4, z = pl.outpostZ - 7;
                h.place(x, g.world.height(x, z), z);
                h.yaw = g.camYaw = M.atan2(445 - x, 1590 - z) - .25f;
                g.camPitch = -.1f;
                pump(g, .1f);
            }
            case 9 -> {
                g.story.enterDungeon(4);
                Dungeon d = g.dungeon;
                g.foes.clear();
                d.bigKey = true;
                for (Dungeon.Thing t : d.things)
                    if (t.kind == 'B' || t.kind == 'L') {
                        t.on = true;
                        t.anim = 1;
                    }
                h.place(d.bossX - 4, d.bossY, d.bossZ - 6);
                h.yaw = g.camYaw = M.atan2(d.bossX - h.x, d.bossZ - h.z);
                h.swordOutT = 3;
                h.invuln = 99;
                g.story.dungeonEvents();
                pump(g, 2.2f);
                g.camYaw = M.atan2(g.boss.x - h.x, g.boss.z - h.z);
                g.camPitch = .22f;
                g.messageT = 0;
            }
            case 10 -> {
                g.story.flags.add("intro");
                float x = pl.villageX + 60, z = pl.villageZ + 120;
                float[] p = pl.settle(x, z, 4, .25f);
                h.place(p[0], g.world.height(p[0], p[1]), p[1]);
                h.yaw = g.camYaw = .9f;
                g.camPitch = -.1f;
                g.life.stage(h.x, h.z, h.yaw);
            }
            case 11 -> {
                g.story.flags.add("intro");
                float x = pl.villageX - 4, z = pl.villageZ - 26;
                h.place(x, g.world.height(x, z), z);
                h.yaw = g.camYaw = .15f;
                g.camPitch = -.12f;
                pump(g, .2f);
            }
            default -> {
                g.story.enterDungeon(0);
                Dungeon d = g.dungeon;
                h.place(d.bossX + 9, d.bossY, d.bossZ - 3);
                h.yaw = g.camYaw = M.atan2(d.bossX - h.x, d.bossZ - h.z);
                h.swordOutT = 3;
                g.camPitch = .05f;
                for (Dungeon.Thing t : d.things)
                    if (t.kind == 'B') {
                        t.on = true;
                        t.anim = 1;
                    }
                g.story.dungeonEvents();
                pump(g, 1.2f);
                h.locked = true;
                h.target = g.boss;
                h.targetPoint = 0;
            }
        }
        g.fade = 0;
    }

    /** Advances the simulation without any input. */
    private static void pump(Game g, float secs) {
        for (int k = 0; k < secs * 60; k++) {
            g.hero.update(1 / 60f, g);
            g.magic.update(1 / 60f);
            for (Foe f : g.foes) f.update(1 / 60f, g);
            if (g.dungeon != null) g.dungeon.update(1 / 60f, g);
        }
    }

    static void bench() {
        Story.saving = false;
        Renderer r = new Renderer(640, 360);
        for (int i = 1; i < SHOTS.length; i++) {
            if (i == 5) continue;
            Game g = new Game(new Input(), new Audio(false));
            Scene scene = new Scene(g, r);
            setup(g, i);
            for (int k = 0; k < 20; k++) scene.render(1 / 60f);
            long t0 = System.nanoTime();
            for (int k = 0; k < 100; k++) {
                g.camYaw += .01f;
                scene.render(1 / 60f);
            }
            System.out.printf("%-9s %.2f ms/frame%n", SHOTS[i], (System.nanoTime() - t0) / 100 / 1e6);
        }
    }

    /**
     * Mashes random buttons and mouse movement through the field and every dungeon (rendering as it goes),
     * reporting each distinct exception once. Seeded, so any crash it finds can be replayed.
     */
    static boolean fuzz() {
        Story.saving = false;
        java.util.Random rnd = new java.util.Random(3);
        int[] keys = {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_SPACE, KeyEvent.VK_SHIFT, KeyEvent.VK_J,
                KeyEvent.VK_K, KeyEvent.VK_F, KeyEvent.VK_Q, KeyEvent.VK_X, KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_R, KeyEvent.VK_T,
                KeyEvent.VK_E, KeyEvent.VK_G, KeyEvent.VK_H, KeyEvent.VK_Z, KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4,
                KeyEvent.VK_5, KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_TAB, KeyEvent.VK_M, Input.MOUSE_L, Input.MOUSE_R,
                KeyEvent.VK_W, KeyEvent.VK_W, KeyEvent.VK_J, KeyEvent.VK_E, KeyEvent.VK_ENTER};
        java.util.Map<String, Integer> seen = new java.util.LinkedHashMap<>();
        int frames = 0;
        for (int level = -1; level < Dungeons.ALL.length; level++) {
            Input in = new Input();
            Game g = new Game(in, new Audio(false));
            Renderer r = new Renderer(160, 90);
            Scene scene = new Scene(g, r);
            Hud hud = new Hud(g, r);
            BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
            Game.Gear gr = g.gear;
            gr.sword = gr.shield = gr.flute = gr.bow = gr.bombBag = gr.claw = true;
            gr.songs = 15;
            gr.spells = 31;
            gr.arrows = 30;
            gr.bombs = 20;
            gr.gleams = 3000;
            g.story.flags.add("intro");
            g.screen = Game.Screen.PLAY;
            if (level >= 0) g.story.enterDungeon(level);
            for (int f = 0; f < 6000; f++, frames++) {
                if (f % 4 == 0) {
                    int k = keys[rnd.nextInt(keys.length)];
                    in.force(k, rnd.nextFloat() < .6f);
                }
                if (f % 90 == 0) for (int k : keys) in.force(k, false);
                in.addDelta((float) rnd.nextGaussian() * 25, (float) rnd.nextGaussian() * 8);
                if (level < 0 && f % 700 == 0) {
                    float x = 200 + rnd.nextFloat() * 1650, z = 200 + rnd.nextFloat() * 1650;
                    if (g.world.height(x, z) > 2) g.hero.place(x, g.world.height(x, z), z);
                }
                try {
                    g.update(1 / 60f);
                    if (f % 8 == 0) {
                        scene.render(1 / 60f);
                        Graphics2D g2 = img.createGraphics();
                        hud.draw(g2, 320, 180);
                        g2.dispose();
                    }
                } catch (RuntimeException e) {
                    StackTraceElement top = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
                    String key = e + " @ " + top;
                    if (seen.merge(key, 1, Integer::sum) == 1) {
                        System.out.println("FUZZ " + (level < 0 ? "field" : Dungeons.ALL[level].name()) + " frame " + f + ": " + e);
                        for (int i = 0; i < Math.min(6, e.getStackTrace().length); i++) System.out.println("        at " + e.getStackTrace()[i]);
                    }
                }
            }
        }
        System.out.println("fuzzed " + frames + " frames: " + seen.size() + " distinct error(s)");
        return seen.isEmpty();
    }

    private static int fails;

    private static void check(boolean ok, String name) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) fails++;
    }

    private static void run(Game g, float secs) {
        for (int i = 0; i < secs * 60; i++) g.update(1 / 60f);
    }

    /** Rimeholt's ice, its wolves and Hoarwing; the mesa, the field claw and a side quest. */
    private static void northTests(Game g, Input in) {
        Hero h = g.hero;
        g.gear.claw = true;
        g.gear.item = 2;
        g.gear.spells |= 1;
        g.story.enterDungeon(4);
        Dungeon rime = g.dungeon;
        g.foes.clear();
        h.invuln = 99;
        Dungeon.Thing ice = null;
        for (Dungeon.Thing t : rime.things) if (t.kind == 'I') ice = t;
        h.place(ice.x, rime.floor(ice.x, ice.z + 5), ice.z + 5);
        boolean sealed = rime.floor(ice.x, ice.z) > h.y + 2;
        h.fp = h.maxFp;
        g.gear.spell = 0;
        h.yaw = g.camYaw = M.atan2(ice.x - h.x, ice.z - h.z);
        run(g, .1f);
        tap(g, in, KeyEvent.VK_C);
        run(g, 1.5f);
        check(sealed && ice.on && rime.floor(ice.x, ice.z) < h.y + 2, "an Ember Bolt melts Rimeholt's ice wall (sealed=" + sealed + ", melted=" + ice.on + ")");

        float[] icy = null;
        for (int j = 11; j <= 14 && icy == null; j++)
            if (rime.at(18, j) == '-' && rime.at(18, j + 1) == '-') icy = new float[]{rime.cx(18), rime.cz(j)};
        h.place(icy[0], rime.floor(icy[0], icy[1]), icy[1]);
        h.yaw = g.camYaw = M.PI / 2;
        in.force(KeyEvent.VK_W, true);
        run(g, .6f);
        in.force(KeyEvent.VK_W, false);
        float let = h.x;
        run(g, .5f);
        check(rime.icy(h.x, h.z) && Math.abs(h.x - let) > .3f, "the hero keeps gliding on ice after letting go (" + (h.x - let) + " m)");

        Foe wolf = new Foe(Foe.Kind.WOLF, h.x + 3, h.y, h.z);
        g.foes.add(wolf);
        run(g, 3);
        int hpBefore = h.hp;
        for (int i = 0; i < 12 && wolf.alive(); i++) wolf.damage(g, Foe.SWORD, 1, wolf.x, wolf.centerY(), wolf.z);
        check(!wolf.alive(), "a Frostfang wolf hunts the hero and can be cut down (hero hp " + hpBefore + ")");
        g.foes.clear();

        rime.bigKey = true;
        for (Dungeon.Thing t : rime.things) if (t.kind == 'B' || t.kind == 'L') t.on = true;
        h.place(rime.bossX, rime.bossY, rime.bossZ - 8);
        run(g, .2f);
        Foe owl = g.boss;
        Dungeon.Thing perch = null;
        for (Dungeon.Thing t : rime.things) if (t.kind == 'g' && t.j < 10 && (perch == null || M.dist(t.x, t.z, h.x, h.z) < M.dist(perch.x, perch.z, h.x, h.z))) perch = t;
        boolean started = owl != null && owl.kind == Foe.Kind.HOARWING;
        if (started) {
            owl.setState(Foe.PERCH);
            owl.tx = owl.x = perch.x;
            owl.tz = owl.z = perch.z;
            owl.headY = owl.y = perch.y + 3.2f;
        }
        float ux = rime.bossX - perch.x, uz = rime.bossZ - perch.z, ul = M.hypot(ux, uz), px = perch.x + ux / ul * 9, pz = perch.z + uz / ul * 9;
        h.place(px, rime.floor(px, pz), pz);
        h.yaw = g.camYaw = M.atan2(perch.x - h.x, perch.z - h.z);
        run(g, .1f);
        in.force(KeyEvent.VK_F, true);
        run(g, .3f);
        float[] at = g.clawTarget(h);
        in.force(KeyEvent.VK_F, false);
        run(g, .8f);
        boolean downed = started && owl.state == Foe.STUN;
        float owlHp = started ? owl.hp : 0;
        if (downed) owl.damage(g, Foe.SWORD, 2, owl.x, owl.centerY(), owl.z);
        check(downed && at != null && at[3] == 1 && owl.hp < owlHp, "the claw drags a perched Hoarwing down, and then the sword bites (target=" + (at == null ? -1 : (int) at[3]) + ")");
        g.boss = null;
        g.foes.clear();
        h.invuln = 0;
        g.story.exitDungeon();
        g.screen = Game.Screen.PLAY;

        Places pl = g.world.places;
        Places.Gate fg = pl.gates.get(Places.FROST);
        float[] post = pl.posts.get(0);
        float bx = post[0], bz = post[2] - 9;
        h.place(bx, g.world.height(bx, bz), bz);
        h.yaw = g.camYaw = 0;
        in.force(KeyEvent.VK_W, true);
        run(g, 2.5f);
        in.force(KeyEvent.VK_W, false);
        boolean walled = h.y < fg.y - 4;
        h.place(bx, g.world.height(bx, bz), bz);
        h.yaw = g.camYaw = 0;
        run(g, .1f);
        in.force(KeyEvent.VK_F, true);
        run(g, .3f);
        at = g.clawTarget(h);
        in.force(KeyEvent.VK_F, false);
        run(g, 2.5f);
        check(walled && at != null && at[3] == 0 && Math.abs(h.y - fg.y) < .6f, "Rimeholt's mesa can't be walked up, but the claw hauls you to the top (y=" + h.y + ", top=" + fg.y + ")");

        Places.Pickup kite = null;
        for (java.util.List<Places.Pickup> list : pl.pickups) for (Places.Pickup p : list) if ("kite".equals(p.id)) kite = p;
        h.place(kite.x - 6, g.world.height(kite.x - 6, kite.z), kite.z);
        h.yaw = g.camYaw = M.atan2(kite.x - h.x, kite.z - h.z);
        run(g, .1f);
        in.force(KeyEvent.VK_F, true);
        run(g, .3f);
        in.force(KeyEvent.VK_F, false);
        run(g, 1.5f);
        boolean fetched = kite.taken && g.story.quest(0) == 2;
        dialogs(g, in);
        int pieces = g.gear.pieces;
        for (Places.Npc n : pl.npcs) if (n.id().equals("child")) g.story.talkTo(n);
        dialogs(g, in);
        check(fetched && g.story.quest(0) == 3 && g.gear.pieces == pieces + 1,
                "the claw snags Tibb's kite from the oak, and Tibb pays with a heart piece (fetched=" + fetched + ", quest=" + g.story.quest(0) + ")");
    }

    /** Clicks through any dialog or item screens. */
    private static void dialogs(Game g, Input in) {
        for (int i = 0; i < 60 && g.screen != Game.Screen.PLAY; i++) {
            tap(g, in, KeyEvent.VK_ENTER);
            run(g, .3f);
        }
    }

    private static void tap(Game g, Input in, int key) {
        in.force(key, true);
        run(g, 1 / 60f);
        in.force(key, false);
        run(g, 1 / 60f);
    }

    /** Scripted checks of the core mechanics, driven through the real update loop and input. */
    static boolean test() {
        Story.SAVE = Path.of(System.getProperty("java.io.tmpdir"), "kestrel-isle-selftest.properties");
        for (Dungeons.Spec s : Dungeons.ALL) {
            int width = s.map()[0].length(), chests = 0;
            boolean even = true;
            for (String row : s.map()) {
                even &= row.length() == width;
                for (char c : row.toCharArray()) if (c == 'c' || c == 'C') chests++;
            }
            Dungeon d = new Dungeon(s);
            check(even && chests == s.chests().length && d.spawnX > 0 && d.bossX > 0, s.name() + ": map is well formed");
        }
        Input in = new Input();
        Game g = new Game(in, new Audio(false));
        g.screen = Game.Screen.PLAY;
        Hero h = g.hero;
        g.gear.sword = g.gear.shield = g.gear.flute = true;
        g.gear.songs = 1;
        float z0 = h.z;
        g.camYaw = 0;
        in.force(KeyEvent.VK_W, true);
        run(g, 1);
        in.force(KeyEvent.VK_W, false);
        check(h.z - z0 > 4, "walking north covers ground (" + (int) (h.z - z0) + " m)");
        tap(g, in, KeyEvent.VK_SPACE);
        check(h.st == Hero.State.ROLL, "space rolls");
        run(g, .6f);

        Foe f = new Foe(Foe.Kind.THORNLING, h.x + M.sin(h.yaw) * 1.6f, h.y, h.z + M.cos(h.yaw) * 1.6f);
        g.foes.clear();
        g.foes.add(f);
        for (int i = 0; i < 6 && f.alive(); i++) {
            tap(g, in, KeyEvent.VK_J);
            run(g, .5f);
            f.x = h.x + M.sin(h.yaw) * 1.6f;
            f.z = h.z + M.cos(h.yaw) * 1.6f;
        }
        check(!f.alive(), "sword combos kill a thornling");
        g.foes.clear();
        Foe doomed = new Foe(Foe.Kind.THORNLING, h.x + M.sin(h.yaw) * 4, h.y, h.z + M.cos(h.yaw) * 4);
        g.foes.add(doomed);
        in.force(KeyEvent.VK_SHIFT, true);
        run(g, .2f);
        boolean lockedOn = h.target == doomed;
        doomed.damage(g, Foe.SWORD, 99, doomed.x, doomed.centerY(), doomed.z);
        run(g, .3f);
        check(lockedOn && h.target == null && !h.locked, "when your target dies with nobody else near, the lock lets go");
        in.force(KeyEvent.VK_SHIFT, false);
        run(g, .1f);
        g.foes.clear();
        Foe slime = new Foe(Foe.Kind.SLIME, h.x + M.sin(h.yaw) * 1.4f, h.y, h.z + M.cos(h.yaw) * 1.4f);
        slime.hp = .1f;
        g.foes.add(slime);
        g.swordStrike(h, 150, 2.4f, 1);
        check(!slime.alive() && g.foes.size() >= 3, "slicing a big slime splits it without crashing");
        g.foes.clear();
        int hp = h.hp;
        in.force(KeyEvent.VK_K, true);
        run(g, .2f);
        g.hurtHero(2, h.x + M.sin(h.yaw) * 2, h.z + M.cos(h.yaw) * 2, true);
        in.force(KeyEvent.VK_K, false);
        check(h.hp == hp, "the shield blocks a frontal blow");
        run(g, .05f);
        h.invuln = 0;
        g.hurtHero(2, h.x + M.sin(h.yaw) * 2, h.z + M.cos(h.yaw) * 2, true);
        check(h.hp == hp - 2, "without the shield the blow lands");
        run(g, .5f);

        g.story.enterDungeon(0);
        Dungeon d = g.dungeon;
        check(g.level == d && d.foes.size() > 3, "entering Rootdeep spawns its monsters");
        in.force(KeyEvent.VK_T, true);
        run(g, 1 / 60f);
        in.force(KeyEvent.VK_T, false);
        for (int n : Story.SONGS[0]) tap(g, in, KeyEvent.VK_1 + n);
        run(g, .2f);
        check(d.torchesLit(2, 3) && d.find('S', 1).on, "the Song of Kindling lights the torches and opens the door");
        d.find('o', 0).on = true;
        run(g, .1f);
        check(d.floor(d.cx(4), d.cz(19)) == 0, "striking the crystal raises a bridge");
        Dungeon.Thing ledgeChest = d.find('c', 2);
        h.place(d.cx(4), 0, d.cz(23));
        h.yaw = g.camYaw = -M.PI / 2;
        in.force(KeyEvent.VK_W, true);
        run(g, 1.5f);
        in.force(KeyEvent.VK_W, false);
        check(h.y > 1.4f, "the hero pulls up onto a ledge (y=" + h.y + ")");
        h.place(ledgeChest.x, ledgeChest.y, ledgeChest.z + 1.3f);
        h.yaw = M.PI;
        run(g, .1f);
        tap(g, in, KeyEvent.VK_E);
        run(g, .1f);
        for (int k = 0; k < 4; k++) tap(g, in, KeyEvent.VK_E);
        check(g.gear.bow, "the ledge chest holds the bow");
        Dungeon.Thing block = d.find('b', 0);
        int bj = block.j;
        h.place(block.x, 0, block.z - 2.2f);
        h.yaw = g.camYaw = 0;
        in.force(KeyEvent.VK_W, true);
        run(g, 1.2f);
        in.force(KeyEvent.VK_W, false);
        check(block.j == bj - 1, "pushing the block slides it a tile");
        d.keys = 1;
        Dungeon.Thing lock = d.find('L', -1);
        h.place(lock.x, 0, lock.z - 2);
        h.yaw = 0;
        run(g, .1f);
        tap(g, in, KeyEvent.VK_E);
        check(lock.on && d.keys == 0, "a small key opens the locked door");
        g.foes.clear();
        Foe bat = new Foe(Foe.Kind.BAT, d.cx(15), 2.5f, d.cz(13));
        g.foes.add(bat);
        h.place(d.cx(15), 0, d.cz(17));
        run(g, 3);
        check(d.tj(bat.z) <= 14 && h.hp == hp - 2, "bats can't fly through walls");
        g.foes.clear();
        Dungeon.Thing great = d.find('B', -1);
        h.place(great.x, 0, great.z - 2.2f);
        run(g, .3f);
        check(!d.bossStarted, "waiting at the closed great door doesn't wake the boss");
        great.on = true;
        great.anim = 1;
        h.place(great.x, 0, great.z + 3);
        run(g, .2f);
        check(d.bossStarted, "stepping through the open great door does");
        Foe bm = g.boss;
        h.invuln = 99;
        h.place(great.x, 0, great.z + 5);
        h.yaw = g.camYaw = 0;
        in.force(KeyEvent.VK_SHIFT, true);
        run(g, .3f);
        int firstSpot = h.targetPoint;
        check(h.target == bm && firstSpot < 2, "locking on to Bramblemaw picks one of its seed-bulbs");
        g.gear.spells |= 1;
        g.gear.spell = Magic.EMBER;
        h.fp = h.maxFp;
        tap(g, in, KeyEvent.VK_C);
        run(g, 1.2f);
        check(bm.bits != 0 && h.targetPoint != firstSpot, "an Ember Bolt bursts that bulb and the lock jumps to the other");
        in.force(KeyEvent.VK_SHIFT, false);
        run(g, .1f);
        h.invuln = 0;
        Renderer rr = new Renderer(160, 90);
        Scene sc = new Scene(g, rr);
        h.place(great.x, 0, great.z + 1.2f);
        g.camYaw = 0;
        g.camPitch = .3f;
        g.shake = 0;
        sc.render(1 / 60f);
        check(Math.abs(rr.fy - M.sin(g.camPitch)) < .02f, "the camera keeps your tilt even squeezed against a wall");

        g.story.enterDungeon(1);
        Dungeon cf = g.dungeon;
        Dungeon.Thing wall = cf.find('X', -1);
        g.explode(wall.x - 2, 1, wall.z, false);
        check(wall.on, "a bomb blasts the cracked wall");

        Foe bramble = new Foe(Foe.Kind.BRAMBLE, 0, 0, 0);
        bramble.setState(Foe.CHASE);
        bramble.update(0, g);
        for (int s = -1; s <= 1; s += 2)
            bramble.damage(g, Foe.ARROW, 1, bramble.headX + M.cos(bramble.yaw) * 1.6f * s, bramble.headY + 1.6f, bramble.headZ - M.sin(bramble.yaw) * 1.6f * s);
        check(bramble.state == Foe.STUN, "two seed-bulb arrows stun Bramblemaw");
        Foe king = new Foe(Foe.Kind.KING, 0, 4, 0);
        king.setState(Foe.CHASE);
        king.damage(g, Foe.ARROW, 1, 0, 6.1f, 0);
        check(king.state == Foe.STUN, "an arrow to the lantern-heart stuns the Hollow King");

        // Soulslike systems and the newer powers.
        g.story.exitDungeon();
        g.screen = Game.Screen.PLAY;
        h.place(h.x, h.y, h.z);
        float stam = h.stamina;
        tap(g, in, KeyEvent.VK_SPACE);
        check(h.stamina < stam, "rolling costs stamina");
        run(g, 1);
        h.hp = 4;
        int flasks = g.gear.flasks;
        tap(g, in, KeyEvent.VK_R);
        run(g, 1);
        check(h.hp > 4 && g.gear.flasks == flasks - 1, "an Ember Draught heals (" + h.hp + ")");
        float[] sh = g.world.places.shrines.get(0);
        h.place(sh[0] + 1.5f, sh[1], sh[2] - 1.5f);
        g.gear.gleams = 500;
        int maxHp = h.maxHp;
        g.story.rest(0, sh[0], sh[1], sh[2]);
        g.screen = Game.Screen.LEVEL;
        g.menuSel = 0;
        tap(g, in, KeyEvent.VK_E);
        check(g.gear.vig == 1 && h.maxHp == maxHp + 2 && g.gear.level == 2, "levelling Vigor at a shrine adds life");
        g.screen = Game.Screen.PLAY;
        h.setState(Hero.State.MOVE);
        g.foes.clear();
        g.gear.spells |= 1;
        g.gear.spell = Magic.EMBER;
        h.fp = h.maxFp;
        Foe target = new Foe(Foe.Kind.THORNLING, h.x + M.sin(h.yaw) * 6, h.y, h.z + M.cos(h.yaw) * 6);
        target.y = g.level.floor(target.x, target.z);
        g.foes.add(target);
        tap(g, in, KeyEvent.VK_C);
        run(g, 1.2f);
        check(target.hp < target.maxHp || !target.alive(), "Ember Bolt scorches a thornling");
        g.foes.clear();
        g.gear.songs |= Story.STEED;
        g.horse.call(g);
        run(g, 5);
        check(M.dist(g.horse.x, g.horse.z, h.x, h.z) < 5, "Juniper answers the Song of the Steed");
        g.horse.mount(g);
        float hx = g.horse.x, hz = g.horse.z;
        g.camYaw = g.horse.yaw;
        in.force(KeyEvent.VK_W, true);
        in.force(KeyEvent.VK_SHIFT, true);
        run(g, 2.5f);
        in.force(KeyEvent.VK_W, false);
        in.force(KeyEvent.VK_SHIFT, false);
        check(M.dist(hx, hz, g.horse.x, g.horse.z) > 12 && h.st == Hero.State.RIDE, "galloping on Juniper covers ground (" + (int) M.dist(hx, hz, g.horse.x, g.horse.z) + " m)");
        run(g, 3);
        tap(g, in, KeyEvent.VK_E);
        run(g, .2f);
        check(!g.horse.ridden && h.st != Hero.State.RIDE, "E gets you off Juniper and doesn't put you straight back on");
        tap(g, in, KeyEvent.VK_G);
        check(!g.horse.summoned, "G sends Juniper away");
        g.gear.gleams = 77;
        h.hp = 0;
        h.pose(Hero.State.DEAD, Characters.H_DEAD);
        float dx = h.x, dz = h.z;
        g.story.continueAfterDeath();
        check(g.gear.gleams == 0 && g.lostGleams == 77, "falling drops your gleams");
        h.place(dx, g.level.floor(dx, dz), dz);
        run(g, .1f);
        check(g.gear.gleams == 77, "reaching the spot recovers them");
        float before = g.swordMult();
        g.gear.ascended = true;
        g.gear.str = 10;
        check(g.swordMult() > before * 3, "Ascension and Strength make you hit like a god (x" + g.swordMult() + ")");

        g.gear.bow = true;
        g.gear.arrows = 10;
        g.gear.bowOut = false;
        h.setState(Hero.State.MOVE);
        tap(g, in, KeyEvent.VK_Q);
        in.force(KeyEvent.VK_J, true);
        run(g, .4f);
        boolean drawing = h.st == Hero.State.AIM;
        in.force(KeyEvent.VK_J, false);
        run(g, 1 / 60f);
        boolean loosed = h.st == Hero.State.LOOSE;
        check(g.gear.bowOut && drawing && loosed && g.gear.arrows == 9, "Q swaps to the bow; click-and-hold draws, releasing looses with a follow-through");
        run(g, .6f);
        float[] goal = new float[3];
        g.story.flags.add("intro");
        g.gear.stones = 0;
        Places.Gate rootGate = g.world.places.gates.get(Places.ROOT), forgeGate = g.world.places.gates.get(Places.CINDER);
        check(g.story.objectiveTarget(goal) && M.dist(goal[0], goal[2], rootGate.x, rootGate.z) < 8, "the gold marker points at Rootdeep first");
        g.gear.stones = 1;
        check(g.story.objectiveTarget(goal) && Math.abs(goal[1] - forgeGate.y - 8.6f) < .1f, "after Rootdeep it points at the forge gate's lantern");
        g.gear.stones = 0;
        g.gear.bowOut = false;

        Foe golem = new Foe(Foe.Kind.GOLEM, h.x + 12, h.y, h.z);
        golem.y = g.level.floor(golem.x, golem.z);
        golem.setState(Foe.RECOVER);
        golem.tx = golem.x - 3.6f;
        golem.tz = golem.z;
        g.foes.add(golem);
        Game.Bomb bomb = new Game.Bomb();
        bomb.x = golem.tx;
        bomb.z = golem.tz;
        bomb.y = g.level.floor(bomb.x, bomb.z) + .2f;
        bomb.fuse = 3;
        g.bombs.add(bomb);
        run(g, 3.3f);
        check(!golem.armour, "a bomb lit after the Golem's fist lands still cracks its armour");
        g.foes.clear();

        g.story.enterDungeon(1);
        g.foes.clear();
        h.invuln = 99;
        g.gear.bombBag = true;
        g.gear.bombs = 0;
        Dungeon forge = g.dungeon;
        Dungeon.Thing pod = null;
        for (Dungeon.Thing t : forge.things) if (t.kind == 'P') pod = t;
        h.place(pod.x, pod.y, pod.z);
        run(g, .1f);
        boolean picked = g.gear.bombs == 3 && !pod.on;
        h.place(pod.x - 4, pod.y, pod.z);
        run(g, 12.5f);
        check(picked && pod.on, "a Blast Pod gives 3 bombs and regrows (picked=" + picked + ", regrown=" + pod.on + ")");
        h.invuln = 0;
        g.story.exitDungeon();
        g.screen = Game.Screen.PLAY;

        g.story.enterDungeon(2);
        Dungeon tide = g.dungeon;
        g.foes.clear();
        h.invuln = 99;
        tide.flooded = true;
        h.place(tide.cx(15), -.45f, tide.cz(20));
        h.setState(Hero.State.SWIM);
        g.camYaw = M.PI / 2;
        in.force(KeyEvent.VK_W, true);
        run(g, 2.5f);
        in.force(KeyEvent.VK_W, false);
        check(h.y > 1.4f, "swimming into the island's edge climbs you onto it (y=" + h.y + ")");
        Dungeon.Thing post = tide.find('g', 0);
        g.gear.claw = true;
        g.gear.item = 2;
        h.place(post.x + 2, 0, tide.cz(14));
        h.yaw = g.camYaw = M.atan2(post.x - h.x, post.z - h.z);
        run(g, .1f);
        in.force(KeyEvent.VK_F, true);
        run(g, .3f);
        float[] at = g.clawTarget(h);
        boolean aimed = at != null && at[3] == 0 && Math.abs(at[0] - post.x) < .01f && Math.abs(at[2] - post.z) < .01f;
        in.force(KeyEvent.VK_F, false);
        run(g, 2);
        check(aimed && tide.tj(h.z) <= 11 && h.y > -1, "the claw snaps onto the post and lands you safely across the pit");
        g.story.exitDungeon();
        g.screen = Game.Screen.PLAY;
        h.invuln = 0;
        int gleamsBefore = g.gear.gleams;
        tap(g, in, KeyEvent.VK_CLOSE_BRACKET);
        check(g.gear.gleams == gleamsBefore + 1000, "the dev key ] adds 1000 gleams");

        northTests(g, in);

        Game fresh = new Game(in, new Audio(false));
        fresh.screen = Game.Screen.PLAY;
        for (int i = 0; i < 13; i++) {
            if (fresh.screen != Game.Screen.PLAY) fresh.screen = Game.Screen.PLAY;
            tap(fresh, in, KeyEvent.VK_BACK_QUOTE);
        }
        check(fresh.gear.stones == 15 && fresh.gear.ascended && fresh.gear.longclaw && fresh.gear.bombBag && fresh.screen == Game.Screen.END && fresh.gear.songs == 15,
                "the dev skip key (`) walks through every objective: four Wardstones and every song (stones=" + fresh.gear.stones + ", songs=" + fresh.gear.songs + ")");

        g.gear.gleams = 123;
        g.gear.bombBag = true;
        g.story.save();
        Game g2 = new Game(new Input(), new Audio(false));
        g2.story.load();
        check(g2.gear.gleams == 123 && g2.gear.bombBag && g2.dungeons[0] != null && g2.dungeons[0].find('S', 1).on, "saving and loading keeps progress");
        Story.SAVE.toFile().delete();
        System.out.println(fails == 0 ? "all checks passed" : fails + " check(s) failed");
        return fails == 0;
    }
}

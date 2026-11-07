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

    static final String[] SHOTS = {"title", "village", "ride", "hero", "spell", "shrine", "forge", "boss", "north", "rimeholt", "wildlife", "night", "glide", "temple", "drake", "gear", "attire", "duel", "town", "reef", "wanted", "jobs"};

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
            if (i == 3 || i == 16) scene.zoom = 2.3f;
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
    static void setup(Game g, int i) {
        Hero h = g.hero;
        Places pl = g.world.places;
        g.gear.sword = g.gear.shield = g.gear.flute = g.gear.bow = true;
        g.gear.songs = 7;
        g.gear.arrows = 20;
        g.time = 40 + i;
        g.screen = i == 0 ? Game.Screen.TITLE : Game.Screen.PLAY;
        g.climate.hour = new float[]{9, 10, 16.5f, 11, 17.8f, 13, 12, 12, 11, 12, 8.5f, 21.5f, 16, 12, 15, 10, 15.5f, 11, 11, 12, 15, 10}[i];
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
            case 12 -> {
                g.story.flags.add("intro");
                g.gear.windcloak = true;
                float x = pl.campX + 30, z = pl.campZ + 70;
                h.place(x, g.world.height(x, z) + 24, z);
                h.yaw = g.camYaw = .25f;
                h.setState(Hero.State.GLIDE);
                h.stamina = h.maxStamina;
                pump(g, .4f);
                g.camYaw = h.yaw + .35f;
                g.camPitch = -.12f;
            }
            case 13 -> {
                g.story.enterDungeon(5);
                Dungeon d = g.dungeon;
                g.foes.clear();
                for (Dungeon.Thing t : d.things) if (t.kind == 'r' && t.j >= 26) t.on = true;
                h.place(d.cx(20), 0, d.cz(29));
                h.yaw = g.camYaw = M.atan2(d.cx(17) - h.x, d.cz(26) - h.z) - .15f;
                g.camPitch = -.28f;
                pump(g, .4f);
                g.messageT = 0;
            }
            case 14 -> {
                g.story.flags.add("intro");
                g.gear.windcloak = true;
                g.gear.bow = g.gear.bowOut = true;
                float x = pl.villageX + 90, z = pl.villageZ + 150;
                float[] p = pl.settle(x, z, 4, .3f);
                h.place(p[0], g.world.height(p[0], p[1]), p[1]);
                h.invuln = 99;
                Foe drake = g.drake = new Foe(Foe.Kind.STORMDRAKE, h.x, h.y, h.z);
                g.foes.add(drake);
                drake.x = h.x + 12;
                drake.z = h.z + 30;
                drake.y = h.y + 13;
                g.boss = drake;
                drake.setState(Foe.ATTACK);
                drake.yaw = M.atan2(h.x - drake.x, h.z - drake.z);
                drake.tx = M.sin(drake.yaw);
                drake.tz = M.cos(drake.yaw);
                pump(g, .5f);
                h.yaw = g.camYaw = M.atan2(drake.x - h.x, drake.z - h.z) + .1f;
                g.camPitch = .3f;
                g.messageT = 0;
            }
            case 15 -> {
                g.story.flags.add("intro");
                Game.Gear gr = g.gear;
                gr.weapons = 31;
                gr.outfits = 1 | 1 << Arms.FURS | 1 << Arms.PLATE | 1 << Arms.WRAPS;
                gr.dyes = 1 | 1 << 1 | 1 << 2 | 1 << 5;
                gr.weapon = Arms.GREATSWORD;
                gr.outfit = Arms.PLATE;
                gr.dye = 1;
                float x = pl.villageX + 6, z = pl.villageZ - 8;
                h.place(x, g.world.height(x, z), z);
                h.yaw = g.camYaw = 2.4f;
                g.camPitch = -.15f;
                g.screen = Game.Screen.GEAR;
                g.gearCol = 0;
                g.gearRow = Arms.GREATSWORD;
            }
            case 17 -> {
                // Two duellists: Wren below with a greatsword drawn, the hero above on the Skyward Gust's wind with one sheathed.
                g.story.flags.add("intro");
                float x = pl.villageX + 40, z = pl.villageZ + 70, y = g.world.height(x, z);
                Game.Player wren = new Game.Player(new Hero(x + 1.5f, y, z + 5), new Game.Gear(), new Input(), "Wren");
                wren.id = 1;
                wren.gear.sword = true;
                wren.gear.weapons = 31;
                wren.gear.weapon = Arms.GREATSWORD;
                wren.hero.swordOutT = 3;
                wren.hero.yaw = M.PI;
                wren.hero.dueling = h.dueling = true;
                g.players.add(wren);
                g.gear.windcloak = true;
                g.gear.weapons = 31;
                g.gear.weapon = Arms.GREATSWORD;
                h.place(x, y, z);
                h.yaw = 0;
                h.launch(26, 0);
                h.hoverNext = true;
                pump(g, 1.6f);
                wren.hero.swordOutT = 3;
                g.camYaw = .5f;
                g.camPitch = -.5f;
            }
            case 18, 20 -> {
                // Kingsreach: the castle from one of the plaza's streets; for "wanted", with the guard closing in.
                g.story.flags.add("intro");
                Places.Town king = pl.towns.get(1);
                float x = king.x() + 30, z = king.z() - 26, y = g.world.height(x, z);
                h.place(x, y, z);
                h.yaw = g.camYaw = M.atan2(king.x() - x, king.z() - z) + .35f;
                g.camPitch = -.12f;
                if (i == 20) {
                    h.wanted = 3;
                    h.seenT = 1;
                    for (int k = 0; k < 4; k++) {
                        float a = h.yaw + M.PI + (k - 1.5f) * .5f, gx = x + M.sin(a) * (6 + k), gz = z + M.cos(a) * (6 + k);
                        Foe f = new Foe(Foe.Kind.GUARD, gx, g.world.height(gx, gz), gz);
                        f.bits = k == 0 ? 1 : 0;
                        f.setState(Foe.CHASE);
                        g.foes.add(f);
                    }
                    g.camYaw = h.yaw + M.PI * .8f;
                    pump(g, .4f);
                }
            }
            case 21 -> {
                // Kingsreach's job board, read.
                g.story.flags.add("intro");
                float[] bd = pl.boards.get(1);
                h.place(bd[0] + 2, g.world.height(bd[0] + 2, bd[2] - 2), bd[2] - 2);
                h.yaw = g.camYaw = M.atan2(bd[0] - h.x, bd[2] - h.z);
                g.camPitch = -.15f;
                g.gear.gleams = 420;
                g.jobs.board(1);
            }
            case 19 -> {
                // Down among the kelp on Brindle's nearest reef.
                g.story.flags.add("intro");
                float[] sc = pl.schools.get(0);
                h.place(sc[0] - 6, World.WATER, sc[2] - 6);
                pump(g, .3f);
                h.st = Hero.State.DIVE;
                h.y = sc[1] - 1;
                h.yaw = g.camYaw = M.atan2(6, 6);
                g.camPitch = -.15f;
            }
            case 16 -> {
                g.story.flags.add("intro");
                g.gear.weapons = 31;
                g.gear.weapon = Arms.HAMMER;
                g.gear.outfits = 63;
                g.gear.outfit = Arms.FURS;
                g.gear.dye = 2;
                g.gear.dyes = 127;
                float x = pl.outpostX - 3, z = pl.outpostZ - 6;
                h.place(x, g.world.height(x, z), z);
                h.yaw = 2.2f;
                h.swordOutT = 3;
                g.camYaw = h.yaw + M.PI - .5f;
                g.camPitch = -.05f;
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
                KeyEvent.VK_5, KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_TAB, KeyEvent.VK_M, KeyEvent.VK_I, Input.MOUSE_L, Input.MOUSE_R,
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
            // Every weapon, outfit and dye, picked at random, plus the glider.
            gr.weapons = 31;
            gr.outfits = 63;
            gr.dyes = 127;
            gr.weapon = rnd.nextInt(5);
            gr.outfit = rnd.nextInt(6);
            gr.dye = rnd.nextInt(7);
            gr.windcloak = true;
            g.story.flags.add("intro");
            g.screen = Game.Screen.PLAY;
            if (level >= 0) g.story.enterDungeon(level);
            for (int f = 0; f < 6000; f++, frames++) {
                if (f % 4 == 0) {
                    int k = keys[rnd.nextInt(keys.length)];
                    in.force(k, rnd.nextFloat() < .6f);
                }
                if (f % 90 == 0) for (int k : keys) in.force(k, false);
                if (f % 1500 == 0) gr.weapon = rnd.nextInt(5);
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

    /** Parry and riposte, the Windcloak and its updrafts, the Sunscour's mirrors and chasm, its stalkers and wyrm, and the Stormdrake. */
    private static void skyTests(Game g, Input in) {
        Hero h = g.hero;
        Places pl = g.world.places;
        g.screen = Game.Screen.PLAY;
        g.foes.clear();
        float fx = pl.villageX + 30, fz = pl.villageZ + 60;
        h.place(fx, g.world.height(fx, fz), fz);
        h.invuln = 0;
        h.hp = h.maxHp;
        h.yaw = g.camYaw = 0;
        Foe sentry = new Foe(Foe.Kind.SENTRY, h.x, h.y, h.z + 2);
        g.foes.add(sentry);
        in.force(KeyEvent.VK_K, true);
        run(g, 2 / 60f);
        int hpBefore = h.hp;
        g.hurtHero(2, sentry.x, sentry.z, true);
        boolean parried = h.hp == hpBefore && sentry.state == Foe.STUN && sentry.riposteT > 0;
        in.force(KeyEvent.VK_K, false);
        run(g, 1 / 60f);
        float sHp = sentry.hp;
        sentry.damage(g, Foe.SWORD, 1, sentry.x, sentry.centerY(), sentry.z);
        check(parried && sentry.hp == sHp - 3, "a parry staggers the attacker, and the riposte hits for triple (hp " + sHp + " -> " + sentry.hp + ")");
        g.foes.clear();

        g.gear.windcloak = true;
        float[] fire = pl.fires.get(1);
        h.place(fire[0] + .4f, fire[1], fire[2]);
        h.stamina = h.maxStamina;
        run(g, .1f);
        tap(g, in, KeyEvent.VK_SPACE);
        run(g, 2);
        check(h.st == Hero.State.GLIDE && h.y > fire[1] + 8, "standing in a campfire's warm air, Space lifts you up on the Windcloak (rose " + (h.y - fire[1]) + " m)");

        Places.Gate fg = pl.gates.get(Places.FROST);
        h.place(fg.x, fg.y, fg.z - 15);
        h.yaw = g.camYaw = M.PI;
        h.stamina = h.maxStamina;
        in.force(KeyEvent.VK_W, true);
        for (int k = 0; k < 120 && h.st != Hero.State.AIR; k++) run(g, 1 / 60f);
        in.force(KeyEvent.VK_W, false);
        run(g, .15f);
        tap(g, in, KeyEvent.VK_SPACE);
        float startZ = h.z, startY = h.y;
        int hpGlide = h.hp;
        run(g, 1.5f);
        check(h.st == Hero.State.GLIDE && startZ - h.z > 9 && startY - h.y < 6 && h.hp == hpGlide,
                "stepping off Rimeholt's mesa and opening the Windcloak glides you out from the cliff (" + (startZ - h.z) + " m out, " + (startY - h.y) + " m down)");
        in.force(KeyEvent.VK_W, false);

        g.story.enterDungeon(5);
        Dungeon sun = g.dungeon;
        g.foes.clear();
        h.invuln = 99;
        boolean dark = !sun.sunlit(0) && !sun.sunlit(1) && !sun.find('S', 0).on && sun.find('C', 0).hidden;
        Dungeon.Thing entryMirror = null;
        for (Dungeon.Thing t : sun.things) if (t.kind == 'r' && t.j >= 26) entryMirror = t;
        h.place(entryMirror.x + 1.4f, sun.floor(entryMirror.x + 1.4f, entryMirror.z), entryMirror.z);
        run(g, .1f);
        tap(g, in, KeyEvent.VK_E);
        run(g, .3f);
        boolean doorOpen = sun.sunlit(1) && sun.find('S', 0).on;
        for (Dungeon.Thing t : sun.things) if (t.kind == 'r' && t.j < 26) t.on = !t.on;
        run(g, .2f);
        check(dark && doorOpen && sun.sunlit(0) && !sun.find('C', 0).hidden,
                "turning the Sunscour's mirrors bends the sunbeams into its crystals: the door opens and Wren's chest appears");

        g.screen = Game.Screen.PLAY;
        Dungeon.Thing vent = null;
        for (Dungeon.Thing t : sun.things) if (t.kind == 'V') vent = t;
        h.place(vent.x, vent.y, vent.z);
        h.yaw = g.camYaw = 0;
        h.stamina = h.maxStamina;
        run(g, .1f);
        tap(g, in, KeyEvent.VK_SPACE);
        run(g, 1.2f);
        in.force(KeyEvent.VK_W, true);
        run(g, 2.5f);
        in.force(KeyEvent.VK_W, false);
        run(g, .5f);
        check(sun.tj(h.z) <= 14 && h.y > -1, "a vent lifts the Windcloak and you glide clean over the Sunscour's chasm (row " + sun.tj(h.z) + ")");

        Foe stalker = new Foe(Foe.Kind.STALKER, h.x + 6, h.y, h.z);
        stalker.setState(Foe.HIDE);
        g.foes.add(stalker);
        h.invuln = 99;
        boolean shielded = !stalker.damage(g, Foe.SWORD, 3, stalker.x, stalker.centerY(), stalker.z);
        boolean surfaced = false;
        for (int k = 0; k < 300 && !surfaced; k++) {
            run(g, 1 / 60f);
            surfaced = stalker.state == Foe.RECOVER;
        }
        boolean hurt = surfaced && stalker.damage(g, Foe.SWORD, 1, stalker.x, stalker.centerY(), stalker.z);
        check(shielded && hurt, "a Dune Stalker can't be hit under the sand, but strikes and is exposed afterwards");
        g.foes.clear();

        sun.bigKey = true;
        for (Dungeon.Thing t : sun.things) if (t.kind == 'B') t.on = true;
        h.place(sun.bossX, sun.bossY, sun.bossZ - 8);
        run(g, .2f);
        Foe wyrm = g.boss;
        boolean woke = wyrm != null && wyrm.kind == Foe.Kind.ASHMAW;
        if (woke) {
            wyrm.setState(Foe.RECOVER);
            run(g, .05f);
        }
        float[] maw = new float[3];
        boolean choked = woke && wyrm.point(0, maw) && wyrm.damage(g, Foe.ARROW, 1, maw[0], maw[1], maw[2]) && wyrm.state == Foe.STUN;
        float wHp = woke ? wyrm.hp : 0;
        if (choked) wyrm.damage(g, Foe.SWORD, 2, wyrm.headX, wyrm.headY, wyrm.headZ);
        check(choked && wyrm.hp < wHp, "an arrow in Ashmaw's open maw brings it down, and then the sword bites");
        g.boss = null;
        g.foes.clear();
        h.invuln = 0;
        g.story.exitDungeon();
        g.screen = Game.Screen.PLAY;

        run(g, .1f);
        Foe drake = g.drake;
        boolean spawned = drake != null && g.foes.contains(drake);
        if (spawned) {
            drake.x = h.x + 20;
            drake.z = h.z;
            drake.y = h.y + 12;
        }
        h.invuln = 99;
        run(g, .2f);
        boolean engaged = spawned && g.boss == drake && drake.state != Foe.IDLE;
        float[] p = new float[3];
        if (engaged) for (int i = 0; i < 3; i++) if (drake.point(i, p)) drake.damage(g, Foe.ARROW, 1, p[0], p[1], p[2]);
        boolean downed = engaged && drake.state == Foe.STUN;
        float dHp = engaged ? drake.hp : 0;
        run(g, .5f);
        if (downed) drake.damage(g, Foe.SWORD, 2, drake.headX, drake.headY, drake.headZ);
        check(downed && drake.hp < dHp, "the Stormdrake comes down on you; darkening its three storm-scales grounds it for the sword");
        drake.calm(g);
        h.invuln = 0;
    }

    /** Each weapon's weight and reach, the hammer's smashing, the outfits, the gear screen and a treasure chest. */
    private static void armsTests(Game g, Input in) {
        Hero h = g.hero;
        Places pl = g.world.places;
        g.screen = Game.Screen.PLAY;
        g.foes.clear();
        g.gear.weapons = 31;
        g.gear.bowOut = false;
        h.invuln = 99;
        float fx = pl.villageX + 30, fz = pl.villageZ + 60;
        float[] got = new float[5];
        for (int w = 0; w < 5; w++) {
            g.gear.weapon = w;
            h.place(fx, g.world.height(fx, fz), fz);
            h.yaw = g.camYaw = 0;
            h.stamina = h.maxStamina;
            Foe dummy = new Foe(Foe.Kind.WOLF, h.x, h.y, h.z + 1.6f);
            dummy.hp = dummy.maxHp = 50;
            dummy.frozenT = 9;
            g.foes.add(dummy);
            run(g, .05f);
            tap(g, in, KeyEvent.VK_J);
            run(g, .9f);
            got[w] = 50 - dummy.hp;
            g.foes.clear();
        }
        float m = g.swordMult();
        boolean weights = Math.abs(got[0] - m) < .01f && got[1] > got[0] * 1.7f && got[4] > got[1] && got[3] < got[0] && got[2] > got[0];
        check(weights, "every weapon swings with its own weight (sword " + got[0] + ", greatsword " + got[1] + ", spear " + got[2] + ", fangs " + got[3] + ", hammer " + got[4] + ")");

        float[] reach = new float[2];
        for (int k = 0; k < 2; k++) {
            g.gear.weapon = k == 0 ? Arms.SWORD : Arms.SPEAR;
            h.place(fx, g.world.height(fx, fz), fz);
            h.yaw = g.camYaw = 0;
            h.stamina = h.maxStamina;
            Foe far = new Foe(Foe.Kind.WOLF, h.x, h.y, h.z + 4);
            far.hp = far.maxHp = 50;
            far.frozenT = 9;
            g.foes.add(far);
            run(g, .05f);
            tap(g, in, KeyEvent.VK_J);
            run(g, .9f);
            reach[k] = 50 - far.hp;
            g.foes.clear();
        }
        check(reach[0] == 0 && reach[1] > 0, "the spear reaches a foe 4 m away that the sword can't (sword " + reach[0] + ", spear " + reach[1] + ")");

        g.gear.weapon = Arms.HAMMER;
        h.place(fx, g.world.height(fx, fz), fz);
        h.stamina = h.maxStamina;
        Foe[] ring = new Foe[3];
        for (int k = 0; k < 3; k++) {
            ring[k] = new Foe(Foe.Kind.WOLF, h.x + M.sin(k * 2.1f) * 3, h.y, h.z + M.cos(k * 2.1f) * 3);
            ring[k].hp = ring[k].maxHp = 50;
            g.foes.add(ring[k]);
        }
        in.force(KeyEvent.VK_J, true);
        run(g, 1.6f);
        in.force(KeyEvent.VK_J, false);
        run(g, .6f);
        boolean quake = true;
        for (Foe f : ring) quake &= f.hp < 50 && (f.state == Foe.STUN || f.riposteT > 0);
        check(quake, "the hammer's charged quake hits and staggers every foe around you");
        g.foes.clear();

        g.story.enterDungeon(1);
        Dungeon forge = g.dungeon;
        g.foes.clear();
        Dungeon.Thing wall = null;
        for (Dungeon.Thing t : forge.things) if (t.kind == 'X') wall = t;
        float[] spot = null;
        for (int[] d : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            char c = forge.at(wall.i + d[0], wall.j + d[1]);
            if (spot == null && c != '#' && c != ' ' && forge.floor(forge.cx(wall.i + d[0]), forge.cz(wall.j + d[1])) < 3) spot = new float[]{forge.cx(wall.i + d[0]), forge.cz(wall.j + d[1])};
        }
        h.place(spot[0], forge.floor(spot[0], spot[1]), spot[1]);
        h.yaw = g.camYaw = M.atan2(wall.x - h.x, wall.z - h.z);
        h.stamina = h.maxStamina;
        run(g, .05f);
        tap(g, in, KeyEvent.VK_J);
        run(g, 1);
        check(wall.on, "the Rimebreaker Hammer smashes a cracked wall, no bomb needed");
        g.story.exitDungeon();
        g.screen = Game.Screen.PLAY;

        h.invuln = 0;
        h.hp = h.maxHp;
        g.gear.outfit = Arms.GARB;
        g.hurtHero(4, h.x + 1, h.z);
        int plain = h.maxHp - h.hp;
        h.invuln = 0;
        h.hp = h.maxHp;
        g.gear.outfits |= 1 << Arms.PLATE;
        g.gear.outfit = Arms.PLATE;
        g.hurtHero(4, h.x + 1, h.z);
        int plated = h.maxHp - h.hp;
        check(plain == 4 && plated == 3, "Forgeplate armour turns aside a third of a blow (" + plain + " -> " + plated + ")");
        h.invuln = 99;
        g.screen = Game.Screen.PLAY;

        g.gear.outfit = Arms.GARB;
        g.gear.outfits |= 1 << Arms.WRAPS;
        tap(g, in, KeyEvent.VK_I);
        boolean opened = g.screen == Game.Screen.GEAR;
        tap(g, in, KeyEvent.VK_D);
        tap(g, in, KeyEvent.VK_S);
        tap(g, in, KeyEvent.VK_S);
        tap(g, in, KeyEvent.VK_E);
        tap(g, in, KeyEvent.VK_I);
        check(opened && g.gear.outfit == Arms.WRAPS && g.screen == Game.Screen.PLAY,
                "the gear screen (I) changes outfits (opened=" + opened + ", outfit=" + g.gear.outfit + ", screen=" + g.screen + ")");

        Places.Chest star = null;
        for (Places.Chest c : pl.chests) if (c.id.equals("starweave")) star = c;
        h.place(star.x + 1.2f, star.y, star.z);
        run(g, .1f);
        tap(g, in, KeyEvent.VK_E);
        dialogs(g, in);
        check(g.owns(1, Arms.STARWEAVE) && star.anim == 1 && g.story.flags.contains("chest:starweave"), "the chest atop the desert mesa holds the Starweave Robe");
        g.gear.weapon = Arms.SWORD;
        g.gear.outfit = Arms.GARB;
        h.invuln = 0;
    }

    /** Clicks through any dialog or item screens. */
    private static void dialogs(Game g, Input in) {
        for (int i = 0; i < 60 && g.screen != Game.Screen.PLAY; i++) {
            tap(g, in, KeyEvent.VK_ENTER);
            run(g, .3f);
        }
    }

    private static void gustTests(Game g, Input in) {
        Hero h = g.hero;
        Places pl = g.world.places;
        g.screen = Game.Screen.PLAY;
        g.foes.clear();
        g.gear.windcloak = true;
        g.gear.spells |= 1 << Magic.GUST;
        g.gear.spell = Magic.GUST;
        float x = pl.villageX + 30, z = pl.villageZ + 60, ground = g.world.height(x, z);
        h.place(x, ground, z);
        h.fp = h.maxFp;
        h.hp = h.maxHp;
        h.stamina = h.maxStamina;
        run(g, .1f);
        tap(g, in, KeyEvent.VK_C);
        float top = 0;
        for (int i = 0; i < 60; i++) {
            run(g, 1 / 60f);
            top = Math.max(top, h.y - g.level.floor(h.x, h.z));
        }
        check(top > 9, "Skyward Gust throws the hero high into the sky from flat ground (" + Math.round(top) + " m)");
        tap(g, in, KeyEvent.VK_SPACE);
        check(h.st == Hero.State.GLIDE, "Space opens the glider up there");
        for (int i = 0; i < 600 && h.st != Hero.State.MOVE; i++) {
            if (h.st == Hero.State.GLIDE && i == 300) tap(g, in, KeyEvent.VK_SPACE);
            run(g, 1 / 60f);
        }
        check(h.st == Hero.State.MOVE && h.hp == h.maxHp, "and the hero lands unhurt (" + h.st + ", " + h.hp + "/" + h.maxHp + " hp)");
    }

    /** Co-op without a network: a friend's hero driven by their own keys, foes choosing between heroes, and the world copied to a friend. */
    private static void coopTests(Game g, Input in) throws java.io.IOException {
        Places pl = g.world.places;
        g.screen = Game.Screen.PLAY;
        g.foes.clear();
        float x = pl.villageX + 40, z = pl.villageZ + 70, y = g.world.height(x, z);
        g.hero.place(x, y, z);
        Game.Player friend = new Game.Player(new Hero(x + 3, y, z), new Game.Gear(), new Input(), "Friend");
        friend.gear.sword = true;
        friend.id = 1;
        g.players.add(friend);
        friend.camYaw = 0;
        float z0 = friend.hero.z;
        friend.in.force(KeyEvent.VK_W, true);
        run(g, 1);
        friend.in.force(KeyEvent.VK_W, false);
        check(friend.hero.z - z0 > 3 && M.dist(g.hero.x, g.hero.z, x, z) < .5f, "a friend's own keys move only their hero (" + Math.round(friend.hero.z - z0) + " m)");
        // Somewhere level with the friend, about 10 m off (wolves only hunt what's on their own level).
        float wx = friend.hero.x + 10, wz = friend.hero.z;
        for (int k = 0; k < 16 && Math.abs(g.world.height(wx, wz) - friend.hero.y) > 1; k++) {
            wx = friend.hero.x + M.sin(k * .4f) * 10;
            wz = friend.hero.z + M.cos(k * .4f) * 10;
        }
        Foe wolf = new Foe(Foe.Kind.WOLF, wx, g.world.height(wx, wz), wz);
        wolf.yaw = M.atan2(friend.hero.x - wolf.x, friend.hero.z - wolf.z);
        g.foes.add(wolf);
        g.hero.place(x - 60, g.world.height(x - 60, z), z);
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < 360; i++) {
            run(g, 1 / 60f);
            closest = Math.min(closest, M.dist(wolf.x, wolf.z, friend.hero.x, friend.hero.z));
        }
        check(closest < 2.5f || friend.hero.hp < friend.hero.maxHp, "a wolf hunts the friend it is nearest to (closest " + Math.round(closest) + " m)");
        Game copy = new Game(new Input(), new Audio(false));
        copy.screen = Game.Screen.PLAY;
        Coop.mirror(g, copy);
        Game.Player seen = null;
        for (Game.Player p : copy.players) if (p.id == 1) seen = p;
        boolean same = seen != null && M.dist(seen.hero.x, seen.hero.z, friend.hero.x, friend.hero.z) < .01f && copy.foes.size() == g.foes.size();
        for (int i = 0; same && i < g.foes.size(); i++) same = M.dist(copy.foes.get(i).x, copy.foes.get(i).z, g.foes.get(i).x, g.foes.get(i).z) < .01f;
        check(same, "a friend's game sees every hero and foe where the host has them (" + copy.players.size() + " players, " + copy.foes.size() + " foes)");
        g.players.remove(friend);
        g.foes.clear();
    }

    /** Shift locks on only to someone; duellists fight (blocks, parries, a friendly end); a locked-on Gust hovers until an attack spell. */
    private static void duelTests(Game g, Input in) {
        Hero h = g.hero;
        Places pl = g.world.places;
        g.screen = Game.Screen.PLAY;
        g.foes.clear();
        float x = pl.villageX + 40, z = pl.villageZ + 70;
        h.place(x, g.world.height(x, z), z);
        h.hp = h.maxHp;
        h.invuln = 0;
        in.force(KeyEvent.VK_SHIFT, true);
        run(g, .1f);
        check(!h.locked, "Shift with nobody around locks on to nothing");
        in.force(KeyEvent.VK_SHIFT, false);
        run(g, .1f);

        Game.Player friend = new Game.Player(new Hero(x, h.y, z + 1.6f), new Game.Gear(), new Input(), "Friend");
        friend.id = 1;
        friend.gear.shield = friend.gear.sword = true;
        g.players.add(friend);
        Hero f = friend.hero;
        f.yaw = M.PI;
        h.yaw = 0;
        run(g, .05f);
        int hp0 = f.hp;
        g.swordStrike(h, 150, 2.4f, 1);
        check(f.hp == hp0, "a sword can't hurt a friend who isn't duelling");
        h.dueling = f.dueling = true;
        in.force(KeyEvent.VK_SHIFT, true);
        run(g, .1f);
        check(h.locked && h.rival == f, "two duellists can lock on to each other");
        in.force(KeyEvent.VK_SHIFT, false);
        g.swordStrike(h, 150, 2.4f, 1);
        check(f.hp < hp0, "a duellist's sword wounds the other (" + hp0 + " to " + f.hp + " hp)");
        run(g, .6f);
        f.place(x, h.y, z + 1.6f);
        f.yaw = M.PI;
        h.yaw = 0;
        f.invuln = 0;
        friend.in.force(KeyEvent.VK_K, true);
        run(g, 1 / 60f);
        g.swordStrike(h, 150, 2.4f, 1);
        friend.in.force(KeyEvent.VK_K, false);
        check(h.st == Hero.State.HURT, "a parried duellist reels back");
        run(g, .5f);
        f.invuln = 0;
        f.hp = 2;
        f.setState(Hero.State.MOVE);
        h.yaw = M.atan2(f.x - h.x, f.z - h.z);
        g.swordStrike(h, 150, 2.4f, 3);
        check(f.hp == 1 && f.st != Hero.State.DEAD && !f.dueling && !h.dueling, "a duel ends at the last heart, with nobody killed");
        g.players.remove(friend);

        // A locked-on Skyward Gust: hang in the air, then an Ember Bolt lets go.
        Foe mark = new Foe(Foe.Kind.WOLF, h.x, g.world.height(h.x, h.z + 9), h.z + 9);
        mark.frozenT = 99;
        g.foes.add(mark);
        h.place(x, g.world.height(x, z), z);
        h.yaw = g.camYaw = 0;
        h.fp = h.maxFp;
        g.gear.windcloak = true;
        g.gear.spells |= 1 << Magic.GUST | 1 << Magic.EMBER;
        g.gear.spell = Magic.GUST;
        in.force(KeyEvent.VK_SHIFT, true);
        run(g, .1f);
        tap(g, in, KeyEvent.VK_C);
        run(g, 3);
        float high = h.y - g.level.floor(h.x, h.z);
        check(h.st == Hero.State.HOVER && high > 8, "a Skyward Gust cast while locked on hangs in the air (" + h.st + ", " + Math.round(high) + " m up)");
        g.gear.spell = Magic.EMBER;
        h.fp = h.maxFp;
        tap(g, in, KeyEvent.VK_C);
        run(g, 1);
        in.force(KeyEvent.VK_SHIFT, false);
        check(h.st == Hero.State.AIR || h.st == Hero.State.MOVE || h.st == Hero.State.GLIDE, "an attack spell from up there lets the wind go (" + h.st + ")");
        run(g, 2);
        g.foes.clear();
    }

    private static void devTests(Game g, Input in) {
        g.screen = Game.Screen.PLAY;
        g.gear.outfits = 1;
        g.gear.songs = 0;
        tap(g, in, KeyEvent.VK_OPEN_BRACKET);
        check(g.screen == Game.Screen.DEV, "[ opens the dev pack");
        // Drawing it must not throw; -Dkestrel.devshot=FILE keeps the frame to look at.
        BufferedImage frame = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        new Hud(g, new Renderer(640, 360)).draw(frame.createGraphics(), 1280, 720);
        String shot = System.getProperty("kestrel.devshot");
        if (shot != null) {
            try {
                ImageIO.write(frame, "png", new File(shot));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        tap(g, in, KeyEvent.VK_D);
        tap(g, in, KeyEvent.VK_S);
        tap(g, in, KeyEvent.VK_E);
        check(g.owns(1, 1), "the dev pack puts the chosen outfit in the inventory");
        for (int t = 0; t < 3; t++) tap(g, in, KeyEvent.VK_D);
        tap(g, in, KeyEvent.VK_F);
        check(g.gear.songs == (1 << Story.SONG_NAMES.length) - 1, "F takes every song in the tab at once");
        tap(g, in, KeyEvent.VK_OPEN_BRACKET);
        check(g.screen == Game.Screen.PLAY, "[ closes the dev pack");
        int before = g.foes.size() - (g.drake != null && g.foes.contains(g.drake) ? 1 : 0);
        int summonTab = java.util.List.of(Game.DEV_TABS).indexOf("Summon");
        for (Foe.Kind k : Foe.Kind.values()) g.devGive(summonTab, k.ordinal(), false);
        check(g.foes.size() == before + Foe.Kind.values().length && g.drake != null && g.boss != null, "the dev pack can summon every creature, bosses included");
        g.hero.hp = 999;
        for (int i = 0; i < 300; i++) g.update(1 / 60f);
        for (Foe.Kind k : Foe.Kind.values()) for (int i = g.foes.size() - 1; i >= 0; i--) if (g.foes.get(i).kind == k) g.foes.remove(i);
        g.boss = null;
        g.drake = null;
        g.hero.hp = g.hero.maxHp;
    }

    /** The towns, the law and the royal guard: Kingsreach and a grown Brindle, crimes, the hunt, and getting away. */
    private static void lawTests(Game g, Input in) {
        Places pl = g.world.places;
        Hero h = g.hero;
        Places.Town king = null;
        for (Places.Town t : pl.towns) if (t.name().equals("Kingsreach")) king = t;
        int inKing = 0;
        for (Places.Building b : pl.buildings) if (king != null && M.dist(b.x(), b.z(), king.x(), king.z()) < king.radius()) inKing++;
        check(king != null && inKing >= 35 && pl.folk.size() >= 50, "Kingsreach is a big town (" + inKing + " buildings) and the towns are busy (" + pl.folk.size() + " townsfolk)");
        boolean open = true;
        for (Places.Npc n : pl.folk) open &= g.field.floor(n.x, n.z) < n.y + .6f && n.y > World.WATER;
        check(open, "every townsperson starts out on open, dry ground");
        g.screen = Game.Screen.PLAY;
        h.st = Hero.State.MOVE;
        h.hp = h.maxHp;
        h.wanted = 0;
        Places.Npc victim = pl.folk.get(0);
        h.place(victim.x, victim.y, victim.z - 1.2f);
        h.yaw = 0;
        g.swordStrike(h, 90, 2.2f, 1);
        check(victim.hp < 4 && h.wanted == 1 && victim.fleeT > 0, "striking a townsperson is a crime: one star, and they run");
        for (int i = 0; i < 6 && victim.downT <= 0; i++) {
            victim.flash = 0;
            h.place(victim.x, victim.y, victim.z - 1.2f);
            g.swordStrike(h, 90, 2.2f, 1);
        }
        check(victim.downT > 0 && h.wanted >= 2, "killing them makes it worse (" + h.wanted + " stars)");
        run(g, 7);
        int hunters = 0;
        for (Foe f : g.foes) if (f.kind == Foe.Kind.GUARD && (f.bits & 2) != 0) hunters++;
        check(hunters >= 1, "the royal guard send reinforcements after a wanted player (" + hunters + ")");
        h.hp = h.maxHp;
        boolean struck = false;
        for (int i = 0; i < 14 && !struck; i++) {
            run(g, 1);
            struck = h.hp < h.maxHp;
        }
        check(struck && h.wanted > 0, "the guard catch up and strike");
        h.hp = h.maxHp;
        // Gone over the hills, out of every guard's sight.
        h.place(pl.campX, g.world.height(pl.campX, pl.campZ), pl.campZ);
        h.seenT = 0;
        run(g, 30);
        hunters = 0;
        for (Foe f : g.foes) if (f.kind == Foe.Kind.GUARD && (f.bits & 2) != 0 && f.alive()) hunters++;
        check(h.wanted == 0 && hunters == 0, "far away and out of sight long enough, the hunt is called off (" + h.wanted + " stars, " + hunters + " guards)");
        h.place(victim.hx, g.world.height(victim.hx, victim.hz), victim.hz - 3);
        g.summon(Foe.Kind.GUARD);
        Foe guard = g.foes.get(g.foes.size() - 1);
        guard.damage(g, Foe.SWORD, 1, guard.x, guard.y + 1, guard.z);
        check(h.wanted == 1, "striking a guard who wasn't after you is a crime too");
        float[] pose = Characters.GUARD.newPose();
        Characters.THRUST.apply(.2f, 1, pose);
        check(pose[Characters.GUARD.part("armUR") * 3] != 0 && pose[Characters.GUARD.part("capeU") * 3] == 0,
                "the halberd thrust (made for a body without a cape) fits the caped guard");
        h.wanted = 0;
        g.foes.remove(guard);
        h.hp = h.maxHp;
    }

    /** Job boards and their jobs, carriages, homes, fines and pickpocketing, all through the keys a player would press. */
    private static void jobTests(Game g, Input in) {
        Places pl = g.world.places;
        Hero h = g.hero;
        Game.Gear gr = g.gear;
        g.screen = Game.Screen.PLAY;
        h.st = Hero.State.MOVE;
        h.wanted = 0;
        h.hp = h.maxHp;
        check(pl.boards.size() == 2 && pl.carriages.size() == 4 && pl.homes.size() == 2 && g.jobs.offers(0).size() == 3,
                "both towns have a job board and a house for sale, and four carriage stands link the settlements");
        // A bounty: the gang turns up at the spot, and bringing them all down pays.
        int gleams = gr.gleams;
        float[] at = {pl.campX + 60, pl.campZ + 40};
        g.jobs.accept(new Jobs.Offer(Jobs.BOUNTY, 4, at[0], at[1], 450, "test"));
        run(g, .1f);
        java.util.List<Foe> gang = new java.util.ArrayList<>();
        for (Foe f : g.foes) if (f.job && f.alive()) gang.add(f);
        check(gang.size() == 4 && gang.stream().anyMatch(f -> f.kind == Foe.Kind.BANDIT && (f.bits & 1) != 0), "a bounty puts the outlaw and their gang at the spot");
        for (Foe f : gang) f.damage(g, Foe.BOMB, 100, f.x, f.y + 1, f.z);
        check(gr.job == Jobs.NONE && gr.gleams >= gleams + 450 && gr.renown >= 1 && h.wanted == 0, "bringing down the whole gang pays the bounty (and isn't a crime)");
        // A delivery to another carriage stand.
        float[] stand = pl.carriages.get(1);
        g.jobs.accept(new Jobs.Offer(Jobs.DELIVER, 1, stand[0], stand[2], 100, "test"));
        h.place(stand[0] + 2, g.world.height(stand[0] + 2, stand[2]), stand[2]);
        run(g, .1f);
        check(gr.job == Jobs.NONE, "a parcel is delivered on reaching its carriage stand");
        // Pearl diving: three pearls appear on the reef.
        float[] sc = pl.schools.get(1);
        g.jobs.accept(new Jobs.Offer(Jobs.PEARLS, 3, sc[0], sc[2], 260, "test"));
        run(g, .1f);
        boolean three = g.jobs.pearls.size() == 3;
        for (float[] p : new java.util.ArrayList<>(g.jobs.pearls)) {
            h.place(p[0], p[1], p[2]);
            g.jobs.progress();
        }
        check(three && gr.job == Jobs.NONE, "pearl diving: three pearls lie on the reef, and gathering them finishes the job");
        // The carriage: E at the stand opens the destinations; E again rides there for the fare.
        float[] c0 = pl.carriages.get(0);
        h.place(c0[0] + 1.5f, g.world.height(c0[0] + 1.5f, c0[2]), c0[2]);
        g.screen = Game.Screen.PLAY;
        h.st = Hero.State.MOVE;
        gr.gleams = 100;
        run(g, .1f);
        tap(g, in, KeyEvent.VK_E);
        boolean menu = g.screen == Game.Screen.PICK;
        tap(g, in, KeyEvent.VK_E);
        check(menu && gr.gleams == 100 - Jobs.FARE && M.dist(h.x, h.z, c0[0], c0[2]) > 200, "a carriage carries you to another settlement for the fare");
        // A house: buy it, then sleep in it like at a shrine.
        float[] home = pl.homes.get(0);
        gr.gleams = Jobs.HOME_PRICE + 10;
        h.place(home[0], home[1], home[2]);
        g.screen = Game.Screen.PLAY;
        h.st = Hero.State.MOVE;
        run(g, .1f);
        tap(g, in, KeyEvent.VK_E);
        boolean bought = (gr.homes & 1 << (int) home[4]) != 0 && gr.gleams == 10;
        run(g, .1f);
        tap(g, in, KeyEvent.VK_E);
        check(bought && g.screen == Game.Screen.REST, "buy a house, then sleep there to rest and save");
        g.screen = Game.Screen.PLAY;
        h.st = Hero.State.MOVE;
        // Fines: with two stars, the board offers to take 300 gleams to clear them.
        float[] bd = pl.boards.get(0);
        h.place(bd[0] + 1, g.world.height(bd[0] + 1, bd[2]), bd[2]);
        h.wanted = 2;
        h.seenT = 0;
        h.heat = 99;
        gr.gleams = 400;
        run(g, .1f);
        tap(g, in, KeyEvent.VK_E);
        tap(g, in, KeyEvent.VK_E);
        check(h.wanted == 0 && gr.gleams == 100, "paying a fine at a job board clears your wanted level");
        g.screen = Game.Screen.PLAY;
        // Pickpocketing: it works or the mark catches you (and that's a crime).
        Places.Npc mark = pl.folk.get(3);
        mark.downT = 0;
        mark.robT = 0;
        h.place(mark.x - M.sin(mark.yaw) * 1.2f, mark.y, mark.z - M.cos(mark.yaw) * 1.2f);
        gleams = gr.gleams;
        g.jobs.steal();
        check(gr.gleams > gleams && h.wanted == 0 || gr.gleams == gleams && h.wanted == 1, "L picks a pocket, or gets you caught and wanted");
        h.wanted = 0;
        h.hp = h.maxHp;
        for (Foe.Kind k : Foe.Kind.values()) g.foes.removeIf(f -> f.kind == Foe.Kind.GUARD && (f.bits & 2) != 0);
    }

    /** Reefs, diving, breath and drowning. */
    private static void diveTests(Game g, Input in) {
        Places pl = g.world.places;
        Hero h = g.hero;
        g.screen = Game.Screen.PLAY;
        h.st = Hero.State.MOVE;
        check(pl.reef.size() > 100 && pl.schools.size() >= 8, "reefs grow in the shallows (" + pl.reef.size() + " pieces, " + pl.schools.size() + " fish schools)");
        int sunk = 0;
        for (Places.Chest c : pl.chests) if (c.y < -4) sunk++;
        check(sunk >= 4, "chests lie on the sea floor in wrecks and ruins (" + sunk + ")");
        float[] sc = pl.schools.get(0);
        h.place(sc[0], World.WATER, sc[2]);
        run(g, .5f);
        boolean swam = h.st == Hero.State.SWIM;
        in.force(KeyEvent.VK_CONTROL, true);
        run(g, 3);
        check(swam && h.st == Hero.State.DIVE && h.y < -4, "Ctrl dives below the surface (y " + h.y + ")");
        h.hp = h.maxHp;
        run(g, 31);
        in.force(KeyEvent.VK_CONTROL, false);
        check(h.breath <= 0 && h.hp < h.maxHp, "staying down too long runs out of air and hurts");
        h.hp = h.maxHp;
        in.force(KeyEvent.VK_SPACE, true);
        run(g, 10);
        in.force(KeyEvent.VK_SPACE, false);
        run(g, 1);
        check(h.st == Hero.State.SWIM && h.breath > 0, "Space swims back up for air");
        h.hp = h.maxHp;
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
        skyTests(g, in);
        armsTests(g, in);
        gustTests(g, in);
        duelTests(g, in);
        devTests(g, in);
        lawTests(g, in);
        diveTests(g, in);
        jobTests(g, in);
        try {
            coopTests(g, in);
        } catch (java.io.IOException e) {
            check(false, "co-op world copy: " + e);
        }

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

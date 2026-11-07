package kestrel;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;

/** The running game: levels, combat, projectiles, pickups and screen flow. Story content lives in Story. */
final class Game {
    enum Screen { TITLE, PLAY, DIALOG, FLUTE, MENU, MAP, PAUSE, OVER, END, REST, LEVEL, TRAVEL, GEAR, DEV, JOIN, PICK }

    static final int ARROW = 0, FIREBALL = 1, SEED = 2, WATERBALL = 3, LANTERN = 4, ROCK = 5, P_EMBER = 6, P_FROST = 7, P_RADIANT = 8;
    static final int CONTAINER = 7;
    static final long SEED_WORLD = 1337;

    static final class Gear {
        boolean sword, shield, flute, bow, bombBag, claw, ascended, bowOut, frostbrand, longclaw, windcloak, stormcloak, sunRelic;
        /** Side quests: two bits each (0 unknown, 1 asked, 2 item in hand, 3 done); wolves counts Hessa's hunt. */
        int quests, wolves;
        /** The weapon, outfit and cape dye in use, and bitmasks of those owned. */
        int weapon, weapons = 1, outfit, outfits = 1, dye, dyes = 1;
        int arrows, bombs, gleams, pieces, songs, stones, item;
        int hearts = 3, level = 1, vig, end, mnd, str, arc, swordLv, flasks = 3, maxFlasks = 3, spells, spell;
        /** The job from a board (Jobs.NONE..CLEAR) and its progress; renown counts jobs done; homes owned, one bit per town. */
        int job, jobGoal, jobDone, jobX, jobZ, jobPay, renown, homes;
    }

    static final class Shot {
        int kind;
        float x, y, z, vx, vy, vz, life, dmg = 1;
        final List<Foe> hit = new ArrayList<>(2);
        /** Shots loosed while locked on home in on that weak point and pass by the rest of the foe. */
        Foe homing;
        /** The hero who loosed it (so it can strike a duelling friend, never its owner). */
        Hero owner;
        int homePoint;

        boolean friendly() { return kind == ARROW || kind >= P_EMBER; }
    }

    static final class Bomb {
        float x, y, z, vx, vy, vz, fuse;
    }

    static final class Spark {
        float x, y, z, vx, vy, vz, life;
        int col;
    }

    static final class Drop {
        int item;
        float x, y, z, vy, life = 14;
    }

    /**
     * Someone playing: this computer's own hero first, then friends who joined over the network (see Coop). Each has
     * their own hero, gear, keys and camera.
     */
    static final class Player {
        final Hero hero;
        final Gear gear;
        Input in;
        String name;
        /** Which player this is in co-op (0 is the host). */
        int id;
        float camYaw, camPitch = -.3f, downT;
        /** The recent sword trail, hilt and tip positions. */
        final float[] hilt = new float[30], tip = new float[30];
        int trailN;

        Player(Hero hero, Gear gear, Input in, String name) {
            this.hero = hero;
            this.gear = gear;
            this.in = in;
            this.name = name;
        }
    }

    /**
     * The hero, gear, keys and camera every rule works on. Normally this computer's own player; for a moment at a
     * time a friend's (see as()), so everything in the game works the same for every hero.
     */
    Input in;
    Gear gear = new Gear();
    Hero hero;
    final Player me;
    final List<Player> players = new ArrayList<>();
    private Player current;
    /** Co-op, when hosting or joined; null alone. */
    Coop coop;
    final Audio audio;
    final World world;
    final Field field;
    final Jobs jobs = new Jobs(this);
    final Climate climate = new Climate();
    final Story story;
    final Magic magic;
    final Horse horse = new Horse();
    final Life life;
    /** The Stormdrake, once it takes to the skies. */
    Foe drake;
    final Random rng = new Random(9);
    final List<Foe> foes = new ArrayList<>();
    final List<Shot> shots = new ArrayList<>();
    final List<Bomb> bombs = new ArrayList<>();
    final List<Spark> sparks = new ArrayList<>();
    final List<Drop> drops = new ArrayList<>();
    final List<float[]> waves = new ArrayList<>();
    final Dungeon[] dungeons = new Dungeon[6];
    Level level;
    Dungeon dungeon;
    int dungeonIndex = -1;
    Screen screen = Screen.TITLE;
    float time, camYaw, camPitch = -.3f, camX, camY, camZ, shake, hurtFlash, freeze, fade, messageT, overT;
    String message = "", prompt;
    Foe boss;
    // Dialog box.
    String speaker;
    String[] pages, options;
    int page, menuSel;
    float typed;
    Runnable onClose;
    IntConsumer onChoose;
    Mesh shownItem;
    // Flute.
    final int[] notes = new int[8];
    int noteCount;
    // Grappling claw.
    boolean clawOut, clawBack, clawPull;
    /** A drop the claw has snagged and is reeling in. */
    Drop clawItem;
    float clawX, clawY, clawZ, clawDX, clawDY, clawDZ, clawDist, clawTX, clawTY, clawTZ;
    // Soulslike bookkeeping: gleam pop-ups, tips, where you respawn, and the gleams you dropped.
    float gainT, tipT;
    int gain, lostLevel = -9, lostGleams;
    float lostX, lostY, lostZ;
    String tip;
    /** The host's address being typed on the join screen. */
    final StringBuilder joinAddress = new StringBuilder();
    private Runnable action;
    private float bestD;

    Game(Input in, Audio audio) {
        this.in = in;
        this.audio = audio;
        world = new World(SEED_WORLD);
        field = new Field(world);
        life = new Life(this);
        level = field;
        Places pl = world.places;
        hero = new Hero(pl.spawnX, world.height(pl.spawnX, pl.spawnZ), pl.spawnZ);
        me = current = new Player(hero, gear, in, System.getProperty("kestrel.name", System.getProperty("user.name", "Keeper")));
        players.add(me);
        story = new Story(this);
        magic = new Magic(this);
        refreshStats();
        spawnFieldFoes();
        audio.music(Audio.TITLE);
    }

    /** Points every rule (hero, gear, keys, camera) at p; returns who it was before, to switch back with. */
    Player as(Player p) {
        Player was = current;
        was.camYaw = camYaw;
        was.camPitch = camPitch;
        current = p;
        hero = p.hero;
        gear = p.gear;
        in = p.in;
        camYaw = p.camYaw;
        camPitch = p.camPitch;
        return was;
    }

    Player current() { return current; }

    /** The nearest player still on their feet (this computer's own if everyone is down). */
    Player nearest(float x, float z) {
        Player best = me;
        float bd = Float.MAX_VALUE;
        for (Player p : players) {
            float d = M.dist(x, z, p.hero.x, p.hero.z);
            if (p.hero.st != Hero.State.DEAD && d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** A friend's hero for one frame: their keys for tools and spells, their moves, and what they pick up. */
    private void guest(float dt, Player p) {
        Player was = as(p);
        in.poll();
        Hero host = me.hero;
        // Left behind by a dungeon door or fast travel: catch up with the host.
        if (M.dist(hero.x, hero.z, host.x, host.z) > 200) hero.place(host.x + 1.5f, host.y, host.z + 1.5f);
        if (hero.st == Hero.State.DEAD) {
            hero.update(dt, this);
            if ((p.downT += dt) > 3) {
                hero.place(host.x + 1.5f, host.y, host.z + 1.5f);
                hero.hp = hero.maxHp;
                hero.setState(Hero.State.MOVE);
                hero.invuln = 2;
                p.downT = 0;
            }
        } else {
            if (in.pressed(KeyEvent.VK_Q) && gear.bow) gear.bowOut = !gear.bowOut;
            if (in.pressed(KeyEvent.VK_X)) cycleItem(1);
            if (in.pressed(KeyEvent.VK_V)) cycleSpell();
            if (in.pressed(KeyEvent.VK_P)) toggleDuel();
            if (!hero.busy()) hero.update(dt, this);
            else hero.anim.update(dt);
            collect();
        }
        as(was);
    }

    /** A friend's own screen while joined: their menus and camera; the world itself comes from the host. */
    private void guestPlay(float dt) {
        if (in.pressed(KeyEvent.VK_ESCAPE)) screen = Screen.PAUSE;
        else if (in.any(KeyEvent.VK_I)) {
            screen = Screen.GEAR;
            gearCol = 0;
            gearRow = gear.weapon;
        } else if (in.any(KeyEvent.VK_TAB)) screen = Screen.MENU;
        else if (in.pressed(KeyEvent.VK_M) && dungeon == null) screen = Screen.MAP;
        else if (in.pressed(KeyEvent.VK_OPEN_BRACKET)) screen = Screen.DEV;
        look(dt);
        if (dungeon == null) life.update(dt);
    }

    /** Mouse and arrow keys turn the camera. */
    private void look(float dt) {
        float turn = 2.2f * dt;
        camYaw += in.dx * .0036f;
        camPitch -= in.dy * .0031f;
        if (in.down(KeyEvent.VK_LEFT)) camYaw -= turn;
        if (in.down(KeyEvent.VK_RIGHT)) camYaw += turn;
        if (in.down(KeyEvent.VK_UP)) camPitch += turn * .7f;
        if (in.down(KeyEvent.VK_DOWN)) camPitch -= turn * .7f;
        camPitch = M.clamp(camPitch, -1.2f, .6f);
    }

    void spawnFieldFoes() {
        foes.clear();
        for (float[] f : world.places.foes)
            foes.add(new Foe(Foe.Kind.values()[(int) f[0]], f[1], world.height(f[1], f[2]), f[2]));
    }

    // ---- frame update -------------------------------------------------------------------------

    /** Menus a co-op host can sit in while friends play on (a conversation still pauses the island). */
    static final java.util.Set<Screen> MENUS = java.util.EnumSet.of(Screen.PAUSE, Screen.MENU, Screen.MAP, Screen.GEAR, Screen.DEV, Screen.PICK);
    /** Keys nobody presses, for the host's hero while they're in a menu. */
    private final Input calm = new Input();

    void update(float dt) {
        in.poll();
        if (coop != null) coop.before();
        Screen shown = screen;
        time += dt;
        messageT -= dt;
        shake = Math.max(0, shake - dt * 2);
        hurtFlash = Math.max(0, hurtFlash - dt * 2.5f);
        fade = Math.max(0, fade - dt * 1.5f);
        switch (screen) {
            case TITLE -> story.titleScreen();
            case DIALOG -> dialog(dt);
            case FLUTE -> flute();
            case MENU -> {
                if (in.any(KeyEvent.VK_TAB, KeyEvent.VK_I, KeyEvent.VK_ESCAPE)) screen = Screen.PLAY;
                if (in.any(KeyEvent.VK_A, KeyEvent.VK_LEFT, KeyEvent.VK_Q)) cycleItem(-1);
                if (in.any(KeyEvent.VK_D, KeyEvent.VK_RIGHT, KeyEvent.VK_E)) cycleItem(1);
            }
            case MAP -> { if (in.any(KeyEvent.VK_M, KeyEvent.VK_ESCAPE)) screen = Screen.PLAY; }
            case GEAR -> gearMenu();
            case DEV -> devPack();
            case PAUSE -> { if (in.any(KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER)) screen = Screen.PLAY; }
            case OVER -> {
                overT += dt;
                if (overT > 1.5f && in.any(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE, KeyEvent.VK_E)) story.continueAfterDeath();
            }
            case REST -> story.restMenu();
            case LEVEL -> story.levelMenu();
            case TRAVEL -> story.travelMenu();
            case END -> {
                overT += dt;
                if (overT > 4 && in.any(KeyEvent.VK_ENTER)) {
                    screen = Screen.PLAY;
                    story.exitDungeon();
                }
            }
            case PLAY -> {
                if (coop != null && coop.joined()) guestPlay(dt);
                else play(dt);
            }
            case JOIN -> joinScreen();
            case PICK -> jobs.pickMenu();
        }
        if (coop != null && coop.hosting() && players.size() > 1 && MENUS.contains(shown) && MENUS.contains(screen)) idle(dt);
        if (coop != null) coop.after(dt);
    }

    /** The world carries on with this computer's own hero standing still, so friends keep playing while the host is in a menu. */
    private void idle(float dt) {
        Screen menu = screen;
        Input keys = me.in;
        me.in = in = calm;
        screen = Screen.PLAY;
        play(dt);
        if (screen == Screen.PLAY) screen = menu;
        me.in = in = keys;
    }

    /** Typing the host's address to join their island: letters, digits, dots and dashes; Enter joins, Esc goes back. */
    private void joinScreen() {
        if (in.pressed(KeyEvent.VK_ESCAPE)) {
            screen = Screen.TITLE;
            return;
        }
        for (int k = KeyEvent.VK_0; k <= KeyEvent.VK_Z; k++)
            if (in.pressed(k) && Character.isLetterOrDigit(k) && joinAddress.length() < 60) joinAddress.append(Character.toLowerCase((char) k));
        if (in.pressed(KeyEvent.VK_PERIOD)) joinAddress.append('.');
        if (in.pressed(KeyEvent.VK_SEMICOLON) && in.down(KeyEvent.VK_SHIFT)) joinAddress.append(':');
        if (in.pressed(KeyEvent.VK_MINUS)) joinAddress.append('-');
        if (in.pressed(KeyEvent.VK_BACK_SPACE) && !joinAddress.isEmpty()) joinAddress.setLength(joinAddress.length() - 1);
        if (in.pressed(KeyEvent.VK_ENTER) && !joinAddress.isEmpty()) Coop.join(this, joinAddress.toString());
    }

    private void play(float dt) {
        if (in.pressed(KeyEvent.VK_ESCAPE)) {
            screen = Screen.PAUSE;
            return;
        }
        if (in.any(KeyEvent.VK_I)) {
            screen = Screen.GEAR;
            gearCol = 0;
            gearRow = gear.weapon;
            return;
        }
        if (in.any(KeyEvent.VK_TAB)) {
            screen = Screen.MENU;
            return;
        }
        if (in.pressed(KeyEvent.VK_M) && dungeon == null) {
            screen = Screen.MAP;
            return;
        }
        if (in.pressed(KeyEvent.VK_Q) && gear.bow) {
            gear.bowOut = !gear.bowOut;
            sfx(Audio.CLICK);
        }
        if (in.pressed(KeyEvent.VK_X)) cycleItem(1);
        if (in.pressed(KeyEvent.VK_V)) cycleSpell();
        if (in.pressed(KeyEvent.VK_P)) toggleDuel();
        if (in.pressed(KeyEvent.VK_BACK_QUOTE)) {
            story.devSkip();
            return;
        }
        if (in.pressed(KeyEvent.VK_EQUALS)) story.devJump();
        if (in.pressed(KeyEvent.VK_OPEN_BRACKET)) {
            screen = Screen.DEV;
            return;
        }
        if (in.pressed(KeyEvent.VK_CLOSE_BRACKET)) {
            gear.gleams += 1000;
            sfx(Audio.GLEAM);
            say("DEV +1000 gleams");
        }
        if (in.pressed(KeyEvent.VK_H)) {
            story.hint();
            return;
        }
        gainT -= dt;
        tipT -= dt;
        if (in.pressed(KeyEvent.VK_T) && gear.flute && hero.st == Hero.State.MOVE) {
            screen = Screen.FLUTE;
            noteCount = 0;
            hero.pose(Hero.State.FLUTE, Characters.H_FLUTE);
            return;
        }
        look(dt);
        if (freeze > 0) {
            freeze -= dt;
            return;
        }
        if (dungeon == null) climate.update(dt);
        if (dungeon == null && screen != Screen.TITLE) life.update(dt);
        if (in.pressed(KeyEvent.VK_G) && horse.summoned && dungeon == null) horse.dismiss(this);
        if (horse.ridden) {
            horse.update(dt, this);
            if (horse.ridden) hero.riding(dt, this, horse);
        } else {
            if (!hero.busy()) hero.update(dt, this);
            else hero.anim.update(dt);
            horse.update(dt, this);
        }
        magic.update(dt);
        for (int i = 1; i < players.size(); i++) guest(dt, players.get(i));
        if (lostGleams > 0 && lostLevel == dungeonIndex && M.dist(hero.x, hero.z, lostX, lostZ) < 1.6f && Math.abs(hero.y - lostY) < 2.5f) {
            gear.gleams += lostGleams;
            say("You recover " + lostGleams + " gleams.");
            sfx(Audio.GLEAM);
            lostGleams = 0;
        }
        if (hero.st == Hero.State.DEAD) {
            hero.update(dt, this);
            if ((overT += dt) > 1.6f) {
                screen = Screen.OVER;
                overT = 0;
                audio.sfx(Audio.DIE);
            }
            return;
        }
        if (dungeon != null) {
            dungeon.update(dt, this);
            dungeon.push(this, dt);
            story.dungeonEvents();
            if (hero.y < -6 && dungeon.at(dungeon.ti(hero.x), dungeon.tj(hero.z)) != '~') fellInPit();
        }
        for (Player p : players) {
            Player was = as(p);
            law(dt);
            as(was);
        }
        // Each foe goes after whoever is nearest (a guard: whoever's wanted), so every rule it uses about "the hero" means that player.
        for (int i = 0; i < foes.size(); i++) {
            Foe f = foes.get(i);
            Player prey = f.kind == Foe.Kind.GUARD ? hunted(f.x, f.z) : nearest(f.x, f.z);
            if (f.kind == Foe.Kind.GUARD && (f.bits & 2) != 0 && M.dist(f.x, f.z, prey.hero.x, prey.hero.z) > 120) {
                // A reinforcement left far behind just goes home.
                f.state = Foe.DEAD;
                f.deadT = 99;
            }
            if (f.boss || M.dist(f.x, f.z, prey.hero.x, prey.hero.z) < 90) {
                Player was = as(prey);
                f.update(dt, this);
                as(was);
            }
        }
        foes.removeIf(f -> !f.alive() && f.deadT > 1.5f);
        updateProjectiles(dt);
        updateClaw(dt);
        updateDrops(dt);
        interactions();
        story.fieldEvents(dt);
        if (in.pressed(KeyEvent.VK_E) && action != null && (hero.st == Hero.State.MOVE || hero.st == Hero.State.DIVE)) action.run();
        if (in.pressed(KeyEvent.VK_L) && hero.st == Hero.State.MOVE && dungeon == null) jobs.steal();
    }

    private void fellInPit() {
        hero.place(hero.safeX, hero.safeY, hero.safeZ);
        hero.hp -= 2;
        hero.invuln = 1.2f;
        hurtFlash = 1;
        audio.sfx(Audio.HURT);
        if (hero.hp <= 0) {
            hero.hp = 0;
            hero.pose(Hero.State.DEAD, Characters.H_DEAD);
        }
    }

    void cycleItem(int dir) {
        boolean[] owned = {false, gear.bombBag, gear.claw};
        for (int k = 1; k <= 3; k++) {
            int i = Math.floorMod(gear.item + dir * k, 3);
            if (owned[i]) {
                gear.item = i;
                return;
            }
        }
    }

    // ---- combat -------------------------------------------------------------------------------

    Foe nearestFoe(float x, float z, float yaw, float range) { return nearestFoe(x, z, yaw, range, false); }

    /** The best foe to lock on to; anyDirection ignores whether it's in front of the hero. */
    Foe nearestFoe(float x, float z, float yaw, float range, boolean anyDirection) {
        Foe best = null;
        float bestScore = 1e9f;
        for (Foe f : foes) {
            if (!f.alive() || f.state == Foe.HIDE) continue;
            float d = M.dist(x, z, f.x, f.z) - f.radius(), facing = M.cos(M.angleDiff(yaw, M.atan2(f.x - x, f.z - z)));
            if (d > range || !anyDirection && facing < .1f && d > 3) continue;
            float score = d * (2 - facing);
            if (score < bestScore) {
                bestScore = score;
                best = f;
            }
        }
        return best;
    }

    float swordMult() { return (1 + .12f * gear.str) * (1 + .3f * gear.swordLv) * (gear.ascended ? 1.5f : 1) * (gear.outfit == Arms.REGALIA ? 1.2f : 1); }

    float spellMult() { return (1 + .15f * gear.arc) * (gear.ascended ? 1.5f : 1) * (gear.sunRelic ? 1.25f : 1) * (gear.outfit == Arms.STARWEAVE ? 1.3f : 1); }

    /** Which column (weapons, outfits, dyes) and row the gear screen has selected. */
    int gearCol, gearRow;

    /** The gear screen: W/S pick, A/D switch column, E or Enter equips, I (or Tab, Esc) closes. */
    private void gearMenu() {
        if (in.any(KeyEvent.VK_I, KeyEvent.VK_TAB, KeyEvent.VK_ESCAPE)) {
            screen = Screen.PLAY;
            return;
        }
        int[] rows = {Arms.WEAPON.length, Arms.OUTFIT.length, Arms.DYE.length};
        int turn = in.any(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 2 : in.any(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0;
        if (turn > 0) {
            // A new column starts on what's being worn there.
            gearCol = (gearCol + turn) % 3;
            gearRow = gearCol == 0 ? gear.weapon : gearCol == 1 ? gear.outfit : gear.dye;
        }
        if (in.any(KeyEvent.VK_W, KeyEvent.VK_UP)) gearRow = (gearRow + rows[gearCol] - 1) % rows[gearCol];
        if (in.any(KeyEvent.VK_S, KeyEvent.VK_DOWN)) gearRow = (gearRow + 1) % rows[gearCol];
        if (!in.any(KeyEvent.VK_E, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) return;
        if (!owns(gearCol, gearRow)) {
            audio.sfx(Audio.CLANG);
            return;
        }
        switch (gearCol) {
            case 0 -> gear.weapon = gearRow;
            case 1 -> gear.outfit = gearRow;
            default -> gear.dye = gearRow;
        }
        audio.sfx(Audio.PICKUP);
        story.save();
    }

    /** Whether the hero owns weapon (kind 0), outfit (1) or dye (2) number i. */
    boolean owns(int kind, int i) { return ((kind == 0 ? gear.weapons : kind == 1 ? gear.outfits : gear.dyes) & 1 << i) != 0; }

    // ---- the dev pack ------------------------------------------------------------------------------

    /** One thing the dev pack can hand over: its name, whether the hero has it, and how to give it. */
    record DevItem(String name, java.util.function.BooleanSupplier has, Runnable give) {}

    static final String[] DEV_TABS = {"Weapons", "Outfits", "Dyes", "Tools", "Songs", "Spells", "Stones", "Supplies", "Summon"};
    int devTab, devRow;
    private List<List<DevItem>> devPack;

    /** The dev pack ([): A/D pick a tab, W/S an item, E or Enter takes it, F takes the whole tab, [ (or Esc) closes. */
    private void devPack() {
        if (in.any(KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB)) {
            screen = Screen.PLAY;
            return;
        }
        List<List<DevItem>> tabs = devItems();
        int turn = in.any(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? tabs.size() - 1 : in.any(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0;
        if (turn > 0) {
            devTab = (devTab + turn) % tabs.size();
            devRow = 0;
        }
        List<DevItem> tab = tabs.get(devTab);
        if (in.any(KeyEvent.VK_W, KeyEvent.VK_UP)) devRow = (devRow + tab.size() - 1) % tab.size();
        if (in.any(KeyEvent.VK_S, KeyEvent.VK_DOWN)) devRow = (devRow + 1) % tab.size();
        boolean summoning = DEV_TABS[devTab].equals("Summon"), all = !summoning && in.pressed(KeyEvent.VK_F);
        if (!all && !in.any(KeyEvent.VK_E, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) return;
        audio.sfx(Audio.PICKUP);
        say("DEV " + (summoning ? "summoned " : "") + (all ? "took every one of the " + DEV_TABS[devTab].toLowerCase() : tab.get(devRow).name()));
        // A co-op friend's gear lives on the host's island, so the host hands it over.
        if (coop != null && coop.joined()) {
            coop.dev(devTab, devRow, all);
            return;
        }
        devGive(devTab, devRow, all);
        story.save();
    }

    /** Hands the current player item row of dev pack tab t (or the whole tab). */
    void devGive(int t, int row, boolean all) {
        List<DevItem> tab = devItems().get(t);
        for (DevItem it : all ? tab : List.of(tab.get(row))) it.give().run();
    }

    /** Every item in the game, by dev pack tab. */
    List<List<DevItem>> devItems() {
        if (devPack != null) return devPack;
        devPack = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            List<DevItem> tab = new ArrayList<>();
            int kind = k;
            for (int i = 0; i < (k == 0 ? Arms.WEAPON : k == 1 ? Arms.OUTFIT : Arms.DYE).length; i++) {
                int n = i;
                tab.add(new DevItem(Story.itemName(kind, n), () -> owns(kind, n), () -> {
                    if (kind == 0) {
                        gear.sword = true;
                        gear.weapons |= 1 << n;
                    } else if (kind == 1) gear.outfits |= 1 << n;
                    else gear.dyes |= 1 << n;
                }));
            }
            devPack.add(tab);
        }
        devPack.add(List.of(
                new DevItem("Keeper's Sword", () -> gear.sword, () -> gear.sword = true),
                new DevItem("Driftwood Shield", () -> gear.shield, () -> gear.shield = true),
                new DevItem("Windflute", () -> gear.flute, () -> gear.flute = true),
                new DevItem("Hunter's Bow", () -> gear.bow, () -> {
                    gear.bow = true;
                    gear.arrows = Math.max(gear.arrows, 30);
                }),
                new DevItem("Bomb Bag", () -> gear.bombBag, () -> {
                    gear.bombBag = true;
                    gear.bombs = Math.max(gear.bombs, 15);
                }),
                new DevItem("Grapple Claw", () -> gear.claw, () -> gear.claw = true),
                new DevItem("Longclaw", () -> gear.longclaw, () -> gear.claw = gear.longclaw = true),
                new DevItem("Rime-Tempered Blade", () -> gear.frostbrand, () -> gear.frostbrand = true),
                new DevItem("Windcloak", () -> gear.windcloak, () -> {
                    gear.windcloak = true;
                    gear.spells |= 1 << Magic.GUST;
                }),
                new DevItem("Stormcloak", () -> gear.stormcloak, () -> {
                    gear.windcloak = gear.stormcloak = true;
                    gear.spells |= 1 << Magic.GUST;
                }),
                new DevItem("Sun Relic", () -> gear.sunRelic, () -> gear.sunRelic = true)));
        devPack.add(bits(Story.SONG_NAMES, "", () -> gear.songs, b -> {
            gear.songs |= b;
            gear.flute = true;
        }));
        devPack.add(bits(Magic.NAME, "", () -> gear.spells, b -> gear.spells |= b));
        devPack.add(bits(Story.STONES, " Stone", () -> gear.stones, b -> {
            gear.stones |= b;
            if (Integer.bitCount(gear.stones) == Story.STONES.length) {
                gear.ascended = true;
                gear.spells |= 1 << Magic.STARFALL;
            }
        }));
        devPack.add(List.of(
                new DevItem("Full quiver (50 arrows)", () -> gear.arrows >= 50, () -> {
                    gear.bow = true;
                    gear.arrows = 50;
                }),
                new DevItem("Full bomb bag (30 bombs)", () -> gear.bombs >= 30, () -> {
                    gear.bombBag = true;
                    gear.bombs = 30;
                }),
                new DevItem("1000 gleams", () -> false, () -> gear.gleams += 1000),
                new DevItem("Heart container", () -> gear.hearts >= 20, () -> gear.hearts = Math.min(20, gear.hearts + 1)),
                new DevItem("Draught flask (refills them all)", () -> gear.maxFlasks >= 7, () -> gear.flasks = gear.maxFlasks = Math.min(7, gear.maxFlasks + 1)),
                new DevItem("Everything at once", () -> false, () -> {
                    for (int t = 0; t < List.of(DEV_TABS).indexOf("Supplies"); t++) for (DevItem it : devPack.get(t)) it.give().run();
                    gear.bow = gear.bombBag = true;
                    gear.arrows = 50;
                    gear.bombs = 30;
                })));
        List<DevItem> summon = new ArrayList<>();
        for (Foe.Kind k : Foe.Kind.values()) summon.add(new DevItem(foeName(k), () -> false, () -> summon(k)));
        devPack.add(summon);
        return devPack;
    }

    static String foeName(Foe.Kind k) {
        return switch (k) {
            case BRAMBLE -> "Bramblemaw";
            case GOLEM -> "Cinder Golem";
            case SERPENT -> "Tide Serpent";
            case KING -> "Hollow King";
            case HOARWING -> "Hoarwing";
            case ASHMAW -> "Ashmaw";
            case STORMDRAKE -> "Stormdrake";
            default -> k.name().charAt(0) + k.name().substring(1).toLowerCase();
        };
    }

    /** Dev pack: puts a creature in front of the current player. Bosses come in fighting. */
    void summon(Foe.Kind kind) {
        float d = kind.ordinal() >= Foe.Kind.BRAMBLE.ordinal() ? 12 : 5;
        float x = hero.x + M.sin(hero.yaw) * d, z = hero.z + M.cos(hero.yaw) * d;
        Foe f = new Foe(kind, x, level.floor(x, z), z);
        f.yaw = M.atan2(hero.x - x, hero.z - z);
        if (kind == Foe.Kind.KING) f.y += 4;
        if (kind == Foe.Kind.HOARWING) f.y += 7;
        if (kind == Foe.Kind.STORMDRAKE) {
            // Only one drake rides the island: the summoned one takes over from any still circling.
            if (drake != null) foes.remove(drake);
            drake = f;
            f.y = hero.y + 13;
            f.tx = M.sin(f.yaw);
            f.tz = M.cos(f.yaw);
            f.setState(Foe.ATTACK);
        } else if (f.boss) f.setState(Foe.CHASE);
        if (f.boss) boss = f;
        foes.add(f);
    }

    /** A tab of numbered things kept as bits (songs, spells, stones). */
    private static List<DevItem> bits(String[] names, String suffix, java.util.function.IntSupplier mask, java.util.function.IntConsumer give) {
        List<DevItem> tab = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            int bit = 1 << i;
            tab.add(new DevItem(names[i] + suffix, () -> (mask.getAsInt() & bit) != 0, () -> give.accept(bit)));
        }
        return tab;
    }

    /** A charged blow's burst: every foe within r of (x, z) is hit, and lesser ones reel if stagger is set. */
    void blast(float x, float z, float r, float dmg, boolean stagger) {
        for (Foe f : new ArrayList<>(foes)) {
            if (!f.alive() || f.state == Foe.HIDE || M.dist(x, z, f.x, f.z) > r + f.radius() * .5f) continue;
            if (f.damage(this, Foe.SPIN, dmg * swordMult(), f.x, f.centerY(), f.z) && stagger && f.alive()) f.stagger();
        }
    }

    /** The hammer's weight: cracked walls and ice give way in dungeons, and rubble crumbles out on the island. */
    void smash(float x, float z) {
        if (dungeon == null) {
            story.bombedField(x, z);
            return;
        }
        for (Dungeon.Thing t : dungeon.things)
            if (t.kind == 'X' && !t.on && M.dist(x, z, t.x, t.z) < 2.6f) {
                t.on = true;
                sparks(t.x, 1.5f, t.z, 0x8a8478, 24);
                shake(.4f);
                audio.sfx(Audio.SECRET);
            }
        dungeon.melt(x, z, 2.4f, this);
    }

    /** Whether warm air rises at (x, y, z): over campfires and desert vents outside, over vents in dungeons. */
    float updraft(float x, float y, float z) {
        if (dungeon != null) return dungeon.updraft(x, y, z);
        for (float[] f : world.places.fires) if (M.dist(x, z, f[0], f[2]) < 1.9f && y > f[1] - 1 && y < f[1] + 22) return 1;
        for (float[] v : world.places.vents) if (M.dist(x, z, v[0], v[2]) < 2.6f && y > v[1] - 1 && y < v[1] + 62) return 1;
        return 0;
    }

    /** Applies levels and heart containers to the hero's maximums. */
    void refreshStats() {
        hero.maxHp = gear.hearts * 4 + gear.vig * 2;
        hero.maxStamina = 80 + gear.end * 8;
        hero.maxFp = 40 + gear.mnd * 10;
        hero.hp = Math.min(hero.hp, hero.maxHp);
        hero.fp = Math.min(hero.fp, hero.maxFp);
    }

    void drinkDraught() {
        hero.hp = Math.min(hero.maxHp, hero.hp + 12 + gear.vig);
        sparks(hero.x, hero.y + 1.2f, hero.z, 0xff9a5a, 18);
        sfx(Audio.HEART);
    }

    void cycleSpell() {
        for (int k = 1; k <= Magic.NAME.length; k++) {
            int i = (gear.spell + k) % Magic.NAME.length;
            if ((gear.spells & 1 << i) != 0) {
                gear.spell = i;
                return;
            }
        }
    }

    /** Fires a player projectile from the hero's hand, aimed at the locked target or the nearest foe ahead. */
    void shootFriendly(int kind, Hero h, float speed, float dmg) {
        float[] p = new float[3];
        boolean aimed = h.aimPoint(p);
        if (!aimed) {
            Foe aim = nearestFoe(h.x, h.z, h.yaw, 25);
            if (aimed = aim != null) {
                p[0] = aim.x;
                p[1] = aim.centerY();
                p[2] = aim.z;
            }
        }
        float sx = h.x + M.sin(h.yaw) * .6f, sy = h.y + 1.3f, sz = h.z + M.cos(h.yaw) * .6f;
        float dx = M.sin(h.yaw), dy = 0, dz = M.cos(h.yaw);
        if (aimed) {
            dx = p[0] - sx;
            dy = p[1] - sy;
            dz = p[2] - sz;
            float l = M.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l;
            dy /= l;
            dz /= l;
        }
        Shot s = new Shot();
        s.kind = kind;
        s.owner = h;
        s.x = sx;
        s.y = sy;
        s.z = sz;
        s.vx = dx * speed;
        s.vy = dy * speed;
        s.vz = dz * speed;
        s.life = kind == P_RADIANT ? .7f : 2.5f;
        s.dmg = dmg;
        if (kind != P_RADIANT && h.aimPoint(p)) {
            s.homing = h.target;
            s.homePoint = h.targetPoint;
        }
        shots.add(s);
    }

    private final float[] homeTmp = new float[3];

    /**
     * Steers a homing shot toward its weak point. Returns 1 if it reached the point (and dealt the blow), 0 if still
     * flying, -1 if it isn't homing.
     */
    private int home(Shot s, float dt) {
        Foe f = s.homing;
        if (f == null || !f.alive() || !f.point(s.homePoint, homeTmp)) {
            s.homing = null;
            return -1;
        }
        float dx = homeTmp[0] - s.x, dy = homeTmp[1] - s.y, dz = homeTmp[2] - s.z, d = M.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.3f) {
            f.damage(this, Foe.ARROW, s.dmg, homeTmp[0], homeTmp[1], homeTmp[2]);
            s.hit.add(f);
            sparks(homeTmp[0], homeTmp[1], homeTmp[2], s.kind == P_EMBER ? 0xff8a3a : s.kind == P_FROST ? 0xbff0ff : 0xf0e0c0, 10);
            s.homing = null;
            return 1;
        }
        float sp = M.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz), k = Math.min(1, 10 * dt);
        s.vx = M.lerp(s.vx / sp, dx / d, k) * sp;
        s.vy = M.lerp(s.vy / sp, dy / d, k) * sp;
        s.vz = M.lerp(s.vz / sp, dz / d, k) * sp;
        return 0;
    }

    /** Area blows (thunder, falling stars) also break pots and wake crystals. */
    void areaHit(float x, float z, float r) {
        if (dungeon == null) return;
        for (Dungeon.Thing t : dungeon.things) {
            if (t.gone || t.hidden || M.dist(x, z, t.x, t.z) > r) continue;
            if (t.kind == 'u') breakPot(t);
            if (t.kind == 'o') t.on = !t.on;
        }
    }

    /** Shows a one-time tutorial tip. */
    void tip(String id, String text) {
        if (story.flags.add("tip:" + id)) {
            tip = text;
            tipT = 8;
        }
    }

    void swordStrike(Hero h, float arc, float range, float dmg) {
        boolean spin = arc >= 360;
        if (gear.weapon == Arms.HAMMER && !spin) smash(h.x + M.sin(h.yaw) * 1.6f, h.z + M.cos(h.yaw) * 1.6f);
        dmg *= swordMult();
        if (gear.ascended && (spin || dmg >= 1.5f * swordMult())) shootFriendly(P_RADIANT, h, 24, 1.5f * swordMult());
        int samples = spin ? 8 : 3;
        boolean hit = false;
        for (int k = 0; k < samples; k++) {
            float a = h.yaw + (spin ? k * M.TAU / samples : (k - 1) * M.PI * arc / 360 / 2);
            for (float reach = .7f; reach <= range; reach += .8f) {
                float px = h.x + M.sin(a) * reach, pz = h.z + M.cos(a) * reach, py = h.y + 1.1f;
                // A copy: killing a slime splits it, adding foes mid-swing.
                for (Foe f : new ArrayList<>(foes)) {
                    if (f.flash > .7f || f.hitTest(px, py, pz) == 0 && f.hitTest(px, h.y + .4f, pz) == 0) continue;
                    if (f.damage(this, spin ? Foe.SPIN : Foe.SWORD, dmg, px, f.hitTest(px, py, pz) > 0 ? py : h.y + .4f, pz)) hit = true;
                }
                if (hitPeople(px, py, pz, .3f, dmg)) hit = true;
                if (dungeon != null) for (Dungeon.Thing t : dungeon.things) {
                    if (t.gone || t.hidden || M.dist(px, pz, t.x, t.z) > .9f) continue;
                    if (t.kind == 'o' && t.anim <= 0) {
                        t.on = !t.on;
                        t.anim = .4f;
                        audio.sfx(Audio.CLICK);
                    }
                    if (t.kind == 'u') breakPot(t);
                }
            }
        }
        // Duelling friends' blades find each other too.
        Player by = current;
        for (Player p : players) {
            Hero o = p.hero;
            if (!rivals(h, o) || o.invuln > 0) continue;
            float dx = o.x - h.x, dz = o.z - h.z, d = M.hypot(dx, dz);
            if (d > range + .5f || Math.abs(o.y - h.y) > 1.8f || !spin && Math.abs(M.angleDiff(h.yaw, M.atan2(dx, dz))) > M.PI * arc / 360 + .35f) continue;
            duelHit(p, by, Math.max(1, Math.round(dmg * 1.5f)), h.x, h.z, true);
            hit = true;
        }
        if (hit) shake(.12f);
    }

    // ---- the law ------------------------------------------------------------------------------

    static final int ASSAULT = 0, MURDER = 1, GUARD_DOWN = 2;

    /** The current player broke the king's peace: their wanted level rises, the guard hears of it and folk nearby run. */
    void crime(int what) {
        Hero h = hero;
        int was = h.wanted;
        h.wanted = Math.min(5, switch (what) {
            case MURDER -> Math.max(2, h.wanted + 2);
            case GUARD_DOWN -> Math.max(2, h.wanted + 1);
            default -> Math.max(1, h.wanted);
        });
        h.seenT = 1;
        h.heat = escape(h);
        for (Places.Npc n : world.places.everyone) if (M.dist(n.x, n.z, h.x, h.z) < 40) n.fleeT = 12;
        if (h.wanted > was) {
            audio.sfx(Audio.SLAM);
            say("WANTED " + "★".repeat(h.wanted) + "  The royal guard is coming. Get far away and out of sight to lose them.");
        }
    }

    /** Seconds a wanted player must stay out of every guard's sight before the hunt is called off. */
    static float escape(Hero h) { return 8 + 3 * h.wanted; }

    /**
     * Once a frame for the current player: while a guard can see them the hunt stays hot; out of sight it cools until the guard give up.
     * While they're seen, reinforcements keep arriving, up to two guards for every star (knights from four stars up).
     */
    private void law(float dt) {
        Hero h = hero;
        if (h.wanted == 0) return;
        if (h.st == Hero.State.DEAD) {
            h.wanted = 0;
            return;
        }
        if ((h.seenT -= dt) > 0) h.heat = escape(h);
        else if ((h.heat -= dt) <= 0) {
            h.wanted = 0;
            audio.sfx(Audio.SECRET);
            say(current.name + " lost the royal guard, and is no longer wanted.");
            return;
        }
        // Reinforcements only come while a guard has eyes on them: break the line of sight and nobody new joins the hunt.
        if (dungeon != null || h.seenT <= 0 || (h.lawT -= dt) > 0) return;
        h.lawT = 3;
        int hunting = 0;
        for (Foe f : foes) if (f.kind == Foe.Kind.GUARD && f.alive() && f.state != Foe.IDLE && M.dist(f.x, f.z, h.x, h.z) < 90) hunting++;
        if (hunting >= h.wanted * 2) return;
        for (int k = 0; k < 12; k++) {
            float a = rng.nextFloat() * M.TAU, d = 40 + rng.nextFloat() * 18, x = h.x + M.sin(a) * d, z = h.z + M.cos(a) * d, y = world.height(x, z);
            if (y < World.WATER + .8f || world.slope(x, z) > .7f || field.floor(x, z) > y + .5f) continue;
            Foe f = new Foe(Foe.Kind.GUARD, x, y, z);
            f.bits = 2 | (h.wanted >= 4 ? 1 : 0);
            if (h.wanted >= 4) f.hp = f.maxHp = f.maxHp * 2;
            f.setState(Foe.CHASE);
            f.yaw = M.atan2(h.x - x, h.z - z);
            foes.add(f);
            return;
        }
    }

    /** The nearest wanted player still standing, for a guard; whoever's nearest if nobody's wanted. */
    private Player hunted(float x, float z) {
        Player best = null;
        float bd = Float.MAX_VALUE;
        for (Player p : players) {
            float d = M.dist(x, z, p.hero.x, p.hero.z);
            if (p.hero.wanted > 0 && p.hero.st != Hero.State.DEAD && d < bd) {
                bd = d;
                best = p;
            }
        }
        return best != null ? best : nearest(x, z);
    }

    /**
     * The current player strikes at (px, py, pz) (py NaN: at any height): anyone on the island within reach is hurt, and that's a crime.
     * Townsfolk can be killed; the island's named folk are only knocked out, and get up again in a minute. True if someone was hit.
     */
    boolean hitPeople(float px, float py, float pz, float reach, float dmg) {
        if (dungeon != null) return false;
        boolean hit = false;
        for (Places.Npc n : world.places.everyone) {
            if (n.downT > 0 || n.flash > .5f) continue;
            float dx = px - n.x, dy = Float.isNaN(py) ? 0 : py - n.y - 1, dz = pz - n.z;
            if (dx * dx + dy * dy + dz * dz > (reach + .5f) * (reach + .5f)) continue;
            n.hp -= dmg;
            n.flash = 1;
            hit = true;
            sparks(n.x, n.y + 1.1f, n.z, 0xd04040, 8);
            audio.sfx(Audio.HIT);
            if (n.hp > 0) {
                n.fleeT = 14;
                crime(ASSAULT);
                continue;
            }
            n.hp = 0;
            n.downT = n.folk ? 150 : 60;
            crime(MURDER);
            say(Story.name(n) + (n.folk ? " is dead." : " is knocked out cold."));
        }
        return hit;
    }

    // ---- duels --------------------------------------------------------------------------------

    /** Two players who have both opted in to duels (P) can fight each other. */
    boolean rivals(Hero a, Hero b) { return a != b && a.dueling && b.dueling; }

    /** P: opt in to (or out of) duels; two duellists can lock on to each other and fight. */
    private void toggleDuel() {
        hero.dueling = !hero.dueling;
        audio.sfx(Audio.CLICK);
        say(hero.dueling ? current.name + " is ready to duel: anyone else who presses P can fight them." : current.name + " put away their duel.");
    }

    Player player(int id) {
        for (Player p : players) if (p.id == id) return p;
        return null;
    }

    private Player of(Hero h) {
        for (Player p : players) if (p.hero == h) return p;
        return me;
    }

    /** The nearest duelling friend within range: in front, or anywhere when anyDirection. */
    Hero nearestRival(Hero h, float range, boolean anyDirection) {
        Hero best = null;
        float bd = range;
        for (Player p : players) {
            Hero o = p.hero;
            if (!rivals(h, o) || o.st == Hero.State.DEAD) continue;
            float d = M.dist(h.x, h.z, o.x, o.z);
            if (d < bd && (anyDirection || Math.abs(M.angleDiff(h.yaw, M.atan2(o.x - h.x, o.z - h.z))) < 1.4f)) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** A duelling friend's blow lands on player p (who may block or parry it). At their last heart the duel is over: friends never kill. */
    private void duelHit(Player p, Player by, int dmg, float fromX, float fromZ, boolean blockable) {
        Player was = as(p);
        if (hero.hit(this, dmg, fromX, fromZ, blockable)) {
            audio.sfx(Audio.HURT);
            if (p == me) {
                hurtFlash = 1;
                shake(.3f);
            }
            if (hero.hp <= 1) {
                hero.hp = 1;
                if (hero.st == Hero.State.DEAD) {
                    hero.setState(Hero.State.HURT);
                    hero.anim.restart(Characters.H_HURT, .04f);
                }
                hero.dueling = by.hero.dueling = false;
                hero.invuln = 2;
                say(by.name + " wins the duel against " + p.name + "!");
            }
        }
        as(was);
    }

    /** A duellist's blast (Thunder Ring, a falling star) catches duelling friends within r of (x, z). */
    void duelArea(Hero by, float x, float z, float r, int dmg) {
        Player from = of(by);
        for (Player p : new ArrayList<>(players))
            if (rivals(by, p.hero) && p.hero.invuln <= 0 && M.dist(p.hero.x, p.hero.z, x, z) < r) duelHit(p, from, dmg, x, z, false);
        // Spells are no kinder to bystanders.
        if (from != null) {
            Player was = as(from);
            hitPeople(x, Float.NaN, z, r, dmg);
            as(was);
        }
    }

    /** A duellist's arrow or spell striking a duelling friend; true if it hit. */
    private boolean duelShot(Shot s) {
        if (s.owner == null || !s.owner.dueling) return false;
        for (Player p : players) {
            Hero o = p.hero;
            float dx = s.x - o.x, dy = s.y - o.y - 1, dz = s.z - o.z;
            if (!rivals(s.owner, o) || o.invuln > 0 || dx * dx + dy * dy + dz * dz > 1) continue;
            duelHit(p, of(s.owner), s.kind == ARROW ? 2 : 3, s.x - s.vx, s.z - s.vz, true);
            sparks(s.x, s.y, s.z, 0xfff0c0, 6);
            return true;
        }
        return false;
    }

    void breakPot(Dungeon.Thing t) {
        t.gone = true;
        sparks(t.x, t.y + .4f, t.z, 0xb06a44, 10);
        audio.sfx(Audio.CLICK);
        loot(t.x, t.y, t.z, .7f);
    }

    /** F uses the selected tool (bombs or the claw); the bow lives on Q and the attack button. */
    boolean useItem(Hero h) {
        if (gear.item == 0) cycleItem(1);
        switch (gear.item) {
            case 0 -> {
                return false;
            }
            case 1 -> {
                if (!gear.bombBag) return false;
                if (gear.bombs <= 0) {
                    say("Out of bombs! Walk into a glowing orange Blast Pod to pick up more.");
                    return false;
                }
                gear.bombs--;
                h.holdT = 0;
                h.setState(Hero.State.HOLD);
                audio.sfx(Audio.FUSE);
            }
            default -> {
                if (!gear.claw || clawOut) return false;
                h.aimClaw = true;
                h.setState(Hero.State.AIM);
            }
        }
        return true;
    }

    private float[] aimDir(Hero h) {
        float[] p = new float[3];
        if (h.aimPoint(p)) {
            float dx = p[0] - h.x, dy = p[1] - (h.y + 1.45f), dz = p[2] - h.z, l = M.sqrt(dx * dx + dy * dy + dz * dz);
            return new float[]{dx / l, dy / l + .006f * l, dz / l};
        }
        float cp = M.cos(camPitch);
        float ax = camX + M.sin(camYaw) * cp * 60, ay = camY + M.sin(camPitch) * 60, az = camZ + M.cos(camYaw) * cp * 60;
        float dx = ax - h.x, dy = ay - (h.y + 1.45f), dz = az - h.z, l = M.sqrt(dx * dx + dy * dy + dz * dz);
        return new float[]{dx / l, dy / l, dz / l};
    }

    /** Looses an arrow; power (0-1, how far the string was drawn) sets its speed and sting. */
    void fireArrow(Hero h, float power) {
        if (gear.arrows <= 0) return;
        gear.arrows--;
        float[] d = aimDir(h);
        Shot s = new Shot();
        s.kind = ARROW;
        s.x = h.x;
        s.y = h.y + 1.45f;
        s.z = h.z;
        float speed = 26 + 20 * power;
        s.vx = d[0] * speed;
        s.vy = d[1] * speed + 1.5f;
        s.vz = d[2] * speed;
        s.life = 3;
        s.dmg = (1 + .06f * gear.str) * (.5f + .5f * power);
        if (h.aimPoint(new float[3])) {
            s.homing = h.target;
            s.homePoint = h.targetPoint;
        }
        shots.add(s);
        audio.sfx(Audio.BOW);
    }

    /** How far the claw reaches; the Longclaw from Rimeholt nearly doubles it. */
    float clawRange() { return gear.longclaw ? 30 : 16; }

    /**
     * What the claw would bite, as {x, y, z, kind}: kind 0 is a grapple ring, 1 a foe to yank, 2 something to fetch.
     * The best-aligned one in range with nothing solid in the way wins; a locked-on foe always does.
     */
    float[] clawTarget(Hero h) {
        float range = clawRange() + 2, fx = M.sin(camYaw), fz = M.cos(camYaw), bestScore = 1e9f;
        Foe lock = h.locked ? h.target : null;
        if (lock != null && lock.clawable() && M.dist(lock.x, lock.z, h.x, h.z) < range) return new float[]{lock.x, lock.centerY(), lock.z, 1};
        float[] best = null;
        for (float[] c : clawCandidates(h, range)) {
            float dx = c[0] - h.x, dz = c[2] - h.z, d = M.hypot(dx, dz), align = d < 1e-3f ? 0 : (dx * fx + dz * fz) / d;
            if (d > range || d < 1.5f || align < .7f) continue;
            float score = d * (2 - align) * c[4];
            if (score < bestScore && clearLine(h.x, h.y + 1.4f, h.z, c[0], c[1], c[2])) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** Everything the claw could bite: {x, y, z, kind, weight}, where a lower weight is preferred. */
    private List<float[]> clawCandidates(Hero h, float range) {
        List<float[]> out = new ArrayList<>();
        if (dungeon != null) {
            for (Dungeon.Thing t : dungeon.things) if (t.kind == 'g' && !t.gone && !t.hidden) out.add(new float[]{t.x, t.y + 2.3f, t.z, 0, 1});
        } else {
            for (float[] p : world.places.posts) out.add(new float[]{p[0], p[1] + 2.3f, p[2], 0, 1});
            float size = World.CHUNK * World.CELL;
            int ci = (int) (h.x / size), cj = (int) (h.z / size);
            for (int j = cj - 1; j <= cj + 1; j++)
                for (int i = ci - 1; i <= ci + 1; i++)
                    if (i >= 0 && j >= 0 && i < World.CHUNKS && j < World.CHUNKS)
                        for (Places.Pickup p : world.places.pickups[i + j * World.CHUNKS])
                            if (!p.taken && M.dist(p.x, p.z, h.x, h.z) < range) out.add(new float[]{p.x, p.y + .4f, p.z, 2, 1.3f});
        }
        for (Drop d : drops) if (d.item != CONTAINER) out.add(new float[]{d.x, d.y + .3f, d.z, 2, 1.3f});
        // A perched or exposed boss beats the post it sits on; posts beat lesser foes.
        for (Foe f : foes) if (f.clawable()) out.add(new float[]{f.x, f.centerY(), f.z, 1, f.boss ? .4f : 1.4f});
        return out;
    }

    private boolean clearLine(float x0, float y0, float z0, float x1, float y1, float z1) {
        int n = 4 + (int) (M.dist(x0, z0, x1, z1) / 1.2f);
        for (int k = 1; k < n; k++) {
            float f = k / (float) n;
            if (level.floor(M.lerp(x0, x1, f), M.lerp(z0, z1, f)) >= M.lerp(y0, y1, f)) return false;
        }
        return true;
    }

    void fireClaw(Hero h) {
        // There's one claw chain in the world; in co-op it's the host's.
        if (current != me || coop != null && coop.joined()) return;
        float[] d = aimDir(h), at = clawTarget(h);
        if (at != null) {
            float dx = at[0] - h.x, dy = at[1] - (h.y + 1.4f), dz = at[2] - h.z, l = M.sqrt(dx * dx + dy * dy + dz * dz);
            d = new float[]{dx / l, dy / l, dz / l};
        }
        clawOut = true;
        clawBack = clawPull = false;
        clawX = h.x;
        clawY = h.y + 1.4f;
        clawZ = h.z;
        clawDX = d[0];
        clawDY = d[1];
        clawDZ = d[2];
        clawDist = 0;
        clawItem = null;
        audio.sfx(Audio.CLAW);
    }

    void throwBomb(Hero h, float fuse) {
        Bomb b = new Bomb();
        b.x = h.x;
        b.y = h.y + 1.8f;
        b.z = h.z;
        b.vx = M.sin(h.yaw) * 7;
        b.vz = M.cos(h.yaw) * 7;
        b.vy = 4.5f;
        b.fuse = Math.max(.3f, fuse);
        bombs.add(b);
    }

    void dropBomb(Hero h) {
        Bomb b = new Bomb();
        b.x = h.x;
        b.y = h.y + 1.5f;
        b.z = h.z;
        b.fuse = Math.max(.3f, 3 - h.holdT);
        bombs.add(b);
    }

    void explode(float x, float y, float z, boolean inHand) {
        if (dungeon != null) dungeon.melt(x, z, 3.6f, this);
        sparks(x, y, z, 0xffb050, 30);
        sparks(x, y, z, 0x5a5452, 16);
        audio.sfx(Audio.BOOM);
        shake(.6f);
        if (M.dist(x, z, hero.x, hero.z) < 2.6f && Math.abs(y - hero.y - 1) < 2.5f) hurtHero(2, x + .1f, z, false);
        for (Foe f : new ArrayList<>(foes))
            if (M.dist(x, z, f.x, f.z) < 3.4f + f.radius() * .5f) f.damage(this, Foe.BOMB, 2, x, f.centerY(), z);
        hitPeople(x, Float.NaN, z, 3.2f, 3);
        if (dungeon != null) for (Dungeon.Thing t : dungeon.things) {
            if (t.gone || M.dist(x, z, t.x, t.z) > 3.4f) continue;
            if (t.kind == 'X' && !t.on) {
                t.on = true;
                audio.sfx(Audio.SECRET);
            }
            if (t.kind == 'u') breakPot(t);
            if (t.kind == 'o') t.on = !t.on;
        }
        else story.bombedField(x, z);
    }

    void hurtHero(int dmg, float fromX, float fromZ) { hurtHero(dmg, fromX, fromZ, false); }

    void hurtHero(int dmg, float fromX, float fromZ, boolean blockable) {
        if (screen != Screen.PLAY && screen != Screen.FLUTE) return;
        // Only this computer's own player rides Juniper and feels the screen flash when hit.
        boolean mine = current == me;
        if (mine && horse.ridden && hero.invuln <= 0) horse.dismount(this);
        if (hero.hit(this, dmg, fromX, fromZ, blockable)) {
            if (mine) {
                hurtFlash = 1;
                shake(.35f);
            }
            audio.sfx(Audio.HURT);
            if (screen == Screen.FLUTE) screen = Screen.PLAY;
            overT = 0;
        }
    }

    void shoot(int kind, float x, float y, float z, float tx, float ty, float tz, float speed) {
        Shot s = new Shot();
        s.kind = kind;
        s.x = x;
        s.y = y;
        s.z = z;
        s.life = 6;
        if (kind == ROCK) {
            s.vy = -4;
        } else {
            float dx = tx - x, dy = ty - y, dz = tz - z, l = Math.max(.1f, M.sqrt(dx * dx + dy * dy + dz * dz));
            s.vx = dx / l * speed;
            s.vy = dy / l * speed;
            s.vz = dz / l * speed;
        }
        shots.add(s);
    }

    /** A perfect guard: a burst of sparks, and the attacker nearest the blow reels open for a riposte. */
    void parried(float fromX, float fromZ) {
        Foe best = null;
        float bd = 3.5f;
        for (Foe f : foes) {
            float d = M.dist(f.x, f.z, fromX, fromZ);
            if (f.alive() && d < bd) {
                bd = d;
                best = f;
            }
        }
        if (best != null) best.stagger();
        // A duelling friend's swing turned aside leaves them reeling too.
        for (Player p : players)
            if (rivals(hero, p.hero) && M.dist(p.hero.x, p.hero.z, fromX, fromZ) < 2.5f) p.hero.reel(hero.x, hero.z);
        audio.sfx(Audio.PARRY);
        sparks(hero.x + M.sin(hero.yaw) * .6f, hero.y + 1.1f, hero.z + M.cos(hero.yaw) * .6f, 0xffe9a0, 24);
        shake(.25f);
        say("Parry!");
    }

    void shockwave(float x, float z) { waves.add(new float[]{x, z, 1, 0}); }

    void foeDied(Foe f) {
        if (f.kind == Foe.Kind.WOLF) story.wolfDown();
        if (f.kind == Foe.Kind.GUARD) crime(GUARD_DOWN);
        jobs.foeDied(f);
        sparks(f.x, f.centerY(), f.z, 0x6a4a6a, 14);
        audio.sfx(Audio.STUN);
        if (f.kind == Foe.Kind.SLIME && f.size > .8f)
            for (int s = -1; s <= 1; s += 2) {
                Foe c = new Foe(Foe.Kind.SLIME, f.x + s * .8f, f.y, f.z);
                c.size = .55f;
                c.hp = c.maxHp = 2;
                c.state = Foe.CHASE;
                c.si = f.si;
                c.sj = f.sj;
                foes.add(c);
                if (dungeon != null) dungeon.foes.add(c);
            }
        gear.gleams += f.reward();
        gain = (gainT > 0 ? gain : 0) + f.reward();
        gainT = 2;
        if (f.boss) story.bossDefeated(f);
        else loot(f.x, f.y, f.z, .65f);
    }

    void loot(float x, float y, float z, float chance) {
        if (rng.nextFloat() > chance) return;
        float q = rng.nextFloat();
        int item = gear.bombBag && gear.bombs < 3 && q < .45f ? Places.BOMBS : hero.hp < hero.maxHp && q < .45f ? Places.HEART : gear.bow && gear.arrows < 15 && q < .6f ? Places.ARROWS
                : gear.bombBag && gear.bombs < 6 && q < .7f ? Places.BOMBS : q < .93f ? Places.G1 : q < .99f ? Places.G5 : Places.G20;
        Drop d = new Drop();
        d.item = item;
        d.x = x;
        d.y = y + .5f;
        d.z = z;
        d.vy = 4;
        drops.add(d);
    }

    // ---- projectiles, bombs, claw, pickups ----------------------------------------------------

    private void updateProjectiles(float dt) {
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot s = shots.get(i);
            if (s.kind == ARROW || s.kind == SEED) s.vy -= 9 * dt;
            if (s.kind >= P_EMBER) {
                if (duelShot(s) || stepSpell(s, dt)) shots.remove(i);
                continue;
            }
            if (s.kind == ARROW && s.homing != null) {
                s.vy += 9 * dt;
                if (home(s, dt) == 1) {
                    shots.remove(i);
                    continue;
                }
            }
            if (s.kind == ROCK) s.vy -= 14 * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.z += s.vz * dt;
            boolean dead = (s.life -= dt) <= 0 || s.y < level.floor(s.x, s.z) || !Float.isNaN(level.water(s.x, s.z)) && s.y < 0;
            if (s.kind == ARROW && duelShot(s)) dead = true;
            if (s.kind == ARROW) {
                for (Foe f : foes)
                    if (f != s.homing && f.hitTest(s.x, s.y, s.z) > 0) {
                        f.damage(this, Foe.ARROW, s.dmg, s.x, s.y, s.z);
                        dead = true;
                        break;
                    }
                if (!dead) {
                    // Whoever loosed it answers for whoever it hits.
                    Player shooter = s.owner == null ? null : of(s.owner), was = shooter == null ? null : as(shooter);
                    if (hitPeople(s.x, s.y, s.z, .15f, s.dmg)) dead = true;
                    if (shooter != null) as(was);
                }
                if (dungeon != null) for (Dungeon.Thing t : dungeon.things) {
                    if (t.gone || t.hidden) continue;
                    if (t.kind == 'o' && M.dist(s.x, s.z, t.x, t.z) < .8f && Math.abs(s.y - t.y - 1.5f) < .9f) {
                        t.on = !t.on;
                        audio.sfx(Audio.CLICK);
                        dead = true;
                    }
                    if (t.kind == 'u' && M.dist(s.x, s.z, t.x, t.z) < .5f && s.y < t.y + .8f) {
                        breakPot(t);
                        dead = true;
                    }
                }
                else if (story.arrowAt(s.x, s.y, s.z)) dead = true;
                if (dead) sparks(s.x, s.y, s.z, 0xf0e0c0, 4);
            } else {
                float r = s.kind == ROCK ? 1.6f : 1;
                for (Player p : players) {
                    float dx = s.x - p.hero.x, dy = s.y - p.hero.y - 1, dz = s.z - p.hero.z;
                    if (dead || dx * dx + dy * dy + dz * dz >= r * r) continue;
                    Player was = as(p);
                    hurtHero(s.kind == ROCK ? 4 : 2, s.x - s.vx, s.z - s.vz, s.kind != ROCK);
                    as(was);
                    dead = true;
                }
                if (dead) {
                    sparks(s.x, s.y, s.z, s.kind == FIREBALL ? 0xff7a20 : s.kind == WATERBALL ? 0x7ad8ff : s.kind == LANTERN ? 0x9fe8ff : 0x8a857c, 10);
                    if (s.kind == ROCK) shake(.3f);
                }
            }
            if (dead) shots.remove(i);
        }
        for (int i = bombs.size() - 1; i >= 0; i--) {
            Bomb b = bombs.get(i);
            b.vy -= 22 * dt;
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.z += b.vz * dt;
            float f = level.floor(b.x, b.z);
            if (f > b.y + 1) {
                b.x -= b.vx * dt;
                b.z -= b.vz * dt;
                b.vx *= -.3f;
                b.vz *= -.3f;
            } else if (b.y < f + .2f) {
                b.y = f + .2f;
                b.vy = Math.abs(b.vy) * .3f;
                b.vx *= .6f;
                b.vz *= .6f;
            }
            if ((int) (b.fuse * 8) != (int) ((b.fuse - dt) * 8)) audio.sfx(Audio.FUSE);
            if ((b.fuse -= dt) <= 0) {
                bombs.remove(i);
                explode(b.x, b.y, b.z, false);
            }
        }
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.vy -= 9 * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.z += s.vz * dt;
            if ((s.life -= dt) <= 0) sparks.remove(i);
        }
        for (int i = waves.size() - 1; i >= 0; i--) {
            float[] w = waves.get(i);
            w[2] += 13 * dt;
            for (Player p : players) {
                float d = M.dist(w[0], w[1], p.hero.x, p.hero.z);
                if (w[3] == 0 && Math.abs(d - w[2]) < 1 && p.hero.onGround()) {
                    w[3] = 1;
                    Player was = as(p);
                    hurtHero(3, w[0], w[1], false);
                    as(was);
                }
            }
            if (w[2] > 22) waves.remove(i);
        }
    }

    /** Moves a player spell; true when it's spent. */
    private boolean stepSpell(Shot s, float dt) {
        if (home(s, dt) == 1 && s.kind == P_EMBER) return true;
        s.x += s.vx * dt;
        s.y += s.vy * dt;
        s.z += s.vz * dt;
        // Fire melts Rimeholt's ice walls (checked before the wall stops the bolt).
        if (s.kind == P_EMBER && dungeon != null && dungeon.melt(s.x, s.z, 1.6f, this)) return true;
        if ((s.life -= dt) <= 0 || s.y < level.floor(s.x, s.z) - .2f) {
            if (s.kind != P_RADIANT) sparks(s.x, s.y, s.z, s.kind == P_EMBER ? 0xff8a3a : 0xbff0ff, 8);
            return true;
        }
        for (Foe f : new ArrayList<>(foes)) {
            if (f == s.homing || s.hit.contains(f) || f.hitTest(s.x, s.y, s.z) == 0 && (s.kind != P_RADIANT || f.hitTest(s.x, s.y - .8f, s.z) == 0)) continue;
            s.hit.add(f);
            f.damage(this, Foe.ARROW, s.dmg, s.x, s.y, s.z);
            if (s.kind == P_FROST && !f.boss && f.alive()) f.frozenT = 2.5f;
            sparks(s.x, s.y, s.z, s.kind == P_EMBER ? 0xff8a3a : s.kind == P_FROST ? 0xbff0ff : 0xfff0b0, 8);
            if (s.kind == P_EMBER) return true;
        }
        if (dungeon != null) for (Dungeon.Thing t : dungeon.things) {
            if (t.gone || t.hidden) continue;
            if (s.kind == P_EMBER && t.kind == 't' && !t.on && M.dist(s.x, s.z, t.x, t.z) < 1 && Math.abs(s.y - t.y - 1.4f) < 1.2f) {
                t.on = true;
                sparks(t.x, t.y + 1.5f, t.z, 0xffb050, 12);
                return true;
            }
            if (t.kind == 'o' && M.dist(s.x, s.z, t.x, t.z) < .8f && Math.abs(s.y - t.y - 1.5f) < .9f && !s.hit.isEmpty() == false) {
                t.on = !t.on;
                sfx(Audio.CLICK);
                return true;
            }
            if (t.kind == 'u' && M.dist(s.x, s.z, t.x, t.z) < .5f && s.y < t.y + .8f) breakPot(t);
        }
        else if (story.arrowAt(s.x, s.y, s.z)) return true;
        return false;
    }

    private void updateClaw(float dt) {
        if (!clawOut) return;
        if (clawPull) {
            float dx = clawTX - hero.x, dy = clawTY - hero.y, dz = clawTZ - hero.z, l = M.sqrt(dx * dx + dy * dy + dz * dz);
            if (l < .6f) {
                clawOut = clawPull = false;
                hero.place(clawTX, level.floor(clawTX, clawTZ), clawTZ);
                // Arriving on a post never jars you: no fall is counted from the swing.
                hero.vy = 0;
                return;
            }
            float step = Math.min(l, 20 * dt);
            hero.x += dx / l * step;
            hero.y += dy / l * step;
            hero.z += dz / l * step;
            hero.st = Hero.State.POSE;
            return;
        }
        float speed = (gear.longclaw ? 46 : 34) * dt;
        clawDist += clawBack ? -speed : speed;
        if (clawBack && clawDist <= 0) {
            clawOut = false;
            clawItem = null;
            return;
        }
        clawX = hero.x + clawDX * clawDist;
        clawY = hero.y + 1.4f + clawDY * clawDist;
        clawZ = hero.z + clawDZ * clawDist;
        if (clawItem != null) {
            clawItem.x = clawX;
            clawItem.y = clawY - .4f;
            clawItem.z = clawZ;
            clawItem.vy = 0;
        }
        if (clawBack) return;
        if (clawDist > clawRange() || clawY < level.floor(clawX, clawZ)) {
            clawBack = true;
            return;
        }
        // Foes first, so a boss perched on a post is dragged down rather than the post being grappled.
        for (Foe f : new ArrayList<>(foes))
            if (f.alive() && f.hitTest(clawX, clawY, clawZ) > 0) {
                f.damage(this, Foe.CLAW, f.boss ? 0 : 1, clawX, clawY, clawZ);
                clawBack = true;
                return;
            }
        if (dungeon != null) {
            for (Dungeon.Thing t : dungeon.things) if (t.kind == 'g' && !t.gone && !t.hidden && bite(t.x, t.y, t.z)) return;
        } else for (float[] p : world.places.posts) if (bite(p[0], p[1], p[2])) return;
        for (Drop d : drops)
            if (d.item != CONTAINER && Math.abs(d.x - clawX) < 1 && Math.abs(d.z - clawZ) < 1 && Math.abs(d.y + .3f - clawY) < 1.2f) {
                clawItem = d;
                clawBack = true;
                audio.sfx(Audio.CLICK);
                return;
            }
        Places.Pickup p = dungeon == null ? pickupNear(clawX, clawY, clawZ) : null;
        if (p != null) {
            p.taken = true;
            sparks(p.x, p.y + .4f, p.z, 0xffe9a0, 10);
            clawBack = true;
            story.collect(p.item, p.id);
        }
    }

    /** The claw snaps onto the ring of a post standing at (x, y, z); true if it bit. */
    private boolean bite(float x, float y, float z) {
        if (M.dist(clawX, clawZ, x, z) > 1 || Math.abs(clawY - y - 2.3f) > 1.2f) return false;
        clawPull = true;
        float dx = hero.x - x, dz = hero.z - z, l = Math.max(.1f, M.hypot(dx, dz));
        // Land just in front of the post, or behind it when the near side is a drop or a wall.
        float nx = x + dx / l * .6f, nz = z + dz / l * .6f;
        for (float k : new float[]{-.6f, -1.8f, 1.8f}) {
            if (Math.abs(level.floor(nx, nz) - y) <= .5f) break;
            nx = x + dx / l * k;
            nz = z + dz / l * k;
        }
        clawTX = nx;
        clawTZ = nz;
        clawTY = level.floor(nx, nz) + .8f;
        hero.pose(Hero.State.POSE, Characters.H_AIR);
        audio.sfx(Audio.CLANG);
        return true;
    }

    /** An island pickup within the claw's bite of (x, y, z). */
    private Places.Pickup pickupNear(float x, float y, float z) {
        float size = World.CHUNK * World.CELL;
        int ci = (int) (x / size), cj = (int) (z / size);
        for (int j = cj - 1; j <= cj + 1; j++)
            for (int i = ci - 1; i <= ci + 1; i++) {
                if (i < 0 || j < 0 || i >= World.CHUNKS || j >= World.CHUNKS) continue;
                for (Places.Pickup p : world.places.pickups[i + j * World.CHUNKS])
                    if (!p.taken && Math.abs(p.x - x) < 1.1f && Math.abs(p.z - z) < 1.1f && Math.abs(p.y + .4f - y) < 1.3f) return p;
            }
        return null;
    }

    private void updateDrops(float dt) {
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            float f = level.floor(d.x, d.z);
            d.vy -= 18 * dt;
            d.y = Math.max(f, d.y + d.vy * dt);
            if (d.y <= f) d.vy = 0;
            if (d.item != CONTAINER && (d.life -= dt) <= 0) drops.remove(i);
        }
        collect();
    }

    /** Picks up whatever drops and island pickups lie under the current hero. */
    private void collect() {
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            if (M.dist(d.x, d.z, hero.x, hero.z) < 1.1f && Math.abs(d.y - hero.y) < 1.8f) {
                drops.remove(i);
                story.collect(d.item, null);
            }
        }
        if (dungeon != null) return;
        float size = World.CHUNK * World.CELL;
        int ci = (int) (hero.x / size), cj = (int) (hero.z / size);
        for (int j = cj - 1; j <= cj + 1; j++)
            for (int i = ci - 1; i <= ci + 1; i++) {
                if (i < 0 || j < 0 || i >= World.CHUNKS || j >= World.CHUNKS) continue;
                for (Places.Pickup p : world.places.pickups[i + j * World.CHUNKS])
                    if (!p.taken && M.dist(p.x, p.z, hero.x, hero.z) < 1.1f && Math.abs(p.y - hero.y) < 1.8f) {
                        p.taken = true;
                        story.collect(p.item, p.id);
                    }
            }
    }

    // ---- interaction --------------------------------------------------------------------------

    void offer(float d, String text, Runnable act) {
        if (d < bestD) {
            bestD = d;
            prompt = text;
            action = act;
        }
    }

    private void interactions() {
        prompt = null;
        action = null;
        bestD = 1e9f;
        if (dungeon == null) jobs.progress();
        // On foot, or underwater (for the sunken chests).
        if (hero.st != Hero.State.MOVE && hero.st != Hero.State.DIVE) return;
        story.offers(hero);
    }

    // ---- dialog and flute ---------------------------------------------------------------------

    void talk(String who, Runnable then, String... text) {
        speaker = who;
        pages = text;
        page = 0;
        typed = 0;
        options = null;
        onChoose = null;
        onClose = then;
        screen = Screen.DIALOG;
        if (hero.st == Hero.State.MOVE) hero.pose(Hero.State.POSE, null);
    }

    void choose(String who, String question, String[] opts, IntConsumer pick) {
        talk(who, null, question);
        options = opts;
        onChoose = pick;
        menuSel = 0;
    }

    /** Raises an item overhead with a fanfare, then explains it. */
    void itemGet(Mesh mesh, String title, String text, Runnable then) {
        hero.pose(Hero.State.POSE, Characters.H_ITEM);
        shownItem = mesh;
        audio.sfx(Audio.ITEM);
        talk(title, () -> {
            shownItem = null;
            if (then != null) then.run();
        }, text);
    }

    private void dialog(float dt) {
        hero.anim.update(dt);
        String cur = pages[page];
        float before = typed;
        typed = Math.min(cur.length(), typed + dt * 55);
        if ((int) typed / 3 != (int) before / 3 && typed < cur.length()) audio.sfx(Audio.TALK);
        boolean next = in.any(KeyEvent.VK_E, KeyEvent.VK_SPACE, KeyEvent.VK_ENTER, KeyEvent.VK_J, Input.MOUSE_L);
        if (options != null && typed >= cur.length()) {
            if (in.any(KeyEvent.VK_W, KeyEvent.VK_UP)) menuSel = Math.floorMod(menuSel - 1, options.length);
            if (in.any(KeyEvent.VK_S, KeyEvent.VK_DOWN)) menuSel = Math.floorMod(menuSel + 1, options.length);
        }
        if (!next) return;
        if (typed < cur.length()) {
            typed = cur.length();
            return;
        }
        if (page + 1 < pages.length) {
            page++;
            typed = 0;
            return;
        }
        screen = Screen.PLAY;
        if (hero.st == Hero.State.POSE) {
            hero.setState(Hero.State.MOVE);
            hero.anim.play(null, .2f);
        }
        IntConsumer pick = onChoose;
        Runnable close = onClose;
        onChoose = null;
        onClose = null;
        if (pick != null) pick.accept(menuSel);
        if (close != null) close.run();
    }

    private void flute() {
        hero.anim.update(1 / 60f);
        int[] keys = {KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5};
        for (int n = 0; n < 5; n++) {
            if (!in.pressed(keys[n])) continue;
            audio.note(n);
            if (noteCount == notes.length) {
                System.arraycopy(notes, 1, notes, 0, notes.length - 1);
                noteCount--;
            }
            notes[noteCount++] = n;
            if (story.songPlayed()) return;
        }
        if (in.any(KeyEvent.VK_ESCAPE, KeyEvent.VK_T)) stopFlute();
    }

    void stopFlute() {
        screen = Screen.PLAY;
        hero.setState(Hero.State.MOVE);
        hero.anim.play(null, .2f);
    }

    // ---- helpers ------------------------------------------------------------------------------

    void sfx(int id) { audio.sfx(id); }

    void hitStop(float t) { freeze = Math.max(freeze, t); }

    void shake(float a) { shake = Math.max(shake, a); }

    void say(String s) {
        message = s;
        messageT = 4;
    }

    void splash(float x, float y, float z) {
        sparks(x, y + .1f, z, 0xe0f0ff, 12);
        audio.sfx(Audio.SPLASH);
    }

    void sparks(float x, float y, float z, int col, int n) {
        for (int i = 0; i < n && sparks.size() < 600; i++) {
            Spark s = new Spark();
            s.x = x;
            s.y = y;
            s.z = z;
            s.vx = (rng.nextFloat() - .5f) * 7;
            s.vy = rng.nextFloat() * 6 + 1;
            s.vz = (rng.nextFloat() - .5f) * 7;
            s.life = .35f + rng.nextFloat() * .45f;
            s.col = col;
            sparks.add(s);
        }
    }
}

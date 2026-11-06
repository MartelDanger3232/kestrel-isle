package kestrel;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Story and progression: the people of Brindle, songs, gates, dungeon events, saving and loading. */
final class Story {
    static final int KINDLING = 1, TIDES = 2, DAWN = 4, STEED = 8;
    /** Original melodies on the flute's five notes (keys 1-5). */
    static final int[][] SONGS = {{0, 2, 4, 3, 2}, {4, 3, 1, 0, 1}, {2, 0, 2, 3, 4}, {1, 3, 4, 3, 1}};
    static final String[] SONG_NAMES = {"Song of Kindling", "Song of Tides", "Song of Dawn", "Song of the Steed"};
    static final String[] STATS = {"Vigor", "Endurance", "Mind", "Strength", "Arcana"};
    static final String[] STAT_HOW = {"more life", "more stamina", "more spirit for spells", "harder sword blows", "stronger spells"};
    private static final String[] BOSS_TIPS = {
            "Bramblemaw: hold Shift to lock on to a glowing seed-bulb, then shoot it (bow on Q, or Ember Bolts on C). The lock jumps to the other bulb; once it slumps, strike its head.",
            "Cinder Golem: roll out of the way when it raises its fist. When the fist sticks in the floor glowing, light a bomb (X to pick bombs, F) and throw it (F) right beside the fist. The armour cracks: strike the glowing core while it kneels. Roll through its shockwave rings. Out of bombs? The glowing orange Blast Pods in the corners regrow.",
            "Tide Serpent: when its head crashes down and rests on the floor, fire the Grapple Claw at its mask to tear it free.",
            "Hollow King: lock on (Shift) to its glowing lantern-heart and shoot it to bring it down. When it rises high and shines, drag it down with the claw.",
            "Hoarwing: it circles high, dives at you (roll aside) and rains icicles (keep moving). When it lands on a perch post and its eyes glow, fire the claw at it (X picks the claw, hold F, let go) to drag it onto the ice, then strike while it's down."};
    /** Side quests, in the order of their two-bit slots in Gear.quests. */
    static final String[] QUESTS = {"Tibb's kite", "Ansel's lost rod", "Oskar's rime ore", "Hessa's wolf hunt"};
    static final String[] QUEST_HOW = {"Caught high in the great oak west of Brindle. Only a claw could reach it.",
            "Dropped atop a rock spire west of the rangers' outpost. Grapple the post on top.",
            "Rime ore glitters somewhere inside Rimeholt. Bring it to Oskar's forge.",
            "Hunt 6 Frostfang wolves in the snowfields, then report to Hessa."};
    static final String[] QUEST_BACK = {"Take the kite back to Tibb in Brindle.", "Take the rod back to Ansel in Brindle.",
            "Take the ore to Oskar the smith in Brindle.", "Report to Hessa at the outpost."};
    /** Where you wake after falling: {dungeon index or -1, x, y, z}. */
    float[] respawn;
    private float bossClock;
    static final String[] STONES = {"Root", "Ember", "Tide", "Frost"};
    /**
     * The player's save file, in a per-user folder so saving works however the game was launched (a packaged app
     * may start somewhere it can't write). Self-tests point this somewhere temporary so they never touch it.
     */
    static Path SAVE = Path.of(System.getProperty("user.home"), ".kestrel-isle", "save.properties");
    /** Off for headless tools, so they never touch a real save. */
    static boolean saving = true;

    private final Game g;
    final Set<String> flags = new HashSet<>();
    int titleSel;
    private float endingT = -1;

    Story(Game g) { this.g = g; }

    boolean hasSave() { return Files.exists(SAVE); }

    void titleScreen() {
        g.climate.update(1 / 60f);
        int n = hasSave() ? 2 : 1;
        if (g.in.any(KeyEvent.VK_W, KeyEvent.VK_UP, KeyEvent.VK_S, KeyEvent.VK_DOWN)) titleSel = (titleSel + 1) % n;
        if (!g.in.any(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE, KeyEvent.VK_E)) return;
        g.screen = Game.Screen.PLAY;
        if (titleSel == 1) load();
        else {
            g.camYaw = M.atan2(g.world.places.npcs.get(0).x() - g.hero.x, g.world.places.npcs.get(0).z() - g.hero.z);
            g.hero.yaw = g.camYaw;
            g.say("Brindle village. Find Elder Maren (walk up and press E).");
        }
        g.audio.music(Audio.VILLAGE);
    }

    // ---- people and places --------------------------------------------------------------------

    void offers(Hero h) {
        if (g.dungeon != null) {
            dungeonOffers(h);
            return;
        }
        Places pl = g.world.places;
        Horse horse = g.horse;
        if (horse.summoned && !horse.ridden && horse.remountT <= 0) {
            float d = M.dist(h.x, h.z, horse.x, horse.z);
            // People and shrines nearby take priority over riding; G sends her away entirely.
            if (d < 2.8f) g.offer(d + 1.5f, "Ride Juniper   (G: send her away)", () -> horse.mount(g));
        }
        for (int i = 0; i < pl.shrines.size(); i++) {
            float[] sh = pl.shrines.get(i);
            float d = M.dist(h.x, h.z, sh[0], sh[2]);
            int id = i;
            if (d < 12) g.tip("shrine", "A Lantern Shrine. Rest here (E) to heal, refill your draughts, level up and travel. Resting wakes the monsters again.");
            if (d < 2.6f) g.offer(d, "Rest at the Lantern Shrine", () -> rest(id, sh[0], sh[1], sh[2]));
        }
        for (Places.Npc n : pl.npcs) {
            float d = M.dist(h.x, h.z, n.x(), n.z());
            if (d < 2.6f) g.offer(d, "Talk to " + name(n.id()), () -> talkTo(n));
        }
        for (Places.Sign s : pl.signs) {
            float d = M.dist(h.x, h.z, s.x(), s.z());
            if (d < 2.2f) g.offer(d, "Read", () -> g.talk("Sign", null, s.text()));
        }
        for (Places.Gate gate : pl.gates) {
            float fx = gate.x, fz = gate.z - 5, d = M.dist(h.x, h.z, fx, fz);
            if (d > 3.5f) continue;
            switch (gate.kind) {
                case Places.ROOT -> g.offer(d, "Enter Rootdeep Hollow", () -> enterDungeon(0));
                case Places.CINDER -> g.offer(d, gate.open ? "Enter Cinder Forge" : "Sealed. A lantern glows above the gate.", gate.open ? () -> enterDungeon(1) : null);
                case Places.TIDE -> g.offer(d, gate.open ? "Enter Tidewell Vault" : "A curtain of seawater bars the way.", gate.open ? () -> enterDungeon(2) : null);
                case Places.CROWN -> g.offer(d, g.gear.stones == 15 ? "Descend into the Hollow Crown" : "Shadow seals the way (Wardstones " + Integer.bitCount(g.gear.stones) + "/4)",
                        g.gear.stones == 15 ? () -> enterDungeon(3) : null);
                case Places.FROST -> g.offer(d, "Enter Rimeholt", () -> enterDungeon(4));
                default -> {
                    boolean clear = pl.boulders.get(1)[3] == 0;
                    g.offer(d, clear ? "Touch the old altar" : "Rubble buries something here.", clear ? this::altar : null);
                }
            }
        }
    }

    private static String name(String id) {
        return switch (id) {
            case "elder" -> "Elder Maren";
            case "merchant" -> "Pell";
            case "smith" -> "Oskar";
            case "child" -> "Tibb";
            case "ranger" -> "Hessa";
            default -> "Ansel";
        };
    }

    void talkTo(Places.Npc n) {
        Game.Gear gear = g.gear;
        switch (n.id()) {
            case "elder" -> elder();
            case "merchant" -> shop();
            case "smith" -> {
                if ((gear.songs & STEED) == 0) g.talk("Oskar", () -> teach(3),
                        "The island's a long walk on two legs. Take old Juniper. She's quick, she's brave, and she knows every road.",
                        "Whistle her this tune on your flute and she'll come running wherever you are. Press E beside her to ride.");
                else if (quest(2) == 2) g.talk("Oskar", () -> {
                    setQuest(2, 3);
                    gear.swordLv++;
                    g.refreshStats();
                    g.itemGet(Characters.SWORD, "Rime-Tempered Blade", "Oskar folds the rime ore into your sword. It strikes harder (blade +" + gear.swordLv + "), and it cost you nothing.", this::save);
                }, "Rime ore! Look at it shine. My grandfather forged with this once, and never shut up about it.", "Give me that blade of yours. This one's on the house.");
                else if (quest(2) == 0 && (gear.stones & 7) == 7) g.talk("Oskar", () -> {
                    setQuest(2, 1);
                    smithy();
                }, "Heading north? Then listen. Rimeholt's ice is shot through with rime ore, the finest steel-stuff there is.", "Bring me a chunk and I'll temper your blade for free. Now, what can I do for you today?");
                else smithy();
            }
            case "child" -> {
                if (quest(0) == 2) g.talk("Tibb", () -> {
                    setQuest(0, 3);
                    collect(Places.PIECE, "tibb-reward");
                }, "MY KITE! You got it down! How did you even...", "Here. I found this in the flower patch. Gran says it's a piece of somebody's heart. That's weird, right? You keep it.");
                else if ((gear.songs & DAWN) == 0) g.talk("Tibb", () -> teach(2), "My gran hums this when she wants the morning to hurry up. Want to hear it?");
                else if (quest(0) == 3) g.talk("Tibb", null, "I'm flying it LOW from now on. Probably.");
                else {
                    if (quest(0) == 0) setQuest(0, 1);
                    g.talk("Tibb", null, "My kite got stuck way up in the big old oak, past the houses on the west side. Nobody can climb that high.",
                            gear.claw ? "Hey, is that a grappling claw? Could you snag it down for me? Please?" : "If only someone had a really long arm. Or a hook on a rope.");
                }
            }
            case "ranger" -> hessa();
            default -> {
                if (quest(1) == 2) g.talk("Ansel", () -> {
                    setQuest(1, 3);
                    gear.gleams += 400;
                    g.say("+400 gleams");
                    save();
                }, "My rod! Forty years that rod and I have fished together. I thought the wolves had it for sure.", "Take these gleams. No, I insist. The fish will be biting again soon, I can feel it.");
                else if (quest(1) == 3) g.talk("Ansel", null, (gear.stones & 15) == 15 ? "The fish are rising again. Whatever you're doing, Keeper, keep doing it." : "That rod's never felt luckier. Thank you again.");
                else if (flags.contains("hessa") && quest(1) == 0) {
                    setQuest(1, 1);
                    g.talk("Ansel", null, "Up north, are you? I went ice-fishing past the rangers' outpost and a pack of wolves chased me clean up a rock spire.",
                            "I scrambled down the other side, but my rod's still up there. If you can get up a sheer rock, it's yours to fetch.");
                } else g.talk("Ansel", null, gear.bombBag || (gear.stones & 2) != 0
                        ? "Out east past the dunes there's an altar buried in rubble. Old folk say it taught a song that moves the sea."
                        : "The fish have all gone deep. Something's wrong under the mountain, mark my words.");
            }
        }
    }

    int quest(int q) { return g.gear.quests >> q * 2 & 3; }

    void setQuest(int q, int v) { g.gear.quests = g.gear.quests & ~(3 << q * 2) | v << q * 2; }

    /** Elder Maren: where to go next, and a little more of the island's story each time a Wardstone comes home. */
    private void elder() {
        Game.Gear gear = g.gear;
        if (!flags.contains("intro")) intro();
        else if ((gear.stones & 1) == 0) g.talk("Elder Maren", null, "Rootdeep Hollow lies up the north-west road.",
                "Its sealed doors answer to fire. Play the Song of Kindling near the torches: T, then 1 3 5 4 3.");
        else if ((gear.stones & 2) == 0) g.talk("Elder Maren", null, "The Root stone is home. Do you feel it? The ground's a little steadier.",
                "Long ago there was another Keeper before your grandmother. Corvin. Brilliant, proud, and the first to hear the Hollow whispering under the mountain.",
                "The Ember stone is in the Cinder Forge, at the foot of the mountain. The forge gate opens for those who strike the lantern above it.");
        else if ((gear.stones & 4) == 0) g.talk("Elder Maren", null, "Two stones. The forge was Corvin's work, you know. He built it to make a crown that could cage the Hollow.",
                "Iron can't hold a thing with no shape. He learned that too late.", "Ansel the fisher has been muttering about the sea. Ask him.");
        else if ((gear.stones & 8) == 0) g.talk("Elder Maren", null, "Three stones, and still the shadow holds the Crown. I feared this.",
                "There were four Wardstones, child. When Corvin put on his crown and the Hollow took him, your grandmother carried the fourth far into the northern snow, where he could never reach it.",
                "Rimeholt. Find Hessa's rangers at their outpost past the Rootdeep woods. They'll show you the way.");
        else if (g.dungeons[3] == null || !g.dungeons[3].bossDead) g.talk("Elder Maren", null, "All four. Wren would be so proud of you. Your grandmother, I mean. I still can't say her name without smiling.",
                "The thing beneath the Crown wears Corvin's face. Whatever it says to you down there, remember: he asked to be stopped.",
                "Go, Keeper. Bring the island its morning back.");
        else g.talk("Elder Maren", null, "The lighthouse is burning again. I keep walking past it just to look.", "Corvin is at rest now. You did that.");
    }

    /** Hessa, captain of the northern rangers: the way into Rimeholt, and a wolf hunt. */
    private void hessa() {
        Game.Gear gear = g.gear;
        if (!flags.contains("hessa")) {
            flags.add("hessa");
            g.talk("Hessa", this::save, "A Keeper? Out here? Maren must be desperate. Or you're very good.",
                    "Rimeholt is the old ice-hold up on the mesa, just north of here. No stairs, no ropes. The cliff is sheer on every side.",
                    "The Keepers of old came and went by claw. There's a post on the cliff edge. Point your claw at it and let it haul you up.",
                    "Inside it's ice, wolves, and something big that nests at the top. We hear it scream at night. Melt the ice with fire, and watch your footing.");
        } else if (quest(3) == 2) g.talk("Hessa", () -> {
            setQuest(3, 3);
            gear.maxFlasks = Math.min(7, gear.maxFlasks + 1);
            gear.flasks = gear.maxFlasks;
            gear.gleams += 500;
            g.itemGet(Characters.DRAUGHT, "Ranger's Draught", "Hessa adds a flask to your belt: one more Ember Draught (now " + gear.maxFlasks + "), and 500 gleams for the hunt.", this::save);
        }, "Six fewer wolves on my roads. The supply sleds will run again.", "Here, ranger's pay, and something better.");
        else if (quest(3) == 0) {
            setQuest(3, 1);
            gear.wolves = 0;
            g.talk("Hessa", this::save, "One more thing, Keeper. The Frostfang packs have grown bold since the Hollow woke. They're cutting off our supply sleds.",
                    "Hunt six of them and come back. I'll make it worth your while.");
        } else if (quest(3) == 1) g.talk("Hessa", null, "Wolves hunted: " + Math.min(6, gear.wolves) + " of 6. They circle before they lunge. Hit them as they come in.");
        else g.talk("Hessa", null, (gear.stones & 8) == 0 ? "Rimeholt is up on the mesa. Claw your way up the post on the cliff edge." : "The north is quiet for the first time in years. Go finish it, Keeper.");
    }

    private void smithy() {
        Game.Gear gear = g.gear;
        String hint = gear.bombBag ? "Bombs crack old stone. There's a rubble-choked cave east of the river, and more on the coast."
                : gear.bow ? "That forge gate? A lantern sits above it. Put an arrow through it and the old works will wake."
                : "Rootdeep's up the north-west road. Ride, don't walk.";
        if (gear.swordLv >= 5) {
            g.talk("Oskar", null, "That blade's as fine as anything I've ever made. " + hint);
            return;
        }
        int cost = 150 << gear.swordLv;
        g.choose("Oskar", "I can hone your sword to +" + (gear.swordLv + 1) + " for " + cost + " gleams. (You have " + gear.gleams + ".)",
                new String[]{"Hone it", "Any advice?", "Not now"}, pick -> {
                    if (pick == 1) g.talk("Oskar", null, hint);
                    else if (pick == 0) {
                        if (gear.gleams < cost) g.say("Not enough gleams.");
                        else {
                            gear.gleams -= cost;
                            gear.swordLv++;
                            g.sfx(Audio.CLANG);
                            g.say("Keeper's Sword +" + gear.swordLv + "!");
                            save();
                        }
                    }
                });
    }

    private void intro() {
        g.talk("Elder Maren", () -> g.itemGet(Characters.SWORD, "Keeper's Sword", "Click or J to swing. Keep pressing for a combo, and hold to charge a spin attack.", () ->
                        g.itemGet(Characters.SHIELD, "Driftwood Shield", "Hold K or right-click to raise it. Hold Shift to lock on to a foe, then Space to hop or flip.", () ->
                                g.itemGet(Characters.FLUTE, "Windflute", "Press T to play and 1-5 for notes. Every song you learn is listed in your satchel (Tab).", () ->
                                        g.itemGet(Characters.EMBER_BOLT, "Ember Bolt", "Your grandmother's first spell. Press C to cast it, V to switch spells. Spells drain spirit (the blue bar).", () -> {
                                            flags.add("intro");
                                            g.gear.spells |= 1;
                                            teach(0);
                                        })))),
                "There you are, Keeper's child. The lighthouse went dark last night, and the old songs have gone quiet.",
                "Something called the Hollow crept up from beneath the mountain. It stole the three Wardstones that keep Kestrel Isle in balance.",
                "Root, Ember and Tide. The first rests in Rootdeep Hollow, up the north-west road.",
                "Your grandmother kept the island's songs. These were hers. Take them.");
        g.gear.sword = g.gear.shield = g.gear.flute = true;
    }

    private void teach(int song) {
        StringBuilder keys = new StringBuilder();
        for (int n : SONGS[song]) keys.append(n + 1).append(' ');
        g.gear.songs |= 1 << song;
        g.sfx(Audio.SECRET);
        if (song == 3) g.horse.call(g);
        g.talk(SONG_NAMES[song], this::save, "You learned the " + SONG_NAMES[song] + "!  Play it with T, then: " + keys.toString().trim(),
                switch (song) {
                    case 0 -> "It wakes any fire close by.";
                    case 1 -> "The sea and the waters under the island listen to it.";
                    case 3 -> "Juniper will come galloping wherever you are on the island.";
                    default -> "It hurries the sky along to morning, or to night.";
                });
    }

    private void shop() {
        Game.Gear gear = g.gear;
        String[] opts = {"Arrows x10  -  20", gear.bombBag ? "Bombs x5  -  30" : "Bombs (sold out)", flags.contains("shop-piece") ? "(sold)" : "Piece of Heart  -  150",
                "Red Tonic: refill hearts  -  30", "Just looking"};
        g.choose("Pell", "Welcome! You have " + gear.gleams + " gleams. What'll it be?", opts, pick -> {
            int[] cost = {20, 30, 150, 30, 0};
            if (pick == 4) return;
            boolean can = switch (pick) {
                case 0 -> gear.bow;
                case 1 -> gear.bombBag;
                case 2 -> !flags.contains("shop-piece");
                default -> true;
            };
            if (!can) {
                g.say("You can't use that yet.");
                return;
            }
            if (gear.gleams < cost[pick]) {
                g.say("Not enough gleams.");
                return;
            }
            gear.gleams -= cost[pick];
            g.sfx(Audio.PICKUP);
            switch (pick) {
                case 0 -> gear.arrows = Math.min(50, gear.arrows + 10);
                case 1 -> gear.bombs = Math.min(30, gear.bombs + 5);
                case 2 -> {
                    flags.add("shop-piece");
                    collect(Places.PIECE, null);
                }
                default -> g.hero.hp = g.hero.maxHp;
            }
        });
    }

    private void altar() {
        if ((g.gear.songs & TIDES) == 0) g.talk("Old altar", () -> teach(1), "Waves are carved into the stone. As you touch it, a melody rises out of the sea wind...");
        else g.talk("Old altar", null, "The altar hums the Song of Tides. The water gate stands north of here.");
    }

    /** Called after each flute note; true if the last notes formed a known song. */
    boolean songPlayed() {
        for (int s = 0; s < SONGS.length; s++) {
            int[] song = SONGS[s];
            if ((g.gear.songs & 1 << s) == 0 || g.noteCount < song.length) continue;
            boolean match = true;
            for (int i = 0; i < song.length; i++) match &= g.notes[g.noteCount - song.length + i] == song[i];
            if (!match) continue;
            g.noteCount = 0;
            g.stopFlute();
            g.sfx(Audio.SECRET);
            songEffect(s);
            return true;
        }
        return false;
    }

    private void songEffect(int s) {
        Hero h = g.hero;
        switch (s) {
            case 0 -> {
                int lit = 0;
                if (g.dungeon != null) for (Dungeon.Thing t : g.dungeon.things)
                    if (t.kind == 't' && !t.on && M.dist(t.x, t.z, h.x, h.z) < 21) {
                        t.on = true;
                        lit++;
                        g.sparks(t.x, t.y + 1.5f, t.z, 0xffb050, 12);
                    }
                if (g.dungeon != null && g.dungeon.melt(h.x, h.z, 21, g)) lit++;
                g.say(lit > 0 ? "The Song of Kindling! Flames leap up." : "Song of Kindling. Nothing nearby to light.");
            }
            case 1 -> {
                if (g.dungeon != null && g.dungeonIndex == 2) {
                    if (g.dungeon.bossStarted && !g.dungeon.bossDead) g.say("The water won't answer while the serpent stirs.");
                    else {
                        g.dungeon.flooded = !g.dungeon.flooded;
                        g.shake(.5f);
                        g.say(g.dungeon.flooded ? "Water rushes back into the vault." : "The water drains away!");
                    }
                    return;
                }
                Places.Gate tide = g.world.places.gates.get(2);
                if (g.dungeon == null && M.dist(h.x, h.z, tide.x, tide.z) < 40 && !tide.open) {
                    tide.open = true;
                    g.say("The curtain of water parts. Tidewell Vault is open.");
                    save();
                } else g.say("The sea stirs, but nothing answers here.");
            }
            case 3 -> {
                if (g.dungeon != null) g.say("Juniper can't hear you down here.");
                else {
                    g.horse.call(g);
                    g.say("Juniper comes at a gallop!");
                }
            }
            default -> {
                if (g.dungeon != null) {
                    g.say("There's no sky down here to answer.");
                    return;
                }
                g.climate.hour = g.climate.isNight() ? 6.6f : 18.6f;
                g.say(g.climate.isNight() ? "Dusk falls." : "Morning comes in a rush of light.");
            }
        }
    }

    /** An arrow in the open world; true if it struck something special. */
    boolean arrowAt(float x, float y, float z) {
        Places.Gate forge = g.world.places.gates.get(1);
        if (!forge.open && M.dist(x, z, forge.x, forge.z) < 1.4f && Math.abs(y - forge.y - 8.6f) < 1.4f) {
            forge.open = true;
            g.sfx(Audio.SECRET);
            g.shake(.5f);
            g.say("The lantern flares and the forge gate grinds open!");
            save();
            return true;
        }
        return false;
    }

    void bombedField(float x, float z) {
        java.util.List<float[]> bs = g.world.places.boulders;
        for (int i = 0; i < bs.size(); i++) {
            float[] b = bs.get(i);
            if (b[3] == 0 || M.dist(x, z, b[0], b[2]) > 4.5f) continue;
            b[3] = 0;
            flags.add("boulder" + i);
            g.sparks(b[0], b[1] + 1.5f, b[2], 0x6e6a62, 30);
            g.sfx(Audio.SECRET);
        }
    }

    void fieldEvents(float dt) {
        tutorials();
        if (g.dungeon == null)
            for (float[] p : g.world.places.pods) {
                if (p[3] == 0 && (p[4] += dt) > 12) {
                    p[3] = 1;
                    p[4] = 0;
                }
                if (p[3] > 0 && g.gear.bombBag && g.gear.bombs < 30 && M.dist(g.hero.x, g.hero.z, p[0], p[2]) < 1.4f) {
                    p[3] = 0;
                    g.gear.bombs = Math.min(30, g.gear.bombs + 3);
                    g.sfx(Audio.PICKUP);
                    g.say("+3 bombs (the Blast Pod will regrow)");
                }
            }
        if (endingT > 0 && (endingT -= dt) <= 0) g.talk("Corvin", () -> {
                    g.screen = Game.Screen.END;
                    g.overT = 0;
                    g.audio.music(Audio.TITLE);
                }, "...Keeper. Wren's grandchild. I can see your face now, and not through the Hollow's eyes.",
                "I tried to cage the dark by wearing it. For forty years I have been its lantern, its jailer, and its servant.",
                "You did what I could not. You carried all four stones, and you never once reached for the crown.",
                "Tell Maren I'm sorry. And tell her the lighthouse should be lit at dusk, not dawn. She'll laugh at that.",
                "Goodbye, Keeper. Kestrel Isle is yours to keep now.");
        if (g.dungeon != null) return;
        Places pl = g.world.places;
        boolean village = M.dist(g.hero.x, g.hero.z, pl.villageX, pl.villageZ) < 70;
        g.audio.music(village ? Audio.VILLAGE : g.climate.isNight() ? Audio.NIGHT : Audio.FIELD);
    }

    // ---- items and pickups --------------------------------------------------------------------

    void collect(int item, String id) {
        if (id != null) flags.add("p:" + id);
        Game.Gear gear = g.gear;
        Hero h = g.hero;
        switch (item) {
            case Places.G1, Places.G5, Places.G20 -> {
                gear.gleams += new int[]{1, 5, 20}[item];
                g.sfx(Audio.GLEAM);
            }
            case Places.HEART -> {
                h.hp = Math.min(h.maxHp, h.hp + 4);
                g.sfx(Audio.HEART);
            }
            case Places.ARROWS -> {
                gear.arrows = Math.min(50, gear.arrows + 10);
                g.sfx(Audio.PICKUP);
            }
            case Places.BOMBS -> {
                gear.bombs = Math.min(30, gear.bombs + 5);
                g.sfx(Audio.PICKUP);
            }
            case Places.KITE -> {
                setQuest(0, 2);
                g.itemGet(Models.KITE, "Tibb's Kite", "Snagged it! Take it back to Tibb in Brindle.", this::save);
            }
            case Places.ROD -> {
                setQuest(1, 2);
                g.itemGet(Models.ROD, "Ansel's Rod", "A well-worn fishing rod with forty years of stories in it. Take it back to Ansel in Brindle.", this::save);
            }
            case Places.PIECE -> {
                gear.pieces++;
                boolean whole = gear.pieces % 4 == 0;
                if (whole) {
                    gear.hearts++;
                    g.refreshStats();
                    h.hp = h.maxHp;
                }
                g.itemGet(Models.HEART_PIECE, "Piece of Heart", whole ? "Four pieces make a whole heart! Your life grows." : "Collect four to grow your life. (" + gear.pieces % 4 + "/4)", this::save);
            }
            default -> {
                gear.hearts++;
                g.refreshStats();
                h.hp = h.maxHp;
                g.itemGet(Models.HEART_CONTAINER, "Heart Vessel", "Your life grows by one heart.", this::save);
            }
        }
    }

    private void giveChest(Dungeon.Thing t) {
        Dungeon d = g.dungeon;
        t.on = true;
        g.sfx(Audio.CHEST);
        Game.Gear gear = g.gear;
        switch (t.content) {
            case "key" -> {
                d.keys++;
                g.itemGet(Models.KEY, "Small Key", "It opens one locked door in this dungeon.", null);
            }
            case "bigkey" -> {
                d.bigKey = true;
                g.itemGet(Models.KEY, "Great Key", "It fits the great door that guards this dungeon's master.", null);
            }
            case "bow" -> {
                gear.bow = gear.bowOut = true;
                gear.arrows = Math.max(gear.arrows, 20);
                g.itemGet(Models.BOW, "Hunter's Bow", "Q swaps between your sword and bow. With the bow out, hold click (or J) to draw and release to shoot. Lock on (Shift) and your arrows fly straight to the target.", this::save);
            }
            case "bombs" -> {
                gear.bombBag = true;
                gear.bombs = Math.max(gear.bombs, 10);
                gear.item = 1;
                g.itemGet(Models.BOMB_BAG, "Bomb Bag", "Press F to light a bomb, F again to throw it. X switches between your tools. Cracked stone won't stand up to these.", this::save);
            }
            case "longclaw" -> {
                gear.longclaw = true;
                gear.item = 2;
                g.itemGet(Characters.CLAW, "Longclaw", "Rimeholt's chain nearly doubles your claw's reach (30 m) and reels it in faster. Lock on to yank a foe to you; it tears shields away and fetches things far out of reach.", this::save);
            }
            case "ore" -> {
                if (quest(2) != 3) setQuest(2, 2);
                g.itemGet(Models.stone(0x9fdcf4), "Rime Ore", "A chunk of glittering blue ore. Oskar the smith would give a lot for this.", this::save);
            }
            default -> {
                gear.claw = true;
                gear.item = 2;
                g.itemGet(Characters.CLAW, "Grapple Claw", "Select it with X, hold F to aim and release to fire. It drags you to wooden posts and tears loose things free.", this::save);
            }
        }
    }

    // ---- dungeons -----------------------------------------------------------------------------

    private void dungeonOffers(Hero h) {
        Dungeon d = g.dungeon;
        for (Dungeon.Thing t : d.things) {
            if (t.gone || t.hidden) continue;
            float dist = M.dist(h.x, h.z, t.x, t.z);
            switch (t.kind) {
                case 'c', 'C' -> { if (!t.on && dist < 2.1f && Math.abs(h.y - t.y) < 1) g.offer(dist, "Open", () -> giveChest(t)); }
                case 'L' -> {
                    if (!t.on && dist < 2.4f) g.offer(dist, d.keys > 0 ? "Unlock (keys: " + d.keys + ")" : "Locked. A small key would open it.", d.keys > 0 ? () -> {
                        d.keys--;
                        t.on = true;
                        g.sfx(Audio.DOOR);
                    } : null);
                }
                case 'B' -> {
                    if (!t.on && dist < 2.4f) g.offer(dist, d.bigKey ? "Open the great door" : "A great lock. You need this dungeon's Great Key.", d.bigKey ? () -> {
                        t.on = true;
                        g.sfx(Audio.DOOR);
                    } : null);
                }
                case 'R' -> {
                    if (dist < 2.4f) g.offer(dist, "Rest at the Lantern Shrine", () -> rest(-1, t.x, t.y, t.z));
                }
                case 'w' -> {
                    if (dist < 2.2f) g.offer(dist, "Take the " + d.spec.stoneName(), () -> {
                        t.gone = true;
                        g.gear.stones |= Dungeons.stoneBit(g.dungeonIndex);
                        int n = Integer.bitCount(g.gear.stones);
                        Runnable after = n == 4 ? this::ascend : this::exitDungeon;
                        String memory = MEMORIES[Math.min(3, g.dungeonIndex == 4 ? 3 : g.dungeonIndex)];
                        g.itemGet(d.spec.stone(), d.spec.stoneName(), n == 4 ? "All four Wardstones! The shadow over the Hollow Crown will break now."
                                : "The island breathes a little easier. (" + n + "/4 Wardstones)", () -> g.talk("A voice in the stone", after, memory));
                    });
                }
                default -> {}
            }
        }
    }

    /** Corvin's last thoughts, sealed into each Wardstone in turn. */
    private static final String[] MEMORIES = {
            "\"...the Hollow came up through the roots first. I was young, and so sure I could master it.\"  - C.",
            "\"...I forged a crown to cage it. Iron cannot hold what has no shape. Wren warned me. I didn't listen.\"  - C.",
            "\"...I wore the crown myself, and it was not me who wore it after. Forgive me, Wren. Hide the last stone where I can never follow.\"  - C.",
            "\"...if you hold this, the Frost stone has been found. Then I can finally be stopped. Be braver than I was.\"  - C."};

    /** All four Wardstones: the hero becomes Ascended. */
    private void ascend() {
        Game.Gear gear = g.gear;
        gear.ascended = true;
        gear.spells |= 1 << Magic.STARFALL;
        g.refreshStats();
        g.hero.hp = g.hero.maxHp;
        g.hero.fp = g.hero.maxFp;
        g.shake(1);
        g.itemGet(Characters.STAR, "Ascended", "The four Wardstones burn inside you. Your sword sheds radiant waves, your spells and blows hit far harder, and you can call down Starfall (C, after V to select it).",
                this::exitDungeon);
    }

    void dungeonEvents() {
        Dungeon d = g.dungeon;
        Hero h = g.hero;
        if (d.at(d.ti(h.x), d.tj(h.z)) == 'E') {
            exitDungeon();
            return;
        }
        if (!d.bossStarted && !d.bossDead && M.dist(h.x, h.z, d.bossX, d.bossZ) < 13 && d.inBossRoom(h.z)) {
            d.bossStarted = true;
            Foe.Kind kind = Foe.Kind.values()[Foe.Kind.BRAMBLE.ordinal() + d.spec.boss()];
            Foe b = new Foe(kind, d.bossX, d.bossY, d.bossZ);
            b.setState(Foe.CHASE);
            b.yaw = M.PI;
            if (kind == Foe.Kind.KING) b.y = d.bossY + 4;
            if (kind == Foe.Kind.HOARWING) b.y = d.bossY + 7;
            if (kind == Foe.Kind.SERPENT) {
                d.flooded = true;
                b.y = 0;
            }
            g.boss = b;
            g.foes.add(b);
            bossClock = 0;
            g.audio.music(Audio.BOSS);
            g.say(switch (kind) {
                case BRAMBLE -> "BRAMBLEMAW, the Rotting Root";
                case GOLEM -> "THE CINDER GOLEM, Keeper of the Forge";
                case SERPENT -> "THE TIDE SERPENT, Drowned Warden";
                case HOARWING -> "HOARWING, the Frost That Watches";
                default -> "THE HOLLOW KING, once Corvin the Keeper";
            });
            g.messageT = 4;
        }
    }

    void bossDefeated(Foe f) {
        Dungeon d = g.dungeon;
        if (d == null) return;
        d.bossDead = true;
        int spell = switch (f.kind) {
            case BRAMBLE -> Magic.FROST;
            case GOLEM -> Magic.THUNDER;
            case SERPENT -> Magic.MEND;
            default -> -1;
        };
        if (spell >= 0) {
            g.gear.spells |= 1 << spell;
            g.gear.maxFlasks = Math.min(6, g.gear.maxFlasks + 1);
            g.gear.flasks = g.gear.maxFlasks;
            g.tip("spell" + spell, "You absorbed a new spell: " + Magic.NAME[spell] + "! " + Magic.HOW[spell] + " (V to select, C to cast)");
        }
        if (f.kind == Foe.Kind.HOARWING) {
            g.gear.frostbrand = true;
            g.gear.maxFlasks = Math.min(7, g.gear.maxFlasks + 1);
            g.gear.flasks = g.gear.maxFlasks;
            g.tip("frostbrand", "Hoarwing's frost clings to your blade: the Frostbrand. Your sword now freezes lesser foes for a moment with every blow.");
        }
        g.boss = null;
        g.shake(1);
        g.audio.music(Audio.DUNGEON);
        if (f.kind == Foe.Kind.KING) {
            endingT = 3;
            g.say("The Hollow unravels into morning light...");
            return;
        }
        for (Dungeon.Thing t : d.things) if (t.kind == 'w') t.hidden = false;
        Game.Drop c = new Game.Drop();
        c.item = Game.CONTAINER;
        c.x = d.bossX;
        c.y = d.bossY + 2;
        c.z = d.bossZ - 3;
        g.drops.add(c);
        g.say("The way out has opened. Claim the Wardstone.");
        save();
    }

    void enterDungeon(int k) {
        if (g.horse.ridden) g.horse.dismount(g);
        if (g.dungeons[k] == null) g.dungeons[k] = new Dungeon(Dungeons.ALL[k]);
        Dungeon d = g.dungeons[k];
        g.dungeon = d;
        g.dungeonIndex = k;
        g.level = d;
        g.foes.clear();
        g.shots.clear();
        g.bombs.clear();
        g.drops.clear();
        g.waves.clear();
        g.clawOut = false;
        d.foes.clear();
        d.bossStarted = false;
        for (Dungeon.Thing s : d.spawns) {
            Foe f = new Foe(switch (s.kind) {
                case 'f' -> Foe.Kind.BAT;
                case 'a' -> Foe.Kind.SENTRY;
                case 'm' -> Foe.Kind.SLIME;
                case 'i' -> Foe.Kind.IMP;
                case 'W' -> Foe.Kind.WOLF;
                default -> Foe.Kind.THORNLING;
            }, s.x, s.y + (s.kind == 'f' ? 2.5f : 0), s.z);
            f.si = s.i;
            f.sj = s.j;
            g.foes.add(f);
            d.foes.add(f);
        }
        g.hero.place(d.spawnX, d.spawnY, d.spawnZ);
        g.hero.yaw = g.camYaw = 0;
        g.camPitch = -.45f;
        g.fade = 1;
        g.audio.music(Audio.DUNGEON);
        g.say(d.spec.name());
        save();
    }

    /** After a Wardstone, spell out the next step instead of leaving the player to guess. */
    private void whatNext() {
        int stones = g.gear.stones;
        if (!flags.add("told:" + stones)) return;
        String[] text = switch (stones) {
            case 1 -> new String[]{"The Root stone is safe. The next Wardstone burns inside the Cinder Forge, at the foot of the mountain east of Brindle.",
                    "Follow the gold marker and the pillar of light to its gate. A lantern glows above the gate: swap to your bow (Q), hold click to draw, aim at it and let go.",
                    "Bramblemaw's power is yours too: Frost Lance (V to select it, C to cast). Rest at Lantern Shrines to spend gleams on levels."};
            case 3 -> new String[]{"The Ember stone is yours, and so are bombs (press X to pick them, F to use them).",
                    "Ansel the fisher spoke of an old altar on the east coast, buried in rubble, that once sang to the sea. Bomb the rubble and touch the altar to learn its song.",
                    "Then play that song at the Tidewell water gate nearby. The gold marker will lead you."};
            case 7 -> new String[]{"Three Wardstones, yet the shadow over the Hollow Crown still holds. Something is missing.",
                    "Elder Maren will know. Or go straight north past the Rootdeep woods to the rangers' outpost in the snow. The gold marker shows the way.",
                    "Your claw works out here too now: grapple the gold-lit posts on cliffs and sea stacks, yank foes to you, and snatch things that are out of reach."};
            case 15 -> new String[]{"All four Wardstones! The shadow over the Hollow Crown has broken.", "Its gate stands beside the Cinder Forge. The Hollow King waits below. Follow the marker."};
            default -> null;
        };
        if (text != null) g.talk("What next?", null, text);
    }

    void exitDungeon() {
        int k = g.dungeonIndex;
        g.dungeon = null;
        g.dungeonIndex = -1;
        g.level = g.field;
        g.boss = null;
        g.shots.clear();
        g.bombs.clear();
        g.drops.clear();
        g.clawOut = false;
        g.spawnFieldFoes();
        Places.Gate gate = g.world.places.gates.get(k == 4 ? Places.FROST : Math.max(0, k));
        float x = gate.x, z = gate.z - 8;
        g.hero.place(x, g.world.height(x, z), z);
        g.hero.yaw = g.camYaw = M.PI;
        g.camPitch = -.25f;
        g.fade = 1;
        save();
        if (g.gear.stones != 0) whatNext();
    }

    /** Soulslike: your gleams stay where you fell, and you wake at the last shrine you rested at. */
    void continueAfterDeath() {
        Hero h = g.hero;
        g.lostLevel = g.dungeonIndex;
        g.lostX = h.x;
        g.lostY = h.y;
        g.lostZ = h.z;
        g.lostGleams = g.gear.gleams;
        g.gear.gleams = 0;
        g.screen = Game.Screen.PLAY;
        g.overT = 0;
        if (g.boss != null) g.foes.remove(g.boss);
        g.boss = null;
        float[] at = respawn;
        if (at == null) {
            Places pl = g.world.places;
            at = new float[]{-1, pl.spawnX, g.world.height(pl.spawnX, pl.spawnZ), pl.spawnZ};
        }
        if (at[0] >= 0) enterDungeon((int) at[0]);
        else if (g.dungeon != null) exitDungeon();
        else g.spawnFieldFoes();
        h.place(at[1], at[2], at[3]);
        refill();
        h.invuln = 2;
        h.anim.restart(null, 0);
        if (g.lostGleams > 0) g.tip("lost", "Your gleams lie where you fell, glowing green. Reach them before you fall again or they're gone for good.");
    }

    private void refill() {
        Hero h = g.hero;
        g.refreshStats();
        h.hp = h.maxHp;
        h.stamina = h.maxStamina;
        h.fp = h.maxFp;
        g.gear.flasks = g.gear.maxFlasks;
    }

    // ---- Lantern Shrines ----------------------------------------------------------------------

    /** Resting heals and refills everything, wakes the monsters, and remembers this spot. id < 0 is a dungeon shrine. */
    void rest(int id, float x, float y, float z) {
        refill();
        respawn = new float[]{g.dungeonIndex, x + 1.2f, y, z - 1.2f};
        if (id >= 0) flags.add("shrine:" + id);
        if (g.dungeon != null) {
            float hx = g.hero.x, hy = g.hero.y, hz = g.hero.z, yaw = g.hero.yaw;
            enterDungeon(g.dungeonIndex);
            g.hero.place(hx, hy, hz);
            g.hero.yaw = yaw;
            g.fade = 0;
        } else g.spawnFieldFoes();
        g.sfx(Audio.REST);
        g.screen = Game.Screen.REST;
        g.menuSel = 0;
        g.hero.pose(Hero.State.POSE, null);
        save();
    }

    static int levelCost(int level) { return 40 + level * 30; }

    String[] restOptions() {
        return g.dungeon == null ? new String[]{"Level up", "Travel", "Leave"} : new String[]{"Level up", "Leave"};
    }

    void restMenu() {
        String[] opts = restOptions();
        if (menuNav(opts.length)) return;
        if (!g.in.any(KeyEvent.VK_E, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) return;
        switch (opts[g.menuSel]) {
            case "Level up" -> {
                g.screen = Game.Screen.LEVEL;
                g.menuSel = 0;
            }
            case "Travel" -> {
                g.screen = Game.Screen.TRAVEL;
                g.menuSel = 0;
            }
            default -> leaveRest();
        }
    }

    private void leaveRest() {
        g.screen = Game.Screen.PLAY;
        g.hero.setState(Hero.State.MOVE);
        g.hero.anim.play(null, .2f);
    }

    /** W/S moves the selection; Esc backs out. Returns true when the menu was closed. */
    private boolean menuNav(int count) {
        if (g.in.any(KeyEvent.VK_W, KeyEvent.VK_UP)) g.menuSel = Math.floorMod(g.menuSel - 1, count);
        if (g.in.any(KeyEvent.VK_S, KeyEvent.VK_DOWN)) g.menuSel = Math.floorMod(g.menuSel + 1, count);
        if (g.in.pressed(KeyEvent.VK_ESCAPE)) {
            if (g.screen == Game.Screen.REST) leaveRest();
            else {
                g.screen = Game.Screen.REST;
                g.menuSel = 0;
            }
            return true;
        }
        return false;
    }

    int stat(int i) {
        Game.Gear gr = g.gear;
        return switch (i) {
            case 0 -> gr.vig;
            case 1 -> gr.end;
            case 2 -> gr.mnd;
            case 3 -> gr.str;
            default -> gr.arc;
        };
    }

    void levelMenu() {
        if (menuNav(STATS.length)) return;
        if (!g.in.any(KeyEvent.VK_E, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) return;
        Game.Gear gr = g.gear;
        int cost = levelCost(gr.level);
        if (gr.gleams < cost) {
            g.sfx(Audio.CLICK);
            return;
        }
        gr.gleams -= cost;
        gr.level++;
        switch (g.menuSel) {
            case 0 -> gr.vig++;
            case 1 -> gr.end++;
            case 2 -> gr.mnd++;
            case 3 -> gr.str++;
            default -> gr.arc++;
        }
        refill();
        g.sfx(Audio.LEVEL);
        save();
    }

    /** Discovered field shrines, as indices into Places.shrines. */
    java.util.List<Integer> travelSpots() {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (int i = 0; i < g.world.places.shrines.size(); i++) if (flags.contains("shrine:" + i)) list.add(i);
        return list;
    }

    static final String[] SHRINE_NAMES = {"Brindle", "Rootdeep road", "Forge road", "Tidewell shore", "Below the mountain", "Old altar", "Rimeholt mesa", "Rangers' outpost"};

    void travelMenu() {
        java.util.List<Integer> spots = travelSpots();
        if (menuNav(spots.size())) return;
        if (spots.isEmpty() || !g.in.any(KeyEvent.VK_E, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) return;
        float[] sh = g.world.places.shrines.get(spots.get(g.menuSel));
        Hero h = g.hero;
        h.place(sh[0] + 1.2f, g.world.height(sh[0] + 1.2f, sh[2] - 1.2f), sh[2] - 1.2f);
        respawn = new float[]{-1, h.x, h.y, h.z};
        if (g.horse.summoned) g.horse.call(g);
        g.fade = 1;
        leaveRest();
        save();
    }

    // ---- objectives and hints ------------------------------------------------------------------

    // ---- dev shortcuts ------------------------------------------------------------------------

    /** Dev key (`): completes whatever the current objective is, granting what it would have given. */
    void devSkip() {
        Game.Gear gear = g.gear;
        Places pl = g.world.places;
        String did;
        if (!flags.contains("intro")) {
            gear.sword = gear.shield = gear.flute = true;
            gear.spells |= 1;
            gear.songs |= KINDLING | STEED | DAWN;
            flags.add("intro");
            did = "intro (sword, shield, flute, Ember Bolt, songs)";
        } else if (g.dungeon != null) {
            int k = g.dungeonIndex;
            completeDungeon(k);
            did = k == 3 ? "the Hollow King" : g.dungeon.spec.name();
            if (k != 3) exitDungeon();
        } else if ((gear.stones & 1) == 0) {
            completeDungeon(0);
            did = "Rootdeep Hollow";
        } else if ((gear.stones & 2) == 0 && !pl.gates.get(Places.CINDER).open) {
            pl.gates.get(Places.CINDER).open = true;
            did = "forge gate opened";
        } else if ((gear.stones & 2) == 0) {
            completeDungeon(1);
            did = "Cinder Forge";
        } else if ((gear.stones & 4) == 0 && (gear.songs & TIDES) == 0 && pl.boulders.get(1)[3] > 0) {
            bombedField(pl.boulders.get(1)[0], pl.boulders.get(1)[2]);
            did = "altar rubble cleared";
        } else if ((gear.stones & 4) == 0 && (gear.songs & TIDES) == 0) {
            gear.songs |= TIDES;
            did = "learned the Song of Tides";
        } else if ((gear.stones & 4) == 0 && !pl.gates.get(Places.TIDE).open) {
            pl.gates.get(Places.TIDE).open = true;
            did = "Tidewell gate opened";
        } else if ((gear.stones & 4) == 0) {
            completeDungeon(2);
            did = "Tidewell Vault";
        } else if ((gear.stones & 8) == 0 && !flags.contains("hessa")) {
            flags.add("hessa");
            did = "met Hessa at the rangers' outpost";
        } else if ((gear.stones & 8) == 0) {
            completeDungeon(4);
            did = "Rimeholt";
        } else {
            completeDungeon(3);
            did = "the Hollow King";
        }
        g.refreshStats();
        g.hero.hp = g.hero.maxHp;
        save();
        g.say("DEV skip: " + did);
    }

    /** Marks dungeon k as beaten and hands over its rewards. */
    private void completeDungeon(int k) {
        Game.Gear gear = g.gear;
        if (g.dungeons[k] == null) g.dungeons[k] = new Dungeon(Dungeons.ALL[k]);
        Dungeon d = g.dungeons[k];
        d.bossDead = d.bigKey = true;
        for (Dungeon.Thing t : d.things) {
            if (t.kind == 'w') t.gone = true;
            if ("SLBXIcC".indexOf(t.kind) >= 0) {
                t.on = true;
                t.hidden = false;
                t.anim = 1;
            }
        }
        if (k == 3) {
            gear.songs |= KINDLING | TIDES | DAWN | STEED;
            if (g.boss != null) g.foes.remove(g.boss);
            g.boss = null;
            g.screen = Game.Screen.END;
            g.overT = 0;
            g.audio.music(Audio.TITLE);
            return;
        }
        switch (k) {
            case 0 -> {
                gear.bow = gear.bowOut = true;
                gear.arrows = Math.max(gear.arrows, 20);
                gear.spells |= 1 << Magic.FROST;
            }
            case 1 -> {
                gear.bombBag = true;
                gear.bombs = Math.max(gear.bombs, 10);
                gear.item = 1;
                gear.spells |= 1 << Magic.THUNDER;
                g.world.places.gates.get(Places.CINDER).open = true;
            }
            case 2 -> {
                gear.claw = true;
                gear.spells |= 1 << Magic.MEND;
                gear.songs |= TIDES;
                g.world.places.gates.get(Places.TIDE).open = true;
            }
            default -> {
                gear.longclaw = gear.frostbrand = true;
                if (quest(2) == 1) setQuest(2, 2);
            }
        }
        gear.stones |= Dungeons.stoneBit(k);
        gear.hearts++;
        gear.maxFlasks = Math.min(7, gear.maxFlasks + 1);
        gear.flasks = gear.maxFlasks;
        if (Integer.bitCount(gear.stones) == 4) {
            gear.ascended = true;
            gear.spells |= 1 << Magic.STARFALL;
        }
    }

    /** Dev key (=): teleports to the current objective. */
    void devJump() {
        float[] p = new float[3];
        if (!objectiveTarget(p)) return;
        if (g.horse.ridden) g.horse.dismount(g);
        float x = p[0], z = p[2] - (g.dungeon == null ? 6 : 2.5f);
        g.hero.place(x, Math.max(g.level.floor(x, z), -2), z);
        g.hero.yaw = g.camYaw = 0;
        g.fade = 1;
        g.say("DEV jump to the current objective");
    }

    /** Where the current goal is, for the on-screen marker and the pillar of light. */
    boolean objectiveTarget(float[] out) {
        Game.Gear gear = g.gear;
        Places pl = g.world.places;
        if (g.dungeon != null) {
            Dungeon d = g.dungeon;
            for (Dungeon.Thing t : d.things) {
                if (d.bossDead && t.kind == 'w' && !t.gone && !t.hidden) return put(out, t.x, t.y + 1.6f, t.z);
                if (!d.bossDead && d.bigKey && t.kind == 'B' && !t.on) return put(out, t.x, 2.5f, t.z);
                if (!d.bigKey && (t.kind == 'c' || t.kind == 'C') && "bigkey".equals(t.content) && !t.on) return put(out, t.x, t.y + 1.2f, t.z);
            }
            return !d.bossDead && put(out, d.bossX, d.bossY + 3, d.bossZ);
        }
        if (!flags.contains("intro")) {
            Places.Npc elder = pl.npcs.get(0);
            return put(out, elder.x(), elder.y() + 2.3f, elder.z());
        }
        if ((gear.stones & 7) == 7 && (gear.stones & 8) == 0) {
            if (!flags.contains("hessa")) {
                Places.Npc hessa = pl.npcs.get(pl.npcs.size() - 1);
                return put(out, hessa.x(), hessa.y() + 2.3f, hessa.z());
            }
            Places.Gate fg = pl.gates.get(Places.FROST);
            float[] post = pl.posts.get(0);
            return g.hero.y < fg.y - 3 ? put(out, post[0], post[1] + 2.5f, post[2]) : put(out, fg.x, fg.y + 2.5f, fg.z - 5);
        }
        int gate = (gear.stones & 1) == 0 ? Places.ROOT : (gear.stones & 2) == 0 ? Places.CINDER
                : (gear.stones & 4) == 0 ? ((gear.songs & TIDES) == 0 ? Places.ALTAR : Places.TIDE) : Places.CROWN;
        if (gate == Places.CROWN && g.dungeons[3] != null && g.dungeons[3].bossDead) return false;
        Places.Gate gt = pl.gates.get(gate);
        if (gate == Places.CINDER && !gt.open) return put(out, gt.x, gt.y + 8.6f, gt.z);
        return put(out, gt.x, gt.y + 2.5f, gt.z - 5);
    }

    private static boolean put(float[] o, float x, float y, float z) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
        return true;
    }

    /** "north-east, 420 m" from the hero to a point. */
    String direction(float[] p) {
        Hero h = g.hero;
        float dx = p[0] - h.x, dz = p[2] - h.z;
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        int k = Math.floorMod(Math.round(M.atan2(dx, dz) / (M.TAU / 8)), 8);
        return names[k] + ", " + (int) M.hypot(dx, dz) + " m";
    }

    /** The one-line goal shown on screen. */
    String objective() {
        Game.Gear gear = g.gear;
        if (!flags.contains("intro")) return "Talk to Elder Maren (purple robe) in Brindle";
        if (g.dungeon != null) {
            Dungeon d = g.dungeon;
            if (g.dungeonIndex == 3) return d.bossDead ? "The Hollow is broken" : "Defeat the Hollow King";
            if (d.bossDead) return "Take the " + d.spec.stoneName();
            return d.bigKey ? "Open the great door and defeat the master of " + d.spec.name() : "Find the Great Key  (small keys: " + d.keys + ")";
        }
        Places pl = g.world.places;
        if ((gear.stones & 1) == 0) return "Go to Rootdeep Hollow, the giant tree up the north-west road";
        if ((gear.stones & 2) == 0) return pl.gates.get(1).open ? "Enter the Cinder Forge and find the Ember Wardstone"
                : "Shoot the glowing lantern above the Cinder Forge gate (bow: Q, hold click)";
        if ((gear.stones & 4) == 0) {
            if ((gear.songs & TIDES) == 0) return pl.boulders.get(1)[3] > 0 ? "Bomb the rubble at the old altar on the east coast (F lights a bomb)"
                    : "Touch the old altar on the east coast to learn its song";
            return pl.gates.get(2).open ? "Enter the Tidewell Vault and find the Tide Wardstone" : "Play the Song of Tides at the Tidewell water gate (T, then 5 4 2 1 2)";
        }
        if ((gear.stones & 8) == 0) return !flags.contains("hessa") ? "Find the rangers' outpost in the northern snows, past the Rootdeep woods"
                : g.hero.y < pl.gates.get(Places.FROST).y - 3 ? "Climb to Rimeholt: grapple the post on the cliff edge" : "Enter Rimeholt and find the Wardstone of Frost";
        return g.dungeons[3] != null && g.dungeons[3].bossDead ? "Explore Kestrel Isle" : "Descend into the Hollow Crown beside the forge";
    }

    /** H: a nudge for exactly where you are. */
    void hint() {
        String text;
        Dungeon d = g.dungeon;
        if (g.boss != null && g.boss.alive()) text = BOSS_TIPS[g.boss.kind.ordinal() - Foe.Kind.BRAMBLE.ordinal()];
        else if (d != null) {
            int i = d.ti(g.hero.x), j = d.tj(g.hero.z), k = g.dungeonIndex;
            text = roomHint(k, i, j, d);
        } else {
            float[] p = new float[3];
            String where = objectiveTarget(p) ? "  It's " + direction(p) + " from you: follow the gold marker and the pillar of light." : "";
            text = objective() + "." + where + switch (objective().charAt(0)) {
                case 'S' -> "  At the gate, swap to the bow (Q), hold click to draw, aim up at the glowing lantern, and let go.";
                case 'B' -> "  Pick bombs with X, press F to light one and F again to throw it at the rubble.";
                case 'P' -> "  Stand by the water curtain, press T, then play 5 4 2 1 2.";
                case 'C' -> "  Press X until the top-right panel shows the Grapple Claw, then face the glinting post up on the cliff edge, hold F, and let go when the gold ring locks on.";
                case 'F' -> "  Take the road north from Rootdeep Hollow's tree. Juniper gets you there fast (T, then 2 4 5 4 2). Hessa wears a green cloak.";
                default -> "  Juniper gets you there fast: play the Song of the Steed (T, then 2 4 5 4 2).";
            };
        }
        g.talk("Hint", null, text);
    }

    private String roomHint(int k, int i, int j, Dungeon d) {
        if (d.bossDead) return "Take the Wardstone in the master's chamber to leave.";
        if (k == 0) {
            if (j >= 26) return "Two cold torches beside a sealed door. Play the Song of Kindling (T, then 1 3 5 4 3) or hit them with Ember Bolts.";
            if (i <= 8) return "Walk into the low ledge to climb it. Something glints across the pit: hit the crystal from range (bow or Ember Bolt).";
            if (i >= 28) return "Push the stone block onto the plate: walk into it and keep pushing. Line it up from the right side.";
            if (j <= 14) return "The great door needs this dungeon's Great Key. It's hidden in the east room.";
            return "The west door opens once every monster here is beaten. The north door needs a small key from the west room.";
        }
        if (k == 1) {
            if (j >= 26) return "A crystal glints up on the high ledge. Shoot it.";
            if (i <= 8) return "The shield-bearer blocks from the front. Strike after it thrusts, or lock on (Shift) and side-hop around it.";
            if (i >= 28) return "There's a small key in here.";
            if (j <= 14) return "Beat the imps and the Great Key will appear.";
            return "Light BOTH torches on the far side of the lava and a bridge rises in the middle. Stand at the edge and play the Song of Kindling (T, then 1 3 5 4 3), or face each torch and hit it with an Ember Bolt (C). The cracked wall to the east breaks with bombs.";
        }
        if (k == 2) {
            if (j >= 26) return "The door is held shut by water. Play the Song of Tides (T, then 5 4 2 1 2).";
            if (j <= 14) return "Press X until the top-right panel shows the Grapple Claw, then face the glinting wooden post across the pit and hold F. A gold ring snaps onto the post; let go and you zip across.";
            return d.flooded ? "Two chests: one on the island, one on the pool floor. Swim out to the island and keep swimming into its edge to climb out. For the pool-floor chest, drain the water with the Song of Tides (T, then 5 4 2 1 2)."
                    : "With the water gone you can walk the pool floor to its chest, and climb back out at the edges. Flood it again (Song of Tides) to swim to the island.";
        }
        if (k == 4) {
            if (j >= 26) return "Ice seals the north door. Melt it with fire: an Ember Bolt (C), the Song of Kindling (T, then 1 3 5 4 3), or a bomb.";
            if (j >= 16 && i <= 8) return "Frostfang wolves circle, then crouch and lunge. Roll or block when one crouches, then strike. Beat them all and a chest appears.";
            if (j >= 16 && i >= 28) return "Things slide on ice until they hit something. Push the block so it skids to a stop on the plate: line it up first, then shove.";
            if (j >= 16) return "Grapple across the pit using the posts (X to pick the claw, hold F, let go). The doors east and west lead to the rest of the hold.";
            if (j >= 11) return "Wolves on the ice. Let them come to you: they skid past when they miss a lunge.";
            return "The great door needs Rimeholt's Great Key, hidden in the west room.";
        }
        return BOSS_TIPS[3];
    }

    /** A Frostfang fell: counts towards Hessa's hunt once she has asked. */
    void wolfDown() {
        if (quest(3) != 1) return;
        g.gear.wolves++;
        if (g.gear.wolves >= 6) {
            setQuest(3, 2);
            g.say("Six wolves! Report to Hessa at the outpost.");
        } else g.say("Wolves hunted: " + g.gear.wolves + " of 6");
    }

    /** Where side quest q wants you next (for the blue markers), or false when it's idle or done. */
    boolean questTarget(int q, float[] out) {
        Places pl = g.world.places;
        int st = quest(q);
        if (st == 0 || st == 3 || g.dungeon != null) return false;
        String giver = new String[]{"child", "fisher", "smith", "ranger"}[q];
        if (st == 2) {
            for (Places.Npc n : pl.npcs) if (n.id().equals(giver)) return put(out, n.x(), n.y() + 2.3f, n.z());
            return false;
        }
        String item = q == 0 ? "kite" : q == 1 ? "rod" : null;
        if (item != null)
            for (java.util.List<Places.Pickup> list : pl.pickups)
                for (Places.Pickup p : list) if (item.equals(p.id) && !p.taken) return put(out, p.x, p.y + .8f, p.z);
        if (q == 2) {
            Places.Gate fg = pl.gates.get(Places.FROST);
            return put(out, fg.x, fg.y + 2.5f, fg.z);
        }
        return q == 3 && put(out, pl.outpostX, g.world.height(pl.outpostX, pl.outpostZ) + 3, pl.outpostZ);
    }

    /** One-time tips that teach systems the moment they matter. */
    private void tutorials() {
        Hero h = g.hero;
        Places.Gate forge = g.world.places.gates.get(Places.CINDER);
        if (g.dungeon == null && !forge.open && g.gear.bow && M.dist(h.x, h.z, forge.x, forge.z) < 45)
            g.tip("lantern", "See the glowing lantern above the gate? Swap to your bow (Q), hold click to draw, aim at the lantern (the gold marker sits on it) and let go.");
        for (Foe f : g.foes)
            if (f.alive() && !f.boss && M.dist(f.x, f.z, h.x, h.z) < 10) {
                g.tip("lock", "Hold Shift to lock on. While locked on, Space side-hops (A/D) or back-flips (S).");
                break;
            }
        if (h.hp <= h.maxHp / 2 && h.hp > 0) g.tip("draught", "Press R to drink an Ember Draught. Resting at a Lantern Shrine refills them.");
        if (g.dungeon != null && g.gear.claw)
            for (Dungeon.Thing t : g.dungeon.things)
                if (t.kind == 'g' && M.dist(t.x, t.z, h.x, h.z) < 12)
                    g.tip("claw", "A glinting wooden post across the pit! Press X to pick the Grapple Claw, face the post and hold F (a gold ring locks on), then let go to zip over.");
        if (h.stamina < 8) g.tip("stamina", "Rolling, swinging and blocking tire you out. Let the green bar recover.");
        if (g.boss != null && g.boss.alive() && (bossClock += 1 / 60f) > 22)
            g.tip("boss" + g.boss.kind, BOSS_TIPS[g.boss.kind.ordinal() - Foe.Kind.BRAMBLE.ordinal()]);
    }

    // ---- saving -------------------------------------------------------------------------------

    void save() {
        if (!saving) return;
        Properties p = new Properties();
        Game.Gear gr = g.gear;
        int[] nums = {gr.sword ? 1 : 0, gr.shield ? 1 : 0, gr.flute ? 1 : 0, gr.bow ? 1 : 0, gr.bombBag ? 1 : 0, gr.claw ? 1 : 0,
                gr.arrows, gr.bombs, gr.gleams, gr.pieces, gr.songs, gr.stones, gr.item, g.hero.maxHp, g.hero.hp,
                gr.hearts, gr.level, gr.vig, gr.end, gr.mnd, gr.str, gr.arc, gr.swordLv, gr.maxFlasks, gr.spells, gr.spell, gr.ascended ? 1 : 0,
                gr.frostbrand ? 1 : 0, gr.longclaw ? 1 : 0, gr.quests, gr.wolves};
        StringBuilder sb = new StringBuilder();
        for (int v : nums) sb.append(v).append(',');
        p.setProperty("gear", sb.toString());
        p.setProperty("flags", String.join(",", flags));
        p.setProperty("hour", Float.toString(g.climate.hour));
        StringBuilder gates = new StringBuilder();
        for (Places.Gate gt : g.world.places.gates) gates.append(gt.open ? '1' : '0');
        p.setProperty("gates", gates.toString());
        for (int k = 0; k < g.dungeons.length; k++) if (g.dungeons[k] != null) p.setProperty("d" + k, g.dungeons[k].saveState());
        if (respawn != null) p.setProperty("respawn", respawn[0] + "," + respawn[1] + "," + respawn[2] + "," + respawn[3]);
        p.setProperty("lost", g.lostLevel + "," + g.lostX + "," + g.lostY + "," + g.lostZ + "," + g.lostGleams);
        try {
            if (SAVE.getParent() != null) Files.createDirectories(SAVE.getParent());
            try (Writer w = Files.newBufferedWriter(SAVE)) {
                p.store(w, "Kestrel Isle save");
            }
        } catch (IOException e) {
            g.say("Couldn't save: " + e.getMessage());
        }
    }

    void load() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(SAVE)) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        String[] v = p.getProperty("gear", "").split(",");
        if (v.length < 15) return;
        int[] n = new int[v.length];
        for (int i = 0; i < v.length; i++) n[i] = Integer.parseInt(v[i]);
        Game.Gear gr = g.gear;
        gr.sword = n[0] == 1;
        gr.shield = n[1] == 1;
        gr.flute = n[2] == 1;
        gr.bow = n[3] == 1;
        gr.bombBag = n[4] == 1;
        gr.claw = n[5] == 1;
        gr.arrows = n[6];
        gr.bombs = n[7];
        gr.gleams = n[8];
        gr.pieces = n[9];
        gr.songs = n[10];
        gr.stones = n[11];
        gr.item = n[12];
        if (n.length >= 27) {
            gr.hearts = n[15];
            gr.level = n[16];
            gr.vig = n[17];
            gr.end = n[18];
            gr.mnd = n[19];
            gr.str = n[20];
            gr.arc = n[21];
            gr.swordLv = n[22];
            gr.maxFlasks = n[23];
            gr.spells = n[24];
            gr.spell = n[25];
            gr.ascended = n[26] == 1;
            if (n.length >= 31) {
                gr.frostbrand = n[27] == 1;
                gr.longclaw = n[28] == 1;
                gr.quests = n[29];
                gr.wolves = n[30];
            }
        } else gr.hearts = n[13] / 4;
        refill();
        String rs = p.getProperty("respawn");
        if (rs != null) {
            String[] q = rs.split(",");
            respawn = new float[]{Float.parseFloat(q[0]), Float.parseFloat(q[1]), Float.parseFloat(q[2]), Float.parseFloat(q[3])};
        }
        String[] lost = p.getProperty("lost", "-9,0,0,0,0").split(",");
        g.lostLevel = (int) Float.parseFloat(lost[0]);
        g.lostX = Float.parseFloat(lost[1]);
        g.lostY = Float.parseFloat(lost[2]);
        g.lostZ = Float.parseFloat(lost[3]);
        g.lostGleams = (int) Float.parseFloat(lost[4]);
        for (String f : p.getProperty("flags", "").split(",")) if (!f.isEmpty()) flags.add(f);
        g.climate.hour = Float.parseFloat(p.getProperty("hour", "9"));
        String gates = p.getProperty("gates", "");
        for (int i = 0; i < gates.length() && i < g.world.places.gates.size(); i++) g.world.places.gates.get(i).open = gates.charAt(i) == '1';
        for (int k = 0; k < g.dungeons.length; k++) {
            String s = p.getProperty("d" + k);
            if (s == null) continue;
            g.dungeons[k] = new Dungeon(Dungeons.ALL[k]);
            g.dungeons[k].loadState(s);
        }
        java.util.List<float[]> bs = g.world.places.boulders;
        for (int i = 0; i < bs.size(); i++) if (flags.contains("boulder" + i)) bs.get(i)[3] = 0;
        for (java.util.List<Places.Pickup> list : g.world.places.pickups)
            for (Places.Pickup pk : list) if (pk.id != null && flags.contains("p:" + pk.id)) pk.taken = true;
        if ((gr.songs & STEED) != 0) g.horse.call(g);
        if (respawn != null) {
            if (respawn[0] >= 0) enterDungeon((int) respawn[0]);
            g.hero.place(respawn[1], respawn[2], respawn[3]);
        }
        g.say("Welcome back to Kestrel Isle.");
        if (gr.stones != 0 && g.dungeon == null) whatNext();
    }
}

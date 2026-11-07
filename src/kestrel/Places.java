package kestrel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Everything placed on the island: scenery, pickups, the village, paths and the dungeon gates. */
final class Places {
    static final int TREE = 0, PINE = 1, SNOWPINE = 2, BUSH = 3, ROCK = 4, CACTUS = 5, DEADTREE = 6, REDROCK = 7,
            DARKROCK = 8, FLOWERS = 9, COLLIDER = 10;
    static final int G1 = 0, G5 = 1, G20 = 2, HEART = 3, ARROWS = 4, BOMBS = 5, PIECE = 6, KITE = 7, ROD = 8;
    static final int ROOT = 0, CINDER = 1, TIDE = 2, CROWN = 3, ALTAR = 4, FROST = 5, SAND = 6;

    record Prop(int type, float x, float y, float z, float yaw, float scale, float radius, float top) {}

    record Box(float x0, float z0, float x1, float z1) {}

    record Building(Mesh mesh, float x, float y, float z, float yaw) {}

    /** Someone who lives on the island. Life walks them about: around home by day, back home at night. */
    static final class Npc {
        private final String id;
        private final Rig rig;
        float x, y, z, yaw, walk, phase, wait, tx, tz;
        /** Hit points; while down they're out cold (or, for townsfolk, dead) until downT runs out. fleeT: running scared. */
        float hp = 4, downT, fleeT, flash, robT;
        final float hx, hz, hyaw, wander;
        /** Townsfolk have a name of their own and only chatter; the island's named folk have stories. */
        final boolean folk;
        final String name;

        Npc(String id, Rig rig, float x, float y, float z, float yaw, float wander) { this(id, null, rig, x, y, z, yaw, wander); }

        Npc(String id, String name, Rig rig, float x, float y, float z, float yaw, float wander) {
            this.id = id;
            this.name = name;
            folk = name != null;
            this.rig = rig;
            this.x = this.hx = this.tx = x;
            this.y = y;
            this.z = this.hz = this.tz = z;
            this.yaw = this.hyaw = yaw;
            this.wander = wander;
        }

        String id() { return id; }

        Rig rig() { return rig; }

        float x() { return x; }

        float y() { return y; }

        float z() { return z; }

        float yaw() { return yaw; }
    }

    record Sign(float x, float y, float z, float yaw, String text) {}

    /** A town on the map: its name, its middle and how far its streets reach. */
    record Town(String name, float x, float z, float radius) {}

    static final class Pickup {
        final int item;
        final float x, y, z;
        final String id;
        boolean taken;

        Pickup(int item, float x, float y, float z, String id) {
            this.item = item;
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
        }
    }

    static final class Gate {
        final int kind;
        final float x, y, z, yaw;
        boolean open;

        Gate(int kind, float x, float y, float z, float yaw) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            open = kind == ROOT || kind == FROST || kind == SAND;
        }
    }

    private final World w;
    private final List<float[]> reserved = new ArrayList<>();
    @SuppressWarnings("unchecked") final List<Prop>[] props = new List[World.CHUNKS * World.CHUNKS];
    @SuppressWarnings("unchecked") final List<Pickup>[] pickups = new List[World.CHUNKS * World.CHUNKS];
    final List<Building> buildings = new ArrayList<>();
    final List<Box> boxes = new ArrayList<>();
    /** The boxes again, filed by chunk, so a floor test only looks at buildings nearby. */
    @SuppressWarnings("unchecked") private final List<Box>[] boxGrid = new List[World.CHUNKS * World.CHUNKS];
    final List<Npc> npcs = new ArrayList<>();
    /** Townsfolk, and then everyone: the named islanders followed by the townsfolk. */
    final List<Npc> folk = new ArrayList<>(), everyone = new ArrayList<>();
    final List<Town> towns = new ArrayList<>();
    /** Job boards {x, y, z, town, yaw}; homes for sale {door x, y, z, yaw, town}; carriage stands {x, y, z, yaw}, named in stops. */
    final List<float[]> boards = new ArrayList<>(), homes = new ArrayList<>(), carriages = new ArrayList<>();
    final List<String> stops = new ArrayList<>();
    /** The sea floor's furnishings, {kind (KELP..PILLAR), x, y, z, yaw, scale}, and fish schools {x, y, z, radius}. */
    final List<float[]> reef = new ArrayList<>(), schools = new ArrayList<>();
    static final int KELP = 0, CORAL = 1, WRECK = 2, PILLAR = 3;
    final List<Sign> signs = new ArrayList<>();
    final List<Gate> gates = new ArrayList<>();
    final List<float[]> fires = new ArrayList<>();
    final List<float[]> foes = new ArrayList<>();
    final List<float[]> boulders = new ArrayList<>();
    /** Lantern Shrines: Brindle first, then one beside each gate, in gate order. */
    final List<float[]> shrines = new ArrayList<>();
    /** Blast Pods outside: {x, y, z, ripe (1/0), regrow timer}. */
    final List<float[]> pods = new ArrayList<>();
    /** Grapple posts out on the island, {x, ground y, z}: the claw bites the ring on top. */
    final List<float[]> posts = new ArrayList<>();
    /** A treasure chest out in the world; opened chests are remembered by id in the save. */
    static final class Chest {
        final String id;
        final float x, y, z, yaw;
        float anim;

        Chest(String id, float x, float y, float z, float yaw) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }

    final List<Chest> chests = new ArrayList<>();
    /** Hot-air vents in the desert, {x, y, z}: the Windcloak rides them high. */
    final List<float[]> vents = new ArrayList<>();
    /** Tents at the rangers' outpost: {x, y, z, yaw}. */
    final List<float[]> tents = new ArrayList<>();
    /** Sheer rock tables only the claw can climb: {x, z, radius, top}. */
    private final List<float[]> mesas = new ArrayList<>();
    final float spawnX, spawnZ, villageX, villageZ, outpostX, outpostZ, campX, campZ;

    Places(World w, long seed) {
        this.w = w;
        for (int i = 0; i < props.length; i++) {
            props[i] = new ArrayList<>();
            pickups[i] = new ArrayList<>();
        }
        float[] v = settle(770, 330, 4, .3f);
        villageX = v[0];
        villageZ = v[1];
        float vy = Math.max(3, w.height(villageX, villageZ));
        flatten(villageX, villageZ, 46, vy);

        float[] root = gateSpot(430, 1120, ROOT), cinder = gateSpot(1024, 690, CINDER), tide = gateSpot(1840, 880, TIDE);
        float[] crown = gateSpot(1130, 700, CROWN), altar = gateSpot(1760, 760, ALTAR);
        // Gate clearings first, so the roads are graded to meet them.
        for (Gate g : gates) flatten(g.x, g.z, 9, g.y);
        path(villageX, villageZ + 30, 600, 760, root[0], root[1] - 9);
        path(villageX + 30, villageZ + 10, 900, 560, cinder[0], cinder[1] - 9);
        path(cinder[0] + 10, cinder[1] - 12, crown[0], crown[1] - 9);
        path(villageX + 40, villageZ, 1300, 420, 1650, 620, tide[0] - 9, tide[1]);
        path(1650, 620, altar[0] - 6, altar[1] - 6);

        // The frozen north, past the Rootdeep woods: the rangers' outpost, and Rimeholt on a sheer mesa.
        float[] camp0 = settle(420, 1490, 6, .3f);
        outpostX = camp0[0];
        outpostZ = camp0[1];
        path(root[0] + 16, root[1] - 4, 470, 1260, 430, 1390, outpostX, outpostZ - 10);
        // Level the camp to where the road arrives, so the road walks straight in rather than meeting a shelf.
        flatten(outpostX, outpostZ, 12, Math.max(3, w.height(outpostX, outpostZ - 10)));
        path(outpostX + 4, outpostZ + 10, 445, 1548);
        float top = mesa(445, 1590, 18, 8.5f);
        gates.add(new Gate(FROST, 445, top, 1590, 0));
        posts.add(new float[]{445, top, 1590 - 16.8f});
        signs.add(new Sign(448.5f, w.height(448.5f, 1564), 1564, 0, "RIMEHOLT, above.  The cliff is sheer: the old Keepers came and went by claw."));
        Gate frostGate = gates.get(FROST);
        flatten(frostGate.x, frostGate.z, 9, frostGate.y);

        village(vy);
        shrine(villageX - 7, villageZ - 12);
        for (Gate g : gates) shrine(g.x + 7, g.z - 12);
        outpost();

        // The deep desert: Sela's nomad camp, the Sunscour Temple, and hot-air vents beside mesas only a glider can top.
        float[] nomads = settle(1450, 480, 4, .3f);
        campX = nomads[0];
        campZ = nomads[1];
        float[] sun = gateSpot(1600, 380, SAND);
        Gate sunGate = gates.get(SAND);
        flatten(sunGate.x, sunGate.z, 9, sunGate.y);
        path(campX + 6, campZ - 9, sun[0] - 3, sun[1] - 10);
        shrine(sunGate.x + 7, sunGate.z - 12);
        camp();
        float[][] tops = {{1454, 622}, {1638, 470}, {1342, 310}};
        for (int k = 0; k < tops.length; k++) {
            float[] t = tops[k];
            ventBeside(t[0], t[1]);
            if (k == 0) addPickup(PIECE, t[0], t[1], "piece-mesa");
            else for (int i = 0; i < 3; i++) addPickup(G20, t[0] + M.sin(i * 2.1f) * 2.5f, t[1] + M.cos(i * 2.1f) * 2.5f, "mesa" + k + "-" + i);
        }
        vents.add(new float[]{sunGate.x - 9, w.height(sunGate.x - 9, sunGate.z - 4), sunGate.z - 4});
        ventBeside(1518, 830);
        chest("starweave", 1518, 830, 0);
        spawnX = villageX;
        spawnZ = villageZ - 14;

        // Optional treasures: heart pieces in a sealed cave, a flower grove and a thornling camp.
        float[] cave = settle(1260, 1180, 4, .35f);
        boulders.add(new float[]{cave[0], w.height(cave[0], cave[1]), cave[1], 1});
        addPickup(PIECE, cave[0] + 3, cave[1] + 1, "piece-cave");
        boulders.add(new float[]{altar[0], w.height(altar[0], altar[1] - 4.5f), altar[1] - 4.5f, 1});
        for (float[] b : boulders)
            for (int k = 0; k < 2; k++) {
                float px = b[0] + (k == 0 ? 5 : -5), pz = b[2] - 4;
                pods.add(new float[]{px, w.height(px, pz), pz, 1, 0});
            }
        float[] grove = settle(560, 980, 4, .3f);
        addPickup(PIECE, grove[0], grove[1], "piece-grove");
        for (int i = 0; i < 6; i++) addProp(FLOWERS, grove[0] + M.sin(i) * 5, grove[1] + M.cos(i) * 5, i, 1.2f);
        float[] camp = settle(980, 420, 4, .3f);
        fires.add(new float[]{camp[0], w.height(camp[0], camp[1]), camp[1]});
        addPickup(PIECE, camp[0] + 3, camp[1] + 3, "piece-camp");
        for (int i = 0; i < 3; i++) foes.add(new float[]{0, camp[0] + M.sin(i * 2.1f) * 5, camp[1] + M.cos(i * 2.1f) * 5});
        for (float[] f : new float[][]{{520, 860}, {380, 1300}, {1240, 520}, {1500, 1000}, {700, 1200}}) {
            float[] p = settle(f[0], f[1], 3, .4f);
            foes.add(new float[]{0, p[0], p[1]});
            foes.add(new float[]{0, p[0] + 4, p[1] + 3});
        }
        for (float[] f : new float[][]{{1000, 820}, {1150, 760}, {900, 760}}) {
            float[] p = settle(f[0], f[1], 3, .5f);
            foes.add(new float[]{4, p[0], p[1]});
        }

        // Wolf packs roam the snowfields.
        for (float[] f : new float[][]{{360, 1430}, {545, 1470}, {330, 1585}}) {
            float[] p = settle(f[0], f[1], 3, .45f);
            for (int i = 0; i < 3; i++) foes.add(new float[]{5, p[0] + M.sin(i * 2.1f) * 4, p[1] + M.cos(i * 2.1f) * 4});
        }
        // Things only the claw can reach: Tibb's kite in the great oak, Ansel's rod atop a spire, treasure on sea stacks.
        float ox = villageX - 31, oz = villageZ + 27;
        bigTree(ox, oz, 1.8f);
        addPickup(KITE, ox + 3.4f, w.height(ox, oz) + 6.6f, oz - 1.6f, "kite");
        float[] sp = settle(352, 1528, 4, .4f);
        float st = mesa(sp[0], sp[1], 3.4f, 7);
        posts.add(new float[]{sp[0] - .6f, st, sp[1] - .6f});
        addPickup(ROD, sp[0] + 1.3f, st, sp[1] + .9f, "rod");
        stacks.add(new float[]{700, 212, 6});
        stacks.add(new float[]{706, 199, 8});
        stacks.add(new float[]{722, 177, 11});
        for (float[] s : stacks) {
            float t = mesa(s[0], s[1], 3, s[2]);
            posts.add(new float[]{s[0], t, s[1]});
        }
        addPickup(G20, 707.2f, 199.9f, "stack-g");
        addPickup(PIECE, 723.3f, 177.9f, "piece-stack");
        chest("dye-gold", 720.9f, 176.1f, M.PI);
        chest("dye-snow", 452, 1597, M.PI);
        chest("gleams-grove", grove[0] + 3, grove[1] - 2.5f, 2);
        towns.add(new Town("Brindle", villageX, villageZ, 112));
        town(villageX, villageZ, 52, 112, 26, new Mesh[]{Models.MARKET, Models.CHURCH, Models.WINDMILL, Models.SMITHY, Models.MARKET}, 22, 4, seed);
        float[] kr = settle(600, 750, 4, .35f);
        kingsreach(kr[0], kr[1], seed);
        reefs(seed);
        scatter(seed);
        everyone.addAll(npcs);
        everyone.addAll(folk);
        float size = World.CHUNK * World.CELL;
        for (int i = 0; i < boxGrid.length; i++) boxGrid[i] = new ArrayList<>();
        for (Box b : boxes)
            for (int j = (int) (b.z0() / size); j <= (int) (b.z1() / size); j++)
                for (int i = (int) (b.x0() / size); i <= (int) (b.x1() / size); i++)
                    if (i >= 0 && j >= 0 && i < World.CHUNKS && j < World.CHUNKS) boxGrid[i + j * World.CHUNKS].add(b);
        // Later flattening may have softened a mesa's edge into a ramp; cut them sheer again.
        for (float[] m : mesas) stamp(m);
    }

    // ---- towns ----------------------------------------------------------------------------------

    private static final String[] FOLK_NAMES = {"Abel", "Brannoc", "Cressa", "Dunstan", "Elsbet", "Fenwick", "Gilda", "Harrow", "Ilse", "Jory",
            "Kestra", "Lorcan", "Mabry", "Nell", "Odran", "Perrin", "Quilla", "Roswen", "Saoirse", "Tamsin", "Ulric", "Verity", "Wystan",
            "Yseult", "Aldous", "Bryony", "Cormac", "Delphine", "Edric", "Fennel", "Garrick", "Hollis", "Ione", "Jessamy", "Kit", "Linnet",
            "Merrow", "Nesbit", "Oriel", "Pippa", "Rook", "Sorrel", "Thane", "Una", "Wren", "Bramwell", "Cosima", "Dorian", "Ember", "Fable",
            "Godric", "Hester", "Iver", "Juniper", "Kerra", "Lowan", "Morwen", "Nye", "Osric", "Prue", "Rhosyn", "Silas"};

    /** Kingsreach, the royal city: the castle in the middle, barracks and towers, and the guard who keep the king's peace. */
    private void kingsreach(float cx, float cz, long seed) {
        float y = Math.max(2, w.height(cx, cz));
        flatten(cx, cz, 26, y);
        building(Models.CASTLE, cx, y, cz, M.PI, 10.5f);
        towns.add(new Town("Kingsreach", cx, cz, 140));
        for (int i = 0; i < 4; i++) {
            float a = i * M.TAU / 4 + M.PI / 4;
            fires.add(new float[]{cx + M.sin(a) * 20, y, cz + M.cos(a) * 20});
        }
        signs.add(new Sign(cx + 3, y, cz - 24, 0, "KINGSREACH, the royal city.  Keep the king's peace: the royal guard hunts down anyone who breaks it."));
        town(cx, cz, 40, 140, 42, new Mesh[]{Models.BARRACKS, Models.CHURCH, Models.MARKET, Models.MARKET, Models.SMITHY, Models.SHOP, Models.TOWER,
                Models.TOWER, Models.BARRACKS, Models.WINDMILL, Models.TOWER, Models.TOWER, Models.MARKET}, 40, 10, seed * 7 + 1);
        for (int i = 0; i < 4; i++) {
            float a = i * M.TAU / 4;
            foes.add(new float[]{Foe.Kind.GUARD.ordinal(), cx + M.sin(a) * 14, cz + M.cos(a) * 14});
        }
    }

    /**
     * Grows a town around (cx, cz): two ring streets and six spokes, then the landmarks and homes along both sides of the rings, each on a
     * levelled pad of its own, facing the street. Last come the people: townsfolk living in the homes, and the guard walking the streets.
     */
    private void town(float cx, float cz, float r0, float r1, int houses, Mesh[] landmarks, int people, int watch, long seed) {
        Random rnd = new Random(seed);
        float[] rings = {r0 + (r1 - r0) * .3f, r1 - 8};
        // Only where there's land: a ring that meets the sea breaks into separate streets rather than wading out as a causeway.
        for (float R : rings) {
            List<Float> run = new ArrayList<>();
            for (int k = 0; k <= 48; k++) {
                float a = k * M.TAU / 48, x = cx + M.sin(a) * R, z = cz + M.cos(a) * R;
                if (w.height(x, z) > 1.2f) {
                    run.add(x);
                    run.add(z);
                }
                if ((w.height(x, z) <= 1.2f || k == 48) && run.size() >= 6) {
                    float[] pts = new float[run.size()];
                    for (int i = 0; i < pts.length; i++) pts[i] = run.get(i);
                    path(pts);
                }
                if (w.height(x, z) <= 1.2f) run.clear();
            }
        }
        for (int k = 0; k < 6; k++) {
            float a = k * M.TAU / 6 + .3f, end = r0 * .7f;
            while (end < r1 && w.height(cx + M.sin(a) * (end + 4), cz + M.cos(a) * (end + 4)) > 1.2f) end += 4;
            if (end > r0) path(cx + M.sin(a) * r0 * .7f, cz + M.cos(a) * r0 * .7f, cx + M.sin(a) * end, cz + M.cos(a) * end);
        }
        // Building plots on a loose grid, nearest the middle first so the town stays close-knit; each faces the middle.
        List<float[]> plots = new ArrayList<>();
        for (float gx = -r1; gx <= r1; gx += 12)
            for (float gz = -r1; gz <= r1; gz += 12) {
                float d = M.hypot(gx, gz);
                if (d >= r0 && d <= r1) plots.add(new float[]{cx + gx + (rnd.nextFloat() - .5f) * 3, cz + gz + (rnd.nextFloat() - .5f) * 3, d});
            }
        plots.sort((p, q) -> Float.compare(p[2], q[2]));
        // A job board beside the first spoke road near the middle, and the carriage stand a little further out along it.
        int ti = towns.size() - 1;
        float ba = .3f, br = r0 * .8f, bx = cx + M.sin(ba) * br + M.cos(ba) * 3.5f, bz = cz + M.cos(ba) * br - M.sin(ba) * 3.5f;
        boards.add(new float[]{bx, w.height(bx, bz), bz, ti, ba});
        reserved.add(new float[]{bx, bz, 3});
        float sx = cx + M.sin(ba) * (br + 10) + M.cos(ba) * 5, sz = cz + M.cos(ba) * (br + 10) - M.sin(ba) * 5;
        carriages.add(new float[]{sx, w.height(sx, sz), sz, ba});
        stops.add(towns.get(ti).name());
        reserved.add(new float[]{sx, sz, 4});
        List<float[]> doors = new ArrayList<>();
        int placed = 0, want = landmarks.length + houses;
        for (float[] plot : plots) {
            if (placed >= want) break;
            float x = plot[0], z = plot[1];
            Mesh mesh = placed < landmarks.length ? landmarks[placed] : rnd.nextFloat() < .1f ? Models.SHOP : Models.HOUSE;
            float size = footprint(mesh);
            if (!buildable(x, z, size)) continue;
            float yaw = M.atan2(cx - x, cz - z);
            flatten(x, z, size, Math.max(1.5f, w.height(x, z)));
            building(mesh, x, w.height(x, z), z, yaw, size);
            if (mesh == Models.HOUSE || mesh == Models.SHOP) doors.add(new float[]{x + M.sin(yaw) * (size + 1.5f), z + M.cos(yaw) * (size + 1.5f), yaw});
            if (mesh == Models.HOUSE && (homes(ti) == 0)) {
                // The town's first house is for sale.
                float dx = x + M.sin(yaw) * (size + .8f), dz = z + M.cos(yaw) * (size + .8f);
                this.homes.add(new float[]{dx, w.height(dx, dz), dz, yaw, ti});
                signs.add(new Sign(dx + M.cos(yaw) * 1.6f, w.height(dx, dz), dz - M.sin(yaw) * 1.6f, yaw, "FOR SALE: this fine house, " + Jobs.HOME_PRICE + " gleams. Sleep here as at a shrine."));
            }
            placed++;
        }
        for (int i = 0; i < people && !doors.isEmpty(); i++) {
            float[] d = doors.get(rnd.nextInt(doors.size()));
            int n = folk.size();
            folk.add(new Npc("folk" + n, FOLK_NAMES[n % FOLK_NAMES.length], Characters.FOLK[n % Characters.FOLK.length], d[0], w.height(d[0], d[1]), d[1],
                    d[2], 10 + rnd.nextFloat() * 16));
        }
        for (int i = 0; i < watch; i++) {
            float a = i * M.TAU / watch + rnd.nextFloat() * .3f, R = rings[i % 2], x = cx + M.sin(a) * R, z = cz + M.cos(a) * R;
            if (w.height(x, z) > 1.2f) foes.add(new float[]{Foe.Kind.GUARD.ordinal(), x, z});
        }
    }

    private int homes(int town) {
        int n = 0;
        for (float[] hm : homes) if (hm[4] == town) n++;
        return n;
    }

    /** How far a town building reaches from its middle (its levelled pad's radius). */
    private static float footprint(Mesh m) {
        if (m == Models.BARRACKS || m == Models.CHURCH) return 6;
        if (m == Models.TOWER) return 3;
        return 4.3f;
    }

    /** Dry ground, not too steep to level, clear of roads and of everything else already placed. */
    private boolean buildable(float x, float z, float size) {
        if (near(x, z, size * .6f) || w.height(x, z) < 1.5f || w.biomeAt(x, z) == World.PATH) return false;
        float lo = 1e9f, hi = -1e9f;
        for (int k = 0; k < 8; k++) {
            float a = k * M.TAU / 8, px = x + M.sin(a) * size, pz = z + M.cos(a) * size, h = w.height(px, pz);
            if (h < 1 || k % 2 == 0 && w.biomeAt(x + M.sin(a) * size * .8f, z + M.cos(a) * size * .8f) == World.PATH) return false;
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        return hi - lo < 7;
    }

    /** A town building with a square collider a little inside its footprint. */
    private void building(Mesh mesh, float x, float y, float z, float yaw, float size) {
        buildings.add(new Building(mesh, x, y, z, yaw));
        float hx = mesh == Models.CASTLE ? 9.5f : mesh == Models.TOWER ? 2 : mesh == Models.MARKET ? 1.4f : size * .66f;
        boxes.add(new Box(x - hx, z - hx, x + hx, z + hx));
    }

    // ---- under the sea --------------------------------------------------------------------------

    /** Reefs in the shallows: kelp forests and coral, pearls, fish, and here and there a wreck or sunken ruin with a chest to find. */
    private void reefs(long seed) {
        Random rnd = new Random(seed ^ 0x5ea5eaL);
        int made = 0;
        for (int tries = 0; tries < 6000 && made < 16; tries++) {
            // The first reefs are searched for off Brindle's shore, so there's always one close to home.
            boolean home = tries < 800;
            float x = home ? villageX + (rnd.nextFloat() - .5f) * 700 : 60 + rnd.nextFloat() * (World.EXTENT - 120);
            float z = home ? villageZ + (rnd.nextFloat() - .5f) * 700 : 60 + rnd.nextFloat() * (World.EXTENT - 120), y = w.height(x, z);
            if (home && made >= 2 || y > -7 || y < -24) continue;
            boolean close = false, coast = false;
            for (float[] r : schools) close |= M.dist(x, z, r[0], r[2]) < 140;
            for (int k = 0; k < 8 && !coast; k++)
                for (float d = 20; d < 160; d += 20) coast |= w.height(x + M.sin(k * M.TAU / 8) * d, z + M.cos(k * M.TAU / 8) * d) > 1;
            if (close || !coast) continue;
            for (int i = 0; i < 34; i++) {
                float a = rnd.nextFloat() * M.TAU, d = 3 + rnd.nextFloat() * 24, px = x + M.sin(a) * d, pz = z + M.cos(a) * d, py = w.height(px, pz);
                if (py > -2 || py < -40) continue;
                reef.add(new float[]{rnd.nextFloat() < .55f ? KELP : CORAL, px, py, pz, rnd.nextFloat() * M.TAU, .7f + rnd.nextFloat() * .8f});
            }
            for (int i = 0; i < 2; i++) {
                float a = rnd.nextFloat() * M.TAU, px = x + M.sin(a) * 10, pz = z + M.cos(a) * 10;
                if (w.height(px, pz) < -2) addPickup(G20, px, w.height(px, pz) + .3f, pz, "pearl" + made + "-" + i);
            }
            if (made % 2 == 0) {
                float yaw = rnd.nextFloat() * M.TAU, tilt = made % 4 == 0 ? WRECK : PILLAR;
                reef.add(new float[]{tilt, x, y - .3f, z, yaw, 1});
                if (tilt == PILLAR)
                    for (int i = 0; i < 4; i++) {
                        float a = yaw + i * M.TAU / 4, px = x + M.sin(a) * 6, pz = z + M.cos(a) * 6;
                        reef.add(new float[]{PILLAR, px, w.height(px, pz), pz, a, .8f + rnd.nextFloat() * .4f});
                    }
                float cx = x + M.sin(yaw) * 3.5f, cz = z + M.cos(yaw) * 3.5f;
                chests.add(new Chest((tilt == WRECK ? "wreck" : "ruin") + made, cx, w.height(cx, cz), cz, yaw));
            }
            schools.add(new float[]{x, y + 3 + rnd.nextFloat() * 3, z, 5 + rnd.nextFloat() * 5});
            made++;
        }
    }

    /** Sea stacks off Brindle's shore, {x, z, height above the sea}, each crowned with a grapple post. */
    private final List<float[]> stacks = new ArrayList<>();

    /** Hessa's rangers keep a cold camp on the road north: a shrine, tents, a fire and a warning sign. */
    private void outpost() {
        float x = outpostX, z = outpostZ, y = w.height(x, z);
        shrine(x - 8, z + 6);
        fires.add(new float[]{x, y, z});
        for (int i = 0; i < 3; i++) {
            float a = i * 2.1f + .4f, tx = x + M.sin(a) * 7.5f, tz = z + M.cos(a) * 7.5f;
            tents.add(new float[]{tx, y, tz, a + M.PI});
            collider(tx, tz, 2, 2.6f);
        }
        npcs.add(new Npc("ranger", Characters.RANGER, x + 2.5f, y, z - 2.2f, M.PI, 6));
        carriages.add(new float[]{x + 9, w.height(x + 9, z + 3), z + 3, 0});
        stops.add("Rangers' outpost");
        signs.add(new Sign(x + 4.5f, y, z - 10, 0, "RANGERS' OUTPOST  -  Mind the wolves.  Rimeholt stands north, atop the mesa."));
    }

    private void chest(String id, float x, float z, float yaw) {
        chests.add(new Chest(id, x, w.height(x, z), z, yaw));
        reserved.add(new float[]{x, z, 2});
    }

    /** Sela's nomads camp among the dunes: tents, a fire, a shrine and a sign pointing the way. */
    private void camp() {
        float x = campX, z = campZ, y = Math.max(3, w.height(x, z));
        flatten(x, z, 11, y);
        shrine(x - 7, z + 6);
        fires.add(new float[]{x, y, z});
        for (int i = 0; i < 3; i++) {
            float a = i * 2.1f + 1.2f, tx = x + M.sin(a) * 7, tz = z + M.cos(a) * 7;
            tents.add(new float[]{tx, y, tz, a + M.PI});
            collider(tx, tz, 2, 2.6f);
        }
        npcs.add(new Npc("nomad", Characters.NOMAD, x + 2.4f, y, z - 2.4f, M.PI, 4));
        carriages.add(new float[]{x + 9, y, z + 3, 0});
        stops.add("Nomad camp");
        signs.add(new Sign(x + 4.5f, y, z - 9, 0, "SUNSCOUR NOMADS  -  Water, shade and stories.  The Sunscour Temple lies south-east."));
    }

    /** A hot-air vent at the foot of the mesa whose flat top is at (x, z), on whichever side drops away soonest. */
    private void ventBeside(float x, float z) {
        float top = w.height(x, z), best = 1e9f, bx = x, bz = z;
        for (int a = 0; a < 16; a++) {
            float dx = M.sin(a * M.TAU / 16), dz = M.cos(a * M.TAU / 16);
            for (float r = 4; r < 60; r += 1) {
                if (w.height(x + dx * r, z + dz * r) > top - 22) continue;
                if (r < best) {
                    best = r;
                    bx = x + dx * (r + 2.5f);
                    bz = z + dz * (r + 2.5f);
                }
                break;
            }
        }
        vents.add(new float[]{bx, w.height(bx, bz), bz});
        reserved.add(new float[]{bx, bz, 4});
    }

    /** Raises a sheer-sided rock table that nothing can walk up; returns the height of its top. */
    private float mesa(float x, float z, float rad, float rise) {
        float top = World.WATER;
        for (int a = 0; a < 32; a++) top = Math.max(top, w.height(x + M.sin(a * M.TAU / 32) * (rad + 5), z + M.cos(a * M.TAU / 32) * (rad + 5)));
        float[] m = {x, z, rad, top + rise};
        mesas.add(m);
        stamp(m);
        reserved.add(new float[]{x, z, rad + 3});
        return m[3];
    }

    private void stamp(float[] m) {
        int r = (int) (m[2] / World.CELL) + 2, ci = (int) (m[0] / World.CELL), cj = (int) (m[1] / World.CELL);
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++)
                if (i >= 0 && j >= 0 && i <= World.SIZE && j <= World.SIZE && M.dist(i * World.CELL, j * World.CELL, m[0], m[1]) < m[2]) w.h[i + j * World.V] = m[3];
    }

    /** A great tree, solid to walk into, with a canopy well out of reach. */
    private void bigTree(float x, float z, float scale) {
        int ci = (int) M.clamp(x / (World.CHUNK * World.CELL), 0, World.CHUNKS - 1);
        int cj = (int) M.clamp(z / (World.CHUNK * World.CELL), 0, World.CHUNKS - 1);
        props[ci + cj * World.CHUNKS].add(new Prop(TREE, x, w.height(x, z) - .15f, z, .7f, scale, .45f * scale, 9 * scale));
        reserved.add(new float[]{x, z, 5});
    }

    private float[] gateSpot(float x, float z, int kind) {
        float[] p = settle(x, z, 4, .35f);
        float y = Math.max(2, w.height(p[0], p[1]));
        gates.add(new Gate(kind, p[0], y, p[1], 0));
        reserved.add(new float[]{p[0], p[1], 14});
        return p;
    }

    private void village(float vy) {
        float x = villageX, z = villageZ;
        house(Models.HOUSE, x - 15, z + 14, 0);
        house(Models.SHOP, x + 1, z + 20, 0);
        house(Models.HOUSE, x + 16, z + 13, 0);
        house(Models.HOUSE, x - 20, z - 8, M.PI / 2);
        house(Models.HOUSE, x + 21, z - 7, -M.PI / 2);
        buildings.add(new Building(Models.WELL, x, vy, z, 0));
        collider(x, z, 1.3f, 3);
        for (int i = -4; i <= 4; i++) {
            if (Math.abs(i) <= 1) continue;
            buildings.add(new Building(Models.FENCE, x + i * 4, vy, z - 30, 0));
        }
        npcs.add(new Npc("elder", Characters.ELDER, x - 15, vy, z + 9.6f, M.PI, 9));
        npcs.add(new Npc("merchant", Characters.MERCHANT, x + 1, vy, z + 21.2f, M.PI, 0));
        npcs.add(new Npc("smith", Characters.SMITH, x + 16, vy, z + 8.6f, M.PI, 2.5f));
        npcs.add(new Npc("child", Characters.CHILD, x + 4, vy, z - 3, -2.4f, 17));
        npcs.add(new Npc("fisher", Characters.FISHER, x - 9, vy, z - 18, .6f, 5));
        signs.add(new Sign(x + 2.5f, vy, z - 29, 0, "BRINDLE  -  north-west: Rootdeep Hollow  /  north-east: the mountain"));
        fires.add(new float[]{x - 6, vy, z + 2});
        for (int i = 0; i < 5; i++) addPickup(G1, x - 10 + i * 5, z - 22, "v-g" + i);
    }

    private void shrine(float x, float z) {
        flatten(x, z, 2.5f, Math.max(1, w.height(x, z)));
        shrines.add(new float[]{x, w.height(x, z), z});
        collider(x, z, .5f, 3);
    }

    private void house(Mesh mesh, float x, float z, float yaw) {
        float y = w.height(x, z);
        buildings.add(new Building(mesh, x, y, z, yaw));
        boolean side = Math.abs(M.sin(yaw)) > .5f;
        float hx = side ? 2.7f : 3.2f, hz = side ? 3.2f : 2.7f;
        boxes.add(new Box(x - hx, z - hz, x + hx, z + hz));
        reserved.add(new float[]{x, z, 6});
    }

    private void addPickup(int item, float x, float z, String id) { addPickup(item, x, w.height(x, z), z, id); }

    private void addPickup(int item, float x, float y, float z, String id) {
        int ci = (int) M.clamp(x / (World.CHUNK * World.CELL), 0, World.CHUNKS - 1);
        int cj = (int) M.clamp(z / (World.CHUNK * World.CELL), 0, World.CHUNKS - 1);
        pickups[ci + cj * World.CHUNKS].add(new Pickup(item, x, y, z, id));
    }

    private void addProp(int type, float x, float z, float yaw, float scale) {
        int ci = (int) M.clamp(x / (World.CHUNK * World.CELL), 0, World.CHUNKS - 1);
        int cj = (int) M.clamp(z / (World.CHUNK * World.CELL), 0, World.CHUNKS - 1);
        props[ci + cj * World.CHUNKS].add(new Prop(type, x, w.height(x, z), z, yaw, scale, 0, 0));
    }

    private void collider(float x, float z, float r, float top) {
        int ci = (int) M.clamp(x / (World.CHUNK * World.CELL), 0, World.CHUNKS - 1);
        int cj = (int) M.clamp(z / (World.CHUNK * World.CELL), 0, World.CHUNKS - 1);
        props[ci + cj * World.CHUNKS].add(new Prop(COLLIDER, x, w.height(x, z), z, 0, 1, r, top));
    }

    /** Lays a gently graded dirt road along a polyline, cutting through hills and causewaying water. */
    private void path(float... pts) {
        List<float[]> s = new ArrayList<>();
        for (int k = 0; k + 3 < pts.length; k += 2) {
            float x0 = pts[k], z0 = pts[k + 1], x1 = pts[k + 2], z1 = pts[k + 3];
            int n = Math.max(1, (int) (M.dist(x0, z0, x1, z1) / 2));
            for (int i = 0; i < n; i++) s.add(new float[]{M.lerp(x0, x1, i / (float) n), M.lerp(z0, z1, i / (float) n)});
        }
        int n = s.size();
        float[] hs = new float[n], tmp = new float[n];
        for (int i = 0; i < n; i++) hs[i] = Math.max(.7f, w.height(s.get(i)[0], s.get(i)[1]));
        for (int pass = 0; pass < 4; pass++) {
            for (int i = 0; i < n; i++) {
                float sum = 0;
                int c = 0;
                for (int k = Math.max(0, i - 6); k <= Math.min(n - 1, i + 6); k++, c++) sum += hs[k];
                tmp[i] = sum / c;
            }
            System.arraycopy(tmp, 0, hs, 0, n);
        }
        // Pin both ends to the ground they start and finish on, so a road meets a camp or a village level, not as a mound or a trench.
        float first = Math.max(.7f, w.height(s.get(0)[0], s.get(0)[1])), last = Math.max(.7f, w.height(s.get(n - 1)[0], s.get(n - 1)[1]));
        for (int i = 0; i < Math.min(8, n / 2); i++) {
            hs[i] = M.lerp(first, hs[i], i / 8f);
            hs[n - 1 - i] = M.lerp(last, hs[n - 1 - i], i / 8f);
        }
        // Never steeper than 1 in 4: cut through rises and build up over dips.
        for (int i = 1; i < n; i++) hs[i] = M.clamp(hs[i], hs[i - 1] - .5f, hs[i - 1] + .5f);
        for (int i = n - 2; i >= 0; i--) hs[i] = M.clamp(hs[i], hs[i + 1] - .5f, hs[i + 1] + .5f);
        for (int i = 0; i < n; i++) {
            float x = s.get(i)[0], z = s.get(i)[1];
            flatten(x, z, 3, Math.max(.7f, hs[i]));
            int ci = (int) (x / World.CELL), cj = (int) (z / World.CELL);
            for (int j = cj - 1; j <= cj + 2; j++)
                for (int k = ci - 1; k <= ci + 2; k++)
                    if (k >= 0 && j >= 0 && k <= World.SIZE && j <= World.SIZE && M.dist(k * World.CELL, j * World.CELL, x, z) < 2.6f)
                        w.biome[k + j * World.V] = World.PATH;
        }
    }

    private void flatten(float x, float z, float rad, float y) {
        int r = (int) ((rad + 6) / World.CELL) + 1, ci = (int) (x / World.CELL), cj = (int) (z / World.CELL);
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++) {
                if (i < 0 || j < 0 || i > World.SIZE || j > World.SIZE) continue;
                int k = i + j * World.V;
                w.h[k] = M.lerp(w.h[k], y, M.smooth(rad + 6, rad, M.dist(i * World.CELL, j * World.CELL, x, z)));
            }
        reserved.add(new float[]{x, z, rad + 2});
    }

    private boolean near(float x, float z, float margin) {
        for (float[] r : reserved) if (M.dist(x, z, r[0], r[1]) < r[2] + margin) return true;
        return false;
    }

    /** Nearest dry, gentle spot to (x, z), searching outwards. */
    float[] settle(float x, float z, float minH, float maxSlope) {
        for (int r = 0; r < 500; r += 6)
            for (int a = 0; a < (r == 0 ? 1 : 24); a++) {
                float px = x + M.sin(a * M.TAU / 24) * r, pz = z + M.cos(a * M.TAU / 24) * r;
                if (w.height(px, pz) > minH && w.slope(px, pz) < maxSlope && !near(px, pz, 4)) return new float[]{px, pz};
            }
        return new float[]{x, z};
    }

    private void scatter(long seed) {
        float size = World.CHUNK * World.CELL;
        for (int cj = 0; cj < World.CHUNKS; cj++)
            for (int ci = 0; ci < World.CHUNKS; ci++) {
                Random r = new Random(seed * 31 + ci * 1013L + cj * 7L);
                List<Prop> list = props[ci + cj * World.CHUNKS];
                for (int a = 0; a < 30; a++) {
                    float x = (ci + r.nextFloat()) * size, z = (cj + r.nextFloat()) * size, y = w.height(x, z);
                    float s = w.slope(x, z), q = r.nextFloat(), yaw = r.nextFloat() * M.TAU, sc = .75f + r.nextFloat() * .6f;
                    int b = w.biomeAt(x, z);
                    if (y < World.WATER + 1.2f || b == World.PATH || near(x, z, 3)) continue;
                    float dens = w.n.get(x * .012f + 7, z * .012f) + .15f;
                    int type = -1;
                    if (s > .75f) {
                        if (q < .05f) type = b == World.DESERT ? REDROCK : b == World.ASH ? DARKROCK : ROCK;
                    } else switch (b) {
                        case World.FOREST -> type = dens > -.25f ? (q < .55f ? PINE : TREE) : q < .3f ? BUSH : -1;
                        case World.MEADOW -> type = dens > .2f && q < .45f ? TREE : q < .07f ? BUSH : q < .1f ? ROCK : q < .14f ? FLOWERS : -1;
                        case World.SNOW -> type = y < 215 && dens > -.1f && q < .5f ? SNOWPINE : q < .06f ? ROCK : -1;
                        case World.DESERT -> type = q < .05f ? CACTUS : q < .1f ? REDROCK : -1;
                        case World.ASH -> type = q < .05f ? DEADTREE : q < .14f ? DARKROCK : -1;
                        default -> {}
                    }
                    if (type < 0) continue;
                    boolean rock = type == ROCK || type == REDROCK || type == DARKROCK;
                    float rad = type == BUSH || type == FLOWERS ? 0 : rock ? 1.3f * sc : .45f * sc;
                    list.add(new Prop(type, x, y - .15f, z, yaw, sc, rad, rock ? 1.6f * sc : 9));
                }
                if (r.nextFloat() < .35f) {
                    float x = (ci + r.nextFloat()) * size, z = (cj + r.nextFloat()) * size;
                    if (w.height(x, z) > 1.5f && !near(x, z, 1)) addPickup(r.nextFloat() < .8f ? G1 : G5, x, z, null);
                }
            }
    }

    /** Pushes a circle at (pos[0], pos[1]) with feet at y out of solid scenery. */
    /** Whether (x, z) is inside a building. */
    boolean inBuilding(float x, float z) {
        float size = World.CHUNK * World.CELL;
        int i = (int) (x / size), j = (int) (z / size);
        if (i < 0 || j < 0 || i >= World.CHUNKS || j >= World.CHUNKS) return false;
        for (Box b : boxGrid[i + j * World.CHUNKS]) if (x > b.x0() && x < b.x1() && z > b.z0() && z < b.z1()) return true;
        return false;
    }

    void collide(float[] pos, float y, float rad) {
        float size = World.CHUNK * World.CELL;
        int ci = (int) (pos[0] / size), cj = (int) (pos[1] / size);
        for (int j = cj - 1; j <= cj + 1; j++)
            for (int i = ci - 1; i <= ci + 1; i++) {
                if (i < 0 || j < 0 || i >= World.CHUNKS || j >= World.CHUNKS) continue;
                for (Prop p : props[i + j * World.CHUNKS]) {
                    if (p.radius() <= 0 || y > p.y() + p.top() || y < p.y() - 2) continue;
                    float dx = pos[0] - p.x(), dz = pos[1] - p.z(), d = M.hypot(dx, dz), min = p.radius() + rad;
                    if (d < min && d > 1e-4f) {
                        pos[0] = p.x() + dx / d * min;
                        pos[1] = p.z() + dz / d * min;
                    }
                }
            }
    }
}

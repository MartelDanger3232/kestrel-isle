package kestrel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Everything placed on the island: scenery, pickups, the village, paths and the dungeon gates. */
final class Places {
    static final int TREE = 0, PINE = 1, SNOWPINE = 2, BUSH = 3, ROCK = 4, CACTUS = 5, DEADTREE = 6, REDROCK = 7,
            DARKROCK = 8, FLOWERS = 9, COLLIDER = 10;
    static final int G1 = 0, G5 = 1, G20 = 2, HEART = 3, ARROWS = 4, BOMBS = 5, PIECE = 6, KITE = 7, ROD = 8;
    static final int ROOT = 0, CINDER = 1, TIDE = 2, CROWN = 3, ALTAR = 4, FROST = 5;

    record Prop(int type, float x, float y, float z, float yaw, float scale, float radius, float top) {}

    record Box(float x0, float z0, float x1, float z1) {}

    record Building(Mesh mesh, float x, float y, float z, float yaw) {}

    /** Someone who lives on the island. Life walks them about: around home by day, back home at night. */
    static final class Npc {
        private final String id;
        private final Rig rig;
        float x, y, z, yaw, walk, phase, wait, tx, tz;
        final float hx, hz, hyaw, wander;

        Npc(String id, Rig rig, float x, float y, float z, float yaw, float wander) {
            this.id = id;
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
            open = kind == ROOT || kind == FROST;
        }
    }

    private final World w;
    private final List<float[]> reserved = new ArrayList<>();
    @SuppressWarnings("unchecked") final List<Prop>[] props = new List[World.CHUNKS * World.CHUNKS];
    @SuppressWarnings("unchecked") final List<Pickup>[] pickups = new List[World.CHUNKS * World.CHUNKS];
    final List<Building> buildings = new ArrayList<>();
    final List<Box> boxes = new ArrayList<>();
    final List<Npc> npcs = new ArrayList<>();
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
    /** Tents at the rangers' outpost: {x, y, z, yaw}. */
    final List<float[]> tents = new ArrayList<>();
    /** Sheer rock tables only the claw can climb: {x, z, radius, top}. */
    private final List<float[]> mesas = new ArrayList<>();
    final float spawnX, spawnZ, villageX, villageZ, outpostX, outpostZ;

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
        scatter(seed);
        // Later flattening may have softened a mesa's edge into a ramp; cut them sheer again.
        for (float[] m : mesas) stamp(m);
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
        signs.add(new Sign(x + 4.5f, y, z - 10, 0, "RANGERS' OUTPOST  -  Mind the wolves.  Rimeholt stands north, atop the mesa."));
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

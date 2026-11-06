package kestrel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A dungeon built from an ASCII map (see Dungeons for the legend): static tile geometry baked into
 * chunk meshes, plus doors, chests, blocks, switches and the rest as live objects.
 */
final class Dungeon implements Level {
    static final float T = 2, WALL = 6;

    static final class Thing {
        final char kind;
        final int index;
        int i, j;
        float x, y, z, anim;
        boolean on, gone, hidden;
        String content;
        Thing(char kind, int i, int j, int index) { this.kind = kind; this.i = i; this.j = j; this.index = index; }
    }

    record Rule(BooleanSupplier when, Runnable then) {}

    final Dungeons.Spec spec;
    final int w, h;
    final char[] tile;
    final float[] fh;
    final boolean[] wet;
    final List<Thing> things = new ArrayList<>();
    final List<Thing> spawns = new ArrayList<>();
    final List<Rule> rules = new ArrayList<>();
    private final boolean[] fired;
    final List<Foe> foes = new ArrayList<>();
    final Mesh[] chunks;
    private final int cw, ch;
    float spawnX, spawnY, spawnZ, bossX, bossY, bossZ;
    boolean flooded = true, bossStarted, bossDead;
    int keys;
    boolean bigKey;

    Dungeon(Dungeons.Spec spec) {
        this.spec = spec;
        h = spec.map().length;
        int wid = 0;
        for (String row : spec.map()) wid = Math.max(wid, row.length());
        w = wid;
        tile = new char[w * h];
        fh = new float[w * h];
        wet = new boolean[w * h];
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) tile[i + j * w] = i < spec.map()[j].length() ? spec.map()[j].charAt(i) : '#';
        for (int k = 0; k < w * h; k++) {
            fh[k] = baseHeight(tile[k]);
            wet[k] = tile[k] == '~';
        }
        int chest = 0, door = 0, plate = 0, crystal = 0, torch = 0;
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                char c = tile[i + j * w];
                if ("kcCbptToguRP".indexOf(c) >= 0 || "eEfamiWM@w".indexOf(c) >= 0) {
                    float nh = neighbourHeight(i, j);
                    // Something standing in open water sits on the pool bed.
                    wet[i + j * w] = Float.isNaN(nh);
                    fh[i + j * w] = Float.isNaN(nh) ? -2 : nh;
                }
                float x = cx(i), z = cz(j), y = fh[i + j * w];
                switch (c) {
                    case '@' -> { spawnX = x; spawnY = y; spawnZ = z; }
                    case 'M' -> { bossX = x; bossY = y; bossZ = z; }
                    case 'e', 'f', 'a', 'm', 'i', 'W' -> spawns.add(at(new Thing(c, i, j, spawns.size()), x, y, z));
                    case 'c', 'C' -> {
                        Thing t = add(c, i, j, chest, x, y, z);
                        t.content = spec.chests()[chest++];
                        t.hidden = c == 'C';
                    }
                    case 'S', 'L', 'B', 'X', 'I' -> add(c, i, j, c == 'S' ? door++ : -1, x, 0, z);
                    case 'p' -> add(c, i, j, plate++, x, y, z);
                    case 'o' -> add(c, i, j, crystal++, x, y, z);
                    case 't', 'T' -> add('t', i, j, torch++, x, y, z).on = c == 'T';
                    case 'k', 'b', 'g', 'u', 'w', 'R' -> add(c, i, j, 0, x, y, z).hidden = c == 'w';
                    case 'P' -> add(c, i, j, 0, x, y, z).on = true;
                    default -> {}
                }
            }
        spec.rules().accept(this);
        fired = new boolean[rules.size()];
        cw = (w + 7) / 8;
        ch = (h + 7) / 8;
        chunks = new Mesh[cw * ch];
        for (int cj = 0; cj < ch; cj++)
            for (int ci = 0; ci < cw; ci++) chunks[ci + cj * cw] = bake(ci * 8, cj * 8);
    }

    private Thing at(Thing t, float x, float y, float z) {
        t.x = x;
        t.y = y;
        t.z = z;
        return t;
    }

    private Thing add(char kind, int i, int j, int index, float x, float y, float z) {
        Thing t = at(new Thing(kind, i, j, index), x, y, z);
        things.add(t);
        return t;
    }

    static float baseHeight(char c) {
        return switch (c) {
            case '#' -> WALL;
            case ' ' -> -40;
            case '~' -> -2;
            case '^' -> 1.5f;
            case 'H', 'v' -> 4;
            default -> c >= '1' && c <= '8' ? (c - '0') * .5f : 0;
        };
    }

    /** Most common height among walkable neighbours; NaN when surrounded by water. */
    private float neighbourHeight(int i, int j) {
        float best = Float.NaN;
        int bestCount = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int a = i + d[0], b = j + d[1];
            if (a < 0 || b < 0 || a >= w || b >= h) continue;
            char c = tile[a + b * w];
            if (c == '#' || c == ' ' || c == '~' || "SLBXDI".indexOf(c) >= 0) continue;
            float v = baseHeight(c);
            int count = 0;
            for (int[] e : dirs) {
                int p = i + e[0], q = j + e[1];
                if (p >= 0 && q >= 0 && p < w && q < h && baseHeight(tile[p + q * w]) == v) count++;
            }
            if (count > bestCount || count == bestCount && (Float.isNaN(best) || v > best)) {
                best = v;
                bestCount = count;
            }
        }
        return best;
    }

    float cx(int i) { return i * T + T / 2; }

    float cz(int j) { return (h - 1 - j) * T + T / 2; }

    int ti(float x) { return (int) Math.floor(x / T); }

    int tj(float z) { return h - 1 - (int) Math.floor(z / T); }

    char at(int i, int j) { return i < 0 || j < 0 || i >= w || j >= h ? '#' : tile[i + j * w]; }

    // ---- Level --------------------------------------------------------------------------------

    @Override
    public float floor(float x, float z) {
        int i = ti(x), j = tj(z);
        if (i < 0 || j < 0 || i >= w || j >= h) return WALL;
        float f = fh[i + j * w];
        for (Thing t : things) {
            if (t.gone || t.i != i || t.j != j) continue;
            switch (t.kind) {
                case 'S', 'L', 'B', 'X', 'I' -> { if (t.anim < 1) return WALL; }
                case 'b' -> { return f + 2.4f; }
                default -> {}
            }
        }
        for (Thing t : things) {
            if (t.kind != 'b' || t.gone || t.anim <= 0) continue;
            if (ti(t.x) == i && tj(t.z) == j) return f + 2.4f;
        }
        return f;
    }

    @Override
    public float water(float x, float z) {
        int i = ti(x), j = tj(z);
        return flooded && i >= 0 && j >= 0 && i < w && j < h && wet[i + j * w] ? 0 : Float.NaN;
    }

    @Override
    public float maxStep() { return .6f; }

    @Override
    public boolean ledges() { return true; }

    @Override
    public boolean vine(float x, float z) { return at(ti(x), tj(z)) == 'v'; }

    @Override
    public boolean icy(float x, float z) { return at(ti(x), tj(z)) == '-'; }

    /** Fire (Ember Bolts, the Song of Kindling, bombs) melts ice blocks within reach of (x, z). */
    boolean melt(float x, float z, float reach, Game g) {
        boolean any = false;
        for (Thing t : things)
            if (t.kind == 'I' && !t.on && M.dist(x, z, t.x, t.z) < reach) {
                t.on = any = true;
                g.sparks(t.x, 1.5f, t.z, 0xe0f4ff, 24);
                g.sfx(Audio.SPLASH);
            }
        return any;
    }

    @Override
    public void collide(float[] pos, float y, float r) {
        for (Thing t : things) {
            if (t.gone || t.hidden && t.kind != 'w') continue;
            float rad = switch (t.kind) {
                case 'c', 'C' -> .8f;
                case 't', 'o', 'g' -> .35f;
                case 'u' -> .35f;
                case 'R' -> .5f;
                case 'w' -> t.hidden ? 0 : .6f;
                default -> 0;
            };
            if (rad == 0 || Math.abs(y - t.y) > 1.5f) continue;
            float dx = pos[0] - t.x, dz = pos[1] - t.z, d = M.hypot(dx, dz), min = rad + r;
            if (d < min && d > 1e-4f) {
                pos[0] = t.x + dx / d * min;
                pos[1] = t.z + dz / d * min;
            }
        }
    }

    // ---- puzzle helpers used by the rules in Dungeons ------------------------------------------

    Thing find(char kind, int index) {
        for (Thing t : things) if (t.kind == kind && t.index == index) return t;
        throw new IllegalStateException(spec.name() + ": no " + kind + index);
    }

    boolean torchesLit(int... idx) {
        for (int k : idx) if (!find('t', k).on) return false;
        return true;
    }

    boolean plate(int k) { return find('p', k).on; }

    boolean crystal(int k) { return find('o', k).on; }

    /** All foes that started inside the tile rectangle are dead. */
    boolean cleared(int i0, int j0, int i1, int j1) {
        for (Foe f : foes)
            if (f.alive() && f.si >= i0 && f.si <= i1 && f.sj >= j0 && f.sj <= j1) return false;
        return true;
    }

    void rule(BooleanSupplier when, Runnable then) { rules.add(new Rule(when, then)); }

    void openDoor(int k) { find('S', k).on = true; }

    void closeDoor(int k) {
        Thing t = find('S', k);
        t.on = false;
    }

    void reveal(int chest) {
        for (Thing t : things) if ((t.kind == 'C' || t.kind == 'c') && t.index == chest) t.hidden = false;
    }

    /** Turns pit tiles in a rectangle into solid floor (a bridge rises). */
    void bridge(int i0, int j0, int i1, int j1, float y) {
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++)
                if (at(i, j) == ' ') {
                    tile[i + j * w] = '=';
                    fh[i + j * w] = y;
                }
        for (int cj = 0; cj < ch; cj++)
            for (int ci = 0; ci < cw; ci++) chunks[ci + cj * cw] = bake(ci * 8, cj * 8);
    }

    // ---- per-frame ----------------------------------------------------------------------------

    void update(float dt, Game g) {
        Hero p = g.hero;
        for (Thing t : things) {
            switch (t.kind) {
                case 'S' -> t.anim = M.clamp(t.anim + (t.on ? dt : -dt * 3) * 1.4f, 0, 1);
                case 'L', 'B', 'X', 'I' -> { if (t.on) t.anim = Math.min(1, t.anim + dt * 1.4f); }
                case 'c', 'C' -> { if (t.on) t.anim = Math.min(1, t.anim + dt * 2.5f); }
                case 'p' -> {
                    boolean down = M.dist(p.x, p.z, t.x, t.z) < .9f && Math.abs(p.y - t.y) < .5f;
                    for (Thing b : things) if (b.kind == 'b' && M.dist(b.x, b.z, t.x, t.z) < .5f) down = true;
                    if (down && !t.on) g.sfx(Audio.CLICK);
                    t.on = down;
                }
                case 'P' -> {
                    if (!t.on && (t.anim += dt) > 12) {
                        t.on = true;
                        t.anim = 0;
                    }
                    if (t.on && g.gear.bombBag && g.gear.bombs < 30 && M.dist(p.x, p.z, t.x, t.z) < 1.3f && Math.abs(p.y - t.y) < 1.5f) {
                        t.on = false;
                        g.gear.bombs = Math.min(30, g.gear.bombs + 3);
                        g.sfx(Audio.PICKUP);
                        g.say("+3 bombs (the Blast Pod will regrow)");
                    }
                }
                case 'b' -> {
                    if (t.anim > 0) {
                        float tx = cx(t.i), tz = cz(t.j);
                        t.x = M.approach(t.x, tx, 9, dt);
                        t.z = M.approach(t.z, tz, 9, dt);
                        if (M.dist(t.x, t.z, tx, tz) < .02f) {
                            t.x = tx;
                            t.z = tz;
                            t.anim = 0;
                        }
                    }
                }
                default -> {}
            }
        }
        for (int k = 0; k < rules.size(); k++) {
            if (fired[k] || !rules.get(k).when().getAsBoolean()) continue;
            fired[k] = true;
            rules.get(k).then().run();
            g.sfx(Audio.SECRET);
        }
    }

    /** Pushing against a block for a moment slides it one tile, if the far side is clear floor. */
    void push(Game g, float dt) {
        Hero p = g.hero;
        if (p.st != Hero.State.MOVE || !p.moving) {
            p.pushT = Math.max(0, p.pushT - dt);
            return;
        }
        float fx = M.sin(p.yaw), fz = M.cos(p.yaw);
        int di = Math.abs(fx) > Math.abs(fz) ? (int) Math.signum(fx) : 0, dj = di == 0 ? -(int) Math.signum(fz) : 0;
        int pi = ti(p.x + fx * .9f), pj = tj(p.z + fz * .9f);
        for (Thing b : things) {
            if (b.kind != 'b' || b.anim > 0 || b.i != pi || b.j != pj) continue;
            int ni = b.i + di, nj = b.j + dj;
            char c = at(ni, nj);
            boolean clear = "#~ SLBXvI".indexOf(c) < 0 && Math.abs(fh[ni + nj * w] - fh[b.i + b.j * w]) < .1f;
            for (Thing o : things) if (!o.gone && o != b && o.i == ni && o.j == nj && "kcCtoguRP".indexOf(o.kind) >= 0) clear = false;
            if (clear && (p.pushT += dt) > .35f) {
                // On slick ice a pushed block keeps sliding until something stops it.
                while (at(ni, nj) == '-' && free(ni + di, nj + dj, fh[ni + nj * w], b)) {
                    ni += di;
                    nj += dj;
                }
                b.i = ni;
                b.j = nj;
                b.anim = 1;
                p.pushT = 0;
                g.sfx(Audio.PUSH);
            }
            return;
        }
    }

    /** Whether a block could move into tile (i, j) at floor height y. */
    private boolean free(int i, int j, float y, Thing self) {
        char c = at(i, j);
        if ("#~ SLBXvI".indexOf(c) >= 0 || Math.abs(fh[i + j * w] - y) > .1f) return false;
        for (Thing o : things) if (!o.gone && o != self && o.i == i && o.j == j && "kcCtoguRPb".indexOf(o.kind) >= 0) return false;
        return true;
    }

    // ---- geometry -----------------------------------------------------------------------------

    private Mesh bake(int i0, int j0) {
        Mesh m = new Mesh();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int j = j0; j < Math.min(h, j0 + 8); j++)
            for (int i = i0; i < Math.min(w, i0 + 8); i++) {
                char c = tile[i + j * w];
                float y = c == ' ' ? pitY() : fh[i + j * w], x0 = i * T, x1 = x0 + T, z0 = (h - 1 - j) * T, z1 = z0 + T;
                if ("SLBXDI".indexOf(c) >= 0) y = 0;
                int top = wet[i + j * w] ? topColor('~', i, j) : topColor(c, i, j);
                quad(m, x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0, 0, 1, 0, top);
                for (int[] d : dirs) {
                    int ni = i + d[0], nj = j + d[1];
                    char nc = at(ni, nj);
                    float ny = ni < 0 || nj < 0 || ni >= w || nj >= h ? WALL : nc == ' ' ? pitY() : "SLBXDI".indexOf(nc) >= 0 ? 0 : fh[ni + nj * w];
                    if (ny >= y) continue;
                    int side = c == 'v' ? (((i * 7 + j * 3) & 1) == 0 ? 0x3d6a2e : 0x4f7f38) : c == '#' ? spec.wall() : M.scale(spec.wall(), 1.12f);
                    float sx0, sz0, sx1, sz1;
                    if (d[0] == 1) { sx0 = x1; sz0 = z0; sx1 = x1; sz1 = z1; }
                    else if (d[0] == -1) { sx0 = x0; sz0 = z1; sx1 = x0; sz1 = z0; }
                    else if (d[1] == 1) { sx0 = x0; sz0 = z0; sx1 = x1; sz1 = z0; }
                    else { sx0 = x1; sz0 = z1; sx1 = x0; sz1 = z1; }
                    quad(m, sx0, y, sz0, sx1, y, sz1, sx1, ny, sz1, sx0, ny, sz0, d[0], 0, -d[1], side);
                }
            }
        return m;
    }

    /** Lava glows just below the floor; ordinary pits drop into darkness. */
    private float pitY() { return spec.lava() ? -1.2f : -8; }

    private int topColor(char c, int i, int j) {
        float v = 1 + (M.hash(i, j) - .5f) * .12f + ((i + j & 1) == 0 ? .04f : -.02f);
        return switch (c) {
            case '#' -> M.scale(spec.wall(), .55f);
            case ' ' -> spec.lava() ? 0xff5a1a | Mesh.GLOW : 0x050608;
            case '~' -> M.scale(spec.floor(), .55f * v);
            case 'v' -> M.scale(0x4f7f38, v);
            case '-' -> M.scale(0xc4e6f2, v);
            case '^', 'H', '=' -> M.scale(spec.accent(), v);
            default -> c >= '1' && c <= '8' ? M.scale(spec.accent(), v * .95f) : M.scale(spec.floor(), v);
        };
    }

    /** Quad whose winding makes it face along (nx, ny, nz). */
    private static void quad(Mesh m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float nx, float ny, float nz, int col) {
        float ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        float px = uy * vz - uz * vy, py = uz * vx - ux * vz, pz = ux * vy - uy * vx;
        if (px * nx + py * ny + pz * nz >= 0) {
            m.tri(ax, ay, az, bx, by, bz, cx, cy, cz, col);
            m.tri(ax, ay, az, cx, cy, cz, dx, dy, dz, col);
        } else {
            m.tri(ax, ay, az, cx, cy, cz, bx, by, bz, col);
            m.tri(ax, ay, az, dx, dy, dz, cx, cy, cz, col);
        }
    }

    /** Draws the visible chunks, then every live object. */
    void render(Renderer r, Xf xf, float time) {
        xf.identity();
        for (int cj = 0; cj < ch; cj++)
            for (int ci = 0; ci < cw; ci++) {
                float mx = (ci * 8 + 4) * T, mz = (h - 1 - (cj * 8 + 4)) * T + T;
                if (M.dist(mx, mz, r.cx, r.cz) > r.fogEnd + 12 || !r.visible(mx, 2, mz, 13)) continue;
                r.mesh(chunks[ci + cj * cw], xf);
            }
        for (Thing t : things) {
            if (t.gone || t.hidden || M.dist(t.x, t.z, r.cx, r.cz) > r.fogEnd + 4) continue;
            switch (t.kind) {
                case 'I' -> {
                    float k = 1 - t.anim;
                    if (k > .02f) r.mesh(Models.ICE_BLOCK, xf.identity().translate(t.x, 0, t.z).scale(k, k, k), 200);
                }
                case 'S', 'L', 'B', 'X' -> {
                    boolean alongX = "#".indexOf(at(t.i - 1, t.j)) >= 0;
                    xf.identity().translate(t.x, -t.anim * 4.2f, t.z).rotY(alongX ? 0 : M.PI / 2);
                    r.mesh(t.kind == 'X' ? Models.CRACKED : t.kind == 'L' ? Models.DOOR_LOCKED : t.kind == 'B' ? Models.DOOR_BOSS : Models.DOOR, xf);
                }
                case 'c', 'C' -> {
                    xf.identity().translate(t.x, t.y, t.z).rotY(chestYaw(t));
                    r.mesh(t.content.equals("bigkey") ? Models.BIG_CHEST : Models.CHEST, xf);
                    Xf lid = new Xf().set(xf).translate(0, .6f, -.38f).rotX(-1.9f * t.anim);
                    r.mesh(Models.LID, lid);
                }
                case 'k' -> {
                    xf.identity().translate(t.x, t.y + .5f + M.sin(time * 3) * .1f, t.z).rotY(time * 2);
                    r.mesh(Models.KEY, xf);
                }
                case 'b' -> {
                    xf.identity().translate(t.x, fh[t.i + t.j * w], t.z);
                    r.mesh(Models.BLOCK, xf);
                }
                case 'p' -> {
                    xf.identity().translate(t.x, t.y - (t.on ? .1f : 0), t.z);
                    r.mesh(t.on ? Models.PLATE_ON : Models.PLATE_OFF, xf);
                }
                case 't' -> {
                    xf.identity().translate(t.x, t.y, t.z);
                    r.mesh(Models.TORCH, xf);
                    if (t.on) {
                        xf.identity().translate(t.x, t.y + 1.45f, t.z).rotY(time * 3).scale(.3f, .3f + M.sin(time * 13 + t.i) * .05f, .3f);
                        r.mesh(Models.FLAME, xf);
                    }
                }
                case 'o' -> {
                    xf.identity().translate(t.x, t.y, t.z);
                    r.mesh(Models.PEDESTAL, xf);
                    xf.identity().translate(t.x, t.y + 1.5f + M.sin(time * 2) * .08f, t.z).rotY(time);
                    r.mesh(t.on ? Models.CRYSTAL_ON : Models.CRYSTAL_OFF, xf);
                }
                case 'g' -> {
                    xf.identity().translate(t.x, t.y, t.z);
                    r.mesh(Models.POST, xf);
                    r.point(t.x, t.y + 2.5f, t.z, .12f + M.sin(time * 5) * .03f, 0xffd870, 220);
                }
                case 'u' -> {
                    xf.identity().translate(t.x, t.y, t.z);
                    r.mesh(Models.POT, xf);
                }
                case 'R' -> {
                    xf.identity().translate(t.x, t.y, t.z);
                    r.mesh(Models.SHRINE, xf);
                }
                case 'P' -> {
                    xf.identity().translate(t.x, t.y, t.z).rotY(t.i);
                    r.mesh(t.on ? Models.BLAST_POD : Models.POD_SPROUT, xf);
                }
                case 'w' -> {
                    xf.identity().translate(t.x, t.y + 1.2f + M.sin(time * 2) * .15f, t.z).rotY(time * .8f);
                    r.mesh(spec.stone(), xf);
                }
                default -> {}
            }
        }
        if (flooded) {
            int col = r.shade(0x2a6a8a | Mesh.GLOW, 0, 1, 0, 8);
            for (int j = 0; j < h; j++)
                for (int i = 0; i < w; i++) {
                    if (!wet[i + j * w]) continue;
                    float x0 = i * T, z0 = (h - 1 - j) * T, y = M.sin(time * 1.5f + i * .7f + j) * .04f;
                    r.worldTri(x0, y, z0 + T, x0 + T, y, z0 + T, x0, y, z0, col, false, 170);
                    r.worldTri(x0 + T, y, z0 + T, x0 + T, y, z0, x0, y, z0, col, false, 170);
                }
        }
    }

    /** The master wakes only once the hero has opened the great door and stepped past it. */
    boolean inBossRoom(float hz) {
        for (Thing t : things) if (t.kind == 'B') return t.on && hz > t.z + 1.5f;
        return true;
    }

    String saveState() {
        StringBuilder sb = new StringBuilder();
        sb.append(keys).append(';').append(bigKey ? 1 : 0).append(';').append(flooded ? 1 : 0).append(';').append(bossDead ? 1 : 0).append(';');
        for (boolean f : fired) sb.append(f ? '1' : '0');
        sb.append(';');
        for (Thing t : things) sb.append(t.on ? '1' : '0').append(t.gone ? '1' : '0').append(t.hidden ? '1' : '0');
        return sb.toString();
    }

    void loadState(String s) {
        String[] p = s.split(";", -1);
        if (p.length < 6) return;
        keys = Integer.parseInt(p[0]);
        bigKey = p[1].equals("1");
        flooded = p[2].equals("1");
        bossDead = p[3].equals("1");
        for (int k = 0; k < fired.length && k < p[4].length(); k++)
            if (p[4].charAt(k) == '1') {
                fired[k] = true;
                rules.get(k).then().run();
            }
        for (int k = 0; k < things.size() && k * 3 + 2 < p[5].length(); k++) {
            Thing t = things.get(k);
            t.on = p[5].charAt(k * 3) == '1';
            t.gone = p[5].charAt(k * 3 + 1) == '1';
            t.hidden = p[5].charAt(k * 3 + 2) == '1';
            if (t.on && "SLBXIcC".indexOf(t.kind) >= 0) t.anim = 1;
        }
    }

    /** Chests face away from the nearest wall. */
    float chestYaw(Thing t) {
        if (at(t.i, t.j - 1) == '#') return M.PI;
        if (at(t.i - 1, t.j) == '#') return M.PI / 2;
        if (at(t.i + 1, t.j) == '#') return -M.PI / 2;
        return 0;
    }
}

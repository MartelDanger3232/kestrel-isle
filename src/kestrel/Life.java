package kestrel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The island's small lives, none of which fight: grazing deer, foxes and rabbits that bolt when you come close,
 * birds wheeling overhead or flocking up off the ground, leaping fish, butterflies by day and fireflies by night,
 * plus the villagers' daily strolls and the island's background sounds.
 */
final class Life {
    static final int DEER = 0, FOX = 1, SNOWFOX = 2, RABBIT = 3;
    private static final int GRAZE = 0, WALK = 1, FLEE = 2;
    private static final Rig[] RIGS = {Characters.DEER, Characters.FOX, Characters.SNOWFOX};
    private static final float[] SCARE = {16, 10, 10, 7}, RUN = {10, 8, 8, 7}, AMBLE = {1.4f, 2, 2, 2.2f}, STRIDE = {1.1f, 2.6f, 2.6f, 3.4f};
    private static final int[] WINGS = {0xf2d14e, 0xf4f4f0, 0xf08a3a, 0x6aa8f0, 0xe070b0};
    private static final String[] LEGS = {"FL", "FR", "HL", "HR"};
    private static final float[] LEG_OFF = {0, .5f, .6f, .1f};

    private static final class Beast {
        int kind, act;
        boolean hidden = true, white;
        float x, y, z, yaw, speed, phase, t;
        final float[] pose = Characters.DEER.newPose();
    }

    private static final class Bird {
        float x, y, z, vx, vy, vz, yaw;
    }

    private final Game g;
    private final World w;
    private final Random rng = new Random(77);
    private final List<Beast> beasts = new ArrayList<>();
    private final List<Bird> perched = new ArrayList<>();
    private final float[][] flutter = new float[8][3], glow = new float[30][3];
    private float flockX, flockZ, flockA, perchT = 2, flightT, fishT = 3, fishK = -1, fishX, fishZ, fishYaw, chirpT = 3, nightT = 2;
    private final Xf tmp = new Xf();

    Life(Game g) {
        this.g = g;
        w = g.world;
        for (int i = 0; i < 9; i++) beasts.add(new Beast());
        for (float[] f : flutter) f[2] = -1;
        for (float[] f : glow) f[2] = -1;
    }

    /** Gathers some grazing beasts and the bird flock around (x, z): for screenshots and tests. */
    void stage(float x, float z, float yaw) {
        int[] kinds = {DEER, DEER, DEER, RABBIT, RABBIT, FOX};
        for (int i = 0; i < kinds.length; i++) {
            Beast b = beasts.get(i);
            float a = yaw + (i - 2.5f) * .22f, r = 9 + (i * 7 % 5) * 2.2f;
            b.kind = kinds[i];
            b.x = x + M.sin(a) * r;
            b.z = z + M.cos(a) * r;
            b.y = w.height(b.x, b.z);
            b.yaw = a + 1.4f + i;
            b.act = GRAZE;
            b.t = 30;
            b.speed = 0;
            b.white = false;
            b.hidden = false;
        }
        flockX = x + M.sin(yaw) * 30;
        flockZ = z + M.cos(yaw) * 30;
    }

    // ---- update ---------------------------------------------------------------------------------

    void update(float dt) {
        villagers(dt);
        for (Beast b : beasts) beast(b, dt);
        birds(dt);
        fish(dt);
        insects();
        sounds(dt);
    }

    /** Brindle's folk stroll around home by day, head back at night, and stop to face you when you come close. */
    private void villagers(float dt) {
        Hero h = g.hero;
        boolean night = g.climate.isNight();
        float[] pos = new float[2];
        for (Places.Npc n : w.places.everyone) {
            n.flash = Math.max(0, n.flash - dt * 4);
            n.robT = Math.max(0, n.robT - dt);
            if (n.downT > 0) {
                // Out cold (or, for townsfolk, dead): back on their feet at home when the time's up.
                if ((n.downT -= dt) <= 0) {
                    n.hp = 4;
                    n.fleeT = 0;
                    if (n.folk) {
                        n.x = n.tx = n.hx;
                        n.z = n.tz = n.hz;
                    }
                }
                continue;
            }
            float near = M.dist(n.x, n.z, h.x, h.z);
            if (near > 150) continue;
            // Anyone hurt, or near trouble, or near a wanted player, runs.
            boolean scared = (n.fleeT -= dt) > 0 || h.wanted > 0 && near < 18;
            if (scared) {
                float away = near < .1f ? 1 : near;
                n.tx = n.x + (n.x - h.x) / away * 6;
                n.tz = n.z + (n.z - h.z) / away * 6;
            }
            boolean hold = !scared && (near < 4.5f || g.screen == Game.Screen.DIALOG && near < 9);
            float dx = n.tx - n.x, dz = n.tz - n.z, d = M.hypot(dx, dz);
            if (hold || d < .3f) {
                n.walk = M.approach(n.walk, 0, 6, dt);
                if (!hold && (n.wait -= dt) <= 0) pickTarget(n, night);
                if (!hold && d < .3f && (night || n.wander <= 0)) n.yaw = M.turn(n.yaw, n.hyaw, 3 * dt);
            } else {
                boolean child = n.id().equals("child");
                float sp = scared ? 4.2f : child ? 2.6f : 1.2f;
                n.walk = M.approach(n.walk, scared || child ? 1.5f : 1, 4, dt);
                float nx = n.x + dx / d * sp * dt, nz = n.z + dz / d * sp * dt;
                if (g.field.floor(nx, nz) > n.y + .6f || w.height(nx, nz) < World.WATER + .3f) {
                    n.tx = n.x;
                    n.tz = n.z;
                    n.wait = 1;
                } else {
                    pos[0] = nx;
                    pos[1] = nz;
                    g.field.collide(pos, n.y, .35f);
                    n.x = pos[0];
                    n.z = pos[1];
                }
                n.yaw = M.turn(n.yaw, M.atan2(dx, dz), 5 * dt);
                n.phase += dt * sp * 3.4f;
            }
            n.y = w.height(n.x, n.z);
        }
    }

    private void pickTarget(Places.Npc n, boolean night) {
        n.wait = 3 + rng.nextFloat() * 8;
        if (night || n.wander <= 0) {
            n.tx = n.hx;
            n.tz = n.hz;
            return;
        }
        for (int k = 0; k < 6; k++) {
            float a = rng.nextFloat() * M.TAU, r = (.3f + .7f * rng.nextFloat()) * n.wander, x = n.hx + M.sin(a) * r, z = n.hz + M.cos(a) * r;
            if (g.field.floor(x, z) < n.y + .6f && w.height(x, z) > World.WATER + .3f) {
                n.tx = x;
                n.tz = z;
                return;
            }
        }
    }

    private void beast(Beast b, float dt) {
        Hero h = g.hero;
        float d = M.dist(b.x, b.z, h.x, h.z);
        if (b.hidden || d > 130) {
            if ((b.t -= dt) <= 0) spawn(b);
            return;
        }
        float scare = SCARE[b.kind] * (g.horse.ridden ? 1.7f : 1);
        b.t -= dt;
        if (d < scare && b.act != FLEE) {
            b.act = FLEE;
            b.t = 2.5f + rng.nextFloat() * 2;
            b.yaw = M.atan2(b.x - h.x, b.z - h.z) + (rng.nextFloat() - .5f) * .8f;
        }
        if (b.t <= 0) {
            if (b.act == GRAZE) {
                b.act = WALK;
                b.t = 2 + rng.nextFloat() * 4;
                b.yaw += (rng.nextFloat() - .5f) * 2.5f;
            } else {
                b.act = GRAZE;
                b.t = 3 + rng.nextFloat() * 6;
            }
        }
        float want = b.act == FLEE ? RUN[b.kind] : b.act == WALK ? AMBLE[b.kind] : 0;
        b.speed = M.approach(b.speed, want, 5, dt);
        if (b.speed < .05f) return;
        float nx = b.x + M.sin(b.yaw) * b.speed * dt, nz = b.z + M.cos(b.yaw) * b.speed * dt, ny = w.height(nx, nz);
        if (ny < World.WATER + .4f || Math.abs(ny - b.y) > b.speed * dt * 1.1f + .05f || g.field.floor(nx, nz) > ny + .5f)
            b.yaw += (rng.nextBoolean() ? 1 : -1) * (.8f + rng.nextFloat());
        else {
            b.x = nx;
            b.z = nz;
            b.y = ny;
        }
        b.phase += dt * b.speed * STRIDE[b.kind];
    }

    /** Finds a home for a beast out of sight around the hero, suited to the land there. */
    private void spawn(Beast b) {
        Hero h = g.hero;
        Places pl = w.places;
        b.t = 1.5f;
        if (g.dungeon != null) return;
        for (int k = 0; k < 10; k++) {
            float a = rng.nextFloat() * M.TAU, r = 45 + rng.nextFloat() * 70, x = h.x + M.sin(a) * r, z = h.z + M.cos(a) * r;
            if (x < 40 || z < 40 || x > World.EXTENT - 40 || z > World.EXTENT - 40) continue;
            float y = w.height(x, z);
            if (y < World.WATER + 1.5f || w.slope(x, z) > .55f || M.dist(x, z, pl.villageX, pl.villageZ) < 50 || M.dist(x, z, pl.outpostX, pl.outpostZ) < 25) continue;
            int biome = w.biomeAt(x, z);
            float q = rng.nextFloat();
            int kind = switch (biome) {
                case World.MEADOW, World.PATH -> q < .45f ? DEER : q < .85f ? RABBIT : FOX;
                case World.FOREST -> q < .5f ? DEER : q < .8f ? FOX : RABBIT;
                case World.SNOW -> q < .45f ? SNOWFOX : RABBIT;
                default -> -1;
            };
            if (kind < 0) continue;
            b.kind = kind;
            b.white = biome == World.SNOW;
            b.x = x;
            b.y = y;
            b.z = z;
            b.yaw = rng.nextFloat() * M.TAU;
            b.act = GRAZE;
            b.t = rng.nextFloat() * 5;
            b.speed = 0;
            b.hidden = false;
            return;
        }
    }

    private void birds(float dt) {
        Hero h = g.hero;
        // The sky flock drifts after the hero and wheels in slow circles.
        flockX = M.approach(flockX, h.x, .05f, dt);
        flockZ = M.approach(flockZ, h.z, .05f, dt);
        if (M.dist(flockX, flockZ, h.x, h.z) > 200) {
            flockX = h.x + 60;
            flockZ = h.z + 40;
        }
        flockA += dt * .22f;
        // A little group pecks on the ground until something big comes near.
        if (perched.isEmpty()) {
            if ((perchT -= dt) > 0) return;
            perchT = 20 + rng.nextFloat() * 15;
            float a = rng.nextFloat() * M.TAU, r = 25 + rng.nextFloat() * 30, x = h.x + M.sin(a) * r, z = h.z + M.cos(a) * r;
            if (w.height(x, z) < World.WATER + 1 || g.field.floor(x, z) > 900 || w.biomeAt(x, z) == World.ASH) return;
            for (int i = 0; i < 5; i++) {
                Bird b = new Bird();
                b.x = x + (rng.nextFloat() - .5f) * 3;
                b.z = z + (rng.nextFloat() - .5f) * 3;
                b.y = w.height(b.x, b.z);
                b.yaw = rng.nextFloat() * M.TAU;
                perched.add(b);
            }
            flightT = 0;
            return;
        }
        Bird first = perched.get(0);
        if (flightT == 0 && M.dist(first.x, first.z, h.x, h.z) < (g.horse.ridden ? 15 : 9)) {
            flightT = .01f;
            for (Bird b : perched) {
                float a = M.atan2(b.x - h.x, b.z - h.z) + (rng.nextFloat() - .5f);
                b.vx = M.sin(a) * (4 + rng.nextFloat() * 3);
                b.vz = M.cos(a) * (4 + rng.nextFloat() * 3);
                b.vy = 4 + rng.nextFloat() * 3;
                b.yaw = a;
            }
            g.sfx(Audio.FLAP);
        }
        if (flightT > 0) {
            flightT += dt;
            for (Bird b : perched) {
                b.x += b.vx * dt;
                b.y += b.vy * dt;
                b.z += b.vz * dt;
                b.vx *= 1 + dt * .3f;
                b.vz *= 1 + dt * .3f;
            }
            if (flightT > 7) perched.clear();
        } else if (M.dist(first.x, first.z, h.x, h.z) > 140) perched.clear();
    }

    private void fish(float dt) {
        if (fishK >= 0) {
            float before = fishK;
            fishK += dt / .8f;
            if (before < 1 && fishK >= 1) {
                g.splash(fishX + M.sin(fishYaw) * 1.6f, World.WATER, fishZ + M.cos(fishYaw) * 1.6f);
                fishK = -1;
            }
            return;
        }
        if ((fishT -= dt) > 0) return;
        fishT = 3 + rng.nextFloat() * 6;
        Hero h = g.hero;
        for (int k = 0; k < 6; k++) {
            float a = rng.nextFloat() * M.TAU, r = 7 + rng.nextFloat() * 25, x = h.x + M.sin(a) * r, z = h.z + M.cos(a) * r;
            if (w.height(x, z) > -1.5f || w.height(x + 2, z + 2) > -1.2f) continue;
            fishX = x;
            fishZ = z;
            fishYaw = rng.nextFloat() * M.TAU;
            fishK = 0;
            g.splash(x, World.WATER, z);
            return;
        }
    }

    /** Butterflies flit over grass by day; fireflies drift low by night. Both stay near the hero. */
    private void insects() {
        Hero h = g.hero;
        int b = w.biomeAt(h.x, h.z);
        boolean green = b == World.MEADOW || b == World.FOREST || b == World.PATH, night = g.climate.isNight();
        place(flutter, green && !night && g.climate.rain < .3f, 22, h);
        place(glow, green && night, 26, h);
    }

    private void place(float[][] set, boolean wanted, float rad, Hero h) {
        for (float[] f : set) {
            if (!wanted) {
                f[2] = -1;
                continue;
            }
            if (f[2] >= 0 && M.dist(f[0], f[1], h.x, h.z) < rad * 1.4f) continue;
            float a = rng.nextFloat() * M.TAU, r = 4 + rng.nextFloat() * rad;
            f[0] = h.x + M.sin(a) * r;
            f[1] = h.z + M.cos(a) * r;
            f[2] = w.height(f[0], f[1]) < World.WATER + .5f ? -1 : rng.nextFloat() * 100;
        }
    }

    private void sounds(float dt) {
        Hero h = g.hero;
        int b = w.biomeAt(h.x, h.z);
        boolean night = g.climate.isNight();
        if ((chirpT -= dt) <= 0) {
            if (b == World.SNOW) {
                chirpT = 5 + rng.nextFloat() * 5;
                g.sfx(Audio.WIND);
            } else if (night) {
                chirpT = 1.2f + rng.nextFloat() * 2;
                if (b != World.ASH && b != World.DESERT) g.sfx(Audio.CRICKET);
            } else {
                chirpT = 2.5f + rng.nextFloat() * 6;
                if (b == World.MEADOW || b == World.FOREST || b == World.PATH) g.sfx(Audio.CHIRP);
            }
        }
        if (night && b == World.FOREST && (nightT -= dt) <= 0) {
            nightT = 14 + rng.nextFloat() * 16;
            g.sfx(Audio.HOOT);
        }
    }

    // ---- drawing --------------------------------------------------------------------------------

    void draw(Renderer r, Xf xf) {
        float t = g.time;
        for (Beast b : beasts) {
            if (b.hidden || M.dist(b.x, b.z, r.cx, r.cz) > 110 || !r.visible(b.x, b.y + .5f, b.z, 1.4f)) continue;
            if (b.kind == RABBIT) {
                float hop = b.speed > .05f ? Math.abs(M.sin(b.phase)) * .28f : 0;
                float nibble = b.speed < .05f ? M.sin(t * 9 + b.x) * .02f : 0;
                r.mesh(b.white ? Characters.HARE : Characters.RABBIT, xf.identity().translate(b.x, b.y + hop + nibble, b.z).rotY(b.yaw));
                continue;
            }
            Rig rig = RIGS[b.kind];
            boolean grazing = b.act == GRAZE && b.speed < .3f;
            float amp = Math.min(1, b.speed / RUN[b.kind] * 1.8f) * (b.act == FLEE ? .9f : .55f);
            float head = grazing ? (b.kind == DEER ? 1.2f : .45f) + M.sin(t * 1.3f + b.x) * .08f : b.act == FLEE ? -.2f : 0;
            trot(rig, b.pose, b.phase, amp, head, b.kind == DEER ? (b.act == FLEE ? -.7f : .25f) : (b.act == FLEE ? .25f : .6f + M.sin(t * 3 + b.z) * .1f));
            if (grazing) b.pose[rig.n * 3 + 1] = b.kind == DEER ? .12f : 0;
            rig.draw(r, xf.identity().translate(b.x, b.y, b.z).rotY(b.yaw), b.pose);
        }
        drawBirds(r, xf, t);
        if (fishK >= 0) {
            float k = Math.min(1, fishK);
            xf.identity().translate(fishX + M.sin(fishYaw) * 1.6f * k, World.WATER - .2f + M.sin(k * M.PI) * 1.1f, fishZ + M.cos(fishYaw) * 1.6f * k)
                    .rotY(fishYaw).rotX(-M.cos(k * M.PI) * 1.1f);
            r.mesh(Characters.FISH, xf);
        }
        for (int i = 0; i < flutter.length; i++) {
            float[] f = flutter[i];
            if (f[2] < 0) continue;
            float p = t * .7f + f[2], x = f[0] + M.sin(p) * 2.2f + M.sin(p * 2.3f) * .6f, z = f[1] + M.cos(p * .8f) * 2.2f;
            float y = w.height(x, z) + .7f + M.sin(p * 3.1f) * .35f, yaw = p + M.PI / 2, flap = M.sin(t * 24 + i) * .8f;
            wings(r, x, y, z, yaw, .09f, .05f, flap, WINGS[i % WINGS.length]);
        }
        for (float[] f : glow) {
            if (f[2] < 0) continue;
            float p = t * .3f + f[2], x = f[0] + M.sin(p) * 1.5f, z = f[1] + M.cos(p * 1.3f) * 1.5f, y = w.height(x, z) + .5f + (M.sin(p * 2.1f) + 1) * .5f;
            int a = (int) (230 * Math.max(0, M.sin(t * 1.6f + f[2] * 3)));
            if (a < 10) continue;
            r.point(x, y, z, .05f, 0xd8ff6a, a);
            r.point(x, y, z, .16f, 0x9fd040, a / 6);
        }
    }

    private void drawBirds(Renderer r, Xf xf, float t) {
        if (!g.climate.isNight()) {
            float gy = Math.max(World.WATER, w.height(flockX, flockZ)) + 32;
            for (int i = 0; i < 7; i++) {
                float a = flockA + i * .19f, rad = 26 + i * 1.7f;
                float x = flockX + M.sin(a) * rad + M.sin(t * .9f + i * 1.3f) * 3, z = flockZ + M.cos(a) * rad + M.cos(t * .8f + i * 2.1f) * 3;
                float y = gy + M.sin(t * 1.7f + i) * 1.5f + i * .6f;
                if (!r.visible(x, y, z, 1)) continue;
                wings(r, x, y, z, a + M.PI / 2, .55f, .18f, M.sin(t * 7 + i * 1.7f) * .55f, 0x2a2830);
            }
        }
        for (Bird b : perched) {
            if (!r.visible(b.x, b.y + .2f, b.z, .5f)) continue;
            if (flightT > 0) wings(r, b.x, b.y + .2f, b.z, b.yaw, .3f, .1f, M.sin(t * 16 + b.x) * .7f, 0x5a4a3a);
            else r.mesh(Characters.BIRD, xf.identity().translate(b.x, b.y, b.z).rotY(b.yaw + M.sin(t * .7f + b.z) * .6f)
                    .rotX(Math.max(0, M.sin(t * 5 + b.x * 3)) * .5f));
        }
    }

    /** Two flapping wing triangles and a sliver of body: enough for a bird or a butterfly at a distance. */
    private void wings(Renderer r, float x, float y, float z, float yaw, float span, float len, float flap, int col) {
        float fx = M.sin(yaw), fz = M.cos(yaw), rx = M.cos(yaw), rz = -M.sin(yaw), lift = M.sin(flap) * span, reach = M.cos(flap) * span;
        float nx = x + fx * len, nz = z + fz * len, tx = x - fx * len, tz = z - fz * len;
        r.worldTri(nx, y, nz, tx, y, tz, x + rx * reach, y + lift, z + rz * reach, col, true, 256);
        r.worldTri(nx, y, nz, tx, y, tz, x - rx * reach, y + lift, z - rz * reach, col, true, 256);
    }

    private static void trot(Rig rig, float[] pose, float phase, float amp, float head, float tail) {
        java.util.Arrays.fill(pose, 0);
        for (int k = 0; k < 4; k++) {
            float lp = phase + LEG_OFF[k] * M.TAU;
            pose[rig.part("leg" + LEGS[k] + "u") * 3] = M.sin(lp) * amp;
            pose[rig.part("leg" + LEGS[k] + "l") * 3] = Math.max(0, M.sin(lp + 1.4f)) * amp * (k < 2 ? 1 : -.7f);
        }
        pose[rig.part("head") * 3] = head;
        pose[rig.part("tail") * 3] = tail;
        pose[rig.n * 3] = Math.abs(M.sin(phase)) * .05f * Math.min(1, amp * 2);
    }
}

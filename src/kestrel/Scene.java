package kestrel;

import java.util.HashMap;
import java.util.Map;

/** Points the camera and draws the current level and everything in it. */
final class Scene {
    private static final int TRAIL = 10;
    private final Game g;
    private final Renderer r;
    final WorldView view;
    private final Xf xf = new Xf(), tmp = new Xf();
    private final Map<String, float[]> npcPose = new HashMap<>();
    private float camDist = 6, camTy = Float.NaN, camLift;
    /** Dev screenshots can pull the camera in close. */
    float zoom;
    /** This frame's rain or snow, 0 to 1 (the graphics card paints it over the frame). */
    float weather;
    boolean snowing;

    Scene(Game g, Renderer r) {
        this.g = g;
        this.r = r;
        view = new WorldView(r, g.world);
    }

    void render(float dt) {
        camera(dt);
        Dungeon d = g.dungeon;
        boolean gpu = r.gpu != null;
        if (d == null) {
            float rain = g.screen == Game.Screen.TITLE ? 0 : localRain();
            g.climate.apply(r, rain);
            view.terrain();
            if (!gpu) view.props();
            view.grass(g.time);
            view.pickups(g.time);
            fieldThings();
            if (g.screen != Game.Screen.TITLE) g.life.draw(r, xf);
        } else {
            dungeonLights(d);
            d.render(r, xf, g.time);
        }
        for (Foe f : g.foes) if (M.dist(f.x, f.z, r.cx, r.cz) < 200 && r.visible(f.x, f.centerY(), f.z, f.radius() * 3)) f.draw(r, xf, g.time);
        if (d == null && g.screen != Game.Screen.TITLE && g.horse.summoned && r.visible(g.horse.x, g.horse.y + 1.2f, g.horse.z, 2.5f)) g.horse.draw(r, xf);
        // Every player's hero (friends' too in co-op), each drawn just as this computer's own is.
        if (g.screen != Game.Screen.TITLE)
            for (Game.Player p : g.players) {
                Game.Player was = g.as(p);
                hero(p);
                g.as(was);
            }
        things();
        if (gpu) {
            // The card has its own sky, ocean and background.
        } else if (d == null) {
            view.sky(g.climate, g.time);
            view.water(g.time);
        } else {
            int c = r.fogCol;
            for (int i = 0; i < r.px.length; i++) if (r.zb[i] == 0) r.px[i] = c;
        }
        effects();
        if (d == null && g.drake != null) g.drake.drawStrikes(r, g);
        weather = 0;
        if (d == null && g.screen != Game.Screen.TITLE) {
            snowing = g.world.biomeAt(g.hero.x, g.hero.z) == World.SNOW;
            weather = snowing ? .45f + g.climate.rain * .55f : localRain();
            if (!gpu) view.weather(weather, snowing, g.time);
        }
    }

    private float localRain() {
        int b = g.world.biomeAt(g.hero.x, g.hero.z);
        return b == World.DESERT || b == World.ASH ? 0 : g.climate.rain;
    }

    private void dungeonLights(Dungeon d) {
        float flick = 1 + M.sin(g.time * 7.3f) * .025f + M.sin(g.time * 13.1f) * .015f;
        r.lx = .3f;
        r.ly = .86f;
        r.lz = .41f;
        boolean lava = d.spec.lava();
        r.ar = (lava ? .5f : .42f) * flick;
        r.ag = (lava ? .38f : .4f) * flick;
        r.ab = (lava ? .3f : .4f) * flick;
        r.sr = .62f;
        r.sg = .56f;
        r.sb = .48f;
        r.fogCol = d.spec.fog();
        r.fogStart = 9;
        r.fogEnd = 46;
    }

    private void camera(float dt) {
        Hero h = g.hero;
        float cx, cy, cz, yaw = g.camYaw, pitch = g.camPitch;
        if (g.screen == Game.Screen.TITLE) {
            Places pl = g.world.places;
            float a = g.time * .05f + 2.2f;
            cx = pl.villageX + M.sin(a) * 70;
            cz = pl.villageZ + M.cos(a) * 70;
            cy = Math.max(g.world.height(cx, cz), pl.villageX * 0) + 26;
            yaw = M.atan2(pl.villageX - cx, pl.villageZ - cz);
            pitch = -.28f;
        } else {
            if (h.locked) {
                float[] p = new float[3];
                boolean spot = h.aimPoint(p);
                float want = spot ? M.atan2(p[0] - h.x, p[2] - h.z) : h.target != null ? M.atan2(h.target.x - h.x, h.target.z - h.z) : h.yaw;
                g.camYaw = yaw = M.turn(yaw, want, (h.target != null ? 4 : 7) * dt);
                if (spot) {
                    float up = M.atan2(p[1] - (h.y + 1.5f), Math.max(2, M.dist(h.x, h.z, p[0], p[2])));
                    g.camPitch = pitch = M.approach(pitch, M.clamp(up * .7f - .18f, -.6f, .45f), 3, dt);
                }
            }
            boolean aim = h.st == Hero.State.AIM;
            float want = zoom > 0 ? zoom : aim ? 2.4f : g.horse.ridden ? 8.5f : g.dungeon != null ? 6.5f : 6.2f;
            float tx = h.x, ty = h.y + (aim ? 1.55f : 1.5f), tz = h.z;
            if (aim) {
                tx += M.cos(yaw) * .6f;
                tz -= M.sin(yaw) * .6f;
            }
            camTy = Float.isNaN(camTy) || Math.abs(camTy - ty) > 5 ? ty : M.approach(camTy, ty, 10, dt);
            float fx = M.sin(yaw) * M.cos(pitch), fy = M.sin(pitch), fz = M.cos(yaw) * M.cos(pitch);
            // The camera pulls in front of anything behind the hero (indoors it also rises a little); the mouse always owns the tilt.
            boolean inside = g.dungeon != null;
            float d = want;
            for (int i = 1; i <= 14; i++) {
                float t = want * i / 14, px = tx - fx * t, py = camTy - fy * t, pz = tz - fz * t;
                if (g.level.floor(px, pz) + .35f <= py) continue;
                d = Math.max(inside ? 1.2f : .7f, t - want / 14);
                break;
            }
            camDist = d < camDist ? d : M.approach(camDist, d, 3, dt);
            camLift = inside ? Math.min(1.1f, (want - camDist) * .25f) : 0;
            cx = tx - fx * camDist;
            cy = camTy - fy * camDist + camLift;
            cz = tz - fz * camDist;
            float w = g.level.water(cx, cz);
            if (!Float.isNaN(w)) cy = Math.max(cy, w + .3f);
        }
        float sh = g.shake * .04f;
        r.camera(cx, cy, cz, yaw + M.sin(g.time * 53) * sh, pitch + M.sin(g.time * 41) * sh);
        g.camX = cx;
        g.camY = cy;
        g.camZ = cz;
    }

    // ---- the open island ----------------------------------------------------------------------

    private void fieldThings() {
        Places pl = g.world.places;
        float t = g.time;
        boolean night = g.climate.isNight();
        for (Places.Building b : pl.buildings) {
            if (M.dist(b.x(), b.z(), r.cx, r.cz) > 420 || !r.visible(b.x(), b.y() + 2, b.z(), 6)) continue;
            r.mesh(b.mesh(), xf.identity().translate(b.x(), b.y(), b.z()).rotY(b.yaw()));
            if (night && (b.mesh() == Models.HOUSE || b.mesh() == Models.SHOP)) {
                r.mesh(b.mesh() == Models.HOUSE ? Models.HOUSE_LIT : Models.SHOP_LIT, xf);
                r.light(b.x(), b.y() + 1.6f, b.z(), 0xffb060, 11);
            }
            if (b.mesh() == Models.HOUSE && M.dist(b.x(), b.z(), r.cx, r.cz) < 170) smoke(b);
        }
        for (Places.Gate gt : pl.gates) {
            if (M.dist(gt.x, gt.z, r.cx, r.cz) > 900 || !r.visible(gt.x, gt.y + 6, gt.z, 18)) continue;
            xf.identity().translate(gt.x, gt.y, gt.z).rotY(M.PI);
            switch (gt.kind) {
                case Places.ROOT -> r.mesh(Models.TREE_GATE, xf);
                case Places.CINDER, Places.CROWN -> {
                    r.mesh(Models.FORGE_GATE, xf);
                    if (gt.kind == Places.CINDER) {
                        r.mesh(Models.LANTERN_TARGET, tmp.identity().translate(gt.x, gt.y + 8.6f, gt.z).rotY(t));
                        if (!gt.open) r.mesh(Models.DOOR, tmp.set(xf).translate(0, 0, .2f).scale(1.6f, 1.25f, 1));
                    }
                }
                case Places.TIDE -> r.mesh(Models.TIDE_GATE, xf);
                case Places.FROST -> r.mesh(Models.ICE_GATE, xf);
                case Places.SAND -> r.mesh(Models.SUN_GATE, xf);
                default -> r.mesh(Models.ALTAR, xf);
            }
        }
        for (float[] b : pl.boulders) if (b[3] > 0 && r.visible(b[0], b[1] + 1.5f, b[2], 3)) r.mesh(Models.BOULDER, xf.identity().translate(b[0], b[1], b[2]));
        for (float[] p : pl.pods)
            if (M.dist(p[0], p[2], r.cx, r.cz) < 200 && r.visible(p[0], p[1] + .5f, p[2], 1)) r.mesh(p[3] > 0 ? Models.BLAST_POD : Models.POD_SPROUT, xf.identity().translate(p[0], p[1], p[2]));
        for (float[] f : pl.fires) {
            if (M.dist(f[0], f[2], r.cx, r.cz) > 300 || !r.visible(f[0], f[1], f[2], 2)) continue;
            r.mesh(Models.FIREPIT, xf.identity().translate(f[0], f[1], f[2]));
            r.mesh(Models.FLAME, xf.identity().translate(f[0], f[1] + .1f, f[2]).rotY(t * 3).scale(.42f, .42f + M.sin(t * 11 + f[0]) * .06f, .42f));
            r.light(f[0], f[1] + 1.2f, f[2], 0xff8a3a, 14 + M.sin(t * 9 + f[0]) * .6f);
        }
        for (Places.Sign s : pl.signs) r.mesh(Models.SIGN, xf.identity().translate(s.x(), s.y(), s.z()).rotY(s.yaw()));
        for (float[] p : pl.posts) {
            if (M.dist(p[0], p[2], r.cx, r.cz) > 220 || !r.visible(p[0], p[1] + 1.2f, p[2], 1.5f)) continue;
            r.mesh(Models.POST, xf.identity().translate(p[0], p[1], p[2]));
            r.point(p[0], p[1] + 2.5f, p[2], .14f + M.sin(t * 5 + p[0]) * .03f, 0xffd870, 220);
            if (night) r.light(p[0], p[1] + 2.5f, p[2], 0xffc860, 10);
        }
        for (float[] v : pl.vents) {
            if (M.dist(v[0], v[2], r.cx, r.cz) > 260 || !r.visible(v[0], v[1] + 6, v[2], 9)) continue;
            r.mesh(Models.VENT, xf.identity().translate(v[0], v[1], v[2]));
            // Sand and heat shimmer streaming up the column of rising air.
            for (int k = 0; k < 14; k++) {
                float f = (t * (.35f + k % 3 * .07f) + k * .137f) % 1, a = k * 2.4f + t * .8f, rr = .4f + f * 1.6f;
                r.point(v[0] + M.sin(a) * rr, v[1] + .3f + f * 30, v[2] + M.cos(a) * rr, .07f + f * .12f, 0xe8d2a0, (int) (170 * (1 - f)));
            }
        }
        for (Places.Chest c : pl.chests) {
            if (M.dist(c.x, c.z, r.cx, r.cz) > 200 || !r.visible(c.x, c.y + .5f, c.z, 1.2f)) continue;
            xf.identity().translate(c.x, c.y, c.z).rotY(c.yaw);
            r.mesh(Models.CHEST, xf);
            r.mesh(Models.LID, new Xf().set(xf).translate(0, .6f, -.38f).rotX(-1.9f * c.anim));
        }
        for (float[] tn : pl.tents)
            if (M.dist(tn[0], tn[2], r.cx, r.cz) < 250 && r.visible(tn[0], tn[1] + 1.2f, tn[2], 2.5f)) r.mesh(Models.TENT, xf.identity().translate(tn[0], tn[1], tn[2]).rotY(tn[3]));
        for (float[] s : pl.shrines)
            if (M.dist(s[0], s[2], r.cx, r.cz) < 500 && r.visible(s[0], s[1] + 1, s[2], 2.5f)) r.mesh(Models.SHRINE, xf.identity().translate(s[0], s[1], s[2]));
        for (Places.Npc n : pl.npcs) {
            if (M.dist(n.x(), n.z(), r.cx, r.cz) > 160 || !r.visible(n.x(), n.y() + 1, n.z(), 1.5f)) continue;
            float near = M.dist(n.x(), n.z(), g.hero.x, g.hero.z);
            boolean talking = g.screen == Game.Screen.DIALOG && near < 4, waving = !talking && n.id().equals("child") && near < 9;
            float yaw = near < 6 ? M.atan2(g.hero.x - n.x(), g.hero.z - n.z()) : n.yaw();
            float[] pose = npcPose.computeIfAbsent(n.id(), k -> n.rig().newPose());
            n.rig().locomote(pose, n.phase, n.walk, t + n.hx);
            if (talking) Characters.TALK.apply(t, 1, pose);
            else if (waving) Characters.WAVE.apply(t, 1, pose);
            n.rig().draw(r, xf.identity().translate(n.x(), n.y(), n.z()).rotY(yaw).scale(n.id().equals("child") ? .78f : 1), pose);
        }
    }

    /** Lazy chimney smoke: puffs rise, swell and fade as the breeze takes them. */
    private void smoke(Places.Building b) {
        float c = M.cos(b.yaw()), s = M.sin(b.yaw()), cx = b.x() + 1.8f * c - .8f * s, cz = b.z() - 1.8f * s - .8f * c, cy = b.y() + 5.3f;
        for (int k = 0; k < 5; k++) {
            float f = (g.time * .16f + k / 5f + b.x() * .013f) % 1;
            float px = cx + f * 2.6f + M.sin(g.time * .6f + k) * .25f, pz = cz + f * 1.2f, py = cy + f * 4.5f;
            r.mesh(Models.PUFF, tmp.identity().translate(px, py, pz).scale(.3f + f * .9f), (int) (150 * (1 - f) * Math.min(1, f * 6)));
        }
    }

    // ---- the hero -----------------------------------------------------------------------------

    /** Draws player p's hero (already the current one): body, gear, glider, and the trail of a swing. */
    private void hero(Game.Player p) {
        Hero h = g.hero;
        Rig R = Arms.dressed(g.gear);
        if (h.st == Hero.State.GLIDE)
            r.mesh(g.gear.stormcloak ? Models.STORMCLOAK : Models.WINDCLOAK, xf.identity().translate(h.x, h.y, h.z).rotY(h.yaw).rotZ(M.sin(g.time * 2.3f) * .05f));
        if (h.st == Hero.State.HOVER || h.aloft == 1) {
            // The Skyward Gust holding the hero up: three rings of wind wheeling opposite ways, gusts streaming up from below, a pale glow.
            for (int ring = 0; ring < 3; ring++)
                for (int i = 0; i < 12; i++) {
                    float a = i * M.TAU / 12 + g.time * (2.2f + ring * .8f) * (ring == 1 ? -1 : 1), rr = .75f + ring * .22f;
                    r.point(h.x + M.sin(a) * rr, h.y + .15f + ring * .6f + M.sin(g.time * 3 + i + ring) * .06f, h.z + M.cos(a) * rr, .06f, ring == 1 ? 0xffffff : 0xbfe8ff, 170);
                }
            for (int k = 0; k < 8; k++) {
                float f = (g.time * .8f + k / 8f) % 1, a = k * 1.7f + g.time * 1.3f;
                r.point(h.x + M.sin(a) * .45f, h.y - 1.8f + f * 2.2f, h.z + M.cos(a) * .45f, .05f + f * .06f, 0xd8f4ff, (int) (210 * (1 - f)));
            }
            r.light(h.x, h.y + 1, h.z, 0xbfe8ff, 7);
        }
        float[] hilt = p.hilt, tip = p.tip;
        for (int i = 1; i < p.trailN; i++) {
            int a = (i - 1) * 3, b = i * 3, alpha = 40 + 120 * i / TRAIL;
            int col = g.gear.ascended ? 0xffe8a0 : 0xe8f8ff;
            r.worldTri(hilt[a], hilt[a + 1], hilt[a + 2], tip[a], tip[a + 1], tip[a + 2], tip[b], tip[b + 1], tip[b + 2], col, true, alpha);
            r.worldTri(hilt[a], hilt[a + 1], hilt[a + 2], tip[b], tip[b + 1], tip[b + 2], hilt[b], hilt[b + 1], hilt[b + 2], col, true, alpha);
        }
        if (h.st == Hero.State.CHARGE && h.chargeT > .5f) r.point(tip[0], tip[1], tip[2], .25f + M.sin(g.time * 30) * .05f, 0x9fe8ff, 200);
        boolean flicker = h.invuln > 0 && g.hurtFlash > 0 && ((int) (g.time * 22) & 1) == 0 && h.st != Hero.State.DEAD;
        if (flicker) return;
        xf.identity().translate(h.x, h.y, h.z).rotY(h.yaw);
        boolean bowMode = g.gear.bow && g.gear.bowOut && h.st != Hero.State.RIDE;
        boolean drawn = !bowMode && (h.swordOutT > 0 || h.st == Hero.State.ATTACK || h.st == Hero.State.CHARGE || h.st == Hero.State.SPIN);
        boolean swinging = h.st == Hero.State.ATTACK || h.st == Hero.State.SPIN;
        R.draw(r, xf, h.pose);
        Xf handR = R.xf[R.part("armLR")], handL = R.xf[R.part("armLL")], torso = R.xf[R.part("torso")], head = R.xf[R.part("head")];
        if (g.gear.sword) {
            // Between swings a drawn blade points ahead rather than into the ground, and long ones ride higher and
            // more slanted on the back, so their tips stay clear of it.
            float held = swinging || h.st == Hero.State.CHARGE ? M.PI / 2 - .35f : -.3f;
            boolean big = Arms.TIP[g.gear.weapon] > 1.3f;
            if (drawn) tmp.set(handR).translate(0, -.27f, 0).rotX(held);
            else tmp.set(torso).translate(.14f, big ? .62f : .42f, -.17f).rotZ(big ? -.85f : -.55f).rotX(M.PI / 2 + .25f);
            r.mesh(Arms.MESH[g.gear.weapon], tmp);
            if (g.gear.weapon == Arms.FANGS && drawn && !h.blocking)
                r.mesh(Characters.FANG, new Xf().set(handL).translate(0, -.27f, 0).rotX(held));
            if (swinging) {
                if (p.trailN == TRAIL) {
                    System.arraycopy(hilt, 3, hilt, 0, (TRAIL - 1) * 3);
                    System.arraycopy(tip, 3, tip, 0, (TRAIL - 1) * 3);
                    p.trailN--;
                }
                apply(tmp, 0, 0, .25f, hilt, p.trailN);
                apply(tmp, 0, 0, Arms.TIP[g.gear.weapon], tip, p.trailN);
                p.trailN++;
            } else p.trailN = Math.max(0, p.trailN - 2);
        }
        if (g.gear.shield) {
            if (h.blocking) tmp.set(xf).translate(-.08f, 1.12f, .45f).rotX(M.PI / 2);
            else if ((drawn || h.locked) && g.gear.weapon != Arms.FANGS) tmp.set(handL).translate(-.07f, -.14f, 0).rotZ(M.PI / 2);
            else tmp.set(torso).translate(0, .28f, -.19f).rotX(-M.PI / 2);
            r.mesh(Characters.SHIELD, tmp);
        }
        if (g.gear.bow) {
            r.mesh(Models.QUIVER, new Xf().set(torso).translate(.16f, -.12f, -.17f).rotZ(-.35f).rotX(-.2f));
            if (bowMode) tmp.set(handL).translate(0, -.27f, 0).rotX(M.PI / 2);
            else tmp.set(torso).translate(-.08f, .3f, -.2f).rotZ(-.5f);
            r.mesh(Models.BOW, tmp);
            float pull = h.draw();
            boolean twang = bowMode && h.st == Hero.State.LOOSE && h.stT < .3f;
            if (bowMode && (pull > 0 || twang)) {
                float[] q = new float[12];
                apply(tmp, 0, .62f, -.075f, q, 0);
                apply(tmp, 0, -.62f, -.075f, q, 1);
                apply(tmp, 0, 0, -.075f + (twang ? M.sin(h.stT * 95) * (.3f - h.stT) * .35f : 0), q, 2);
                apply(new Xf().set(handR).translate(0, -.26f, 0), 0, 0, 0, q, 3);
                float mx = M.lerp(q[6], q[9], pull), my = M.lerp(q[7], q[10], pull), mz = M.lerp(q[8], q[11], pull);
                for (int e = 0; e < 2; e++)
                    for (int k = 0; k <= 16; k++)
                        r.point(M.lerp(q[e * 3], mx, k / 16f), M.lerp(q[e * 3 + 1], my, k / 16f), M.lerp(q[e * 3 + 2], mz, k / 16f), .008f, 0xece4d2, 256);
                if (pull > .15f) {
                    // The nocked arrow rests on the string and points out past the grip.
                    float[] grip = new float[3];
                    apply(new Xf().set(handL).translate(0, -.27f, 0), 0, 0, 0, grip, 0);
                    float ax = grip[0] - mx, ay = grip[1] - my, az = grip[2] - mz;
                    r.mesh(Models.ARROW, new Xf().translate(mx, my, mz).rotY(M.atan2(ax, az)).rotX(-M.atan2(ay, M.hypot(ax, az))).translate(0, 0, .52f));
                }
            } else r.mesh(Models.BOW_STRING, tmp);
        }
        if (h.st == Hero.State.AIM && h.aimClaw) r.mesh(Characters.CLAW, tmp.set(handL).translate(0, -.27f, 0).rotX(M.PI / 2));
        if (h.st == Hero.State.HOLD) {
            r.mesh(Characters.BOMB, tmp.set(xf).translate(0, 2.05f, .05f));
            if (((int) (h.holdT * 10) & 1) == 0) r.point(h.x, h.y + 2.3f, h.z, .08f, 0xffd060, 256);
        }
        if (h.st == Hero.State.FLUTE) r.mesh(Characters.FLUTE, tmp.set(head).translate(0, .1f, .2f));
        if (h.st == Hero.State.DRINK) r.mesh(Characters.DRAUGHT, tmp.set(handR).translate(0, -.3f, .05f));
        if (h.st == Hero.State.CAST) {
            tmp.set(handR).translate(0, -.3f, 0);
            int[] glow = {0xff9a3a, 0xbff0ff, 0xffe85a, 0x7af0d0, 0xfff0b0, 0xd8f4ff};
            r.point(tmp.m[3], tmp.m[7], tmp.m[11], .16f + M.sin(g.time * 40) * .03f, glow[g.gear.spell], 256);
            r.light(tmp.m[3], tmp.m[7], tmp.m[11], glow[g.gear.spell], 7);
        }
        if (g.shownItem != null) r.mesh(g.shownItem, tmp.identity().translate(h.x, h.y + 2.35f + M.sin(g.time * 3) * .05f, h.z).rotY(g.time * 1.5f));
    }

    private static void apply(Xf x, float px, float py, float pz, float[] out, int i) {
        float[] m = x.m;
        out[i * 3] = m[0] * px + m[1] * py + m[2] * pz + m[3];
        out[i * 3 + 1] = m[4] * px + m[5] * py + m[6] * pz + m[7];
        out[i * 3 + 2] = m[8] * px + m[9] * py + m[10] * pz + m[11];
    }

    // ---- projectiles, pickups and effects -----------------------------------------------------

    private void things() {
        float t = g.time;
        for (Game.Shot s : g.shots) {
            xf.identity().translate(s.x, s.y, s.z).rotY(M.atan2(s.vx, s.vz)).rotX(-M.atan2(s.vy, M.hypot(s.vx, s.vz)));
            r.mesh(switch (s.kind) {
                case Game.ARROW -> Models.ARROW;
                case Game.FIREBALL -> Models.FIREBALL;
                case Game.SEED -> Models.SEED;
                case Game.WATERBALL -> Models.WATERBALL;
                case Game.LANTERN -> Models.LANTERN;
                case Game.P_EMBER -> Characters.EMBER_BOLT;
                case Game.P_FROST -> Characters.FROST_LANCE;
                case Game.P_RADIANT -> Characters.RADIANT;
                default -> Models.ROCK_SHOT;
            }, s.kind == Game.ARROW || s.kind == Game.P_FROST || s.kind == Game.P_RADIANT ? xf : xf.rotZ(t * 8));
            if (s.kind == Game.FIREBALL || s.kind == Game.P_EMBER || s.kind == Game.LANTERN) r.light(s.x, s.y, s.z, 0xff8a3a, 9);
            else if (s.kind == Game.P_FROST || s.kind == Game.P_RADIANT) r.light(s.x, s.y, s.z, s.kind == Game.P_FROST ? 0x9fe8ff : 0xfff0b0, 8);
        }
        for (float[] st : g.magic.stars) {
            if (st[3] <= 0) r.mesh(Characters.STAR, xf.identity().translate(st[0], st[1], st[2]).rotY(t * 4));
            else for (int i = 0; i < 10; i++) {
                float a = i * M.TAU / 10 + t, f = Math.max(g.level.floor(st[0], st[2]), -2) + .2f;
                r.point(st[0] + M.sin(a) * 2.5f, f, st[2] + M.cos(a) * 2.5f, .14f, 0xfff0b0, 220);
            }
        }
        for (float[] ring : g.magic.rings) {
            float rad = 5.5f * ring[3] / .5f;
            for (int i = 0; i < 36; i++) {
                float a = i * M.TAU / 36, x = ring[0] + M.sin(a) * rad, z = ring[2] + M.cos(a) * rad;
                r.point(x, ring[1] + .5f + M.hash(i, (int) (t * 30)) * .8f, z, .16f, i % 3 == 0 ? 0xffffff : 0xffe85a, 256);
            }
        }
        for (Game.Bomb b : g.bombs) {
            float swell = b.fuse < 1 ? 1 + M.sin(t * 30) * .08f : 1;
            r.mesh(Characters.BOMB, xf.identity().translate(b.x, b.y, b.z).scale(swell));
        }
        if (g.clawOut) {
            Hero h = g.hero;
            float hx = h.x, hy = h.y + 1.4f, hz = h.z;
            for (int i = 1; i < 12; i++) {
                float k = i / 12f;
                r.point(M.lerp(hx, g.clawX, k), M.lerp(hy, g.clawY, k), M.lerp(hz, g.clawZ, k), .04f, 0x8a8e96, 256);
            }
            r.mesh(Characters.CLAW, xf.identity().translate(g.clawX, g.clawY, g.clawZ).rotY(M.atan2(g.clawDX, g.clawDZ)).rotX(-M.atan2(g.clawDY, M.hypot(g.clawDX, g.clawDZ))));
        }
        for (Game.Drop dr : g.drops) {
            if (dr.life < 3 && ((int) (t * 10) & 1) == 0) continue;
            xf.identity().translate(dr.x, dr.y + .15f + M.sin(t * 3 + dr.x) * .06f, dr.z).rotY(t * 2);
            r.mesh(switch (dr.item) {
                case Places.HEART -> Models.HEART;
                case Places.ARROWS -> Models.ARROW_BUNDLE;
                case Places.BOMBS -> Models.BOMB_BAG;
                case Game.CONTAINER -> Models.HEART_CONTAINER;
                default -> Models.GLEAM[Math.min(2, dr.item)];
            }, dr.item == Game.CONTAINER ? xf.translate(0, .8f, 0) : xf);
        }
    }

    private void effects() {
        Hero h = g.hero;
        if (g.screen != Game.Screen.TITLE) shadow(h.x, h.z, .5f, h.y);
        for (Foe f : g.foes) if (f.alive() && M.dist(f.x, f.z, r.cx, r.cz) < 60) shadow(f.x, f.z, Math.min(2.5f, f.radius()), f.kind == Foe.Kind.BAT || f.kind == Foe.Kind.KING ? f.y - 2 : f.y);
        for (Game.Spark s : g.sparks) r.point(s.x, s.y, s.z, .08f, s.col, 256);
        for (Game.Shot s : g.shots)
            if (s.kind == Game.ARROW)
                for (int i = 1; i <= 3; i++) r.point(s.x - s.vx * .012f * i, s.y - s.vy * .012f * i, s.z - s.vz * .012f * i, .05f, 0xffffff, 170 - i * 40);
        if (g.lostGleams > 0 && g.lostLevel == g.dungeonIndex)
            for (int i = 0; i < 7; i++)
                r.point(g.lostX + M.sin(g.time * 2 + i) * .25f, g.lostY + .2f + i * .28f + M.sin(g.time * 3 + i) * .05f, g.lostZ + M.cos(g.time * 2 + i) * .25f, .14f, 0x5ef08a, 210);
        if (g.gear.ascended && g.screen != Game.Screen.TITLE)
            for (int i = 0; i < 6; i++) {
                float a = g.time * 2.5f + i * 1.05f, k = (g.time * .7f + i / 6f) % 1;
                r.point(h.x + M.sin(a) * .65f, h.y + .2f + k * 2, h.z + M.cos(a) * .65f, .06f, 0xffd870, (int) (230 * (1 - k)));
            }
        for (float[] w : g.waves)
            for (int i = 0; i < 40; i++) {
                float a = i * M.TAU / 40, x = w[0] + M.sin(a) * w[2], z = w[1] + M.cos(a) * w[2];
                r.point(x, g.level.floor(x, z) + .4f, z, .35f, 0xff8a3a, 210);
            }
        float[] goal = new float[3];
        if (g.dungeon == null && g.screen == Game.Screen.PLAY && g.story.objectiveTarget(goal) && M.dist(h.x, h.z, goal[0], goal[2]) > 18)
            r.mesh(Models.BEACON, xf.identity().translate(goal[0], g.world.height(goal[0], goal[2]), goal[2]), 55);
        if (g.dungeon == null) {
            for (Places.Gate gt : g.world.places.gates) {
                if (M.dist(gt.x, gt.z, r.cx, r.cz) > 300) continue;
                xf.identity().translate(gt.x, gt.y, gt.z).rotY(M.PI);
                if (gt.kind == Places.TIDE && !gt.open) r.mesh(Models.CURTAIN, xf, 150);
                if (gt.kind == Places.CROWN && g.gear.stones != 15) r.mesh(Models.VEIL, xf, 200);
            }
        }
        if (g.dungeon != null) for (Dungeon.Thing t : g.dungeon.things)
            if (t.kind == 't' && t.on && M.dist(t.x, t.z, r.cx, r.cz) < 30) r.point(t.x, t.y + 1.7f, t.z, .7f + M.sin(g.time * 9 + t.i) * .08f, 0xff9a40, 70);
    }

    /** Soft round shadow on whatever floor is below. */
    private void shadow(float x, float z, float rad, float y) {
        if (r.gpu != null) return; // the card casts real shadows
        float f = g.level.floor(x, z);
        if (f < -20) return;
        int a = (int) (100 * M.clamp(1 - (y - f) / 12, 0, 1));
        if (a < 8) return;
        float px = x + rad, pz = z, py = g.level.floor(px, pz) + .06f;
        for (int i = 1; i <= 8; i++) {
            float an = i * M.TAU / 8, qx = x + M.cos(an) * rad, qz = z + M.sin(an) * rad, qy = g.level.floor(qx, qz) + .06f;
            if (Math.abs(qy - f) < 1 && Math.abs(py - f) < 1) r.worldTri(x, f + .06f, z, qx, qy, qz, px, py, pz, 0x101418, true, a);
            px = qx;
            pz = qz;
            py = qy;
        }
    }
}

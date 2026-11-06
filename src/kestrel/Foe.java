package kestrel;

/** Every enemy and boss, with its behaviour, weak points and drawing. */
final class Foe {
    enum Kind { THORNLING, BAT, SENTRY, SLIME, IMP, WOLF, STALKER, BRAMBLE, GOLEM, SERPENT, KING, HOARWING, ASHMAW, STORMDRAKE }

    static final int IDLE = 0, CHASE = 1, WINDUP = 2, ATTACK = 3, RECOVER = 4, STUN = 5, DEAD = 6, HIDE = 7, PERCH = 8;
    static final int SWORD = 0, SPIN = 1, ARROW = 2, BOMB = 3, CLAW = 4, STAR = 5;

    final Kind kind;
    final boolean boss;
    float x, y, z, yaw, vy, homeX, homeZ, homeY, t, flash, deadT, alert, size = 1, cool, aux, tx, tz, headX, headY, headZ;
    int state, si = -1, sj = -1, bits;
    float hp, maxHp, frozenT, shieldT;
    /** Set by a parry: while it lasts, the next sword blow is a riposte. */
    float riposteT;
    boolean armour = true;
    private final float[] pose;
    private final Rig.Anim an = new Rig.Anim();
    private final Rig rig;

    Foe(Kind kind, float x, float y, float z) {
        this.kind = kind;
        this.x = homeX = tx = x;
        this.y = homeY = y;
        this.z = homeZ = tz = z;
        boss = kind.ordinal() >= Kind.BRAMBLE.ordinal();
        hp = maxHp = switch (kind) {
            case THORNLING, IMP -> 3;
            case BAT -> 1;
            case SENTRY -> 6;
            case SLIME -> 4;
            case WOLF -> 5;
            case STALKER -> 4;
            case KING -> 20;
            case HOARWING -> 14;
            case ASHMAW -> 16;
            case STORMDRAKE -> 22;
            default -> 12;
        };
        rig = switch (kind) {
            case THORNLING -> Characters.THORNLING;
            case SENTRY -> Characters.SENTRY;
            case IMP -> Characters.IMP;
            case BAT -> Characters.BAT;
            case BRAMBLE -> Characters.BRAMBLE;
            case GOLEM -> Characters.GOLEM;
            case KING -> Characters.KING;
            case WOLF -> Characters.WOLF;
            case HOARWING -> Characters.HOARWING;
            case STALKER -> Characters.STALKER;
            case STORMDRAKE -> Characters.STORMDRAKE;
            default -> null;
        };
        pose = rig == null ? null : rig.newPose();
        state = boss ? HIDE : IDLE;
        yaw = M.hash((int) x, (int) z) * M.TAU;
    }

    boolean alive() { return state != DEAD; }

    /** Gleams the hero earns for the kill. */
    int reward() {
        return switch (kind) {
            case THORNLING -> 6;
            case BAT -> 3;
            case SENTRY -> 18;
            case SLIME -> size < .8f ? 3 : 8;
            case IMP -> 10;
            case WOLF -> 12;
            case HOARWING -> 1000;
            case STALKER -> 14;
            case ASHMAW -> 1200;
            case STORMDRAKE -> 2500;
            case BRAMBLE -> 250;
            case GOLEM -> 500;
            case SERPENT -> 800;
            case KING -> 3000;
        };
    }

    float radius() {
        return switch (kind) {
            case BAT -> .5f;
            case SLIME -> .6f * size;
            case BRAMBLE -> 2.2f;
            case GOLEM -> 2.4f;
            case SERPENT -> 1.4f;
            case KING -> 1.2f;
            case HOARWING -> 2f;
            case STALKER -> .6f;
            case ASHMAW -> 1.6f;
            case STORMDRAKE -> 3;
            default -> .55f;
        };
    }

    float centerY() {
        return switch (kind) {
            case BAT, KING, HOARWING, STORMDRAKE -> y;
            case ASHMAW -> headY;
            case STALKER -> y + .45f;
            case SLIME -> y + .5f * size;
            case BRAMBLE -> headY;
            case GOLEM -> y + 4.4f;
            case SERPENT -> headY;
            default -> y + 1;
        };
    }

    static final int POINTS = 3;

    /** Lock-on spot i: a boss's weak points, or a lesser foe's body. False while that spot isn't a target. */
    boolean point(int i, float[] out) {
        if (!alive() || state == HIDE) return false;
        return switch (kind) {
            case BRAMBLE -> {
                if (i < 2) {
                    if (state == STUN || (bits & 1 << i) != 0) yield false;
                    float s = i == 0 ? -1 : 1;
                    yield put(out, headX + M.cos(yaw) * 1.6f * s, headY + 1.6f, headZ - M.sin(yaw) * 1.6f * s);
                }
                yield i == 2 && state == STUN && put(out, headX, headY + .9f, headZ);
            }
            case GOLEM -> i == 0 ? put(out, x + M.sin(yaw) * 1.6f, y + (state == STUN ? 2.2f : 4.6f), z + M.cos(yaw) * 1.6f)
                    : i == 1 && state == RECOVER && put(out, tx, y + .6f, tz);
            case SERPENT -> i == 0 && state != CHASE && put(out, headX, headY, headZ);
            case KING -> i == 0 && put(out, x + M.sin(yaw) * .3f, y + 2.45f, z + M.cos(yaw) * .3f);
            case ASHMAW -> i == 0 && state == RECOVER && put(out, headX + M.sin(yaw) * .95f, headY - .1f, headZ + M.cos(yaw) * .95f);
            case STORMDRAKE -> (bits & 1 << i) == 0 && state != STUN && state != IDLE && drakeScale(i, out);
            case HOARWING -> i == 0 ? put(out, x + M.sin(yaw) * .9f, y - .1f, z + M.cos(yaw) * .9f) : i == 1 && state == STUN && put(out, x, y + 1.6f, z);
            default -> i == 0 && put(out, x, centerY(), z);
        };
    }

    private static boolean put(float[] o, float x, float y, float z) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
        return true;
    }

    /** The next active lock-on spot after i (wrapping round), or -1 if there is none. */
    int nextPoint(int i, float[] tmp) {
        for (int k = 1; k <= POINTS; k++) {
            int j = (i + k) % POINTS;
            if (point(j, tmp)) return j;
        }
        return -1;
    }

    /** Where a hit at this point lands: 2 for the weak point, 1 for the body, 0 for a miss. */
    int hitTest(float px, float py, float pz) {
        if (!alive() || state == HIDE) return 0;
        switch (kind) {
            case BRAMBLE -> {
                for (int s = -1; s <= 1; s += 2) {
                    float bx = headX + M.cos(yaw) * 1.6f * s, bz = headZ - M.sin(yaw) * 1.6f * s;
                    if (d3(px, py, pz, bx, headY + 1.6f, bz) < 1.5f) return 2;
                }
                return d3(px, py, pz, headX, headY + .9f, headZ) < 1.8f || M.dist(px, pz, x, z) < 1.2f && py < y + 4.8f ? 1 : 0;
            }
            case GOLEM -> {
                float cx = x + M.sin(yaw) * 1.6f, cz = z + M.cos(yaw) * 1.6f;
                if (d3(px, py, pz, cx, y + 4.6f, cz) < 1.5f) return 2;
                return M.dist(px, pz, x, z) < 2.6f && py < y + 8 ? 1 : 0;
            }
            case SERPENT -> {
                return d3(px, py, pz, headX, headY, headZ) < 1.8f ? 2 : 0;
            }
            case ASHMAW -> {
                if (state == WINDUP) return 0;
                if (state == RECOVER && d3(px, py, pz, headX + M.sin(yaw) * .95f, headY - .1f, headZ + M.cos(yaw) * .95f) < 1.3f) return 2;
                return d3(px, py, pz, headX, headY, headZ) < 1.9f ? 1 : 0;
            }
            case STORMDRAKE -> {
                if (state == STUN && d3(px, py, pz, headX, headY, headZ) < 2.2f) return 2;
                float[] p = new float[3];
                for (int i = 0; i < 3; i++) if (point(i, p) && d3(px, py, pz, p[0], p[1], p[2]) < 1.5f) return 2;
                return d3(px, py, pz, x, y, z) < 3.2f ? 1 : 0;
            }
            case HOARWING -> {
                if (d3(px, py, pz, x + M.sin(yaw) * .9f, y - .1f, z + M.cos(yaw) * .9f) < 1.3f) return 2;
                return d3(px, py, pz, x, y + .3f, z) < 2.2f ? 1 : 0;
            }
            case KING -> {
                if (d3(px, py, pz, x + M.sin(yaw) * .3f, y + 2.45f, z + M.cos(yaw) * .3f) < 1.2f) return 2;
                return d3(px, py, pz, x, y + 1f, z) < 1.1f ? 1 : 0;
            }
            default -> {
                return d3(px, py, pz, x, centerY(), z) < radius() + .5f ? 1 : 0;
            }
        }
    }

    private static float d3(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = ax - bx, dy = ay - by, dz = az - bz;
        return M.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Something hit this foe at a point. Returns true if the blow connected (armour and shields say no). */
    /** Knocked off balance by a parry: lesser foes reel wide open, and the next sword blow is a riposte. */
    void stagger() {
        riposteT = 1.6f;
        if (boss || kind == Kind.BAT || kind == Kind.SLIME) return;
        setState(STUN);
        tx = tz = 0;
        t = -1.1f;
        flash = 1;
    }

    boolean damage(Game g, int src, float dmg, float px, float py, float pz) {
        if (!alive() || state == HIDE) return false;
        if (riposteT > 0 && (src == SWORD || src == SPIN)) {
            riposteT = 0;
            dmg *= g.gear.weapon == Arms.FANGS ? 5 : 3;
            g.say("Riposte!");
            g.shake(.45f);
            g.sparks(px, py, pz, 0xffd870, 28);
        }
        int where = hitTest(px, py, pz);
        if (boss && (src == ARROW || src == CLAW)) {
            // Shots that land close to a weak point count as hitting it, so armour around it can't swallow them.
            float[] p = new float[3];
            for (int i = 0; i < POINTS; i++)
                if (point(i, p) && d3(px, py, pz, p[0], p[1], p[2]) < 1.8f) {
                    where = 2;
                    px = p[0];
                    py = p[1];
                    pz = p[2];
                    break;
                }
        }
        Hero h = g.hero;
        if (src == STAR && boss) dmg *= .6f;
        if (src != STAR) switch (kind) {
            case SENTRY -> {
                float toHero = M.atan2(h.x - x, h.z - z);
                if ((src == CLAW || src == SWORD && g.gear.weapon == Arms.HAMMER) && armour) {
                    // The claw tears the tower shield away for a while; the hammer smashes it aside.
                    armour = false;
                    shieldT = 7;
                    g.sparks(x, y + 1.1f, z, 0x8a8e96, 12);
                    g.sfx(Audio.CLANG);
                    g.say("You rip its shield away!");
                    setState(STUN);
                    t = 0;
                    return true;
                }
                if ((src == SWORD || src == ARROW) && armour && state != RECOVER && state != STUN && Math.abs(M.angleDiff(yaw, toHero)) < 1.2f) {
                    clang(g);
                    return false;
                }
            }
            case BRAMBLE -> {
                if (where == 2 && src == ARROW) {
                    float side = (px - headX) * M.cos(yaw) - (pz - headZ) * M.sin(yaw);
                    bits |= side < 0 ? 1 : 2;
                    flash = 1;
                    g.sfx(Audio.HIT);
                    if (bits == 3) stun(g, 4.5f);
                    return true;
                }
                if (state != STUN) {
                    clang(g);
                    return false;
                }
            }
            case GOLEM -> {
                if (src == BOMB && state == RECOVER && M.dist(px, pz, tx, tz) < 3.5f) {
                    armour = false;
                    stun(g, 6);
                    g.say("Its armour cracks! Strike the glowing core!");
                    return true;
                }
                if (armour || where != 2 && src != BOMB) {
                    if (src != BOMB) clang(g);
                    return false;
                }
            }
            case SERPENT -> {
                if (src == CLAW && armour && state == RECOVER) {
                    armour = false;
                    stun(g, 4.5f);
                    g.say("The mask tears free!");
                    return true;
                }
                if (armour || where == 0) {
                    if (src == SWORD || src == ARROW) clang(g);
                    return false;
                }
            }
            case ASHMAW -> {
                if (state == WINDUP) return false;
                if (state == RECOVER && (src == ARROW || src == BOMB) && where == 2) {
                    stun(g, 5);
                    g.shake(.7f);
                    g.sfx(Audio.SLAM);
                    g.say("Ashmaw chokes and crashes into the sand! Strike its head!");
                    return true;
                }
                if (state != STUN) {
                    if (src == SWORD || src == SPIN || src == ARROW) clang(g);
                    return false;
                }
            }
            case STORMDRAKE -> {
                if (state != STUN) {
                    float[] p = new float[3];
                    if (src == ARROW || src == CLAW)
                        for (int i = 0; i < 3; i++)
                            if (point(i, p) && d3(px, py, pz, p[0], p[1], p[2]) < 2.3f) {
                                bits |= 1 << i;
                                flash = 1;
                                g.sparks(p[0], p[1], p[2], 0xffe14a, 18);
                                g.sfx(Audio.STUN);
                                if (bits == 7) {
                                    stun(g, 7);
                                    g.shake(.9f);
                                    g.say("The Stormdrake crashes to the ground! Strike its head!");
                                } else g.say("A storm-scale goes dark! (" + Integer.bitCount(bits) + "/3)");
                                return true;
                            }
                    if (src == SWORD || src == SPIN || src == ARROW) clang(g);
                    return false;
                }
            }
            case HOARWING -> {
                if (src == CLAW && state == PERCH) {
                    stun(g, 5);
                    g.shake(.8f);
                    g.sfx(Audio.SLAM);
                    g.say("Hoarwing crashes to the ice! Strike it!");
                    return true;
                }
                if (state != STUN) {
                    if (src == SWORD || src == SPIN || src == ARROW) clang(g);
                    return false;
                }
            }
            case KING -> {
                if (state != STUN) {
                    if (src == ARROW && where == 2 && aux < .5f) {
                        stun(g, 4);
                        return true;
                    }
                    if (src == CLAW && where > 0 && aux >= .5f) {
                        stun(g, 4);
                        g.say("You drag the Hollow King down!");
                        return true;
                    }
                    if (src == SWORD || src == SPIN) clang(g);
                    return false;
                }
            }
            default -> {}
        }
        if (!boss && src == CLAW && kind != Kind.SENTRY) {
            // The claw yanks lesser foes off their feet and drags them in.
            float dx = h.x - x, dz = h.z - z, d = Math.max(.5f, M.hypot(dx, dz));
            tx = dx / d * Math.min(14, d * 2.5f);
            tz = dz / d * Math.min(14, d * 2.5f);
            setState(STUN);
            flash = 1;
            g.sfx(Audio.CLAW);
            return true;
        }
        if (!boss && (src == SWORD || src == SPIN) && g.gear.frostbrand) frozenT = 1.2f;
        hp -= dmg;
        flash = 1;
        g.sfx(Audio.HIT);
        g.hitStop(.05f);
        if (!boss) {
            float dx = x - h.x, dz = z - h.z, d = Math.max(.01f, M.hypot(dx, dz));
            tx = dx / d * 6;
            tz = dz / d * 6;
            if (kind != Kind.BAT) setState(STUN);
        }
        if (hp <= 0) {
            hp = 0;
            setState(DEAD);
            g.foeDied(this);
        }
        return true;
    }

    /** True when nothing solid stands between this foe and the hero: walls block sight. */
    boolean sees(Game g) {
        Hero h = g.hero;
        float y0 = kind == Kind.BAT ? y : y + 1.2f, dx = h.x - x, dz = h.z - z;
        int n = (int) (M.hypot(dx, dz) / .5f);
        for (int i = 1; i < n; i++) {
            float k = i / (float) n;
            if (g.level.floor(x + dx * k, z + dz * k) > M.lerp(y0, h.y + 1.2f, k)) return false;
        }
        return true;
    }

    private void clang(Game g) {
        g.sfx(Audio.CLANG);
        g.sparks(x, centerY(), z, 0xfff0c0, 6);
    }

    private void stun(Game g, float secs) {
        setState(STUN);
        aux = secs;
        stunTime = secs;
        flash = 1;
        g.sfx(Audio.STUN);
    }

    void setState(int s) {
        state = s;
        t = 0;
    }

    void update(float dt, Game g) {
        flash = Math.max(0, flash - dt * 4);
        alert = Math.max(0, alert - dt);
        t += dt;
        cool -= dt;
        riposteT -= dt;
        if (rig != null) an.update(dt);
        if (state == DEAD) {
            deadT += dt;
            return;
        }
        if (frozenT > 0) {
            frozenT -= dt;
            return;
        }
        Hero h = g.hero;
        float dx = h.x - x, dz = h.z - z, d = M.hypot(dx, dz), face = M.atan2(dx, dz);
        boolean near = Math.abs(h.y - y) < 3 && h.st != Hero.State.DEAD;
        switch (kind) {
            case THORNLING -> thornling(dt, g, d, face, near);
            case SENTRY -> sentry(dt, g, d, face, near);
            case IMP -> imp(dt, g, d, face, near);
            case BAT -> bat(dt, g, d);
            case SLIME -> slime(dt, g, d, face);
            case BRAMBLE -> bramble(dt, g, d, face);
            case GOLEM -> golem(dt, g, d, face);
            case SERPENT -> serpent(dt, g, d, face);
            case KING -> king(dt, g, d, face);
            case WOLF -> wolf(dt, g, d, face, near);
            case HOARWING -> hoarwing(dt, g, d, face);
            case STALKER -> stalker(dt, g, d, face, near);
            case ASHMAW -> ashmaw(dt, g, d, face);
            case STORMDRAKE -> drake(dt, g, d, face);
        }
    }

    // ---- small foes ---------------------------------------------------------------------------

    private boolean knockback(float dt, Game g) {
        if (state != STUN || boss) return false;
        walk(g, tx * dt, tz * dt);
        tx *= .88f;
        tz *= .88f;
        if (t > .35f) setState(CHASE);
        return true;
    }

    private void walk(Game g, float dx, float dz) {
        float nx = x + dx, nz = z + dz, f = g.level.floor(nx, nz);
        if (Math.abs(f - y) < .7f && Float.isNaN(g.level.water(nx, nz))) {
            x = nx;
            z = nz;
            y = f;
        }
    }

    private void chase(Game g, float speed, float dt, float face) {
        yaw = M.turn(yaw, face, 7 * dt);
        walk(g, M.sin(yaw) * speed * dt, M.cos(yaw) * speed * dt);
        aux += dt * speed * 1.6f;
    }

    private void thornling(float dt, Game g, float d, float face, boolean near) {
        if (knockback(dt, g)) return;
        switch (state) {
            case IDLE -> {
                if (t > 2.5f) {
                    setState(IDLE);
                    yaw = M.atan2(homeX - x, homeZ - z) + (M.hash((int) (g.time * 10), 3) - .5f) * 2;
                }
                if (t < 1.2f) chase(g, 1.2f, dt, yaw);
                if (d < 12 && near && sees(g)) {
                    setState(CHASE);
                    alert = 1;
                }
            }
            case CHASE -> {
                if (d > 30) setState(IDLE);
                if (d > 1.6f) chase(g, 3.6f, dt, face);
                else yaw = M.turn(yaw, face, 8 * dt);
                if (d < 2 && near) {
                    setState(WINDUP);
                    an.restart(Characters.SMASH, .05f);
                }
            }
            case WINDUP -> {
                if (t > .35f) {
                    if (d < 2.4f && Math.abs(M.angleDiff(yaw, face)) < 1) g.hurtHero(2, x, z, true);
                    setState(RECOVER);
                }
            }
            case RECOVER -> { if (t > .6f) setState(CHASE); }
            default -> setState(CHASE);
        }
    }

    private void sentry(float dt, Game g, float d, float face, boolean near) {
        if (shieldT > 0 && (shieldT -= dt) <= 0) armour = true;
        if (knockback(dt, g)) return;
        switch (state) {
            case IDLE -> { if (d < 14 && near && sees(g)) { setState(CHASE); alert = 1; } }
            case CHASE -> {
                yaw = M.turn(yaw, face, 2.8f * dt);
                if (d > 2.6f) {
                    walk(g, M.sin(yaw) * 1.8f * dt, M.cos(yaw) * 1.8f * dt);
                    aux += dt * 3;
                }
                if (d < 3 && cool <= 0) {
                    setState(WINDUP);
                    an.restart(Characters.THRUST, .05f);
                }
            }
            case WINDUP -> {
                if (t > .5f) {
                    if (d < 3.4f && Math.abs(M.angleDiff(yaw, face)) < .8f) g.hurtHero(3, x, z, true);
                    setState(RECOVER);
                    cool = 1.5f;
                }
            }
            case RECOVER -> { if (t > 1) setState(CHASE); }
            default -> setState(CHASE);
        }
    }

    private void imp(float dt, Game g, float d, float face, boolean near) {
        if (knockback(dt, g)) return;
        switch (state) {
            case IDLE -> { if (d < 16 && near && sees(g)) { setState(CHASE); alert = 1; } }
            case CHASE -> {
                yaw = M.turn(yaw, face, 6 * dt);
                float side = M.sin(g.time * .9f + homeX) * 1.5f;
                float back = d < 6 ? -2.5f : d > 11 ? 2.5f : 0;
                walk(g, (M.sin(yaw) * back + M.cos(yaw) * side) * dt, (M.cos(yaw) * back - M.sin(yaw) * side) * dt);
                aux += dt * 3;
                if (t > 2.2f && d < 16 && sees(g)) {
                    setState(WINDUP);
                    an.restart(Characters.HURL, .05f);
                }
            }
            case WINDUP -> {
                if (t > .4f) {
                    g.shoot(Game.FIREBALL, x, y + 1.3f, z, g.hero.x, g.hero.y + 1, g.hero.z, 11);
                    setState(RECOVER);
                }
            }
            case RECOVER -> { if (t > .5f) setState(CHASE); }
            default -> setState(CHASE);
        }
    }

    private void bat(float dt, Game g, float d) {
        aux += dt;
        Hero h = g.hero;
        if (state == ATTACK) {
            float ddx = h.x - x, ddy = h.y + 1 - y, ddz = h.z - z, l = Math.max(.1f, M.sqrt(ddx * ddx + ddy * ddy + ddz * ddz));
            float nx = x + ddx / l * 7 * dt, ny = y + ddy / l * 7 * dt, nz = z + ddz / l * 7 * dt;
            if (g.level.floor(nx, nz) > ny - .3f) {
                setState(RECOVER);
                return;
            }
            x = nx;
            y = ny;
            z = nz;
            yaw = M.atan2(ddx, ddz);
            if (l < 1) {
                g.hurtHero(2, x, z, true);
                setState(RECOVER);
            }
            if (t > 1.6f) setState(RECOVER);
            return;
        }
        float floor = g.level.floor(homeX, homeZ);
        float gx = homeX + M.sin(aux * 1.3f) * 3, gz = homeZ + M.cos(aux * 1.1f) * 3, gy = floor + 2.8f + M.sin(aux * 3) * .4f;
        float nx = M.approach(x, gx, 2, dt), nz = M.approach(z, gz, 2, dt);
        if (g.level.floor(nx, z) < y - .3f) x = nx;
        if (g.level.floor(x, nz) < y - .3f) z = nz;
        y = M.approach(y, gy, 2, dt);
        yaw = M.atan2(gx - x, gz - z);
        if ((state == RECOVER && t > 1.4f || state == IDLE && t > 1) && d < 9 && sees(g)) setState(ATTACK);
    }

    private void slime(float dt, Game g, float d, float face) {
        if (state == STUN) {
            walk(g, tx * dt, tz * dt);
            tx *= .88f;
            tz *= .88f;
            if (t > .35f) setState(CHASE);
            return;
        }
        float f = g.level.floor(x, z);
        if (y > f || vy > 0) {
            vy -= 22 * dt;
            y += vy * dt;
            walk(g, M.sin(yaw) * 2.6f * dt, M.cos(yaw) * 2.6f * dt);
            y = Math.max(y, g.level.floor(x, z));
            if (y <= g.level.floor(x, z) && vy < 0) {
                vy = 0;
                aux = 0;
            }
        } else if ((aux += dt) > (d < 12 ? .7f : 2)) {
            yaw = d < 12 ? face : M.hash((int) x, (int) (g.time * 3)) * M.TAU;
            vy = 6 * Math.min(1, size + .2f);
            y += .01f;
        }
        if (d < .9f * size + .4f && Math.abs(g.hero.y - y) < 1.2f) g.hurtHero(2, x, z, true);
    }

    // ---- bosses -------------------------------------------------------------------------------

    private void bramble(float dt, Game g, float d, float face) {
        if (state == HIDE) return;
        boolean down = state == STUN;
        float lean = down ? 1.15f : 0;
        headX = x + M.sin(yaw) * (2.4f * M.sin(lean));
        headZ = z + M.cos(yaw) * (2.4f * M.sin(lean));
        headY = y + (down ? 1.2f : 5.5f);
        if (!down) yaw = M.turn(yaw, face, .9f * dt);
        switch (state) {
            case STUN -> {
                if (t > aux) {
                    bits = 0;
                    setState(CHASE);
                }
            }
            case CHASE -> {
                if (t > (hp < 6 ? 1.6f : 2.4f)) {
                    setState(WINDUP);
                    tx = g.hero.x;
                    tz = g.hero.z;
                    cool = d < 10 ? 0 : 1;
                }
            }
            case WINDUP -> {
                if (cool == 0 && t > 1.1f) {
                    if (M.dist(g.hero.x, g.hero.z, tx, tz) < 2.4f) g.hurtHero(4, tx, tz, false);
                    g.shake(.4f);
                    g.sparks(tx, y + .3f, tz, 0x4a7a34, 12);
                    g.sfx(Audio.SLAM);
                    setState(CHASE);
                } else if (cool == 1 && t > .6f) {
                    for (int i = -1; i <= 1; i++)
                        g.shoot(Game.SEED, headX, headY + .5f, headZ, g.hero.x + i * 2.5f, g.hero.y + 1, g.hero.z + i * 1.5f, 13);
                    setState(CHASE);
                }
            }
            default -> setState(CHASE);
        }
    }

    private void golem(float dt, Game g, float d, float face) {
        if (state == HIDE) return;
        switch (state) {
            case CHASE -> {
                yaw = M.turn(yaw, face, 1.2f * dt);
                if (d > 5) walk(g, M.sin(yaw) * 1.6f * dt, M.cos(yaw) * 1.6f * dt);
                aux += dt * 2;
                if (t > 2.2f) {
                    setState(WINDUP);
                    bits = d < 7 ? 0 : (int) (g.time * 7) % 2 + 1;
                }
            }
            case WINDUP -> {
                if (bits == 0) yaw = M.turn(yaw, face, 1.5f * dt);
                if (t > 1.1f) {
                    if (bits == 0) {
                        tx = x + M.sin(yaw) * 3.6f;
                        tz = z + M.cos(yaw) * 3.6f;
                        if (M.dist(g.hero.x, g.hero.z, tx, tz) < 3) g.hurtHero(4, tx, tz, false);
                        g.shockwave(tx, tz);
                        setState(RECOVER);
                    } else if (bits == 1) {
                        g.shockwave(x, z);
                        setState(CHASE);
                    } else {
                        for (int i = 0; i < 5; i++) {
                            float a = i * 1.3f + g.time, r = i == 0 ? 0 : 3.5f;
                            g.shoot(Game.ROCK, g.hero.x + M.sin(a) * r, y + 14, g.hero.z + M.cos(a) * r, g.hero.x + M.sin(a) * r, y, g.hero.z + M.cos(a) * r, 0);
                        }
                        setState(CHASE);
                    }
                    g.shake(.6f);
                    g.sfx(Audio.SLAM);
                }
            }
            // The fist stays stuck long enough for a freshly lit bomb (3 s fuse) to go off beside it.
            case RECOVER -> { if (t > 4.5f) setState(CHASE); }
            case STUN -> {
                if (t > aux) {
                    armour = true;
                    setState(CHASE);
                }
            }
            default -> setState(CHASE);
        }
    }

    private void serpent(float dt, Game g, float d, float face) {
        if (state == HIDE) return;
        Hero h = g.hero;
        switch (state) {
            case CHASE -> {
                headY = y - 3;
                if (t > 1.3f) {
                    float a = M.atan2(h.x - homeX, h.z - homeZ);
                    x = homeX + M.sin(a) * 6;
                    z = homeZ + M.cos(a) * 6;
                    yaw = M.atan2(h.x - x, h.z - z);
                    setState(WINDUP);
                    g.splash(x, 0, z);
                }
            }
            case WINDUP -> {
                float rise = M.smooth(0, .6f, t);
                headX = x - M.sin(yaw) * 1.5f;
                headZ = z - M.cos(yaw) * 1.5f;
                headY = M.lerp(-2, 5.5f, rise);
                yaw = M.turn(yaw, M.atan2(h.x - x, h.z - z), 2 * dt);
                if (t > 1.2f && t - dt <= 1.2f && hp < 7)
                    g.shoot(Game.WATERBALL, headX, headY, headZ, h.x, h.y + 1, h.z, 14);
                if (t > 1.6f) {
                    tx = h.x;
                    tz = h.z;
                    setState(ATTACK);
                }
            }
            case ATTACK -> {
                float k = M.smooth(0, .35f, t);
                headX = M.lerp(x, tx, k);
                headZ = M.lerp(z, tz, k);
                headY = M.lerp(5.5f, g.level.floor(tx, tz) + .9f, k);
                if (k >= 1) {
                    if (M.dist(h.x, h.z, tx, tz) < 2) g.hurtHero(4, tx, tz, true);
                    g.shake(.4f);
                    g.sfx(Audio.SLAM);
                    setState(RECOVER);
                }
            }
            case RECOVER -> {
                if (t > 2.4f) {
                    setState(CHASE);
                    g.splash(x, 0, z);
                }
            }
            case STUN -> {
                if (t > aux) {
                    armour = true;
                    setState(CHASE);
                }
            }
            default -> setState(CHASE);
        }
    }

    private void king(float dt, Game g, float d, float face) {
        if (state == HIDE) return;
        Hero h = g.hero;
        boolean second = hp <= maxHp / 2;
        yaw = M.turn(yaw, face, 2 * dt);
        if (state == STUN) {
            y = M.approach(y, homeY + .2f, 6, dt);
            if (t > aux) setState(CHASE);
            return;
        }
        aux = second && (int) (g.time / 6) % 2 == 1 ? 1 : 0;
        float hx = homeX + M.sin(g.time * .35f) * 9, hz = homeZ + M.cos(g.time * .27f) * 7;
        x = M.approach(x, hx, .8f, dt);
        z = M.approach(z, hz, .8f, dt);
        y = M.approach(y, homeY + (aux > .5f ? 6 : 4) + M.sin(g.time * 2) * .3f, 2, dt);
        if (state == CHASE && t > (second ? 1.8f : 2.6f)) {
            setState(WINDUP);
            bits = second && (int) (g.time * 3) % 2 == 0 ? 1 : 0;
            tx = h.x;
            tz = h.z;
        }
        if (state == WINDUP && t > (bits == 1 ? 1.1f : .7f)) {
            if (bits == 1) {
                if (M.dist(h.x, h.z, tx, tz) < 3.2f) g.hurtHero(4, tx, tz, false);
                g.shake(.6f);
                g.sfx(Audio.SLAM);
                g.sparks(tx, homeY + .3f, tz, 0x9fe8ff, 14);
            } else {
                g.shoot(Game.LANTERN, x, y + 2, z, h.x, h.y + 1, h.z, 15);
                if (second) g.shoot(Game.LANTERN, x, y + 2, z, h.x + 3, h.y + 1, h.z - 3, 15);
            }
            setState(CHASE);
        }
    }

    /** Whether the claw can bite this foe: lesser foes always, bosses only while they're open to it. */
    boolean clawable() {
        if (!alive() || state == HIDE) return false;
        return !boss || kind == Kind.HOARWING && state == PERCH || kind == Kind.SERPENT || kind == Kind.KING;
    }

    private void wolf(float dt, Game g, float d, float face, boolean near) {
        if (knockback(dt, g)) return;
        switch (state) {
            case IDLE -> {
                if (t > 3) {
                    setState(IDLE);
                    yaw = M.atan2(homeX - x, homeZ - z) + (M.hash((int) (g.time * 10), 7) - .5f) * 2;
                }
                if (t < 1.5f) chase(g, 1.5f, dt, yaw);
                if (d < 16 && near && sees(g)) {
                    setState(CHASE);
                    alert = 1;
                }
            }
            case CHASE -> {
                if (d > 34) setState(IDLE);
                if (d > 6) chase(g, 5.5f, dt, face);
                else {
                    // Circle the prey, waiting for an opening.
                    float side = face + M.PI / 2 * (M.sin(homeX + g.time * .4f) > 0 ? 1 : -1);
                    yaw = M.turn(yaw, face, 6 * dt);
                    walk(g, M.sin(side) * 3.5f * dt, M.cos(side) * 3.5f * dt);
                    aux += dt * 5;
                }
                if (t > 1.6f && d < 8 && near) setState(WINDUP);
            }
            case WINDUP -> {
                yaw = M.turn(yaw, face, 8 * dt);
                if (t > .45f) {
                    setState(ATTACK);
                    tx = g.hero.x;
                    tz = g.hero.z;
                }
            }
            case ATTACK -> {
                float ddx = tx - x, ddz = tz - z, l = M.hypot(ddx, ddz);
                if (l > .3f) walk(g, ddx / l * 11 * dt, ddz / l * 11 * dt);
                aux += dt * 9;
                if (d < 1.4f && cool <= 0) {
                    g.hurtHero(3, x, z, true);
                    cool = 1;
                }
                if (t > .35f || l < .3f) setState(RECOVER);
            }
            case RECOVER -> {
                // A missed lunge on ice skids on past.
                if (g.level.icy(x, z) && t < .5f) walk(g, M.sin(yaw) * (7 - t * 14) * dt, M.cos(yaw) * (7 - t * 14) * dt);
                if (t > .7f) setState(CHASE);
            }
            default -> setState(CHASE);
        }
    }

    private void hoarwing(float dt, Game g, float d, float face) {
        if (state == HIDE) return;
        Hero h = g.hero;
        boolean second = hp <= maxHp / 2;
        aux += dt;
        switch (state) {
            case CHASE -> {
                float a = g.time * .6f, gx = homeX + M.sin(a) * 9, gz = homeZ + M.cos(a) * 6;
                x = M.approach(x, gx, 1.5f, dt);
                z = M.approach(z, gz, 1.5f, dt);
                y = M.approach(y, homeY + 7 + M.sin(g.time * 1.7f) * .6f, 1.5f, dt);
                yaw = M.turn(yaw, face, 3 * dt);
                if (t > (second ? 2.4f : 3.4f)) {
                    float q = g.rng.nextFloat();
                    bits = q < .35f ? 0 : q < .6f ? 1 : 2;
                    if (bits == 2 && g.dungeon != null) {
                        java.util.List<Dungeon.Thing> perches = new java.util.ArrayList<>();
                        for (Dungeon.Thing p : g.dungeon.things) if (p.kind == 'g' && p.j < 10) perches.add(p);
                        if (perches.isEmpty()) bits = 0;
                        else {
                            Dungeon.Thing p = perches.get(g.rng.nextInt(perches.size()));
                            tx = p.x;
                            tz = p.z;
                            headY = p.y + 3.2f;
                        }
                    }
                    setState(bits == 2 ? PERCH : WINDUP);
                    if (bits == 2) g.say("Hoarwing settles on a perch. Grapple it down!");
                }
            }
            case WINDUP -> {
                yaw = M.turn(yaw, face, 4 * dt);
                y = M.approach(y, homeY + 8, 2, dt);
                if (bits == 0 && t > .9f) {
                    setState(ATTACK);
                    tx = h.x;
                    tz = h.z;
                    g.sfx(Audio.NEIGH);
                }
                if (bits == 1 && t > .6f) {
                    for (int i = 0; i < (second ? 8 : 6); i++) {
                        float a = i * 1.1f + g.time, r = i == 0 ? 0 : 3.5f;
                        float ix = h.x + M.sin(a) * r, iz = h.z + M.cos(a) * r;
                        g.shoot(Game.ROCK, ix, homeY + 14, iz, ix, homeY, iz, 0);
                    }
                    setState(CHASE);
                }
            }
            case ATTACK -> {
                float ddx = tx - x, ddy = homeY + 1.2f - y, ddz = tz - z, l = M.sqrt(ddx * ddx + ddy * ddy + ddz * ddz), sp = second ? 26 : 22;
                if (l < 1) {
                    if (M.dist(h.x, h.z, x, z) < 2.6f && h.y < y + 2) g.hurtHero(4, x, z, false);
                    g.shake(.5f);
                    g.sparks(x, homeY + .3f, z, 0xe0f4ff, 18);
                    g.sfx(Audio.SLAM);
                    setState(RECOVER);
                } else {
                    x += ddx / l * sp * dt;
                    y += ddy / l * sp * dt;
                    z += ddz / l * sp * dt;
                    yaw = M.atan2(ddx, ddz);
                }
            }
            case RECOVER -> {
                y = M.approach(y, homeY + 7, 1.5f, dt);
                if (t > 1.2f) setState(CHASE);
            }
            case PERCH -> {
                x = M.approach(x, tx, 3, dt);
                z = M.approach(z, tz, 3, dt);
                y = M.approach(y, headY, 3, dt);
                yaw = M.turn(yaw, face, 3 * dt);
                if (t > (second ? 4.5f : 5.5f)) setState(CHASE);
            }
            case STUN -> {
                y = M.approach(y, homeY + 1.4f, 8, dt);
                if (t > aux2()) setState(RECOVER);
            }
            default -> setState(CHASE);
        }
    }

    private float stunTime = 5;

    private void stalker(float dt, Game g, float d, float face, boolean near) {
        if (knockback(dt, g)) return;
        switch (state) {
            case HIDE -> {
                // Burrowed: only a ripple of sand gives it away as it closes in.
                aux += dt * 5;
                if (d < 16 && near) {
                    yaw = M.turn(yaw, face, 4 * dt);
                    walk(g, M.sin(yaw) * 3.2f * dt, M.cos(yaw) * 3.2f * dt);
                }
                if (d < 2.4f && near && t > .6f) {
                    setState(WINDUP);
                    g.sparks(x, y + .2f, z, 0xd8b884, 12);
                    g.sfx(Audio.ROLL);
                }
            }
            case WINDUP -> {
                yaw = M.turn(yaw, face, 6 * dt);
                if (t > .6f) setState(ATTACK);
            }
            case ATTACK -> {
                if (t > .12f && cool <= 0 && d < 2.6f && near) {
                    g.hurtHero(2, x, z, true);
                    cool = 1;
                }
                if (t > .4f) setState(RECOVER);
            }
            case RECOVER -> {
                yaw = M.turn(yaw, face, 2 * dt);
                if (t > 1.7f) setState(HIDE);
            }
            default -> setState(HIDE);
        }
    }

    /** Ashmaw swims under the sand, bursts up beneath the hero, arcs over and dives; now and then it surfaces to roar. */
    private void ashmaw(float dt, Game g, float d, float face) {
        Hero h = g.hero;
        boolean second = hp <= maxHp / 2;
        float base = homeY;
        switch (state) {
            case HIDE -> {
                yaw = M.turn(yaw, face, 2.5f * dt);
                if (d > 3) walk(g, M.sin(yaw) * (second ? 7 : 5.5f) * dt, M.cos(yaw) * (second ? 7 : 5.5f) * dt);
                if (t > (second ? 1.8f : 2.8f)) {
                    setState(WINDUP);
                    tx = h.x;
                    tz = h.z;
                    // Arc away along whichever heading lands on open sand.
                    for (float turn : new float[]{0, M.PI / 2, -M.PI / 2, M.PI}) {
                        float a = face + turn;
                        if (g.level.floor(tx + M.sin(a) * 9, tz + M.cos(a) * 9) < base + .5f) {
                            yaw = a;
                            break;
                        }
                    }
                }
            }
            case WINDUP -> {
                if (t > (second ? .7f : .95f)) {
                    setState(ATTACK);
                    x = tx;
                    z = tz;
                    cool = 0;
                    g.shake(.5f);
                    g.sfx(Audio.BOOM);
                    g.sparks(tx, base + .3f, tz, 0xd8b884, 28);
                    if (M.dist(h.x, h.z, tx, tz) < 2.6f && h.y < base + 3) g.hurtHero(4, tx, tz, false);
                }
            }
            case ATTACK -> {
                float s = Math.min(1, t / 1.3f);
                headX = tx + M.sin(yaw) * s * 9;
                headZ = tz + M.cos(yaw) * s * 9;
                headY = base - 1 + M.sin(s * M.PI) * 6.5f;
                if (cool <= 0 && d3(h.x, h.y + 1, h.z, headX, headY, headZ) < 2.3f) {
                    g.hurtHero(3, headX, headZ, false);
                    cool = 1;
                }
                if (s >= 1) {
                    x = headX;
                    z = headZ;
                    g.sparks(x, base + .3f, z, 0xd8b884, 20);
                    g.sfx(Audio.SLAM);
                    bits++;
                    setState(bits % (second ? 3 : 2) == 0 ? RECOVER : HIDE);
                }
            }
            case RECOVER -> {
                headX = x;
                headZ = z;
                headY = base + 3.2f + M.sin(t * 2) * .2f;
                yaw = M.turn(yaw, M.atan2(h.x - x, h.z - z), 3 * dt);
                if (second && t > .8f && cool <= 0) {
                    g.shoot(Game.SEED, headX + M.sin(yaw) * 1.2f, headY, headZ + M.cos(yaw) * 1.2f, h.x, h.y + 1, h.z, 13);
                    cool = 1.1f;
                }
                if (t > (second ? 2.6f : 3.4f)) {
                    setState(HIDE);
                    g.sfx(Audio.ROLL);
                }
            }
            case STUN -> {
                headX = x + M.sin(yaw) * 2;
                headZ = z + M.cos(yaw) * 2;
                headY = base + .8f;
                if (t > stunTime) {
                    setState(HIDE);
                    bits = 0;
                }
            }
            default -> setState(HIDE);
        }
    }

    /** Lightning the drake has called down: {x, z, seconds until it lands, flash left}. */
    final java.util.List<float[]> strikes = new java.util.ArrayList<>();

    /** Where storm-scale i glows: the chest, then each wing root. */
    private boolean drakeScale(int i, float[] out) {
        float lx = i == 0 ? 0 : i == 1 ? -1.4f : 1.4f, ly = i == 0 ? -.6f : .55f, lz = i == 0 ? 1.3f : .3f, c = M.cos(yaw), n = M.sin(yaw);
        return put(out, x + (lx * c + lz * n) * 2, y + ly * 2, z + (-lx * n + lz * c) * 2);
    }

    /** The Stormdrake circles the mountain far above, comes down on anyone who strays close, and strafes them with lightning. */
    private void drake(float dt, Game g, float d, float face) {
        Hero h = g.hero;
        boolean second = hp <= maxHp / 2;
        for (int i = strikes.size() - 1; i >= 0; i--) {
            float[] s = strikes.get(i);
            float gy = g.world.height(s[0], s[1]);
            if (s[2] > 0 && (s[2] -= dt) <= 0) {
                s[3] = .3f;
                g.sparks(s[0], gy + .3f, s[1], 0xd8f0ff, 16);
                g.sfx(Audio.THUNDER);
                if (M.dist(h.x, h.z, s[0], s[1]) < 2.4f && Math.abs(h.y - gy) < 3) g.hurtHero(3, s[0], s[1], false);
            } else if (s[2] <= 0 && (s[3] -= dt) <= 0) strikes.remove(i);
        }
        headX = x + M.sin(yaw) * 4.6f;
        headZ = z + M.cos(yaw) * 4.6f;
        headY = y + (state == STUN ? .2f : 1.8f);
        if (state != IDLE && state != STUN && (g.dungeon != null || h.st == Hero.State.DEAD || M.dist(h.x, h.z, x, z) > 240)) {
            calm(g);
            return;
        }
        switch (state) {
            case IDLE -> {
                float a = g.time * .1f;
                x = M.approach(x, World.MID + M.sin(a) * 220, .4f, dt);
                z = M.approach(z, World.MID + M.cos(a) * 220, .4f, dt);
                y = M.approach(y, 240, .4f, dt);
                yaw = M.turn(yaw, a + M.PI / 2, dt);
                if (g.dungeon == null && g.boss == null && g.screen == Game.Screen.PLAY && h.st != Hero.State.DEAD && M.dist(h.x, h.z, x, z) < 150) {
                    g.boss = this;
                    setState(CHASE);
                    g.audio.music(Audio.BOSS);
                    g.say("THE STORMDRAKE, Rider of the Squall");
                    g.messageT = 4;
                }
            }
            case CHASE -> {
                float a = g.time * (second ? .55f : .42f), gx = h.x + M.sin(a) * 17, gz = h.z + M.cos(a) * 17;
                float gy = Math.max(g.world.height(gx, gz), Math.max(h.y, World.WATER)) + 12;
                x = M.approach(x, gx, 1.2f, dt);
                y = M.approach(y, gy, 1.2f, dt);
                z = M.approach(z, gz, 1.2f, dt);
                yaw = M.turn(yaw, a + M.PI / 2, 2 * dt);
                if (t > (second ? 3.5f : 5)) setState(WINDUP);
            }
            case WINDUP -> {
                y += 2 * dt;
                yaw = M.turn(yaw, face, 3 * dt);
                if (t > 1.1f) {
                    setState(ATTACK);
                    tx = M.sin(yaw);
                    tz = M.cos(yaw);
                    g.sfx(Audio.THUNDER);
                }
            }
            case ATTACK -> {
                // A strafing run straight over the hero, calling lightning down along its path.
                x += tx * 20 * dt;
                z += tz * 20 * dt;
                y = M.approach(y, Math.max(g.world.height(x, z), Math.max(h.y, World.WATER)) + 9, 2, dt);
                if (cool <= 0) {
                    cool = second ? .17f : .24f;
                    strikes.add(new float[]{x + (g.rng.nextFloat() - .5f) * 3, z + (g.rng.nextFloat() - .5f) * 3, .85f, 0});
                }
                if (t > 2.4f) setState(CHASE);
            }
            case STUN -> {
                y = M.approach(y, g.world.height(x, z) + 1.6f, 5, dt);
                if (t > stunTime) {
                    setState(CHASE);
                    bits = 0;
                    g.say("The Stormdrake takes to the sky again!");
                }
            }
            default -> setState(IDLE);
        }
    }

    /** Breaks off the fight and heals: the drake goes back to circling the mountain. */
    void calm(Game g) {
        setState(IDLE);
        hp = maxHp;
        bits = 0;
        strikes.clear();
        if (g.boss == this) {
            g.boss = null;
            g.audio.music(Audio.FIELD);
        }
    }

    /** Lightning markers and bolts, drawn even when the drake itself is out of view. */
    void drawStrikes(Renderer r, Game g) {
        for (float[] s : strikes) {
            float gy = g.world.height(s[0], s[1]) + .15f;
            if (s[2] > 0) {
                float rad = .8f + s[2] * 1.8f;
                for (int k = 0; k < 14; k++) {
                    float a = k * M.TAU / 14 + g.time * 3;
                    r.point(s[0] + M.sin(a) * rad, gy, s[1] + M.cos(a) * rad, .11f, 0xbfe8ff, 220);
                }
            } else {
                int col = 0xeaf6ff | Mesh.GLOW;
                r.worldTri(s[0] - .25f, gy, s[1], s[0] + .25f, gy, s[1], s[0], gy + 40, s[1], col, true, 230);
                r.worldTri(s[0], gy, s[1] - .25f, s[0], gy, s[1] + .25f, s[0], gy + 40, s[1], col, true, 230);
                r.point(s[0], gy + .5f, s[1], 1.2f, 0xd8f0ff, 160);
            }
        }
    }

    private float aux2() { return stunTime; }

    // ---- drawing ------------------------------------------------------------------------------

    void draw(Renderer r, Xf xf, float time) {
        if (state == HIDE && (kind == Kind.STALKER || kind == Kind.ASHMAW)) {
            // Burrowed: a travelling mound of sand.
            float s = kind == Kind.ASHMAW ? 2.4f : 1;
            r.mesh(Models.MOUND, xf.identity().translate(x, y + M.sin(time * 8 + homeX) * .04f, z).rotY(time * 2).scale(s, s * .6f, s));
            for (int k = 0; k < 4; k++) {
                float a = time * 3 + k * 1.6f;
                r.point(x + M.sin(a) * .6f * s, y + .25f + (k % 2) * .15f, z + M.cos(a) * .6f * s, .06f * s, 0xd8b884, 160);
            }
            return;
        }
        if (state == HIDE || state == DEAD && deadT > 1) return;
        if (riposteT > 0 && state != DEAD) r.point(x, centerY() + radius() + .7f, z, .2f + M.sin(time * 14) * .04f, 0xffd870, 230);
        r.flash = Math.max(flash * .85f, state == DEAD ? deadT : frozenT > 0 ? .55f : 0);
        r.flashCol = frozenT > 0 && flash < .3f ? 0x9fe8ff : 0xffffff;
        float sink = state == DEAD ? deadT * (boss ? 3 : 1) : 0;
        switch (kind) {
            case THORNLING, SENTRY, IMP -> {
                float scale = kind == Kind.THORNLING ? .75f : kind == Kind.IMP ? .62f : 1.1f;
                rig.locomote(pose, aux, state == CHASE || state == IDLE && t < 1.2f ? 1 : 0, time + homeX);
                if (state == WINDUP || state == RECOVER) an.apply(pose);
                else an.restart(null, 0);
                xf.identity().translate(x, y - sink, z).rotY(yaw).scale(scale);
                rig.draw(r, xf, pose);
                Xf hand = rig.xf[rig.part("armLR")];
                if (kind == Kind.THORNLING) r.mesh(Characters.CLUB, new Xf().set(hand).translate(0, -.27f, 0).rotX(1.2f));
                if (kind == Kind.SENTRY) {
                    r.mesh(Characters.SPEAR, new Xf().set(hand).translate(0, -.27f, 0).rotX(1.57f));
                    if (armour) r.mesh(Characters.TOWER_SHIELD, new Xf().set(xf).translate(-.32f, 1.05f, .32f).rotY(-.2f));
                }
            }
            case WOLF -> {
                java.util.Arrays.fill(pose, 0);
                boolean run = state == CHASE || state == ATTACK || state == IDLE && t < 1.5f;
                float p = aux * 2.2f, amp = run ? (state == ATTACK ? .9f : .6f) : 0;
                String[] legs = {"FL", "FR", "HL", "HR"};
                float[] off = {0, .5f, .6f, .1f};
                for (int k = 0; k < 4; k++) {
                    float lp = p + off[k] * M.TAU;
                    pose[rig.part("leg" + legs[k] + "u") * 3] = M.sin(lp) * amp;
                    pose[rig.part("leg" + legs[k] + "l") * 3] = Math.max(0, M.sin(lp + 1.4f)) * amp * (k < 2 ? 1 : -.7f);
                }
                pose[rig.part("head") * 3] = state == WINDUP ? .35f : -.1f + M.sin(time * 2 + homeX) * .05f;
                pose[rig.part("tail") * 3] = state == WINDUP ? .2f : .7f + M.sin(time * 6 + homeX) * .15f;
                pose[rig.n * 3] = state == WINDUP ? -.12f : Math.abs(M.sin(p)) * .05f * (run ? 1 : 0);
                xf.identity().translate(x, y - sink, z).rotY(yaw);
                rig.draw(r, xf, pose);
            }
            case STALKER -> {
                java.util.Arrays.fill(pose, 0);
                float rise = state == WINDUP ? M.clamp(t / .5f, 0, 1) : 1;
                pose[rig.part("tail") * 3] = state == ATTACK ? .9f : state == WINDUP ? -.35f : M.sin(time * 3 + homeX) * .08f;
                float snap = state == RECOVER ? M.sin(time * 9 + homeZ) * .25f : .1f;
                pose[rig.part("clawL") * 3 + 1] = snap;
                pose[rig.part("clawR") * 3 + 1] = -snap;
                xf.identity().translate(x, y - sink - (1 - rise) * .7f, z).rotY(yaw);
                rig.draw(r, xf, pose);
            }
            case ASHMAW -> {
                float fx = M.sin(yaw), fz = M.cos(yaw);
                if (state == WINDUP) {
                    // Churning sand marks where it will burst.
                    float k = M.clamp(t / .95f, 0, 1);
                    for (int i = 0; i < 18; i++) {
                        float a = i * M.TAU / 18 + time * 2, rr = 2.6f * (1.2f - k * .4f);
                        r.point(tx + M.sin(a) * rr, homeY + .15f, tz + M.cos(a) * rr, .14f, 0xd8b884, 210);
                    }
                    r.mesh(Models.MOUND, xf.identity().translate(x, homeY, z).scale(2.4f, 1.4f, 2.4f));
                    break;
                }
                float pitch = state == STUN ? .15f : state == RECOVER ? -.35f
                        : -M.atan2(6.5f * M.PI * M.cos(Math.min(1, t / 1.3f) * M.PI), 9);
                r.mesh(Characters.ASHMAW_HEAD, xf.identity().translate(headX, headY - sink, headZ).rotY(yaw).rotX(pitch));
                if (state == RECOVER) r.point(headX + fx * .95f, headY - .1f, headZ + fz * .95f, .35f + M.sin(time * 12) * .06f, 0xff8a3a, 230);
                for (int k = 1; k <= 8; k++) {
                    float sx, sy, sz;
                    if (state == ATTACK) {
                        float s = Math.max(0, Math.min(1, t / 1.3f) - k * .055f);
                        sx = tx + fx * s * 9;
                        sz = tz + fz * s * 9;
                        sy = homeY - 1 + M.sin(s * M.PI) * 6.5f;
                    } else if (state == STUN || state == DEAD) {
                        sx = headX - fx * k * 1.1f;
                        sz = headZ - fz * k * 1.1f;
                        sy = homeY + .55f;
                    } else {
                        sx = x - fx * k * .3f;
                        sz = z - fz * k * .3f;
                        sy = headY - k * .75f;
                    }
                    if (sy < homeY - 1.2f) continue;
                    r.mesh(Characters.ASHMAW_SEG, xf.identity().translate(sx, sy - sink, sz).rotY(yaw).scale(1 - k * .05f));
                }
            }
            case STORMDRAKE -> {
                java.util.Arrays.fill(pose, 0);
                boolean grounded = state == STUN || state == DEAD;
                float flap = grounded ? 0 : M.sin(time * (state == ATTACK ? 3.5f : 2.2f)) * .55f, fold = grounded ? 1 : 0;
                pose[rig.part("wingL") * 3 + 2] = flap - fold * .4f;
                pose[rig.part("wingR") * 3 + 2] = -flap + fold * .4f;
                pose[rig.part("wingL") * 3 + 1] = -fold * .8f;
                pose[rig.part("wingR") * 3 + 1] = fold * .8f;
                pose[rig.part("neck") * 3] = grounded ? .35f : state == WINDUP ? -.4f : 0;
                pose[rig.part("head") * 3] = grounded ? .3f : state == ATTACK ? .35f : -.1f;
                pose[rig.part("tail") * 3] = M.sin(time * 1.7f) * .15f;
                pose[rig.part("tail") * 3 + 1] = M.sin(time * 1.1f) * .25f;
                pose[rig.n * 3 + 1] = state == ATTACK ? .15f : 0;
                xf.identity().translate(x, y - sink, z).rotY(yaw).scale(2);
                rig.draw(r, xf, pose);
                float[] p = new float[3];
                for (int i = 0; i < 3; i++) if (point(i, p)) r.point(p[0], p[1], p[2], .45f + M.sin(time * 6 + i) * .08f, 0xffe14a, 230);
            }
            case HOARWING -> {
                java.util.Arrays.fill(pose, 0);
                boolean flying = state != PERCH && state != STUN;
                float flap = flying ? M.sin(time * (state == ATTACK ? 3 : 7)) * .6f : 0, fold = flying ? 0 : 1;
                pose[rig.part("wingL") * 3 + 2] = flap - fold * .9f;
                pose[rig.part("wingR") * 3 + 2] = -flap + fold * .9f;
                pose[rig.part("wingL") * 3 + 1] = fold * .9f;
                pose[rig.part("wingR") * 3 + 1] = -fold * .9f;
                pose[rig.part("head") * 3] = state == STUN ? .5f : state == WINDUP ? -.25f : 0;
                pose[rig.part("head") * 3 + 1] = state == PERCH ? M.sin(time * 1.3f) * .5f : 0;
                pose[rig.n * 3 + 1] = state == ATTACK ? .7f : state == STUN ? .9f : 0;
                xf.identity().translate(x, y - sink, z).rotY(yaw).scale(1.25f);
                rig.draw(r, xf, pose);
                if (state == PERCH) r.point(x + M.sin(yaw) * 1.1f, y - .1f, z + M.cos(yaw) * 1.1f, .35f + M.sin(time * 10) * .08f, 0x9fe8ff, 230);
            }
            case BAT -> {
                float flap = M.sin(time * 18 + homeX) * .9f;
                pose[rig.part("wingL") * 3 + 2] = flap;
                pose[rig.part("wingR") * 3 + 2] = -flap;
                xf.identity().translate(x, y - sink, z).rotY(yaw);
                rig.draw(r, xf, pose);
            }
            case SLIME -> {
                float squash = vy > 0 ? 1 + vy * .05f : 1 - Math.min(.25f, aux < .15f ? (.15f - aux) * 2 : 0);
                xf.identity().translate(x, y - sink * .3f, z).rotY(yaw).scale(size / M.sqrt(squash), size * squash, size / M.sqrt(squash));
                r.mesh(Characters.SLIME, xf);
            }
            case BRAMBLE -> {
                java.util.Arrays.fill(pose, 0);
                boolean down = state == STUN;
                float sway = M.sin(time * 1.3f) * .08f;
                pose[rig.part("stem") * 3] = down ? .5f : sway;
                pose[rig.part("neck") * 3] = down ? .65f : -sway + (state == WINDUP && cool == 1 ? -.3f : 0);
                pose[rig.part("head") * 3] = down ? .3f : .1f;
                float lash = state == WINDUP && cool == 0 ? -1.2f - t : 0;
                pose[rig.part("vineL1") * 3] = lash + M.sin(time * 2) * .3f;
                pose[rig.part("vineL2") * 3] = lash * .5f + M.sin(time * 2.6f) * .4f;
                pose[rig.part("vineR1") * 3] = M.sin(time * 2 + 1) * .3f - .2f;
                pose[rig.part("vineR2") * 3] = M.sin(time * 2.3f + 1) * .4f;
                pose[rig.part("vineL1") * 3 + 2] = .4f;
                pose[rig.part("vineR1") * 3 + 2] = -.4f;
                xf.identity().translate(x, y - sink, z).rotY(yaw);
                rig.draw(r, xf, pose);
                if (state == WINDUP && cool == 0) g_marker(r, tx, tz, time);
            }
            case GOLEM -> {
                rig.locomote(pose, aux, state == CHASE ? 1 : 0, time);
                int ar = rig.part("armUR"), al = rig.part("armUL");
                if (state == WINDUP && bits == 0) pose[ar * 3] = -M.lerp(.3f, 2.8f, M.smooth(0, .8f, t));
                if (state == WINDUP && bits != 0) pose[ar * 3] = pose[al * 3] = -2.6f * M.smooth(0, .8f, t);
                if (state == RECOVER) {
                    pose[ar * 3] = -1.2f;
                    pose[rig.n * 3] = -.6f;
                    pose[rig.part("torso") * 3] = .5f;
                }
                if (state == STUN) {
                    pose[rig.n * 3] = -1.2f;
                    pose[rig.part("torso") * 3] = .35f;
                }
                xf.identity().translate(x, y - sink, z).rotY(yaw).scale(1.25f);
                rig.draw(r, xf, pose);
                if (armour) r.mesh(Characters.GOLEM_ARMOUR, rig.xf[rig.part("torso")]);
                if (state == RECOVER) r.point(tx, y + .6f, tz, .6f + M.sin(time * 12) * .1f, 0xff8a3a, 200);
            }
            case SERPENT -> {
                if (state == CHASE) break;
                for (int i = 6; i >= 1; i--) {
                    float k = i / 7f, bx = M.lerp(headX, x - M.sin(yaw) * 3, k), bz = M.lerp(headZ, z - M.cos(yaw) * 3, k);
                    float by = M.lerp(headY, -2.5f, k) + M.sin(k * M.PI) * 1.5f;
                    xf.identity().translate(bx, by, bz).rotY(yaw).scale(1 - k * .3f);
                    r.mesh(Characters.SERPENT_BODY, xf);
                }
                xf.identity().translate(headX, headY, headZ).rotY(state == ATTACK || state == RECOVER || state == STUN ? M.atan2(tx - x, tz - z) : yaw)
                        .rotX(state == RECOVER || state == STUN ? .3f : -.2f);
                r.mesh(Characters.SERPENT_HEAD, xf);
                if (armour) r.mesh(Characters.SERPENT_MASK, xf);
            }
            case KING -> {
                java.util.Arrays.fill(pose, 0);
                float cast = state == WINDUP ? M.smooth(0, .5f, t) : 0;
                pose[rig.part("armUL") * 3] = -.3f - cast * 2.4f + M.sin(time * 1.7f) * .1f;
                pose[rig.part("armUR") * 3] = -.3f - cast * 2.4f - M.sin(time * 1.7f) * .1f;
                pose[rig.part("armUL") * 3 + 2] = -.3f;
                pose[rig.part("armUR") * 3 + 2] = .3f;
                pose[rig.part("head") * 3] = state == STUN ? .6f : -.1f;
                xf.identity().translate(x, y - sink, z).rotY(yaw).scale(1.3f);
                rig.draw(r, xf, pose);
                if (state == WINDUP && bits == 1) {
                    xf.identity().translate(tx, homeY + 4 - 3.6f * M.smooth(.6f, 1.1f, t), tz).rotY(yaw);
                    r.mesh(Characters.SHADOW_HAND, xf);
                    g_marker(r, tx, tz, time);
                }
            }
        }
        r.flash = 0;
    }

    /** A pulsing ring on the floor that warns where a blow will land. */
    private void g_marker(Renderer r, float mx, float mz, float time) {
        for (int i = 0; i < 16; i++) {
            float a = i * M.TAU / 16 + time;
            r.point(mx + M.sin(a) * 2.2f, homeY + .15f, mz + M.cos(a) * 2.2f, .18f, 0xff5a3a, 220);
        }
    }
}

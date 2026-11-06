package kestrel;

import java.awt.event.KeyEvent;

/** The hero: movement, lock-on combat, shield, items and every animation state. */
final class Hero {
    enum State { MOVE, AIR, ROLL, HOP, FLIP, ATTACK, CHARGE, SPIN, HURT, LEDGE, CLIMB, SWIM, AIM, HOLD, THROW, FLUTE, POSE, DEAD, CAST, DRINK, RIDE, LOOSE }

    static final float RADIUS = .35f, GRAVITY = 26;
    private static final Rig R = Characters.HERO;
    private static final Rig.Clip[] SLASH = {Characters.H_SLASH1, Characters.H_SLASH2, Characters.H_SLASH3};

    float x, y, z, vx, vy, vz, yaw, stT, invuln, swordOutT, runAmt, phase, capeA, capeV, holdT, chargeT;
    float stamina = 80, maxStamina = 80, fp = 40, maxFp = 40, castAt;
    private float stamDelay, haulT;
    private boolean drank;
    float safeX, safeY, safeZ, pushT, ledgeY, ledgeT;
    int hp = 12, maxHp = 12, combo;
    State st = State.MOVE;
    Foe target;
    boolean locked, blocking, moving, comboQueued, aimClaw;
    /** Which of the target's lock-on spots (weak points) is selected. */
    int targetPoint;
    private final float[] aimPt = new float[3];
    /** Set when the target died with nobody left to lock on to; cleared when Shift is let go. */
    private boolean lockSpent;
    final float[] pose = R.newPose();
    final Rig.Anim anim = new Rig.Anim();
    private float hopX, hopZ, fallFrom, slideX, slideZ;
    private boolean struck;
    private final float[] pos = new float[2];

    Hero(float x, float y, float z) {
        place(x, y, z);
    }

    void place(float x, float y, float z) {
        this.x = safeX = x;
        this.y = safeY = y;
        this.z = safeZ = z;
        vx = vy = vz = 0;
        st = State.MOVE;
        anim.restart(null, 0);
    }

    boolean busy() { return st == State.POSE || st == State.FLUTE || st == State.DEAD; }

    boolean onGround() { return st != State.AIR && st != State.HOP && st != State.FLIP && st != State.SWIM; }

    void setState(State s) {
        st = s;
        stT = 0;
    }

    /** Freeze in a pose (talking, receiving an item, playing the flute). */
    void pose(State s, Rig.Clip clip) {
        setState(s);
        vx = vz = 0;
        anim.play(clip, .2f);
    }

    /** Spends stamina on an action; false (and nothing spent) when exhausted. */
    boolean spend(float cost) {
        if (stamina < 1) return false;
        stamina = Math.max(0, stamina - cost);
        stamDelay = .6f;
        return true;
    }

    private void regen(float dt) {
        if ((stamDelay -= dt) <= 0) stamina = Math.min(maxStamina, stamina + (blocking ? 14 : 48) * dt);
        fp = Math.min(maxFp, fp + 1.2f * dt);
    }

    /** While mounted, Juniper does the moving; the hero just sits, swings and animates. */
    void riding(float dt, Game g, Horse horse) {
        x = horse.x;
        y = horse.seat();
        z = horse.z;
        yaw = horse.yaw;
        vx = M.sin(yaw) * horse.speed;
        vz = M.cos(yaw) * horse.speed;
        st = State.RIDE;
        invuln -= dt;
        swordOutT -= dt;
        regen(dt);
        animate(dt, g);
    }

    void update(float dt, Game g) {
        Level L = g.level;
        Input in = g.in;
        stT += dt;
        invuln -= dt;
        swordOutT -= dt;
        regen(dt);
        float mx = (in.down(KeyEvent.VK_D) ? 1 : 0) - (in.down(KeyEvent.VK_A) ? 1 : 0);
        float mz = (in.down(KeyEvent.VK_W) ? 1 : 0) - (in.down(KeyEvent.VK_S) ? 1 : 0);
        float len = M.hypot(mx, mz);
        if (len > 1) {
            mx /= len;
            mz /= len;
        }
        moving = len > 0;
        float cs = M.sin(g.camYaw), cc = M.cos(g.camYaw);
        float ix = mx * cc + mz * cs, iz = -mx * cs + mz * cc;
        boolean controllable = st == State.MOVE || st == State.AIM || st == State.HOLD;

        // Lock-on: hold Shift to face the nearest foe in front (or just strafe if none).
        if (!in.down(KeyEvent.VK_SHIFT)) lockSpent = false;
        locked = !lockSpent && in.down(KeyEvent.VK_SHIFT) && controllable | st == State.ATTACK | st == State.HOP | st == State.FLIP | st == State.CAST | st == State.LOOSE;
        if (!locked) target = null;
        else if (target == null || !target.alive() || M.dist(x, z, target.x, target.z) > 26) {
            boolean lost = target != null;
            // After a kill, jump to whoever is next closest (any direction); otherwise let go entirely.
            target = lost ? g.nearestFoe(x, z, yaw, 15, true) : g.nearestFoe(x, z, yaw, 18);
            if (target == null && lost) {
                lockSpent = true;
                locked = false;
            }
            targetPoint = target == null ? 0 : Math.max(0, target.nextPoint(Foe.POINTS - 1, aimPt));
        }
        if (target != null && (in.pressed(KeyEvent.VK_Z) || in.wheel != 0 || !target.point(targetPoint, aimPt))) {
            // Weak points come and go (a bulb bursts, a head slumps): follow to the next one, or cycle by hand.
            int next = target.nextPoint(targetPoint, aimPt);
            if (next >= 0) targetPoint = next;
        }
        if (locked) swordOutT = 4;

        blocking = false;
        switch (st) {
            case MOVE -> move(dt, g, ix, iz, mx, mz);
            case AIR -> air(dt, g, ix, iz);
            case ROLL -> {
                float k = 1 - M.clamp(stT / .45f, 0, 1) * .5f;
                step(L, hopX * 9 * k * dt, hopZ * 9 * k * dt);
                if (stT > .45f) setState(State.MOVE);
                ground(g, true);
            }
            case HOP, FLIP -> {
                vy -= GRAVITY * dt;
                y += vy * dt;
                step(L, hopX * dt, hopZ * dt);
                float f = L.floor(x, z);
                if (y <= f && vy < 0) {
                    y = f;
                    setState(State.MOVE);
                }
            }
            case ATTACK -> attack(dt, g, in);
            case CHARGE -> {
                if (moving) step(L, ix * 2 * dt, iz * 2 * dt);
                faceTarget(dt, 12);
                chargeT += dt;
                if (!in.down(KeyEvent.VK_J) && !in.down(Input.MOUSE_L)) {
                    if (chargeT > .5f && spend(20)) {
                        setState(State.SPIN);
                        anim.restart(Characters.H_SPIN, .05f);
                        struck = false;
                        g.sfx(Audio.SPIN);
                    } else setState(State.MOVE);
                }
                ground(g, false);
            }
            case SPIN -> {
                if (!struck && stT > .18f) {
                    struck = true;
                    g.swordStrike(this, 360, 2.9f, 2);
                }
                if (stT > .55f) setState(State.MOVE);
            }
            case HURT -> {
                step(L, hopX * dt, hopZ * dt);
                hopX *= .9f;
                hopZ *= .9f;
                if (stT > .35f) setState(State.MOVE);
                ground(g, true);
            }
            case LEDGE -> {
                if (stT > .45f) {
                    y = ledgeY;
                    x += M.sin(yaw) * .55f;
                    z += M.cos(yaw) * .55f;
                    setState(State.MOVE);
                }
            }
            case CLIMB -> climb(dt, g, mz);
            case SWIM -> swim(dt, g, ix, iz);
            case AIM -> {
                if (target != null) faceTarget(dt, 14);
                else yaw = g.camYaw;
                if (moving) step(L, ix * 2 * dt, iz * 2 * dt);
                if (aimClaw ? !in.down(KeyEvent.VK_F) : !in.down(KeyEvent.VK_J) && !in.down(Input.MOUSE_L)) {
                    if (aimClaw) {
                        g.fireClaw(this);
                        setState(State.MOVE);
                    } else {
                        g.fireArrow(this, draw());
                        setState(State.LOOSE);
                        anim.restart(Characters.H_LOOSE, .03f);
                    }
                }
                ground(g, false);
            }
            case LOOSE -> {
                if (target != null) faceTarget(dt, 10);
                if (moving) step(L, ix * 2 * dt, iz * 2 * dt);
                if (stT > .4f) setState(State.MOVE);
                ground(g, false);
            }
            case HOLD -> {
                holdT += dt;
                if (moving) {
                    step(L, ix * 4 * dt, iz * 4 * dt);
                    yaw = M.turn(yaw, M.atan2(ix, iz), 10 * dt);
                }
                if (in.pressed(KeyEvent.VK_F) || in.pressed(KeyEvent.VK_J) || in.pressed(Input.MOUSE_L)) {
                    g.throwBomb(this, 3 - holdT);
                    setState(State.THROW);
                    anim.restart(Characters.H_THROW, .05f);
                } else if (holdT > 3) {
                    g.explode(x, y + 1.6f, z, true);
                    setState(State.MOVE);
                }
                ground(g, false);
            }
            case THROW -> {
                if (stT > .3f) setState(State.MOVE);
            }
            case CAST -> {
                if (castAt >= 0 && stT >= castAt) {
                    castAt = -1;
                    g.magic.release(this);
                }
                faceTarget(dt, 10);
                if (anim.done()) setState(State.MOVE);
            }
            case DRINK -> {
                if (moving) step(L, ix * 1.5f * dt, iz * 1.5f * dt);
                if (!drank && stT > .5f) {
                    drank = true;
                    g.drinkDraught();
                }
                if (stT > .9f) setState(State.MOVE);
                ground(g, false);
            }
            case DEAD -> {}
            default -> {}
        }
        animate(dt, g);
    }

    private void move(float dt, Game g, float ix, float iz, float mx, float mz) {
        Level L = g.level;
        Input in = g.in;
        blocking = (in.down(KeyEvent.VK_K) || in.down(Input.MOUSE_R)) && g.gear.shield;
        if (blocking) swordOutT = 4;
        float speed = blocking ? 1.6f : locked ? 4.2f : 6;
        boolean ice = L.icy(x, z);
        // On ice you build speed slowly and glide on after letting go.
        slideX = ice ? M.approach(slideX, moving ? ix * speed : 0, 2.2f, dt) : moving ? ix * speed : 0;
        slideZ = ice ? M.approach(slideZ, moving ? iz * speed : 0, 2.2f, dt) : moving ? iz * speed : 0;
        if (!moving && ice && Math.abs(slideX) + Math.abs(slideZ) > .15f && !step(L, slideX * dt, slideZ * dt)) slideX = slideZ = 0;
        if (moving) {
            float dx = (ice ? slideX : ix * speed) * dt, dz = (ice ? slideZ : iz * speed) * dt;
            boolean stepped = step(L, dx, dz);
            if (!stepped && ice) slideX = slideZ = 0;
            if (!stepped && L.ledges()) {
                float ahead = L.floor(x + ix * .6f, z + iz * .6f), rise = ahead - y;
                if (L.vine(x + ix * .6f, z + iz * .6f)) {
                    setState(State.CLIMB);
                    yaw = M.atan2(ix, iz);
                    ledgeY = ahead;
                    return;
                }
                if (rise > L.maxStep() && rise < 2.1f && (pushT += dt) > .15f) {
                    setState(State.LEDGE);
                    ledgeY = ahead;
                    yaw = M.atan2(ix, iz);
                    anim.restart(Characters.H_LEDGE, .05f);
                    pushT = 0;
                    return;
                }
            } else {
                pushT = 0;
            }
            if (!locked && !blocking) yaw = M.turn(yaw, M.atan2(ix, iz), 14 * dt);
            phase += dt * (locked || blocking ? 7 : 10.5f);
        }
        if (locked || blocking) faceTarget(dt, 14);
        runAmt = M.approach(runAmt, moving ? (locked || blocking ? 1 : 1.8f) : 0, 10, dt);
        vx = moving ? ix * speed : 0;
        vz = moving ? iz * speed : 0;

        if (in.pressed(KeyEvent.VK_C) && g.magic.begin(this)) return;
        if (in.pressed(KeyEvent.VK_R)) {
            if (g.gear.flasks > 0) {
                g.gear.flasks--;
                drank = false;
                setState(State.DRINK);
                anim.restart(Characters.H_DRINK, .1f);
            } else g.say("No Ember Draughts left. Rest at a Lantern Shrine to refill them.");
            return;
        }
        if (in.pressed(KeyEvent.VK_SPACE) && spend(locked && Math.abs(mx) > .5f ? 14 : locked && mz < -.5f ? 16 : 18)) {
            if (locked && Math.abs(mx) > .5f) {
                float s = Math.signum(mx);
                hopX = M.cos(yaw) * 8 * s;
                hopZ = -M.sin(yaw) * 8 * s;
                vy = 5;
                setState(State.HOP);
                anim.restart(Characters.H_HOP, .05f);
                invuln = Math.max(invuln, .3f);
            } else if (locked && mz < -.5f) {
                hopX = -M.sin(yaw) * 6;
                hopZ = -M.cos(yaw) * 6;
                vy = 7;
                setState(State.FLIP);
                anim.restart(Characters.H_FLIP, .05f);
                invuln = Math.max(invuln, .45f);
            } else {
                hopX = moving ? ix : M.sin(yaw);
                hopZ = moving ? iz : M.cos(yaw);
                yaw = M.atan2(hopX, hopZ);
                setState(State.ROLL);
                anim.restart(Characters.H_ROLL, .04f);
                invuln = Math.max(invuln, .25f);
                g.sfx(Audio.ROLL);
            }
            return;
        }
        if (in.any(KeyEvent.VK_J, Input.MOUSE_L)) {
            if (g.gear.bowOut && g.gear.bow) {
                if (g.gear.arrows > 0) {
                    aimClaw = false;
                    setState(State.AIM);
                    anim.restart(Characters.H_DRAW, .08f);
                    return;
                }
                g.say("Out of arrows.");
            } else if (g.gear.sword && spend(7)) {
                startAttack(g, 0);
                return;
            }
        }
        if (in.pressed(KeyEvent.VK_F) && g.useItem(this)) return;
        ground(g, true);
    }

    private void startAttack(Game g, int k) {
        combo = k;
        comboQueued = false;
        struck = false;
        swordOutT = 4;
        setState(State.ATTACK);
        anim.restart(SLASH[k], .04f);
        if (target != null) yaw = M.atan2(target.x - x, target.z - z);
        g.sfx(Audio.SWING);
    }

    private void attack(float dt, Game g, Input in) {
        float len = SLASH[combo].length, hitAt = combo == 2 ? .24f : .16f;
        step(g.level, M.sin(yaw) * (stT < hitAt ? 2.5f : 0) * dt, M.cos(yaw) * (stT < hitAt ? 2.5f : 0) * dt);
        faceTarget(dt, 10);
        if (!struck && stT > hitAt) {
            struck = true;
            g.swordStrike(this, combo == 2 ? 100 : 150, 2.4f, combo == 2 ? 2 : 1);
        }
        if (stT > .1f && in.any(KeyEvent.VK_J, Input.MOUSE_L)) comboQueued = true;
        if (stT > len * .8f && comboQueued && combo < 2 && spend(combo == 1 ? 11 : 7)) {
            startAttack(g, combo + 1);
            return;
        }
        if (stT > len) {
            if (in.down(KeyEvent.VK_J) || in.down(Input.MOUSE_L)) {
                setState(State.CHARGE);
                chargeT = 0;
                anim.play(Characters.H_CHARGE, .1f);
            } else setState(State.MOVE);
        }
        ground(g, false);
    }

    private void air(float dt, Game g, float ix, float iz) {
        vy = Math.max(vy - GRAVITY * dt, -40);
        if (moving) {
            vx = M.approach(vx, ix * 5, 2, dt);
            vz = M.approach(vz, iz * 5, 2, dt);
        }
        y += vy * dt;
        step(g.level, vx * dt, vz * dt);
        float f = g.level.floor(x, z), w = g.level.water(x, z);
        if (!Float.isNaN(w) && y < w - .4f && f < w - 1) {
            setState(State.SWIM);
            g.splash(x, w, z);
            return;
        }
        if (y <= f) {
            y = f;
            float drop = fallFrom - y;
            setState(State.MOVE);
            if (drop > 9) g.hurtHero(Math.min(8, (int) ((drop - 9) / 2) + 2), x, z);
        }
    }

    private void climb(float dt, Game g, float mz) {
        y += mz * 2.2f * dt;
        phase += Math.abs(mz) * dt * 6;
        float base = g.level.floor(x - M.sin(yaw) * .5f, z - M.cos(yaw) * .5f);
        if (y >= ledgeY - .3f) {
            setState(State.LEDGE);
            anim.restart(Characters.H_LEDGE, .05f);
        } else if (y <= base && mz < 0) {
            y = base;
            setState(State.MOVE);
        }
    }

    private void swim(float dt, Game g, float ix, float iz) {
        Level L = g.level;
        float w = L.water(x, z);
        if (moving) {
            if (!step(L, ix * 3 * dt, iz * 3 * dt) && L.ledges()) {
                // Swimming into a low edge hauls the hero out onto it.
                float ahead = L.floor(x + ix * .7f, z + iz * .7f), rise = ahead - y;
                if (rise > 0 && rise < 2.3f && (haulT += dt) > .1f) {
                    setState(State.LEDGE);
                    ledgeY = ahead;
                    yaw = M.atan2(ix, iz);
                    anim.restart(Characters.H_LEDGE, .05f);
                    haulT = 0;
                    return;
                }
            } else haulT = 0;
            yaw = M.turn(yaw, M.atan2(ix, iz), 8 * dt);
            phase += dt * 6;
        }
        w = L.water(x, z);
        float f = L.floor(x, z);
        if (Float.isNaN(w) || f > w - .9f) {
            y = Math.max(f, y);
            setState(State.MOVE);
            return;
        }
        y = w - .45f + M.sin(stT * 2.4f) * .05f;
    }

    /** Snaps to the ground, starting a fall (with the classic hop off running edges). */
    private void ground(Game g, boolean canFall) {
        Level L = g.level;
        float f = L.floor(x, z), w = L.water(x, z);
        if (!Float.isNaN(w) && f < w - 1) {
            setState(State.SWIM);
            g.splash(x, w, z);
            return;
        }
        if (f < y - .6f && canFall) {
            fallFrom = y;
            if (st == State.MOVE && moving) {
                vy = 4.5f;
                g.sfx(Audio.JUMP);
            } else vy = 0;
            setState(State.AIR);
            return;
        }
        y = Math.max(f, y - .6f);
        if (f > -50 && Float.isNaN(w)) {
            safeX = x;
            safeY = y;
            safeZ = z;
        }
    }

    /** Moves with wall sliding; false if anything blocked the way. */
    private boolean step(Level L, float dx, float dz) {
        boolean free = true;
        float lim = st == State.AIR || st == State.HOP || st == State.FLIP ? .05f : L.maxStep();
        if (dx != 0) {
            float px = x + dx + Math.signum(dx) * RADIUS;
            if (L.floor(px, z - RADIUS * .7f) <= y + lim && L.floor(px, z + RADIUS * .7f) <= y + lim) x += dx;
            else free = false;
        }
        if (dz != 0) {
            float pz = z + dz + Math.signum(dz) * RADIUS;
            if (L.floor(x - RADIUS * .7f, pz) <= y + lim && L.floor(x + RADIUS * .7f, pz) <= y + lim) z += dz;
            else free = false;
        }
        pos[0] = x;
        pos[1] = z;
        L.collide(pos, y, RADIUS);
        if (pos[0] != x || pos[1] != z) free = false;
        x = pos[0];
        z = pos[1];
        return free;
    }

    private void faceTarget(float dt, float rate) {
        if (target == null) return;
        float tx = target.x, tz = target.z;
        if (target.point(targetPoint, aimPt)) {
            tx = aimPt[0];
            tz = aimPt[2];
        }
        yaw = M.turn(yaw, M.atan2(tx - x, tz - z), rate * dt);
    }

    /** How far the bowstring is drawn, 0 to 1. */
    float draw() { return st == State.AIM && !aimClaw ? Math.min(1, stT / .35f) : 0; }

    /** The locked-on spot, if any. */
    boolean aimPoint(float[] out) { return target != null && target.alive() && target.point(targetPoint, out); }

    /** Called by the game when something hits the hero; returns false if the shield took it. */
    boolean hit(Game g, int dmg, float fromX, float fromZ, boolean blockable) {
        if (invuln > 0 || st == State.DEAD) return false;
        float dx = x - fromX, dz = z - fromZ, d = Math.max(.01f, M.hypot(dx, dz));
        if (blockable && blocking && Math.abs(M.angleDiff(yaw, M.atan2(-dx, -dz))) < 1.2f && (stamina -= dmg * 7) > 0) {
            stamDelay = .8f;
            step(g.level, dx / d * .4f, dz / d * .4f);
            g.sfx(Audio.CLANG);
            g.sparks(x + M.sin(yaw) * .6f, y + 1.1f, z + M.cos(yaw) * .6f, 0xfff0c0, 8);
            return false;
        }
        hp -= dmg;
        invuln = 1.1f;
        hopX = dx / d * 7;
        hopZ = dz / d * 7;
        if (st == State.HOLD) g.dropBomb(this);
        if (hp <= 0) {
            hp = 0;
            pose(State.DEAD, Characters.H_DEAD);
        } else if (st != State.SWIM && st != State.CLIMB) {
            setState(State.HURT);
            anim.restart(Characters.H_HURT, .04f);
        }
        return true;
    }

    private void animate(float dt, Game g) {
        anim.update(dt);
        float speed = M.hypot(vx, vz);
        if (st == State.RIDE) {
            java.util.Arrays.fill(pose, 0);
            Characters.H_RIDE.apply(g.time, 1, pose);
            anim.apply(pose);
            capeA = M.approach(capeA, .3f + Math.min(1.2f, speed / 10), 6, dt);
            pose[R.part("capeU") * 3] += capeA;
            pose[R.part("capeL") * 3] += capeA * .45f + M.sin(g.time * 11) * .05f * Math.min(1, speed / 6);
            return;
        }
        switch (st) {
            case MOVE -> anim.play(blocking ? Characters.H_BLOCK : null, .12f);
            case AIR -> anim.play(Characters.H_AIR, .15f);
            case CLIMB -> anim.play(Characters.H_CLIMB, .15f);
            case SWIM -> anim.play(Characters.H_SWIM, .2f);
            case AIM -> {
                if (aimClaw || stT > .35f) anim.play(Characters.H_AIM, .12f);
            }
            case HOLD -> anim.play(Characters.H_HOLD, .15f);
            default -> {}
        }
        R.locomote(pose, phase, st == State.MOVE || st == State.CHARGE ? runAmt : 0, g.time);
        if (st == State.CLIMB) anim.t = phase * .2f;
        anim.apply(pose);
        float target = .12f + Math.min(1.1f, speed / 6) + (st == State.AIR || st == State.FLIP ? .7f : 0) + (st == State.SPIN ? 1 : 0);
        capeV = (capeV + (target - capeA) * 50 * dt) * (float) Math.exp(-7 * dt);
        capeA += capeV * dt;
        int cu = R.part("capeU"), cl = R.part("capeL");
        pose[cu * 3] += capeA;
        pose[cl * 3] += capeA * .45f + M.sin(g.time * 9) * .04f * Math.min(1, speed / 3);
    }
}

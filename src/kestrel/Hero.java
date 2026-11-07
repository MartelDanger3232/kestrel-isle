package kestrel;

import java.awt.event.KeyEvent;

/** The hero: movement, lock-on combat, shield, items and every animation state. */
final class Hero {
    enum State { MOVE, AIR, ROLL, HOP, FLIP, ATTACK, CHARGE, SPIN, HURT, LEDGE, CLIMB, SWIM, AIM, HOLD, THROW, FLUTE, POSE, DEAD, CAST, DRINK, RIDE, LOOSE, GLIDE, HOVER }

    static final float RADIUS = .35f, GRAVITY = 26;
    private static final Rig R = Characters.HERO;
    private static final Rig.Clip[] SLASH = {Characters.H_SLASH1, Characters.H_SLASH2, Characters.H_SLASH3};

    float x, y, z, vx, vy, vz, yaw, stT, invuln, swordOutT, runAmt, phase, capeA, capeV, holdT, chargeT;
    float stamina = 80, maxStamina = 80, fp = 40, maxFp = 40, castAt;
    /** The spell being cast, until it's released (-1 for none); every hero casts their own. */
    int spellCast = -1;
    private float stamDelay, haulT;
    /** Cuts landed so far in a charged whirlwind. */
    private int hits;
    /** How long the shield has been up: a guard raised just before a blow lands is a parry. */
    private float guardT;
    private boolean drank;
    float safeX, safeY, safeZ, pushT, ledgeY, ledgeT;
    int hp = 12, maxHp = 12, combo;
    State st = State.MOVE;
    Foe target;
    /** A duelling friend locked on to instead of a foe (see Game.rivals). */
    Hero rival;
    /** Where the lock-on aims, kept as plain numbers so a co-op friend's copy of this hero aims there too. */
    float lockX, lockY, lockZ;
    boolean lockOn;
    /** Opted in to duels with P: two duellists can fight each other. */
    boolean dueling;
    /**
     * The Skyward Gust cast while locked on: at the top of the throw the hero hangs in the air (HOVER) at hoverY until
     * an attack spell. aloft is 1 while casting from a hover, 2 once an attack spell has ended it.
     */
    boolean hoverNext;
    float hoverY;
    int aloft;
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

    boolean onGround() { return st != State.AIR && st != State.HOP && st != State.FLIP && st != State.SWIM && st != State.GLIDE; }

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

    private void regen(float dt, Game g) {
        int fit = g.gear.outfit;
        if ((stamDelay -= dt) <= 0) stamina = Math.min(maxStamina, stamina + (blocking ? 14 : 48) * (fit == Arms.FURS ? 1.4f : 1) * dt);
        fp = Math.min(maxFp, fp + 1.2f * (fit == Arms.STARWEAVE ? 3 : 1) * dt);
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
        regen(dt, g);
        animate(dt, g);
    }

    void update(float dt, Game g) {
        Level L = g.level;
        Input in = g.in;
        stT += dt;
        invuln -= dt;
        swordOutT -= dt;
        regen(dt, g);
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

        // Lock-on: hold Shift to face the nearest foe (or duelling friend) in front. With nobody there, nothing happens.
        if (!in.down(KeyEvent.VK_SHIFT)) lockSpent = false;
        boolean holding = !lockSpent && in.down(KeyEvent.VK_SHIFT)
                && controllable | st == State.ATTACK | st == State.HOP | st == State.FLIP | st == State.CAST | st == State.LOOSE | st == State.HOVER;
        if (!holding) {
            target = null;
            rival = null;
        } else {
            if (rival != null && (rival.st == State.DEAD || M.dist(x, z, rival.x, rival.z) > 26 || !g.rivals(this, rival))) rival = null;
            boolean lost = target != null && (!target.alive() || M.dist(x, z, target.x, target.z) > 26);
            if (lost) target = null;
            if (target == null && rival == null) {
                // After a kill, jump to whoever is next closest (any direction); otherwise only what's in front.
                target = lost ? g.nearestFoe(x, z, yaw, 15, true) : g.nearestFoe(x, z, yaw, 18);
                Hero r = g.nearestRival(this, lost ? 15 : 18, lost);
                if (r != null && (target == null || M.dist(x, z, r.x, r.z) < M.dist(x, z, target.x, target.z))) {
                    rival = r;
                    target = null;
                }
                if (target == null && rival == null && lost) lockSpent = true;
                targetPoint = target == null ? 0 : Math.max(0, target.nextPoint(Foe.POINTS - 1, aimPt));
            }
        }
        locked = target != null || rival != null;
        if (target != null && (in.pressed(KeyEvent.VK_Z) || in.wheel != 0 || !target.point(targetPoint, aimPt))) {
            // Weak points come and go (a bulb bursts, a head slumps): follow to the next one, or cycle by hand.
            int next = target.nextPoint(targetPoint, aimPt);
            if (next >= 0) targetPoint = next;
        }
        lockOn = rival != null || target != null && target.point(targetPoint, aimPt);
        if (rival != null) {
            lockX = rival.x;
            lockY = rival.y + 1.2f;
            lockZ = rival.z;
        } else if (lockOn) {
            lockX = aimPt[0];
            lockY = aimPt[1];
            lockZ = aimPt[2];
        }
        if (locked) swordOutT = 4;

        blocking = false;
        switch (st) {
            case MOVE -> move(dt, g, ix, iz, mx, mz);
            case AIR -> air(dt, g, ix, iz);
            case GLIDE -> glide(dt, g, ix, iz);
            case HOVER -> hover(dt, g, ix, iz);
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
                    int w = g.gear.weapon;
                    if (chargeT > .5f && spend(20 * Arms.STAMINA[w])) {
                        setState(State.SPIN);
                        anim.restart(w == Arms.SWORD || w == Arms.FANGS ? Characters.H_SPIN : w == Arms.SPEAR ? SLASH[0] : SLASH[2], .05f);
                        struck = false;
                        hits = 0;
                        g.sfx(Audio.SPIN);
                    } else setState(State.MOVE);
                }
                ground(g, false);
            }
            case SPIN -> charged(dt, g);
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
                if (locked) faceTarget(dt, 14);
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
                if (locked) faceTarget(dt, 10);
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
                if (st == State.CAST && anim.done()) {
                    // A cast from a hover goes back to hovering, unless it was an attack: then the wind lets go.
                    if (aloft == 1) setState(State.HOVER);
                    else if (aloft == 2 || y - g.level.floor(x, z) > .5f) {
                        vy = 0;
                        setState(State.AIR);
                    } else setState(State.MOVE);
                    aloft = 0;
                }
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
        guardT = blocking ? guardT + dt : 0;
        if (blocking) swordOutT = 4;
        int fit = g.gear.outfit;
        float speed = (blocking ? 1.6f : locked ? 4.2f : 6) * (fit == Arms.WRAPS ? 1.12f : fit == Arms.PLATE ? .92f : 1);
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
        if (g.gear.windcloak && g.updraft(x, y + 1, z) > 0) {
            g.tip("updraft", "Warm air rises here. Press Space to ride it up with the Windcloak.");
            if (in.pressed(KeyEvent.VK_SPACE)) {
                vy = 9;
                fallFrom = y;
                setState(State.GLIDE);
                g.sfx(Audio.GUST);
                return;
            }
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
            } else if (g.gear.sword && spend(7 * Arms.STAMINA[g.gear.weapon])) {
                startAttack(g, 0);
                return;
            }
        }
        if (in.pressed(KeyEvent.VK_F) && g.useItem(this)) return;
        ground(g, true);
    }

    /** Each weapon's charged move, released after holding the attack button. */
    private void charged(float dt, Game g) {
        float fx = M.sin(yaw), fz = M.cos(yaw);
        switch (g.gear.weapon) {
            case Arms.GREATSWORD -> {
                if (!struck && stT > .32f) {
                    struck = true;
                    g.swordStrike(this, 140, 3.6f, 3.2f);
                    g.blast(x + fx * 2.6f, z + fz * 2.6f, 3.4f, 2, false);
                    g.sparks(x + fx * 2.6f, y + .4f, z + fz * 2.6f, 0xff8a3a, 30);
                    g.shake(.5f);
                    g.sfx(Audio.BOOM);
                }
                if (stT > .8f) setState(State.MOVE);
            }
            case Arms.SPEAR -> {
                if (stT < .32f) step(g.level, fx * 15 * dt, fz * 15 * dt);
                if (!struck && stT > .12f) {
                    struck = true;
                    g.swordStrike(this, 40, 5.5f, 2.6f);
                }
                if (stT > .5f) setState(State.MOVE);
            }
            case Arms.FANGS -> {
                if (hits < 3 && stT > .12f + hits * .16f) {
                    hits++;
                    g.swordStrike(this, 360, 2.3f, .9f);
                    g.sfx(Audio.SWING);
                }
                if (stT > .62f) setState(State.MOVE);
            }
            case Arms.HAMMER -> {
                if (!struck && stT > .4f) {
                    struck = true;
                    g.blast(x, z, 5, 2.4f, true);
                    g.smash(x + fx * 1.6f, z + fz * 1.6f);
                    for (int k = 0; k < 10; k++) g.sparks(x + M.sin(k * .63f) * 3, y + .2f, z + M.cos(k * .63f) * 3, 0x9fe8ff, 4);
                    g.shake(1);
                    g.sfx(Audio.SLAM);
                }
                if (stT > .9f) setState(State.MOVE);
            }
            default -> {
                if (!struck && stT > .18f) {
                    struck = true;
                    g.swordStrike(this, 360, 2.9f, 2);
                }
                if (stT > .55f) setState(State.MOVE);
            }
        }
    }

    private void startAttack(Game g, int k) {
        combo = k;
        comboQueued = false;
        struck = false;
        swordOutT = 4;
        setState(State.ATTACK);
        anim.restart(SLASH[k], .04f);
        if (target != null) yaw = M.atan2(target.x - x, target.z - z);
        else if (rival != null) yaw = M.atan2(rival.x - x, rival.z - z);
        g.sfx(Audio.SWING);
    }

    private void attack(float dt, Game g, Input in) {
        int w = g.gear.weapon;
        float tm = Arms.TIME[w], len = SLASH[combo].length * tm, hitAt = (combo == 2 ? .24f : .16f) * tm;
        step(g.level, M.sin(yaw) * (stT < hitAt ? 2.5f : 0) * dt, M.cos(yaw) * (stT < hitAt ? 2.5f : 0) * dt);
        faceTarget(dt, 10);
        if (!struck && stT > hitAt) {
            struck = true;
            g.swordStrike(this, Arms.ARC[w] * (combo == 2 ? .67f : 1), Arms.REACH[w], (combo == 2 ? 2 : 1) * Arms.DMG[w]);
        }
        if (stT > .1f && in.any(KeyEvent.VK_J, Input.MOUSE_L)) comboQueued = true;
        if (stT > len * .8f && comboQueued && combo < 2 && spend((combo == 1 ? 11 : 7) * Arms.STAMINA[w])) {
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

    /** Thrown skywards (the Skyward Gust), drifting forward; coming back down to where it started never hurts. */
    void launch(float up, float ahead) {
        vy = up;
        vx += M.sin(yaw) * ahead;
        vz += M.cos(yaw) * ahead;
        fallFrom = y;
        setState(State.AIR);
    }

    private void air(float dt, Game g, float ix, float iz) {
        if (g.gear.windcloak && g.in.pressed(KeyEvent.VK_SPACE) && y - g.level.floor(x, z) > 1.1f && stamina > 4) {
            setState(State.GLIDE);
            g.sfx(Audio.GUST);
            return;
        }
        if (g.gear.windcloak && vy < -6 && y - g.level.floor(x, z) > 4)
            g.tip("glide", "Falling! Press Space to open the Windcloak and glide. Steer with the movement keys; Space again folds it. Warm air over fires and vents lifts you.");
        vy = Math.max(vy - GRAVITY * dt, -40);
        if (hoverNext && vy <= 0) {
            // The top of a locked-on Skyward Gust: the wind holds the hero here.
            hoverNext = false;
            hoverY = y;
            vx = vz = 0;
            setState(State.HOVER);
            return;
        }
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

    /** Under the Windcloak: drift down slowly, steer with the movement keys, and ride warm air upwards. */
    private void glide(float dt, Game g, float ix, float iz) {
        Level L = g.level;
        float lift = g.updraft(x, y, z), speed = g.gear.stormcloak ? 13 : 9;
        if (moving) yaw = M.turn(yaw, M.atan2(ix, iz), 2.6f * dt);
        // In rising air you hover while you're not steering; elsewhere the cloak always carries you forward.
        float ahead = lift > 0 && !moving ? 0 : speed;
        vx = M.approach(vx, M.sin(yaw) * ahead, 1.6f, dt);
        vz = M.approach(vz, M.cos(yaw) * ahead, 1.6f, dt);
        vy = M.approach(vy, lift > 0 ? 9 : -2.3f, lift > 0 ? 2.5f : 3, dt);
        if (lift <= 0 && !g.gear.stormcloak) {
            stamina -= (g.gear.outfit == Arms.WRAPS ? 3 : 6) * dt;
            stamDelay = .6f;
        }
        fallFrom = y;
        y += vy * dt;
        step(L, vx * dt, vz * dt);
        phase += dt * 2;
        float f = L.floor(x, z), w = L.water(x, z);
        if (!Float.isNaN(w) && y < w - .2f && f < w - 1) {
            setState(State.SWIM);
            g.splash(x, w, z);
        } else if (y <= f) {
            y = f;
            setState(State.MOVE);
        } else if (g.in.pressed(KeyEvent.VK_SPACE) || stamina <= 0) {
            if (stamina <= 0) g.say("Out of stamina: the Windcloak folds!");
            setState(State.AIR);
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
        float lim = st == State.AIR || st == State.GLIDE || st == State.HOP || st == State.FLIP ? .05f : L.maxStep();
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
        float tx, tz;
        if (rival != null) {
            tx = rival.x;
            tz = rival.z;
        } else if (target != null) {
            tx = target.x;
            tz = target.z;
            if (target.point(targetPoint, aimPt)) {
                tx = aimPt[0];
                tz = aimPt[2];
            }
        } else return;
        yaw = M.turn(yaw, M.atan2(tx - x, tz - z), rate * dt);
    }

    /** Knocked off balance by a duelling friend's parry. */
    void reel(float fromX, float fromZ) {
        float dx = x - fromX, dz = z - fromZ, d = Math.max(.01f, M.hypot(dx, dz));
        hopX = dx / d * 6;
        hopZ = dz / d * 6;
        setState(State.HURT);
        anim.restart(Characters.H_HURT, .04f);
    }

    /** Held up by the Skyward Gust's wind, facing the lock-on, until an attack spell (Space opens the glider; the wind fades after 15 s). */
    private void hover(float dt, Game g, float ix, float iz) {
        vx = M.approach(vx, ix * 2, 4, dt);
        vz = M.approach(vz, iz * 2, 4, dt);
        step(g.level, vx * dt, vz * dt);
        y = hoverY + M.sin(stT * 2.4f) * .18f;
        faceTarget(dt, 8);
        if (g.in.pressed(KeyEvent.VK_C) && g.magic.begin(this)) return;
        if (g.in.pressed(KeyEvent.VK_SPACE) && g.gear.windcloak && stamina > 4) {
            setState(State.GLIDE);
            g.sfx(Audio.GUST);
        } else if (stT > 15) {
            vy = 0;
            setState(State.AIR);
        }
    }

    /** How far the bowstring is drawn, 0 to 1. */
    float draw() { return st == State.AIM && !aimClaw ? Math.min(1, stT / .35f) : 0; }

    /** The locked-on spot, if any (on a co-op friend's copy, from the numbers the host sent). */
    boolean aimPoint(float[] out) {
        if (rival != null || target == null && locked && lockOn) {
            out[0] = lockX;
            out[1] = lockY;
            out[2] = lockZ;
            return true;
        }
        return target != null && target.alive() && target.point(targetPoint, out);
    }

    /** Called by the game when something hits the hero; returns false if the shield took it. */
    boolean hit(Game g, int dmg, float fromX, float fromZ, boolean blockable) {
        if (invuln > 0 || st == State.DEAD) return false;
        float dx = x - fromX, dz = z - fromZ, d = Math.max(.01f, M.hypot(dx, dz));
        boolean facing = blockable && blocking && Math.abs(M.angleDiff(yaw, M.atan2(-dx, -dz))) < 1.2f;
        if (facing && guardT < .25f) {
            // A guard raised just as the blow lands turns it aside for free and leaves the attacker reeling.
            g.parried(fromX, fromZ);
            return false;
        }
        if (facing && (stamina -= dmg * 7) > 0) {
            stamDelay = .8f;
            step(g.level, dx / d * .4f, dz / d * .4f);
            g.sfx(Audio.CLANG);
            g.sparks(x + M.sin(yaw) * .6f, y + 1.1f, z + M.cos(yaw) * .6f, 0xfff0c0, 8);
            g.tip("parry", "Blocked! Raise your shield just as a blow lands to PARRY: it costs nothing, the foe reels, and your next sword strike is a riposte for triple damage.");
            return false;
        }
        // Forgeplate turns a third of every blow aside.
        hp -= g.gear.outfit == Arms.PLATE ? Math.max(1, Math.round(dmg * .65f)) : dmg;
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
        // Heavy weapons swing slower, light ones faster.
        anim.update(st == State.ATTACK ? dt / Arms.TIME[g.gear.weapon] : dt);
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
            case GLIDE -> anim.play(Characters.H_GLIDE, .2f);
            case HOVER -> anim.play(Characters.H_HOVER, .3f);
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

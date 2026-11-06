package kestrel;

import java.awt.event.KeyEvent;

/** Juniper, the hero's mare: comes when called, carries the hero at a gallop, jumps, and lets the hero fight from the saddle. */
final class Horse {
    private static final Rig R = Characters.HORSE;
    private static final String[] LEGS = {"FL", "FR", "HL", "HR"};
    float x, y, z, yaw, speed, vy, phase, swingT;
    /** After dismounting, a moment before "Ride" is offered again, so one E press can't do both. */
    float remountT;
    boolean summoned, ridden, coming;
    private int swing;
    private boolean struck;
    private float hoofT;
    final float[] pose = R.newPose();
    private final float[] pos = new float[2];

    /** Brings Juniper galloping in from behind the hero. */
    void call(Game g) {
        Hero h = g.hero;
        float bx = h.x - M.sin(h.yaw) * 22, bz = h.z - M.cos(h.yaw) * 22;
        if (g.field.floor(bx, bz) > 900 || !Float.isNaN(g.field.water(bx, bz))) {
            bx = h.x + M.cos(h.yaw) * 3;
            bz = h.z - M.sin(h.yaw) * 3;
        }
        x = bx;
        z = bz;
        y = g.field.floor(x, z);
        yaw = M.atan2(h.x - x, h.z - z);
        summoned = coming = true;
        g.sfx(Audio.NEIGH);
    }

    void mount(Game g) {
        ridden = true;
        coming = false;
        speed = 0;
        g.hero.pose(Hero.State.RIDE, null);
        g.sfx(Audio.NEIGH);
    }

    void dismount(Game g) {
        ridden = false;
        speed = 0;
        remountT = .6f;
        Hero h = g.hero;
        float sx = x + M.cos(yaw) * 1.4f, sz = z - M.sin(yaw) * 1.4f;
        h.place(sx, g.field.floor(sx, sz), sz);
        h.yaw = yaw;
    }

    /** Sends Juniper away (dismounting first); the Song of the Steed calls her back. */
    void dismiss(Game g) {
        if (ridden) dismount(g);
        summoned = coming = false;
        g.sparks(x, y + 1.2f, z, 0xeadcb4, 14);
        g.sfx(Audio.NEIGH);
        g.say("Juniper trots off. Play the Song of the Steed (T, then 2 4 5 4 2) to call her back.");
    }

    /** Seat height above the ground, for placing the rider. */
    float seat() { return y + .86f + M.sin(phase * 2) * .04f * Math.min(1, speed / 6); }

    void update(float dt, Game g) {
        remountT -= dt;
        if (!summoned || g.dungeon != null) return;
        if (ridden) ride(dt, g);
        else if (coming) {
            Hero h = g.hero;
            float d = M.dist(x, z, h.x, h.z);
            yaw = M.turn(yaw, M.atan2(h.x - x, h.z - z), 3 * dt);
            speed = M.approach(speed, d > 3.5f ? Math.min(14, d * 2) : 0, 2, dt);
            if (d < 3.5f && speed < .5f) coming = false;
        } else speed = M.approach(speed, 0, 4, dt);
        Level L = g.field;
        float ground = L.floor(x, z);
        boolean onGround = y <= ground + .05f;
        float nx = x + M.sin(yaw) * speed * dt, nz = z + M.cos(yaw) * speed * dt, f = L.floor(nx, nz);
        boolean deep = !Float.isNaN(L.water(nx, nz)) && f < -.9f;
        if (f > y + (onGround ? .9f : .3f) || deep) speed *= .2f;
        else {
            x = nx;
            z = nz;
        }
        pos[0] = x;
        pos[1] = z;
        L.collide(pos, y, .7f);
        x = pos[0];
        z = pos[1];
        vy -= 26 * dt;
        y += vy * dt;
        ground = L.floor(x, z);
        if (y <= ground || onGround && y - ground < .9f && vy <= 0) {
            y = ground;
            vy = 0;
        }
        phase += dt * (2 + speed * .5f);
        if (speed > 1 && (hoofT -= dt) <= 0) {
            hoofT = 1.6f / (2 + speed * .5f);
            g.sfx(Audio.HOOF);
        }
    }

    private void ride(float dt, Game g) {
        Input in = g.in;
        Hero h = g.hero;
        float mx = (in.down(KeyEvent.VK_D) ? 1 : 0) - (in.down(KeyEvent.VK_A) ? 1 : 0);
        float mz = (in.down(KeyEvent.VK_W) ? 1 : 0) - (in.down(KeyEvent.VK_S) ? 1 : 0);
        float s = M.sin(g.camYaw), c = M.cos(g.camYaw), ix = mx * c + mz * s, iz = -mx * s + mz * c;
        if (mx != 0 || mz != 0) {
            float want = M.atan2(ix, iz), align = M.cos(M.angleDiff(yaw, want));
            yaw = M.turn(yaw, want, (2.8f - speed * .08f) * dt);
            speed = M.approach(speed, (in.down(KeyEvent.VK_SHIFT) ? 18 : 10) * Math.max(.15f, align), 1.3f, dt);
        } else speed = M.approach(speed, 0, 2.2f, dt);
        if (in.pressed(KeyEvent.VK_SPACE) && y <= g.field.floor(x, z) + .05f) {
            vy = 7.5f;
            g.sfx(Audio.JUMP);
        }
        if (in.any(KeyEvent.VK_J, Input.MOUSE_L) && swingT <= 0) {
            swing ^= 1;
            swingT = .45f;
            struck = false;
            h.anim.restart(swing == 0 ? Characters.H_SLASH1 : Characters.H_SLASH2, .05f);
            h.swordOutT = 3;
            g.sfx(Audio.SWING);
        }
        if (swingT > 0 && (swingT -= dt) < .27f && !struck) {
            struck = true;
            g.swordStrike(h, 260, 3.4f, 1.2f);
        }
        if (in.pressed(KeyEvent.VK_E) && speed < 6) dismount(g);
    }

    void draw(Renderer r, Xf xf) {
        if (!summoned) return;
        java.util.Arrays.fill(pose, 0);
        float s = Math.min(1, speed / 5), gallop = M.smooth(9, 14, speed);
        float[] off = gallop > .5f ? new float[]{0, .12f, .55f, .67f} : new float[]{0, .5f, .5f, 0};
        for (int k = 0; k < 4; k++) {
            float p = phase + off[k] * M.TAU, amp = (.32f + .3f * gallop) * s;
            pose[R.part("leg" + LEGS[k] + "u") * 3] = M.sin(p) * amp;
            pose[R.part("leg" + LEGS[k] + "l") * 3] = Math.max(0, M.sin(p + 1.4f)) * (.9f + .4f * gallop) * s * (k < 2 ? 1 : -.6f);
        }
        float bob = M.sin(phase * 2) * .05f * s;
        pose[R.part("neck") * 3] = .95f - .25f * gallop + M.sin(phase * 2 + .5f) * .06f * s;
        pose[R.part("head") * 3] = -.62f + .2f * gallop;
        pose[R.part("tail") * 3] = .45f + .5f * gallop + M.sin(phase * 2) * .08f;
        pose[R.part("tail") * 3 + 2] = M.sin(phase * 1.3f) * .15f;
        pose[R.n * 3] = bob;
        pose[R.n * 3 + 1] = M.sin(phase) * .05f * gallop - (vy != 0 ? M.clamp(vy * .04f, -.2f, .2f) : 0);
        R.draw(r, xf.identity().translate(x, y, z).rotY(yaw), pose);
    }
}

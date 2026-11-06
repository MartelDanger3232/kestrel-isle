package kestrel;

import java.util.HashMap;
import java.util.Map;

/**
 * Posable characters. A rig is a hierarchy of meshes; a pose holds (pitch, yaw, roll) per part plus a
 * root triple (lift, pitch, yaw). Clips are keyframed poses written in a small notation, for example
 * {@code "armUR:-1.5,.8 armLR:-.6 root:-.1"}; parts a clip never mentions keep the base pose.
 */
final class Rig {
    final int n;
    final String[] names;
    final int[] parent;
    final float[] ox, oy, oz;
    final Mesh[] mesh;
    final Xf[] xf;
    private final Map<String, Integer> index = new HashMap<>();

    /** Parts as (name, parentName or null, x, y, z, mesh) groups of six, parents first. */
    Rig(Object... spec) {
        n = spec.length / 6;
        names = new String[n];
        parent = new int[n];
        ox = new float[n];
        oy = new float[n];
        oz = new float[n];
        mesh = new Mesh[n];
        xf = new Xf[n];
        for (int i = 0; i < n; i++) {
            names[i] = (String) spec[i * 6];
            parent[i] = spec[i * 6 + 1] == null ? -1 : index.get((String) spec[i * 6 + 1]);
            ox[i] = ((Number) spec[i * 6 + 2]).floatValue();
            oy[i] = ((Number) spec[i * 6 + 3]).floatValue();
            oz[i] = ((Number) spec[i * 6 + 4]).floatValue();
            mesh[i] = (Mesh) spec[i * 6 + 5];
            xf[i] = new Xf();
            index.put(names[i], i);
        }
    }

    int part(String name) {
        Integer i = index.get(name);
        if (i == null) throw new IllegalArgumentException("no part " + name);
        return i;
    }

    boolean has(String name) { return index.containsKey(name); }

    float[] newPose() { return new float[n * 3 + 3]; }

    /** Writes "part:pitch,yaw,roll ..." into a pose; "root:lift,pitch,yaw" moves the whole body. */
    float[] parse(String spec, float[] pose, boolean[] mask) {
        for (String tok : spec.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            int c = tok.indexOf(':');
            int base = tok.startsWith("root:") ? n * 3 : part(tok.substring(0, c)) * 3;
            String[] v = tok.substring(c + 1).split(",");
            for (int k = 0; k < 3; k++) {
                if (k < v.length && !v[k].isEmpty()) pose[base + k] = Float.parseFloat(v[k]);
                if (mask != null) mask[base + k] = true;
            }
        }
        return pose;
    }

    void draw(Renderer r, Xf root, float[] pose) {
        int R = n * 3;
        for (int i = 0; i < n; i++) {
            Xf x = xf[i];
            if (parent[i] < 0) x.set(root).translate(0, pose[R], 0).rotY(pose[R + 2]).translate(ox[i], oy[i], oz[i]).rotX(pose[R + 1]);
            else x.set(xf[parent[i]]).translate(ox[i], oy[i], oz[i]);
            x.rotY(pose[i * 3 + 1]).rotX(pose[i * 3]).rotZ(pose[i * 3 + 2]);
            if (mesh[i] != null) r.mesh(mesh[i], x);
        }
    }

    /** Keyframed animation; poses only drive the components named in them. */
    static final class Clip {
        final float[] times;
        final float[][] poses;
        final boolean[] mask;
        final boolean loop;
        final float length;

        Clip(Rig rig, boolean loop, Object... keys) {
            this.loop = loop;
            times = new float[keys.length / 2];
            poses = new float[times.length][];
            mask = new boolean[rig.n * 3 + 3];
            for (int k = 0; k < times.length; k++) {
                times[k] = ((Number) keys[k * 2]).floatValue();
                poses[k] = rig.parse((String) keys[k * 2 + 1], rig.newPose(), mask);
            }
            length = times[times.length - 1];
        }

        /** Blends this clip at time t into out with weight w. */
        void apply(float t, float w, float[] out) {
            float tt = loop && length > 0 ? t % length : Math.min(t, length);
            int k = 0;
            while (k < times.length - 2 && tt > times[k + 1]) k++;
            int k1 = Math.min(k + 1, times.length - 1);
            float span = times[k1] - times[k], f = span > 0 ? M.smooth(0, 1, (tt - times[k]) / span) : 1;
            float[] a = poses[k], b = poses[k1];
            for (int i = 0; i < out.length; i++)
                if (mask[i]) out[i] = M.lerp(out[i], a[i] + (b[i] - a[i]) * f, w);
        }
    }

    /** Plays clips over a base pose, crossfading between them. */
    static final class Anim {
        Clip clip, prev;
        float t, prevT, fade, fadeDur;
        private float[] a, b;

        void play(Clip c, float fadeTime) {
            if (c == clip) return;
            prev = clip;
            prevT = t;
            clip = c;
            t = 0;
            fade = 0;
            fadeDur = fadeTime;
        }

        void restart(Clip c, float fadeTime) {
            clip = null;
            play(c, fadeTime);
        }

        void update(float dt) {
            t += dt;
            prevT += dt;
            fade += dt;
        }

        boolean done() { return clip == null || !clip.loop && t >= clip.length; }

        /** Turns the base pose in {@code pose} into the animated pose. */
        void apply(float[] pose) {
            if (a == null || a.length != pose.length) {
                a = new float[pose.length];
                b = new float[pose.length];
            }
            float w = fadeDur <= 0 ? 1 : M.clamp(fade / fadeDur, 0, 1);
            System.arraycopy(pose, 0, b, 0, pose.length);
            if (clip != null) clip.apply(t, 1, b);
            if (w >= 1 || prev == null && fadeDur <= 0) {
                System.arraycopy(b, 0, pose, 0, pose.length);
                return;
            }
            System.arraycopy(pose, 0, a, 0, pose.length);
            if (prev != null) prev.apply(prevT, 1, a);
            for (int i = 0; i < pose.length; i++) pose[i] = a[i] + (b[i] - a[i]) * w;
        }
    }

    /**
     * Procedural idle/walk/run for humanoid rigs (parts torso, head, armU/L L/R, legU/L L/R).
     * run: 0 standing, 1 walking, 2 sprinting.
     */
    void locomote(float[] pose, float phase, float run, float time) {
        java.util.Arrays.fill(pose, 0);
        int torso = part("torso"), head = part("head");
        int aUL = part("armUL"), aLL = part("armLL"), aUR = part("armUR"), aLR = part("armLR");
        int lUL = part("legUL"), lLL = part("legLL"), lUR = part("legUR"), lLR = part("legLR");
        float move = Math.min(1, run), fast = M.clamp(run - 1, 0, 1);
        float s = M.sin(phase), c = M.cos(phase), amp = (.5f + .25f * fast) * move;
        float breathe = M.sin(time * 2.2f) * .03f * (1 - move);
        pose[torso * 3] = .04f + .14f * fast + breathe;
        pose[torso * 3 + 1] = s * .14f * move;
        pose[head * 3] = -.04f - .08f * fast - breathe;
        pose[head * 3 + 1] = -s * .1f * move;
        pose[lUL * 3] = -s * amp - .05f * fast;
        pose[lUR * 3] = s * amp - .05f * fast;
        pose[lLL * 3] = .08f + Math.max(0, -c) * (.7f + .5f * fast) * move;
        pose[lLR * 3] = .08f + Math.max(0, c) * (.7f + .5f * fast) * move;
        pose[aUL * 3] = s * amp * .9f;
        pose[aUR * 3] = -s * amp * .9f;
        pose[aUL * 3 + 2] = -.1f - .05f * move;
        pose[aUR * 3 + 2] = .1f + .05f * move;
        pose[aLL * 3] = -.2f - .5f * fast - .1f * move;
        pose[aLR * 3] = -.2f - .5f * fast - .1f * move;
        pose[n * 3] = -Math.abs(c) * .05f * move * (1 + fast) + .02f * move;
    }
}

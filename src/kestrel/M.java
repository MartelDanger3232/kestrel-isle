package kestrel;

/** Small float math and colour helpers shared across the game. */
final class M {
    static final float PI = (float) Math.PI, TAU = PI * 2;

    private M() {}

    static float sin(float a) { return (float) Math.sin(a); }
    static float cos(float a) { return (float) Math.cos(a); }
    static float atan2(float y, float x) { return (float) Math.atan2(y, x); }
    static float sqrt(float v) { return (float) Math.sqrt(v); }
    static float hypot(float a, float b) { return sqrt(a * a + b * b); }
    static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    static float dist(float x0, float z0, float x1, float z1) { return hypot(x1 - x0, z1 - z0); }

    /** Smoothstep that also works with a > b (falling edge). */
    static float smooth(float a, float b, float x) {
        float t = clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static float angleDiff(float from, float to) {
        float d = (to - from) % TAU;
        if (d > PI) d -= TAU;
        else if (d < -PI) d += TAU;
        return d;
    }

    /** Rotate angle {@code from} towards {@code to} by at most {@code step}. */
    static float turn(float from, float to, float step) {
        float d = angleDiff(from, to);
        return Math.abs(d) <= step ? from + d : from + Math.signum(d) * step;
    }

    static float approach(float v, float target, float rate, float dt) {
        return v + (target - v) * Math.min(1, rate * dt);
    }

    static int rgb(int r, int g, int b) {
        return Math.clamp(r, 0, 255) << 16 | Math.clamp(g, 0, 255) << 8 | Math.clamp(b, 0, 255);
    }

    static int mix(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        return rgb((int) (ar + ((b >> 16 & 255) - ar) * t), (int) (ag + ((b >> 8 & 255) - ag) * t),
                (int) (ab + ((b & 255) - ab) * t));
    }

    static int scale(int c, float f) {
        return rgb((int) ((c >> 16 & 255) * f), (int) ((c >> 8 & 255) * f), (int) ((c & 255) * f));
    }

    /** Cheap deterministic hash in [0,1). */
    static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffffff) / (float) 0x1000000;
    }
}

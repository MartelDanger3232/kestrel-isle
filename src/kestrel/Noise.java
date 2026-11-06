package kestrel;

import java.util.Random;

/** Seeded 2D gradient noise and the fractal variants the terrain is built from. */
final class Noise {
    private static final float[] GX = {1, -1, 1, -1, 1.41f, -1.41f, 0, 0};
    private static final float[] GZ = {1, 1, -1, -1, 0, 0, 1.41f, -1.41f};
    private final int[] perm = new int[512];

    Noise(long seed) {
        Random r = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1), t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static float grad(int h, float x, float z) {
        return GX[h & 7] * x + GZ[h & 7] * z;
    }

    /** Roughly in [-0.7, 0.7]. */
    float get(float x, float z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        float u = fx * fx * fx * (fx * (fx * 6 - 15) + 10);
        float v = fz * fz * fz * (fz * (fz * 6 - 15) + 10);
        int X = xi & 255, Z = zi & 255;
        int a = perm[X] + Z, b = perm[X + 1] + Z;
        float n00 = grad(perm[a], fx, fz), n10 = grad(perm[b], fx - 1, fz);
        float n01 = grad(perm[a + 1], fx, fz - 1), n11 = grad(perm[b + 1], fx - 1, fz - 1);
        float top = n00 + (n10 - n00) * u, bot = n01 + (n11 - n01) * u;
        return top + (bot - top) * v;
    }

    float fbm(float x, float z, int octaves) {
        float sum = 0, amp = .5f;
        for (int i = 0; i < octaves; i++) {
            sum += get(x, z) * amp;
            x = x * 2.03f + 17.1f;
            z = z * 2.03f - 9.7f;
            amp *= .5f;
        }
        return sum;
    }

    /** Sharp mountain ridges, roughly in [0, 1]. */
    float ridged(float x, float z, int octaves) {
        float sum = 0, amp = .5f, weight = 1;
        for (int i = 0; i < octaves; i++) {
            float v = Math.max(0, 1 - Math.abs(get(x, z)) * 1.7f);
            v *= v * weight;
            sum += v * amp;
            weight = M.clamp(v * 2, 0, 1);
            x = x * 2.1f + 13.3f;
            z = z * 2.1f + 7.9f;
            amp *= .5f;
        }
        return sum * 1.1f;
    }
}

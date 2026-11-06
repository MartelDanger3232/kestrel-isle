package kestrel;

/** Mutable 3x4 affine transform (row-major). Operations post-multiply, so they read outside-in. */
final class Xf {
    final float[] m = new float[12];

    Xf() { identity(); }

    Xf identity() {
        java.util.Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = 1;
        return this;
    }

    Xf set(Xf o) {
        System.arraycopy(o.m, 0, m, 0, 12);
        return this;
    }

    Xf translate(float x, float y, float z) {
        m[3] += m[0] * x + m[1] * y + m[2] * z;
        m[7] += m[4] * x + m[5] * y + m[6] * z;
        m[11] += m[8] * x + m[9] * y + m[10] * z;
        return this;
    }

    /** Yaw: model +z faces (sin a, 0, cos a). */
    Xf rotY(float a) {
        float c = M.cos(a), s = M.sin(a);
        for (int r = 0; r < 12; r += 4) {
            float x = m[r], z = m[r + 2];
            m[r] = x * c - z * s;
            m[r + 2] = x * s + z * c;
        }
        return this;
    }

    /** Pitch: positive tips the model's +y towards +z. */
    Xf rotX(float a) {
        float c = M.cos(a), s = M.sin(a);
        for (int r = 0; r < 12; r += 4) {
            float y = m[r + 1], z = m[r + 2];
            m[r + 1] = y * c + z * s;
            m[r + 2] = -y * s + z * c;
        }
        return this;
    }

    Xf rotZ(float a) {
        float c = M.cos(a), s = M.sin(a);
        for (int r = 0; r < 12; r += 4) {
            float x = m[r], y = m[r + 1];
            m[r] = x * c + y * s;
            m[r + 1] = -x * s + y * c;
        }
        return this;
    }

    Xf scale(float sx, float sy, float sz) {
        for (int r = 0; r < 12; r += 4) {
            m[r] *= sx;
            m[r + 1] *= sy;
            m[r + 2] *= sz;
        }
        return this;
    }

    Xf scale(float s) { return scale(s, s, s); }
}

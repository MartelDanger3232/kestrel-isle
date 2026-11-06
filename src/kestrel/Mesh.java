package kestrel;

import java.util.Arrays;
import java.util.Random;

/** Flat-shaded triangle soup. Faces made by the builders are wound to face away from their solid's centre. */
final class Mesh {
    /** Colour flag: the triangle ignores lighting (fire, crystals, eyes, lava). */
    static final int GLOW = 0x1000000;
    private static final Random RNG = new Random(42);
    private static final int[][] BOX = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};

    float[] p = new float[9 * 16];
    float[] n = new float[3 * 16];
    int[] c = new int[16];
    int tris;
    boolean twoSided;
    private float vary;

    /** Random brightness variation per face, for a hand-painted low-poly look. */
    Mesh vary(float amount) {
        vary = amount;
        return this;
    }

    Mesh twoSided() {
        twoSided = true;
        return this;
    }

    /** Per-corner normals (nine per triangle) once {@link #smooth()} has run. */
    float[] vn;
    boolean smooth;

    /**
     * Rounds the shading: each corner's normal averages the faces meeting there within about 50 degrees,
     * so curved surfaces look smooth while hard edges stay crisp.
     */
    Mesh smooth() {
        vn = new float[tris * 9];
        java.util.Map<Long, java.util.List<Integer>> at = new java.util.HashMap<>();
        for (int k = 0; k < tris * 3; k++) at.computeIfAbsent(key(k), q -> new java.util.ArrayList<>()).add(k / 3);
        for (int k = 0; k < tris * 3; k++) {
            int t = k / 3;
            float sx = 0, sy = 0, sz = 0;
            for (int o : at.get(key(k))) {
                float d = n[t * 3] * n[o * 3] + n[t * 3 + 1] * n[o * 3 + 1] + n[t * 3 + 2] * n[o * 3 + 2];
                if (d < .64f) continue;
                sx += n[o * 3];
                sy += n[o * 3 + 1];
                sz += n[o * 3 + 2];
            }
            float l = M.sqrt(sx * sx + sy * sy + sz * sz);
            vn[k * 3] = sx / l;
            vn[k * 3 + 1] = sy / l;
            vn[k * 3 + 2] = sz / l;
        }
        smooth = true;
        return this;
    }

    private long key(int corner) {
        long x = Math.round(p[corner * 3] * 2000), y = Math.round(p[corner * 3 + 1] * 2000), z = Math.round(p[corner * 3 + 2] * 2000);
        return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
    }

    /** A tapered tube between two arbitrary points (radius r0 at a, r1 at b), capped at both ends. */
    Mesh segment(float ax, float ay, float az, float bx, float by, float bz, float r0, float r1, int sides, int col) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, l = M.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= l;
        dy /= l;
        dz /= l;
        float ux = Math.abs(dy) < .9f ? 0 : 1, uy = Math.abs(dy) < .9f ? 1 : 0;
        float px = uy * dz, py = -ux * dz, pz = ux * dy - uy * dx, pl = M.sqrt(px * px + py * py + pz * pz);
        px /= pl;
        py /= pl;
        pz /= pl;
        float qx = dy * pz - dz * py, qy = dz * px - dx * pz, qz = dx * py - dy * px;
        float[][] lo = new float[sides][], hi = new float[sides][];
        for (int i = 0; i < sides; i++) {
            float a = i * M.TAU / sides, c = M.cos(a), s = M.sin(a);
            lo[i] = new float[]{ax + (px * c + qx * s) * r0, ay + (py * c + qy * s) * r0, az + (pz * c + qz * s) * r0};
            hi[i] = new float[]{bx + (px * c + qx * s) * r1, by + (py * c + qy * s) * r1, bz + (pz * c + qz * s) * r1};
        }
        float ox = (ax + bx) / 2, oy = (ay + by) / 2, oz = (az + bz) / 2;
        float[] ea = {ax, ay, az}, eb = {bx, by, bz};
        for (int i = 0; i < sides; i++) {
            int k = (i + 1) % sides, fc = vc(col);
            quad(lo[i], lo[k], hi[k], hi[i], ox, oy, oz, fc);
            face(hi[i], hi[k], eb, ox, oy, oz, fc);
            face(lo[k], lo[i], ea, ox, oy, oz, fc);
        }
        return this;
    }

    /** Copies another mesh's triangles into this one. */
    Mesh append(Mesh from) {
        for (int i = 0; i < from.tris; i++) {
            int o = i * 9;
            float[] q = from.p;
            tri(q[o], q[o + 1], q[o + 2], q[o + 3], q[o + 4], q[o + 5], q[o + 6], q[o + 7], q[o + 8], from.c[i]);
        }
        return this;
    }

    /** Elliptical tube standing on (x, y, z): radii (rx0, rz0) at the bottom and (rx1, rz1) at the top, both ends capped. */
    Mesh tube(float x, float y, float z, float rx0, float rz0, float rx1, float rz1, float h, int sides, int col) {
        float[][] lo = new float[sides][], hi = new float[sides][];
        for (int i = 0; i < sides; i++) {
            float a = i * M.TAU / sides;
            lo[i] = new float[]{x + M.sin(a) * rx0, y, z + M.cos(a) * rz0};
            hi[i] = new float[]{x + M.sin(a) * rx1, y + h, z + M.cos(a) * rz1};
        }
        float oy = y + h * .5f;
        float[] top = {x, y + h, z}, bottom = {x, y, z};
        for (int i = 0; i < sides; i++) {
            int k = (i + 1) % sides, fc = vc(col);
            quad(lo[i], lo[k], hi[k], hi[i], x, oy, z, fc);
            if (rx1 > 0) face(hi[i], hi[k], top, x, oy, z, fc);
            if (rx0 > 0) face(lo[k], lo[i], bottom, x, oy, z, fc);
        }
        return this;
    }

    private int vc(int col) {
        return vary <= 0 || (col & Mesh.GLOW) != 0 ? col : M.scale(col, 1 + (RNG.nextFloat() - .5f) * 2 * vary);
    }

    Mesh tri(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, int col) {
        if (tris == c.length) {
            p = Arrays.copyOf(p, p.length * 2);
            n = Arrays.copyOf(n, n.length * 2);
            c = Arrays.copyOf(c, c.length * 2);
        }
        int o = tris * 9;
        p[o] = ax; p[o + 1] = ay; p[o + 2] = az;
        p[o + 3] = bx; p[o + 4] = by; p[o + 5] = bz;
        p[o + 6] = cx; p[o + 7] = cy; p[o + 8] = cz;
        float ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float l = M.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9f) l = 1;
        n[tris * 3] = nx / l;
        n[tris * 3 + 1] = ny / l;
        n[tris * 3 + 2] = nz / l;
        c[tris++] = col;
        return this;
    }

    private void face(float[] a, float[] b, float[] q, float ox, float oy, float oz, int col) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = q[0] - a[0], vy = q[1] - a[1], vz = q[2] - a[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float d = nx * ((a[0] + b[0] + q[0]) / 3 - ox) + ny * ((a[1] + b[1] + q[1]) / 3 - oy)
                + nz * ((a[2] + b[2] + q[2]) / 3 - oz);
        if (d >= 0) tri(a[0], a[1], a[2], b[0], b[1], b[2], q[0], q[1], q[2], col);
        else tri(a[0], a[1], a[2], q[0], q[1], q[2], b[0], b[1], b[2], col);
    }

    private void quad(float[] a, float[] b, float[] q, float[] d, float ox, float oy, float oz, int col) {
        face(a, b, q, ox, oy, oz, col);
        face(a, q, d, ox, oy, oz, col);
    }

    /** Axis-aligned box from centre and half extents. */
    Mesh box(float x, float y, float z, float hx, float hy, float hz, int col) {
        float[][] v = new float[8][];
        for (int i = 0; i < 8; i++)
            v[i] = new float[]{x + ((i & 1) == 0 ? -hx : hx), y + ((i & 2) == 0 ? -hy : hy), z + ((i & 4) == 0 ? -hz : hz)};
        for (int[] f : BOX) quad(v[f[0]], v[f[1]], v[f[2]], v[f[3]], x, y, z, vc(col));
        return this;
    }

    Mesh prism(float x, float y, float z, float r0, float r1, float h, int sides, int col) {
        return prism(x, y, z, r0, r1, h, sides, col, 0);
    }

    /** Cylinder or cone standing on (x, y, z); r1 = 0 makes a cone. Top cap only. */
    Mesh prism(float x, float y, float z, float r0, float r1, float h, int sides, int col, float jitter) {
        float[][] lo = new float[sides][], hi = new float[sides][];
        for (int i = 0; i < sides; i++) {
            float a = i * M.TAU / sides;
            float j0 = 1 + (RNG.nextFloat() - .5f) * jitter, j1 = 1 + (RNG.nextFloat() - .5f) * jitter;
            lo[i] = new float[]{x + M.sin(a) * r0 * j0, y, z + M.cos(a) * r0 * j0};
            hi[i] = new float[]{x + M.sin(a) * r1 * j1, y + h, z + M.cos(a) * r1 * j1};
        }
        float oy = y + h * .5f;
        float[] top = {x, y + h, z};
        for (int i = 0; i < sides; i++) {
            int k = (i + 1) % sides, fc = vc(col);
            if (r1 > 0) {
                quad(lo[i], lo[k], hi[k], hi[i], x, oy, z, fc);
                face(hi[i], hi[k], top, x, oy, z, fc);
            } else {
                face(lo[i], lo[k], top, x, oy, z, fc);
            }
        }
        return this;
    }

    /** Lumpy ellipsoid: rocks, canopies, bodies. rings=2, segs=4 gives an octahedron. */
    Mesh blob(float x, float y, float z, float rx, float ry, float rz, int rings, int segs, int col, float jitter) {
        float[][][] v = new float[rings + 1][segs][];
        for (int i = 0; i <= rings; i++) {
            float lat = M.PI * i / rings - M.PI / 2;
            for (int j = 0; j < segs; j++) {
                float lon = M.TAU * j / segs + (i % 2) * M.PI / segs;
                float jt = i == 0 || i == rings ? 1 : 1 + (RNG.nextFloat() - .5f) * jitter;
                v[i][j] = new float[]{x + M.cos(lat) * M.sin(lon) * rx * jt, y + M.sin(lat) * ry * jt,
                        z + M.cos(lat) * M.cos(lon) * rz * jt};
            }
        }
        for (int i = 0; i < rings; i++)
            for (int j = 0; j < segs; j++) {
                int k = (j + 1) % segs, fc = vc(col);
                if (i == 0) face(v[0][0], v[1][j], v[1][k], x, y, z, fc);
                else if (i == rings - 1) face(v[i][j], v[i][k], v[rings][0], x, y, z, fc);
                else quad(v[i][j], v[i][k], v[i + 1][k], v[i + 1][j], x, y, z, fc);
            }
        return this;
    }

    /** Flat hoop in the XY plane (faces +z), always drawn two-sided. */
    Mesh ring(float r, float t, int segs, int col) {
        for (int i = 0; i < segs; i++) {
            float a0 = i * M.TAU / segs, a1 = (i + 1) * M.TAU / segs;
            float s0 = M.sin(a0), c0 = M.cos(a0), s1 = M.sin(a1), c1 = M.cos(a1);
            tri(s0 * (r - t), c0 * (r - t), 0, s0 * (r + t), c0 * (r + t), 0, s1 * (r + t), c1 * (r + t), 0, col);
            tri(s0 * (r - t), c0 * (r - t), 0, s1 * (r + t), c1 * (r + t), 0, s1 * (r - t), c1 * (r - t), 0, col);
        }
        return twoSided();
    }
}

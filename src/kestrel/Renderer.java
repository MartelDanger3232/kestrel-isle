package kestrel;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/** Software rasteriser: flat-shaded, z-buffered triangles into a low-res frame. */
final class Renderer {
    static final float NEAR = .2f;
    final int w, h;
    final BufferedImage image;
    final int[] px;
    final float[] zb;
    final float hw, hh, foc;
    private final float cosH, sinH, cosV, sinV;
    // Camera position and basis (right has no y component: the camera never rolls).
    float cx, cy, cz, rx, rz, ux, uy, uz, fx, fy, fz;
    // Lighting, copied from Climate every frame.
    float lx, ly = 1, lz, ar = .5f, ag = .5f, ab = .5f, sr = .6f, sg = .6f, sb = .6f, fogStart = 150, fogEnd = 1600;
    int fogCol = 0xb0d0f0;
    /** Pushes everything drawn towards white (hit flashes). */
    float flash;
    int flashCol = 0xffffff;
    private final float[] cin = new float[9], cout = new float[12];

    Renderer(int w, int h) {
        this.w = w;
        this.h = h;
        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        px = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        zb = new float[w * h];
        hw = w * .5f;
        hh = h * .5f;
        foc = hh / (float) Math.tan(Math.toRadians(30));
        float tH = hw / foc, tV = hh / foc;
        cosH = 1 / M.sqrt(1 + tH * tH);
        sinH = tH * cosH;
        cosV = 1 / M.sqrt(1 + tV * tV);
        sinV = tV * cosV;
    }

    void camera(float x, float y, float z, float yaw, float pitch) {
        cx = x;
        cy = y;
        cz = z;
        float sy = M.sin(yaw), cyw = M.cos(yaw), sp = M.sin(pitch), cp = M.cos(pitch);
        fx = sy * cp;
        fy = sp;
        fz = cyw * cp;
        rx = cyw;
        rz = -sy;
        ux = -sy * sp;
        uy = cp;
        uz = -cyw * sp;
        Arrays.fill(zb, 0);
    }

    /** Sphere-vs-frustum test (no far plane). */
    boolean visible(float x, float y, float z, float rad) {
        float dx = x - cx, dy = y - cy, dz = z - cz;
        float vx = dx * rx + dz * rz, vy = dx * ux + dy * uy + dz * uz, vz = dx * fx + dy * fy + dz * fz;
        return vz > -rad && -vx * cosH + vz * sinH > -rad && vx * cosH + vz * sinH > -rad
                && -vy * cosV + vz * sinV > -rad && vy * cosV + vz * sinV > -rad;
    }

    float depthOf(float x, float y, float z) {
        return (x - cx) * fx + (y - cy) * fy + (z - cz) * fz;
    }

    /** Screen position of a world point; false when behind the camera. */
    boolean project(float x, float y, float z, float[] out) {
        float dx = x - cx, dy = y - cy, dz = z - cz;
        float vz = dx * fx + dy * fy + dz * fz;
        if (vz < NEAR) return false;
        out[0] = hw + foc * (dx * rx + dz * rz) / vz;
        out[1] = hh - foc * (dx * ux + dy * uy + dz * uz) / vz;
        out[2] = vz;
        return true;
    }

    float fog(float depth) {
        if (depth <= fogStart) return 0;
        float t = Math.min(1, (depth - fogStart) / (fogEnd - fogStart));
        return t * (2 - t);
    }

    /** Lit, fogged colour of a face with world normal n seen at the given depth. */
    int shade(int col, float nx, float ny, float nz, float depth) {
        float r = col >> 16 & 255, g = col >> 8 & 255, b = col & 255;
        if ((col & Mesh.GLOW) == 0) {
            float d = Math.max(0, nx * lx + ny * ly + nz * lz);
            r *= ar + sr * d;
            g *= ag + sg * d;
            b *= ab + sb * d;
        }
        if (flash > 0) {
            r += ((flashCol >> 16 & 255) - r) * flash;
            g += ((flashCol >> 8 & 255) - g) * flash;
            b += ((flashCol & 255) - b) * flash;
        }
        float f = fog(depth);
        if (f > 0) {
            r += ((fogCol >> 16 & 255) - r) * f;
            g += ((fogCol >> 8 & 255) - g) * f;
            b += ((fogCol & 255) - b) * f;
        }
        return M.rgb((int) r, (int) g, (int) b);
    }

    void mesh(Mesh m, Xf t) {
        mesh(m, t, 256);
    }

    /** Draws a mesh; alpha below 256 blends it (no depth write). */
    void mesh(Mesh m, Xf t, int alpha) {
        float[] a = t.m;
        float ox = a[3] - cx, oy = a[7] - cy, oz = a[11] - cz;
        float m00 = rx * a[0] + rz * a[8], m01 = rx * a[1] + rz * a[9], m02 = rx * a[2] + rz * a[10], m03 = rx * ox + rz * oz;
        float m10 = ux * a[0] + uy * a[4] + uz * a[8], m11 = ux * a[1] + uy * a[5] + uz * a[9];
        float m12 = ux * a[2] + uy * a[6] + uz * a[10], m13 = ux * ox + uy * oy + uz * oz;
        float m20 = fx * a[0] + fy * a[4] + fz * a[8], m21 = fx * a[1] + fy * a[5] + fz * a[9];
        float m22 = fx * a[2] + fy * a[6] + fz * a[10], m23 = fx * ox + fy * oy + fz * oz;
        float inv = 1 / M.sqrt(a[0] * a[0] + a[4] * a[4] + a[8] * a[8]);
        float[] p = m.p, n = m.n;
        for (int i = 0, o = 0; i < m.tris; i++, o += 9) {
            float x0 = p[o], y0 = p[o + 1], z0 = p[o + 2], x1 = p[o + 3], y1 = p[o + 4], z1 = p[o + 5];
            float x2 = p[o + 6], y2 = p[o + 7], z2 = p[o + 8];
            float az = m20 * x0 + m21 * y0 + m22 * z0 + m23, bz = m20 * x1 + m21 * y1 + m22 * z1 + m23;
            float qz = m20 * x2 + m21 * y2 + m22 * z2 + m23;
            if (az < NEAR && bz < NEAR && qz < NEAR) continue;
            int k = i * 3;
            if (m.smooth && alpha >= 256 && (m.c[i] & Mesh.GLOW) == 0) {
                float[] vn = m.vn;
                int base = i * 9, c0 = m.c[i];
                int ca = shade(c0, (a[0] * vn[base] + a[1] * vn[base + 1] + a[2] * vn[base + 2]) * inv,
                        (a[4] * vn[base] + a[5] * vn[base + 1] + a[6] * vn[base + 2]) * inv, (a[8] * vn[base] + a[9] * vn[base + 1] + a[10] * vn[base + 2]) * inv, az);
                int cb = shade(c0, (a[0] * vn[base + 3] + a[1] * vn[base + 4] + a[2] * vn[base + 5]) * inv,
                        (a[4] * vn[base + 3] + a[5] * vn[base + 4] + a[6] * vn[base + 5]) * inv, (a[8] * vn[base + 3] + a[9] * vn[base + 4] + a[10] * vn[base + 5]) * inv, bz);
                int cq = shade(c0, (a[0] * vn[base + 6] + a[1] * vn[base + 7] + a[2] * vn[base + 8]) * inv,
                        (a[4] * vn[base + 6] + a[5] * vn[base + 7] + a[6] * vn[base + 8]) * inv, (a[8] * vn[base + 6] + a[9] * vn[base + 7] + a[10] * vn[base + 8]) * inv, qz);
                emitSmooth(m00 * x0 + m01 * y0 + m02 * z0 + m03, m10 * x0 + m11 * y0 + m12 * z0 + m13, az, ca,
                        m00 * x1 + m01 * y1 + m02 * z1 + m03, m10 * x1 + m11 * y1 + m12 * z1 + m13, bz, cb,
                        m00 * x2 + m01 * y2 + m02 * z2 + m03, m10 * x2 + m11 * y2 + m12 * z2 + m13, qz, cq, m.twoSided);
                continue;
            }
            float nx = (a[0] * n[k] + a[1] * n[k + 1] + a[2] * n[k + 2]) * inv;
            float ny = (a[4] * n[k] + a[5] * n[k + 1] + a[6] * n[k + 2]) * inv;
            float nz = (a[8] * n[k] + a[9] * n[k + 1] + a[10] * n[k + 2]) * inv;
            int col = shade(m.c[i], nx, ny, nz, (az + bz + qz) * .333f);
            emit(m00 * x0 + m01 * y0 + m02 * z0 + m03, m10 * x0 + m11 * y0 + m12 * z0 + m13, az,
                    m00 * x1 + m01 * y1 + m02 * z1 + m03, m10 * x1 + m11 * y1 + m12 * z1 + m13, bz,
                    m00 * x2 + m01 * y2 + m02 * z2 + m03, m10 * x2 + m11 * y2 + m12 * z2 + m13, qz, col, m.twoSided, alpha);
        }
    }

    /** World-space triangle with an already-lit colour. */
    void worldTri(float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz,
                  int col, boolean two, int alpha) {
        ax -= cx; ay -= cy; az -= cz;
        bx -= cx; by -= cy; bz -= cz;
        qx -= cx; qy -= cy; qz -= cz;
        emit(ax * rx + az * rz, ax * ux + ay * uy + az * uz, ax * fx + ay * fy + az * fz,
                bx * rx + bz * rz, bx * ux + by * uy + bz * uz, bx * fx + by * fy + bz * fz,
                qx * rx + qz * rz, qx * ux + qy * uy + qz * uz, qx * fx + qy * fy + qz * fz, col, two, alpha);
    }

    /** Triangle in view space; clips against the near plane, culls back faces unless two-sided. */
    void emit(float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz,
              int col, boolean two, int alpha) {
        if (az >= NEAR && bz >= NEAR && qz >= NEAR) {
            float aw = 1 / az, bw = 1 / bz, qw = 1 / qz;
            float sax = hw + foc * ax * aw, say = hh - foc * ay * aw;
            float sbx = hw + foc * bx * bw, sby = hh - foc * by * bw;
            float sqx = hw + foc * qx * qw, sqy = hh - foc * qy * qw;
            if (!two && (sbx - sax) * (sqy - say) - (sqx - sax) * (sby - say) <= 0) return;
            fill(sax, say, aw, sbx, sby, bw, sqx, sqy, qw, col, alpha);
            return;
        }
        if (az < NEAR && bz < NEAR && qz < NEAR) return;
        float[] v = cin, o = cout;
        v[0] = ax; v[1] = ay; v[2] = az; v[3] = bx; v[4] = by; v[5] = bz; v[6] = qx; v[7] = qy; v[8] = qz;
        int n = 0;
        for (int i = 0; i < 3; i++) {
            int a = i * 3, b = (i + 1) % 3 * 3;
            boolean inA = v[a + 2] >= NEAR, inB = v[b + 2] >= NEAR;
            if (inA) {
                o[n++] = v[a]; o[n++] = v[a + 1]; o[n++] = v[a + 2];
            }
            if (inA != inB) {
                float t = (NEAR - v[a + 2]) / (v[b + 2] - v[a + 2]);
                o[n++] = v[a] + (v[b] - v[a]) * t;
                o[n++] = v[a + 1] + (v[b + 1] - v[a + 1]) * t;
                o[n++] = NEAR;
            }
        }
        for (int i = 3; i + 3 < n; i += 3)
            emit(o[0], o[1], o[2], o[i], o[i + 1], o[i + 2], o[i + 3], o[i + 4], o[i + 5], col, two, alpha);
    }

    private void fill(float ax, float ay, float aw, float bx, float by, float bw, float qx, float qy, float qw,
                      int col, int alpha) {
        float t;
        if (ay > by) { t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; t = aw; aw = bw; bw = t; }
        if (by > qy) { t = bx; bx = qx; qx = t; t = by; by = qy; qy = t; t = bw; bw = qw; qw = t; }
        if (ay > by) { t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; t = aw; aw = bw; bw = t; }
        float det = (bx - ax) * (qy - ay) - (qx - ax) * (by - ay);
        if (det == 0 || qy - ay < 1e-6f) return;
        float dwx = ((bw - aw) * (qy - ay) - (qw - aw) * (by - ay)) / det;
        float dwy = ((qw - aw) * (bx - ax) - (bw - aw) * (qx - ax)) / det;
        int y0 = Math.max(0, (int) Math.ceil(ay - .5f)), y1 = Math.min(h, (int) Math.ceil(qy - .5f));
        float sLong = (qx - ax) / (qy - ay);
        float sTop = by > ay ? (bx - ax) / (by - ay) : 0, sBot = qy > by ? (qx - bx) / (qy - by) : 0;
        int cr = col >> 16 & 255, cg = col >> 8 & 255, cb = col & 255;
        for (int y = y0; y < y1; y++) {
            float yc = y + .5f;
            float xa = ax + (yc - ay) * sLong;
            float xb = yc < by ? ax + (yc - ay) * sTop : bx + (yc - by) * sBot;
            if (xa > xb) { t = xa; xa = xb; xb = t; }
            int x0 = Math.max(0, (int) Math.ceil(xa - .5f)), x1 = Math.min(w, (int) Math.ceil(xb - .5f));
            if (x0 >= x1) continue;
            float z = aw + (x0 + .5f - ax) * dwx + (yc - ay) * dwy;
            int i = y * w + x0, end = y * w + x1;
            if (alpha >= 256) {
                for (; i < end; i++, z += dwx)
                    if (z > zb[i]) {
                        zb[i] = z;
                        px[i] = col;
                    }
            } else {
                for (; i < end; i++, z += dwx)
                    if (z > zb[i]) {
                        int d = px[i], dr = d >> 16 & 255, dg = d >> 8 & 255, db = d & 255;
                        px[i] = (dr + ((cr - dr) * alpha >> 8)) << 16 | (dg + ((cg - dg) * alpha >> 8)) << 8
                                | (db + ((cb - db) * alpha >> 8));
                    }
            }
        }
    }

    private final float[] sIn = new float[18], sOut = new float[24];

    /** Like emit, but with a lit colour per corner blended across the face (Gouraud shading). */
    void emitSmooth(float ax, float ay, float az, int ca, float bx, float by, float bz, int cb, float qx, float qy, float qz, int cq, boolean two) {
        if (az >= NEAR && bz >= NEAR && qz >= NEAR) {
            float aw = 1 / az, bw = 1 / bz, qw = 1 / qz;
            float sax = hw + foc * ax * aw, say = hh - foc * ay * aw;
            float sbx = hw + foc * bx * bw, sby = hh - foc * by * bw;
            float sqx = hw + foc * qx * qw, sqy = hh - foc * qy * qw;
            if (!two && (sbx - sax) * (sqy - say) - (sqx - sax) * (sby - say) <= 0) return;
            fillSmooth(sax, say, aw, ca, sbx, sby, bw, cb, sqx, sqy, qw, cq);
            return;
        }
        if (az < NEAR && bz < NEAR && qz < NEAR) return;
        float[] v = sIn, o = sOut;
        int[] cs = {ca, cb, cq};
        float[] xs = {ax, bx, qx}, ys = {ay, by, qy}, zs = {az, bz, qz};
        for (int i = 0; i < 3; i++) {
            v[i * 6] = xs[i];
            v[i * 6 + 1] = ys[i];
            v[i * 6 + 2] = zs[i];
            v[i * 6 + 3] = cs[i] >> 16 & 255;
            v[i * 6 + 4] = cs[i] >> 8 & 255;
            v[i * 6 + 5] = cs[i] & 255;
        }
        int n = 0;
        for (int i = 0; i < 3 && n < 24; i++) {
            int a = i * 6, b = (i + 1) % 3 * 6;
            boolean inA = v[a + 2] >= NEAR, inB = v[b + 2] >= NEAR;
            if (inA) for (int c = 0; c < 6; c++) o[n++] = v[a + c];
            if (inA != inB) {
                float t = (NEAR - v[a + 2]) / (v[b + 2] - v[a + 2]);
                for (int c = 0; c < 6; c++) o[n++] = c == 2 ? NEAR : v[a + c] + (v[b + c] - v[a + c]) * t;
            }
        }
        for (int i = 6; i + 6 < n; i += 6)
            emitSmooth(o[0], o[1], o[2], M.rgb((int) o[3], (int) o[4], (int) o[5]), o[i], o[i + 1], o[i + 2],
                    M.rgb((int) o[i + 3], (int) o[i + 4], (int) o[i + 5]), o[i + 6], o[i + 7], o[i + 8],
                    M.rgb((int) o[i + 9], (int) o[i + 10], (int) o[i + 11]), two);
    }

    private void fillSmooth(float ax, float ay, float aw, int ac, float bx, float by, float bw, int bc, float qx, float qy, float qw, int qc) {
        float t;
        int ti;
        if (ay > by) { t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; t = aw; aw = bw; bw = t; ti = ac; ac = bc; bc = ti; }
        if (by > qy) { t = bx; bx = qx; qx = t; t = by; by = qy; qy = t; t = bw; bw = qw; qw = t; ti = bc; bc = qc; qc = ti; }
        if (ay > by) { t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; t = aw; aw = bw; bw = t; ti = ac; ac = bc; bc = ti; }
        float det = (bx - ax) * (qy - ay) - (qx - ax) * (by - ay);
        if (det == 0 || qy - ay < 1e-6f) return;
        float ux = bx - ax, uy = by - ay, vx = qx - ax, vy = qy - ay, id = 1 / det;
        float dwx = ((bw - aw) * vy - (qw - aw) * uy) * id, dwy = ((qw - aw) * ux - (bw - aw) * vx) * id;
        float ar = ac >> 16 & 255, ag = ac >> 8 & 255, ab = ac & 255;
        float br = (bc >> 16 & 255) - ar, bg = (bc >> 8 & 255) - ag, bb = (bc & 255) - ab;
        float qr = (qc >> 16 & 255) - ar, qg = (qc >> 8 & 255) - ag, qb = (qc & 255) - ab;
        float drx = (br * vy - qr * uy) * id, dry = (qr * ux - br * vx) * id;
        float dgx = (bg * vy - qg * uy) * id, dgy = (qg * ux - bg * vx) * id;
        float dbx = (bb * vy - qb * uy) * id, dby = (qb * ux - bb * vx) * id;
        int y0 = Math.max(0, (int) Math.ceil(ay - .5f)), y1 = Math.min(h, (int) Math.ceil(qy - .5f));
        float sLong = (qx - ax) / (qy - ay);
        float sTop = by > ay ? (bx - ax) / (by - ay) : 0, sBot = qy > by ? (qx - bx) / (qy - by) : 0;
        for (int y = y0; y < y1; y++) {
            float yc = y + .5f;
            float xa = ax + (yc - ay) * sLong;
            float xb = yc < by ? ax + (yc - ay) * sTop : bx + (yc - by) * sBot;
            if (xa > xb) { t = xa; xa = xb; xb = t; }
            int x0 = Math.max(0, (int) Math.ceil(xa - .5f)), x1 = Math.min(w, (int) Math.ceil(xb - .5f));
            if (x0 >= x1) continue;
            float ox = x0 + .5f - ax, oy = yc - ay;
            float z = aw + ox * dwx + oy * dwy, r = ar + ox * drx + oy * dry, g = ag + ox * dgx + oy * dgy, b = ab + ox * dbx + oy * dby;
            for (int i = y * w + x0, end = y * w + x1; i < end; i++, z += dwx, r += drx, g += dgx, b += dbx)
                if (z > zb[i]) {
                    zb[i] = z;
                    int ir = r < 0 ? 0 : r > 255 ? 255 : (int) r, ig = g < 0 ? 0 : g > 255 ? 255 : (int) g, ib = b < 0 ? 0 : b > 255 ? 255 : (int) b;
                    px[i] = ir << 16 | ig << 8 | ib;
                }
        }
    }

    /** Camera-facing square of world size {@code size}; alpha 256 is opaque. Unlit but fogged. */
    void point(float x, float y, float z, float size, int col, int alpha) {
        float dx = x - cx, dy = y - cy, dz = z - cz;
        float vz = dx * fx + dy * fy + dz * fz;
        if (vz < NEAR) return;
        float iw = 1 / vz, sx = hw + foc * (dx * rx + dz * rz) * iw, sy = hh - foc * (dx * ux + dy * uy + dz * uz) * iw;
        float r = Math.max(.5f, foc * size * iw);
        int x0 = Math.max(0, (int) (sx - r)), x1 = Math.min(w, (int) (sx + r) + 1);
        int y0 = Math.max(0, (int) (sy - r)), y1 = Math.min(h, (int) (sy + r) + 1);
        int c = shade(col | Mesh.GLOW, 0, 1, 0, vz), cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255;
        for (int row = y0; row < y1; row++)
            for (int i = row * w + x0, end = row * w + x1; i < end; i++) {
                if (iw <= zb[i]) continue;
                if (alpha >= 256) {
                    px[i] = c;
                    zb[i] = iw;
                } else {
                    int d = px[i], dr = d >> 16 & 255, dg = d >> 8 & 255, db = d & 255;
                    px[i] = (dr + ((cr - dr) * alpha >> 8)) << 16 | (dg + ((cg - dg) * alpha >> 8)) << 8
                            | (db + ((cb - db) * alpha >> 8));
                }
            }
    }
}

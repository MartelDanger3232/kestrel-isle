package kestrel;

import java.util.Arrays;
import java.util.stream.IntStream;

/** Draws the island itself: terrain LODs, scenery, grass, sky, water and weather. */
final class WorldView {
    private static final float CHUNK_M = World.CHUNK * World.CELL;
    private static final int[] FLOWERS = {0xf2d75a, 0xf4f1ea, 0xb98be0};
    static final int CLOUD = 512;
    private final Renderer r;
    private final World w;
    private final long[] order = new long[World.CHUNKS * World.CHUNKS];
    private int visible;
    private final float[] tvx = new float[33 * 33], tvy = new float[33 * 33], tvz = new float[33 * 33];
    final float[] cloud = new float[CLOUD * CLOUD];
    private final Xf xf = new Xf();
    // Per-frame sky constants.
    private float zr, zg, zbl, hr, hg, hb, sunX, sunY, sunZ, sunVis, night, cover, drift, cr, cg, cb;

    WorldView(Renderer r, World w) {
        this.r = r;
        this.w = w;
        Noise cn = new Noise(99);
        IntStream.range(0, CLOUD).parallel().forEach(j -> {
            for (int i = 0; i < CLOUD; i++) {
                float u = i / (float) CLOUD, v = j / (float) CLOUD, s = .018f;
                float f00 = cn.fbm(i * s, j * s, 5), f10 = cn.fbm((i - CLOUD) * s, j * s, 5);
                float f01 = cn.fbm(i * s, (j - CLOUD) * s, 5), f11 = cn.fbm((i - CLOUD) * s, (j - CLOUD) * s, 5);
                float a = f00 + (f10 - f00) * u, b = f01 + (f11 - f01) * u;
                cloud[i + j * CLOUD] = .5f + (a + (b - a) * v) * 1.5f;
            }
        });
    }

    private int lodAt(int ci, int cj) {
        int id = ci + cj * World.CHUNKS;
        float dx = (ci + .5f) * CHUNK_M - r.cx, dz = (cj + .5f) * CHUNK_M - r.cz;
        float dy = (w.cMin[id] + w.cMax[id]) * .5f - r.cy;
        float d = M.sqrt(dx * dx + dz * dz + dy * dy * .25f);
        return d < 110 ? 0 : d < 230 ? 1 : d < 470 ? 2 : d < 950 ? 3 : 4;
    }

    void terrain() {
        int count = 0;
        for (int cj = 0; cj < World.CHUNKS; cj++)
            for (int ci = 0; ci < World.CHUNKS; ci++) {
                int id = ci + cj * World.CHUNKS;
                float x = (ci + .5f) * CHUNK_M, z = (cj + .5f) * CHUNK_M, half = (w.cMax[id] - w.cMin[id]) * .5f;
                float rad = M.sqrt(2 * 32 * 32 + half * half), d = M.dist(x, z, r.cx, r.cz);
                if (d - rad > r.fogEnd || !r.visible(x, w.cMin[id] + half, z, rad)) continue;
                order[count++] = (long) (d * 8) << 20 | id;
            }
        Arrays.sort(order, 0, count);
        visible = count;
        // The graphics card keeps the ground (and the trees and rocks on it) itself; it only needs the chunk order.
        if (r.gpu != null) return;
        for (int i = 0; i < count; i++) {
            int id = (int) (order[i] & 0xfffff);
            drawChunk(id % World.CHUNKS, id / World.CHUNKS);
        }
    }

    private void drawChunk(int ci, int cj) {
        int lod = lodAt(ci, cj);
        World.Chunk c = w.chunk(ci, cj, lod);
        int n = c.n(), nv = n + 1;
        float s = World.CELL * (1 << lod), ox = ci * CHUNK_M, oz = cj * CHUNK_M;
        float[] y = c.y(), nx = c.nx(), ny = c.ny(), nz = c.nz();
        int[] col = c.col();
        for (int j = 0, k = 0; j < nv; j++) {
            float dz = oz + j * s - r.cz;
            for (int i = 0; i < nv; i++, k++) {
                float dx = ox + i * s - r.cx, dy = y[k] - r.cy;
                tvx[k] = dx * r.rx + dz * r.rz;
                tvy[k] = dx * r.ux + dy * r.uy + dz * r.uz;
                tvz[k] = dx * r.fx + dy * r.fy + dz * r.fz;
            }
        }
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = i + j * nv, q = (i + j * n) * 2;
                tri(k, k + nv, k + 1, col[q], nx[q], ny[q], nz[q]);
                tri(k + 1, k + nv, k + nv + 1, col[q + 1], nx[q + 1], ny[q + 1], nz[q + 1]);
            }
        // Skirts hide cracks where neighbouring chunks use a different level of detail.
        if (ci > 0 && lodAt(ci - 1, cj) != lod) skirt(c, lod, 0);
        if (ci < World.CHUNKS - 1 && lodAt(ci + 1, cj) != lod) skirt(c, lod, 1);
        if (cj > 0 && lodAt(ci, cj - 1) != lod) skirt(c, lod, 2);
        if (cj < World.CHUNKS - 1 && lodAt(ci, cj + 1) != lod) skirt(c, lod, 3);
    }

    private void tri(int a, int b, int e, int col, float nx, float ny, float nz) {
        float az = tvz[a], bz = tvz[b], ez = tvz[e];
        if (az < Renderer.NEAR && bz < Renderer.NEAR && ez < Renderer.NEAR) return;
        r.emit(tvx[a], tvy[a], az, tvx[b], tvy[b], bz, tvx[e], tvy[e], ez,
                r.shade(col, nx, ny, nz, (az + bz + ez) * .333f), false, 256);
    }

    private void skirt(World.Chunk c, int lod, int edge) {
        int n = c.n(), nv = n + 1;
        float drop = 3f * (1 << lod) + 2, dy = drop * r.uy, dz = drop * r.fy;
        for (int t = 0; t < n; t++) {
            int k0, k1, q;
            switch (edge) {
                case 0 -> { k0 = t * nv; k1 = k0 + nv; q = t * n * 2; }
                case 1 -> { k0 = n + t * nv; k1 = k0 + nv; q = (n - 1 + t * n) * 2; }
                case 2 -> { k0 = t; k1 = t + 1; q = t * 2; }
                default -> { k0 = t + n * nv; k1 = k0 + 1; q = (t + (n - 1) * n) * 2; }
            }
            float x0 = tvx[k0], y0 = tvy[k0], z0 = tvz[k0], x1 = tvx[k1], y1 = tvy[k1], z1 = tvz[k1];
            int col = r.shade(c.col()[q], c.nx()[q], c.ny()[q], c.nz()[q], (z0 + z1) * .5f);
            r.emit(x0, y0, z0, x1, y1, z1, x1, y1 - dy, z1 - dz, col, true, 256);
            r.emit(x0, y0, z0, x1, y1 - dy, z1 - dz, x0, y0 - dy, z0 - dz, col, true, 256);
        }
    }

    /** Trees and rocks, nearest chunks first (reuses the terrain pass's sorted chunk list). */
    void props() {
        for (int i = 0; i < visible; i++) {
            if ((order[i] >>> 20) / 8f > 760) break;
            for (Places.Prop p : w.places.props[(int) (order[i] & 0xfffff)]) {
                if (p.type() == Places.COLLIDER) continue;
                float d = M.dist(p.x(), p.z(), r.cx, r.cz);
                if (d > 720 || !r.visible(p.x(), p.y() + 4 * p.scale(), p.z(), 7 * p.scale())) continue;
                xf.identity().translate(p.x(), p.y(), p.z()).rotY(p.yaw()).scale(p.scale());
                r.mesh(d < 150 ? Models.PROP_NEAR[p.type()] : Models.PROP_FAR[p.type()], xf);
            }
        }
    }

    void pickups(float time) {
        for (int i = 0; i < visible; i++) {
            if ((order[i] >>> 20) / 8f > 140) break;
            for (Places.Pickup p : w.places.pickups[(int) (order[i] & 0xfffff)]) {
                if (p.taken || M.dist(p.x, p.z, r.cx, r.cz) > 90) continue;
                boolean lost = p.item >= Places.KITE;
                xf.identity().translate(p.x, p.y + .05f, p.z).rotY(lost ? M.sin(time * 1.3f + p.x) * .5f + p.x : time * .6f + p.x);
                r.mesh(switch (p.item) {
                    case Places.KITE -> Models.KITE;
                    case Places.ROD -> Models.ROD;
                    case Places.HEART -> Models.HEART;
                    case Places.ARROWS -> Models.ARROW_BUNDLE;
                    case Places.BOMBS -> Models.BOMB_BAG;
                    case Places.PIECE -> Models.HEART_PIECE;
                    default -> Models.GLEAM[p.item];
                }, xf);
            }
        }
    }

    void grass(float time) {
        boolean gpu = r.gpu != null;
        int gi = (int) (r.cx / World.CELL), gj = (int) (r.cz / World.CELL), R = gpu ? 22 : 13;
        if (r.cy - w.height(r.cx, r.cz) > 45) return;
        float lit = Math.max(0, r.ly) * .85f, kr = r.ar + r.sr * lit, kg = r.ag + r.sg * lit, kb = r.ab + r.sb * lit;
        for (int j = gj - R; j <= gj + R; j++)
            for (int i = gi - R; i <= gi + R; i++) {
                if (i < 1 || j < 1 || i >= World.SIZE || j >= World.SIZE) continue;
                int k = i + j * World.V;
                byte b = w.biome[k];
                float y0 = w.h[k];
                if (b != World.MEADOW && b != World.FOREST || y0 < 1.6f) continue;
                if (Math.abs(w.h[k + 1] - y0) + Math.abs(w.h[k + World.V] - y0) > 1.3f) continue;
                float bx = i * World.CELL, bz = j * World.CELL, dx = bx - r.cx, dz = bz - r.cz, d2 = dx * dx + dz * dz;
                if (d2 > R * R * 4) continue;
                float vz = dx * r.fx + (y0 - r.cy) * r.fy + dz * r.fz;
                if (vz < -2 || Math.abs(dx * r.rx + dz * r.rz) > vz * 1.9f + 3) continue;
                for (int t = 0, blades = gpu ? (d2 < 300 ? 9 : 4) : d2 < 200 ? 4 : 2; t < blades; t++) {
                    float hx = M.hash(i * 7 + t, j), hz = M.hash(i, j * 5 + t), hv = M.hash(i + t * 31, j + 17);
                    float x = bx + hx * 2, z = bz + hz * 2, y = w.height(x, z);
                    float tall = (b == World.FOREST ? .4f : .3f) + hv * .35f;
                    float sway = M.sin(time * 1.7f + x * .3f + z * .2f) * .16f + .08f;
                    float ang = hv * 6.28f, cw = M.cos(ang) * .13f, sw = M.sin(ang) * .13f;
                    int base = hv < .05f ? FLOWERS[(int) (hx * 2.99f)] : b == World.FOREST ? 0x3a7032 : 0x5f9a42;
                    float v = .75f + hz * .35f;
                    if (gpu) {
                        // At full resolution blades need to be slimmer (and more of them) to read as grass.
                        r.gpu.blade(x - cw * .4f, y, z - sw * .4f, x + cw * .4f, y, z + sw * .4f, x + sway, y + tall, z + sway * .6f, M.scale(base, v));
                        continue;
                    }
                    int col = M.rgb((int) ((base >> 16 & 255) * kr * v), (int) ((base >> 8 & 255) * kg * v), (int) ((base & 255) * kb * v));
                    r.worldTri(x - cw, y, z - sw, x + cw, y, z + sw, x + sway, y + tall, z + sway * .6f, col, true, 256);
                }
            }
    }

    private static float wave(float x, float z, float t, int n) {
        return n < 8 ? 0 : (M.sin(x * .35f + t * 1.3f) + M.sin(z * .29f - t * 1.1f + x * .1f)) * .09f;
    }

    void water(float time) {
        int cci = (int) Math.floor(r.cx / CHUNK_M), ccj = (int) Math.floor(r.cz / CHUNK_M), rad = (int) (r.fogEnd / CHUNK_M) + 1;
        for (int cj = Math.max(-12, ccj - rad); cj <= Math.min(World.CHUNKS + 11, ccj + rad); cj++)
            for (int ci = Math.max(-12, cci - rad); ci <= Math.min(World.CHUNKS + 11, cci + rad); ci++) {
                boolean inside = ci >= 0 && cj >= 0 && ci < World.CHUNKS && cj < World.CHUNKS;
                if (inside && w.cMin[ci + cj * World.CHUNKS] > World.WATER + .5f) continue;
                float x0 = ci * CHUNK_M, z0 = cj * CHUNK_M, d = M.dist(x0 + 32, z0 + 32, r.cx, r.cz);
                if (d > r.fogEnd + 46 || !r.visible(x0 + 32, World.WATER, z0 + 32, 46)) continue;
                int n = d < 110 ? 16 : d < 240 ? 8 : d < 520 ? 2 : 1;
                float s = CHUNK_M / n;
                for (int j = 0; j < n; j++)
                    for (int i = 0; i < n; i++) {
                        float ax = x0 + i * s, az = z0 + j * s, bx = ax + s, bz = az + s;
                        float y00 = wave(ax, az, time, n), y10 = wave(bx, az, time, n);
                        float y01 = wave(ax, bz, time, n), y11 = wave(bx, bz, time, n);
                        waterTri(ax, y00, az, ax, y01, bz, bx, y10, az, inside);
                        waterTri(bx, y10, az, ax, y01, bz, bx, y11, bz, inside);
                    }
            }
    }

    private void waterTri(float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz, boolean inside) {
        float mx = (ax + bx + qx) / 3, my = (ay + by + qy) / 3, mz = (az + bz + qz) / 3;
        float depth = inside ? World.WATER - w.height(mx, mz) : 30;
        if (depth < -.4f) return;
        float ux = bx - ax, uy = by - ay, uz = bz - az, vx = qx - ax, vy = qy - ay, vz = qz - az;
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, l = M.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= l; ny /= l; nz /= l;
        float ex = r.cx - mx, ey = r.cy - my, ez = r.cz - mz, el = M.sqrt(ex * ex + ey * ey + ez * ez);
        ex /= el; ey /= el; ez /= el;
        float fres = 1 - Math.max(0, ex * nx + ey * ny + ez * nz);
        fres *= fres * fres;
        float diff = Math.max(0, nx * r.lx + ny * r.ly + nz * r.lz);
        int deep = M.mix(0x2f9aa0, 0x184a78, M.clamp(depth / 10, 0, 1));
        float cr = (deep >> 16 & 255) * (r.ar + r.sr * diff), cg = (deep >> 8 & 255) * (r.ag + r.sg * diff);
        float cb = (deep & 255) * (r.ab + r.sb * diff);
        cr += ((r.fogCol >> 16 & 255) - cr) * fres * .8f;
        cg += ((r.fogCol >> 8 & 255) - cg) * fres * .8f;
        cb += ((r.fogCol & 255) - cb) * fres * .8f;
        float hx = ex + r.lx, hy = ey + r.ly, hz = ez + r.lz, hl = M.sqrt(hx * hx + hy * hy + hz * hz);
        float spec = Math.max(0, (hx * nx + hy * ny + hz * nz) / hl);
        for (int i = 0; i < 6; i++) spec *= spec;
        spec *= 400;
        cr += spec * r.sr; cg += spec * r.sg; cb += spec * r.sb;
        int col = M.rgb((int) cr, (int) cg, (int) cb);
        if (depth < .5f) col = M.mix(col, 0xe8f4f4, .35f);
        col = r.shade(col | Mesh.GLOW, 0, 1, 0, r.depthOf(mx, my, mz));
        r.worldTri(ax, ay, az, bx, by, bz, qx, qy, qz, col, false, inside ? (int) M.clamp(130 + depth * 14, 130, 236) : 256);
    }

    /** Fills every pixel nothing was drawn to with sky, sun, moon, stars and clouds. */
    void sky(Climate cl, float time) {
        zr = cl.zen >> 16 & 255; zg = cl.zen >> 8 & 255; zbl = cl.zen & 255;
        hr = r.fogCol >> 16 & 255; hg = r.fogCol >> 8 & 255; hb = r.fogCol & 255;
        sunX = cl.sunX; sunY = cl.sunY; sunZ = cl.sunZ;
        sunVis = M.clamp(sunY * 6 + .3f, 0, 1) * (1 - cl.overcast * .85f);
        night = M.clamp(-sunY * 4, 0, 1) * (1 - cl.overcast);
        cover = cl.cloudCover;
        drift = time * 3.5f;
        int cc = cl.cloudCol;
        cr = cc >> 16 & 255; cg = cc >> 8 & 255; cb = cc & 255;
        for (int y = 0; y < r.h; y++) {
            float py = (r.hh - (y + .5f)) / r.foc;
            float bx = r.fx + r.ux * py, by = r.fy + r.uy * py, bz = r.fz + r.uz * py;
            for (int x = 0, i = y * r.w; x < r.w; x++, i++) {
                if (r.zb[i] != 0) continue;
                float pv = (x + .5f - r.hw) / r.foc, dx = bx + r.rx * pv, dz = bz + r.rz * pv;
                float il = 1 / M.sqrt(dx * dx + by * by + dz * dz);
                r.px[i] = skyAt(dx * il, by * il, dz * il);
            }
        }
    }

    private int skyAt(float dx, float dy, float dz) {
        float t = dy <= 0 ? 0 : M.sqrt(dy);
        float cr0 = hr + (zr - hr) * t, cg0 = hg + (zg - hg) * t, cb0 = hb + (zbl - hb) * t;
        float sd = dx * sunX + dy * sunY + dz * sunZ;
        if (sd > 0 && sunVis > 0) {
            float g = sd * sd;
            g *= g; g *= g; g *= g; g *= g;
            float k = (sd > .9994f ? 1 : g * .45f) * sunVis;
            cr0 += (255 - cr0) * k; cg0 += (244 - cg0) * k; cb0 += (214 - cb0) * k;
        } else if (sd < -.9993f && night > 0) {
            cr0 += (232 - cr0) * night; cg0 += (236 - cg0) * night; cb0 += (250 - cb0) * night;
        }
        if (night > 0 && dy > 0 && M.hash((int) (dx * 170), (int) (dy * 170) * 977 + (int) (dz * 170)) < .006f) {
            float k = night * (.5f + M.hash((int) (dz * 99), (int) (dx * 99)) * .5f);
            cr0 += (255 - cr0) * k; cg0 += (255 - cg0) * k; cb0 += (255 - cb0) * k;
        }
        if (dy > .02f && cover > 0) {
            float k = 1 / dy;
            int u = (int) (dx * k * 90 + drift) & (CLOUD - 1), v = (int) (dz * k * 90 + drift * .4f) & (CLOUD - 1);
            float a = M.smooth(1 - cover, 1.3f - cover, cloud[u + v * CLOUD]) * Math.min(1, (dy - .02f) * 7) * .92f;
            cr0 += (cr - cr0) * a; cg0 += (cg - cg0) * a; cb0 += (cb - cb0) * a;
        }
        return M.rgb((int) cr0, (int) cg0, (int) cb0);
    }

    /** Screen-space rain streaks or snowflakes. */
    void weather(float amount, boolean snow, float time) {
        int drops = (int) (amount * (snow ? 300 : 460));
        int seed = (int) (time * 60) * 7919;
        for (int i = 0; i < drops; i++) {
            if (snow) {
                float x = (M.hash(i, 3) * r.w + time * (8 + M.hash(i, 5) * 14) + M.sin(time + i) * 6) % r.w;
                float y = (M.hash(i, 9) * r.h + time * (22 + M.hash(i, 7) * 20)) % r.h;
                blendPx((int) x, (int) y, 0xffffff, 200);
                blendPx((int) x + 1, (int) y, 0xffffff, 140);
            } else {
                int x = (int) (M.hash(i, seed) * r.w), y = (int) (M.hash(seed, i) * r.h), len = 5 + (i & 7);
                for (int k = 0; k < len; k++) blendPx(x - k / 3, y + k, 0xc8d4e4, 90);
            }
        }
    }

    private void blendPx(int x, int y, int col, int a) {
        if (x < 0 || y < 0 || x >= r.w || y >= r.h) return;
        int i = x + y * r.w, d = r.px[i];
        r.px[i] = M.mix(d, col, a / 256f);
    }

    /** Soft round blob shadow on the ground under something at height y. */
    void shadow(float x, float z, float rad, float y) {
        float g = w.height(x, z);
        int a = (int) (110 * M.clamp(1 - (y - g) / 30, 0, 1));
        if (a < 8 || M.dist(x, z, r.cx, r.cz) > 70) return;
        float px = x + rad, pz = z, py = w.height(px, pz) + .1f;
        for (int i = 1; i <= 8; i++) {
            float an = i * M.TAU / 8, qx = x + M.cos(an) * rad, qz = z + M.sin(an) * rad, qy = w.height(qx, qz) + .1f;
            r.worldTri(x, g + .1f, z, qx, qy, qz, px, py, pz, 0x101418, true, a);
            px = qx; pz = qz; py = qy;
        }
    }
}

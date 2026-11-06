package kestrel;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.stream.IntStream;

/** The island: heightfield, biomes, terrain LOD chunks and the fog-of-war map. */
final class World {
    static final int SIZE = 1024, CHUNK = 32, CHUNKS = SIZE / CHUNK, MAP = 256, LODS = 5, V = SIZE + 1;
    static final float CELL = 2f, WATER = 0f, EXTENT = SIZE * CELL, MID = EXTENT / 2, CRATER_Y = 172f;
    static final byte MEADOW = 0, FOREST = 1, DESERT = 2, SNOW = 3, ASH = 4, SPIRE = 5, PATH = 6;

    /** One terrain chunk at one level of detail: corner heights plus baked per-triangle normals and colours. */
    record Chunk(int n, float[] y, float[] nx, float[] ny, float[] nz, int[] col) {}

    final Noise n;
    final float[] h = new float[V * V];
    final byte[] biome = new byte[V * V];
    final float[] cMin = new float[CHUNKS * CHUNKS], cMax = new float[CHUNKS * CHUNKS];
    private final Chunk[][] lods = new Chunk[LODS][CHUNKS * CHUNKS];
    final BufferedImage mapImage = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_ARGB);
    private final int[] mapPx, mapBase = new int[MAP * MAP];
    final boolean[] revealed = new boolean[MAP * MAP];
    final Places places;

    World(long seed) {
        n = new Noise(seed);
        IntStream.range(0, V).parallel().forEach(j -> {
            for (int i = 0; i < V; i++) {
                h[i + j * V] = raw(i * CELL, j * CELL);
                biome[i + j * V] = classify(i * CELL, j * CELL);
            }
        });
        mapPx = ((DataBufferInt) mapImage.getRaster().getDataBuffer()).getData();
        places = new Places(this, seed);
        for (int c = 0; c < CHUNKS * CHUNKS; c++) {
            int ci = c % CHUNKS, cj = c / CHUNKS;
            float lo = 1e9f, hi = -1e9f;
            for (int j = 0; j <= CHUNK; j++)
                for (int i = 0; i <= CHUNK; i++) {
                    float v = h[ci * CHUNK + i + (cj * CHUNK + j) * V];
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
            cMin[c] = lo;
            cMax[c] = hi;
        }
        buildMap();
    }

    private float raw(float x, float z) {
        float r = M.dist(x, z, MID, MID);
        float warp = n.fbm(x * .0017f + 11, z * .0017f + 23, 4);
        float land = M.smooth(.98f, .74f, r / MID + warp * .35f);
        float detail = n.fbm(x * .03f, z * .03f, 3);
        float hgt = 16 + n.fbm(x * .0045f + 5, z * .0045f - 3, 5) * 46;

        float north = M.smooth(780, 220, M.dist(x, z, MID, 1730));
        hgt += north * (25 + n.ridged(x * .0052f + 31, z * .0052f + 77, 5) * 240);
        float east = M.smooth(430, 140, M.dist(x, z, 1660, 1200));
        hgt += east * (15 + n.ridged(x * .007f + 3, z * .007f + 5, 4) * 120);

        float desert = M.smooth(560, 300, M.dist(x, z, 1560, 540));
        if (desert > 0) {
            float dunes = M.sin(x * .035f + z * .018f + warp * 9) * 4 + M.sin(x * .011f - z * .021f + warp * 5) * 5
                    + n.fbm(x * .01f, z * .01f, 3) * 10;
            float mesa = M.smooth(.17f, .21f, n.fbm(x * .008f + 60, z * .008f + 10, 2)) * 44;
            hgt = M.lerp(hgt, 14 + dunes + mesa, desert);
        }

        // The volcano, with a flat arena in its crater.
        float cone = 1 - r / 470;
        if (cone > 0) hgt = Math.max(hgt, 310 * (float) Math.pow(cone, 1.55) + detail * 4);
        if (r < 90) hgt = M.lerp(CRATER_Y + detail * 1.5f, hgt, M.smooth(58, 84, r));

        // Terraced cliffs in the green lowlands give the climbing something to do.
        float tm = M.smooth(.05f, .25f, n.fbm(x * .003f + 40, z * .003f + 90, 2)) * (1 - desert) * M.smooth(470, 560, r);
        if (tm > 0 && hgt > 8) hgt = M.lerp(hgt, terrace(hgt, 18), tm);

        // Rivers and lakes are cut down below sea level.
        float wild = (1 - desert * .8f) * M.smooth(420, 520, r) * (1 - north * .9f);
        float river = M.smooth(.045f, .016f, Math.abs(n.fbm(x * .0024f + 70, z * .0024f - 20, 3))) * M.smooth(110, 50, hgt);
        float lake = M.smooth(-.2f, -.32f, n.fbm(x * .0035f + 3, z * .0035f + 41, 2));
        hgt = M.lerp(hgt, -4, Math.max(river, lake) * wild);

        return M.lerp(-26 + detail * 6, hgt + detail * 1.6f, land);
    }

    private static float terrace(float h, float step) {
        float f = h / step, k = (float) Math.floor(f);
        return (k + M.smooth(.42f, .58f, f - k)) * step;
    }

    private byte classify(float x, float z) {
        float wx = x + n.fbm(x * .004f + 9, z * .004f, 3) * 160, wz = z + n.fbm(x * .004f, z * .004f + 9, 3) * 160;
        if (M.dist(wx, wz, MID, MID) < 330) return ASH;
        if (wz > 1430) return SNOW;
        if (M.dist(wx, wz, 1560, 540) < 450) return DESERT;
        if (wx < 800 && wz > 640) return FOREST;
        return MEADOW;
    }

    /** Exact height of the full-detail terrain surface. */
    float height(float x, float z) {
        float fx = M.clamp(x / CELL, 0, SIZE - .001f), fz = M.clamp(z / CELL, 0, SIZE - .001f);
        int i = (int) fx, j = (int) fz, k = i + j * V;
        float u = fx - i, v = fz - j;
        float a = h[k], b = h[k + 1], c = h[k + V], d = h[k + V + 1];
        return u + v < 1 ? a + (b - a) * u + (c - a) * v : d + (c - d) * (1 - u) + (b - d) * (1 - v);
    }

    float gradX(float x, float z) { return (height(x + 1.5f, z) - height(x - 1.5f, z)) / 3; }

    float gradZ(float x, float z) { return (height(x, z + 1.5f) - height(x, z - 1.5f)) / 3; }

    /** Unsmoothed slope right underfoot, for movement speed on walls. */
    float localSlope(float x, float z) {
        return M.hypot(height(x + .5f, z) - height(x - .5f, z), height(x, z + .5f) - height(x, z - .5f));
    }

    /** Rise over run; 1 is 45 degrees. */
    float slope(float x, float z) { return M.hypot(gradX(x, z), gradZ(x, z)); }

    int biomeAt(float x, float z) {
        int i = (int) M.clamp(Math.round(x / CELL), 0, SIZE), j = (int) M.clamp(Math.round(z / CELL), 0, SIZE);
        return biome[i + j * V];
    }

    Chunk chunk(int ci, int cj, int lod) {
        int id = ci + cj * CHUNKS;
        Chunk c = lods[lod][id];
        if (c == null) lods[lod][id] = c = buildChunk(ci, cj, lod);
        return c;
    }

    private Chunk buildChunk(int ci, int cj, int lod) {
        int step = 1 << lod, cells = CHUNK >> lod, nv = cells + 1;
        float s = step * CELL, ox = ci * CHUNK * CELL, oz = cj * CHUNK * CELL;
        float[] y = new float[nv * nv];
        for (int j = 0; j < nv; j++)
            for (int i = 0; i < nv; i++) y[i + j * nv] = h[ci * CHUNK + i * step + (cj * CHUNK + j * step) * V];
        int t = 2 * cells * cells;
        float[] nx = new float[t], ny = new float[t], nz = new float[t];
        int[] col = new int[t];
        for (int j = 0; j < cells; j++)
            for (int i = 0; i < cells; i++) {
                int k = i + j * nv, q = (i + j * cells) * 2;
                float a = y[k], b = y[k + nv], c = y[k + 1], d = y[k + nv + 1]; // b is north, c is east
                normal(nx, ny, nz, q, a - c, s, a - b);
                normal(nx, ny, nz, q + 1, b - d, s, c - d);
                float x0 = ox + i * s, z0 = oz + j * s;
                col[q] = colorAt(x0 + s * .33f, z0 + s * .33f, (a + b + c) / 3, ny[q]);
                col[q + 1] = colorAt(x0 + s * .67f, z0 + s * .67f, (b + c + d) / 3, ny[q + 1]);
            }
        return new Chunk(cells, y, nx, ny, nz, col);
    }

    private static void normal(float[] nx, float[] ny, float[] nz, int q, float x, float y, float z) {
        float l = M.sqrt(x * x + y * y + z * z);
        nx[q] = x / l;
        ny[q] = y / l;
        nz[q] = z / l;
    }

    /** Ground colour for a patch of terrain; ny is the up component of its normal. */
    int colorAt(float x, float z, float y, float ny) {
        int b = biomeAt(x, z);
        float v = n.get(x * .11f, z * .11f) * .6f + n.get(x * .45f, z * .45f) * .4f;
        boolean steep = ny < .72f;
        int col;
        if (b == PATH && y > WATER - .5f) return M.scale(M.mix(0xa58c62, 0x8f7a56, M.clamp(v + .5f, 0, 1)), ny < .8f ? .85f : 1);
        if (b == SPIRE) col = M.mix(0x5f667a, 0x8d93a3, (M.sin(y * .9f) + 1) * .5f);
        else if (y < WATER - 1.5f) col = M.mix(0xc8b27a, 0x5d6e66, M.clamp(-y / 14, 0, 1));
        else if (b == ASH) {
            if (y > 50 && n.ridged(x * .02f, z * .02f, 2) > .92f
                    || y < CRATER_Y + 2 && M.dist(x, z, MID, MID) < 62 && v > .28f) return 0xff6a1e | Mesh.GLOW;
            col = steep ? 0x463c39 : 0x5b4f49;
        } else if (steep) col = b == DESERT ? 0xb0674a : b == SNOW ? 0x8c93a0 : 0x8a8172;
        else if (y > 205 + v * 25 || b == SNOW && y > 40 + v * 30) col = 0xeef2f8;
        else if (b == SNOW) col = 0x8fa77a;
        else if (y < 1.8f + v * 1.5f || b == DESERT) col = 0xdcc08a;
        else if (b == FOREST) col = M.mix(0x3f7a3a, 0x58893c, M.clamp(v + .5f, 0, 1));
        else col = M.mix(0x5c9842, 0x8fa34a, M.clamp(v + .45f, 0, 1));
        return M.scale(col, 1 + v * .2f);
    }

    private void buildMap() {
        float px = EXTENT / MAP;
        for (int j = 0; j < MAP; j++)
            for (int i = 0; i < MAP; i++) {
                float x = (i + .5f) * px, z = EXTENT - (j + .5f) * px, y = height(x, z);
                float gx = gradX(x, z), gz = gradZ(x, z);
                int c;
                if (y < WATER) c = M.mix(0x3c86b0, 0x1d3f63, M.clamp(-y / 20, 0, 1));
                else c = M.scale(colorAt(x, z, y, 1 / M.sqrt(1 + gx * gx + gz * gz)) & 0xffffff,
                        M.clamp(1 + (gz - gx) * .45f, .55f, 1.35f));
                mapBase[i + j * MAP] = 0xff000000 | c;
                mapPx[i + j * MAP] = y < WATER ? 0xff2a3442 : 0xff3a3f48;
            }
    }

    /** Uncovers the map around a point. */
    void reveal(float x, float z, float radius) {
        int cx = (int) (x / EXTENT * MAP), cz = (int) ((1 - z / EXTENT) * MAP), r = (int) (radius / EXTENT * MAP);
        for (int j = -r; j <= r; j++)
            for (int i = -r; i <= r; i++) {
                int u = cx + i, v = cz + j;
                if (i * i + j * j > r * r || u < 0 || v < 0 || u >= MAP || v >= MAP) continue;
                int k = u + v * MAP;
                if (!revealed[k]) {
                    revealed[k] = true;
                    mapPx[k] = mapBase[k];
                }
            }
    }

    boolean isRevealed(float x, float z) {
        int u = (int) M.clamp(x / EXTENT * MAP, 0, MAP - 1), v = (int) M.clamp((1 - z / EXTENT) * MAP, 0, MAP - 1);
        return revealed[u + v * MAP];
    }
}

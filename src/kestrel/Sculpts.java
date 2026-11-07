package kestrel;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Detailed humanoid body parts sculpted in Blender (art/blender/humanoid.py). They're kept by material zone rather
 * than colour, so every character paints the same body in its own skin, hair and clothing colours.
 */
final class Sculpts {
    /** The zones a sculpt is painted by, numbered as the Blender script numbers them. */
    static final int SKIN = 0, SKIN_SHADE = 1, HAIR = 2, HAIR_SHADE = 3, TOP = 4, TOP_SHADE = 5, BOTTOM = 6, BOOTS = 7,
            BOOTS_SHADE = 8, LEATHER = 9, METAL = 10, PUPIL = 11, EYE_WHITE = 12, MOUTH = 13;
    /** Set before the first character is built; only the graphics card draws the sculpted bodies. */
    static boolean enabled;
    private static Map<String, Mesh> parts;

    private Sculpts() {}

    /** The sculpted part painted from palette (one colour per zone), or null when there's no sculpt to use. */
    static Mesh part(String name, int[] palette) {
        Mesh shape = parts().get(name);
        if (shape == null) return null;
        Mesh m = new Mesh();
        m.tris = shape.tris;
        m.p = Arrays.copyOf(shape.p, shape.tris * 9);
        m.n = Arrays.copyOf(shape.n, shape.tris * 3);
        m.vn = Arrays.copyOf(shape.vn, shape.tris * 9);
        m.smooth = true;
        m.c = new int[shape.tris];
        for (int i = 0; i < shape.tris; i++) m.c[i] = palette[shape.c[i]];
        return m;
    }

    /** Reads Models/humanoid.kscl once: named parts of triangles, each tagged with its zone. */
    private static Map<String, Mesh> parts() {
        if (parts != null) return parts;
        parts = new HashMap<>();
        if (!enabled) return parts;
        try (InputStream in = Sculpts.class.getResourceAsStream("/Models/humanoid.kscl")) {
            if (in == null) return parts;
            DataInputStream d = new DataInputStream(new BufferedInputStream(in));
            if (d.readInt() != 0x4B53434C || d.readInt() != 1) return parts;
            for (int k = d.readInt(); k > 0; k--) {
                String name = d.readUTF();
                Mesh m = new Mesh();
                float[] v = new float[9];
                for (int t = d.readInt(); t > 0; t--) {
                    for (int i = 0; i < 9; i++) v[i] = d.readFloat();
                    m.tri(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], d.readByte());
                }
                parts.put(name, m.smooth());
            }
        } catch (IOException e) {
            System.err.println("Couldn't read the sculpted characters: " + e.getMessage());
        }
        return parts;
    }
}

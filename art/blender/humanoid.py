"""Sculpts Kestrel Isle's humanoid body in Blender and writes it for the game.

    blender -b --factory-startup -P art/blender/humanoid.py -- res/Models/humanoid.kscl

Each part is built in the game's own coordinates (x right, y up, z forward, in metres, around the joint the part hangs
from in Characters.humanoid) out of rounded pieces, smoothed with subdivision, and saved as triangles tagged with a
material zone (skin, hair, tunic and so on) so the game can paint the one body in every character's colours.
"""
import math
import struct
import sys

import bpy

# Material zones, numbered as in Sculpts.java.
SKIN, SKIN_SHADE, HAIR, HAIR_SHADE, TOP, TOP_SHADE, BOTTOM, BOOTS, BOOTS_SHADE, LEATHER, METAL, PUPIL, EYE_WHITE, MOUTH = range(14)

parts = {}


def smoothstep(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def ellipsoid(c, r, segs=20, rings=12, shape=None):
    """A closed ellipsoid around c with radii r; shape(p) may reshape each point. Faces wind outward."""
    pts = [(0.0, -1.0, 0.0)]
    for i in range(1, rings):
        lat = math.pi * i / rings - math.pi / 2
        for j in range(segs):
            lon = 2 * math.pi * j / segs
            pts.append((math.cos(lat) * math.sin(lon), math.sin(lat), math.cos(lat) * math.cos(lon)))
    pts.append((0.0, 1.0, 0.0))
    verts = [[c[0] + x * r[0], c[1] + y * r[1], c[2] + z * r[2]] for x, y, z in pts]
    if shape:
        verts = [shape(v) for v in verts]
    faces, top = [], len(pts) - 1
    for j in range(segs):
        faces.append([0, 1 + (j + 1) % segs, 1 + j])
    for i in range(rings - 2):
        a, b = 1 + i * segs, 1 + (i + 1) * segs
        for j in range(segs):
            k = (j + 1) % segs
            faces.append([a + j, a + k, b + k, b + j])
    a = 1 + (rings - 2) * segs
    for j in range(segs):
        faces.append([a + j, a + (j + 1) % segs, top])
    return verts, faces


def lathe(profile, segs=20, fold=None, shape=None):
    """A capped tube around the y axis: profile lists (y, x radius, z radius) from the bottom up; fold(angle, y)
    scales the radius for creases and hems. Faces wind outward."""
    verts = []
    for y, rx, rz in profile:
        for j in range(segs):
            a = 2 * math.pi * j / segs
            w = fold(a, y) if fold else 1.0
            verts.append([math.sin(a) * rx * w, y, math.cos(a) * rz * w])
    n, faces = len(profile), []
    for i in range(n - 1):
        for j in range(segs):
            k = (j + 1) % segs
            faces.append([i * segs + j, i * segs + k, (i + 1) * segs + k, (i + 1) * segs + j])
    verts.append([0.0, profile[0][0], 0.0])
    for j in range(segs):
        faces.append([len(verts) - 1, (j + 1) % segs, j])
    verts.append([0.0, profile[-1][0], 0.0])
    base = (n - 1) * segs
    for j in range(segs):
        faces.append([base + j, base + (j + 1) % segs, len(verts) - 1])
    if shape:
        verts = [shape(v) for v in verts]
    return verts, faces


def turned(piece, about, ax=0.0, ay=0.0):
    """The piece turned ax radians about x, then ay about y, around the point about."""
    verts, faces = piece
    out = []
    for x, y, z in verts:
        x, y, z = x - about[0], y - about[1], z - about[2]
        y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
        x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
        out.append([x + about[0], y + about[1], z + about[2]])
    return out, faces


def without(piece, cut):
    """The piece with every face whose middle satisfies cut(x, y, z) removed (leaving an open shell)."""
    verts, faces = piece
    keep = []
    for f in faces:
        m = [sum(verts[i][k] for i in f) / len(f) for k in range(3)]
        if not cut(*m):
            keep.append(f)
    return verts, keep


def add(part, zone, piece, smooth=1):
    """Adds a piece to a body part; smooth is how many times subdivision rounds it off."""
    verts, faces = piece
    me = bpy.data.meshes.new(part)
    # Game (x right, y up, z forward) to Blender (x right, z up, -y forward): a turn, so faces still wind outward.
    me.from_pydata([(x, -z, y) for x, y, z in verts], [], faces)
    me.validate()
    ob = bpy.data.objects.new(part, me)
    bpy.context.scene.collection.objects.link(ob)
    if smooth:
        mod = ob.modifiers.new("round", 'SUBSURF')
        mod.levels = mod.render_levels = smooth
        # Rounding multiplies the faces by four; thinning them back keeps the shape at a third of the cost.
        ob.modifiers.new("lighter", 'DECIMATE').ratio = .35
    ob["zone"] = zone
    parts.setdefault(part, []).append(ob)


def head():
    def skull(p):
        x, y, z = p
        jaw = smoothstep(.13, .035, y)
        x *= 1 - .2 * jaw
        z = z * (1 - .08 * jaw) + .01 * jaw if z > 0 else z * .94
        return [x, y, z]

    add("head", SKIN, ellipsoid((0, .145, .004), (.112, .138, .124), 22, 14, skull))
    for s in (-1, 1):
        add("head", SKIN_SHADE, ellipsoid((s * .111, .14, -.006), (.016, .031, .023), 10, 8))
        add("head", EYE_WHITE, ellipsoid((s * .041, .152, .1), (.024, .019, .02), 14, 10), 0)
        add("head", PUPIL, ellipsoid((s * .04, .151, .117), (.012, .013, .006), 12, 8), 0)
        add("head", EYE_WHITE, ellipsoid((s * .035, .157, .123), (.004, .004, .002), 6, 4), 0)
        add("head", HAIR_SHADE, turned(ellipsoid((s * .044, .184, .112), (.027, .0065, .009), 12, 6), (s * .044, .184, .112), 0, -s * .12), 0)
    add("head", SKIN_SHADE, ellipsoid((0, .124, .124), (.016, .026, .02), 12, 8))
    add("head", MOUTH, ellipsoid((0, .083, .112), (.023, .0055, .008), 12, 6), 0)
    # Hair: a cap opened for the face and ears, a fuller back, and a fringe of tufts over the forehead.
    cap = ellipsoid((0, .19, -.018), (.128, .118, .14), 22, 14)
    add("head", HAIR, without(cap, lambda x, y, z: (z > .02 and y < .205 - .3 * abs(x)) or (abs(x) > .085 and y < .165 and -.06 < z < .05)))
    add("head", HAIR, ellipsoid((0, .135, -.072), (.118, .115, .085), 18, 10))
    for x in (-.078, -.038, .002, .042, .08):
        add("head", HAIR, turned(ellipsoid((x, .204 - abs(x) * .3, .106), (.03, .021, .028), 10, 6), (x, .204, .106), .35, 0), 0)
    add("head", HAIR, turned(ellipsoid((.02, .297, -.01), (.026, .03, .05), 10, 6), (.02, .29, -.01), -.5, 0), 0)


def torso():
    def chest(p):
        x, y, z = p
        if z > 0:
            z += .014 * smoothstep(.16, .26, y) * smoothstep(.38, .3, y) * min(1.0, z / .1)
        return [x, y, z]

    add("torso", TOP, lathe([(-.01, .158, .104), (.06, .16, .106), (.14, .172, .114), (.22, .188, .122), (.29, .2, .127),
                             (.34, .203, .124), (.38, .19, .112), (.405, .15, .09), (.42, .09, .065)], 24, shape=chest))
    add("torso", TOP, ellipsoid((0, .362, -.004), (.232, .07, .122), 24, 10))
    add("torso", TOP_SHADE, lathe([(.395, .074, .068), (.425, .069, .063), (.44, .063, .058)], 18))
    add("torso", SKIN, lathe([(.38, .05, .05), (.47, .047, .047), (.54, .045, .045)], 14))
    for y in (.3, .262, .224, .186):
        add("torso", TOP_SHADE, ellipsoid((0, y, .128 + .01 * smoothstep(.16, .26, y)), (.011, .006, .006), 8, 6), 0)


def hips():
    def folds(a, y):
        return 1 + .035 * math.sin(9 * a) * smoothstep(-.03, -.19, y)

    add("hips", TOP, lathe([(-.2, .21, .148), (-.15, .2, .142), (-.08, .188, .132), (-.02, .176, .122), (.025, .17, .116)], 28, folds))
    add("hips", TOP_SHADE, lathe([(-.206, .217, .154), (-.184, .214, .151)], 28, folds))
    add("hips", LEATHER, lathe([(-.03, .18, .125), (.036, .178, .123)], 24))
    add("hips", METAL, ellipsoid((0, .003, .127), (.033, .027, .008), 12, 8))
    add("hips", LEATHER, ellipsoid((0, .003, .133), (.018, .014, .004), 10, 6), 0)
    add("hips", LEATHER, ellipsoid((.135, -.035, .07), (.034, .04, .03), 12, 8))


def arms():
    add("armU", TOP, ellipsoid((0, -.004, 0), (.072, .07, .072), 16, 10))
    add("armU", TOP, lathe([(-.02, .066, .064), (-.08, .066, .063), (-.16, .06, .057), (-.23, .054, .052), (-.27, .052, .05)], 18))
    add("armU", TOP_SHADE, lathe([(-.262, .058, .056), (-.286, .057, .055)], 18))
    add("armL", TOP, ellipsoid((0, 0, 0), (.052, .052, .052), 14, 10))
    add("armL", SKIN, lathe([(-.02, .046, .044), (-.08, .045, .043), (-.16, .039, .036), (-.235, .033, .03)], 16))
    add("armL", LEATHER, lathe([(-.13, .047, .044), (-.15, .048, .045), (-.215, .041, .037), (-.232, .039, .035)], 16))
    add("armL", SKIN, ellipsoid((0, -.282, .006), (.034, .046, .022), 14, 10))
    for fx, length in ((-.021, .03), (-.007, .036), (.007, .034), (.021, .027)):
        add("armL", SKIN, turned(ellipsoid((fx, -.31 - length / 2, .01), (.0085, length / 2 + .005, .0095), 8, 6), (fx, -.31, .01), -.25, 0), 0)
    add("armL", SKIN, turned(ellipsoid((0, -.27, .03), (.011, .025, .012), 8, 6), (0, -.255, .025), -.6, 0), 0)


def legs():
    add("legU", BOTTOM, ellipsoid((0, 0, 0), (.088, .074, .088), 16, 10))
    add("legU", BOTTOM, lathe([(-.02, .088, .088), (-.1, .086, .084), (-.2, .078, .076), (-.32, .068, .066), (-.4, .064, .062)], 18))
    add("legL", BOTTOM, ellipsoid((0, 0, .006), (.066, .062, .07), 14, 10))
    add("legL", BOTTOM, lathe([(-.02, .062, .062), (-.14, .06, .058), (-.25, .054, .052), (-.3, .052, .05)], 16))
    add("legL", BOOTS, lathe([(-.31, .07, .072), (-.335, .066, .068), (-.39, .064, .066), (-.44, .07, .074), (-.46, .072, .078)], 18))
    add("legL", BOOTS_SHADE, lathe([(-.296, .081, .083), (-.318, .079, .081)], 18))
    add("legL", BOOTS, ellipsoid((0, -.448, .068), (.064, .042, .118), 16, 10))
    add("legL", BOOTS_SHADE, ellipsoid((0, -.484, .064), (.07, .012, .124), 16, 6))
    for y in (-.335, -.37, -.405):
        add("legL", LEATHER, ellipsoid((0, y, .072), (.02, .004, .006), 8, 4), 0)


def export(path):
    dg = bpy.context.evaluated_depsgraph_get()
    with open(path, "wb") as f:
        f.write(b"KSCL")
        f.write(struct.pack(">ii", 1, len(parts)))
        for name, obs in parts.items():
            tris = []
            for ob in obs:
                ev = ob.evaluated_get(dg)
                me = ev.to_mesh()
                me.calc_loop_triangles()
                for lt in me.loop_triangles:
                    tris.append(([me.vertices[i].co.copy() for i in lt.vertices], ob["zone"]))
                ev.to_mesh_clear()
            raw = name.encode()
            f.write(struct.pack(">H", len(raw)) + raw + struct.pack(">i", len(tris)))
            for pts, zone in tris:
                for v in pts:
                    f.write(struct.pack(">fff", v.x, v.z, -v.y))
                f.write(struct.pack(">b", zone))
            print(f"{name}: {len(tris)} triangles")


head()
torso()
hips()
arms()
legs()
export(sys.argv[sys.argv.index("--") + 1])

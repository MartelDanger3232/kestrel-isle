package kestrel;

/** Rigs, meshes and animation clips for every character. All designs are original. */
final class Characters {
    private Characters() {}

    static final int SKIN = 0xd9a77e, STEEL = 0xd6dde4, TEAL = 0x2aa198, WOOD = 0x8a5a2e, LEATHER = 0x5a3b26;

    /**
     * Builds a smooth-shaded humanoid rig: hips (with a tunic skirt), torso, head with a face, tapered
     * two-part arms and legs with joint caps, and an optional curved two-piece cape.
     */
    static Rig humanoid(int skin, int hair, int top, int bottom, int boots, Mesh headExtra, Mesh torsoExtra, boolean cape, int capeCol) {
        int skinShade = M.scale(skin, .82f), hairShade = M.scale(hair, .75f);
        // On the graphics card the body is the Blender sculpt, painted in this character's colours (zones as in Sculpts).
        int[] paint = {skin, skinShade, hair, hairShade, top, M.scale(top, .78f), bottom, boots, M.scale(boots, .78f), 0x3a2a1e, 0xc8a050,
                0x231c18, 0xf4f0e8, 0x8a4a3a};
        Mesh hips = Sculpts.part("hips", paint);
        if (hips != null) {
            Mesh torso = Sculpts.part("torso", paint), head = Sculpts.part("head", paint);
            if (torsoExtra != null) torso.join(torsoExtra.smooth());
            if (headExtra != null) head.join(headExtra.smooth());
            Rig full = rig(hips, torso, head, Sculpts.part("armU", paint), Sculpts.part("armL", paint), Sculpts.part("legU", paint), Sculpts.part("legL", paint), cape, capeCol);
            LOW.put(full, plain(skin, hair, top, bottom, boots, headExtra, torsoExtra, cape, capeCol));
            return full;
        }
        return plain(skin, hair, top, bottom, boots, headExtra, torsoExtra, cape, capeCol);
    }

    /** Stand-ins for characters seen from afar: the same skeleton built from a few hundred triangles instead of the sculpt's thousands. */
    private static final java.util.Map<Rig, Rig> LOW = new java.util.IdentityHashMap<>();

    /** The cheap stand-in for a sculpted rig (or the rig itself if it has none). */
    static Rig low(Rig r) { return LOW.getOrDefault(r, r); }

    /** A humanoid built from primitives: the look before the sculpts, and every sculpted character's far-away stand-in. */
    private static Rig plain(int skin, int hair, int top, int bottom, int boots, Mesh headExtra, Mesh torsoExtra, boolean cape, int capeCol) {
        int hairShade = M.scale(hair, .75f), skinShade = M.scale(skin, .82f);
        Mesh hips = new Mesh().tube(0, -.18f, 0, .205f, .14f, .17f, .115f, .2f, 12, top)
                .tube(0, -.015f, 0, .175f, .12f, .175f, .12f, .065f, 12, 0x3a2a1e)
                .box(0, .018f, .121f, .035f, .028f, .008f, 0xc8a050).smooth();
        Mesh torso = new Mesh().tube(0, 0, 0, .165f, .105f, .205f, .125f, .36f, 12, top)
                .blob(0, .37f, 0, .23f, .08f, .13f, 2, 12, top, 0)
                .tube(0, .4f, 0, .052f, .05f, .046f, .046f, .13f, 8, skin);
        if (torsoExtra != null) torso.append(torsoExtra);
        torso.smooth();
        Mesh head = new Mesh().blob(0, .14f, .005f, .118f, .14f, .128f, 4, 12, skin, 0)
                .blob(0, .19f, -.022f, .132f, .118f, .142f, 3, 12, hair, .05f)
                .blob(0, .14f, -.075f, .12f, .12f, .09f, 3, 10, hair, .04f)
                .box(.045f, .152f, .128f, .019f, .021f, .006f, 0x231c18).box(-.045f, .152f, .128f, .019f, .021f, .006f, 0x231c18)
                .box(.049f, .156f, .133f, .006f, .007f, .002f, 0xf4f0e8).box(-.041f, .156f, .133f, .006f, .007f, .002f, 0xf4f0e8)
                .box(.047f, .186f, .125f, .028f, .006f, .006f, hairShade).box(-.047f, .186f, .125f, .028f, .006f, .006f, hairShade)
                .blob(0, .122f, .136f, .017f, .028f, .02f, 2, 6, skinShade, 0)
                .box(0, .078f, .127f, .026f, .005f, .004f, 0x8a4a3a)
                .blob(.12f, .135f, -.005f, .022f, .036f, .026f, 2, 6, skinShade, 0).blob(-.12f, .135f, -.005f, .022f, .036f, .026f, 2, 6, skinShade, 0);
        if (headExtra != null) head.append(headExtra);
        head.smooth();
        Mesh armU = new Mesh().blob(0, 0, 0, .068f, .068f, .068f, 2, 8, top, 0).tube(0, -.27f, 0, .05f, .054f, .064f, .064f, .27f, 8, top).smooth();
        Mesh armL = new Mesh().blob(0, 0, 0, .052f, .052f, .052f, 2, 8, top, 0).tube(0, -.24f, 0, .041f, .044f, .051f, .051f, .24f, 8, skin)
                .blob(0, -.285f, .008f, .046f, .062f, .034f, 3, 8, skin, 0).blob(.03f, -.25f, .03f, .018f, .03f, .018f, 2, 5, skin, 0).smooth();
        Mesh legU = new Mesh().blob(0, 0, 0, .085f, .07f, .085f, 2, 8, bottom, 0).tube(0, -.4f, 0, .066f, .07f, .084f, .088f, .4f, 8, bottom).smooth();
        Mesh legL = new Mesh().blob(0, 0, .005f, .066f, .06f, .07f, 2, 8, bottom, 0).tube(0, -.33f, 0, .054f, .057f, .066f, .068f, .33f, 8, bottom)
                .tube(0, -.46f, .012f, .07f, .078f, .068f, .074f, .16f, 8, boots).blob(0, -.435f, .1f, .064f, .046f, .1f, 2, 8, boots, 0)
                .tube(0, -.32f, .005f, .074f, .08f, .074f, .08f, .035f, 8, M.scale(boots, .8f)).smooth();
        return rig(hips, torso, head, armU, armL, legU, legL, cape, capeCol);
    }

    /** Hangs body parts on the humanoid skeleton, with a two-piece cape if wanted. */
    private static Rig rig(Mesh hips, Mesh torso, Mesh head, Mesh armU, Mesh armL, Mesh legU, Mesh legL, boolean cape, int capeCol) {
        Object[] base = {"hips", null, 0, .9f, 0, hips, "torso", "hips", 0, .06f, 0, torso, "head", "torso", 0, .5f, 0, head,
                "armUL", "torso", -.25f, .44f, 0, armU, "armLL", "armUL", 0, -.28f, 0, armL,
                "armUR", "torso", .25f, .44f, 0, armU, "armLR", "armUR", 0, -.28f, 0, armL,
                "legUL", "hips", -.095f, -.05f, 0, legU, "legLL", "legUL", 0, -.4f, 0, legL,
                "legUR", "hips", .095f, -.05f, 0, legU, "legLR", "legUR", 0, -.4f, 0, legL};
        if (!cape) return new Rig(base);
        Mesh[] panels = cape(capeCol);
        Mesh capeU = panels[0], capeL = panels[1];
        Object[] all = java.util.Arrays.copyOf(base, base.length + 12);
        System.arraycopy(new Object[]{"capeU", "torso", 0, .46f, -.13f, capeU, "capeL", "capeU", 0, -.42f, -.05f, capeL}, 0, all, base.length, 12);
        return new Rig(all);
    }

    /** A two-sided curved strip hanging down from y=0, three columns across. */
    private static Mesh cloth(float[] xTop, float[] zTop, float[] xBot, float[] zBot, float len, int col) {
        Mesh m = new Mesh().twoSided();
        for (int i = 0; i < 2; i++) {
            m.tri(xTop[i], 0, zTop[i], xTop[i + 1], 0, zTop[i + 1], xBot[i + 1], -len, zBot[i + 1], col);
            m.tri(xTop[i], 0, zTop[i], xBot[i + 1], -len, zBot[i + 1], xBot[i], -len, zBot[i], col);
        }
        return m;
    }

    /** A cape's two cloth panels in colour col, hung the way humanoid() hangs them. */
    static Mesh[] cape(int col) {
        return new Mesh[]{cloth(new float[]{-.21f, 0, .21f}, new float[]{-.03f, -.065f, -.03f}, new float[]{-.25f, 0, .25f}, new float[]{-.05f, -.095f, -.05f}, .42f, col),
                cloth(new float[]{-.25f, 0, .25f}, new float[]{0, -.035f, 0}, new float[]{-.28f, 0, .28f}, new float[]{0, -.04f, 0}, .36f, M.scale(col, .92f))};
    }

    // ---- the hero ---------------------------------------------------------------------------------

    /** The Keeper's grandchild: dark hair, rust tunic, teal scarf, a leather pauldron and a hood worn down. */
    static final Rig HERO = humanoid(SKIN, 0x3b2a20, 0xa8432a, 0x4a4458, LEATHER, null,
            new Mesh().tube(0, .39f, 0, .135f, .1f, .112f, .088f, .085f, 12, TEAL).box(.07f, .33f, .125f, .03f, .07f, .012f, TEAL)
                    .blob(0, .4f, -.155f, .15f, .07f, .06f, 2, 10, 0x7a2e1e, 0).blob(-.25f, .415f, 0, .1f, .06f, .1f, 2, 8, LEATHER, 0)
                    .box(0, .2f, 0, .2f, .012f, .127f, 0x6a4a2a).box(-.06f, .22f, .126f, .02f, .2f, .008f, LEATHER),
            true, 0x7a2e1e);
    static final Mesh SWORD = new Mesh().box(0, 0, .06f, .025f, .025f, .08f, TEAL).box(0, 0, .15f, .11f, .025f, .03f, 0x4a4e58)
            .box(0, 0, .6f, .018f, .045f, .43f, STEEL).prism(0, 1.03f, 0, .045f, 0, .01f, 4, STEEL);
    static final Mesh GREATSWORD = new Mesh().box(0, 0, .08f, .03f, .03f, .17f, 0x3a2a20).box(0, 0, .28f, .21f, .035f, .045f, 0x4a4e58)
            .box(0, 0, 1.0f, .03f, .09f, .68f, 0x5a5e68).box(0, 0, .98f, .033f, .022f, .6f, 0xff7a2a | Mesh.GLOW)
            .segment(0, 0, 1.68f, 0, 0, 1.9f, .09f, .005f, 4, 0x5a5e68);
    static final Mesh TIDE_SPEAR = new Mesh().segment(0, 0, -.7f, 0, 0, 1.6f, .028f, .025f, 6, WOOD).box(0, 0, 1.6f, .035f, .035f, .05f, TEAL)
            .segment(0, 0, 1.62f, 0, 0, 2.08f, .075f, .004f, 4, 0xd8e0e8).blob(0, -.07f, 1.52f, .03f, .09f, .03f, 2, 4, 0x2a7a8a, 0);
    static final Mesh FANG = new Mesh().box(0, 0, .05f, .02f, .02f, .07f, 0x2a1e18).box(0, 0, .13f, .06f, .02f, .02f, 0xc8a050)
            .box(0, 0, .3f, .012f, .035f, .17f, 0xc8d4dc).segment(0, 0, .47f, 0, 0, .58f, .035f, .003f, 4, 0xc8d4dc);
    static final Mesh HAMMER = new Mesh().segment(0, 0, -.15f, 0, 0, 1.0f, .032f, .03f, 6, 0x4a3424).box(0, 0, 1.06f, .17f, .13f, .13f, 0x6a7a8a)
            .box(0, 0, 1.06f, .18f, .05f, .05f, 0x9fe8ff | Mesh.GLOW).box(.2f, 0, 1.06f, .03f, .1f, .1f, 0x4a5a6a).box(-.2f, 0, 1.06f, .03f, .1f, .1f, 0x4a5a6a);
    static final Mesh SHIELD = new Mesh().prism(0, -.03f, 0, .31f, .31f, .06f, 12, WOOD).prism(0, .03f, 0, .2f, .2f, .01f, 12, 0x6a4a2a)
            .prism(0, .04f, 0, .09f, .09f, .01f, 8, TEAL).box(0, .035f, 0, .3f, .008f, .025f, 0x5a5e66);
    static final Mesh FLUTE = new Mesh().box(0, 0, 0, .03f, .03f, .16f, 0xe8dcc0).box(0, .031f, .05f, .012f, .002f, .012f, 0x3a2a20)
            .box(0, .031f, -.03f, .012f, .002f, .012f, 0x3a2a20);
    static final Mesh BOMB = new Mesh().blob(0, 0, 0, .2f, .2f, .2f, 3, 6, 0x2a2e3a, 0).box(0, .22f, 0, .04f, .05f, .04f, 0x8a7a5a);
    static final Mesh CLAW = new Mesh().box(0, 0, 0, .05f, .05f, .12f, 0x5a5e66).prism(0, .0f, .12f, .06f, 0, .02f, 3, STEEL)
            .box(.07f, 0, .14f, .015f, .02f, .07f, STEEL).box(-.07f, 0, .14f, .015f, .02f, .07f, STEEL);

    static final Rig.Clip H_SLASH1 = new Rig.Clip(HERO, false, 0, "armUR:-1.3,1.3,.2 armLR:-.5 torso:.05,-.5", .09f, "armUR:-1.4,1.5,.3 armLR:-.4 torso:.05,-.65",
            .19f, "armUR:-1.45,-1.15 armLR:-.1 torso:.14,.6", .36f, "armUR:-.7,-.6 armLR:-.4 torso:.06,.2");
    static final Rig.Clip H_SLASH2 = new Rig.Clip(HERO, false, 0, "armUR:-1.4,-1 armLR:-.3 torso:.1,.5", .08f, "armUR:-1.45,-1.25 torso:.1,.6",
            .18f, "armUR:-1.4,1.3,.2 armLR:-.2 torso:.12,-.55", .36f, "armUR:-.7,.5 armLR:-.4 torso:.05,-.1");
    static final Rig.Clip H_SLASH3 = new Rig.Clip(HERO, false, 0, "armUR:-2.4,.3 armLR:-.8 torso:-.1 armUL:-.6", .14f, "armUR:-3,.1 armLR:-.4 torso:-.2 root:.05",
            .26f, "armUR:-.55,0 armLR:-.1 torso:.45 legUL:-.6 legLL:.3 legUR:.4 root:-.12", .5f, "armUR:-.5 armLR:-.3 torso:.3 root:-.06");
    static final Rig.Clip H_CHARGE = new Rig.Clip(HERO, true, 0, "armUR:-1,1.7,.4 armLR:-.3 torso:.12,-.75 legUL:-.45 legLL:.45 legUR:.4 legLR:.3 root:-.1",
            .3f, "armUR:-1.05,1.75,.4 armLR:-.3 torso:.14,-.8 legUL:-.45 legLL:.45 legUR:.4 legLR:.3 root:-.11");
    static final Rig.Clip H_SPIN = new Rig.Clip(HERO, false, 0, "armUR:-1.45,1.2 armLR:-.1 legUL:-.4 legLL:.4 legUR:.3 root:-.1,0,0",
            .42f, "armUR:-1.45,1.2 armLR:-.1 legUL:-.4 legLL:.4 legUR:.3 root:-.1,0,6.28", .55f, "armUR:-.8,.6 root:0,0,6.28");
    static final Rig.Clip H_ROLL = new Rig.Clip(HERO, false, 0, "torso:.4 armUL:-1 armUR:-1 legUL:-.8 legLL:1 legUR:-.8 legLR:1 root:-.2,0",
            .22f, "torso:.6 armUL:-1.6 armUR:-1.6 armLL:-1 armLR:-1 legUL:-1.6 legLL:2 legUR:-1.6 legLR:2 root:-.45,3.14",
            .42f, "torso:.3 armUL:-.6 armUR:-.6 legUL:-.6 legLL:.8 legUR:-.4 legLR:.6 root:-.15,6.28", .5f, "root:0,6.28");
    static final Rig.Clip H_FLIP = new Rig.Clip(HERO, false, 0, "legUL:-.5 legLL:.6 legUR:-.5 legLR:.6 armUL:-2 armUR:-2 root:-.1,0",
            .25f, "torso:.5 legUL:-1.6 legLL:2 legUR:-1.6 legLR:2 armUL:-1 armUR:-1 root:.7,-3.14", .5f, "legUL:-.4 legLL:.5 legUR:-.4 legLR:.5 root:0,-6.28");
    static final Rig.Clip H_HOP = new Rig.Clip(HERO, false, 0, "legUL:-.3 legUR:-.3 root:-.05", .15f, "legUL:-.7 legLL:.9 legUR:-.5 legLR:.7 armUL:-.5,0,-.6 armUR:-.5,0,.6 root:.3",
            .35f, "legUL:-.2 legLL:.3 legUR:-.2 legLR:.3 root:0");
    static final Rig.Clip H_BLOCK = new Rig.Clip(HERO, true, 0, "armUL:-1.35,.7,-.2 armLL:-1.25 torso:.12 legUL:-.35 legLL:.35 legUR:.3 legLR:.2 root:-.06");
    static final Rig.Clip H_HURT = new Rig.Clip(HERO, false, 0, "torso:-.45 head:-.3 armUL:-.7,0,-.7 armUR:-.7,0,.7 root:0,-.25", .35f, "torso:-.1 head:-.1 root:0,-.05");
    static final Rig.Clip H_DEAD = new Rig.Clip(HERO, false, 0, "torso:-.3 head:-.2 root:0,-.3", .6f, "torso:-.2 armUL:-2.6,0,-.3 armUR:-2.6,0,.3 legUL:-.25 legUR:.1 root:-.72,-1.5");
    static final Rig.Clip H_ITEM = new Rig.Clip(HERO, true, 0, "armUL:-2.95,0,-.15 armUR:-2.95,0,.15 armLL:-.2 armLR:-.2 head:-.35 torso:-.1");
    static final Rig.Clip H_FLUTE = new Rig.Clip(HERO, true, 0, "armUL:-1.25,.95 armLL:-1.95 armUR:-1.25,-.95 armLR:-1.95 head:.08", .5f,
            "armUL:-1.2,.95 armLL:-1.95 armUR:-1.25,-.95 armLR:-1.9 head:.02 torso:-.04", 1f, "armUL:-1.25,.95 armLL:-1.95 armUR:-1.25,-.95 armLR:-1.95 head:.08");
    static final Rig.Clip H_PUSH = new Rig.Clip(HERO, true, 0, "armUL:-1.5 armUR:-1.5 armLL:-.25 armLR:-.25 torso:.45 legUL:-.7 legLL:.2 legUR:.5 legLR:.3 root:-.07",
            .4f, "armUL:-1.5 armUR:-1.5 armLL:-.25 armLR:-.25 torso:.45 legUL:.5 legLL:.3 legUR:-.7 legLR:.2 root:-.07",
            .8f, "armUL:-1.5 armUR:-1.5 armLL:-.25 armLR:-.25 torso:.45 legUL:-.7 legLL:.2 legUR:.5 legLR:.3 root:-.07");
    static final Rig.Clip H_CLIMB = new Rig.Clip(HERO, true, 0, "armUL:-2.8 armLL:-.4 armUR:-2.2 armLR:-.9 legUL:-.9 legLL:1.1 legUR:-.3 legLR:.5",
            .35f, "armUL:-2.2 armLL:-.9 armUR:-2.8 armLR:-.4 legUL:-.3 legLL:.5 legUR:-.9 legLR:1.1",
            .7f, "armUL:-2.8 armLL:-.4 armUR:-2.2 armLR:-.9 legUL:-.9 legLL:1.1 legUR:-.3 legLR:.5");
    static final Rig.Clip H_LEDGE = new Rig.Clip(HERO, false, 0, "armUL:-2.8 armUR:-2.8 legUL:-1 legLL:1.3 root:-.5", .25f,
            "armUL:-1.2 armUR:-1.2 torso:.7 legUL:-1.4 legLL:1.7 legUR:-.4 legLR:.6 root:-.3", .45f, "torso:.2 legUL:-.4 legLL:.5 root:-.05");
    static final Rig.Clip H_GLIDE = new Rig.Clip(HERO, true, 0, "armUL:-2.75,0,-.35 armUR:-2.75,0,.35 armLL:-.25 armLR:-.25 legUL:.15 legLL:.35 legUR:-.1 legLR:.25 torso:-.08");
    /** Held aloft by the Skyward Gust: arms spread on the wind, legs trailing, breathing slowly up and down. */
    static final Rig.Clip H_HOVER = new Rig.Clip(HERO, true, 0, "armUL:-.35,0,-1.15 armUR:-.35,0,1.15 armLL:-.35 armLR:-.35 legUL:.2 legLL:.55 legUR:.05 legLR:.3 torso:-.06 head:-.08",
            .9f, "armUL:-.55,0,-1.3 armUR:-.55,0,1.3 armLL:-.5 armLR:-.5 legUL:.3 legLL:.7 legUR:.12 legLR:.42 torso:-.1 head:-.14",
            1.8f, "armUL:-.35,0,-1.15 armUR:-.35,0,1.15 armLL:-.35 armLR:-.35 legUL:.2 legLL:.55 legUR:.05 legLR:.3 torso:-.06 head:-.08");
    static final Rig.Clip H_AIR = new Rig.Clip(HERO, true, 0, "legUL:-.6 legLL:.9 legUR:.2 legLR:.4 armUL:-.6,0,-.5 armUR:-.6,0,.5 torso:.1");
    static final Rig.Clip H_AIM = new Rig.Clip(HERO, true, 0, "armUL:-1.57,-.12 armLL:-.02 armUR:-1.5,.8 armLR:-2 head:.04,-.2 torso:0,.42 legUL:-.3 legUR:.28 root:-.03",
            .7f, "armUL:-1.59,-.11 armLL:-.03 armUR:-1.52,.82 armLR:-2.02 head:.05,-.2 torso:.02,.43 legUL:-.3 legUR:.28 root:-.035",
            1.4f, "armUL:-1.57,-.12 armLL:-.02 armUR:-1.5,.8 armLR:-2 head:.04,-.2 torso:0,.42 legUL:-.3 legUR:.28 root:-.03");
    /** Raise the bow, nock, and pull the string back to the cheek. */
    static final Rig.Clip H_DRAW = new Rig.Clip(HERO, false, 0, "armUL:-.7,-.1 armLL:-.4 armUR:-.6,.2 armLR:-.9 torso:0,.1",
            .14f, "armUL:-1.45,-.12 armLL:-.1 armUR:-1.3,.35 armLR:-1.4 torso:0,.25 legUL:-.2 legUR:.2",
            .35f, "armUL:-1.57,-.12 armLL:-.02 armUR:-1.5,.8 armLR:-2 head:.04,-.2 torso:0,.42 legUL:-.3 legUR:.28 root:-.03");
    /** The string snaps forward; the drawing hand flies back past the ear, then the bow comes down. */
    static final Rig.Clip H_LOOSE = new Rig.Clip(HERO, false, 0, "armUL:-1.57,-.12 armLL:-.02 armUR:-1.5,.8 armLR:-2 head:.04,-.2 torso:0,.42 legUL:-.3 legUR:.28",
            .07f, "armUL:-1.6,-.1 armLL:0 armUR:-1.55,1.25,.35 armLR:-1.1 head:.04,-.2 torso:-.04,.46 legUL:-.3 legUR:.28",
            .2f, "armUL:-1.5,-.1 armLL:-.05 armUR:-1.4,1.15,.3 armLR:-.9 head:0,-.15 torso:-.02,.4 legUL:-.25 legUR:.25",
            .45f, "armUL:-.8 armLL:-.3 armUR:-.6,.3 armLR:-.6 torso:0,.12");
    static final Rig.Clip H_THROW = new Rig.Clip(HERO, false, 0, "armUR:-2.7 armLR:-.6 torso:-.15", .12f, "armUR:-1.1 armLR:-.1 torso:.25", .3f, "armUR:-.4 torso:.05");
    static final Rig.Clip H_HOLD = new Rig.Clip(HERO, true, 0, "armUL:-2.9,0,-.2 armUR:-2.9,0,.2 armLL:-.6 armLR:-.6");
    static final Rig.Clip H_SWIM = new Rig.Clip(HERO, true, 0, "armUL:-2.8,0,-.4 armUR:-.2,0,.3 legUL:-.3 legUR:.3 root:-.55,1.25",
            .45f, "armUL:-.2,0,-.3 armUR:-2.8,0,.4 legUL:.3 legUR:-.3 root:-.55,1.25", .9f, "armUL:-2.8,0,-.4 armUR:-.2,0,.3 legUL:-.3 legUR:.3 root:-.55,1.25");
    static final Rig.Clip TALK = new Rig.Clip(HERO, true, 0, "armUR:-.5,0,.3 armLR:-.9 head:.05", .4f, "armUR:-.9,.3,.4 armLR:-1.2 head:-.05", .8f, "armUR:-.5,0,.3 armLR:-.9 head:.05");
    static final Rig.Clip WAVE = new Rig.Clip(HERO, true, 0, "armUR:-2.6,0,.3 armLR:-.4", .3f, "armUR:-2.6,0,.6 armLR:-.9", .6f, "armUR:-2.6,0,.3 armLR:-.4");
    static final Rig.Clip H_CAST = new Rig.Clip(HERO, false, 0, "armUR:-.6,.4 armLR:-1.6 armUL:-.4 torso:-.1,.3", .18f,
            "armUR:-1.1,.2 armLR:-1.9 armUL:-.6 torso:-.15,.4 root:.02", .3f, "armUR:-1.6,-.1 armLR:-.05 armUL:-.3,0,-.5 torso:.2,-.15 legUL:-.4 legUR:.3",
            .55f, "armUR:-1.2 armLR:-.2 torso:.08");
    static final Rig.Clip H_STARFALL = new Rig.Clip(HERO, false, 0, "armUL:-1.2,0,-.6 armUR:-1.2,0,.6 torso:.1 root:-.05", .4f,
            "armUL:-3.0,0,-.3 armUR:-3.0,0,.3 head:-.5 torso:-.25 root:.1", 1f, "armUL:-3.05,0,-.35 armUR:-3.05,0,.35 head:-.55 torso:-.28 root:.12",
            1.25f, "armUL:-.5 armUR:-.5 torso:.3 root:-.1");
    static final Rig.Clip H_DRINK = new Rig.Clip(HERO, false, 0, "armUR:-.8 armLR:-1.6", .25f, "armUR:-1.25,-.4 armLR:-2.3 head:-.35", .65f,
            "armUR:-1.25,-.4 armLR:-2.3 head:-.4", .9f, "armUR:-.4 armLR:-.4 head:0");
    static final Rig.Clip H_RIDE = new Rig.Clip(HERO, true, 0, "legUL:-1.25,0,-.42 legLL:1.45 legUR:-1.25,0,.42 legLR:1.45 armUL:-.7 armLL:-.9 armUR:-.7 armLR:-.9 torso:.12",
            .5f, "legUL:-1.25,0,-.42 legLL:1.45 legUR:-1.25,0,.42 legLR:1.45 armUL:-.75 armLL:-.95 armUR:-.75 armLR:-.95 torso:.16",
            1f, "legUL:-1.25,0,-.42 legLL:1.45 legUR:-1.25,0,.42 legLR:1.45 armUL:-.7 armLL:-.9 armUR:-.7 armLR:-.9 torso:.12");

    // ---- Juniper ------------------------------------------------------------------------------

    private static final int COAT = 0x8c4c2a, MANE = 0xeadcb4, HOOF = 0x2a2420;

    private static Mesh leg(boolean upper, boolean sock) {
        if (upper) return new Mesh().blob(0, 0, 0, .12f, .12f, .14f, 2, 8, COAT, 0).tube(0, -.42f, 0, .068f, .078f, .11f, .13f, .42f, 8, COAT).smooth();
        return new Mesh().blob(0, 0, 0, .07f, .07f, .075f, 2, 8, COAT, 0).tube(0, -.42f, 0, .045f, .05f, .056f, .06f, .42f, 8, sock ? 0xf0ece0 : COAT)
                .tube(0, -.5f, .01f, .062f, .07f, .056f, .062f, .09f, 8, HOOF).smooth();
    }

    static final Rig HORSE = new Rig(
            "body", null, 0, 1.28f, 0, new Mesh().blob(0, 0, 0, .31f, .37f, .94f, 4, 12, COAT, 0)
                    .box(0, .33f, -.04f, .335f, .025f, .36f, TEAL).box(0, .36f, -.04f, .345f, .012f, .37f, 0xd8b44a)
                    .box(0, .42f, -.04f, .2f, .06f, .26f, LEATHER).box(0, .49f, .2f, .07f, .05f, .04f, LEATHER).box(0, .47f, -.27f, .17f, .05f, .03f, LEATHER)
                    .box(.31f, .05f, -.02f, .012f, .26f, .02f, LEATHER).box(-.31f, .05f, -.02f, .012f, .26f, .02f, LEATHER)
                    .box(.31f, -.22f, -.02f, .03f, .015f, .06f, 0x9a9ea6).box(-.31f, -.22f, -.02f, .03f, .015f, .06f, 0x9a9ea6).smooth(),
            "neck", "body", 0, .18f, .8f, new Mesh().tube(0, 0, 0, .17f, .23f, .12f, .15f, .8f, 10, COAT)
                    .box(0, .42f, -.14f, .035f, .4f, .05f, MANE).smooth(),
            "head", "neck", 0, .76f, 0, new Mesh().blob(0, 0, .18f, .12f, .14f, .27f, 4, 10, COAT, 0)
                    .blob(0, -.05f, .43f, .1f, .1f, .12f, 3, 10, 0x6e3a20, 0)
                    .tube(.075f, .09f, .0f, .032f, .022f, .008f, .008f, .15f, 5, COAT).tube(-.075f, .09f, .0f, .032f, .022f, .008f, .008f, .15f, 5, COAT)
                    .box(0, .08f, .33f, .028f, .075f, .012f, 0xf4f0e8).box(.11f, .045f, .24f, .012f, .022f, .025f, 0x1a1210)
                    .box(-.11f, .045f, .24f, .012f, .022f, .025f, 0x1a1210).box(0, .13f, .02f, .03f, .07f, .06f, MANE)
                    .box(.1f, -.06f, .36f, .01f, .01f, .1f, LEATHER).box(-.1f, -.06f, .36f, .01f, .01f, .1f, LEATHER).smooth(),
            "tail", "body", 0, .22f, -.9f, new Mesh().tube(0, -.7f, 0, .03f, .035f, .075f, .07f, .7f, 6, MANE).smooth(),
            "legFLu", "body", -.18f, -.18f, .62f, leg(true, false), "legFLl", "legFLu", 0, -.42f, 0, leg(false, true),
            "legFRu", "body", .18f, -.18f, .62f, leg(true, false), "legFRl", "legFRu", 0, -.42f, 0, leg(false, true),
            "legHLu", "body", -.18f, -.16f, -.64f, leg(true, false), "legHLl", "legHLu", 0, -.42f, 0, leg(false, false),
            "legHRu", "body", .18f, -.16f, -.64f, leg(true, false), "legHRl", "legHRu", 0, -.42f, 0, leg(false, false));

    // ---- the north ----------------------------------------------------------------------------

    private static final int FUR = 0xd8dde2, FUR_D = 0x8e98a2;

    private static Mesh wolfLeg(boolean upper, boolean front) {
        if (upper) return new Mesh().blob(0, 0, 0, .065f, .07f, .075f, 2, 6, FUR, 0).tube(0, -.26f, 0, .035f, .04f, .055f, .06f, .26f, 6, FUR).smooth();
        return new Mesh().tube(0, -.26f, 0, .028f, .03f, .036f, .038f, .26f, 6, front ? FUR : FUR_D).blob(0, -.27f, .03f, .04f, .025f, .055f, 2, 6, FUR_D, 0).smooth();
    }

    /** Frostfang: a pale, ice-eyed wolf of the northern snows. */
    static final Rig WOLF = new Rig(
            "body", null, 0, .6f, 0, new Mesh().blob(0, 0, -.05f, .19f, .2f, .48f, 3, 10, FUR, .05f).blob(0, .04f, .26f, .22f, .24f, .2f, 3, 8, 0xeef2f4, .06f).smooth(),
            "head", "body", 0, .12f, .5f, new Mesh().blob(0, 0, .04f, .13f, .13f, .15f, 3, 8, FUR, 0).blob(0, -.045f, .21f, .065f, .06f, .12f, 2, 6, FUR_D, 0)
                    .tube(.075f, .08f, -.01f, .04f, .025f, 0, 0, .13f, 4, FUR_D).tube(-.075f, .08f, -.01f, .04f, .025f, 0, 0, .13f, 4, FUR_D)
                    .box(.058f, .035f, .145f, .022f, .014f, .01f, 0x9fe8ff | Mesh.GLOW).box(-.058f, .035f, .145f, .022f, .014f, .01f, 0x9fe8ff | Mesh.GLOW)
                    .box(0, -.03f, .33f, .025f, .02f, .01f, 0x1a1a1a).smooth(),
            "tail", "body", 0, .06f, -.5f, new Mesh().tube(0, -.38f, 0, .03f, .035f, .07f, .065f, .38f, 6, FUR).smooth(),
            "legFLu", "body", -.1f, -.1f, .3f, wolfLeg(true, true), "legFLl", "legFLu", 0, -.26f, 0, wolfLeg(false, true),
            "legFRu", "body", .1f, -.1f, .3f, wolfLeg(true, true), "legFRl", "legFRu", 0, -.26f, 0, wolfLeg(false, true),
            "legHLu", "body", -.1f, -.1f, -.36f, wolfLeg(true, false), "legHLl", "legHLu", 0, -.26f, 0, wolfLeg(false, false),
            "legHRu", "body", .1f, -.1f, -.36f, wolfLeg(true, false), "legHRl", "legHRu", 0, -.26f, 0, wolfLeg(false, false));

    private static Mesh wing(float s) {
        Mesh m = new Mesh().twoSided();
        int[] tone = {0xeef4f8, 0xd8e8f2, 0xbfd8e8, 0xa8c8dc};
        float[][] tips = {{3.1f, .3f, .2f}, {3.3f, -.3f, -.4f}, {2.7f, -.9f, -.9f}, {1.8f, -1.3f, -1.1f}};
        for (int i = 0; i < tips.length; i++) {
            float[] a = tips[i], b = i + 1 < tips.length ? tips[i + 1] : new float[]{.6f, -1.2f, -.7f};
            m.tri(0, 0, 0, a[0] * s, a[1], a[2], b[0] * s, b[1], b[2], tone[i]);
        }
        return m.tri(0, 0, 0, .8f * s, .4f, .3f, 3.1f * s, .3f, .2f, 0xf4f8fa);
    }

    /** Hoarwing: a vast frost owl that nests in Rimeholt's peak. */
    static final Rig HOARWING = new Rig(
            "body", null, 0, 0, 0, new Mesh().blob(0, 0, 0, 1.05f, 1.35f, .95f, 4, 12, 0xe8f0f6, .04f).blob(0, -.1f, .45f, .78f, .98f, .55f, 3, 10, 0xc8dce8, 0)
                    .blob(0, -.05f, .93f, .26f, .3f, .1f, 2, 6, 0x9fe8ff | Mesh.GLOW, 0)
                    .prism(.35f, -1.45f, .2f, .09f, 0, .35f, 4, 0x3a3a44).prism(-.35f, -1.45f, .2f, .09f, 0, .35f, 4, 0x3a3a44).smooth(),
            "head", "body", 0, 1.25f, .1f, new Mesh().blob(0, .35f, 0, .82f, .72f, .78f, 4, 12, 0xf0f4f8, .03f)
                    .blob(.3f, .42f, .6f, .19f, .19f, .09f, 2, 8, 0xbff4ff | Mesh.GLOW, 0).blob(-.3f, .42f, .6f, .19f, .19f, .09f, 2, 8, 0xbff4ff | Mesh.GLOW, 0)
                    .blob(.3f, .42f, .68f, .07f, .07f, .03f, 2, 6, 0x0a1a2a, 0).blob(-.3f, .42f, .68f, .07f, .07f, .03f, 2, 6, 0x0a1a2a, 0)
                    .blob(0, .2f, .78f, .08f, .14f, .1f, 2, 4, 0xd8b44a, 0)
                    .prism(.48f, .85f, 0, .14f, 0, .45f, 4, 0xe8f0f6).prism(-.48f, .85f, 0, .14f, 0, .45f, 4, 0xe8f0f6).smooth(),
            "wingL", "body", -.85f, .55f, 0, wing(-1), "wingR", "body", .85f, .55f, 0, wing(1),
            "tail", "body", 0, -1.05f, -.7f, new Mesh().twoSided().tri(0, 0, 0, -.7f, -.9f, -.8f, .7f, -.9f, -.8f, 0xd8e8f2)
                    .tri(0, 0, 0, .7f, -.9f, -.8f, 0, -1.1f, -.9f, 0xbfd8e8));
    // ---- wildlife -------------------------------------------------------------------------------

    /** A four-legged beast with the parts Life's trot drives: body, head, tail, and upper and lower legs. */
    private static Rig quad(float hip, Mesh body, Mesh head, float hy, float hz, Mesh tail, float tz,
                            float lx, float front, float hind, float len, float rad, int col, int foot) {
        Mesh up = new Mesh().blob(0, 0, 0, rad * 1.7f, rad * 1.9f, rad * 2, 2, 6, col, 0).tube(0, -len, 0, rad * .9f, rad, rad * 1.3f, rad * 1.4f, len, 6, col).smooth();
        Mesh lo = new Mesh().tube(0, -len, 0, rad * .6f, rad * .7f, rad * .85f, rad * .9f, len, 6, col).box(0, -len - .015f, .01f, rad * .9f, .02f, rad * 1.2f, foot).smooth();
        return new Rig("body", null, 0, hip, 0, body, "head", "body", 0, hy, hz, head, "tail", "body", 0, .04f, tz, tail,
                "legFLu", "body", -lx, -.05f, front, up, "legFLl", "legFLu", 0, -len, 0, lo,
                "legFRu", "body", lx, -.05f, front, up, "legFRl", "legFRu", 0, -len, 0, lo,
                "legHLu", "body", -lx, -.05f, hind, up, "legHLl", "legHLu", 0, -len, 0, lo,
                "legHRu", "body", lx, -.05f, hind, up, "legHRl", "legHRu", 0, -len, 0, lo);
    }

    static final Rig DEER = quad(.93f,
            new Mesh().blob(0, 0, -.02f, .2f, .24f, .5f, 3, 10, 0xa8743c, .04f).blob(0, -.09f, .02f, .16f, .13f, .42f, 3, 8, 0xe0cfae, 0).smooth(),
            new Mesh().segment(0, 0, 0, 0, .42f, .16f, .1f, .07f, 6, 0xa8743c).blob(0, .47f, .23f, .085f, .095f, .14f, 2, 8, 0xa8743c, 0)
                    .blob(0, .43f, .37f, .045f, .045f, .06f, 2, 6, 0x3a2a20, 0)
                    .prism(.07f, .52f, .18f, .035f, 0, .1f, 4, 0x8a5a30).prism(-.07f, .52f, .18f, .035f, 0, .1f, 4, 0x8a5a30)
                    .segment(.04f, .55f, .2f, .15f, .8f, .14f, .018f, .01f, 4, 0xd8c8a0).segment(-.04f, .55f, .2f, -.15f, .8f, .14f, .018f, .01f, 4, 0xd8c8a0)
                    .segment(.11f, .7f, .16f, .14f, .82f, .28f, .012f, .008f, 4, 0xd8c8a0).segment(-.11f, .7f, .16f, -.14f, .82f, .28f, .012f, .008f, 4, 0xd8c8a0).smooth(),
            .1f, .44f, new Mesh().blob(0, -.06f, -.02f, .05f, .08f, .04f, 2, 6, 0xf4f0e8, 0), -.5f,
            .1f, .34f, -.36f, .42f, .04f, 0x8a5a30, 0x2a2018);

    private static Rig fox(int fur, int belly, int legs, int tip) {
        return quad(.36f,
                new Mesh().blob(0, 0, -.02f, .1f, .11f, .27f, 3, 8, fur, .03f).blob(0, -.04f, .07f, .08f, .07f, .19f, 2, 8, belly, 0).smooth(),
                new Mesh().blob(0, .02f, .06f, .08f, .075f, .085f, 2, 8, fur, 0).blob(0, -.005f, .16f, .036f, .032f, .07f, 2, 6, belly, 0)
                        .box(0, .005f, .232f, .014f, .012f, .01f, 0x111111).prism(.045f, .07f, .03f, .03f, 0, .075f, 4, legs).prism(-.045f, .07f, .03f, .03f, 0, .075f, 4, legs)
                        .box(.035f, .035f, .135f, .012f, .008f, .005f, 0x111111).box(-.035f, .035f, .135f, .012f, .008f, .005f, 0x111111).smooth(),
                .06f, .27f, new Mesh().blob(0, -.03f, -.17f, .06f, .065f, .19f, 2, 8, fur, .05f).blob(0, -.06f, -.34f, .04f, .04f, .06f, 2, 6, tip, 0).smooth(), -.26f,
                .055f, .17f, -.19f, .14f, .022f, legs, 0x1a1210);
    }

    static final Rig FOX = fox(0xd8642a, 0xf4ece0, 0x3a2418, 0xf4f0e8), SNOWFOX = fox(0xeef0f2, 0xffffff, 0xaab0ba, 0xffffff);

    private static Mesh rabbit(int fur, int belly) {
        return new Mesh().blob(0, .13f, -.03f, .1f, .1f, .14f, 2, 8, fur, .03f).blob(0, .2f, .1f, .065f, .065f, .075f, 2, 8, fur, 0)
                .blob(.025f, .31f, .07f, .018f, .075f, .012f, 2, 5, fur, 0).blob(-.025f, .31f, .07f, .018f, .075f, .012f, 2, 5, fur, 0)
                .blob(0, .15f, -.17f, .038f, .038f, .035f, 2, 5, belly, 0).blob(0, .09f, .05f, .07f, .05f, .08f, 2, 6, belly, 0)
                .box(.04f, .215f, .155f, .01f, .01f, .006f, 0x111111).box(-.04f, .215f, .155f, .01f, .01f, .006f, 0x111111).smooth();
    }

    static final Mesh RABBIT = rabbit(0x9a8468, 0xf0ece4), HARE = rabbit(0xf2f4f6, 0xffffff);
    static final Mesh FISH = new Mesh().blob(0, 0, 0, .055f, .09f, .25f, 2, 8, 0x9ab4c8, 0).twoSided().tri(0, 0, -.22f, 0, .11f, -.38f, 0, -.11f, -.38f, 0x7a94a8);
    static final Mesh BIRD = new Mesh().blob(0, .07f, 0, .05f, .05f, .08f, 2, 6, 0x5a4a3a, 0).blob(0, .11f, .07f, .035f, .035f, .035f, 2, 5, 0x6a5a48, 0)
            .box(0, .105f, .11f, .008f, .008f, .02f, 0xd8a83a).box(0, .07f, -.1f, .025f, .008f, .05f, 0x4a3a2a).smooth();
    // ---- the desert and the storm --------------------------------------------------------------

    private static final int SAND_C = 0xb07a3a, SAND_D = 0x8a5a28;

    /** Dune Stalker: a sand-coloured scorpion that swims under the dunes and strikes with a glowing tail. */
    static final Rig STALKER = new Rig(
            "body", null, 0, .32f, 0, new Mesh().blob(0, 0, 0, .3f, .15f, .42f, 2, 8, SAND_C, .05f).blob(0, .02f, .38f, .2f, .12f, .14f, 2, 6, SAND_D, 0)
                    .box(.08f, .08f, .5f, .025f, .02f, .01f, 0x111111).box(-.08f, .08f, .5f, .025f, .02f, .01f, 0x111111)
                    .segment(.25f, -.04f, .22f, .55f, -.32f, .36f, .03f, .02f, 4, SAND_D).segment(-.25f, -.04f, .22f, -.55f, -.32f, .36f, .03f, .02f, 4, SAND_D)
                    .segment(.27f, -.04f, 0, .6f, -.32f, 0, .03f, .02f, 4, SAND_D).segment(-.27f, -.04f, 0, -.6f, -.32f, 0, .03f, .02f, 4, SAND_D)
                    .segment(.25f, -.04f, -.22f, .55f, -.32f, -.36f, .03f, .02f, 4, SAND_D).segment(-.25f, -.04f, -.22f, -.55f, -.32f, -.36f, .03f, .02f, 4, SAND_D).smooth(),
            "tail", "body", 0, .08f, -.38f, new Mesh().segment(0, 0, 0, 0, .35f, -.25f, .07f, .06f, 6, SAND_C).segment(0, .35f, -.25f, 0, .7f, -.05f, .06f, .05f, 6, SAND_C)
                    .segment(0, .7f, -.05f, 0, .72f, .25f, .05f, .025f, 6, SAND_C).blob(0, .7f, .3f, .06f, .06f, .08f, 2, 6, 0x8af04a | Mesh.GLOW, 0).smooth(),
            "clawL", "body", -.24f, 0, .42f, new Mesh().segment(0, 0, 0, -.1f, 0, .3f, .045f, .045f, 5, SAND_D).blob(-.12f, 0, .42f, .1f, .06f, .12f, 2, 6, SAND_D, 0).smooth(),
            "clawR", "body", .24f, 0, .42f, new Mesh().segment(0, 0, 0, .1f, 0, .3f, .045f, .045f, 5, SAND_D).blob(.12f, 0, .42f, .1f, .06f, .12f, 2, 6, SAND_D, 0).smooth());

    /** Ashmaw, the sand wyrm of the Sunscour: a head of plated chitin and a long ringed body. */
    static final Mesh ASHMAW_HEAD = new Mesh().blob(0, 0, 0, 1.05f, .85f, 1.3f, 3, 10, 0xb08a52, .06f).blob(0, .35f, -.1f, .9f, .55f, 1.05f, 2, 8, 0x8a6a3a, .08f)
            .blob(.45f, .25f, .75f, .14f, .12f, .1f, 2, 6, 0xffb84a | Mesh.GLOW, 0).blob(-.45f, .25f, .75f, .14f, .12f, .1f, 2, 6, 0xffb84a | Mesh.GLOW, 0)
            .segment(.5f, -.35f, .9f, .25f, -.25f, 1.7f, .14f, .03f, 5, 0xe8dcc0).segment(-.5f, -.35f, .9f, -.25f, -.25f, 1.7f, .14f, .03f, 5, 0xe8dcc0)
            .blob(0, -.15f, 1, .5f, .35f, .25f, 2, 8, 0x3a1a0a, 0).smooth();
    static final Mesh ASHMAW_SEG = new Mesh().blob(0, 0, 0, .85f, .75f, .7f, 3, 10, 0xb08a52, .06f).prism(0, .55f, 0, .25f, 0, .45f, 4, 0x8a6a3a).smooth();

    private static Mesh drakeWing(float s) {
        Mesh m = new Mesh().twoSided();
        int[] tone = {0x34406a, 0x2a3458, 0x222a48};
        float[][] tips = {{4.6f, .6f, .3f}, {4.2f, -.4f, -1.4f}, {2.6f, -.6f, -2.2f}, {1, -.3f, -1.6f}};
        for (int i = 0; i + 1 < tips.length; i++) m.tri(0, 0, 0, tips[i][0] * s, tips[i][1], tips[i][2], tips[i + 1][0] * s, tips[i + 1][1], tips[i + 1][2], tone[i]);
        return m.segment(0, 0, 0, 2.4f * s, .5f, .4f, .09f, .06f, 4, 0x1e2440).segment(2.4f * s, .5f, .4f, 4.6f * s, .6f, .3f, .06f, .02f, 4, 0x1e2440);
    }

    /** The Stormdrake: a slate-blue drake that rides the storms over the mountain, lightning caught in its scales. */
    static final Rig STORMDRAKE = new Rig(
            "body", null, 0, 0, 0, new Mesh().blob(0, 0, 0, .9f, .85f, 2, 3, 12, 0x2e3a5a, .05f).blob(0, -.35f, .2f, .7f, .55f, 1.6f, 3, 10, 0x5a6a8a, 0)
                    .prism(0, .7f, .9f, .22f, 0, .55f, 4, 0x1e2440).prism(0, .7f, 0, .22f, 0, .5f, 4, 0x1e2440).prism(0, .65f, -.9f, .2f, 0, .45f, 4, 0x1e2440).smooth(),
            "neck", "body", 0, .3f, 1.8f, new Mesh().segment(0, 0, 0, 0, .6f, 1.4f, .5f, .38f, 8, 0x2e3a5a).smooth(),
            "head", "neck", 0, .65f, 1.45f, new Mesh().blob(0, 0, .35f, .5f, .45f, .8f, 2, 10, 0x2e3a5a, .04f).blob(0, -.15f, .95f, .32f, .22f, .45f, 2, 8, 0x5a6a8a, 0)
                    .blob(.28f, .15f, .7f, .08f, .06f, .06f, 2, 6, 0xffe14a | Mesh.GLOW, 0).blob(-.28f, .15f, .7f, .08f, .06f, .06f, 2, 6, 0xffe14a | Mesh.GLOW, 0)
                    .segment(.25f, .3f, .1f, .5f, .75f, -.55f, .09f, .02f, 4, 0xd8d0b8).segment(-.25f, .3f, .1f, -.5f, .75f, -.55f, .09f, .02f, 4, 0xd8d0b8).smooth(),
            "tail", "body", 0, 0, -1.9f, new Mesh().segment(0, 0, 0, 0, -.2f, -2.2f, .5f, .3f, 8, 0x2e3a5a).segment(0, -.2f, -2.2f, 0, -.1f, -4.2f, .3f, .08f, 8, 0x2e3a5a)
                    .prism(0, -.1f, -4.3f, .35f, 0, .6f, 4, 0xffe14a | Mesh.GLOW).smooth(),
            "wingL", "body", -.75f, .45f, .4f, drakeWing(-1), "wingR", "body", .75f, .45f, .4f, drakeWing(1));

    static final Mesh ICICLE = new Mesh().prism(0, -1.2f, 0, .02f, .22f, 1.2f, 5, 0xbfe8ff | Mesh.GLOW);

    // ---- spells -------------------------------------------------------------------------------

    static final Mesh EMBER_BOLT = new Mesh().blob(0, 0, 0, .28f, .28f, .4f, 2, 6, 0xff8a2a | Mesh.GLOW, .3f).blob(0, 0, 0, .16f, .16f, .2f, 2, 5, 0xffe08a | Mesh.GLOW, 0);
    static final Mesh FROST_LANCE = new Mesh().blob(0, 0, 0, .14f, .14f, .9f, 2, 6, 0xbff0ff | Mesh.GLOW, 0).blob(0, 0, -.2f, .22f, .06f, .3f, 2, 4, 0x7ad8ff | Mesh.GLOW, 0);
    static final Mesh RADIANT = new Mesh().twoSided().tri(-1.4f, 0, 0, 0, .25f, .5f, 0, 0, .15f, 0xfff0b0 | Mesh.GLOW)
            .tri(1.4f, 0, 0, 0, 0, .15f, 0, .25f, .5f, 0xfff0b0 | Mesh.GLOW).tri(-1.4f, 0, 0, 1.4f, 0, 0, 0, .25f, .5f, 0xffd870 | Mesh.GLOW);
    static final Mesh STAR = new Mesh().blob(0, 0, 0, .7f, .7f, .7f, 2, 6, 0xfff4c8 | Mesh.GLOW, .25f).blob(0, .9f, 0, .35f, 1.2f, .35f, 2, 5, 0xffc860 | Mesh.GLOW, 0);
    static final Mesh DRAUGHT = new Mesh().blob(0, .12f, 0, .07f, .09f, .07f, 2, 6, 0xff7a4a | Mesh.GLOW, 0).box(0, .25f, 0, .025f, .04f, .025f, 0x8a6a4a);

    // ---- villagers ----------------------------------------------------------------------------

    static final Rig ELDER = humanoid(0xc89a74, 0xdedede, 0x5a4a7a, 0x5a4a7a, LEATHER,
            new Mesh().box(0, .02f, .1f, .1f, .09f, .04f, 0xe8e8e8), new Mesh().box(0, -.05f, 0, .22f, .12f, .14f, 0x5a4a7a), false, 0);
    static final Rig SMITH = humanoid(0xb07850, 0x2a1a10, 0x6a6a72, 0x3a3440, 0x2a2420,
            new Mesh().box(0, .04f, .1f, .1f, .05f, .04f, 0x2a1a10), new Mesh().box(0, .2f, .125f, .17f, .26f, .01f, 0x5a3b26), false, 0);
    static final Rig MERCHANT = humanoid(0xe0b090, 0x8a4a20, 0x3a7a5a, 0x5a4a3a, LEATHER,
            new Mesh().prism(0, .28f, 0, .2f, .12f, .12f, 8, 0xc8a050), null, false, 0);
    static final Rig FISHER = humanoid(0xc08a64, 0x6a6a6a, 0x3a5a8a, 0x4a4a4a, 0x2a2a2a,
            new Mesh().prism(0, .26f, 0, .26f, .1f, .08f, 8, 0xd8c070), null, false, 0);
    // The hero's outfits: the same body and skeleton in different clothes, so every animation fits them all.
    static final Rig HERO_FURS = humanoid(SKIN, 0x3b2a20, 0x7a6248, 0x4a4238, 0x2a2420,
            new Mesh().blob(0, .085f, -.06f, .165f, .165f, .15f, 2, 8, 0xd8d2c4, 0),
            new Mesh().tube(0, .36f, 0, .19f, .14f, .15f, .11f, .12f, 12, 0xd8d2c4).box(0, .2f, 0, .205f, .015f, .13f, 0x4a3424)
                    .blob(-.25f, .42f, 0, .1f, .07f, .1f, 2, 8, 0xd8d2c4, 0).blob(.25f, .42f, 0, .1f, .07f, .1f, 2, 8, 0xd8d2c4, 0), true, 0x8a7a62);
    static final Rig HERO_WRAPS = humanoid(SKIN, 0x3b2a20, 0xd8c8a0, 0x8a6a4a, 0x6a4a30,
            new Mesh().blob(0, .1f, -.02f, .16f, .13f, .16f, 2, 8, 0xe8dcc0, 0).box(0, -.02f, .135f, .11f, .04f, .02f, 0xe8dcc0),
            new Mesh().box(0, .2f, 0, .205f, .03f, .13f, 0xc8643a).blob(.1f, .1f, .1f, .05f, .12f, .04f, 2, 6, 0xc8643a, 0), true, 0xd8b884);
    static final Rig HERO_PLATE = humanoid(SKIN, 0x3b2a20, 0x8a8e96, 0x5a5e66, 0x3a3e46,
            new Mesh().blob(0, .07f, -.01f, .155f, .15f, .16f, 2, 10, 0x9aa0aa, 0).box(0, .04f, .145f, .1f, .015f, .01f, 0x1a1a1a)
                    .prism(0, .2f, -.02f, .03f, 0, .1f, 4, 0x7a1e1e),
            new Mesh().blob(0, .27f, .04f, .19f, .19f, .14f, 2, 10, 0xa8aeb8, 0).blob(-.25f, .42f, 0, .12f, .08f, .12f, 2, 8, 0x9aa0aa, 0)
                    .blob(.25f, .42f, 0, .12f, .08f, .12f, 2, 8, 0x9aa0aa, 0).box(0, .2f, 0, .205f, .02f, .13f, 0x4a3424), true, 0x7a1e1e);
    static final Rig HERO_STARWEAVE = humanoid(SKIN, 0x3b2a20, 0x2a2e6a, 0x2a2e6a, 0x1a1a3a,
            new Mesh().prism(0, .13f, -.01f, .23f, .23f, .015f, 12, 0x2a2e6a).prism(0, .14f, -.01f, .17f, .02f, .36f, 8, 0x2a2e6a)
                    .box(.04f, .3f, .04f, .02f, .02f, .02f, 0xffe9a0 | Mesh.GLOW),
            new Mesh().box(0, .2f, 0, .205f, .02f, .13f, 0xd8a83a).box(.08f, .3f, .128f, .012f, .012f, .004f, 0xffe9a0 | Mesh.GLOW)
                    .box(-.06f, .14f, .128f, .012f, .012f, .004f, 0xffe9a0 | Mesh.GLOW).box(.02f, .38f, .128f, .01f, .01f, .004f, 0xbfe8ff | Mesh.GLOW), true, 0x3a3e8a);
    static final Rig HERO_REGALIA = humanoid(SKIN, 0x3b2a20, 0x2a1e2e, 0x1a141e, 0x111111,
            new Mesh().prism(0, .14f, 0, .14f, .13f, .05f, 8, 0x6a5a7a).prism(.1f, .19f, 0, .025f, 0, .14f, 4, 0x6a5a7a).prism(-.1f, .19f, 0, .025f, 0, .14f, 4, 0x6a5a7a)
                    .prism(0, .19f, .08f, .025f, 0, .12f, 4, 0x6a5a7a).box(0, .16f, .135f, .02f, .02f, .01f, 0xc87aff | Mesh.GLOW),
            new Mesh().blob(0, .27f, .04f, .19f, .19f, .14f, 2, 10, 0x2a1e2e, 0).box(0, .27f, .175f, .012f, .14f, .005f, 0xc87aff | Mesh.GLOW)
                    .blob(-.25f, .42f, 0, .12f, .08f, .12f, 2, 8, 0x3a2a3e, 0).blob(.25f, .42f, 0, .12f, .08f, .12f, 2, 8, 0x3a2a3e, 0), true, 0x3a1a4a);

    static final Rig RANGER = humanoid(0xd0a07c, 0x3a2a1e, 0x4a5a4a, 0x3a3a34, 0x2a2420,
            new Mesh().blob(0, .09f, -.07f, .16f, .16f, .14f, 2, 8, 0xd8d2c4, 0), new Mesh().box(0, .1f, 0, .2f, .2f, .14f, 0x6a5a44), true, 0x5a6a5a);
    static final Rig NOMAD = humanoid(0xa87a54, 0x1a120c, 0xd8c8a0, 0x8a6a4a, 0x5a4030,
            new Mesh().blob(0, .1f, -.03f, .17f, .14f, .16f, 2, 8, 0xe8dcc0, 0).box(0, .02f, .135f, .1f, .03f, .02f, 0xc8643a),
            new Mesh().box(0, .02f, 0, .21f, .05f, .15f, 0xc8643a), true, 0xd8b884);
    static final Rig CHILD = humanoid(0xf0c098, 0xc8782a, 0xd8a83a, 0x4a6a9a, LEATHER, null, null, false, 0);

    // ---- townsfolk and the royal guard ----------------------------------------------------------

    /** Everyday islanders for the towns: the same body in ten sets of clothes, some with hats, caps or aprons. */
    static final Rig[] FOLK = new Rig[10];

    static {
        int[] skins = {0xe0b090, 0xc89a74, 0xa87a54, 0xf0c8a0, 0x8a5a3a};
        int[] hairs = {0x2a1a10, 0x8a4a20, 0xd8b060, 0x5a3a24, 0x1a120c, 0xa8a8a8};
        int[] tops = {0x8a3a3a, 0x3a6a8a, 0x5a7a3a, 0xc8a050, 0x6a4a7a, 0xd8d0c0, 0x3a5a5a, 0xa86a3a, 0x7a2a4a, 0x4a4a6a};
        int[] bottoms = {0x4a3a2a, 0x3a3a48, 0x5a4a3a, 0x2a3a4a, 0x6a5a48};
        for (int i = 0; i < FOLK.length; i++) {
            Mesh hat = i % 3 == 0 ? new Mesh().prism(0, .27f, 0, .2f, .13f, .1f, 8, M.scale(tops[i], .7f))
                    : i % 3 == 2 ? new Mesh().prism(0, .22f, -.02f, .15f, .14f, .07f, 8, 0xe8e0d0) : null;
            Mesh apron = i % 4 == 1 ? new Mesh().box(0, -.02f, .13f, .15f, .2f, .01f, 0xe8e0d0) : null;
            FOLK[i] = humanoid(skins[i % skins.length], hairs[i * 7 % hairs.length], tops[i], bottoms[i * 3 % bottoms.length], i % 2 == 0 ? LEATHER : 0x2a2420,
                    hat, apron, false, 0);
        }
    }

    /** Outlaws: a dark red hood, a scarf across the face, and road-worn leathers. */
    static final Rig BANDIT = humanoid(0xc08a64, 0x1a120c, 0x5a3a2a, 0x3a3028, 0x2a2018,
            new Mesh().blob(0, .19f, -.03f, .145f, .14f, .155f, 3, 10, 0x6a1e1e, 0).box(0, .1f, .12f, .1f, .045f, .03f, 0x3a2a24),
            new Mesh().box(0, .3f, 0, .19f, .05f, .145f, 0x6a1e1e), false, 0);

    /** The royal guard: a steel helm, a blue tabard with a gold band over a breastplate, and a crimson cape. */
    static final Rig GUARD = humanoid(0xd0a07c, 0x3a2a1e, 0x2a4a9a, 0x3a3a48, 0x2a2420,
            new Mesh().blob(0, .2f, -.01f, .14f, .12f, .15f, 3, 10, 0xa8acb4, 0).prism(0, .3f, 0, .02f, .02f, .1f, 4, 0xc8a050),
            new Mesh().box(0, .18f, .13f, .1f, .2f, .012f, 0xc8a050).box(0, .3f, 0, .19f, .1f, .14f, 0x9a9ea8), true, 0x9a2a2a);

    // ---- monsters -----------------------------------------------------------------------------

    static final Rig THORNLING = humanoid(0x5d3a5e, 0x3a2a3c, 0x5d3a5e, 0x3a2a3c, 0x2a1e2a,
            new Mesh().vary(.1f).prism(0, .25f, 0, .06f, 0, .25f, 4, 0xa8b46a).prism(.1f, .22f, -.05f, .05f, 0, .2f, 4, 0xa8b46a)
                    .prism(-.1f, .22f, -.05f, .05f, 0, .2f, 4, 0xa8b46a).box(.05f, .15f, .132f, .03f, .025f, .006f, 0xffe14a | Mesh.GLOW)
                    .box(-.05f, .15f, .132f, .03f, .025f, .006f, 0xffe14a | Mesh.GLOW),
            new Mesh().prism(0, .48f, -.1f, .05f, 0, .22f, 4, 0xa8b46a), false, 0);
    static final Mesh CLUB = new Mesh().box(0, 0, .3f, .04f, .04f, .3f, WOOD).box(0, 0, .58f, .07f, .07f, .1f, 0x5a3c28);
    static final Rig.Clip SMASH = new Rig.Clip(THORNLING, false, 0, "armUR:-1.2 torso:0", .25f, "armUR:-3 armLR:-.5 torso:-.2",
            .35f, "armUR:-.6 armLR:-.1 torso:.4", .7f, "armUR:-.4 torso:.1");
    static final Rig SENTRY = humanoid(0x3a3a3a, 0x2a2a2e, 0x6a6e78, 0x4a4e58, 0x2a2a2e,
            new Mesh().box(0, .16f, 0, .15f, .17f, .15f, 0x7a7e88).box(0, .16f, .15f, .1f, .025f, .01f, 0xff6a2a | Mesh.GLOW)
                    .prism(0, .32f, 0, .04f, 0, .2f, 4, 0xa83a2a),
            new Mesh().box(0, .3f, 0, .23f, .2f, .15f, 0x7a7e88), false, 0);
    static final Mesh SPEAR = new Mesh().box(0, 0, .5f, .025f, .025f, .9f, 0x4a3a2a).prism(0, -.0f, 1.35f, .07f, 0, .02f, 4, STEEL);
    static final Mesh TOWER_SHIELD = new Mesh().box(0, 0, 0, .3f, .45f, .04f, 0x5a5e68).box(0, 0, .045f, .06f, .3f, .01f, 0xa83a2a);
    static final Rig.Clip THRUST = new Rig.Clip(SENTRY, false, 0, "armUR:-1.2,0 armLR:-.9 torso:-.1", .4f, "armUR:-1.1,.2 armLR:-1.5 torso:-.25 root:0,0,-.2",
            .5f, "armUR:-1.5 armLR:0 torso:.3 legUL:-.6 legLL:.3 root:-.1,0,0", .9f, "armUR:-1.3 armLR:-.6 torso:.1");
    static final Rig IMP = humanoid(0xc8402a, 0x2a1010, 0xa8302a, 0x5a1a14, 0x2a1010,
            new Mesh().prism(.08f, .27f, 0, .04f, 0, .16f, 4, 0xe8d8b0).prism(-.08f, .27f, 0, .04f, 0, .16f, 4, 0xe8d8b0)
                    .box(.05f, .15f, .132f, .03f, .02f, .006f, 0xffe14a | Mesh.GLOW).box(-.05f, .15f, .132f, .03f, .02f, .006f, 0xffe14a | Mesh.GLOW),
            null, false, 0);
    static final Rig.Clip HURL = new Rig.Clip(IMP, false, 0, "armUR:-1 torso:0", .35f, "armUR:-2.9 armLR:-.8 torso:-.25", .5f, "armUR:-1 armLR:0 torso:.3", .8f, "armUR:-.3");
    static final Rig BAT = new Rig("body", null, 0, 0, 0, new Mesh().blob(0, 0, 0, .22f, .2f, .25f, 2, 6, 0x2a2238, .1f)
            .box(.07f, .05f, .2f, .03f, .03f, .01f, 0xff4a6a | Mesh.GLOW).box(-.07f, .05f, .2f, .03f, .03f, .01f, 0xff4a6a | Mesh.GLOW),
            "wingL", "body", -.15f, 0, 0, new Mesh().twoSided().tri(0, 0, .15f, -.7f, .05f, -.05f, 0, 0, -.15f, 0x3a2e4a),
            "wingR", "body", .15f, 0, 0, new Mesh().twoSided().tri(0, 0, .15f, 0, 0, -.15f, .7f, .05f, -.05f, 0x3a2e4a));
    static final Mesh SLIME = new Mesh().vary(.05f).blob(0, .5f, 0, .6f, .5f, .6f, 3, 8, 0x4a9a5a, .08f)
            .box(.18f, .62f, .5f, .07f, .09f, .03f, 0x10201a).box(-.18f, .62f, .5f, .07f, .09f, .03f, 0x10201a);

    // ---- bosses -------------------------------------------------------------------------------

    /** Bramblemaw: a rooted carnivorous plant with two lashing vines and glowing seed-bulbs. */
    static final Rig BRAMBLE = new Rig(
            "stem", null, 0, 0, 0, new Mesh().vary(.08f).prism(0, 0, 0, 1.6f, 1.1f, 3, 7, 0x3d6a2e, .2f),
            "neck", "stem", 0, 3, 0, new Mesh().vary(.08f).prism(0, 0, 0, 1.1f, .9f, 2.5f, 7, 0x4a7a34, .2f),
            "head", "neck", 0, 2.5f, 0, new Mesh().vary(.1f).blob(0, .9f, .4f, 1.9f, 1.4f, 1.9f, 3, 8, 0x7a2a4a, .15f)
                    .blob(0, .5f, 1.7f, 1.2f, .5f, .5f, 2, 6, 0x2a0a14, 0).blob(1.6f, 1.6f, 0, .45f, .45f, .45f, 2, 6, 0xffd166 | Mesh.GLOW, 0)
                    .blob(-1.6f, 1.6f, 0, .45f, .45f, .45f, 2, 6, 0xffd166 | Mesh.GLOW, 0)
                    .prism(.8f, .2f, 1.9f, .15f, 0, .5f, 4, 0xf0e8d8).prism(-.8f, .2f, 1.9f, .15f, 0, .5f, 4, 0xf0e8d8)
                    .prism(0, .2f, 2.1f, .15f, 0, .5f, 4, 0xf0e8d8),
            "vineL1", "stem", -1.4f, 1.5f, 0, new Mesh().prism(0, 0, 0, .35f, .3f, 2.4f, 5, 0x3d6a2e),
            "vineL2", "vineL1", 0, 2.4f, 0, new Mesh().prism(0, 0, 0, .3f, .1f, 2.4f, 5, 0x4a7a34).prism(0, 1.2f, 0, .1f, 0, .4f, 4, 0xa8b46a),
            "vineR1", "stem", 1.4f, 1.5f, 0, new Mesh().prism(0, 0, 0, .35f, .3f, 2.4f, 5, 0x3d6a2e),
            "vineR2", "vineR1", 0, 2.4f, 0, new Mesh().prism(0, 0, 0, .3f, .1f, 2.4f, 5, 0x4a7a34).prism(0, 1.2f, 0, .1f, 0, .4f, 4, 0xa8b46a));

    /** Cinder Golem: a hulking forge-guardian; bombing its fist cracks its armour off. */
    static final Rig GOLEM = new Rig(
            "hips", null, 0, 2.2f, 0, new Mesh().vary(.1f).blob(0, 0, 0, 1.1f, .7f, .8f, 2, 6, 0x3a3230, .2f),
            "torso", "hips", 0, .4f, 0, new Mesh().vary(.1f).blob(0, 1.3f, 0, 1.8f, 1.4f, 1.3f, 3, 7, 0x4a403c, .2f)
                    .blob(0, 1.4f, 1.05f, .55f, .55f, .35f, 2, 6, 0xff6a1e | Mesh.GLOW, 0),
            "head", "torso", 0, 2.5f, .3f, new Mesh().vary(.1f).blob(0, .3f, 0, .7f, .55f, .7f, 2, 6, 0x3a3230, .2f)
                    .box(0, .35f, .62f, .4f, .08f, .06f, 0xffb347 | Mesh.GLOW),
            "armUL", "torso", -2, 2, 0, new Mesh().vary(.1f).blob(0, -.6f, 0, .55f, .8f, .55f, 2, 6, 0x3a3230, .2f),
            "armLL", "armUL", 0, -1.4f, 0, new Mesh().vary(.1f).blob(0, -.7f, 0, .75f, .8f, .75f, 3, 6, 0x2e2826, .25f),
            "armUR", "torso", 2, 2, 0, new Mesh().vary(.1f).blob(0, -.6f, 0, .55f, .8f, .55f, 2, 6, 0x3a3230, .2f),
            "armLR", "armUR", 0, -1.4f, 0, new Mesh().vary(.1f).blob(0, -.7f, 0, .75f, .8f, .75f, 3, 6, 0x2e2826, .25f),
            "legUL", "hips", -.7f, -.2f, 0, new Mesh().box(0, -.5f, 0, .4f, .5f, .4f, 0x3a3230),
            "legLL", "legUL", 0, -1, 0, new Mesh().box(0, -.4f, .1f, .45f, .4f, .55f, 0x2e2826),
            "legUR", "hips", .7f, -.2f, 0, new Mesh().box(0, -.5f, 0, .4f, .5f, .4f, 0x3a3230),
            "legLR", "legUR", 0, -1, 0, new Mesh().box(0, -.4f, .1f, .45f, .4f, .55f, 0x2e2826));
    static final Mesh GOLEM_ARMOUR = new Mesh().vary(.1f).blob(0, 1.4f, 1.0f, .9f, .8f, .45f, 2, 6, 0x5a504a, .15f);

    /** Tide Serpent segments: a barnacled head and body rings. */
    static final Mesh SERPENT_HEAD = new Mesh().vary(.08f).blob(0, 0, .4f, 1.1f, .8f, 1.5f, 3, 8, 0x2a5a6a, .12f)
            .box(.55f, .35f, 1.3f, .14f, .1f, .05f, 0xffe14a | Mesh.GLOW).box(-.55f, .35f, 1.3f, .14f, .1f, .05f, 0xffe14a | Mesh.GLOW)
            .prism(.5f, .6f, -.2f, .15f, 0, .7f, 4, 0xd8d0b8).prism(-.5f, .6f, -.2f, .15f, 0, .7f, 4, 0xd8d0b8);
    static final Mesh SERPENT_MASK = new Mesh().vary(.08f).box(0, .25f, 1.35f, .9f, .55f, .3f, 0x8a8478).box(0, .25f, 1.66f, .25f, .25f, .02f, 0x5a5e66);
    static final Mesh SERPENT_BODY = new Mesh().vary(.08f).blob(0, 0, 0, .9f, .85f, .9f, 2, 8, 0x2a5a6a, .1f).prism(0, .7f, 0, .2f, 0, .5f, 4, 0x6a9aa0);

    /** The Hollow King: a crowned, robed shade with a lantern for a heart. */
    static final Rig KING = new Rig(
            "body", null, 0, 1.2f, 0, new Mesh().vary(.06f).prism(0, -1.2f, 0, .9f, .45f, 2.2f, 8, 0x1a1626, .1f)
                    .blob(0, .9f, .28f, .28f, .28f, .2f, 2, 6, 0x9fe8ff | Mesh.GLOW, 0),
            "head", "body", 0, 1.1f, 0, new Mesh().blob(0, .25f, 0, .3f, .34f, .3f, 2, 6, 0x0a0812, 0)
                    .box(.1f, .27f, .27f, .05f, .03f, .01f, 0x9fe8ff | Mesh.GLOW).box(-.1f, .27f, .27f, .05f, .03f, .01f, 0x9fe8ff | Mesh.GLOW)
                    .prism(0, .55f, 0, .3f, .34f, .12f, 6, 0xd8b44a).prism(.18f, .67f, 0, .06f, 0, .2f, 3, 0xd8b44a)
                    .prism(-.18f, .67f, 0, .06f, 0, .2f, 3, 0xd8b44a).prism(0, .67f, .18f, .06f, 0, .25f, 3, 0xd8b44a),
            "armUL", "body", -.55f, .95f, 0, new Mesh().box(0, -.4f, 0, .12f, .45f, .12f, 0x1a1626),
            "armLL", "armUL", 0, -.85f, 0, new Mesh().box(0, -.3f, 0, .1f, .35f, .1f, 0x1a1626).box(0, -.7f, 0, .12f, .08f, .12f, 0x9fe8ff | Mesh.GLOW),
            "armUR", "body", .55f, .95f, 0, new Mesh().box(0, -.4f, 0, .12f, .45f, .12f, 0x1a1626),
            "armLR", "armUR", 0, -.85f, 0, new Mesh().box(0, -.3f, 0, .1f, .35f, .1f, 0x1a1626).box(0, -.7f, 0, .12f, .08f, .12f, 0x9fe8ff | Mesh.GLOW));
    static final Mesh SHADOW_HAND = new Mesh().vary(.1f).box(0, 0, 0, 1.6f, .5f, 2, 0x14101e)
            .box(-1.2f, 0, 2.5f, .3f, .35f, .8f, 0x14101e).box(-.4f, 0, 2.7f, .3f, .35f, .9f, 0x14101e)
            .box(.4f, 0, 2.7f, .3f, .35f, .9f, 0x14101e).box(1.2f, 0, 2.4f, .3f, .35f, .7f, 0x14101e)
            .box(1.9f, 0, .6f, .3f, .35f, .7f, 0x14101e).box(0, .5f, 0, .5f, .05f, .5f, 0x9fe8ff | Mesh.GLOW);
}

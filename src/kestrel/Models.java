package kestrel;

/** Every static mesh in the game, built once from primitives. */
final class Models {
    private Models() {}

    private static final int BARK = 0x6b4a32, LEAF = 0x4f8a3a, LEAF2 = 0x6a9e40, PINE_C = 0x2f5e3c, STONE = 0x8a8a86,
            GOLD = 0xf2c14e, WOOD = 0x8a5a2e, PLASTER = 0xf4e6c8, ROOF = 0xa8432a, DARK = 0x14100c;

    static final Mesh[] PROP_NEAR = new Mesh[10], PROP_FAR = new Mesh[10];

    private static Mesh m() { return new Mesh(); }

    private static Mesh rock(int col) { return m().vary(.1f).blob(0, .45f, 0, 1.5f, 1.1f, 1.3f, 3, 5, col, .35f); }

    static {
        PROP_NEAR[Places.TREE] = m().vary(.08f).prism(0, 0, 0, .32f, .22f, 2.8f, 5, BARK)
                .blob(0, 4.3f, 0, 2.5f, 2.0f, 2.5f, 3, 6, LEAF, .25f).blob(1, 3.4f, .7f, 1.6f, 1.4f, 1.6f, 3, 6, LEAF2, .3f);
        PROP_FAR[Places.TREE] = m().prism(0, 0, 0, .3f, .2f, 2.8f, 3, BARK).blob(0, 4.1f, 0, 2.7f, 2.2f, 2.7f, 2, 4, LEAF, 0);
        for (int snow = 0; snow < 2; snow++) {
            int top = snow == 1 ? 0xe8eef4 : PINE_C;
            PROP_NEAR[Places.PINE + snow] = m().vary(.07f).prism(0, 0, 0, .26f, .18f, 2, 5, BARK)
                    .prism(0, 1.6f, 0, 2.4f, 0, 3.6f, 6, PINE_C).prism(0, 3.7f, 0, 1.9f, 0, 3.2f, 6, PINE_C)
                    .prism(0, 5.8f, 0, 1.3f, 0, 2.8f, 6, top);
            PROP_FAR[Places.PINE + snow] = m().prism(0, 1, 0, 2.3f, 0, 7.6f, 4, PINE_C).prism(0, 5.6f, 0, 1.2f, 0, 3f, 4, top);
        }
        PROP_NEAR[Places.BUSH] = PROP_FAR[Places.BUSH] = m().vary(.1f).blob(0, .45f, 0, 1.1f, .7f, 1.1f, 2, 5, 0x4d7f36, .3f);
        PROP_NEAR[Places.ROCK] = PROP_FAR[Places.ROCK] = rock(STONE);
        PROP_NEAR[Places.REDROCK] = PROP_FAR[Places.REDROCK] = rock(0xb06a4c);
        PROP_NEAR[Places.DARKROCK] = PROP_FAR[Places.DARKROCK] = rock(0x3e3735);
        PROP_NEAR[Places.CACTUS] = PROP_FAR[Places.CACTUS] = m().vary(.06f).prism(0, 0, 0, .35f, .3f, 3, 6, 0x4f8a4a)
                .box(.55f, 1.6f, 0, .25f, .12f, .12f, 0x4f8a4a).prism(.75f, 1.6f, 0, .14f, .12f, .9f, 5, 0x4f8a4a);
        PROP_NEAR[Places.DEADTREE] = PROP_FAR[Places.DEADTREE] = m().prism(0, 0, 0, .3f, .12f, 4.5f, 5, 0x3b302c)
                .box(.5f, 3, 0, .5f, .07f, .07f, 0x3b302c).box(-.35f, 3.7f, .2f, .4f, .06f, .06f, 0x3b302c);
        PROP_NEAR[Places.FLOWERS] = PROP_FAR[Places.FLOWERS] = m().blob(0, .15f, 0, .5f, .15f, .5f, 2, 5, 0x5a9a42, .2f)
                .blob(.2f, .32f, .1f, .09f, .06f, .09f, 2, 5, 0xf2d75a, 0).blob(-.2f, .3f, -.15f, .09f, .06f, .09f, 2, 5, 0xf4f1ea, 0)
                .blob(0, .34f, .25f, .09f, .06f, .09f, 2, 5, 0xb98be0, 0);
    }

    // ---- pickups and projectiles ----------------------------------------------------------------

    static final Mesh[] GLEAM = {gem(0x5ef08a), gem(0x5ab4ff), gem(0xff5a6a)};
    static final Mesh HEART = heart(0xe84a4a, .22f), HEART_PIECE = heart(0xf08ab0, .26f), HEART_CONTAINER = heart(0xff4a5a, .45f);
    static final Mesh ARROW_BUNDLE = m().box(-.06f, .1f, 0, .02f, .02f, .4f, BARK).box(.06f, .1f, 0, .02f, .02f, .4f, BARK)
            .box(0, .16f, 0, .02f, .02f, .4f, BARK).box(0, .13f, .4f, .06f, .06f, .04f, 0xe8e8e8);
    static final Mesh ARROW = m().twoSided().box(0, 0, 0, .022f, .022f, .5f, 0xe0c89a).blob(0, 0, .58f, .055f, .04f, .11f, 2, 4, 0xd8dce4, 0)
            .tri(0, 0, -.32f, 0, .1f, -.5f, 0, 0, -.52f, 0xd84a3a).tri(0, 0, -.32f, 0, -.1f, -.5f, 0, 0, -.52f, 0xd84a3a)
            .tri(0, 0, -.32f, .1f, 0, -.5f, 0, 0, -.52f, 0xf4f0e8).tri(0, 0, -.32f, -.1f, 0, -.5f, 0, 0, -.52f, 0xf4f0e8);
    /** A recurve bow: limbs along y, curving back towards the archer (-z) and flicking forward at the tips. */
    static final Mesh BOW = bow();
    static final Mesh BOW_STRING = m().segment(0, .62f, -.075f, 0, -.62f, -.075f, .0045f, .0045f, 4, 0xece4d2);
    static final Mesh QUIVER = m().segment(0, 0, 0, 0, .52f, 0, .07f, .08f, 10, 0x5a3420).segment(0, .05f, 0, 0, .1f, 0, .085f, .085f, 10, 0xd8b44a)
            .segment(0, .4f, 0, 0, .45f, 0, .09f, .09f, 10, 0xd8b44a).segment(.02f, .5f, .02f, .03f, .66f, .03f, .012f, .012f, 4, 0xe0c89a)
            .segment(-.03f, .5f, 0, -.04f, .64f, -.01f, .012f, .012f, 4, 0xe0c89a).segment(0, .5f, -.03f, 0, .67f, -.04f, .012f, .012f, 4, 0xe0c89a)
            .tri(.03f, .6f, .03f, .07f, .72f, .03f, .03f, .7f, .03f, 0xd84a3a).tri(-.04f, .58f, -.01f, -.08f, .7f, -.01f, -.04f, .68f, -.01f, 0xf4f0e8)
            .tri(0, .61f, -.04f, 0, .73f, -.08f, 0, .71f, -.04f, 0xd84a3a).twoSided();
    static final Mesh BEACON = m().prism(0, 0, 0, .9f, .45f, 150, 6, 0xffd870 | Mesh.GLOW);

    private static Mesh bow() {
        Mesh b = m().segment(0, -.11f, 0, 0, .11f, 0, .034f, .034f, 8, 0x3a2418).box(0, .02f, .03f, .012f, .03f, .012f, 0xd8b44a);
        for (int side = -1; side <= 1; side += 2) {
            float py = .09f * side, pz = 0;
            for (int i = 1; i <= 8; i++) {
                float t = i / 8f, y = (.09f + .53f * t) * side, z = -.24f * t * t + .16f * (float) Math.pow(t, 6);
                b.segment(0, py, pz, 0, y, z, .03f - .016f * (t - .125f), .03f - .016f * t, 7, i % 2 == 0 ? 0x7a4a28 : 0x6e4224);
                py = y;
                pz = z;
            }
            b.blob(0, py, pz, .02f, .03f, .02f, 2, 6, 0xe8dcc0, 0);
        }
        return b.smooth();
    }
    static final Mesh VEIL = m().box(0, 2.6f, .5f, 2.3f, 2.6f, .05f, 0x2a1640 | Mesh.GLOW);
    static final Mesh BOMB_BAG =m().blob(0, .25f, 0, .28f, .25f, .28f, 2, 6, 0x6a5a3a, .1f).box(0, .52f, 0, .1f, .06f, .1f, 0x3a2a1a);
    static final Mesh ROCK_SHOT = m().blob(0, 0, 0, .55f, .5f, .55f, 2, 5, 0x6e6a62, .3f);
    static final Mesh FIREBALL = m().blob(0, 0, 0, .35f, .35f, .35f, 2, 5, 0xff7a20 | Mesh.GLOW, .3f);
    static final Mesh SEED = m().blob(0, 0, 0, .3f, .3f, .3f, 2, 5, 0x8ab43a, .2f);
    static final Mesh WATERBALL = m().blob(0, 0, 0, .45f, .45f, .45f, 2, 6, 0x7ad8ff | Mesh.GLOW, .2f);
    static final Mesh LANTERN = m().blob(0, 0, 0, .4f, .4f, .4f, 2, 6, 0x9fe8ff | Mesh.GLOW, .1f);
    static final Mesh FLAME = m().prism(0, 0, 0, .9f, 0, 2.6f, 5, 0xff8a24 | Mesh.GLOW).prism(.2f, 0, .1f, .55f, 0, 3.4f, 4, 0xffd25a | Mesh.GLOW);

    private static Mesh gem(int col) {
        return m().blob(0, .3f, 0, .14f, .26f, .14f, 2, 4, col | Mesh.GLOW, 0);
    }

    private static Mesh heart(int col, float s) {
        return m().blob(-s * .5f, s * .3f, 0, s * .6f, s * .55f, s * .35f, 2, 6, col | Mesh.GLOW, 0)
                .blob(s * .5f, s * .3f, 0, s * .6f, s * .55f, s * .35f, 2, 6, col | Mesh.GLOW, 0)
                .tri(-s * 1.05f, s * .2f, 0, s * 1.05f, s * .2f, 0, 0, -s * 1.1f, 0, col | Mesh.GLOW).twoSided();
    }

    static Mesh stone(int col) {
        return m().blob(0, 0, 0, .45f, .8f, .45f, 2, 4, col | Mesh.GLOW, 0).blob(0, 0, 0, .7f, .12f, .7f, 2, 8, 0xd8b44a, 0);
    }

    // ---- dungeon furniture ----------------------------------------------------------------------

    static final Mesh DOOR = m().vary(.05f).box(0, 2.1f, 0, .98f, 2.1f, .22f, 0x5a5048).box(0, 1.2f, .23f, .92f, .07f, .02f, 0x34343a)
            .box(0, 2.9f, .23f, .92f, .07f, .02f, 0x34343a).box(0, 1.2f, -.23f, .92f, .07f, .02f, 0x34343a).box(0, 2.9f, -.23f, .92f, .07f, .02f, 0x34343a);
    static final Mesh DOOR_LOCKED = m().box(0, 2.1f, 0, .98f, 2.1f, .22f, 0x5a5048).box(0, 1.9f, 0, .5f, .5f, .3f, 0x3a3a40)
            .box(0, 1.9f, 0, .18f, .24f, .34f, GOLD);
    static final Mesh DOOR_BOSS = m().box(0, 2.1f, 0, .98f, 2.1f, .22f, 0x3a1a24).box(0, 2.1f, 0, .8f, .1f, .3f, GOLD)
            .box(0, 2.1f, 0, .1f, 1.8f, .3f, GOLD).blob(0, 2.1f, 0, .45f, .45f, .4f, 2, 6, 0x8a2a3a, 0).box(0, 2.1f, 0, .2f, .2f, .45f, GOLD);
    static final Mesh CRACKED = m().vary(.12f).box(0, 2.6f, 0, 1, 2.6f, .9f, 0x6a5a50).box(-.2f, 2.2f, .91f, .04f, 1.2f, .01f, DARK)
            .box(.25f, 1.6f, .91f, .5f, .04f, .01f, DARK).box(-.2f, 2.2f, -.91f, .04f, 1.2f, .01f, DARK);
    static final Mesh CHEST = m().box(0, .3f, 0, .55f, .3f, .38f, 0x8a5a2e).box(0, .3f, .39f, .12f, .14f, .02f, GOLD);
    static final Mesh BIG_CHEST = m().box(0, .3f, 0, .55f, .3f, .38f, 0x5a2a6a).box(0, .3f, .39f, .14f, .16f, .02f, GOLD)
            .box(.56f, .3f, 0, .02f, .31f, .39f, GOLD).box(-.56f, .3f, 0, .02f, .31f, .39f, GOLD);
    static final Mesh LID = m().box(0, .1f, .38f, .58f, .1f, .4f, 0x9c6a38);
    static final Mesh KEY = m().prism(0, .1f, 0, .14f, .14f, .04f, 8, GOLD | Mesh.GLOW).box(0, -.15f, 0, .03f, .2f, .02f, GOLD | Mesh.GLOW)
            .box(.06f, -.3f, 0, .05f, .03f, .02f, GOLD | Mesh.GLOW);
    static final Mesh BLOCK = m().vary(.05f).box(0, 1, 0, .98f, 1, .98f, 0x7a7266).box(0, 1, .99f, .6f, .6f, .01f, 0x5a544a)
            .box(0, 1, -.99f, .6f, .6f, .01f, 0x5a544a).box(.99f, 1, 0, .01f, .6f, .6f, 0x5a544a).box(-.99f, 1, 0, .01f, .6f, .6f, 0x5a544a);
    static final Mesh PLATE_OFF = m().prism(0, 0, 0, .85f, .85f, .14f, 8, 0x55606a).prism(0, .14f, 0, .5f, .5f, .01f, 8, 0x7a8590);
    static final Mesh PLATE_ON = m().prism(0, 0, 0, .85f, .85f, .06f, 8, 0x55606a).prism(0, .06f, 0, .5f, .5f, .01f, 8, 0x7ef0c8 | Mesh.GLOW);
    static final Mesh TORCH = m().prism(0, 0, 0, .18f, .12f, 1.2f, 6, 0x3a3634).prism(0, 1.2f, 0, .22f, .34f, .25f, 8, 0x5a5048);
    static final Mesh PEDESTAL = m().prism(0, 0, 0, .4f, .3f, .9f, 6, 0x6a6460);
    static final Mesh CRYSTAL_OFF = m().blob(0, 0, 0, .35f, .55f, .35f, 2, 4, 0xff5a5a | Mesh.GLOW, 0);
    static final Mesh CRYSTAL_ON = m().blob(0, 0, 0, .35f, .55f, .35f, 2, 4, 0x6af0d0 | Mesh.GLOW, 0);
    static final Mesh POST = m().prism(0, 0, 0, .2f, .16f, 2.2f, 6, WOOD).prism(0, 2.2f, 0, .3f, .3f, .15f, 8, 0x5a5e66)
            .box(0, 2.45f, 0, .08f, .12f, .08f, 0xe8e8e8);
    static final Mesh SHRINE = m().vary(.05f).prism(0, 0, 0, .7f, .6f, .35f, 8, 0x7a746a).prism(0, .35f, 0, .22f, .18f, 1.5f, 6, 0x8a8478)
            .box(0, 2.05f, 0, .26f, .26f, .26f, 0x3a2e24).box(0, 2.05f, 0, .2f, .2f, .27f, 0xffc86a | Mesh.GLOW)
            .box(0, 2.05f, 0, .27f, .2f, .2f, 0xffc86a | Mesh.GLOW).prism(0, 2.31f, 0, .34f, 0, .3f, 4, 0x3a2e24);
    static final Mesh ICE_GATE = m().vary(.05f).prism(-3.3f, 0, 0, 1.2f, .45f, 8, 6, 0xa8d4e8, .15f).prism(3.3f, 0, 0, 1.2f, .45f, 8.6f, 6, 0xa8d4e8, .15f)
            .box(0, 7, 0, 4.5f, .8f, 1.2f, 0x8ab4c8).prism(0, 7.8f, 0, .8f, 0, 2.6f, 5, 0xd8f4ff | Mesh.GLOW).box(0, 2.6f, -.3f, 2, 2.6f, .3f, DARK)
            .prism(-4.8f, 0, 1.4f, .9f, 0, 3.4f, 5, 0xc4e6f2).prism(4.6f, 0, -1.2f, .8f, 0, 2.6f, 5, 0xc4e6f2).prism(5.6f, 0, 1, .6f, 0, 1.8f, 5, 0xd8f4ff);
    static final Mesh SUN_GATE = m().vary(.05f).prism(-3.4f, 0, 0, 1.3f, .8f, 7.5f, 4, 0xc8a46a).prism(3.4f, 0, 0, 1.3f, .8f, 7.5f, 4, 0xc8a46a)
            .box(0, 7.6f, 0, 4.8f, .6f, 1.4f, 0xb08a52).box(0, 2.7f, -.3f, 2.1f, 2.7f, .3f, DARK).prism(0, 8.2f, 0, .5f, .4f, .6f, 6, 0xb08a52)
            .blob(0, 9.7f, 0, 1.5f, 1.5f, .35f, 2, 12, 0xffc84a | Mesh.GLOW, 0)
            .prism(-5.4f, 0, 1.5f, .8f, 0, 1.6f, 5, 0xd8b884).prism(5.2f, 0, -1, .7f, 0, 1.2f, 5, 0xd8b884);
    static final Mesh MIRROR_BASE = m().vary(.05f).prism(0, 0, 0, .45f, .38f, .35f, 6, 0xa8844a).prism(0, .35f, 0, .12f, .1f, .55f, 6, 0x8a6a3a);
    static final Mesh MIRROR = m().box(0, 1.3f, 0, .62f, .48f, .05f, 0xd8a84a).box(0, 1.3f, .055f, .55f, .41f, .01f, 0xfff4d0 | Mesh.GLOW)
            .box(0, 1.3f, -.055f, .55f, .41f, .01f, 0xfff4d0 | Mesh.GLOW);
    static final Mesh SUN_PEDESTAL = m().vary(.05f).prism(0, 0, 0, .5f, .4f, .6f, 6, 0xb08a52).prism(0, .6f, 0, .3f, .38f, .2f, 6, 0xc8a46a);
    static final Mesh SUN_CRYSTAL_ON = m().prism(0, -.35f, 0, 0, .28f, .35f, 6, 0xffd870 | Mesh.GLOW).prism(0, 0, 0, .28f, 0, .4f, 6, 0xffe9a0 | Mesh.GLOW);
    static final Mesh SUN_CRYSTAL_OFF = m().prism(0, -.35f, 0, 0, .28f, .35f, 6, 0x8a6a3a).prism(0, 0, 0, .28f, 0, .4f, 6, 0x9a7a4a);
    static final Mesh SUN_DISC = m().vary(.05f).prism(0, 0, 0, .55f, .5f, .5f, 8, 0xb08a52).prism(0, .5f, 0, .5f, .5f, .04f, 12, 0xffe9a0 | Mesh.GLOW);
    static final Mesh SUN_SHAFT = m().prism(0, .5f, 0, .5f, .7f, 9, 8, 0xfff0b0 | Mesh.GLOW);
    static final Mesh MOUND = m().blob(0, 0, 0, .6f, .25f, .6f, 2, 8, 0xc8a46a, .15f);
    /** Folded clothes tied with a cord: the item-get picture for an outfit. */
    static Mesh bundle(int col) {
        return m().box(0, .12f, 0, .34f, .12f, .26f, col).box(0, .27f, 0, .3f, .04f, .22f, M.scale(col, 1.15f)).box(0, .15f, 0, .35f, .03f, .03f, 0xd8c8a0)
                .box(0, .15f, 0, .03f, .14f, .27f, 0xd8c8a0);
    }

    /** A little clay pot of dye. */
    static Mesh pot(int col) {
        return m().prism(0, 0, 0, .16f, .2f, .26f, 8, 0xb06a44).prism(0, .26f, 0, .17f, .17f, .02f, 8, col).prism(0, .27f, 0, .09f, 0, .1f, 6, col);
    }

    static final Mesh WINDCLOAK = cloak(0x3a7a9a, 0x2a5a7a), STORMCLOAK = cloak(0x2a2e52, 0xf2c14e);

    /** The glider: a canopy of sailcloth held above the head on two cords. */
    private static Mesh cloak(int top, int under) {
        return m().twoSided().tri(0, 2.8f, .6f, -1.75f, 2.3f, .2f, 0, 2.6f, -.45f, top).tri(0, 2.8f, .6f, 0, 2.6f, -.45f, 1.75f, 2.3f, .2f, top)
                .tri(-1.75f, 2.3f, .2f, -1.1f, 2.1f, -.4f, 0, 2.6f, -.45f, under).tri(1.75f, 2.3f, .2f, 0, 2.6f, -.45f, 1.1f, 2.1f, -.4f, under)
                .segment(-.22f, 1.6f, .05f, -1.5f, 2.32f, .15f, .01f, .01f, 3, 0xd8d0c0).segment(.22f, 1.6f, .05f, 1.5f, 2.32f, .15f, .01f, .01f, 3, 0xd8d0c0);
    }

    static final Mesh VENT = m().vary(.08f).prism(0, -.1f, 0, 1.9f, 1.2f, .25f, 7, 0x8a6a4a).prism(0, .1f, 0, .9f, .7f, .06f, 7, 0x2a1a10)
            .prism(1.6f, 0, .6f, .45f, 0, .5f, 4, 0xa8845a).prism(-1.4f, 0, -.9f, .4f, 0, .45f, 4, 0xa8845a);
    static final Mesh TENT = m().vary(.06f).prism(0, 0, 0, 2.2f, 0, 2.6f, 4, 0x8a6a4a).box(0, 2.55f, 0, .05f, .25f, .05f, WOOD)
            .twoSided().tri(-.5f, .01f, 1.58f, .5f, .01f, 1.58f, 0, 1.25f, .84f, DARK);
    static final Mesh KITE = m().twoSided().tri(0, .8f, 0, .5f, .05f, 0, 0, -.6f, 0, 0xe0503a).tri(0, .8f, 0, 0, -.6f, 0, -.5f, .05f, 0, 0xf2c14e)
            .tri(0, -.6f, 0, .12f, -1.1f, 0, -.12f, -1.1f, 0, 0x3a7ad8).tri(0, -1.1f, 0, .1f, -1.5f, 0, -.1f, -1.5f, 0, 0xe0503a);
    static final Mesh ROD = m().segment(0, .06f, -.95f, 0, .3f, .95f, .028f, .012f, 4, WOOD).blob(0, .12f, -.45f, .08f, .08f, .06f, 2, 5, 0x6a6a72, 0)
            .box(0, .1f, -.8f, .03f, .03f, .12f, 0x3a2a1e);
    static final Mesh ICE_BLOCK = m().vary(.05f).box(0, 2.3f, 0, 1, 2.3f, 1, 0xbfe8ff | Mesh.GLOW).box(0, 2.3f, 0, .7f, 2f, 1.02f, 0xe4f6ff | Mesh.GLOW);
    static final Mesh BLAST_POD = m().vary(.08f).prism(0, 0, 0, .5f, 0, .5f, 6, 0x3d6a2e).prism(.1f, 0, -.1f, .35f, 0, .6f, 5, 0x4a7a34)
            .box(0, .35f, 0, .03f, .2f, .03f, 0x3d6a2e).blob(0, .72f, 0, .24f, .26f, .24f, 3, 7, 0xff7a2a | Mesh.GLOW, .1f)
            .box(0, .99f, 0, .03f, .04f, .03f, 0x5a3a24);
    static final Mesh POD_SPROUT = m().prism(0, 0, 0, .4f, 0, .35f, 6, 0x3d6a2e).blob(0, .38f, 0, .07f, .08f, .07f, 2, 5, 0x8a6a3a, 0);
    static final Mesh POT = m().vary(.05f).prism(0, 0, 0, .26f, .36f, .38f, 8, 0xb06a44).prism(0, .38f, 0, .36f, .2f, .22f, 8, 0xa0603e)
            .prism(0, .6f, 0, .2f, .22f, .06f, 8, 0x8a5034);

    // ---- the village and the dungeon entrances --------------------------------------------------

    static final Mesh HOUSE = m().vary(.04f).box(0, 1.6f, 0, 3, 1.6f, 2.5f, PLASTER).box(0, .15f, 0, 3.1f, .15f, 2.6f, 0x7a7266)
            .prism(0, 3.2f, 0, 4.4f, 0, 2.4f, 4, ROOF).box(0, 1.1f, 2.51f, .55f, 1.1f, .02f, 0x5a3a24)
            .box(-1.8f, 1.9f, 2.51f, .45f, .4f, .02f, 0x3a5a7a).box(1.8f, 1.9f, 2.51f, .45f, .4f, .02f, 0x3a5a7a)
            .box(1.8f, 4.3f, -.8f, .3f, .9f, .3f, 0x6a5a50).box(-3.01f, 1.6f, 0, .02f, 1.6f, .12f, 0x6a4a2a).box(3.01f, 1.6f, 0, .02f, 1.6f, .12f, 0x6a4a2a);
    static final Mesh HOUSE_LIT = m().box(-1.8f, 1.9f, 2.53f, .42f, .37f, .01f, 0xffc060 | Mesh.GLOW).box(1.8f, 1.9f, 2.53f, .42f, .37f, .01f, 0xffc060 | Mesh.GLOW);
    static final Mesh SHOP_LIT = m().box(0, 1.2f, 2.53f, 1.35f, .65f, .01f, 0xffc060 | Mesh.GLOW);
    static final Mesh PUFF = m().blob(0, 0, 0, .5f, .42f, .5f, 2, 6, 0xd8d8d8, .12f);
    static final Mesh SHOP = m().vary(.04f).box(0, 1.6f, 0, 3, 1.6f, 2.5f, 0xd8c8a8).prism(0, 3.2f, 0, 4.4f, 0, 2.4f, 4, 0x3a7a5a)
            .box(0, .55f, 3.1f, 2.2f, .55f, .5f, WOOD).box(-2.1f, 1.6f, 3.4f, .1f, 1.6f, .1f, WOOD).box(2.1f, 1.6f, 3.4f, .1f, 1.6f, .1f, WOOD)
            .box(0, 3.25f, 3.4f, 2.4f, .08f, .9f, 0xd8a83a).box(0, 1.2f, 2.51f, 1.4f, .7f, .02f, 0x3a2a1a);
    static final Mesh WELL = m().vary(.08f).prism(0, 0, 0, 1.2f, 1.2f, .9f, 10, STONE).prism(0, .9f, 0, .95f, .95f, .02f, 10, 0x2a4a6a)
            .box(-1, 1.6f, 0, .08f, .8f, .08f, WOOD).box(1, 1.6f, 0, .08f, .8f, .08f, WOOD).prism(0, 2.4f, 0, 1.5f, 0, .8f, 4, ROOF);
    static final Mesh FENCE = m().box(-1.9f, .5f, 0, .07f, .5f, .07f, WOOD).box(0, .5f, 0, .07f, .5f, .07f, WOOD)
            .box(1.9f, .5f, 0, .07f, .5f, .07f, WOOD).box(0, .7f, 0, 2, .05f, .04f, WOOD).box(0, .35f, 0, 2, .05f, .04f, WOOD);
    static final Mesh SIGN = m().box(0, .6f, 0, .06f, .6f, .06f, WOOD).box(0, 1.15f, 0, .55f, .3f, .04f, 0xb08a5a);
    static final Mesh FIREPIT = m().vary(.1f);
    static final Mesh TREE_GATE = m().vary(.06f).prism(0, 0, 0, 5.5f, 3.8f, 15, 9, 0x5a3e2a, .12f)
            .blob(0, 17, 0, 9, 5, 9, 3, 8, 0x3d6e30, .25f).blob(4, 14.5f, 3, 5, 3, 5, 3, 7, 0x4f8a3a, .3f)
            .box(0, 2.2f, 5.05f, 1.4f, 2.2f, .3f, DARK).prism(-3, 0, 4, 1, 0, 2.5f, 5, 0x5a3e2a).prism(3, 0, 4.2f, 1, 0, 2.2f, 5, 0x5a3e2a);
    static final Mesh FORGE_GATE = m().vary(.06f).box(-3, 3.5f, 0, 1.2f, 3.5f, 1.4f, 0x4a403c).box(3, 3.5f, 0, 1.2f, 3.5f, 1.4f, 0x4a403c)
            .box(0, 7.4f, 0, 4.4f, .7f, 1.5f, 0x3a3230).box(0, 2.6f, -.3f, 1.8f, 2.6f, .3f, DARK).prism(0, 8.1f, 0, .5f, .3f, .6f, 6, 0x5a5048);
    static final Mesh TIDE_GATE = m().vary(.06f).box(-3.2f, 3, 0, 1, 3, 1, 0x5a6e74).box(3.2f, 3, 0, 1, 3, 1, 0x5a6e74)
            .prism(0, 6, 0, 4.6f, 3, 1.4f, 4, 0x4a5a62).box(0, 2.4f, -.3f, 2.2f, 2.4f, .3f, DARK).box(-3.2f, 6.2f, 0, 1.1f, .3f, 1.1f, 0x6a7e84);
    static final Mesh CURTAIN = m().box(0, 2.6f, .5f, 2.3f, 2.6f, .05f, 0x7ad8ff | Mesh.GLOW);
    static final Mesh LANTERN_TARGET = m().blob(0, 0, 0, .5f, .5f, .5f, 2, 6, 0xffd166 | Mesh.GLOW, 0);
    static final Mesh BOULDER = m().vary(.08f).blob(0, 1.6f, 0, 2.2f, 1.7f, 1.8f, 3, 6, 0x6e6a62, .2f);
    static final Mesh SEAL = m().prism(0, 0, 0, 30, 6, 60, 12, 0x2a1640 | Mesh.GLOW);
    static final Mesh ALTAR = m().vary(.05f).prism(0, 0, 0, 1.6f, 1.4f, .6f, 8, 0x8a8478).prism(0, .6f, 0, .5f, .4f, 1, 6, 0x7a746a)
            .blob(0, 1.9f, 0, .3f, .45f, .3f, 2, 4, 0x7ad8ff | Mesh.GLOW, 0);

    static {
        for (int i = 0; i < 7; i++) FIREPIT.blob(M.sin(i * .9f) * .9f, .1f, M.cos(i * .9f) * .9f, .25f, .18f, .25f, 2, 4, 0x777068, .2f);
        FIREPIT.box(0, .12f, 0, .6f, .07f, .07f, BARK).box(0, .16f, 0, .07f, .07f, .6f, BARK);
    }
}

package kestrel;

/** The hero's weapons, outfits and cape dyes: their names, what each one does, where it's found and how it looks. */
final class Arms {
    static final int SWORD = 0, GREATSWORD = 1, SPEAR = 2, FANGS = 3, HAMMER = 4;
    static final String[] WEAPON = {"Keeper's Sword", "Emberfall Greatsword", "Tidecaller Spear", "Wind Fangs", "Rimebreaker Hammer"};
    static final String[] WEAPON_SHORT = {"Sword", "Greatsword", "Spear", "Fangs", "Hammer"};
    static final String[] WEAPON_HOW = {
            "Balanced and quick. Charged: a full spin.",
            "Huge, slow sweeps that hit very hard. Charged: an overhead slam that bursts into embers.",
            "The longest reach, with quick thrusts. Charged: a lunge that pierces every foe in a line.",
            "Twin daggers, the fastest blades there are; a riposte with them hits five times as hard. Charged: a whirlwind of cuts.",
            "Crushing and slow: smashes cracked walls, ice, rubble and shields. Charged: a quake that staggers everything nearby."};
    static final String[] WEAPON_WHERE = {"", "Oskar the smith forges them.", "Ansel might part with his old one.", "Sela's nomads trade them.", "Hessa's rangers trade them."};
    /** Each weapon against the sword: damage, reach in metres, sweep in degrees, swing time, stamina per swing, blade length. */
    static final float[] DMG = {1, 1.8f, 1.15f, .62f, 2.5f}, REACH = {2.4f, 3.1f, 3.7f, 1.9f, 2.6f}, ARC = {150, 200, 60, 170, 150},
            TIME = {1, 1.45f, .9f, .62f, 1.8f}, STAMINA = {1, 1.5f, 1, .7f, 1.7f}, TIP = {1.05f, 1.85f, 2.05f, .55f, 1.15f};
    static final Mesh[] MESH = {Characters.SWORD, Characters.GREATSWORD, Characters.TIDE_SPEAR, Characters.FANG, Characters.HAMMER};

    static final int GARB = 0, FURS = 1, WRAPS = 2, PLATE = 3, STARWEAVE = 4, REGALIA = 5;
    static final String[] OUTFIT = {"Keeper's Garb", "Ranger's Furs", "Nomad's Wraps", "Forgeplate Armor", "Starweave Robe", "Hollow Regalia"};
    static final String[] OUTFIT_HOW = {"Your grandmother's colours. Comfortable and familiar.", "Stamina comes back 40% faster.",
            "You move 12% faster, and gliding tires you half as much.", "You take 35% less damage, but move 8% slower.",
            "Spells hit 30% harder, and spirit flows back three times as fast.", "Every blow you land hits 20% harder."};
    static final String[] OUTFIT_WHERE = {"", "Hessa's thanks for the wolf hunt.", "Sela's nomads trade them.", "Oskar the smith forges it.",
            "Hidden atop a desert mesa only a glider can reach.", "Left behind when the Hollow is broken."};
    static final Rig[] RIG = {Characters.HERO, Characters.HERO_FURS, Characters.HERO_WRAPS, Characters.HERO_PLATE, Characters.HERO_STARWEAVE, Characters.HERO_REGALIA};
    static final int[] OUTFIT_COL = {0xa8432a, 0x7a6248, 0xd8c8a0, 0x8a8e96, 0x2a2e6a, 0x2a1e2e};
    static final Mesh[] ICON = new Mesh[OUTFIT.length];

    static final String[] DYE = {"Outfit's own", "Crimson", "Royal blue", "Forest green", "Midnight", "Sunflower gold", "Snow white"};
    static final String[] DYE_WHERE = {"", "Pell sells it.", "Pell sells it.", "Pell sells it.", "Pell sells it.", "In a chest far out at sea.", "In a chest high in the north."};
    static final int[] DYE_COL = {0x7a2e1e, 0xb02a2a, 0x2a4ab0, 0x2a7a3a, 0x1e1e30, 0xe0b030, 0xeef0f2};
    static final Mesh[] POT = new Mesh[DYE.length];
    private static final Mesh[][] CAPES = new Mesh[DYE.length][], OWN = new Mesh[OUTFIT.length][];

    static {
        for (int i = 0; i < ICON.length; i++) ICON[i] = Models.bundle(OUTFIT_COL[i]);
        for (int i = 0; i < POT.length; i++) POT[i] = Models.pot(DYE_COL[i]);
    }

    private Arms() {}

    /** The hero's rig in the outfit being worn, its cape in the chosen dye. */
    static Rig dressed(Game.Gear gear) {
        Rig r = RIG[gear.outfit];
        int u = r.part("capeU"), l = r.part("capeL");
        if (OWN[gear.outfit] == null) OWN[gear.outfit] = new Mesh[]{r.mesh[u], r.mesh[l]};
        Mesh[] c = gear.dye == 0 ? OWN[gear.outfit] : CAPES[gear.dye] != null ? CAPES[gear.dye] : (CAPES[gear.dye] = Characters.cape(DYE_COL[gear.dye]));
        r.mesh[u] = c[0];
        r.mesh[l] = c[1];
        return r;
    }
}

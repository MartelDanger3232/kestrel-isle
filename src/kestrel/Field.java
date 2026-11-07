package kestrel;

/** The open island as a Level: terrain, buildings, boulders and the edge of the world. */
final class Field implements Level {
    private final World w;
    private final Places pl;

    Field(World w) {
        this.w = w;
        pl = w.places;
    }

    @Override
    public float floor(float x, float z) {
        if (x < 30 || z < 30 || x > World.EXTENT - 30 || z > World.EXTENT - 30) return 999;
        if (pl.inBuilding(x, z)) return 999;
        for (float[] b : pl.boulders) if (b[3] > 0 && M.dist(x, z, b[0], b[2]) < 2.2f) return 999;
        return w.height(x, z);
    }

    @Override
    public float water(float x, float z) { return w.height(x, z) < World.WATER ? World.WATER : Float.NaN; }

    @Override
    public float maxStep() { return .4f; }

    @Override
    public void collide(float[] pos, float y, float r) { pl.collide(pos, y, r); }
}

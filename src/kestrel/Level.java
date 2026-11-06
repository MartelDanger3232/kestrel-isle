package kestrel;

/** The space the hero moves through: the open island or a dungeon. */
interface Level {
    /** Height of whatever stands at (x, z): ground, a wall top (very high) or a pit floor (very low). */
    float floor(float x, float z);

    /** Water surface height, or NaN where there is none. */
    float water(float x, float z);

    /** Largest rise the hero can walk up in one stride. */
    float maxStep();

    /** Whether the hero may pull up onto low ledges. */
    default boolean ledges() { return false; }

    /** Whether (x, z) is a vine-covered wall the hero can climb. */
    default boolean vine(float x, float z) { return false; }

    /** Whether the ground at (x, z) is slick ice. */
    default boolean icy(float x, float z) { return false; }

    /** Pushes a circle out of solid props and objects. */
    void collide(float[] pos, float y, float r);
}

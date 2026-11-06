package kestrel;

import java.util.Random;

/** Time of day and weather, and the light and sky colours they produce. */
final class Climate {
    static final int CLEAR = 0, CLOUDY = 1, RAIN = 2, STORM = 3;
    private static final String[] NAMES = {"Clear", "Cloudy", "Rain", "Storm"};
    private static final float[] KEY_H = {0, 5, 6.5f, 8.5f, 16.5f, 18.2f, 19.6f, 24};
    private static final int[] ZEN = {0x080c22, 0x0a0e28, 0x485c96, 0x3070cd, 0x3070cd, 0x4a4884, 0x0e1230, 0x080c22};
    private static final int[] HOR = {0x18203e, 0x1c2244, 0xf4ac80, 0xb4d6f0, 0xb4d6f0, 0xf89262, 0x262448, 0x18203e};
    private static final float[][] AMB = {{.17f, .2f, .32f}, {.18f, .2f, .33f}, {.42f, .38f, .44f}, {.44f, .47f, .53f},
            {.44f, .47f, .53f}, {.42f, .36f, .42f}, {.2f, .2f, .32f}, {.17f, .2f, .32f}};
    private static final float[][] SUN = {{.12f, .14f, .24f}, {.1f, .12f, .2f}, {.9f, .62f, .42f}, {.74f, .7f, .6f},
            {.74f, .7f, .6f}, {.95f, .56f, .36f}, {.12f, .12f, .22f}, {.12f, .14f, .24f}};
    private static final float[] TARGET_COVER = {.3f, .68f, .9f, 1}, TARGET_OVERCAST = {.05f, .45f, .85f, 1};

    float hour = 8.5f, overcast, rain, cloudCover = .3f, lightning;
    int weather = CLEAR;
    private float weatherT = 240;
    float sunX, sunY, sunZ;
    int zen, cloudCol;
    private final Random rng = new Random(5);

    void update(float dt) {
        hour = (hour + dt * 24 / 960f) % 24;
        weatherT -= dt;
        if (weatherT <= 0) {
            float q = rng.nextFloat();
            weather = q < .5f ? CLEAR : q < .74f ? CLOUDY : q < .92f ? RAIN : STORM;
            weatherT = 90 + rng.nextFloat() * 150;
        }
        overcast = M.approach(overcast, TARGET_OVERCAST[weather], .12f, dt);
        cloudCover = M.approach(cloudCover, TARGET_COVER[weather], .12f, dt);
        rain = M.approach(rain, weather >= RAIN ? 1 : 0, .1f, dt);
        lightning = Math.max(0, lightning - dt * 3);
        if (weather == STORM && rain > .6f && rng.nextFloat() < dt * .12f) lightning = 1;
        float a = (hour - 6) / 12 * M.PI, l = M.sqrt(1 + .35f * .35f);
        sunX = M.cos(a) / l;
        sunY = M.sin(a) / l;
        sunZ = .35f / l;
    }

    void setWeather(int w, float secs) {
        weather = w;
        weatherT = secs;
    }

    boolean storm() { return weather == STORM && rain > .6f; }

    String name() { return NAMES[weather]; }

    String clock() { return String.format("%02d:%02d", (int) hour, (int) (hour % 1 * 60)); }

    boolean isNight() { return hour < 5.5f || hour > 19.2f; }

    /** Pushes this frame's light, fog and sky colours into the renderer. */
    void apply(Renderer r, float localRain) {
        int k = 0;
        while (hour >= KEY_H[k + 1]) k++;
        float t = (hour - KEY_H[k]) / (KEY_H[k + 1] - KEY_H[k]), o = overcast;
        zen = M.mix(ZEN[k], ZEN[k + 1], t);
        int hor = M.mix(HOR[k], HOR[k + 1], t);
        float lum = ((hor >> 16 & 255) + (hor >> 8 & 255) + (hor & 255)) / 765f;
        int grey = M.scale(0x8a929e, .35f + lum * .9f);
        hor = M.mix(hor, grey, o * .75f);
        zen = M.mix(zen, M.scale(grey, .85f), o * .8f);
        float dim = 1 - .15f * o, sunDim = 1 - .75f * o, flash = lightning * 1.3f;
        r.ar = M.lerp(AMB[k][0], AMB[k + 1][0], t) * dim + flash;
        r.ag = M.lerp(AMB[k][1], AMB[k + 1][1], t) * dim + flash;
        r.ab = M.lerp(AMB[k][2], AMB[k + 1][2], t) * dim + flash;
        r.sr = M.lerp(SUN[k][0], SUN[k + 1][0], t) * sunDim;
        r.sg = M.lerp(SUN[k][1], SUN[k + 1][1], t) * sunDim;
        r.sb = M.lerp(SUN[k][2], SUN[k + 1][2], t) * sunDim;
        boolean day = hour >= 5 && hour <= 19.6f;
        r.lx = day ? sunX : -sunX;
        r.ly = day ? sunY : -sunY;
        r.lz = day ? sunZ : -sunZ;
        r.fogCol = M.mix(hor, 0xffffff, lightning * .6f);
        r.fogEnd = M.lerp(1700, 520, localRain);
        r.fogStart = M.lerp(160, 50, localRain);
        float lvl = M.clamp(r.ar + r.sr * .8f, .12f, 1.1f);
        cloudCol = M.scale(M.mix(M.mix(0xfdfbf6, hor, .3f), 0x8a909c, o), lvl);
    }
}

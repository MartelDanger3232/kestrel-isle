package kestrel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Synthesised sound on a background thread: effects, the flute's five notes, and a generative score.
 * Each area's music is composed once from a seeded random walk over its scale, then looped.
 */
final class Audio {
    static final int SWING = 0, HIT = 1, CLANG = 2, ROLL = 3, JUMP = 4, SPIN = 5, HURT = 6, PICKUP = 7, GLEAM = 8, SECRET = 9,
            CLICK = 10, PUSH = 11, DOOR = 12, BOW = 13, BOOM = 14, SLAM = 15, STUN = 16, SPLASH = 17, ITEM = 18, TALK = 19,
            CHEST = 20, DIE = 21, HEART = 22, CLAW = 23, FUSE = 24, CAST = 25, THUNDER = 26, STARFALL = 27, HOOF = 28, NEIGH = 29,
            REST = 30, LEVEL = 31, CHIRP = 32, CRICKET = 33, WIND = 34, HOOT = 35, FLAP = 36;
    static final int TITLE = 0, VILLAGE = 1, FIELD = 2, NIGHT = 3, DUNGEON = 4, BOSS = 5, SILENT = 6;
    /** The flute plays a C major pentatonic: C D E G A. */
    static final double[] FLUTE = {523.25, 587.33, 659.25, 783.99, 880.0};
    private static final int RATE = 22050;

    private static final class Voice {
        double f0, f1, dur, vol, delay, t, phase, vib;
        int wave;
    }

    private final List<Voice> voices = new ArrayList<>();
    private final Random noise = new Random(1);
    private volatile int mode = SILENT;
    private int playing = -1, step, sampleInStep;
    private int[] melody, bass;
    private int stepsPerBar = 16;
    private double stepLen = .12, master = .5;
    private boolean ok;

    Audio(boolean enable) {
        if (!enable) return;
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, 4096);
            line.start();
            ok = true;
            Thread t = new Thread(() -> run(line), "audio");
            t.setDaemon(true);
            t.start();
        } catch (Exception | LinkageError e) {
            ok = false;
        }
    }

    void music(int m) { mode = m; }

    void sfx(int id) {
        if (!ok) return;
        switch (id) {
            case SWING -> { add(2, 900, 300, .09, .10, 0); add(1, 500, 220, .08, .05, 0); }
            case HIT -> { add(0, 220, 90, .12, .18, 0); add(2, 600, 200, .06, .10, 0); }
            case CLANG -> { add(0, 1250, 1180, .16, .10, 0); add(1, 2500, 2400, .22, .06, 0); }
            case ROLL -> add(2, 400, 200, .22, .06, 0);
            case JUMP -> add(0, 300, 620, .12, .07, 0);
            case SPIN -> { add(1, 200, 900, .42, .12, 0); add(2, 800, 200, .4, .06, 0); }
            case HURT -> add(0, 420, 140, .25, .16, 0);
            case PICKUP -> { add(1, 880, 880, .06, .1, 0); add(1, 1320, 1320, .1, .1, .06); }
            case GLEAM -> { add(1, 1320, 1320, .05, .08, 0); add(1, 1760, 1760, .09, .08, .05); }
            case SECRET -> { for (int i = 0; i < 4; i++) add(1, 523.25 * Math.pow(2, new int[]{0, 4, 7, 12}[i] / 12.0), 0, .16, .1, i * .1); }
            case CLICK -> add(0, 1000, 900, .03, .07, 0);
            case PUSH -> add(2, 200, 120, .3, .07, 0);
            case DOOR -> { add(1, 90, 70, .5, .14, 0); add(2, 300, 100, .4, .05, 0); }
            case BOW -> add(1, 900, 300, .1, .09, 0);
            case BOOM -> { add(2, 500, 60, .7, .35, 0); add(0, 90, 30, .5, .2, 0); }
            case SLAM -> { add(2, 300, 50, .45, .25, 0); add(0, 60, 30, .4, .15, 0); }
            case STUN -> add(1, 900, 280, .32, .1, 0);
            case SPLASH -> add(2, 900, 200, .3, .1, 0);
            case ITEM -> { for (int i = 0; i < 4; i++) add(i < 3 ? 1 : 0, 392 * Math.pow(2, new int[]{0, 4, 7, 12}[i] / 12.0), 0, i == 3 ? .7 : .14, .11, i * .14); }
            case TALK -> add(0, 680 + noise.nextInt(80), 0, .025, .04, 0);
            case CHEST -> add(1, 330, 660, .35, .1, 0);
            case DIE -> add(0, 440, 90, 1.1, .15, 0);
            case HEART -> { add(1, 1046, 1046, .08, .1, 0); add(1, 1318, 1318, .12, .1, .07); }
            case CLAW -> add(0, 1600, 900, .15, .05, 0);
            case FUSE -> add(2, 3000, 3000, .04, .02, 0);
            case CAST -> { add(1, 600, 1300, .28, .1, 0); add(3, 1300, 1900, .3, .05, .05); }
            case THUNDER -> { add(2, 4000, 300, .55, .3, 0); add(0, 1000, 70, .5, .14, 0); }
            case STARFALL -> { add(1, 1500, 180, 1.1, .1, 0); add(3, 900, 2400, 1.0, .05, .1); }
            case HOOF -> add(2, 300, 120, .05, .05, 0);
            case NEIGH -> { add(0, 480, 720, .18, .06, 0); add(0, 720, 380, .35, .06, .18); }
            case REST -> { for (int i = 0; i < 3; i++) add(1, 392 * Math.pow(2, new int[]{0, 4, 7}[i] / 12.0), 0, 1.2, .07, i * .12); }
            case LEVEL -> { for (int i = 0; i < 5; i++) add(1, 523.25 * Math.pow(2, new int[]{0, 4, 7, 12, 16}[i] / 12.0), 0, .25, .09, i * .07); }
            case CHIRP -> { add(1, 3100, 4300, .05, .025, 0); add(1, 4000, 3000, .06, .025, .08); add(1, 3300, 4500, .045, .02, .17); }
            case CRICKET -> { for (int i = 0; i < 4; i++) add(1, 4700, 4700, .018, .012, i * .05); }
            case WIND -> { add(2, 500, 250, 1.8, .022, 0); add(2, 300, 420, 1.4, .018, .5); }
            case HOOT -> { add(3, 390, 370, .25, .05, 0); add(3, 370, 330, .4, .05, .32); }
            case FLAP -> { for (int i = 0; i < 5; i++) add(2, 700, 400, .06, .03, i * .07); }
            default -> {}
        }
    }

    /** One flute note (0-4), with a little vibrato. */
    void note(int n) {
        if (!ok) return;
        Voice v = add(3, FLUTE[n], 0, .55, .22, 0);
        v.vib = .012;
        add(1, FLUTE[n] * 2, 0, .4, .03, 0);
    }

    private Voice add(int wave, double f0, double f1, double dur, double vol, double delay) {
        Voice v = new Voice();
        v.wave = wave;
        v.f0 = f0;
        v.f1 = f1 <= 0 ? f0 : f1;
        v.dur = dur;
        v.vol = vol;
        v.delay = delay;
        synchronized (voices) {
            if (voices.size() < 48) voices.add(v);
        }
        return v;
    }

    private void compose(int m) {
        Random r = new Random(m * 7919L + 11);
        int[] scale = m == DUNGEON || m == BOSS || m == NIGHT ? new int[]{0, 3, 5, 7, 10} : new int[]{0, 2, 4, 7, 9};
        int root = new int[]{57, 53, 50, 57, 45, 52, 0}[m];
        stepLen = new double[]{.2, .15, .12, .18, .22, .09, .2}[m];
        int[] prog = m == DUNGEON || m == BOSS || m == NIGHT ? new int[]{0, 8, 10, 0} : new int[]{0, 9, 5, 7};
        int bars = 4;
        melody = new int[bars * stepsPerBar];
        bass = new int[bars * stepsPerBar];
        int deg = 5;
        for (int s = 0; s < melody.length; s++) {
            boolean strong = s % 4 == 0;
            float density = m == DUNGEON ? .18f : m == BOSS ? .55f : m == TITLE ? .25f : .4f;
            if (r.nextFloat() < (strong ? density * 1.6f : density)) {
                deg = Math.max(0, Math.min(11, deg + r.nextInt(5) - 2));
                melody[s] = root + 12 + scale[deg % 5] + 12 * (deg / 5);
            } else melody[s] = -1;
            int bar = s / stepsPerBar;
            bass[s] = s % (m == BOSS ? 2 : 8) == 0 ? root - 12 + prog[bar] : -1;
        }
        // The last bar answers the first, so the loop has a shape.
        System.arraycopy(melody, 0, melody, 3 * stepsPerBar, stepsPerBar / 2);
        step = 0;
        sampleInStep = 0;
        playing = m;
    }

    private void tick() {
        if (mode != playing) {
            if (mode == SILENT) {
                playing = SILENT;
                melody = null;
            } else compose(mode);
        }
        if (melody == null) return;
        int s = step++ % melody.length;
        if (melody[s] >= 0) {
            Voice v = add(playing == TITLE || playing == VILLAGE || playing == NIGHT ? 1 : 0, midi(melody[s]), 0, stepLen * 2.2, .045, 0);
            v.vib = .004;
        }
        if (bass[s] >= 0) add(1, midi(bass[s]), 0, stepLen * (playing == BOSS ? 1.6 : 6), .08, 0);
        if (playing == BOSS && s % 4 == 2) add(2, 3000, 2000, .04, .03, 0);
        if (playing == FIELD && s % 8 == 4) add(2, 6000, 5000, .03, .015, 0);
    }

    private static double midi(int n) { return 440 * Math.pow(2, (n - 69) / 12.0); }

    private void run(SourceDataLine line) {
        byte[] buf = new byte[1024];
        double dt = 1.0 / RATE;
        while (true) {
            synchronized (voices) {
                for (int i = 0; i < buf.length / 2; i++) {
                    if (sampleInStep-- <= 0) {
                        sampleInStep = (int) (stepLen * RATE);
                        tick();
                    }
                    double s = 0;
                    for (int k = voices.size() - 1; k >= 0; k--) {
                        Voice v = voices.get(k);
                        if (v.delay > 0) {
                            v.delay -= dt;
                            continue;
                        }
                        v.t += dt;
                        if (v.t >= v.dur) {
                            voices.remove(k);
                            continue;
                        }
                        double p = v.t / v.dur, f = v.f0 + (v.f1 - v.f0) * p;
                        f *= 1 + v.vib * Math.sin(v.t * 2 * Math.PI * 5.5);
                        v.phase += f * dt;
                        double ph = v.phase - Math.floor(v.phase), w = switch (v.wave) {
                            case 0 -> ph < .5 ? 1 : -1;
                            case 1 -> 4 * Math.abs(ph - .5) - 1;
                            case 2 -> noise.nextDouble() * 2 - 1;
                            default -> Math.sin(ph * 2 * Math.PI);
                        };
                        double env = Math.min(1, v.t / .008) * Math.pow(1 - p, v.wave == 3 ? .6 : 1.4);
                        s += w * env * v.vol;
                    }
                    int val = (int) (Math.max(-1, Math.min(1, s * master)) * 32000);
                    buf[i * 2] = (byte) val;
                    buf[i * 2 + 1] = (byte) (val >> 8);
                }
            }
            line.write(buf, 0, buf.length);
        }
    }
}

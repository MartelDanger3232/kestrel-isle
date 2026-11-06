package kestrel;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** Entry point: the game window, plus headless screenshot, benchmark and self-test modes. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        boolean windowed = false;
        int res = 360;
        float autoquit = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dev-window" -> windowed = true;
                case "--res" -> res = Integer.parseInt(args[++i]);
                case "--autoquit" -> autoquit = Float.parseFloat(args[++i]);
                case "--shots" -> {
                    Dev.shots(new File(args[++i]));
                    return;
                }
                case "--bench" -> {
                    Dev.bench();
                    return;
                }
                case "--fuzz" -> {
                    System.exit(Dev.fuzz() ? 0 : 1);
                    return;
                }
                case "--test" -> {
                    System.exit(Dev.test() ? 0 : 1);
                    return;
                }
                default -> {}
            }
        }
        // Saves used to sit beside the game; carry an old one over to the per-user folder the first time.
        java.nio.file.Path old = java.nio.file.Path.of("save.properties");
        try {
            if (java.nio.file.Files.exists(old) && !java.nio.file.Files.exists(Story.SAVE)) {
                java.nio.file.Files.createDirectories(Story.SAVE.getParent());
                java.nio.file.Files.copy(old, Story.SAVE);
            }
        } catch (java.io.IOException e) {
            System.err.println("Couldn't move the old save: " + e.getMessage());
        }
        play(windowed, res, autoquit);
    }

    /** The canvas centre in screen coordinates, or null while it isn't on screen. */
    private static int[] center(Canvas canvas) {
        try {
            java.awt.Point c = canvas.getLocationOnScreen();
            return new int[]{c.x + canvas.getWidth() / 2, c.y + canvas.getHeight() / 2};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void play(boolean windowed, int res, float autoquit) throws Exception {
        Frame frame = new Frame("Kestrel Isle");
        Canvas canvas = new Canvas();
        canvas.setIgnoreRepaint(true);
        canvas.setBackground(Color.BLACK);
        frame.add(canvas);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
        });
        AtomicBoolean pauseRequest = new AtomicBoolean();
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) { pauseRequest.set(true); }
        });
        Input in = new Input();
        canvas.addKeyListener(in);
        canvas.addMouseListener(in);
        canvas.addMouseMotionListener(in);
        canvas.addMouseWheelListener(in::wheel);
        canvas.addFocusListener(in);
        canvas.setFocusTraversalKeysEnabled(false);
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!windowed) {
            // Always full screen, so the pointer can't wander off the game.
            frame.setUndecorated(true);
            if (gd.isFullScreenSupported()) gd.setFullScreenWindow(frame);
            else {
                frame.setBounds(gd.getDefaultConfiguration().getBounds());
                frame.setVisible(true);
            }
            frame.toFront();
        } else {
            frame.setSize(1280, 760);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
        canvas.setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
                new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new Point(), "none"));
        for (int i = 0; i < 150 && (canvas.getWidth() < 64 || canvas.getHeight() < 64); i++) Thread.sleep(20);
        canvas.requestFocus();
        canvas.createBufferStrategy(2);
        BufferStrategy bs = canvas.getBufferStrategy();
        Graphics2D splash = (Graphics2D) bs.getDrawGraphics();
        splash.setColor(new Color(20, 24, 32));
        splash.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        splash.setColor(new Color(250, 246, 236));
        splash.setFont(new Font(Font.SERIF, Font.BOLD, 28));
        splash.drawString("Shaping the island...", canvas.getWidth() / 2 - 130, canvas.getHeight() / 2);
        splash.dispose();
        bs.show();

        Game game = new Game(in, new Audio(true));
        float aspect = canvas.getHeight() >= 64 ? (float) canvas.getWidth() / canvas.getHeight() : 16 / 9f;
        Renderer r = new Renderer(Math.round(res * M.clamp(aspect, 1.2f, 2.4f)) & ~1, res);
        Scene scene = new Scene(game, r);
        Hud hud = new Hud(game, r);
        int errors = 0;
        MouseLock lock = new MouseLock();
        boolean pinned = false, lockWorks = false;
        int[] mid = center(canvas);
        if (lock.available() && mid != null) {
            lock.moveTo(mid[0], mid[1]);
            Thread.sleep(60);
            PointerInfo pi = MouseInfo.getPointerInfo();
            lockWorks = pi != null && Math.abs(pi.getLocation().x - mid[0]) <= 1 && Math.abs(pi.getLocation().y - mid[1]) <= 1;
        }
        long last = System.nanoTime(), frameNs = 1_000_000_000L / 60;
        float elapsed = 0, fpsT = 0;
        int frames = 0, fps = 0;
        while (true) {
            long now = System.nanoTime();
            float dt = Math.min(.05f, (now - last) / 1e9f);
            last = now;
            elapsed += dt;
            if (pauseRequest.getAndSet(false) && game.screen == Game.Screen.PLAY) game.screen = Game.Screen.PAUSE;
            // While playing (and only while this window has focus) keep the pointer pinned to the centre,
            // turning whatever distance it travelled into camera movement.
            boolean want = lockWorks && frame.isFocused() && game.screen == Game.Screen.PLAY;
            mid = want ? center(canvas) : null;
            PointerInfo pi = want && mid != null ? MouseInfo.getPointerInfo() : null;
            if (pi != null) {
                java.awt.Point p = pi.getLocation();
                if (pinned) in.addDelta(p.x - mid[0], p.y - mid[1]);
                if (p.x != mid[0] || p.y != mid[1]) lock.moveTo(mid[0], mid[1]);
                pinned = true;
            } else if (pinned) {
                pinned = false;
                in.forgetPointer();
            }
            in.locked = pinned;
            // A bug in one frame must never freeze the game: report it and keep running.
            try {
                game.update(dt);
                if (game.screen == Game.Screen.PAUSE && in.pressed(KeyEvent.VK_Q)) System.exit(0);
                scene.render(dt);
                Graphics2D g2 = (Graphics2D) bs.getDrawGraphics();
                int cw = canvas.getWidth(), ch = canvas.getHeight();
                g2.drawImage(r.image, 0, 0, cw, ch, null);
                hud.draw(g2, cw, ch);
                g2.dispose();
                bs.show();
            } catch (RuntimeException e) {
                if (errors++ < 3) {
                    System.err.println("Kestrel Isle hit an error but kept running (please report this):");
                    e.printStackTrace();
                }
            }
            Toolkit.getDefaultToolkit().sync();
            frames++;
            if ((fpsT += dt) >= 1) {
                fps = frames;
                frames = 0;
                fpsT = 0;
            }
            if (autoquit > 0 && elapsed > autoquit) {
                System.out.println("ran " + autoquit + "s, " + fps + " fps at " + r.w + "x" + r.h + ", mouse lock " + (lockWorks ? "working" : "unavailable"));
                System.exit(0);
            }
            long spent = System.nanoTime() - now;
            if (spent < frameNs) Thread.sleep((frameNs - spent) / 1_000_000);
        }
    }
}

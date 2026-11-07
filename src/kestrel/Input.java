package kestrel;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * Keyboard and mouse state, latched on the AWT thread and read once per frame. While the game pins the
 * pointer (see MouseLock), mouse movement arrives as raw deltas through {@link #addDelta} instead of events.
 */
final class Input implements KeyListener, MouseListener, MouseMotionListener, FocusListener {
    static final int MOUSE_L = 1000, MOUSE_R = 1001;
    private final boolean[] down = new boolean[1024], queued = new boolean[1024], hit = new boolean[1024];
    private int lastX = -1, lastY = -1;
    private float accX, accY;
    /** Mouse travel and wheel clicks since the last poll. */
    float dx, dy;
    int wheel;
    private int wheelAcc;
    /** True while the pointer is pinned; pointer events then carry no movement. */
    volatile boolean locked;

    synchronized void poll() {
        System.arraycopy(queued, 0, hit, 0, hit.length);
        java.util.Arrays.fill(queued, false);
        dx = accX;
        dy = accY;
        accX = accY = 0;
        wheel = wheelAcc;
        wheelAcc = 0;
    }

    synchronized void wheel(java.awt.event.MouseWheelEvent e) { wheelAcc += e.getWheelRotation(); }

    synchronized void addDelta(float x, float y) {
        accX += x;
        accY += y;
    }

    synchronized void forgetPointer() { lastX = -1; }

    boolean down(int code) { return down[code]; }

    boolean pressed(int code) { return hit[code]; }

    boolean any(int... codes) {
        for (int c : codes) if (hit[c]) return true;
        return false;
    }

    /** For scripted tests, and for the engine window, which delivers its own key and button events. */
    void force(int code, boolean isDown) { set(code, isDown); }

    synchronized void scroll(int clicks) { wheelAcc += clicks; }

    /** Lets go of everything (the window lost focus). */
    synchronized void release() {
        java.util.Arrays.fill(down, false);
        lastX = -1;
    }


    private synchronized void set(int code, boolean d) {
        if (code < 0 || code >= down.length) return;
        if (d && !down[code]) queued[code] = true;
        down[code] = d;
    }

    private synchronized void move(MouseEvent e) {
        if (lastX >= 0 && !locked) {
            accX += e.getX() - lastX;
            accY += e.getY() - lastY;
        }
        lastX = e.getX();
        lastY = e.getY();
    }

    @Override public void keyPressed(KeyEvent e) { set(e.getKeyCode(), true); }
    @Override public void keyReleased(KeyEvent e) { set(e.getKeyCode(), false); }
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mousePressed(MouseEvent e) { set(e.getButton() == MouseEvent.BUTTON1 && !e.isControlDown() ? MOUSE_L : MOUSE_R, true); }
    @Override public void mouseReleased(MouseEvent e) { set(MOUSE_L, false); set(MOUSE_R, false); }
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) { lastX = -1; move(e); }
    @Override public synchronized void mouseExited(MouseEvent e) { lastX = -1; }
    @Override public void mouseMoved(MouseEvent e) { move(e); }
    @Override public void mouseDragged(MouseEvent e) { move(e); }
    @Override public void focusGained(FocusEvent e) {}

    @Override
    public synchronized void focusLost(FocusEvent e) {
        java.util.Arrays.fill(down, false);
        lastX = -1;
    }
}

package kestrel;

import java.awt.Robot;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Pins the pointer to one spot so mouse movement can turn the camera forever without hitting a screen edge.
 * On macOS this calls CoreGraphics directly (warping the cursor needs no special permission there); elsewhere it
 * falls back to java.awt.Robot.
 */
final class MouseLock {
    private MethodHandle warp, reattach;
    private MemorySegment point;
    private Robot robot;

    MouseLock() {
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
                Linker linker = Linker.nativeLinker();
                SymbolLookup cg = SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global());
                StructLayout cgPoint = MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
                warp = linker.downcallHandle(cg.find("CGWarpMouseCursorPosition").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, cgPoint));
                reattach = linker.downcallHandle(cg.find("CGAssociateMouseAndMouseCursorPosition").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                point = Arena.global().allocate(cgPoint);
            } else {
                robot = new Robot();
            }
        } catch (Throwable t) {
            warp = null;
            robot = null;
        }
    }

    boolean available() { return warp != null || robot != null; }

    /** Moves the pointer to screen point (x, y). */
    void moveTo(int x, int y) {
        try {
            if (warp != null) {
                point.set(ValueLayout.JAVA_DOUBLE, 0, x);
                point.set(ValueLayout.JAVA_DOUBLE, 8, y);
                int err = (int) warp.invokeExact(point);
                // Re-associating right after a warp stops macOS from briefly freezing mouse input.
                err |= (int) reattach.invokeExact(1);
            } else if (robot != null) {
                robot.mouseMove(x, y);
            }
        } catch (Throwable t) {
            warp = null;
            robot = null;
        }
    }
}

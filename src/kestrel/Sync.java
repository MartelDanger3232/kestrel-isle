package kestrel;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Writes an object's plain state (numbers, flags, enums and arrays of numbers) to a stream and reads it back into
 * another, so a co-op host can send friends the whole world without every class spelling out what it holds. Both
 * ends run the same code, so fields line up by name; references to other objects are left alone.
 */
final class Sync {
    private static final Map<Class<?>, Field[]> FIELDS = new HashMap<>();

    private Sync() {}

    private static Field[] fields(Class<?> c) {
        return FIELDS.computeIfAbsent(c, k -> {
            List<Field> out = new ArrayList<>();
            for (Field f : k.getDeclaredFields()) {
                int mod = f.getModifiers();
                Class<?> t = f.getType();
                boolean array = t.isArray() && t.getComponentType().isPrimitive();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || !(t.isPrimitive() || t.isEnum() || array)) continue;
                // Final numbers and kinds are fixed when the object is made; final arrays still have contents to copy.
                if (Modifier.isFinal(mod) && !array) continue;
                f.setAccessible(true);
                out.add(f);
            }
            out.sort(Comparator.comparing(Field::getName));
            return out.toArray(new Field[0]);
        });
    }

    static void write(DataOutputStream out, Object o) throws IOException {
        try {
            for (Field f : fields(o.getClass())) {
                Class<?> t = f.getType();
                if (t == float.class) out.writeFloat(f.getFloat(o));
                else if (t == int.class) out.writeInt(f.getInt(o));
                else if (t == boolean.class) out.writeBoolean(f.getBoolean(o));
                else if (t == long.class) out.writeLong(f.getLong(o));
                else if (t == double.class) out.writeDouble(f.getDouble(o));
                else if (t == short.class) out.writeShort(f.getShort(o));
                else if (t == byte.class) out.writeByte(f.getByte(o));
                else if (t == char.class) out.writeChar(f.getChar(o));
                else if (t.isEnum()) out.writeByte(f.get(o) == null ? -1 : ((Enum<?>) f.get(o)).ordinal());
                else {
                    Object a = f.get(o);
                    int n = a == null ? -1 : Array.getLength(a);
                    out.writeInt(n);
                    for (int i = 0; i < n; i++) {
                        if (a instanceof float[] v) out.writeFloat(v[i]);
                        else if (a instanceof int[] v) out.writeInt(v[i]);
                        else if (a instanceof boolean[] v) out.writeBoolean(v[i]);
                        else if (a instanceof byte[] v) out.writeByte(v[i]);
                        else if (a instanceof long[] v) out.writeLong(v[i]);
                        else if (a instanceof double[] v) out.writeDouble(v[i]);
                        else if (a instanceof short[] v) out.writeShort(v[i]);
                        else out.writeChar(((char[]) a)[i]);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void read(DataInputStream in, Object o) throws IOException {
        try {
            for (Field f : fields(o.getClass())) {
                Class<?> t = f.getType();
                if (t == float.class) f.setFloat(o, in.readFloat());
                else if (t == int.class) f.setInt(o, in.readInt());
                else if (t == boolean.class) f.setBoolean(o, in.readBoolean());
                else if (t == long.class) f.setLong(o, in.readLong());
                else if (t == double.class) f.setDouble(o, in.readDouble());
                else if (t == short.class) f.setShort(o, in.readShort());
                else if (t == byte.class) f.setByte(o, in.readByte());
                else if (t == char.class) f.setChar(o, in.readChar());
                else if (t.isEnum()) {
                    int k = in.readByte();
                    f.set(o, k < 0 ? null : t.getEnumConstants()[k]);
                } else {
                    int n = in.readInt();
                    Object a = f.get(o);
                    boolean fits = n >= 0 && a != null && Array.getLength(a) == n;
                    if (!fits && n >= 0) a = Array.newInstance(t.getComponentType(), n);
                    for (int i = 0; i < n; i++) {
                        if (a instanceof float[] v) v[i] = in.readFloat();
                        else if (a instanceof int[] v) v[i] = in.readInt();
                        else if (a instanceof boolean[] v) v[i] = in.readBoolean();
                        else if (a instanceof byte[] v) v[i] = in.readByte();
                        else if (a instanceof long[] v) v[i] = in.readLong();
                        else if (a instanceof double[] v) v[i] = in.readDouble();
                        else if (a instanceof short[] v) v[i] = in.readShort();
                        else ((char[]) a)[i] = in.readChar();
                    }
                    if (!fits && !Modifier.isFinal(f.getModifiers())) f.set(o, n < 0 ? null : a);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Copies one object's plain state onto another of the same class. */
    static void copy(Object from, Object to) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            write(new DataOutputStream(bytes), from);
            read(new DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())), to);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static <T> void writeList(DataOutputStream out, List<T> list) throws IOException {
        out.writeInt(list.size());
        for (T t : list) write(out, t);
    }

    /** Reads a list written by writeList, reusing the objects already there and making new ones as needed. */
    static <T> void readList(DataInputStream in, List<T> list, Supplier<T> make) throws IOException {
        int n = in.readInt();
        while (list.size() > n) list.remove(list.size() - 1);
        while (list.size() < n) list.add(make.get());
        for (T t : list) read(in, t);
    }

    static void writeArrays(DataOutputStream out, List<float[]> list) throws IOException {
        out.writeInt(list.size());
        for (float[] a : list) {
            out.writeInt(a.length);
            for (float v : a) out.writeFloat(v);
        }
    }

    static void readArrays(DataInputStream in, List<float[]> list) throws IOException {
        int n = in.readInt();
        while (list.size() > n) list.remove(list.size() - 1);
        for (int i = 0; i < n; i++) {
            float[] a = new float[in.readInt()];
            for (int k = 0; k < a.length; k++) a[k] = in.readFloat();
            if (i < list.size()) list.set(i, a);
            else list.add(a);
        }
    }
}

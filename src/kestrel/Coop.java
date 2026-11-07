package kestrel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Online co-op for up to four. One player hosts: their game runs the island, friends' keys arrive over the network to
 * drive their own heroes (each with the gear from their own save), and the whole world goes back out twenty times a
 * second. A friend who joins runs no world of their own: they draw what the host sends, play its sounds, and send back
 * their keys, camera and choice of gear. When they leave, the gear they gathered goes home to their own save.
 */
final class Coop {
    static final int PORT = 47777, MAX = 4;
    private static final int HELLO = 1, WELCOME = 2, KEYS = 3, WORLD = 4, FULL = 5, DEV = 6, VERSION = 2;
    private static final Path PREFS = Story.SAVE.resolveSibling("coop.txt");
    /** Set from the command line: start hosting, or join this address, as soon as the island is ready. */
    static boolean autoHost;
    static String autoJoin;

    private final Game g;
    private final boolean hosting;
    // Hosting.
    private ServerSocket server;
    private final List<Link> links = new CopyOnWriteArrayList<>();
    /** This computer's addresses, for friends to type in. */
    final List<String> addresses = new ArrayList<>();
    private final List<Integer> sounds = new ArrayList<>();
    private float sendT;
    private int nextId = 1;
    // Joined.
    private Link host;
    private volatile byte[] world;
    private int myId = -1, wantWeapon = -1, wantOutfit = -1, wantDye = -1, hostWeapon, hostOutfit, hostDye;
    /** The host's screen: anything but PLAY means their world is paused (a menu or a conversation). */
    Game.Screen hostScreen = Game.Screen.PLAY;
    String hostName = "the host";

    private Coop(Game g, boolean hosting) {
        this.g = g;
        this.hosting = hosting;
    }

    boolean hosting() { return hosting; }

    boolean joined() { return !hosting; }

    // ---- starting -----------------------------------------------------------------------------

    /** Opens this island to friends; false (with a message) if the port can't be opened. */
    static boolean host(Game g) {
        try {
            Coop c = new Coop(g, true);
            c.server = new ServerSocket(PORT);
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                if (ni.isUp() && !ni.isLoopback())
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) if (a instanceof Inet4Address) c.addresses.add(a.getHostAddress());
            Thread t = new Thread(c::accept, "co-op host");
            t.setDaemon(true);
            t.start();
            g.coop = c;
            g.audio.echo = c.sounds::add;
            g.say("Co-op is open: friends choose Join co-op and type " + (c.addresses.isEmpty() ? "this computer's address" : c.addresses.get(0)) + ".");
            return true;
        } catch (IOException e) {
            g.say("Couldn't open co-op: " + e.getMessage());
            return false;
        }
    }

    /** Joins a friend's island, bringing this save's hero and gear. The address may carry a port (a tunnel's, say): host:port. */
    static void join(Game g, String address) {
        try {
            int colon = address.lastIndexOf(':'), port = PORT;
            String host = address;
            if (colon > 0) {
                port = Integer.parseInt(address.substring(colon + 1));
                host = address.substring(0, colon);
            }
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), 8000);
            if (g.story.hasSave()) g.story.load();
            Coop c = new Coop(g, false);
            c.host = c.new Link(s);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(bytes);
            o.writeInt(VERSION);
            o.writeUTF(g.me.name);
            Sync.write(o, g.me.gear);
            c.host.send(HELLO, bytes.toByteArray());
            c.host.start();
            g.coop = c;
            g.screen = Game.Screen.PLAY;
            g.say("Joining " + address + "...");
            Files.createDirectories(PREFS.getParent());
            Files.writeString(PREFS, address);
        } catch (IOException | NumberFormatException e) {
            g.say("Couldn't reach " + address + " (" + e.getMessage() + "). Is co-op open on their island, and are you on the same network?");
        }
    }

    /** The last address joined, to start the join screen with. */
    static String lastAddress() {
        try {
            return Files.exists(PREFS) ? Files.readString(PREFS).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** Starts what the command line asked for, once the island exists. */
    static void auto(Game g) {
        if (!autoHost && autoJoin == null) return;
        g.screen = Game.Screen.PLAY;
        if (autoJoin != null) join(g, autoJoin);
        else {
            if (g.story.hasSave()) g.story.load();
            host(g);
        }
    }

    /** Leaving (quitting, or the host going away): a friend takes their gear home to their own save. */
    void leave() {
        if (hosting) {
            try {
                server.close();
            } catch (IOException ignored) {
                // Closing anyway.
            }
            for (Link l : links) l.close();
            return;
        }
        host.close();
        if (g.story.hasSave() || g.me.gear.gleams > 0) g.story.keepGear(g.me.gear);
    }

    // ---- each frame ---------------------------------------------------------------------------

    /** Before the game's update: a host lets friends in and out and passes on their keys; a friend takes the latest world. */
    void before() {
        if (hosting) {
            for (Link l : links) {
                if (!l.open) {
                    links.remove(l);
                    if (l.player != null) {
                        g.players.remove(l.player);
                        g.say(l.player.name + " left your island.");
                    }
                    continue;
                }
                if (l.gear != null && l.player == null && g.screen != Game.Screen.TITLE) welcome(l);
                if (l.player != null) {
                    l.drive();
                    // A friend's dev pack: their gear is here, so it's handed over here.
                    for (int[] d; (d = l.devs.poll()) != null; ) {
                        Game.Player was = g.as(l.player);
                        g.devGive(d[0], d[1], d[2] == 1);
                        g.as(was);
                    }
                }
            }
            return;
        }
        if (!host.open) {
            String why = hostName + " closed their island.";
            leave();
            g.coop = null;
            g.players.retainAll(List.of(g.me));
            g.screen = Game.Screen.TITLE;
            g.say(why);
            return;
        }
        byte[] w = world;
        world = null;
        if (w != null) {
            try {
                apply(w);
            } catch (IOException e) {
                System.err.println("Co-op: couldn't read the world from the host: " + e);
            }
        }
    }

    /** After the game's update: a host sends the world out; a friend sends their keys. */
    void after(float dt) {
        if (hosting) {
            if (links.isEmpty() || (sendT += dt) < 1 / 20f) return;
            sendT = 0;
            try {
                byte[] w = snapshot();
                for (Link l : links) if (l.player != null) l.send(WORLD, w);
            } catch (IOException e) {
                System.err.println("Co-op: couldn't send the world: " + e);
            }
            sounds.clear();
            return;
        }
        Game.Gear gear = g.me.gear;
        if (gear.weapon != hostWeapon) wantWeapon = gear.weapon;
        if (gear.outfit != hostOutfit) wantOutfit = gear.outfit;
        if (gear.dye != hostDye) wantDye = gear.dye;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(160);
        DataOutputStream o = new DataOutputStream(bytes);
        try {
            byte[] keys = new byte[128];
            for (int k = 0; k < 1024; k++) if (g.me.in.down(k)) keys[k >> 3] |= (byte) (1 << (k & 7));
            o.write(keys);
            o.writeFloat(g.camYaw);
            o.writeFloat(g.camPitch);
            o.writeByte(wantWeapon);
            o.writeByte(wantOutfit);
            o.writeByte(wantDye);
            host.send(KEYS, bytes.toByteArray());
        } catch (IOException e) {
            host.close();
        }
    }

    /** A friend took something from their dev pack: ask the host, who keeps their gear, to hand it over. */
    void dev(int tab, int row, boolean all) {
        host.send(DEV, new byte[]{(byte) tab, (byte) row, (byte) (all ? 1 : 0)});
    }

    /** A friend's hello has arrived: give them a hero next to the host's, with the gear they brought. */
    private void welcome(Link l) {
        Game.Gear gear = new Game.Gear();
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(l.gear));
            if (in.readInt() != VERSION) {
                l.close();
                return;
            }
            l.name = in.readUTF();
            Sync.read(in, gear);
        } catch (IOException e) {
            l.close();
            return;
        }
        Hero h = g.me.hero;
        Game.Player p = new Game.Player(new Hero(h.x + 1.5f, h.y, h.z + 1.5f), gear, new Input(), l.name);
        p.id = nextId++;
        p.camYaw = g.camYaw;
        Game.Player was = g.as(p);
        g.refreshStats();
        g.hero.hp = g.hero.maxHp;
        g.as(was);
        g.players.add(p);
        l.player = p;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            DataOutputStream o = new DataOutputStream(bytes);
            o.writeInt(p.id);
            o.writeUTF(g.me.name);
            l.send(WELCOME, bytes.toByteArray());
        } catch (IOException e) {
            l.close();
        }
        g.say(l.name + " joined your island!");
    }

    // ---- the world, in one message --------------------------------------------------------------

    private byte[] snapshot() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 15);
        try (DataOutputStream o = new DataOutputStream(new DeflaterOutputStream(bytes, new Deflater(Deflater.BEST_SPEED)))) {
            o.writeFloat(g.time);
            o.writeByte(g.screen.ordinal());
            o.writeByte(g.audio.music());
            o.writeInt(sounds.size());
            for (int s : sounds) o.writeByte(s);
            Sync.write(o, g.climate);
            o.writeInt(g.dungeonIndex);
            o.writeInt(g.players.size());
            for (Game.Player p : g.players) {
                o.writeInt(p.id);
                o.writeUTF(p.name);
                Sync.write(o, p.hero);
                Sync.write(o, p.gear);
            }
            Sync.write(o, g.horse);
            o.writeInt(g.foes.size());
            for (Foe f : g.foes) {
                o.writeByte(f.kind.ordinal());
                Sync.write(o, f);
            }
            o.writeInt(g.foes.indexOf(g.boss));
            o.writeInt(g.foes.indexOf(g.drake));
            Sync.writeList(o, g.shots);
            Sync.writeList(o, g.bombs);
            Sync.writeList(o, g.drops);
            Sync.writeList(o, g.sparks);
            Sync.writeArrays(o, g.magic.stars);
            Sync.writeArrays(o, g.magic.rings);
            Sync.writeArrays(o, g.waves);
            o.writeBoolean(g.clawOut);
            for (float v : new float[]{g.clawX, g.clawY, g.clawZ, g.clawDX, g.clawDY, g.clawDZ}) o.writeFloat(v);
            Places pl = g.world.places;
            Sync.writeList(o, pl.gates);
            Sync.writeArrays(o, pl.boulders);
            Sync.writeArrays(o, pl.pods);
            Sync.writeList(o, pl.chests);
            Sync.writeList(o, pl.npcs);
            Sync.writeList(o, pl.folk);
            for (List<Places.Pickup> chunk : pl.pickups) for (Places.Pickup p : chunk) o.writeBoolean(p.taken);
            if (g.dungeon != null) {
                Sync.write(o, g.dungeon);
                Sync.writeList(o, g.dungeon.things);
            }
        }
        return bytes.toByteArray();
    }

    private void apply(byte[] w) throws IOException {
        DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(w)));
        g.time = in.readFloat();
        hostScreen = Game.Screen.values()[in.readByte()];
        int music = in.readByte();
        if (music != g.audio.music()) g.audio.music(music);
        for (int n = in.readInt(); n > 0; n--) g.audio.sfx(in.readByte());
        Sync.read(in, g.climate);
        int k = in.readInt();
        if (k != g.dungeonIndex) {
            g.dungeonIndex = k;
            if (k >= 0) {
                if (g.dungeons[k] == null) g.dungeons[k] = new Dungeon(Dungeons.ALL[k]);
                g.dungeon = g.dungeons[k];
                g.level = g.dungeon;
            } else {
                g.dungeon = null;
                g.level = g.field;
            }
        }
        List<Game.Player> seen = new ArrayList<>();
        for (int n = in.readInt(); n > 0; n--) {
            int id = in.readInt();
            String name = in.readUTF();
            Game.Player p = id == myId ? g.me : null;
            for (Game.Player q : g.players) if (p == null && q.id == id && q != g.me) p = q;
            if (p == null) {
                p = new Game.Player(new Hero(0, 0, 0), new Game.Gear(), new Input(), name);
                p.id = id;
                g.players.add(p);
            }
            p.name = name;
            Sync.read(in, p.hero);
            Sync.read(in, p.gear);
            seen.add(p);
        }
        g.players.retainAll(seen);
        if (!g.players.contains(g.me)) g.players.add(0, g.me);
        // Choices made on the gear screen show at once, until the host has them too.
        Game.Gear gear = g.me.gear;
        hostWeapon = gear.weapon;
        hostOutfit = gear.outfit;
        hostDye = gear.dye;
        if (wantWeapon == hostWeapon) wantWeapon = -1;
        if (wantOutfit == hostOutfit) wantOutfit = -1;
        if (wantDye == hostDye) wantDye = -1;
        if (wantWeapon >= 0) gear.weapon = wantWeapon;
        if (wantOutfit >= 0) gear.outfit = wantOutfit;
        if (wantDye >= 0) gear.dye = wantDye;
        Sync.read(in, g.horse);
        int n = in.readInt();
        while (g.foes.size() > n) g.foes.remove(g.foes.size() - 1);
        for (int i = 0; i < n; i++) {
            Foe.Kind kind = Foe.Kind.values()[in.readByte()];
            if (i < g.foes.size() && g.foes.get(i).kind != kind) g.foes.set(i, new Foe(kind, 0, 0, 0));
            if (i == g.foes.size()) g.foes.add(new Foe(kind, 0, 0, 0));
            Sync.read(in, g.foes.get(i));
        }
        int boss = in.readInt(), drake = in.readInt();
        g.boss = boss < 0 ? null : g.foes.get(boss);
        g.drake = drake < 0 ? null : g.foes.get(drake);
        Sync.readList(in, g.shots, Game.Shot::new);
        Sync.readList(in, g.bombs, Game.Bomb::new);
        Sync.readList(in, g.drops, Game.Drop::new);
        Sync.readList(in, g.sparks, Game.Spark::new);
        Sync.readArrays(in, g.magic.stars);
        Sync.readArrays(in, g.magic.rings);
        Sync.readArrays(in, g.waves);
        g.clawOut = in.readBoolean();
        g.clawX = in.readFloat();
        g.clawY = in.readFloat();
        g.clawZ = in.readFloat();
        g.clawDX = in.readFloat();
        g.clawDY = in.readFloat();
        g.clawDZ = in.readFloat();
        Places pl = g.world.places;
        Sync.readList(in, pl.gates, () -> null);
        Sync.readArrays(in, pl.boulders);
        Sync.readArrays(in, pl.pods);
        Sync.readList(in, pl.chests, () -> null);
        Sync.readList(in, pl.npcs, () -> null);
        Sync.readList(in, pl.folk, () -> null);
        for (List<Places.Pickup> chunk : pl.pickups) for (Places.Pickup p : chunk) p.taken = in.readBoolean();
        if (g.dungeon != null) {
            Sync.read(in, g.dungeon);
            Sync.readList(in, g.dungeon.things, () -> new Dungeon.Thing(' ', 0, 0, 0));
        }
    }

    // ---- connections ----------------------------------------------------------------------------

    private void accept() {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                Link l = new Link(s);
                if (links.size() >= MAX - 1) {
                    l.send(FULL, new byte[0]);
                    l.close();
                    continue;
                }
                links.add(l);
                l.start();
            } catch (IOException e) {
                if (!server.isClosed()) System.err.println("Co-op: a friend couldn't connect: " + e.getMessage());
            }
        }
    }

    /** One connection: frames of [length, type, body], read on one thread and written on another. */
    private final class Link {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        private final BlockingQueue<byte[]> outbox = new ArrayBlockingQueue<>(8);
        volatile boolean open = true;
        // Hosting: the friend this link drives, what they brought, and their latest keys and camera.
        Game.Player player;
        volatile byte[] gear;
        String name = "A friend";
        private volatile byte[] keys = new byte[128];
        private volatile float camYaw, camPitch = -.3f;
        private volatile int weapon = -1, outfit = -1, dye = -1;
        /** Dev pack requests waiting for the game thread: tab, row, whole tab. */
        final java.util.Queue<int[]> devs = new java.util.concurrent.ConcurrentLinkedQueue<>();

        Link(Socket s) throws IOException {
            socket = s;
            s.setTcpNoDelay(true);
            in = new DataInputStream(new java.io.BufferedInputStream(s.getInputStream()));
            out = new DataOutputStream(new java.io.BufferedOutputStream(s.getOutputStream()));
        }

        void start() {
            Thread r = new Thread(this::read, "co-op in"), w = new Thread(this::write, "co-op out");
            r.setDaemon(true);
            w.setDaemon(true);
            r.start();
            w.start();
        }

        void send(int type, byte[] body) {
            byte[] frame = new byte[body.length + 5];
            int len = body.length + 1;
            frame[0] = (byte) (len >>> 24);
            frame[1] = (byte) (len >>> 16);
            frame[2] = (byte) (len >>> 8);
            frame[3] = (byte) len;
            frame[4] = (byte) type;
            System.arraycopy(body, 0, frame, 5, body.length);
            // A slow connection skips old worlds rather than falling further behind.
            while (!outbox.offer(frame)) outbox.poll();
        }

        private void write() {
            try {
                while (open) {
                    byte[] f = outbox.take();
                    out.write(f);
                    if (outbox.isEmpty()) out.flush();
                }
            } catch (IOException | InterruptedException e) {
                close();
            }
        }

        private void read() {
            try {
                while (open) {
                    int len = in.readInt();
                    int type = in.readByte();
                    byte[] body = new byte[len - 1];
                    in.readFully(body);
                    DataInputStream b = new DataInputStream(new ByteArrayInputStream(body));
                    switch (type) {
                        case HELLO -> gear = body;
                        case KEYS -> {
                            byte[] k = new byte[128];
                            b.readFully(k);
                            keys = k;
                            camYaw = b.readFloat();
                            camPitch = b.readFloat();
                            weapon = b.readByte();
                            outfit = b.readByte();
                            dye = b.readByte();
                        }
                        case WELCOME -> {
                            myId = b.readInt();
                            g.me.id = myId;
                            hostName = b.readUTF();
                        }
                        case WORLD -> world = body;
                        case DEV -> devs.add(new int[]{b.readByte(), b.readByte(), b.readByte()});
                        case FULL -> {
                            hostName = "That island is full";
                            close();
                        }
                        default -> {}
                    }
                }
            } catch (IOException e) {
                close();
            }
        }

        /** Hosting, on the game thread: this friend's latest keys, camera and gear choices drive their hero. */
        void drive() {
            byte[] k = keys;
            Input pin = player.in;
            for (int c = 0; c < 1024; c++) {
                boolean d = (k[c >> 3] & 1 << (c & 7)) != 0;
                if (d != pin.down(c)) pin.force(c, d);
            }
            player.camYaw = camYaw;
            player.camPitch = camPitch;
            Game.Gear gr = player.gear;
            if (weapon >= 0 && (gr.weapons & 1 << weapon) != 0) gr.weapon = weapon;
            if (outfit >= 0 && (gr.outfits & 1 << outfit) != 0) gr.outfit = outfit;
            if (dye >= 0 && (gr.dyes & 1 << dye) != 0) gr.dye = dye;
        }

        void close() {
            open = false;
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    /** For the self-tests: the host's world as a friend's game would see it, without any network. */
    static void mirror(Game from, Game to) throws IOException {
        Coop out = new Coop(from, true), in = new Coop(to, false);
        in.myId = -1;
        in.apply(out.snapshot());
    }
}

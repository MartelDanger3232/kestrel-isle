package kestrel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens a hosted island to the whole internet through bore.pub, a free public relay (the same one the bore command uses), so friends
 * anywhere can join with a short code: the public port the relay hands out. Each friend who connects there is passed through to the
 * island's own co-op port. If the relay drops the link (the computer slept, say), it reconnects and asks for the same code again.
 * <p>
 * The relay speaks bore's protocol: JSON messages ending in a zero byte on its control port. We say {"Hello":port} and get back
 * {"Hello":port}; then for each visitor it sends {"Connection":id}, and we open a fresh connection with {"Accept":id} and pipe bytes.
 */
final class Tunnel {
    static final String RELAY = "bore.pub";
    private static final int CONTROL = 7835;
    private static final Pattern HELLO = Pattern.compile("\"Hello\"\\s*:\\s*(\\d+)"), CONNECTION = Pattern.compile("\"Connection\"\\s*:\\s*\"([^\"]+)\""),
            ERROR = Pattern.compile("\"Error\"\\s*:\\s*\"([^\"]*)\"");

    private final int local;
    /** The join code (the relay's public port), 0 until the relay answers. */
    volatile int code;
    /** What went wrong last, for the screen; null while all is well. */
    volatile String problem;
    private volatile boolean closed;
    private volatile Socket control;

    private Tunnel(int local, int want) {
        this.local = local;
        code = 0;
        Thread t = new Thread(() -> run(want), "co-op tunnel");
        t.setDaemon(true);
        t.start();
    }

    /** Starts opening the tunnel in the background, asking for the code used last time if there was one. */
    static Tunnel open(int localPort, int lastCode) { return new Tunnel(localPort, lastCode); }

    void close() {
        closed = true;
        try {
            if (control != null) control.close();
        } catch (IOException ignored) {
            // Closing anyway.
        }
    }

    private void run(int want) {
        int ask = want;
        while (!closed) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(RELAY, CONTROL), 8000);
                s.setSoTimeout(20000);
                control = s;
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                send(out, "{\"Hello\":" + ask + "}");
                String reply = read(in);
                Matcher m = HELLO.matcher(reply);
                if (!m.find()) {
                    Matcher e = ERROR.matcher(reply);
                    problem = e.find() ? e.group(1) : "the relay didn't answer properly";
                    // The code we asked for may be taken now: take any.
                    if (ask != 0) {
                        ask = 0;
                        continue;
                    }
                    throw new IOException(problem);
                }
                code = Integer.parseInt(m.group(1));
                ask = code;
                problem = null;
                while (!closed) {
                    String msg = read(in);
                    Matcher c = CONNECTION.matcher(msg);
                    if (c.find()) visitor(c.group(1));
                }
            } catch (IOException e) {
                if (closed) return;
                problem = "Reconnecting to the internet relay... (" + e.getMessage() + ")";
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }
    }

    /** A friend knocked at the relay: fetch their connection and join it to the island's co-op port. */
    private void visitor(String id) {
        Thread t = new Thread(() -> {
            try {
                Socket remote = new Socket();
                remote.connect(new InetSocketAddress(RELAY, CONTROL), 8000);
                send(remote.getOutputStream(), "{\"Accept\":\"" + id + "\"}");
                Socket island = new Socket("127.0.0.1", local);
                pipe(remote, island);
                pipe(island, remote);
            } catch (IOException ignored) {
                // They gave up, or the relay did: nothing to tidy beyond the sockets, which close themselves on error.
            }
        }, "co-op visitor");
        t.setDaemon(true);
        t.start();
    }

    private static void pipe(Socket from, Socket to) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[16384];
            try (from; to) {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
                // One side hung up; closing both ends the other pipe too.
            }
        }, "co-op pipe");
        t.setDaemon(true);
        t.start();
    }

    private static void send(OutputStream out, String json) throws IOException {
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.write(0);
        out.flush();
    }

    /** One message: bytes up to a zero byte. */
    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int c; ; ) {
            c = in.read();
            if (c < 0) throw new IOException("the relay closed the connection");
            if (c == 0) return b.toString(StandardCharsets.UTF_8);
            if (b.size() > 4096) throw new IOException("the relay sent too much");
            b.write(c);
        }
    }
}

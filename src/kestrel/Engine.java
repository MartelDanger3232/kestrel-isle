package kestrel;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.material.MatParam;
import com.jme3.material.MatParamOverride;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.material.TechniqueDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.LightScatteringFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.shader.VarType;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.system.AppSettings;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.jme3.water.WaterFilter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * The game on the graphics card, through jMonkeyEngine: lit and shadowed models, an ocean with reflections, a shader
 * sky, bloom and god rays. The game itself runs exactly as it does in software: Scene's draw calls arrive here through
 * Renderer.gpu, and the HUD is still painted with Java2D, then laid over the frame as a texture. Game space is
 * left-handed (x right, y up, z forward) and the engine's is right-handed, so z changes sign on the way in.
 */
final class Engine extends SimpleApplication implements Renderer.Gpu, RawInputListener {
    private static final float CHUNK_M = World.CHUNK * World.CELL;
    private static final int LAMPS = 8, SOURCES = 64, SETTLE = 32;
    /** jMonkeyEngine key codes to the AWT ones the game reads. */
    private static final int[] VK = new int[256];
    /** Corners a, q, b: flipping z flips handedness, so every triangle's winding flips with it. */
    private static final int[] ORDER = {0, 2, 1};

    static {
        try {
            for (char c = 'A'; c <= 'Z'; c++) VK[KeyInput.class.getField("KEY_" + c).getInt(null)] = KeyEvent.VK_A + c - 'A';
            for (int d = 0; d <= 9; d++) VK[KeyInput.class.getField("KEY_" + d).getInt(null)] = KeyEvent.VK_0 + d;
            for (int f = 1; f <= 12; f++) VK[KeyInput.class.getField("KEY_F" + f).getInt(null)] = KeyEvent.VK_F1 + f - 1;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        int[][] keys = {{KeyInput.KEY_SPACE, KeyEvent.VK_SPACE}, {KeyInput.KEY_ESCAPE, KeyEvent.VK_ESCAPE},
                {KeyInput.KEY_RETURN, KeyEvent.VK_ENTER}, {KeyInput.KEY_NUMPADENTER, KeyEvent.VK_ENTER}, {KeyInput.KEY_TAB, KeyEvent.VK_TAB},
                {KeyInput.KEY_LSHIFT, KeyEvent.VK_SHIFT}, {KeyInput.KEY_RSHIFT, KeyEvent.VK_SHIFT},
                {KeyInput.KEY_LCONTROL, KeyEvent.VK_CONTROL}, {KeyInput.KEY_RCONTROL, KeyEvent.VK_CONTROL},
                {KeyInput.KEY_LMENU, KeyEvent.VK_ALT}, {KeyInput.KEY_RMENU, KeyEvent.VK_ALT},
                {KeyInput.KEY_UP, KeyEvent.VK_UP}, {KeyInput.KEY_DOWN, KeyEvent.VK_DOWN},
                {KeyInput.KEY_LEFT, KeyEvent.VK_LEFT}, {KeyInput.KEY_RIGHT, KeyEvent.VK_RIGHT},
                {KeyInput.KEY_BACK, KeyEvent.VK_BACK_SPACE}, {KeyInput.KEY_DELETE, KeyEvent.VK_DELETE},
                {KeyInput.KEY_COMMA, KeyEvent.VK_COMMA}, {KeyInput.KEY_PERIOD, KeyEvent.VK_PERIOD}, {KeyInput.KEY_SLASH, KeyEvent.VK_SLASH},
                {KeyInput.KEY_MINUS, KeyEvent.VK_MINUS}, {KeyInput.KEY_EQUALS, KeyEvent.VK_EQUALS},
                {KeyInput.KEY_SEMICOLON, KeyEvent.VK_SEMICOLON}, {KeyInput.KEY_APOSTROPHE, KeyEvent.VK_QUOTE},
                {KeyInput.KEY_GRAVE, KeyEvent.VK_BACK_QUOTE}, {KeyInput.KEY_BACKSLASH, KeyEvent.VK_BACK_SLASH},
                {KeyInput.KEY_LBRACKET, KeyEvent.VK_OPEN_BRACKET}, {KeyInput.KEY_RBRACKET, KeyEvent.VK_CLOSE_BRACKET},
                {KeyInput.KEY_HOME, KeyEvent.VK_HOME}, {KeyInput.KEY_END, KeyEvent.VK_END},
                {KeyInput.KEY_PGUP, KeyEvent.VK_PAGE_UP}, {KeyInput.KEY_PGDN, KeyEvent.VK_PAGE_DOWN}};
        for (int[] k : keys) VK[k[0]] = k[1];
    }

    /** Test switches from the command line: a walking, swinging player, and a picture of the last frame. */
    static boolean bot;
    static File snap;
    private boolean snapped;
    private final boolean windowed;
    private final float autoquit;
    private final File shotDir;
    private final Input in = new Input();
    private volatile Game loaded;
    private volatile Throwable failed;
    private Game game;
    private Scene scene;
    private Hud hud;
    private Renderer r;
    private boolean captured, focusLost, settled;
    private int errors, frames, fps, shot = -1, settle;
    private float elapsed, fpsT;
    private File capture;

    private Material lit, litTwo, glowMat, skyMat, flashMat;
    private MatParamOverride fogColor, fogRange;
    private final ColorRGBA fogC = new ColorRGBA(), tmpC = new ColorRGBA(), zenC = new ColorRGBA(), horC = new ColorRGBA(),
            cloudC = new ColorRGBA(), seaC = new ColorRGBA(), deepC = new ColorRGBA(), seaLight = new ColorRGBA(), flashC = new ColorRGBA();
    private final Vector2f fogR = new Vector2f();
    private final Vector3f camAt = new Vector3f(), camDir = new Vector3f(), camUp = new Vector3f(), sunDir = new Vector3f(),
            seaSun = new Vector3f(), skySun = new Vector3f(), rayAt = new Vector3f(), lampAt = new Vector3f();
    private final Quaternion quat = new Quaternion();
    private DirectionalLight sun;
    private AmbientLight amb;
    private final PointLight[] lamps = new PointLight[LAMPS];
    private final float[] srcX = new float[SOURCES], srcY = new float[SOURCES], srcZ = new float[SOURCES], srcR = new float[SOURCES], srcD = new float[SOURCES];
    private final int[] srcC = new int[SOURCES];
    private int sources;
    private DirectionalLightShadowFilter shadows;
    private SSAOFilter ssao;
    private int quality = 2, slow;
    /** Q was pressed once on the pause screen; a second press quits. */
    static boolean quitArmed;
    private WaterFilter water;
    private LightScatteringFilter rays;
    private Geometry sky, hudQuad, flashQuad;
    private BufferedImage hudImg;
    private int[] hudPx;
    private IntBuffer hudInts;
    private Image hudImage;
    private final Node land = new Node("land"), things = new Node("things");
    private Node[][] chunks;
    private int[] shown, want;
    private final long[] missing = new long[World.CHUNKS * World.CHUNKS];
    private final IdentityHashMap<Mesh, Baked> baked = new IdentityHashMap<>();
    private final List<Baked> live = new ArrayList<>();
    private Batch solidTris, clearTris, blades, sprites, specks;
    private final Xf propXf = new Xf();

    private Engine(boolean windowed, float autoquit, File shotDir) {
        super(new AppState[0]);
        this.windowed = windowed;
        this.autoquit = autoquit;
        this.shotDir = shotDir;
    }

    /** Held so the engine's loggers keep the quieter levels set below (loggers nobody holds are forgotten). */
    private static final Logger[] LOGS = {Logger.getLogger("com.jme3"), Logger.getLogger("com.jme3.asset.AssetConfig")};

    /** Opens the game window (full screen unless windowed) and runs until the player quits. */
    static void play(boolean windowed, float autoquit, File shots) {
        // The HUD still paints with Java2D, but AWT must never open a window of its own beside the engine's.
        System.setProperty("java.awt.headless", "true");
        LOGS[0].setLevel(Level.WARNING);
        LOGS[1].setLevel(Level.SEVERE);
        Sculpts.enabled = true;
        Engine app = new Engine(windowed || shots != null, autoquit, shots);
        AppSettings s = new AppSettings(true);
        s.setTitle("Kestrel Isle");
        s.setRenderer(AppSettings.LWJGL_OPENGL32);
        s.setAudioRenderer(null);
        s.setGammaCorrection(false);
        s.setUseRetinaFrameBuffer(false);
        s.setVSync(true);
        s.setDepthBits(24);
        s.setResizable(false);
        if (app.windowed) {
            s.setWindowSize(1280, 720);
            s.setResolution(1280, 720);
        } else {
            s.setFullscreen(true);
            s.setWindowSize(0, 0);
        }
        app.setSettings(s);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        if (inputManager.hasMapping(INPUT_MAPPING_EXIT)) inputManager.deleteMapping(INPUT_MAPPING_EXIT);
        inputManager.addRawInputListener(this);
        inputManager.setCursorVisible(false);
        captured = true;
        renderManager.setPreferredLightMode(TechniqueDef.LightMode.SinglePass);
        renderManager.setSinglePassLightBatchSize(4);
        materials();
        lights();
        effects();
        hudSetup();
        batches();
        skyDome();
        rootNode.attachChild(land);
        rootNode.attachChild(things);
        // The model packs come from packs/ while developing, and from inside the jar once packaged.
        if (new File("packs").isDirectory()) assetManager.registerLocator("packs", FileLocator.class);
        swap(Models.HOUSE, "buildings", "building_home_A", "building_home_B");
        swap(Models.SHOP, "buildings", "building_tavern");
        swap(Models.WELL, "buildings", "building_well");
        swap(Models.TENT, "decoration/props", "tent");
        swap(Models.CASTLE, "buildings", "building_castle");
        swap(Models.BARRACKS, "buildings", "building_barracks");
        swap(Models.CHURCH, "buildings", "building_church");
        swap(Models.TOWER, "buildings", "building_tower_A", "building_tower_B");
        swap(Models.WINDMILL, "buildings", "building_windmill");
        swap(Models.MARKET, "buildings", "building_market");
        swap(Models.SMITHY, "buildings", "building_blacksmith");
        // The pack houses have their own windows; the warm light at night stays.
        swaps.put(Models.HOUSE_LIT, new Swap(new Spatial[0], 0));
        swaps.put(Models.SHOP_LIT, new Swap(new Spatial[0], 0));
        if (shotDir != null) return;
        Thread t = new Thread(() -> {
            try {
                loaded = new Game(in, new Audio(true));
            } catch (Throwable e) {
                failed = e;
            }
        }, "island");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (failed != null) throw new IllegalStateException("couldn't shape the island", failed);
        if (shotDir != null) {
            shots();
            return;
        }
        if (game == null) {
            if (loaded == null) {
                splash();
                return;
            }
            begin(loaded);
        }
        float dt = Math.min(.05f, tpf);
        elapsed += dt;
        if (focusLost) {
            focusLost = false;
            if (game.screen == Game.Screen.PLAY) game.screen = Game.Screen.PAUSE;
        }
        // While playing, the pointer is captured and its travel turns the camera; menus get it back.
        boolean grab = game.screen == Game.Screen.PLAY;
        if (grab != captured) {
            inputManager.setCursorVisible(!grab);
            captured = grab;
        }
        if (bot) drive(dt);
        // A bug in one frame must never freeze the game: report it and keep running.
        try {
            game.update(dt);
            // Q also swaps items, and the game pauses itself whenever the window loses focus, so quitting takes Q twice.
            if (game.screen != Game.Screen.PAUSE) quitArmed = false;
            else if (in.pressed(KeyEvent.VK_Q)) {
                if (quitArmed) quit();
                quitArmed = true;
            }
            draw(dt);
        } catch (RuntimeException e) {
            if (errors++ < 3) {
                System.err.println("Kestrel Isle hit an error but kept running (please report this):");
                e.printStackTrace();
            }
        }
        frames++;
        if ((fpsT += tpf) >= 1) {
            fps = frames;
            frames = 0;
            fpsT = 0;
            // If the frame rate sags for a few seconds, the costliest effects step down (they never come back up).
            slow = fps < 50 && elapsed > 3 ? slow + 1 : 0;
            if (slow >= 3 && quality > 0) {
                lower();
                slow = 0;
            }
        }
        if (autoquit > 0 && elapsed > autoquit) {
            // With --snap the last frame is kept as a picture first.
            if (snap != null && !snapped) {
                capture = snap;
                snapped = true;
                return;
            }
            System.out.println("ran " + autoquit + "s, " + fps + " fps at " + cam.getWidth() + "x" + cam.getHeight() + ", quality " + quality);
            quit();
        }
    }

    /** Leaves co-op properly (a friend's gear goes home to their save), then closes the game. */
    private void quit() {
        if (game != null && game.coop != null) game.coop.leave();
        System.exit(0);
    }

    /** --bot: a test player who keeps walking in a slow circle and swinging, for watching co-op from two windows. */
    private void drive(float dt) {
        if (game == null || game.screen != Game.Screen.PLAY) return;
        in.force(KeyEvent.VK_W, true);
        game.camYaw += dt * .6f;
        boolean swing = (int) (elapsed * 1.5f) % 3 == 0;
        in.force(KeyEvent.VK_J, swing);
    }

    private void lower() {
        if (--quality == 1) {
            ssao.setEnabled(false);
            rays.setNbSamples(24);
        } else {
            shadows.setEdgeFilteringMode(EdgeFilteringMode.PCF4);
            water.setUseHQShoreline(false);
            water.setUseCaustics(false);
        }
    }

    /** Dev screenshots: each gets a freshly posed game and half a second to settle, then a capture. */
    private void shots() {
        if (game == null || settle == SETTLE) {
            String only = System.getProperty("kestrel.shots");
            do {
                if (++shot == Dev.SHOTS.length) {
                    System.out.println("wrote " + shotDir);
                    System.exit(0);
                }
            } while (only != null && !Arrays.asList(only.split(",")).contains(Dev.SHOTS[shot]));
            Story.saving = false;
            Game g = new Game(new Input(), new Audio(false));
            Dev.setup(g, shot);
            begin(g);
            scene.zoom = shot == 3 || shot == 16 ? 2.3f : 0;
            settle = 0;
        }
        draw(1 / 60f);
        if (++settle == SETTLE) {
            shotDir.mkdirs();
            capture = new File(shotDir, Dev.SHOTS[shot] + ".png");
        }
    }

    private void begin(Game g) {
        game = g;
        float aspect = (float) cam.getWidth() / cam.getHeight();
        cam.setFrustumPerspective(60, aspect, Renderer.NEAR, 2800);
        r = new Renderer(Math.max(64, Math.round(360 * aspect) & ~1), 360);
        r.gpu = this;
        scene = new Scene(g, r);
        hud = new Hud(g, r);
        land.detachAllChildren();
        chunks = new Node[World.CHUNKS * World.CHUNKS][World.LODS];
        shown = new int[chunks.length];
        want = new int[chunks.length];
        Arrays.fill(shown, -1);
        // The coarsest level of every chunk exists from the start, so there is always ground to show.
        for (int id = 0; id < chunks.length; id++) chunks[id][World.LODS - 1] = chunk(id % World.CHUNKS, id / World.CHUNKS, World.LODS - 1);
        settled = false;
        clouds(scene.view.cloud);
        if (shotDir == null) Coop.auto(g);
        // -Dkestrel.start=SHOT: skip the title and start posed as that dev screenshot (for measuring speed in a busy place).
        String start = System.getProperty("kestrel.start");
        if (start != null && Arrays.asList(Dev.SHOTS).contains(start)) Dev.setup(g, Arrays.asList(Dev.SHOTS).indexOf(start));
    }

    private void draw(float dt) {
        scene.render(dt);
        finish();
        paintHud();
    }

    // ---- setup ------------------------------------------------------------------------------------

    private void materials() {
        lit = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        lit.setBoolean("UseVertexColor", true);
        lit.setBoolean("UseMaterialColors", true);
        lit.setColor("Ambient", ColorRGBA.White);
        lit.setColor("Diffuse", ColorRGBA.White);
        lit.setColor("Specular", ColorRGBA.Black);
        lit.setFloat("Shininess", 1);
        litTwo = lit.clone();
        litTwo.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        glowMat = fx(-1, 1);
        glowMat.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        // Fog belongs to the whole world, so it is set once on the root and every material below picks it up.
        rootNode.addMatParamOverride(new MatParamOverride(VarType.Boolean, "UseFog", true));
        rootNode.addMatParamOverride(fogColor = new MatParamOverride(VarType.Vector4, "FogColor", fogC));
        rootNode.addMatParamOverride(fogRange = new MatParamOverride(VarType.Vector2, "LinearFog", fogR));
    }

    /** Self-lit material; in the glow pass, colours brighter than glowFrom feed the bloom. */
    private Material fx(float glowFrom, float glowPower) {
        Material m = new Material(assetManager, "Shaders/Fx.j3md");
        m.setFloat("GlowFrom", glowFrom);
        m.setFloat("GlowPower", glowPower);
        return m;
    }

    private void lights() {
        sun = new DirectionalLight();
        amb = new AmbientLight();
        rootNode.addLight(sun);
        rootNode.addLight(amb);
        for (int i = 0; i < LAMPS; i++) {
            lamps[i] = new PointLight();
            lamps[i].setEnabled(false);
            rootNode.addLight(lamps[i]);
        }
    }

    private void effects() {
        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        shadows = new DirectionalLightShadowFilter(assetManager, 2048, 3);
        shadows.setLight(sun);
        shadows.setLambda(.72f);
        shadows.setShadowIntensity(.5f);
        shadows.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
        shadows.setShadowZExtend(170);
        shadows.setShadowZFadeLength(40);
        shadows.setEnabledStabilization(true);
        fpp.addFilter(shadows);
        // Ambient occlusion from the depth buffer alone, saving a whole extra pass over the scene for normals.
        ssao = new SSAOFilter(2.2f, 1.25f, .25f, .1f);
        ssao.setApproximateNormals(true);
        fpp.addFilter(ssao);
        water = new WaterFilter(rootNode, new Vector3f(-.3f, -1, .2f).normalizeLocal());
        water.setWaterHeight(World.WATER);
        water.setWaterTransparency(.14f);
        water.setMaxAmplitude(.32f);
        water.setWaveScale(.006f);
        water.setSpeed(.7f);
        water.setShoreHardness(.55f);
        water.setFoamIntensity(.55f);
        water.setRefractionStrength(.18f);
        water.setUseCaustics(true);
        water.setCausticsIntensity(.45f);
        water.setReflectionMapSize(512);
        water.setSunScale(1.6f);
        fpp.addFilter(water);
        rays = new LightScatteringFilter(new Vector3f(0, 1000, 0));
        rays.setLightDensity(.45f);
        rays.setNbSamples(40);
        rays.setBlurStart(.02f);
        rays.setBlurWidth(.9f);
        fpp.addFilter(rays);
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
        bloom.setBloomIntensity(1.8f);
        bloom.setBlurScale(1.5f);
        bloom.setExposurePower(1.6f);
        bloom.setDownSamplingFactor(2);
        fpp.addFilter(bloom);
        fpp.addFilter(new FXAAFilter());
        viewPort.addProcessor(fpp);
    }

    private void batches() {
        Material solid = fx(.62f, 1), clear = fx(.62f, 1), soft = fx(-1, .9f), speck = fx(-1, .8f);
        for (Material m : new Material[]{solid, clear, soft, speck}) m.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        for (Material m : new Material[]{clear, soft}) {
            m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            m.getAdditionalRenderState().setDepthWrite(false);
        }
        soft.setTexture("ColorMap", softDot());
        solidTris = new Batch("tris", solid, false, false, Bucket.Opaque, ShadowMode.Off);
        clearTris = new Batch("clear", clear, false, false, Bucket.Transparent, ShadowMode.Off);
        blades = new Batch("grass", litTwo, true, false, Bucket.Opaque, ShadowMode.Receive);
        sprites = new Batch("sprites", soft, false, true, Bucket.Transparent, ShadowMode.Off);
        specks = new Batch("specks", speck, false, false, Bucket.Opaque, ShadowMode.Off);
    }

    /** A round, soft-edged dot: sparks, glows and embers are drawn with it. */
    private static Texture2D softDot() {
        int n = 64;
        ByteBuffer b = BufferUtils.createByteBuffer(n * n * 4);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                float dx = (i + .5f) / n * 2 - 1, dy = (j + .5f) / n * 2 - 1, f = M.clamp(1 - M.sqrt(dx * dx + dy * dy), 0, 1);
                b.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) Math.round(f * f * (3 - 2 * f) * 255));
            }
        Texture2D t = new Texture2D(new Image(Image.Format.RGBA8, n, n, b.flip(), ColorSpace.Linear));
        t.setMagFilter(Texture.MagFilter.Bilinear);
        t.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        return t;
    }

    private void skyDome() {
        skyMat = new Material(assetManager, "Shaders/Sky.j3md");
        sky = new Geometry("sky", new Sphere(24, 32, 10));
        sky.setMaterial(skyMat);
        sky.setQueueBucket(Bucket.Sky);
        sky.setCullHint(CullHint.Never);
        sky.setShadowMode(ShadowMode.Off);
        rootNode.attachChild(sky);
    }

    private void clouds(float[] cloud) {
        int n = WorldView.CLOUD;
        ByteBuffer b = BufferUtils.createByteBuffer(n * n * 4);
        for (float v : cloud) {
            byte k = (byte) Math.round(M.clamp(v * .5f, 0, 1) * 255);
            b.put(k).put(k).put(k).put((byte) -1);
        }
        Texture2D t = new Texture2D(new Image(Image.Format.RGBA8, n, n, b.flip(), ColorSpace.Linear));
        t.setWrap(Texture.WrapMode.Repeat);
        t.setMagFilter(Texture.MagFilter.Bilinear);
        t.setMinFilter(Texture.MinFilter.Trilinear);
        skyMat.setTexture("Clouds", t);
    }

    private void hudSetup() {
        int w = cam.getWidth(), h = cam.getHeight();
        if (hudQuad != null) hudQuad.removeFromParent();
        if (flashQuad != null) flashQuad.removeFromParent();
        hudImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        hudPx = ((DataBufferInt) hudImg.getRaster().getDataBuffer()).getData();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        hudInts = buf.asIntBuffer();
        // Java's ARGB ints sit in memory as B, G, R, A bytes, which the card reads directly.
        hudImage = new Image(Image.Format.BGRA8, w, h, buf, ColorSpace.Linear);
        Texture2D tex = new Texture2D(hudImage);
        tex.setMagFilter(Texture.MagFilter.Nearest);
        tex.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", tex);
        m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        hudQuad = new Geometry("hud", new Quad(w, h, true));
        hudQuad.setMaterial(m);
        hudQuad.setLocalTranslation(0, 0, 1);
        flashMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        flashMat.setColor("Color", flashC);
        flashMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        flashQuad = new Geometry("flash", new Quad(w, h));
        flashQuad.setMaterial(flashMat);
        flashQuad.setCullHint(CullHint.Always);
        guiNode.attachChild(flashQuad);
        guiNode.attachChild(hudQuad);
    }

    // ---- each frame -------------------------------------------------------------------------------

    /** Hands this frame's draws, camera, light and weather to the engine. */
    private void finish() {
        for (Baked b : live) {
            hide(b.faceSlots, b.facesUsed);
            hide(b.glowSlots, b.glowsUsed);
            b.facesUsed = b.glowsUsed = 0;
        }
        for (Swap sw : swaps.values()) {
            for (int i = sw.used; i < sw.spots.size(); i++) sw.spots.get(i).setCullHint(CullHint.Always);
            sw.used = 0;
        }
        solidTris.flush();
        clearTris.flush();
        blades.flush();
        sprites.flush();
        specks.flush();
        cam.setLocation(camAt.set(r.cx, r.cy, -r.cz));
        cam.lookAtDirection(camDir.set(r.fx, r.fy, -r.fz), camUp.set(r.ux, r.uy, -r.uz));
        // Light travels away from the sun.
        sun.setDirection(sunDir.set(-r.lx, -r.ly, r.lz).normalizeLocal());
        sun.setColor(tmpC.set(r.sr, r.sg, r.sb, 1));
        amb.setColor(tmpC.set(r.ar, r.ag, r.ab, 1));
        fogColor.setValue(rgba(fogC, r.fogCol));
        fogRange.setValue(fogR.set(r.fogStart, r.fogEnd));
        viewPort.setBackgroundColor(fogC);
        shadows.setShadowIntensity(M.clamp((r.sr + r.sg + r.sb) / 3 * .8f, .12f, .55f));
        boolean field = game.dungeon == null;
        land.setCullHint(field ? CullHint.Inherit : CullHint.Always);
        sky.setCullHint(field ? CullHint.Never : CullHint.Always);
        water.setEnabled(field);
        rays.setEnabled(false);
        if (field) {
            terrain();
            skyAndSea();
        }
        lamps();
        if (r.flash > 0) {
            rgba(flashC, r.flashCol).a = Math.min(1, r.flash * .8f);
            flashQuad.setCullHint(CullHint.Never);
        } else flashQuad.setCullHint(CullHint.Always);
    }

    private void skyAndSea() {
        Climate cl = game.climate;
        float sunVis = M.clamp(cl.sunY * 6 + .3f, 0, 1) * (1 - cl.overcast * .85f), night = M.clamp(-cl.sunY * 4, 0, 1) * (1 - cl.overcast);
        skySun.set(cl.sunX, cl.sunY, -cl.sunZ);
        skyMat.setColor("Zenith", rgba(zenC, cl.zen));
        skyMat.setColor("Horizon", rgba(horC, r.fogCol));
        skyMat.setVector3("SunDir", skySun);
        skyMat.setFloat("SunVis", sunVis);
        skyMat.setFloat("Night", night);
        skyMat.setFloat("Cover", cl.cloudCover);
        skyMat.setFloat("Drift", game.time * 3.5f);
        skyMat.setColor("CloudCol", rgba(cloudC, cl.cloudCol));
        // God rays only while the sun is up and showing through.
        if (cl.sunY > .03f && sunVis > .15f) {
            rays.setEnabled(true);
            rays.setLightPosition(rayAt.set(skySun).multLocal(2000).addLocal(cam.getLocation()));
            rays.setLightDensity(.5f * sunVis);
        }
        // The sea follows the light: dark at night, glinting by day.
        float lvl = M.clamp(r.ar + (r.sr + r.sg + r.sb) / 3 * .9f, .1f, 1.1f);
        water.setWaterColor(scaled(seaC, 0x2f9aa0, lvl));
        water.setDeepWaterColor(scaled(deepC, 0x184a78, lvl));
        water.setLightDirection(seaSun.set(sunDir));
        water.setLightColor(seaLight.set(r.sr, r.sg, r.sb, 1));
    }

    /** The nearest few light sources become real lights; they matter most at night and underground. */
    private void lamps() {
        for (PointLight p : lamps) {
            int best = -1;
            for (int k = 0; k < sources; k++) if (srcD[k] < Float.MAX_VALUE && (best < 0 || srcD[k] < srcD[best])) best = k;
            p.setEnabled(best >= 0);
            if (best < 0) continue;
            srcD[best] = Float.MAX_VALUE;
            p.setPosition(lampAt.set(srcX[best], srcY[best], -srcZ[best]));
            p.setColor(scaled(tmpC, srcC[best], 1.5f));
            p.setRadius(srcR[best]);
        }
        sources = 0;
    }

    /** Picks each chunk's level of detail by distance, building missing ones nearest first, a few per frame. */
    private void terrain() {
        World w = game.world;
        int need = 0;
        for (int id = 0; id < chunks.length; id++) {
            float dx = (id % World.CHUNKS + .5f) * CHUNK_M - r.cx, dz = (id / World.CHUNKS + .5f) * CHUNK_M - r.cz;
            float dy = (w.cMin[id] + w.cMax[id]) * .5f - r.cy, d = M.sqrt(dx * dx + dz * dz + dy * dy * .25f);
            int lod = d - 46 > r.fogEnd + 40 ? -1 : d < 170 ? 0 : d < 360 ? 1 : d < 760 ? 2 : d < 1500 ? 3 : 4;
            want[id] = lod;
            if (lod >= 0 && chunks[id][lod] == null) missing[need++] = (long) (d * 8) << 24 | (long) lod << 20 | id;
        }
        Arrays.sort(missing, 0, need);
        for (int i = 0, n = settled ? Math.min(need, 5) : need; i < n; i++) {
            int id = (int) (missing[i] & 0xfffff), lod = (int) (missing[i] >> 20 & 15);
            chunks[id][lod] = chunk(id % World.CHUNKS, id / World.CHUNKS, lod);
        }
        settled = true;
        for (int id = 0; id < chunks.length; id++) {
            Node[] c = chunks[id];
            int lod = want[id], show = -1;
            if (lod >= 0) {
                for (int l = lod; show < 0 && l < World.LODS; l++) if (c[l] != null) show = l;
                for (int l = lod - 1; show < 0 && l >= 0; l--) if (c[l] != null) show = l;
            }
            if (show != shown[id]) {
                if (shown[id] >= 0) land.detachChild(c[shown[id]]);
                if (show >= 0) land.attachChild(c[show]);
                shown[id] = show;
            }
            // Far-off chunks let go of their detailed levels to keep memory down.
            if (lod < 0 || lod >= 2) for (int l = 0; l < 2; l++) if (l != show) c[l] = null;
        }
    }

    /** One terrain chunk at one level of detail, with its trees and rocks baked in (one draw for all of it). */
    private Node chunk(int ci, int cj, int lod) {
        World w = game.world;
        World.Chunk c = w.chunk(ci, cj, lod);
        Part part = new Part();
        int n = c.n(), nv = n + 1;
        float s = World.CELL * (1 << lod), ox = ci * CHUNK_M, oz = cj * CHUNK_M;
        float[] y = c.y(), nx = c.nx(), ny = c.ny(), nz = c.nz();
        int[] col = c.col();
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = i + j * nv, q = (i + j * n) * 2;
                float x0 = ox + i * s, x1 = x0 + s, z0 = oz + j * s, z1 = z0 + s;
                part.tri(x0, y[k], z0, x0, y[k + nv], z1, x1, y[k + 1], z0, nx[q], ny[q], nz[q], col[q]);
                part.tri(x1, y[k + 1], z0, x0, y[k + nv], z1, x1, y[k + nv + 1], z1, nx[q + 1], ny[q + 1], nz[q + 1], col[q + 1]);
            }
        // Skirts hide the cracks where neighbouring chunks use a different level of detail; wound both ways.
        float drop = 3f * (1 << lod) + 2;
        for (int e = 0; e < 4; e++)
            for (int t = 0; t < n; t++) {
                int k0, k1, q;
                switch (e) {
                    case 0 -> { k0 = t * nv; k1 = k0 + nv; q = t * n * 2; }
                    case 1 -> { k0 = n + t * nv; k1 = k0 + nv; q = (n - 1 + t * n) * 2; }
                    case 2 -> { k0 = t; k1 = t + 1; q = t * 2; }
                    default -> { k0 = t + n * nv; k1 = k0 + 1; q = (t + (n - 1) * n) * 2; }
                }
                float ax = ox + k0 % nv * s, az = oz + k0 / nv * s, bx = ox + k1 % nv * s, bz = oz + k1 / nv * s, ay = y[k0], by = y[k1];
                part.tri(ax, ay, az, bx, by, bz, bx, by - drop, bz, nx[q], ny[q], nz[q], col[q]);
                part.tri(ax, ay, az, bx, by - drop, bz, ax, ay - drop, az, nx[q], ny[q], nz[q], col[q]);
                part.tri(ax, ay, az, bx, by - drop, bz, bx, by, bz, nx[q], ny[q], nz[q], col[q]);
                part.tri(ax, ay, az, ax, ay - drop, az, bx, by - drop, bz, nx[q], ny[q], nz[q], col[q]);
            }
        if (lod <= 2)
            for (Places.Prop p : w.places.props[ci + cj * World.CHUNKS]) {
                if (p.type() == Places.COLLIDER) continue;
                Mesh m = lod == 0 ? Models.PROP_NEAR[p.type()] : Models.PROP_FAR[p.type()];
                if (m != null) part.mesh(m, propXf.identity().translate(p.x(), p.y(), p.z()).rotY(p.yaw()).scale(p.scale()));
            }
        return part.node();
    }

    // ---- what Scene draws -------------------------------------------------------------------------

    @Override
    public void mesh(Mesh m, Xf t, int alpha) {
        Swap sw = swaps.get(m);
        if (sw != null) {
            if (sw.variants.length > 0) place(sw, t);
            return;
        }
        Baked b = baked.get(m);
        if (b == null) {
            baked.put(m, b = new Baked(m));
            live.add(b);
        } else if (b.tris != m.tris) b.bake();
        if (b.faces != null) place(b.faceSlots, b.facesUsed++, b.faces, m.twoSided ? litTwo : lit, true, t, alpha);
        if (b.glows != null) place(b.glowSlots, b.glowsUsed++, b.glows, glowMat, false, t, alpha);
    }

    private void place(List<Slot> slots, int i, com.jme3.scene.Mesh mesh, Material base, boolean faces, Xf t, int alpha) {
        Slot s;
        if (i < slots.size()) s = slots.get(i);
        else {
            slots.add(s = new Slot(mesh, base));
            s.setShadowMode(faces ? ShadowMode.CastAndReceive : ShadowMode.Off);
            things.attachChild(s);
        }
        if (!transform(s, t)) {
            s.setCullHint(CullHint.Always);
            return;
        }
        s.setCullHint(CullHint.Dynamic);
        if (alpha >= 256) {
            if (s.alpha < 256) {
                s.setMaterial(base);
                s.setQueueBucket(Bucket.Inherit);
                s.setShadowMode(faces ? ShadowMode.CastAndReceive : ShadowMode.Off);
            }
        } else {
            if (s.clear == null) {
                s.clear = base.clone();
                s.clear.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
                s.clear.getAdditionalRenderState().setDepthWrite(false);
            }
            if (faces) s.clear.setColor("Diffuse", s.tint.set(1, 1, 1, alpha / 256f));
            else s.clear.setFloat("Alpha", alpha / 256f);
            if (s.alpha >= 256) {
                s.setMaterial(s.clear);
                s.setQueueBucket(Bucket.Transparent);
                s.setShadowMode(ShadowMode.Off);
            }
        }
        s.alpha = alpha;
    }

    private static void hide(List<Slot> slots, int used) {
        for (int i = used; i < slots.size(); i++) slots.get(i).setCullHint(CullHint.Always);
    }

    /** Copies a game transform onto a spatial (mirrored into engine space); false if it squashes the model flat. */
    private boolean transform(Spatial g, Xf t) {
        float[] a = t.m;
        float m00 = a[0], m01 = a[1], m02 = -a[2], m10 = a[4], m11 = a[5], m12 = -a[6], m20 = -a[8], m21 = -a[9], m22 = a[10];
        float sx = M.sqrt(m00 * m00 + m10 * m10 + m20 * m20), sy = M.sqrt(m01 * m01 + m11 * m11 + m21 * m21), sz = M.sqrt(m02 * m02 + m12 * m12 + m22 * m22);
        if (sx < 1e-5f || sy < 1e-5f || sz < 1e-5f) return false;
        if (m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20) < 0) sx = -sx;
        g.setLocalRotation(quat.fromRotationMatrix(m00 / sx, m01 / sy, m02 / sz, m10 / sx, m11 / sy, m12 / sz, m20 / sx, m21 / sy, m22 / sz));
        g.setLocalScale(sx, sy, sz);
        g.setLocalTranslation(a[3], a[7], -a[11]);
        return true;
    }

    @Override
    public void tri(float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz, int col, int alpha) {
        Batch b = alpha >= 256 ? solidTris : clearTris;
        float a = Math.min(1, alpha / 256f);
        b.v(ax, ay, az, col, a);
        b.v(bx, by, bz, col, a);
        b.v(qx, qy, qz, col, a);
    }

    @Override
    public void blade(float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz, int col) {
        // Darker at the root, sunlit at the tip.
        int root = M.scale(col, .8f);
        blades.v(ax, ay, az, root, 1);
        blades.normal();
        blades.v(bx, by, bz, root, 1);
        blades.normal();
        blades.v(qx, qy, qz, M.scale(col, 1.15f), 1);
        blades.normal();
    }

    @Override
    public void point(float x, float y, float z, float size, int col, int alpha) {
        // Specks (bowstrings, chains) stay crisp; everything bigger is a soft glowing dot.
        boolean speck = size < .05f;
        Batch b = speck ? specks : sprites;
        float s = speck ? size : size * 1.9f, a = speck ? 1 : Math.min(1, alpha / 256f);
        float rx = r.rx * s, rz = r.rz * s, ux = r.ux * s, uy = r.uy * s, uz = r.uz * s;
        corner(b, x - rx - ux, y - uy, z - rz - uz, 0, 0, col, a);
        corner(b, x + rx - ux, y - uy, z + rz - uz, 1, 0, col, a);
        corner(b, x + rx + ux, y + uy, z + rz + uz, 1, 1, col, a);
        corner(b, x - rx - ux, y - uy, z - rz - uz, 0, 0, col, a);
        corner(b, x + rx + ux, y + uy, z + rz + uz, 1, 1, col, a);
        corner(b, x - rx + ux, y + uy, z - rz + uz, 0, 1, col, a);
    }

    private static void corner(Batch b, float x, float y, float z, float u, float v, int col, float a) {
        b.v(x, y, z, col, a);
        if (b.uv != null) b.uv(u, v);
    }

    @Override
    public void light(float x, float y, float z, int col, float range) {
        float dx = x - r.cx, dy = y - r.cy, dz = z - r.cz, d = dx * dx + dy * dy + dz * dz;
        if (sources == SOURCES || d > 90 * 90) return;
        int i = sources++;
        srcX[i] = x;
        srcY[i] = y;
        srcZ[i] = z;
        srcC[i] = col;
        srcR[i] = range;
        srcD[i] = d;
    }

    // ---- models from the packs --------------------------------------------------------------------

    private final Map<String, Spatial> protos = new HashMap<>();
    private final Map<Texture, Material> skins = new HashMap<>();

    /** A model from the packs, loaded once (null when it's missing), its materials made ordinary lit ones so fog and light match. */
    private Spatial proto(String path) {
        if (protos.containsKey(path)) return protos.get(path);
        Spatial s = null;
        try {
            s = assetManager.loadModel(path);
            s.depthFirstTraversal(sp -> {
                if (sp instanceof Geometry g) g.setMaterial(relit(g.getMaterial()));
            });
        } catch (AssetNotFoundException e) {
            System.err.println("Kestrel Isle is missing the model " + path);
        }
        protos.put(path, s);
        return s;
    }

    /** The lit material for a pack model's colour texture, shared by everything painted with that texture. */
    private Material relit(Material pbr) {
        MatParamTexture tex = pbr.getTextureParam("BaseColorMap");
        Texture map = tex == null ? null : tex.getTextureValue();
        Material m = map == null ? null : skins.get(map);
        if (m != null) return m;
        m = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        MatParam base = pbr.getParam("BaseColor");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Ambient", ColorRGBA.White);
        m.setColor("Diffuse", base == null ? ColorRGBA.White : (ColorRGBA) base.getValue());
        m.setColor("Specular", ColorRGBA.Black);
        m.setFloat("Shininess", 1);
        if (map != null) {
            m.setTexture("DiffuseMap", map);
            skins.put(map, m);
        }
        return m;
    }

    // ---- pack models standing in for the game's own -----------------------------------------------

    private static final String MEDIEVAL = "medieval/addons/kaykit_medieval_hexagon_pack/Assets/gltf/";
    private static final String[] ROOFS = {"blue", "green", "red", "yellow"};
    private final IdentityHashMap<Mesh, Swap> swaps = new IdentityHashMap<>();

    /** Pack models drawn wherever the game draws one of its own, scaled to its footprint (none: draw nothing). */
    private static final class Swap {
        final Spatial[] variants;
        final float scale;
        final List<Spot> spots = new ArrayList<>();
        int used;

        Swap(Spatial[] variants, float scale) {
            this.variants = variants;
            this.scale = scale;
        }
    }

    /** One placed stand-in, remembering which variant it shows. */
    private static final class Spot extends Node {
        int variant = -1;
    }

    /** Swaps a game model for pack buildings or props from folder (each building in every roof colour). */
    private void swap(Mesh m, String folder, String... names) {
        List<Spatial> found = new ArrayList<>();
        for (String name : names)
            if (folder.equals("buildings")) for (String c : ROOFS) found.add(proto(MEDIEVAL + "buildings/" + c + "/" + name + "_" + c + ".gltf"));
            else found.add(proto(MEDIEVAL + folder + "/" + name + ".gltf"));
        found.removeIf(Objects::isNull);
        if (found.isEmpty()) return;
        // As wide and deep as the game's own model, so walls and doors stay where the island expects them.
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < m.tris * 9; i += 3) {
            minX = Math.min(minX, m.p[i]);
            maxX = Math.max(maxX, m.p[i]);
            minZ = Math.min(minZ, m.p[i + 2]);
            maxZ = Math.max(maxZ, m.p[i + 2]);
        }
        Spatial first = found.get(0);
        first.updateGeometricState();
        com.jme3.bounding.BoundingBox box = (com.jme3.bounding.BoundingBox) first.getWorldBound();
        float scale = Math.max(maxX - minX, maxZ - minZ) / (2 * Math.max(box.getXExtent(), box.getZExtent()));
        swaps.put(m, new Swap(found.toArray(new Spatial[0]), scale));
    }

    private void place(Swap sw, Xf t) {
        int i = sw.used++;
        Spot s;
        if (i < sw.spots.size()) s = sw.spots.get(i);
        else {
            sw.spots.add(s = new Spot());
            s.setShadowMode(ShadowMode.CastAndReceive);
            things.attachChild(s);
        }
        // Each spot on the island always gets the same variant.
        int v = Math.min(sw.variants.length - 1, (int) (M.hash((int) t.m[3], (int) t.m[11]) * sw.variants.length));
        if (v != s.variant) {
            s.detachAllChildren();
            Spatial c = sw.variants[v].clone(false);
            c.setLocalScale(sw.scale);
            s.attachChild(c);
            s.variant = v;
        }
        s.setCullHint(transform(s, t) ? CullHint.Dynamic : CullHint.Always);
    }

    // ---- the HUD ----------------------------------------------------------------------------------

    private void paintHud() {
        int w = cam.getWidth(), h = cam.getHeight();
        if (hudImg.getWidth() != w || hudImg.getHeight() != h) hudSetup();
        Arrays.fill(hudPx, 0);
        Graphics2D g2 = hudImg.createGraphics();
        if (scene.weather > 0) weather(g2, w, h, scene.weather, scene.snowing, game.time);
        hud.draw(g2, w, h);
        g2.dispose();
        upload();
    }

    private void splash() {
        Arrays.fill(hudPx, 0xff141820);
        Graphics2D g2 = hudImg.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(new Color(250, 246, 236));
        g2.setFont(new Font(Font.SERIF, Font.BOLD, Math.max(20, hudImg.getHeight() / 26)));
        String s = "Shaping the island...";
        g2.drawString(s, (hudImg.getWidth() - g2.getFontMetrics().stringWidth(s)) / 2, hudImg.getHeight() / 2);
        g2.dispose();
        upload();
    }

    private void upload() {
        hudInts.clear();
        hudInts.put(hudPx);
        hudImage.setUpdateNeeded();
    }

    /** Rain streaks or snowflakes over the whole view. */
    private static void weather(Graphics2D g2, int w, int h, float amount, boolean snow, float time) {
        float k = h / 360f;
        int drops = (int) (amount * (snow ? 300 : 460)), seed = (int) (time * 60) * 7919;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (snow) {
            g2.setColor(new Color(255, 255, 255, 200));
            int s = Math.max(2, Math.round(2.2f * k));
            for (int i = 0; i < drops; i++) {
                float x = (M.hash(i, 3) * w + time * (8 + M.hash(i, 5) * 14) * k + M.sin(time + i) * 6 * k) % w;
                float y = (M.hash(i, 9) * h + time * (22 + M.hash(i, 7) * 20) * k) % h;
                g2.fillOval((int) x, (int) y, s, s);
            }
        } else {
            g2.setColor(new Color(200, 212, 228, 110));
            g2.setStroke(new BasicStroke(Math.max(1, k * .8f)));
            for (int i = 0; i < drops; i++) {
                int x = (int) (M.hash(i, seed) * w), y = (int) (M.hash(seed, i) * h), len = (int) ((5 + (i & 7)) * k);
                g2.drawLine(x, y, x - len / 3, y + len);
            }
        }
    }

    @Override
    public void simpleRender(RenderManager rm) {
        if (capture == null) return;
        int w = cam.getWidth(), h = cam.getHeight();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        renderer.readFrameBuffer(null, buf);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, (buf.get(i) & 255) << 16 | (buf.get(i + 1) & 255) << 8 | buf.get(i + 2) & 255);
            }
        try {
            ImageIO.write(img, "png", capture);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        capture = null;
    }

    // ---- input and the window ---------------------------------------------------------------------

    @Override public void beginInput() {}
    @Override public void endInput() {}
    @Override public void onJoyAxisEvent(JoyAxisEvent e) {}
    @Override public void onJoyButtonEvent(JoyButtonEvent e) {}
    @Override public void onTouchEvent(TouchEvent e) {}

    @Override
    public void onKeyEvent(KeyInputEvent e) {
        int k = e.getKeyCode();
        if (!e.isRepeating() && k >= 0 && k < VK.length && VK[k] != 0) in.force(VK[k], e.isPressed());
    }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent e) {
        if (captured) in.addDelta(e.getDX(), -e.getDY());
        if (e.getDeltaWheel() != 0) in.scroll(-Integer.signum(e.getDeltaWheel()));
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent e) {
        in.force(e.getButtonIndex() == 0 ? Input.MOUSE_L : Input.MOUSE_R, e.isPressed());
    }

    @Override
    public void loseFocus() {
        super.loseFocus();
        focusLost = true;
        in.release();
    }

    @Override
    public void handleError(String msg, Throwable t) {
        System.err.println("Kestrel Isle's graphics stopped: " + msg);
        if (t != null) t.printStackTrace();
        System.exit(1);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (game != null && game.coop != null) game.coop.leave();
        System.exit(0);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private static ColorRGBA rgba(ColorRGBA out, int c) {
        return out.set((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 1);
    }

    private static ColorRGBA scaled(ColorRGBA out, int c, float k) {
        return out.set((c >> 16 & 255) / 255f * k, (c >> 8 & 255) / 255f * k, (c & 255) / 255f * k, 1);
    }

    /** A game model as the engine holds it, plus the placed copies reused from frame to frame. */
    private final class Baked {
        final Mesh src;
        com.jme3.scene.Mesh faces, glows;
        int tris, facesUsed, glowsUsed;
        final List<Slot> faceSlots = new ArrayList<>(), glowSlots = new ArrayList<>();

        Baked(Mesh m) {
            src = m;
            bake();
        }

        void bake() {
            Part p = new Part();
            p.mesh(src, null);
            faces = p.faces.build(true);
            glows = p.glows.build(false);
            tris = src.tris;
            for (Slot s : faceSlots) s.removeFromParent();
            for (Slot s : glowSlots) s.removeFromParent();
            faceSlots.clear();
            glowSlots.clear();
        }
    }

    /** One placed copy of a model, with its own see-through material for when it is drawn faded. */
    private static final class Slot extends Geometry {
        final ColorRGBA tint = new ColorRGBA(1, 1, 1, 1);
        Material clear;
        int alpha = 256;

        Slot(com.jme3.scene.Mesh m, Material mat) {
            super("slot", m);
            setMaterial(mat);
        }
    }

    /** Triangles gathered in game space, split into lit faces and the glowing ones that light themselves. */
    private final class Part {
        final Tris faces = new Tris(), glows = new Tris();
        private final float[] v = new float[18];

        void tri(float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz, float nx, float ny, float nz, int col) {
            ((col & Mesh.GLOW) != 0 ? glows : faces).add(ax, ay, az, bx, by, bz, qx, qy, qz, nx, ny, nz, nx, ny, nz, nx, ny, nz, col);
        }

        /** Adds a whole model, moved by t (or as it is when t is null). */
        void mesh(Mesh m, Xf t) {
            float[] p = m.p, a = t == null ? null : t.m;
            float inv = a == null ? 1 : 1 / M.sqrt(a[0] * a[0] + a[4] * a[4] + a[8] * a[8]);
            for (int i = 0; i < m.tris; i++) {
                int o = i * 9;
                for (int k = 0; k < 3; k++) {
                    int c = o + k * 3;
                    float x = p[c], y = p[c + 1], z = p[c + 2];
                    float nx = m.smooth ? m.vn[c] : m.n[i * 3], ny = m.smooth ? m.vn[c + 1] : m.n[i * 3 + 1], nz = m.smooth ? m.vn[c + 2] : m.n[i * 3 + 2];
                    if (a != null) {
                        float tx = a[0] * x + a[1] * y + a[2] * z + a[3], ty = a[4] * x + a[5] * y + a[6] * z + a[7], tz = a[8] * x + a[9] * y + a[10] * z + a[11];
                        float tnx = (a[0] * nx + a[1] * ny + a[2] * nz) * inv, tny = (a[4] * nx + a[5] * ny + a[6] * nz) * inv, tnz = (a[8] * nx + a[9] * ny + a[10] * nz) * inv;
                        x = tx;
                        y = ty;
                        z = tz;
                        nx = tnx;
                        ny = tny;
                        nz = tnz;
                    }
                    v[k * 3] = x;
                    v[k * 3 + 1] = y;
                    v[k * 3 + 2] = z;
                    v[9 + k * 3] = nx;
                    v[9 + k * 3 + 1] = ny;
                    v[9 + k * 3 + 2] = nz;
                }
                ((m.c[i] & Mesh.GLOW) != 0 ? glows : faces).add(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8],
                        v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17], m.c[i]);
            }
        }

        Node node() {
            Node n = new Node("part");
            com.jme3.scene.Mesh f = faces.build(true), gl = glows.build(false);
            if (f != null) {
                Geometry g = new Geometry("faces", f);
                g.setMaterial(lit);
                g.setShadowMode(ShadowMode.CastAndReceive);
                n.attachChild(g);
            }
            if (gl != null) {
                Geometry g = new Geometry("glows", gl);
                g.setMaterial(glowMat);
                n.attachChild(g);
            }
            return n;
        }
    }

    /** A growing list of triangles in game space. */
    private static final class Tris {
        float[] p = new float[9 * 64], n = new float[9 * 64];
        int[] c = new int[64];
        int t;

        void add(float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz,
                 float anx, float any, float anz, float bnx, float bny, float bnz, float qnx, float qny, float qnz, int col) {
            if (t == c.length) {
                p = Arrays.copyOf(p, p.length * 2);
                n = Arrays.copyOf(n, n.length * 2);
                c = Arrays.copyOf(c, c.length * 2);
            }
            int o = t * 9;
            p[o] = ax; p[o + 1] = ay; p[o + 2] = az; p[o + 3] = bx; p[o + 4] = by; p[o + 5] = bz; p[o + 6] = qx; p[o + 7] = qy; p[o + 8] = qz;
            n[o] = anx; n[o + 1] = any; n[o + 2] = anz; n[o + 3] = bnx; n[o + 4] = bny; n[o + 5] = bnz; n[o + 6] = qnx; n[o + 7] = qny; n[o + 8] = qnz;
            c[t++] = col;
        }

        /** The engine's mesh of these triangles, or null when there are none. */
        com.jme3.scene.Mesh build(boolean normals) {
            if (t == 0) return null;
            FloatBuffer pb = BufferUtils.createFloatBuffer(t * 9), cb = BufferUtils.createFloatBuffer(t * 12);
            FloatBuffer nb = normals ? BufferUtils.createFloatBuffer(t * 9) : null;
            for (int i = 0; i < t; i++) {
                int col = c[i];
                float cr = (col >> 16 & 255) / 255f, cg = (col >> 8 & 255) / 255f, cl = (col & 255) / 255f;
                for (int corner : ORDER) {
                    int k = i * 9 + corner * 3;
                    pb.put(p[k]).put(p[k + 1]).put(-p[k + 2]);
                    if (nb != null) nb.put(n[k]).put(n[k + 1]).put(-n[k + 2]);
                    cb.put(cr).put(cg).put(cl).put(1);
                }
            }
            com.jme3.scene.Mesh m = new com.jme3.scene.Mesh();
            m.setBuffer(VertexBuffer.Type.Position, 3, pb.flip());
            if (nb != null) m.setBuffer(VertexBuffer.Type.Normal, 3, nb.flip());
            m.setBuffer(VertexBuffer.Type.Color, 4, cb.flip());
            m.updateBound();
            m.setStatic();
            return m;
        }
    }

    /** Triangles rebuilt every frame (grass, trails, sparks), all sent in one draw. */
    private final class Batch {
        final com.jme3.scene.Mesh mesh = new com.jme3.scene.Mesh();
        final Geometry geo;
        float[] pos = new float[3 * 4096], col = new float[4 * 4096], nor, uv;
        int n;
        private FloatBuffer pb, cb, nb, ub;

        Batch(String name, Material mat, boolean normals, boolean uvs, Bucket bucket, ShadowMode shadow) {
            if (normals) nor = new float[3 * 4096];
            if (uvs) uv = new float[2 * 4096];
            geo = new Geometry(name, mesh);
            geo.setMaterial(mat);
            geo.setQueueBucket(bucket);
            geo.setShadowMode(shadow);
            geo.setCullHint(CullHint.Always);
            rootNode.attachChild(geo);
        }

        void v(float x, float y, float z, int c, float a) {
            if (n * 3 == pos.length) {
                pos = Arrays.copyOf(pos, pos.length * 2);
                col = Arrays.copyOf(col, col.length * 2);
                if (nor != null) nor = Arrays.copyOf(nor, nor.length * 2);
                if (uv != null) uv = Arrays.copyOf(uv, uv.length * 2);
            }
            pos[n * 3] = x;
            pos[n * 3 + 1] = y;
            pos[n * 3 + 2] = -z;
            col[n * 4] = (c >> 16 & 255) / 255f;
            col[n * 4 + 1] = (c >> 8 & 255) / 255f;
            col[n * 4 + 2] = (c & 255) / 255f;
            col[n * 4 + 3] = a;
            n++;
        }

        /** The last vertex faces straight up. */
        void normal() {
            nor[n * 3 - 3] = 0;
            nor[n * 3 - 2] = 1;
            nor[n * 3 - 1] = 0;
        }

        void uv(float u, float v) {
            uv[n * 2 - 2] = u;
            uv[n * 2 - 1] = v;
        }

        void flush() {
            if (n == 0) {
                geo.setCullHint(CullHint.Always);
                return;
            }
            geo.setCullHint(CullHint.Dynamic);
            pb = upload(VertexBuffer.Type.Position, pb, pos, n * 3, 3);
            cb = upload(VertexBuffer.Type.Color, cb, col, n * 4, 4);
            if (nor != null) nb = upload(VertexBuffer.Type.Normal, nb, nor, n * 3, 3);
            if (uv != null) ub = upload(VertexBuffer.Type.TexCoord, ub, uv, n * 2, 2);
            mesh.updateCounts();
            geo.updateModelBound();
            n = 0;
        }

        private FloatBuffer upload(VertexBuffer.Type type, FloatBuffer fb, float[] src, int len, int comps) {
            if (fb == null || fb.capacity() < len) {
                fb = BufferUtils.createFloatBuffer(src.length);
                fb.put(src, 0, len).flip();
                mesh.clearBuffer(type);
                mesh.setBuffer(type, comps, fb);
                mesh.getBuffer(type).setUsage(VertexBuffer.Usage.Stream);
                return fb;
            }
            fb.clear();
            fb.put(src, 0, len).flip();
            mesh.getBuffer(type).updateData(fb);
            return fb;
        }
    }
}

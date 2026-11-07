# Kestrel Isle: The Windflute

An original 3D action-adventure in Java. It draws on the graphics card with [jMonkeyEngine](https://jmonkeyengine.org): real-time sun shadows, an ocean with reflections and foam, glow and god rays, and torches and campfires that light their surroundings. It has a synthesized soundtrack and hand-built dungeons. The original software renderer is still there as a fallback (`--classic`).

![Kestrel Isle screenshots](shots/sheet.png)

The Hollow has stolen the Wardstones of Kestrel Isle. Starting in the village of Brindle, you recover them from Rootdeep Hollow, the Cinder Forge and the Tidewell Vault. Then you head north through the snow to Rimeholt, an ice-hold on a sheer mesa, for the fourth stone. Last, you descend into the Hollow Crown to face the Hollow King, and to learn who he used to be.

Along the way:

- **Side quests:** Tibb, Ansel, Oskar, Hessa and Sela each need a hand. The satchel (Tab) lists them under Requests, and blue diamonds mark where to go.
- **Sands and skies:** in the eastern desert, Sela's nomads point you to the **Sunscour Temple**:
  - bend sunbeams with its mirrors to open its doors;
  - fight burrowing Dune Stalkers and **Ashmaw**, a giant sand wyrm;
  - claim **Wren's Windcloak**, a glider.
- **The Windcloak:** press Space in mid-air to glide. Warm air over campfires and desert vents carries you up, and old offerings wait on top of the desert mesas. Its wind also teaches **Skyward Gust**, a spell that throws you 13 m into the sky from anywhere outdoors, so you can take off from flat ground. Cast it while locked on to a foe (or a duelling friend) and the wind holds you up there, facing them, until you strike with an attack spell.
- **The Stormdrake:** once you fly, a colossal drake rides the storms over the mountain. Darken its three glowing storm-scales to bring it down.
- **Parry:** raise your shield just as a blow lands. The attacker reels, and your next sword strike is a riposte for triple damage.
- **Arsenal and attire:** press I for the gear screen.
  - **Weapons:** five, each with its own reach, speed, weight and charged move:
    - the Keeper's Sword;
    - the huge Emberfall Greatsword;
    - the long Tidecaller Spear;
    - the lightning-fast Wind Fangs;
    - the Rimebreaker Hammer, which smashes walls, ice, rubble and shields.
  - **Outfits:** six that change your look and give a perk: faster stamina, speed and longer glides, heavy armour, stronger spells or harder blows.
  - **Cape dyes:** colour your cape.
  - **Where to find them:** Oskar, Hessa, Sela and Pell sell gear, friends give it as thanks, and treasure chests hide the rest.
- **The grapple claw** works out in the world too:
  - grapple the gold-lit posts on cliffs, spires and sea stacks;
  - lock on and yank enemies to you;
  - tear shields away;
  - fetch things that are out of reach.
- **A living island:** deer, foxes, rabbits and birds bolt when you come near, fish jump, and there are butterflies by day and fireflies by night. Villagers stroll around and head home after dark, windows light up, and chimneys smoke.

## Download and play

Get the latest build from the [Releases page](https://github.com/MartelDanger3232/kestrel-isle/releases/latest). The Mac, Windows and Linux downloads include their own Java, so there's nothing else to install.

- **Mac (Apple Silicon: Apple menu → About This Mac says "Chip: Apple M…"):**
  - **Easiest:** macOS blocks games that Apple hasn't notarized, so install it with one line in Terminal (open Terminal from Spotlight, paste the line, press Return). It downloads the game into your Applications folder and opens it:

        curl -fsSL https://raw.githubusercontent.com/MartelDanger3232/kestrel-isle/main/install-mac.sh | bash

    After that it opens from Launchpad like any other app. Run the same line again to update. (It runs [install-mac.sh](install-mac.sh).)
  - **Already downloaded it in your browser?** Drag **Kestrel Isle** into Applications, then run `xattr -cr "/Applications/Kestrel Isle.app"` in Terminal and open it again.
- **Windows:** download `KestrelIsle-Windows.zip`, unzip it, and run `KestrelIsle.exe` inside the `KestrelIsle` folder. If SmartScreen warns you, click **More info**, then **Run anyway**.
- **Linux:** download `KestrelIsle-Linux.tar.gz`, extract it, and run `KestrelIsle/bin/KestrelIsle`.
- **Anything else with Java 22 or newer, including Intel Macs:** download `kestrel-isle.jar` and run `java -jar kestrel-isle.jar`.

## Run from source

You need JDK 22 or newer. The first run downloads the engine (jMonkeyEngine and LWJGL, about 12 MB) from Maven Central into `lib/`, and the KayKit model packs (about 41 MB) from GitHub into `packs/`, checking every file against `lib.sha256` and `packs.sha256`.

    ./run.sh

The characters' bodies are sculpted in Blender by `art/blender/humanoid.py`. After changing it, rebuild them with `blender -b --factory-startup -P art/blender/humanoid.py -- res/Models/humanoid.kscl`.

`./package.sh` builds a self-contained app for your own computer into `build/app`. Publishing a GitHub release builds the Windows, Mac and Linux downloads automatically (`.github/workflows/release.yml`).

It always runs full screen. Esc pauses, then Q quits.

Progress autosaves to `save.properties` in a `.kestrel-isle` folder in your home folder (`~/.kestrel-isle` on Mac and Linux, `C:\Users\<you>\.kestrel-isle` on Windows). Choose **Continue** on the title screen to resume.

## Co-op (up to 4 players)

Play the island together, each on your own computer, all with the same version of the game.

- **Host:** choose **Host co-op** on the title screen. Your save carries on as the shared world, and a **join code** (like 24179) appears at the bottom of the screen. It works over the internet, with nothing to set up: the game opens its own tunnel through the free bore.pub relay, and asks for the same code each time you host.
- **Join:** choose **Join co-op**, type (or paste) the host's join code, and press Enter. A local address like 192.168.1.20 works too. You bring your own hero and gear from your own save, and the gleams and items you pick up go home with you when you leave.
- **Together:** foes go after whoever is nearest, and everyone can fight, cast and pick things up. The host leads the story: they talk to people, open dungeons and fast-travel, and friends follow them in. Friends keep playing while the host is in a menu, but the island waits while the host talks with someone. A fallen friend gets back up beside the host a few seconds later. The grappling claw and riding Juniper are the host's. Friends can open the dev pack (`[`) too.
- **Duels:** press **P** to say you're ready to duel (your name tag turns red). Two duellists can lock on to each other with Shift and fight with swords, arrows and spells; blocks and parries work as against foes, and a parried attacker reels. A duel ends when someone is down to their last heart: friends never kill each other.
- **Connecting:** the join code is all a friend needs, anywhere. It's the relay's port, so `bore.pub:24179` typed in full works too. Friends on the same Wi-Fi can instead type the host's local address (like `192.168.1.20`); Tailscale addresses and port forwarding (TCP 47777) also work.

## Towns, the law and the sea

- **Towns:** Brindle has grown into a market town, and up the road to Rootdeep stands **Kingsreach**, the royal city, with its castle, barracks, church, markets and towers. Both are full of townsfolk going about their day; talk to them for gossip.
- **The law:** you can attack anyone, but it's a crime. Strike someone and you're **wanted** (one star); kill someone, or a guard, and it climbs, up to five. The royal guard come after you, more of them for every star (knights from four stars up), and townsfolk run. While a guard can see you, the stars flash and more guards arrive. Get far enough away and out of sight, and when the bar under the stars runs out you're no longer wanted. You can't rest at a shrine while wanted, and dying clears it. The island's named folk are only knocked out; townsfolk can be killed and come back a while later.
- **Jobs:** each town has a job board with three jobs a day: monster culling, bounties on outlaw gangs, deliveries by road, pearl diving and clearing monster nests. Jobs pay gleams and earn renown (Newcomer, Known Name, Trusted Hand, Hero of the Realm, Legend of the Isle). While wanted, the board also takes fines (150 gleams a star) to clear your name.
- **Homes and carriages:** each town has a house for sale (2500 gleams); sleep there to rest, level up and save, as at a shrine. Carriages run between Brindle, Kingsreach, the nomad camp and the rangers' outpost for 40 gleams a ride.
- **Pickpocketing:** press **L** behind a townsperson to lift some gleams. From behind it usually works; to their face it usually doesn't, and getting caught makes you wanted.
- **Underwater:** while swimming, hold **Ctrl** to dive. The shallows hold reefs of kelp and coral, schools of fish, pearls, and wrecks and drowned ruins with chests in them. Your breath (the bubbles) lasts 30 seconds; after that you start to drown.

## Controls

| | |
|---|---|
| WASD | move |
| Mouse or arrow keys | camera |
| Space | roll; while locked on, side-hop (A/D) or back-flip (S); in mid-air, open or fold the Windcloak; in rising warm air, take off |
| Shift (hold) | lock on to the nearest foe in front (or duelling friend); with nobody there it does nothing |
| P | co-op: ready to duel (both players press it to fight each other) |
| Ctrl (swimming) | dive; hold Ctrl to go deeper, Space to rise |
| L | pick the pocket of the townsperson in front of you |
| Click / J | sword (3-hit combo; hold, then release for a spin attack) |
| K / right-click | shield (raise it just as a blow lands to parry) |
| F | use item (bow and claw: hold to aim, release to fire; bombs: F to light, F to throw) |
| Q | switch item |
| C · V | cast spell · switch spell (spells use spirit, the blue bar) |
| R | drink an Ember Draught (refilled at Lantern Shrines) |
| T, then 1-5 | play the windflute (the Song of the Steed calls Juniper the horse) |
| E | talk, read, open, ride/dismount, rest at a shrine |
| G | send Juniper away (the Song of the Steed calls her back) |
| H | hint for wherever you are |
| Tab · I · M · Esc | satchel · gear (weapons, outfits, dyes) · map · pause (Q quits from pause) |

Rolling, swinging and blocking use stamina (the green bar). Rest at Lantern Shrines to heal, refill draughts, level up five stats and fast-travel. Resting brings monsters back. If you fall, your gleams stay where you died until you reach them again. Each boss teaches a spell, Oskar the smith sharpens your sword for gleams, and holding all four Wardstones makes you Ascended.

## Dev flags

**Dev keys:** `` ` `` (above Tab) completes the current objective (inside a dungeon it finishes the whole dungeon and hands over its rewards), `=` teleports you to the current objective, and `]` gives 1000 gleams. `[` opens the **dev pack**: every weapon, outfit, dye, tool, song, spell, stone and supply in the game, ready to put straight into your inventory (E takes one, F takes a whole tab).

Blast Pods (glowing orange plants) give 3 bombs each and regrow, so you can't get stuck without bombs.

`--test` runs scripted checks of movement, combat, songs, puzzles, bosses and saving. `--bench` times rendering. `--shots DIR` writes headless screenshots from the software renderer, `--gpu-shots DIR` the same scenes from the graphics card. `--dev-window` runs in a window, and `--autoquit SECONDS` reports the frame rate. If the frame rate sags for a few seconds, the costliest effects (ambient occlusion first) switch off by themselves. For testing co-op on one computer: `--host` or `--join ADDRESS` skip the title screen, `--name NAME` sets your player name, `--bot` makes your hero walk in circles and swing, `--snap FILE` keeps a picture of the last frame, and `-Dkestrel.save=FILE` plays from another save.

## Credits

Town buildings: the [KayKit](https://kaylousberg.com) Medieval Hexagon pack by Kay Lousberg (CC0).

Graphics: [jMonkeyEngine](https://jmonkeyengine.org) and [LWJGL](https://www.lwjgl.org), both under the BSD 3-Clause license, with GLFW (zlib license) and jemalloc (BSD 2-Clause); glTF models load through Gson (Apache 2.0). Their license texts are in [THIRD-PARTY.md](THIRD-PARTY.md) and [licenses/](licenses), which also ship inside the game.

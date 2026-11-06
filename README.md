# Kestrel Isle: The Windflute

An original 3D action-adventure in pure Java (no libraries). It uses its own software renderer, a synthesized soundtrack and hand-built dungeons.

![Kestrel Isle screenshots](shots/sheet.png)

The Hollow has stolen the Wardstones of Kestrel Isle. Starting in the village of Brindle, you recover them from Rootdeep Hollow, the Cinder Forge and the Tidewell Vault. Then you head north through the snow to Rimeholt, an ice-hold on a sheer mesa, for the fourth stone. Last, you descend into the Hollow Crown to face the Hollow King, and to learn who he used to be.

Along the way:

- **Side quests:** Tibb, Ansel, Oskar, Hessa and Sela each need a hand. The satchel (Tab) lists them under Requests, and blue diamonds mark where to go.
- **Sands and skies:** in the eastern desert, Sela's nomads point you to the **Sunscour Temple**:
  - bend sunbeams with its mirrors to open its doors;
  - fight burrowing Dune Stalkers and **Ashmaw**, a giant sand wyrm;
  - claim **Wren's Windcloak**, a glider.
- **The Windcloak:** press Space in mid-air to glide. Warm air over campfires and desert vents carries you up, and old offerings wait on top of the desert mesas.
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

You need JDK 22 or newer.

    ./run.sh

`./package.sh` builds a self-contained app for your own computer into `build/app`. Publishing a GitHub release builds the Windows, Mac and Linux downloads automatically (`.github/workflows/release.yml`).

It always runs full screen. Esc pauses, then Q quits.

Progress autosaves to `save.properties` in a `.kestrel-isle` folder in your home folder (`~/.kestrel-isle` on Mac and Linux, `C:\Users\<you>\.kestrel-isle` on Windows). Choose **Continue** on the title screen to resume.

## Controls

| | |
|---|---|
| WASD | move |
| Mouse or arrow keys | camera |
| Space | roll; while locked on, side-hop (A/D) or back-flip (S); in mid-air, open or fold the Windcloak; in rising warm air, take off |
| Shift (hold) | lock on to the nearest foe |
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

**Dev keys:** `` ` `` (above Tab) completes the current objective (inside a dungeon it finishes the whole dungeon and hands over its rewards), `=` teleports you to the current objective, and `]` gives 1000 gleams.

Blast Pods (glowing orange plants) give 3 bombs each and regrow, so you can't get stuck without bombs.

`--test` runs scripted checks of movement, combat, songs, puzzles, bosses and saving. `--bench` times rendering. `--shots DIR` writes headless screenshots.

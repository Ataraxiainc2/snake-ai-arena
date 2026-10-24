# Snake Battle Royale — Kotlin arena

The same game as `SNAKES.py`, in Kotlin, for tactics written in Kotlin (`kotlin/tactics/Lab-Model_Name.kt`, rules in
`kotlin/tactics/rules_kotlin.txt`). It runs on the PC (JVM) for simulations, tournaments and a live window, and its engine is
the one the phone arena (IssaArcade in EmuGaMind) is meant to use: no Android or desktop code in `arena.api` and
`arena.engine`.

```bash
cd kotlin
./gradlew run --args="view --human"         # builds if needed, then the live window (you steer one snake)
./gradlew run --args="run --hours 2 --fps 0" # unattended tournament, as fast as possible
```

Or build once (`./gradlew installDist`) and call `~/.snake-arena/build/install/snake-arena-kotlin/bin/snake-arena-kotlin <command>`
(`.bat` on Windows). Gradle runs on Java 27 (`gradle/gradle-daemon-jvm.properties`; it downloads that JDK where it is
missing) and the classes target Java 26, the newest that Kotlin 2.4 emits and Android's d8 accepts; Gradle and Kotlin
come with the wrapper. The entries live in `kotlin/tactics/`
(one `Lab-Model_Name.kt` each, rules in `kotlin/tactics/rules_kotlin.txt`).

| Command | What it does |
|---|---|
| `run [--hours H] [--fps 0] [--cells 195x90] [--snakes N] [--refs] ...` | `SNAKES.py --unattended` for `.kt` tactics: rounds, learning DQN snakes in the spare slots, `kotlin/telemetry_kotlin.json` and `kotlin/q_tables_kotlin.bin` every `--save-minutes` (5), changed files reloaded, and a new game when a new file is ready (best scores carry over) |
| `view [--human] [--refs] [--fps 15]` | live window (Swing); arrows steer your snake, `+`/`-` speed, `P` pause, `Esc` quit |
| `check [kotlin/tactics/X.kt ...]` | audit + compile + the timing battery of `SNAKES.py --check` + a 400-snake crowd test |
| `bench [--snakes 400] [--cells 390x180] [--threads T] [--dqn N]` | times a real game: engine, decide phase, per tactic |
| `parity trace.txt ...` | replays games recorded from `SNAKES.py` (below) and stops at the first difference |
| `sync` | an empty `kotlin/tactics/Lab-Model_Name.kt` for every model of models.dev (SNAKES.py's catalog, same names) and every Python entry that has none; never touches an existing file. `run` and `view` do it at start (`--no-sync` skips it); empty files do not play |

`--refs` adds the organizer's exact Kotlin ports of three Python tactics (`ref-Moonshot_AI-Kimi_K3_High`,
`ref-Anthropic-Claude_Opus_5.5_IAN`, `ref-Anthropic-Claude_Opus_5.5_NAI`, plus `ref-Skeleton`). They are test and
benchmark material, not entries, and take no part in the code-size factor.

## Layout

```
kotlin/
  tactics/                                  the entries (Lab-Model_Name.kt) and rules_kotlin.txt
  src/main/kotlin/arena/api/Api.kt          the tactic API (SnakeTactic, Move, Enemy, Perception) — the contract
  src/main/kotlin/arena/engine/             the game: Arena (tick), Features (state vector), Body, Snake, Rules,
                                            PyRandom (CPython's Mersenne Twister), FoodRace, Brain (DQN)
  src/main/kotlin/arena/loader/             Audit (source + bytecode) and TacticLoader (compile, cache, hot reload)
  src/main/kotlin/arena/reference/          exact ports of Python tactics (+ Parity-Chaos, test only)
  src/main/kotlin/arena/*.kt                CLI: Main, Runner (run), Viewer (view), Check, Bench, Parity, Telemetry
  src/test/                                 PyRandom vs CPython, audit, a 20,000-tick SNAKES.py game replayed
  parity/python_trace.py                    records a SNAKES.py game tick by tick (imports SNAKES.py unchanged)
  parity/tactics/Parity-Chaos.py            test-only tactic: exceptions, invalid moves, wall dives
```

`kotlin/` holds only sources and `tactics/`. Everything generated goes to `~/.snake-arena/` (outside the arena
folder): the build (`build.gradle.kts`), Gradle's and Kotlin's project caches (`gradlew` passes them), compiled
tactics cached by content (`tactics/`), `launches.log`, the one-game lock and the organizer key. The arena folder
only gets `kotlin/telemetry_kotlin.json` (what the models read) and `kotlin/q_tables_kotlin.bin` (DQN weights and best
scores); nothing of the Kotlin arena is written next to `SNAKES.py`.

On the organizer's PC `organizer init` locks the arena to a key in `~/.snake-arena/organizer.key`: without it `run`,
`view`, `check`, `bench` and `sync` refuse to start, and every start or refusal is logged in `launches.log` with the
programs that launched it. Another PC or GitHub's check has no key file and needs none.

## Same game as SNAKES.py, move for move

The engine is a port of `Game.step_simulation` and everything it calls, in cells instead of pixels:

* Tick order: everyone chooses (from the grid of the start of the turn) → everyone moves (wrap only if the score
  before the move is < 200) → the collision grid is rebuilt (after the move, before eating) → in list order each
  snake eats the first food (in list order) on its head, then is checked for death with the score after eating
  (self, wall, enemy, starvation, in that order) → rewards → the dead drop food on every 2nd cell (only inside the
  board, while foods < num_food + 25) and respawn in list order. If the episode ends inside that loop, everyone
  respawns and the remaining dead of the turn still drop food and respawn, as `died_map` does.
* The grid is SNAKES.py's `collision_grid` (sets of names per cell) as counts of distinct snakes per cell, so "another
  snake is here" and "the killer is `sorted(names)[0]`" give the same answers, including snakes that already died
  or grew earlier in the same turn.
* Food and respawn positions and the random moves after an exception, an invalid move or a timeout come from
  `PyRandom`, CPython's `random` (seed, `randint`, `choice`, `random()`), called in the same order.
* Distances that SNAKES.py compares with `math.hypot` are compared as exact squared integers (same order, same
  first-on-ties). The 23 state values (`state_vector_16` + planning features, including the depth-first flood fill
  that stops at its cap) are reproduced exactly.

Proof: `parity/python_trace.py` runs `SNAKES.py` unchanged (only the chosen tactics, no DQN, no time limit; its output
files go to a temp folder) and writes every tick: foods in order, and per snake score, direction, hunger, rule flags,
reward, death flag, best score, body and the 23 state values. `parity` replays it with the ports. All identical:

| Trace | Ticks | Snakes | Board | Deaths | Episodes | Best |
|---|---|---|---|---|---|---|
| seed 1 | 3,000 | 3 | 50x35 | self 1 | 0 | 214 |
| seed 2 | 40,000 | 3 | 50x35 | self 24, enemy 1 | 8 | 306 |
| seed 3 | 25,000 | 12 | 50x35 | self 43, enemy 4, starvation 2 | 4 | 302 |
| seed 4 | 20,000 | 15 | 192x108 | self 15, enemy 1, starvation 7 | 1 | 305 |
| seed 7 (chaos) | 30,000 | 6 | 50x35 | self 34, wall 5, enemy 2 | 6 | 302 |
| seed 11 (chaos, digest, in the tests) | 20,000 | 8 | 40x30 | self 28, wall 7 | 4 | 304 |

```bash
python -B kotlin/parity/python_trace.py --out trace.txt --seed 2 --ticks 40000 --cells 50x35 --foods 8
python -B kotlin/parity/python_trace.py --out t.txt --tactics Parity-Chaos,Moonshot_AI-Kimi_K3_High --copies 3
./gradlew run --args="parity trace.txt"      # paths are relative to the arena folder
```

(`--digest` stores one CRC per tick: small enough for `src/test/resources`.) Games with DQN snakes are not
replayable against Python: their exploration and sampling use their own seeded RNG here, `random` + `torch` there.

## Tactic files

`TacticLoader` does what `SNAKES.py` does with `.py`: empty files are skipped; the rest is audited, compiled with the
embedded Kotlin compiler into `package arena.tactics.<id>` (+ `import arena.api.*`), audited again on the bytecode
and loaded with its own class loader (a new one every game: statics do not survive between games). A file that
changes during a game is reloaded at the next save; a broken new version keeps the old one.

The audit has two layers (`loader/Audit.kt`): the source (rules_kotlin.txt section 4: imports, `java.`/`android.`…
names, `System`, `Thread`, reflection, printing, top-level and object `var`s, with comments and strings removed but
string templates kept) and the bytecode: every class a compiled tactic refers to must be its own, `arena.api`, the
Kotlin standard library minus `kotlin.io/.system/.concurrent/.time/.reflect`, or a short allow-list of `java.lang`
and `java.util` classes. The bytecode layer is what is hard to get around (it caught `StringBuffer`, which no
name list mentions).

## DQN snakes

`engine/Brain.kt` is `SNAKES.py`'s "unified" mode: one Double DQN 23 → 64 → 64 → 4 shared by every DQN snake, Huber
loss, gradients clipped to norm 2, Adam (PyTorch's defaults, lr 5e-4), Polyak target update τ = 0.01, γ = 0.92,
ε from 1 to 0.02 over ~1500 steps per snake, the safety shield and the hunger drive, a 30,000-step replay buffer fed
by every snake (tactics too), one training batch of 64 per turn. Weights, ε and each name's best score persist in
`q_tables_kotlin.bin` (the ranking keeps the max across runs, as `q_tables.pkl` does). In a 10-snake test on 192x108
(4 reference tactics, 6 DQN from zero) the DQN snakes reached 266 after 22,000 turns and 307 after 131,000 (3 minutes
at 730 turns/s).

## Speed (400 snakes, 390 x 180 cells, 320 foods)

`bench` with the three ports filling 400 slots (bodies ~80 cells on average after the warm-up):

| | PC (JVM) | Galaxy S25 Ultra (ART) |
|---|---|---|
| Engine alone (Skeleton tactics), 1 thread | 1.55 ms/turn | 0.67 ms/turn (1,494 turns/s) |
| Kimi-style tactics, own food race, 1 thread | 0.69 ms/call, 206 ms/turn | 0.30 ms/call, 89 ms/turn |
| … same, 8 threads (phone) / 16 (PC) | 26.9 ms/turn | 29 ms/turn, but 0.75 ms/call: with the time limit on, **every** call timed out |
| Kimi-style tactics with `foodRivalDistance`, 1 thread | 0.012 ms/call, 6.5 ms/turn | 0.005 ms/call, 2.7 ms/turn (370 turns/s) |
| … same, 8 threads | — | 1.18 ms/turn (848 turns/s), no timeouts |
| + 3 learning DQN snakes (every snake feeds the buffer), 8 threads | 7.2 ms/turn | 3.4 ms/turn (297 turns/s) |

For comparison, the py2kt translation of the Python files in EmuGaMind measured 2.09 ms per move on the PC JVM and
7.8 ms on the S25 on the same kind of crowd (best typed version), and the hand-typed Kimi K3 0.435 ms on the S25.

## Findings for the phone arena (IssaArcade)

1. **Let the board compute what every snake needs.** The cost at 400 snakes is not the engine (0.2–0.5 ms per turn)
   but each tactic redoing the same O(foods × enemies) food race: 128,000 distances per move, 51 million per turn.
   `Perception.foodRivalDistance(i)` (`engine/FoodRace.kt`) computes it once per turn and board mode (nearest head,
   its owner and the second nearest, so each snake excludes itself): the ports went from 0.30 to 0.005 ms per call
   on the S25 with identical decisions (the parity traces still match). It is now in `rules_kotlin.txt`.
   `bodies` (per-cell counts) is the same idea; more of that kind (a per-turn distance field from all heads, the
   free area per cell) would cut the next costs the same way.
2. **Per-call wall-clock budgets are unfair with threads on a big.LITTLE phone.** With 8 threads the same call took
   2.5× longer (little cores, contention, heat), so a 0.25 ms budget turned every move random at 4–8 threads while
   the turn itself was 3× faster. Options: measure each call in thread CPU time (`Debug.threadCpuTimeNanos()` on
   Android), run tactics only on the big cores, or budget the turn and not the call.
3. **Native Kotlin tactics + the board's shared data beat translation** by two orders of magnitude (0.005 ms vs
   2–8 ms per move). The translator remains useful as a parity oracle for Python files.
4. The engine reuses per turn the body arrays, `Enemy` objects, valid moves and the foods snapshot (a new array only
   when a length changes).
5. **Memory is the phone's hard limit, not CPU.** Most tactics keep three `IntArray(width * height)`: ~822 KB per
   snake on 390 x 180, **~321 MB for 400**, more than a normal app's 256 MB heap (the test app crashed until it used
   `android:largeHeap`). The perception now lends per-thread scratch (`marks`, `queue`, `newMark()`), the rules set
   256 KB per snake, and `check` reports each tactic's memory (a tactic that uses `marks`/`queue` keeps ~0 KB).
6. **Live test on the S25 Ultra** (temporary APK: 400 snakes = the 11 real files copied + 3 DQN, 390 x 180, the
   board drawn as one 390 x 180 bitmap scaled to the screen with `lockHardwareCanvas`, no filtering):

   | | 1 thread, 15 turns/s |
   |---|---|
   | screen | **120–128 fps** (the display's 120 Hz); drawing 0.3 ms/frame, building the pixel grid 0.15 ms/turn |
   | turn | ~24 ms (fits the 66 ms of 15 turns/s) |
   | CPU | ~50% of one core, clocks at 1–2 GHz, 33–35 °C steady over the test |
   | timeouts | 11 of 447,000 calls, timed with `Debug.threadCpuTimeNanos()` (`ArenaConfig.callClock`) |

   With 8 threads the same game cost *more* CPU (~55%) and wall-clock timing turned 0.27% of the calls into random
   moves (the scheduler parks threads on slow cores mid-call). At the live pace use 1–2 threads and the thread CPU
   clock; more threads only pay off at full speed (1,494 turns/s engine-only, 848 with 8 threads and real tactics).

## IssaArcade in EmuGaMind (the phone app)

* EmuGaMind plays this arena as a catalog game, "Snake Battle Royale" (console IssaArcade). Its
  `tools/issaarcade/sync_kotlin.py --from D:\SC\PYGAMES` (or from GitHub without `--from`) copies `arena/api`,
  `arena/engine` and `arena/loader/Audit.kt` unchanged into its `:issaarcade` module and the non-empty
  `kotlin/tactics/*.kt` into the app. Edit the engine here, never there: these sources must stay free of desktop-only
  APIs (the JVM CPU clock lives in `arena/Clock.kt` for that reason).
* The app compiles the tactics into the APK the way `TacticLoader` does (`package arena.tactics.<id>` +
  `import arena.api.*`, the same ids) and generates the list of `Tactic()` factories; the engine is a separate module,
  so the tactics see only its public API. A unit test runs `Audit.source` and `Audit.bytecode` on them every build.
* Simulations on the phone without the app (headless, from Android Studio's terminal in `D:\SC\PYGAMES\kotlin`):
  `pack` compiles the tactic files, `phoneDex` turns engine + tactics + stdlib into DEX
  (`~/.snake-arena/build/phone/arena.zip`), and ART runs it with `dalvikvm64`:

```bash
.\gradlew run --args="pack"
.\gradlew phoneDex
adb shell mkdir -p /data/local/tmp/arena
adb push "$env:USERPROFILE\.snake-arena\build\phone\arena.zip" "$env:USERPROFILE\.snake-arena\build\phone\tactics.txt" /data/local/tmp/arena/
adb shell "cd /data/local/tmp/arena && dalvikvm64 -Xmx512m -cp arena.zip arena.MainKt bench --precompiled tactics.txt --fill-loaded --snakes 400 --dqn 3 --threads 1 --cells 390x180 --warmup 300 --ticks 1800 --fps 15 --report-sec 15"
```

  `--fps 15` plays at the live pace (use `--fps 0` for flat out); `--report-sec` prints turns/s, tick time, the
  process's CPU and the hottest CPU sensor with the core clocks. `-Xmx2g` is refused by the S25. With Git Bash
  instead of PowerShell, set `MSYS_NO_PATHCONV=1` first and use `~/.snake-arena/...` paths.
* Same game on both: the PC uses the phone's board (195 x 90, 390 x 180 above 100 snakes; `Rules.board`) and times
  each call by its thread's CPU time (`--wall-clock` for the wall clock). `bench --no-time-limit --digest 1000`
  prints a CRC of the whole game state every 1000 turns: run with the same seed on the PC and with `dalvikvm64` on
  the phone it gave identical lines for 5,000 turns of 40 snakes, with and without DQN snakes (2026-10-22).
* `phone-telemetry` copies the app's telemetry (same format, `"platform": "android"`) to
  `kotlin/telemetry_phone.json` with adb; `run` and `view` do it by themselves at every save (each episode, every 5
  minutes, the end) when a phone is connected. The PC's carries `"platform": "pc"`.
* EmuGaMind's build takes a changed tactic from this folder only after it passes: it runs this arena's `check` on
  the changed files itself (the installed program, `gradlew installDist`), and a file that fails keeps its previous
  version in the app.
* Java: Gradle runs on Java 27 (`./gradlew updateDaemonJvm --jvm-version=27` with the foojay resolver plugin, here and
  in EmuGaMind) and the code targets Java 26, the newest d8 accepts (build-tools 37: 26 yes, 27 "unsupported class
  file major version 71"). EmuGaMind needs the Kotlin Gradle plugin 2.4 on its build classpath for that: AGP 9.4's
  built-in Kotlin 2.2.10 stops at Java 24.

Not done yet: a watchdog for a tactic that never returns (it blocks the turn, as in SNAKES.py), a check that no
tactic wrote into the shared `bodies`/`foods` arrays, SNAKES.py's "separate" DQN mode, mentor distillation and
`game_analysis_report.txt`.

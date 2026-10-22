"""Records a game of SNAKES.py, tick by tick, for the Kotlin arena to replay (kotlin/parity).

    python -B kotlin/parity/python_trace.py --out trace.txt [--seed 1] [--ticks 20000] [--cells 50x35] [--foods 8]
                                            [--tactics Moonshot_AI-Kimi_K3_High,Anthropic-Claude_Opus_5.5_IAN,...]

It imports SNAKES.py unchanged and drives Game.step_simulation with only the given tactics (no DQN snake, no human,
no time limit), after random.seed(seed). Every output file of the game (q_tables.pkl, report, telemetry.json) goes to
a temporary folder, never next to SNAKES.py. Each line after the header is one tick: the foods in list order and,
per snake, score, direction, turns without food, warning, rule on, last reward, died, body cells and the 23-value
state it chose with. Cells are y * width + x. With --copies, the snakes are the tactics in order, then again.
With --digest each tick is only the CRC-32 of that line without the states (reward x 10 as an integer): small enough
to keep as a test resource (kotlin/src/test/resources).
"""
import argparse
import os
import random
import sys
import tempfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ARENA = os.path.dirname(os.path.dirname(HERE))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--ticks", type=int, default=20000)
    ap.add_argument("--cells", default="50x35")
    ap.add_argument("--foods", type=int, default=8)
    ap.add_argument("--tactics", default="Moonshot_AI-Kimi_K3_High,Anthropic-Claude_Opus_5.5_IAN,Anthropic-Claude_Opus_5.5_NAI")
    ap.add_argument("--copies", type=int, default=1, help="snakes per tactic (copies are named <file>#2, #3, ...)")
    ap.add_argument("--digest", action="store_true", help="one CRC-32 per tick instead of the full state (small files for tests)")
    a = ap.parse_args()
    out_path = os.path.abspath(a.out)
    w, h = (int(v) for v in a.cells.lower().split("x"))
    names = [n for n in a.tactics.split(",") if n]

    os.environ["SDL_VIDEODRIVER"] = "dummy"
    os.environ["SDL_AUDIODRIVER"] = "dummy"
    sys.path.insert(0, ARENA)
    work = tempfile.mkdtemp(prefix="snakes_parity_")
    os.chdir(work)  # q_tables.pkl, game_analysis_report.txt and telemetry.json land here
    import SNAKES  # noqa: E402

    tactics, labels = [], []
    for k in range(a.copies):
        for n in names:
            path = os.path.join(ARENA, "tacticas", n + ".py")
            if not os.path.exists(path):  # test-only tactics (Parity-Chaos)
                path = os.path.join(HERE, "tactics", n + ".py")
            t, reason = SNAKES.load_tactic_file(path)
            if t is None:
                sys.exit(f"{n}: {reason}")
            if k:
                t["name"] = f"{t['name']}#{k + 1}"
            tactics.append(t)
            labels.append(t["name"])
    SNAKES.apply_code_factors(tactics)
    SNAKES.load_external_tactics = lambda folder=None, sync=True: (tactics, len(tactics), 0)
    SNAKES.MAX_DECISION_TIME_SEC = float("inf")

    random.seed(a.seed)
    bs = SNAKES.BLOCK_SIZE
    game = SNAKES.Game(len(tactics), a.foods, False, headless=True, arena_size=(w * bs, h * bs), sync_catalog=False)

    def cell(p):
        x, y = p[0] // bs, p[1] // bs
        return y * w + x if 0 <= x < w and 0 <= y < h else -1

    def fmt(v):
        return repr(float(v))

    with open(out_path, "w", encoding="utf-8", newline="\n") as out:
        out.write(f"seed={a.seed} ticks={a.ticks} cells={w}x{h} foods={a.foods} tactics={','.join(labels)}"
                  + (" digest=1" if a.digest else "") + "\n")
        for _ in range(a.ticks):
            game.step_simulation(0.05)
            parts = [str(game.frame_counter), ",".join(str(cell(f.posicion)) for f in game.foods)]
            for s in game.snakes:
                fields = [
                    str(int(s.puntaje)), str(SNAKES.ACTIONS.index(s.direccion)), str(s.turns_without_food),
                    str(int(s.self_collision_warning_active)), str(int(s.self_collision_enabled)),
                    str(round(s.last_reward * 10)) if a.digest else fmt(s.last_reward),
                    str(int(s.died_last_step)), str(int(s.max_score_achieved)),
                    ",".join(str(cell(c)) for c in s.segmentos),
                ]
                if not a.digest:
                    fields.append(",".join(fmt(v) for v in s.prev_state))
                parts.append(";".join(fields))
            line = "|".join(parts)
            out.write("%08x\n" % (zlib.crc32(line.encode()) & 0xffffffff) if a.digest else line + "\n")
    print(f"{a.ticks} ticks -> {out_path} (episodes {game.episode_count}; scratch {work})")


if __name__ == "__main__":
    main()

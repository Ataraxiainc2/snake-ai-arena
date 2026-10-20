"""Pre-checks tactic files the way SNAKES.py loads them: AST anti-cheat audit, then a battery of
random game positions (both grid sizes, every rule phase) measuring errors, invalid moves and time.

    python tools/check_tactic.py                      # every non-empty tacticas/*.py
    python tools/check_tactic.py tacticas/My-Model.py # only these files

Exit code 1 if any file fails. Run it on GitHub (pull request check) before running strangers'
code on your own PC: the audit is a filter, not a sandbox."""
import ast
import importlib.util
import os
import random
import sys
import time
import gc

sys.dont_write_bytecode = True
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIRS = {'UP': (0, -1), 'DOWN': (0, 1), 'LEFT': (-1, 0), 'RIGHT': (1, 0)}
LIMIT_MS = 15.0


def engine_lists():
    """Reads FORBIDDEN_MODULES / FORBIDDEN_CALLS straight from SNAKES.py so the lists never drift."""
    tree = ast.parse(open(os.path.join(ROOT, "SNAKES.py"), encoding="utf-8").read())
    found = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and isinstance(node.targets[0], ast.Name):
            if node.targets[0].id in ("FORBIDDEN_MODULES", "FORBIDDEN_CALLS"):
                found[node.targets[0].id] = ast.literal_eval(node.value)
    return found["FORBIDDEN_MODULES"], found["FORBIDDEN_CALLS"]


def audit(src, modules, calls):
    for n in ast.walk(ast.parse(src)):
        if isinstance(n, ast.Import):
            for a in n.names:
                if a.name.split('.')[0] in modules:
                    return f"forbidden import '{a.name}'"
        elif isinstance(n, ast.ImportFrom) and n.module and n.module.split('.')[0] in modules:
            return f"forbidden import from '{n.module}'"
        elif isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id in calls:
            return f"forbidden call '{n.func.id}()'"
        elif isinstance(n, ast.Attribute) and n.attr.startswith('__') and n.attr not in {'__init__', '__name__', '__len__'}:
            return f"restricted dunder '{n.attr}'"
    return None


def walk(w, h, length, wrap, rnd):
    x, y = rnd.randrange(w), rnd.randrange(h)
    body = [(x, y)]
    for _ in range(length - 1):
        dx, dy = rnd.choice(list(DIRS.values()))
        x, y = body[-1][0] + dx, body[-1][1] + dy
        x, y = (x % w, y % h) if wrap else (min(max(x, 0), w - 1), min(max(y, 0), h - 1))
        body.append((x, y))
    return body


def perception(w, h, score, rnd, bs=20):
    px = lambda c: (c[0] * bs, c[1] * bs)
    me = [px(c) for c in walk(w, h, min(score + 1, 900), score < 200, rnd)]
    enemies = []
    for i in range(14):
        s = rnd.choice([0, 5, 50, 150, 250, 310, 400])
        eb = [px(c) for c in walk(w, h, min(s + 1, 900), s < 200, rnd)]
        enemies.append({'id': i, 'head': eb[0], 'body': tuple(eb), 'direction': rnd.choice(list(DIRS)), 'score': float(s)})
    return {
        'my_name': 'Candidate', 'my_head': me[0], 'my_body': tuple(me), 'my_direction': rnd.choice(list(DIRS)),
        'my_score': float(score), 'width': w * bs, 'height': h * bs, 'block_size': bs,
        'foods': tuple(px((rnd.randrange(w), rnd.randrange(h))) for _ in range(12)), 'enemies': tuple(enemies),
        'active_rules': {'self_collision': score >= 100, 'self_collision_warning': 100 <= score < 110,
                         'wall_collision': score >= 200, 'enemy_collision': score >= 300, 'toroidal_world': score < 200},
        'state_vector_16': (0.0,) * 16, 'previous_reward': rnd.choice([0.0, 15.0, -1.2]), 'died_last_step': False,
        'valid_actions': tuple(DIRS) if score == 0 else tuple(rnd.sample(list(DIRS), 3)),
        'turns_without_food': rnd.choice([0, 500, 1200]), 'starvation_turns': 1500,
    }


def check(path, modules, calls):
    name = os.path.basename(path)
    rel = os.path.relpath(path, ROOT).replace("\\", "/")
    if not (rel.startswith("tacticas/") and rel.endswith(".py") and rel.count("/") == 1):
        return False, f"{rel}: entries must be a single tacticas/<Provider-Model_Name>.py file"
    src = open(path, encoding="utf-8").read()
    if not src.strip():
        return False, f"{name}: empty file"
    reason = audit(src, modules, calls)
    if reason:
        return False, f"{name}: DISQUALIFIED by the anti-cheat audit ({reason})"
    try:
        spec = importlib.util.spec_from_file_location("candidate", path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        agent = mod.create_agent() if hasattr(mod, "create_agent") else mod
        decide = agent.decide_action
    except Exception as e:
        return False, f"{name}: cannot be loaded ({e!r})"
    rnd = random.Random(7)
    errors = invalid = 0
    times = []
    first = ""
    for w, h in ((192, 108), (50, 35)):
        for score in (0, 20, 99, 120, 199, 210, 299, 305, 420):
            for _ in range(12):
                p = perception(w, h, score, rnd)
                gc.disable()
                t = time.perf_counter()
                try:
                    a = decide(p)
                except Exception as e:
                    a, errors, first = None, errors + 1, first or repr(e)
                times.append((time.perf_counter() - t) * 1000)
                gc.enable()
                invalid += a not in p['valid_actions']
    times.sort()
    n = len(times)
    p50, p99 = times[n // 2], times[int(n * 0.99)]
    line = (f"{name}: {len(src)} chars, {n} calls, errors={errors}, invalid={invalid}, "
            f"median={p50:.2f} ms, p99={p99:.2f} ms, max={times[-1]:.2f} ms")
    if errors or invalid:
        return False, line + f"  FAIL {first}"
    if p50 > LIMIT_MS:
        return False, line + f"  FAIL median above {LIMIT_MS} ms"
    if p99 > LIMIT_MS:
        line += f"  WARNING: p99 above {LIMIT_MS} ms (slow turns become random moves)"
    return True, line


def main(argv):
    modules, calls = engine_lists()
    files = [os.path.abspath(f) for f in argv] or sorted(
        os.path.join(ROOT, "tacticas", f) for f in os.listdir(os.path.join(ROOT, "tacticas"))
        if f.endswith(".py") and os.path.getsize(os.path.join(ROOT, "tacticas", f)) > 0)
    ok = True
    for f in files:
        good, line = check(f, modules, calls)
        ok &= good
        print(("PASS " if good else "FAIL ") + line, flush=True)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

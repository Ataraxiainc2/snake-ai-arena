MODEL_NAME = "AI_Competitor"
D = {'UP': (0, -1), 'DOWN': (0, 1), 'LEFT': (-1, 0), 'RIGHT': (1, 0)}


class Agent:
    def decide_action(self, p):
        va = [a for a in p['valid_actions'] if a in D] or ['UP']
        b = p['block_size']
        W = p['width'] // b
        H = p['height'] // b
        wrap = not p['active_rules']['wall_collision']
        sc = p['my_score']
        body = [(q[0] // b, q[1] // b) for q in p['my_body']]
        head, tail, need = body[0], body[-1], len(body) + 8
        fs = [(f[0] // b, f[1] // b) for f in p['foods']]
        B = set(body[:-1]) if sc > 98 else set()
        eh = []
        for e in p['enemies']:
            if sc >= 298:
                B.update((q[0] // b, q[1] // b) for q in e['body'][:-1])
            eh.append((e['head'][0] // b, e['head'][1] // b))

        def nb(c, d):
            x, y = c[0] + d[0], c[1] + d[1]
            if wrap:
                return (x % W, y % H)
            return (x, y) if 0 <= x < W and 0 <= y < H else None

        def dist(a, c):
            dx, dy = abs(a[0] - c[0]), abs(a[1] - c[1])
            if wrap:
                dx, dy = min(dx, W - dx), min(dy, H - dy)
            return dx + dy

        def area(s):
            seen, st = {s}, [s]
            while st and len(seen) < need:
                c = st.pop()
                for d in D.values():
                    n = nb(c, d)
                    if n is None or n in seen or n in B:
                        continue
                    if n == tail:
                        return need, True
                    seen.add(n)
                    st.append(n)
            return len(seen), False

        # Food race: aim for foods I reach before every enemy head; BFS (no tight cap) to the nearest one.
        G = {f for f in fs if dist(head, f) <= min([dist(h, f) for h in eh] or [dist(head, f)])} or set(fs)
        first = {head: None}
        q = [head]
        goal = None
        i = 0
        while i < len(q) and i < 12000:
            c = q[i]
            i += 1
            if c in G:
                goal = first[c]
                break
            for a, d in D.items():
                n = nb(c, d)
                if n is None or n in first or n in B:
                    continue
                first[n] = first[c] or a
                q.append(n)
        tgt = min(fs, key=lambda f: dist(head, f)) if fs else tail
        cands = []
        for a in va:
            n = nb(head, D[a])
            if n is None or n in B:
                continue
            dang = sum(dist(n, h) <= 1 for h in eh) if sc >= 298 else 0
            ar, cyc = area(n)
            tier = 2 if cyc else (1 if ar >= need else 0)
            cands.append((tier > 0 and a == goal, tier, -dang, -dist(n, tgt), a))
        return max(cands)[4] if cands else va[0]


def create_agent():
    return Agent()

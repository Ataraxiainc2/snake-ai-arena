MODEL_NAME = "AI_Competitor"
D = {'UP': (0, -1), 'DOWN': (0, 1), 'LEFT': (-1, 0), 'RIGHT': (1, 0)}


class Agent:
    def decide_action(self, p):
        va = [a for a in p['valid_actions'] if a in D] or ['UP']
        b = p['block_size']
        W = p['width'] // b
        H = p['height'] // b
        wrap = not p['active_rules']['wall_collision']
        body = [(q[0] // b, q[1] // b) for q in p['my_body']]
        head = body[0]
        tail = body[-1]
        L = len(body)
        fs = set((f[0] // b, f[1] // b) for f in p['foods'])
        sc = p['my_score']
        B = set(body[:-1]) if sc > 98 else set()
        eh = []
        for e in p['enemies']:
            eb = [(q[0] // b, q[1] // b) for q in e['body']]
            if sc >= 298:
                B.update(eb[:-1])
            eh.append((e['head'][0] // b, e['head'][1] // b))
        need = 2 * L

        def nb(c, d):
            x = c[0] + d[0]
            y = c[1] + d[1]
            if wrap:
                return (x % W, y % H)
            return (x, y) if 0 <= x < W and 0 <= y < H else None

        def dist(a, c):
            dx = abs(a[0] - c[0])
            dy = abs(a[1] - c[1])
            if wrap:
                dx = min(dx, W - dx)
                dy = min(dy, H - dy)
            return dx + dy

        def area(s):
            seen = {s}
            st = [s]
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

        first = {head: None}
        q = [head]
        i = 0
        goal = None
        while i < len(q) and i < 1500:
            c = q[i]
            i += 1
            if c != head and c in fs:
                goal = first[c]
                break
            for a, d in D.items():
                n = nb(c, d)
                if n is None or n in first or n in B:
                    continue
                first[n] = first[c] or a
                q.append(n)

        cands = []
        for a in va:
            n = nb(head, D[a])
            if n is None or n in B:
                continue
            dang = sum(1 for h in eh if dist(n, h) <= 1)
            if sc >= 298 and dang:
                continue
            ar, cyc = area(n)
            tier = 2 if cyc else (1 if ar >= need else 0)
            cands.append((tier, a, dang))
        if not cands:
            return va[0]
        return max(cands, key=lambda c: (c[1] == goal and c[0] == 2, c[0], -c[2]))[1]


def create_agent():
    return Agent()

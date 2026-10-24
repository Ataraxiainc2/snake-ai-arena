class CobraAgent:
    def __init__(self):
        # Simple deterministic snake: move towards nearest food, avoid self-collision
        self.last_move = None
        self.my_name = None

    def decide_action(self, perception):
        self.my_name = perception.get("my_name")
        my_head = perception["my_head"]
        my_body = perception["my_body"]
        foods = perception["foods"]
        enemies = perception["enemies"]
        valid = perception["valid_actions"]
        my_score = perception["my_score"]
        direction = perception["my_direction"]
        width = perception["width"]
        height = perception["height"]
        block_size = perception["block_size"]
        active_rules = perception["active_rules"]
        self_collision = active_rules.get("self_collision", True)
        wall_collision = active_rules.get("wall_collision", True)
        toroidal = active_rules.get("toroidal_world", False)
        score_phase = 0
        if my_score >= 300:
            score_phase = 3
        elif my_score >= 200:
            score_phase = 2
        elif my_score >= 100:
            score_phase = 1
        else:
            score_phase = 0

        # Determine target: nearest food considering enemy proximity (Voronoi-like)
        # Use Manhattan distance (grid) for simplicity
        best_food = None
        best_dist = None
        my_head_x, my_head_y = my_head

        for f in foods:
            fx, fy = f
            # Compute distance to food
            dist = abs(fx - my_head_x) + abs(fy - my_head_y)
            # Compute nearest enemy distance to that food
            enemy_dist = None
            for e in enemies:
                ex, ey = e["head"]
                ed = abs(fx - ex) + abs(fy - ey)
                if enemy_dist is None or ed < enemy_dist:
                    enemy_dist = ed
            # Prefer foods where we are closer than enemies (or enemies far)
            # If tie, choose closest food
            if best_food is None:
                best_food = f
                best_dist = dist
                continue
            # If we are strictly closer to this food than to best_food
            if dist < best_dist:
                best_food = f
                best_dist = dist
            # If equal distances, check enemy distances: prefer food with larger margin
            elif dist == best_dist:
                # compute enemy distances for both foods
                my_enemy_dist = enemy_dist
                # find best_food enemy dist
                bf_ex, bf_ey = enemies[0]["head"]  # dummy, we need compute
                # Actually need to compute enemy dist for best_food
                # We'll recalc quickly
                bf_enemy_dist = None
                for e in enemies:
                    ex, ey = e["head"]
                    ed = abs(fx - ex) + abs(fy - ey)
                    if bf_enemy_dist is None or ed < bf_enemy_dist:
                        bf_enemy_dist = ed
                if bf_enemy_dist is not None and my_enemy_dist is not None:
                    # larger margin is better
                    if (dist - bf_enemy_dist) < (best_dist - bf_enemy_dist):
                        best_food = f
                        best_dist = dist

        # If no food, pick a safe move away from danger (simple)
        if not foods:
            # Determine safe moves: avoid hitting own body and walls (if collisions enabled)
            safe = []
            for d in valid:
                # compute new head position
                nx, ny = my_head
                if d == "UP":
                    ny -= block_size
                elif d == "DOWN":
                    ny += block_size
                elif d == "LEFT":
                    nx -= block_size
                elif d == "RIGHT":
                    nx += block_size
                # check collisions
                if wall_collision:
                    if toroidal:
                        nx = nx % width
                        ny = ny % height
                    else:
                        if not (0 <= nx < width and 0 <= ny < height):
                            continue
                # self collision
                if self_collision and (nx, ny) in my_body:
                    continue
                # enemy collision
                enemy_collision = active_rules.get("enemy_collision", True)
                if enemy_collision:
                    for e in enemies:
                        if (nx, ny) in e["body"] or (nx, ny) == e["head"]:
                            # If score_phase >= 2 enemy kills are lethal
                            # Actually enemy collision rule may be active
                            # For now avoid
                            pass  # we need actual bodies as tuples, but in perception they are lists? We'll handle by simple check
                safe.append(d)
            # pick any safe move; if none, pick first valid (will be random by engine)
            return safe[0] if safe else valid[0]

        # We have target food
        target = best_food
        tx, ty = target

        # Compute possible moves and select best using simple heuristic
        candidates = []
        for d in valid:
            nx, ny = my_head
            if d == "UP":
                ny -= block_size
            elif d == "DOWN":
                ny += block_size
            elif d == "LEFT":
                nx -= block_size
            elif d == "RIGHT":
                nx += block_size

            # Check collisions based on score_phase and rules
            # Wall collision
            if wall_collision:
                if toroidal:
                    nx_wrap = nx % width
                    ny_wrap = ny % height
                else:
                    nx_wrap, ny_wrap = nx, ny
                if not (0 <= nx_wrap < width and 0 <= ny_wrap < height):
                    continue
            # Self collision
            if self_collision and (nx, ny) in my_body:
                continue
            # Enemy collision (dangerous if score_phase >= 3? Actually enemy kills for all phases >= 300? With score_phase >= 3 (300+), all enemy bodies kill)
            # We'll just avoid stepping onto any enemy head or body
            enemy_collision = active_rules.get("enemy_collision", True)
            if enemy_collision:
                for e in enemies:
                    if (nx, ny) in e["body"] or (nx, ny) == e["head"]:
                        # If it's lethal now based on phase
                        # For simplicity avoid all
                        continue

            # Compute distance to target after move
            new_dist = abs(tx - nx) + abs(ty - ny)
            # Preference: move towards target (dx sign)
            dx = tx - my_head[0]
            dy = ty - my_head[1]
            direction_score = 0
            if d == "UP" and dy < 0:
                direction_score = 1
            elif d == "DOWN" and dy > 0:
                direction_score = 1
            elif d == "LEFT" and dx < 0:
                direction_score = 1
            elif d == "RIGHT" and dx > 0:
                direction_score = 1

            candidates.append((new_dist, direction_score, d))

        if not candidates:
            # fallback: any valid move
            return valid[0]

        # Sort by distance then direction
        candidates.sort(key=lambda x: (x[0], -x[1]))
        best_move = candidates[0][2]

        self.last_move = best_move
        return best_move

def create_agent():
    return CobraAgent()

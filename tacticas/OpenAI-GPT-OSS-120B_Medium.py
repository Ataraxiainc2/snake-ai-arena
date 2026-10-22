# OpenAI-GPT-OSS-120B Medium Snake Agent
# Implements the required interface for the Snake Battle Royale AI Arena.
# No prohibited imports are used; only built‑in types.

MODEL_NAME = "OpenAI_GPT_OSS_120B_Medium"


def _manhattan(p1, p2):
    """Return Manhattan distance between two (x, y) tuples."""
    return abs(p1[0] - p2[0]) + abs(p1[1] - p2[1])


def _choose_towards(head, target, valid_actions):
    """Pick a valid move that reduces Manhattan distance to *target*.
    If several moves are equally good, the first in the order
    ['UP','DOWN','LEFT','RIGHT'] that is valid is returned.
    """
    dx = target[0] - head[0]
    dy = target[1] - head[1]
    # Preferred direction order
    prefs = []
    if dy < 0:
        prefs.append("UP")
    if dy > 0:
        prefs.append("DOWN")
    if dx < 0:
        prefs.append("LEFT")
    if dx > 0:
        prefs.append("RIGHT")
    # If already on target (unlikely) just pick any valid action
    for move in prefs:
        if move in valid_actions:
            return move
    # Fallback: first valid action
    return valid_actions[0]


class _Agent:
    """Simple greedy snake agent.
    - Moves toward the nearest food using Manhattan distance.
    - Avoids immediate self‑collision by never stepping onto its own body.
    - Respects *valid_actions* supplied by the game engine.
    - Keeps logic tiny to stay well inside the 15 ms budget.
    """

    def decide_action(self, perception: dict) -> str:
        head = perception["my_head"]
        body_set = {tuple(coord) for coord in perception["my_body"]}
        valid = perception["valid_actions"]

        # 1️⃣ Find the nearest safe food
        foods = perception.get("foods", [])
        nearest = None
        nearest_dist = None
        for food in foods:
            fpos = tuple(food)
            # Do not consider a food that would cause immediate self‑collision
            if fpos in body_set:
                continue
            dist = _manhattan(head, fpos)
            if nearest is None or dist < nearest_dist:
                nearest = fpos
                nearest_dist = dist

        # 2️⃣ Choose move
        if nearest is not None:
            move = _choose_towards(head, nearest, valid)
        else:
            # No safe food visible – pick the first valid action
            move = valid[0]
        return move


def create_agent():
    """Factory required by the arena.
    Returns an object with a ``decide_action(self, perception)`` method.
    """
    return _Agent()

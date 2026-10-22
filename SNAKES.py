"""
SNAKES.py — Snake Battle Royale AI Arena & Reinforcement Learning Lab
=====================================================================
Single-file unified engine containing:
  - Section 1: Configuration, Constants, Hyperparameters & Trilingual Localization (EN_US, ES_LA, PT_BR)
  - Section 2: Automated AI Model Catalog Synchronizer (`tacticas/Provider-Model_Name.py`)
  - Section 3: Anti-Cheat AST Sandbox & External LLM Tactic Loader
  - Section 4: Hybrid Deep Reinforcement Learning (Double DQN + Target Net + Shared Replay Buffer)
  - Section 5: Game Entities, 16-D State Vector & Progressive Rules (100 / 200 / 300 pts)
  - Section 6: Scientific Telemetry & Learning Analytics (`GameAnalytics`)
  - Section 7: Main Pygame Engine, Live Convergence Graph & Turbo Training Mode
  - Section 8: Unified Headless Simulator, Optuna Optimizer & Interactive CLI Menu
"""

import os
import sys
import ast
import array
import gc
import re
import json
import time
import hashlib
import argparse
import math
import random
import pickle
import logging
import colorsys
import urllib.request
import importlib.util
import shutil
from collections import defaultdict, deque


def check_tactics(paths):
    """`python SNAKES.py --check [tacticas/X.py ...]`: the same AST audit the game applies, then 216 random
    positions (both grid sizes, every rule phase) counting errors, invalid moves and time. Runs before
    pygame/torch are imported, so it also works on a bare Python (e.g. the GitHub pull-request check).
    The audit is a filter, not a sandbox: only run strangers' files after this check, ideally on GitHub."""
    root = os.path.dirname(os.path.abspath(__file__))
    lists = {}
    for node in ast.parse(open(os.path.abspath(__file__), encoding="utf-8").read()).body:
        if isinstance(node, ast.Assign) and getattr(node.targets[0], "id", "") in ("FORBIDDEN_MODULES", "FORBIDDEN_CALLS"):
            lists[node.targets[0].id] = ast.literal_eval(node.value)
    mods, calls = lists["FORBIDDEN_MODULES"], lists["FORBIDDEN_CALLS"]
    dirs = {'UP': (0, -1), 'DOWN': (0, 1), 'LEFT': (-1, 0), 'RIGHT': (1, 0)}

    def audit(src):
        for n in ast.walk(ast.parse(src)):
            if isinstance(n, ast.Import) and any(a.name.split('.')[0] in mods for a in n.names):
                return "forbidden import"
            if isinstance(n, ast.ImportFrom) and n.module and n.module.split('.')[0] in mods:
                return f"forbidden import from '{n.module}'"
            if isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id in calls:
                return f"forbidden call '{n.func.id}()'"
            if isinstance(n, ast.Attribute) and n.attr.startswith('__') and n.attr not in {'__init__', '__name__', '__len__'}:
                return f"restricted dunder '{n.attr}'"
        return None

    def walk(w, h, length, wrap, rnd):
        x, y = rnd.randrange(w), rnd.randrange(h)
        body = [(x, y)]
        for _ in range(length - 1):
            dx, dy = rnd.choice(list(dirs.values()))
            x, y = body[-1][0] + dx, body[-1][1] + dy
            body.append((x % w, y % h) if wrap else (min(max(x, 0), w - 1), min(max(y, 0), h - 1)))
            x, y = body[-1]
        return body

    def perception(w, h, score, rnd, bs=20):
        px = lambda c: (c[0] * bs, c[1] * bs)
        me = [px(c) for c in walk(w, h, min(score + 1, 900), score < 200, rnd)]
        enemies = []
        for i in range(14):
            s = rnd.choice([0, 5, 50, 150, 250, 310, 400])
            eb = [px(c) for c in walk(w, h, min(s + 1, 900), s < 200, rnd)]
            enemies.append({'id': i, 'head': eb[0], 'body': tuple(eb), 'direction': rnd.choice(list(dirs)), 'score': float(s)})
        return {'my_name': 'Candidate', 'my_head': me[0], 'my_body': tuple(me), 'my_direction': rnd.choice(list(dirs)),
                'my_score': float(score), 'width': w * bs, 'height': h * bs, 'block_size': bs,
                'foods': tuple(px((rnd.randrange(w), rnd.randrange(h))) for _ in range(12)), 'enemies': tuple(enemies),
                'active_rules': {'self_collision': score >= 100, 'self_collision_warning': 100 <= score < 110,
                                 'wall_collision': score >= 200, 'enemy_collision': score >= 300, 'toroidal_world': score < 200},
                'state_vector_16': (0.0,) * 16, 'previous_reward': rnd.choice([0.0, 15.0, -1.2]), 'died_last_step': False,
                'valid_actions': tuple(dirs) if score == 0 else tuple(rnd.sample(list(dirs), 3)),
                'turns_without_food': rnd.choice([0, 500, 1200]), 'starvation_turns': 1500}

    def check(path):
        rel = os.path.relpath(os.path.abspath(path), root).replace(os.sep, "/")
        if not (rel.startswith("tacticas/") and rel.endswith(".py") and rel.count("/") == 1):
            return False, f"{rel}: entries must be a single tacticas/<Provider-Model_Name>.py file"
        src = open(path, encoding="utf-8").read()
        if not src.strip():
            return False, f"{rel}: empty file"
        reason = audit(src)
        if reason:
            return False, f"{rel}: DISQUALIFIED by the anti-cheat audit ({reason})"
        try:
            spec = importlib.util.spec_from_file_location("candidate", path)
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            decide = (mod.create_agent() if hasattr(mod, "create_agent") else mod).decide_action
        except Exception as e:
            return False, f"{rel}: cannot be loaded ({e!r})"
        rnd = random.Random(7)
        errors = invalid = 0
        times, first = [], ""
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
        p50, p99 = times[len(times) // 2], times[int(len(times) * 0.99)]
        line = (f"{rel}: {len(src)} chars, errors={errors}, invalid={invalid}, "
                f"median={p50:.2f} ms, p99={p99:.2f} ms, max={times[-1]:.2f} ms")
        if errors or invalid:
            return False, line + f"  FAIL {first}"
        if p50 > 15.0:
            return False, line + "  FAIL median above 15 ms"
        return True, line + ("  WARNING: p99 above 15 ms (slow turns become random moves)" if p99 > 15.0 else "")

    folder = os.path.join(root, "tacticas")
    files = paths or sorted(os.path.join(folder, f) for f in os.listdir(folder)
                            if f.endswith(".py") and os.path.getsize(os.path.join(folder, f)) > 0)
    ok = True
    for f in files:
        good, line = check(f)
        ok &= good
        print(("PASS " if good else "FAIL ") + line, flush=True)
    return 0 if ok else 1


if __name__ == "__main__" and "--check" in sys.argv:
    sys.dont_write_bytecode = True
    sys.exit(check_tactics([a for a in sys.argv[1:] if a != "--check"]))


import pygame
import torch
import torch.nn as nn
import torch.nn.functional as F
import optuna
from colorama import Fore, Style, init

init(autoreset=True)
if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)))  # tacticas/, sounds and outputs live next to this file
sys.dont_write_bytecode = True  # loading tactics must not leave __pycache__ folders in tacticas/
torch.set_num_threads(1)  # the DQN nets are tiny: extra threads only compete with the tactics for CPU
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# ==============================================================================
# SECTION 1: CONFIGURATION, CONSTANTS, HYPERPARAMETERS & LOCALIZATION (i18n)
# ==============================================================================
BLOCK_SIZE = 20
FPS = 15

# Colors
BLACK = (0, 0, 0)
WHITE = (255, 255, 255)
MATRIX_GREEN = (0, 200, 70)
FOOD_WHITE = (255, 255, 255)
ALERT_RED = (255, 50, 50)
GOLD = (255, 215, 0)

# Progressive Rules (Curriculum Learning Thresholds)
SELF_COLLISION_THRESHOLD = 100.0
SELF_COLLISION_WARNING_SEC = 40.0
SELF_COLLISION_WARNING_TURNS = int(SELF_COLLISION_WARNING_SEC * 15)  # 600 turns = 40 s at the live 15 FPS
WALL_COLLISION_THRESHOLD = 200.0
ENEMY_COLLISION_THRESHOLD = 300.0

# Hybrid Reinforcement Learning Hyperparameters (Double DQN)
STATE_SIZE = 23  # 16 classic features + 4 free-space + length + enemy phase + hunger
FLOOD_CAP = 80  # cells explored per direction for the free-space features (non-DQN snakes)
FLOOD_CAP_DQN = 400  # built-in DQN snakes check room up to their own length (cap for CPU)
ACTION_SIZE = 4
ACTIONS = ['UP', 'DOWN', 'LEFT', 'RIGHT']
ACTION_ALIASES = {
    'UP': 'UP', 'DOWN': 'DOWN', 'LEFT': 'LEFT', 'RIGHT': 'RIGHT',
    'ARRIBA': 'UP', 'ABAJO': 'DOWN', 'IZQUIERDA': 'LEFT', 'DERECHA': 'RIGHT',
    'CIMA': 'UP', 'BAIXO': 'DOWN', 'ESQUERDA': 'LEFT', 'DIREITA': 'RIGHT'
}
OPPOSITES = {
    'UP': 'DOWN',
    'DOWN': 'UP',
    'LEFT': 'RIGHT',
    'RIGHT': 'LEFT'
}
DELTAS = {
    'UP': (0, -BLOCK_SIZE),
    'DOWN': (0, BLOCK_SIZE),
    'LEFT': (-BLOCK_SIZE, 0),
    'RIGHT': (BLOCK_SIZE, 0)
}

DEFAULT_LR = 5e-4
DEFAULT_HIDDEN_SIZE = 64
DEFAULT_GAMMA = 0.92
DEFAULT_IMITATION_RATE = 0.20
DEFAULT_EPSILON_START = 1.0
DEFAULT_EPSILON_MIN = 0.02
EPSILON_DECAY_STEPS = 1500
REPLAY_BATCH_SIZE = 64
TARGET_UPDATE_TAU = 0.01  # Polyak soft update rate for Target Network

# Anti-Cheat CPU quota per decision for external LLM tactics (in seconds)
MAX_DECISION_TIME_SEC = 0.015  # 15 ms maximum per turn
# Code-size penalty for LLM tactics: ranking score = max score * code factor, where
# code factor = 1 - CODE_SIZE_PENALTY * log2(chars / chars of the shortest tactic).
# The shortest file keeps 100%; a file twice as long keeps 95%, four times as long 90%, ...
CODE_SIZE_PENALTY = 0.05
# Built-in DQN snakes: "unified" = one shared network trained once per frame from the shared
# replay buffer (default, cheaper); "separate" = one network per snake (original behaviour).
DQN_MODES = ("unified", "separate")
DEFAULT_DQN_MODE = "unified"
# Single file with every statistic competitors need to study a round (written with the report).
TELEMETRY_FILE = "telemetry.json"
LEARNING_WINDOW_MIN = 30  # DQN learning curve resolution (minutes of game time)
AUTOSAVE_SEC = 300  # q_tables.pkl, report and telemetry.json are also written every 5 min while playing
EPISODE_AUTO_CONTINUE_SEC = 15  # the end-of-episode screen continues by itself (no ENTER needed)
# Anti-stalling: a snake that eats nothing for this many turns dies of starvation, so circling
# forever cannot freeze the game (an episode only ends when every snake has died once).
STARVATION_TURNS = 1500  # 100 s at 15 FPS; normal play eats every ~50 turns
# Tactic files the organizer has suspended for now (kept on disk untouched, simply not loaded).
SUSPENDED_TACTICS = set()  # e.g. {"Provider-Model.py"}; Kimi K3 was reinstated after fixing its stall
HUMAN_NAME = "Human (You)"  # distinct from every built-in DQN philosopher name


def default_arena(n_tactics):
    """Default snakes/foods grow with the number of tactics: room for every LLM tactic, one human
    player and two learning DQN snakes; foods keep the original 8-per-10-snakes density."""
    snakes = min(50, max(10, n_tactics + 3))
    return snakes, min(50, max(8, round(snakes * 0.8)))


def count_tactic_files(folder=None):
    folder = folder or TACTICS_FOLDER
    try:
        return sum(1 for f in os.listdir(folder) if f.endswith(".py") and not f.startswith("_")
                   and f not in SUSPENDED_TACTICS and os.path.getsize(os.path.join(folder, f)) > 0)
    except OSError:
        return 0
TACTICS_FOLDER = "tacticas"
MODELS_DEV_URL = "https://models.dev/api.json"
LOCAL_PRICES_FALLBACK = os.environ.get("SNAKES_PRICES_JSON", "")  # optional offline model catalog

PHILOSOPHER_NAMES = [
    "Aristotle", "Plato", "Socrates", "Descartes", "Kant",
    "Nietzsche", "Hegel", "Schopenhauer", "Sartre", "Camus",
    "Heidegger", "Merleau-Ponty", "Foucault", "Derrida", "Simondon",
    "Rousseau", "Voltaire", "Locke", "Hume", "Berkeley",
    "Spinoza", "Leibniz", "Rawls", "Habermas", "Wittgenstein",
    "Chomsky", "Marx", "Freud", "Jaspers", "Bergson",
    "Cioran", "Baudrillard", "Deleuze", "Guattari", "Arendt",
    "Aquinas", "Confucius", "Laozi", "Zhuangzi", "Epicurus",
    "Lucretius", "Marcus Aurelius", "Seneca", "Epictetus", "Plotinus",
    "Protagoras", "Gorgias", "Parmenides", "Empedocles", "Anaximander"
]

# Trilingual Interface Dictionary: English (USA) [default], Español (Latinoamérica), Português (Brasil)
LANG_ORDER = ["EN_US", "ES_LA", "PT_BR"]
CURRENT_LANG = "EN_US"

STRINGS = {
    "EN_US": {
        "lang_name": "English (USA)",
        "window_title": "Snake AI Arena & RL Lab",
        "hud_ep_info": "Episode: {ep} | Time: {t}s | Shared Memory: {mem} | Lang: EN (Key I){turbo}",
        "turbo_badge": " | [TURBO TRAINING ON - Key T]",
        "hud_survivors": "Unfallen Survivors: {surv}/{total} | Active LLM Tactics: {llms}",
        "hud_instructions": "ESC: Save & Exit | T: Turbo Train | I: Language | R: Reset AI | P: Pause | L: Logs | 1/0: +/-Speed",
        "msg_top3": "All-Time Top 3: ",
        "msg_new_leader": "{name} takes the lead!",
        "msg_best_learner": "Top Learner: {name} (Max: {score:.0f} pts)",
        "msg_record_10": "New 10-pt Record! {name} in {sec:.2f}s",
        "msg_warn_100": "100-PT WARNING for {name}: Self-collision activates in 40s!",
        "msg_rule_100_on": "RULE 100 ACTIVE for {name}: Self-collision is now lethal!",
        "msg_died": "{name} died from {reason}",
        "death_self": "self-collision",
        "death_wall": "wall collision",
        "death_starvation": "starvation (no food for {turns} turns)",
        "death_enemy": "collision with {other}",
        "ep_end_title": "End of Episode {ep}",
        "ep_end_subtitle": "Ranking by Adjusted Score (Max Score x Code-Size Factor)",
        "ep_end_prompt": "Press ENTER to start next episode | Press I to change language | ESC to exit",
        "graph_title": "Mean Max Score: {val:.1f} pts",
        "cli_header": "SNAKE AI ARENA & RL LAB (Fair LLM Tournament + Hybrid DQN)",
        "cli_sync_status": "Model catalog synchronized in '{folder}/': {total} total model files ({active} active with code, {empty} empty templates ready).",
        "cli_select_mode": "Select execution mode:",
        "cli_mode_1": "1. Play / Watch Live Arena (Press 'T' inside for Turbo Training, 'I' for Language).",
        "cli_mode_2": "2. Run Bayesian Hyperparameter Optimization (Optuna).",
        "cli_mode_3": "3. Fast Headless Background Training (Saves weights to q_tables.pkl).",
        "cli_mode_4": "4. Show Official English Rules & Prompt for Rival LLMs.",
        "cli_mode_5": "5. Change Interface Language (Current: English USA).",
        "cli_input_option": "Enter option (1-5): ",
        "cli_num_snakes": "Number of snakes (1-50, Enter for {default}): ",
        "cli_num_food": "Number of simultaneous foods (1-50, Enter for {default}): ",
        "cli_play_user": "Do you want to control a snake yourself? (y/n, Enter=n): ",
        "cli_dqn_mode": "DQN training: U = unified shared network, S = separate per snake (Enter=U): ",
        "report_title": "Scientific Tournament & RL Analysis Report"
    },
    "ES_LA": {
        "lang_name": "Español (Latinoamérica)",
        "window_title": "Arena de IA Snake y Laboratorio RL",
        "hud_ep_info": "Episodio: {ep} | Tiempo: {t}s | Memoria Colectiva: {mem} | Idioma: ES (Tecla I){turbo}",
        "turbo_badge": " | [ENTRENAMIENTO TURBO ACTIVO - Tecla T]",
        "hud_survivors": "Supervivientes sin caer: {surv}/{total} | Tácticas LLM Activas: {llms}",
        "hud_instructions": "ESC: Guardar y Salir | T: Modo Turbo | I: Idioma | R: Reset IA | P: Pausa | L: Logs | 1/0: +/-Vel",
        "msg_top3": "Top 3 Histórico: ",
        "msg_new_leader": "¡{name} toma el liderato!",
        "msg_best_learner": "Líder de Aprendizaje: {name} (Máx: {score:.0f} pts)",
        "msg_record_10": "¡Récord a 10 pts! {name} en {sec:.2f}s",
        "msg_warn_100": "¡ADVERTENCIA 100 PTS para {name}: Auto-colisión en 40s!",
        "msg_rule_100_on": "REGLA 100 ACTIVA para {name}: ¡Auto-colisión mortal!",
        "msg_died": "{name} murió por {reason}",
        "death_self": "auto-colisión",
        "death_wall": "colisión con pared",
        "death_starvation": "inanición (sin comer {turns} turnos)",
        "death_enemy": "colisión con {other}",
        "ep_end_title": "Fin del Episodio {ep}",
        "ep_end_subtitle": "Ranking por Puntaje Ajustado (Máximo x Factor de Tamaño de Código)",
        "ep_end_prompt": "Presiona ENTER para el siguiente episodio | Tecla I para cambiar idioma | ESC para salir",
        "graph_title": "Puntaje Máx Medio: {val:.1f} pts",
        "cli_header": "ARENA DE IA SNAKE Y LABORATORIO RL (Torneo LLM + DQN Híbrido)",
        "cli_sync_status": "Catálogo sincronizado en '{folder}/': {total} archivos de modelos ({active} activos con código, {empty} vacíos listos para llenar).",
        "cli_select_mode": "Seleccione el modo de ejecución:",
        "cli_mode_1": "1. Jugar / Observar Arena en Vivo (Tecla 'T' adentro para Turbo, 'I' para Idioma).",
        "cli_mode_2": "2. Ejecutar Optimización Bayesiana de Hiperparámetros (Optuna).",
        "cli_mode_3": "3. Entrenamiento Rápido Headless en Segundo Plano (Guarda en q_tables.pkl).",
        "cli_mode_4": "4. Mostrar Reglas y Prompt Oficial en Inglés para otros LLMs.",
        "cli_mode_5": "5. Cambiar Idioma de la Interfaz (Actual: Español Latinoamérica).",
        "cli_input_option": "Ingrese una opción (1-5): ",
        "cli_num_snakes": "Cantidad de serpientes (1-50, Enter para {default}): ",
        "cli_num_food": "Cantidad de comidas simultáneas (1-50, Enter para {default}): ",
        "cli_play_user": "¿Desea controlar usted mismo una serpiente? (s/n, Enter=n): ",
        "cli_dqn_mode": "Entrenamiento DQN: U = red unificada compartida, S = separada por serpiente (Enter=U): ",
        "report_title": "Informe Científico de Torneo y Aprendizaje RL"
    },
    "PT_BR": {
        "lang_name": "Português (Brasil)",
        "window_title": "Arena de IA Snake & Laboratório RL",
        "hud_ep_info": "Episódio: {ep} | Tempo: {t}s | Memória Coletiva: {mem} | Idioma: PT (Tecla I){turbo}",
        "turbo_badge": " | [TREINAMENTO TURBO ATIVO - Tecla T]",
        "hud_survivors": "Sobreviventes sem cair: {surv}/{total} | Táticas LLM Ativas: {llms}",
        "hud_instructions": "ESC: Salvar e Sair | T: Modo Turbo | I: Idioma | R: Reset IA | P: Pausa | L: Logs | 1/0: +/-Vel",
        "msg_top3": "Top 3 Histórico: ",
        "msg_new_leader": "{name} assumiu a liderança!",
        "msg_best_learner": "Líder de Aprendizado: {name} (Máx: {score:.0f} pts)",
        "msg_record_10": "Novo Recorde de 10 pts! {name} em {sec:.2f}s",
        "msg_warn_100": "AVISO DE 100 PTS para {name}: Autocolisão em 40s!",
        "msg_rule_100_on": "REGRA 100 ATIVA para {name}: Autocolisão agora é mortal!",
        "msg_died": "{name} morreu por {reason}",
        "death_self": "autocolisão",
        "death_wall": "colisão com a parede",
        "death_starvation": "inanição (sem comer {turns} turnos)",
        "death_enemy": "colisão com {other}",
        "ep_end_title": "Fim do Episódio {ep}",
        "ep_end_subtitle": "Ranking por Pontuação Ajustada (Máxima x Fator de Tamanho de Código)",
        "ep_end_prompt": "Pressione ENTER para o próximo episódio | Tecla I para mudar idioma | ESC para sair",
        "graph_title": "Pontuação Máx Média: {val:.1f} pts",
        "cli_header": "ARENA DE IA SNAKE & LABORATÓRIO RL (Torneio LLM + DQN Híbrido)",
        "cli_sync_status": "Catálogo sincronizado em '{folder}/': {total} arquivos de modelos ({active} ativos com código, {empty} vazios prontos para preencher).",
        "cli_select_mode": "Selecione o modo de execução:",
        "cli_mode_1": "1. Jogar / Assistir Arena ao Vivo (Tecla 'T' dentro para Turbo, 'I' para Idioma).",
        "cli_mode_2": "2. Executar Otimização Bayesiana de Hiperparâmetros (Optuna).",
        "cli_mode_3": "3. Treinamento Rápido Headless em Segundo Plano (Salva em q_tables.pkl).",
        "cli_mode_4": "4. Mostrar Regras e Prompt Oficial em Inglês para outros LLMs.",
        "cli_mode_5": "5. Alterar Idioma da Interface (Atual: Português Brasil).",
        "cli_input_option": "Digite uma opção (1-5): ",
        "cli_num_snakes": "Quantidade de cobras (1-50, Enter para {default}): ",
        "cli_num_food": "Quantidade de comidas simultâneas (1-50, Enter para {default}): ",
        "cli_play_user": "Deseja controlar uma cobra você mesmo? (s/n, Enter=n): ",
        "cli_dqn_mode": "Treinamento DQN: U = rede unificada compartilhada, S = separada por cobra (Enter=U): ",
        "report_title": "Relatório Científico de Torneio e Aprendizado RL"
    }
}


def tr(key, **kwargs):
    """Translates UI/report string according to CURRENT_LANG (defaults to EN_US)."""
    lang_dict = STRINGS.get(CURRENT_LANG, STRINGS["EN_US"])
    template = lang_dict.get(key, STRINGS["EN_US"].get(key, key))
    return template.format(**kwargs) if kwargs else template


def cycle_language():
    """Cycles CURRENT_LANG between EN_US -> ES_LA -> PT_BR."""
    global CURRENT_LANG
    idx = (LANG_ORDER.index(CURRENT_LANG) + 1) % len(LANG_ORDER)
    CURRENT_LANG = LANG_ORDER[idx]
    return CURRENT_LANG


# ==============================================================================
# SECTION 2: AUTOMATED AI MODEL CATALOG SYNCHRONIZER (tacticas/Provider-Model.py)
# ==============================================================================
EXCLUDED_MODEL_REGEX = re.compile(
    r"embed|whisper|tts|audio|speech|moderation|rerank|image|dall-?e|imagen|veo|sora|"
    r"realtime|transcribe|guard|aqa|search|batch|lyria|banana|asr|ocr",
    re.IGNORECASE
)


def _clean_filename_token(text):
    """Normalizes a provider or model name into a clean filesystem token."""
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", str(text).strip())
    return re.sub(r"_+", "_", cleaned).strip("_")


def sync_model_catalog_files(folder=TACTICS_FOLDER):
    """
    Fetches the up-to-date catalog of AI providers and text models from https://models.dev/api.json
    (with an optional offline fallback file named by $SNAKES_PRICES_JSON) and ensures that every model has a
    `Provider-Model_Name.py` file in `tacticas/`.
    - Newly discovered models are created as empty 0-byte `.py` files ready to be filled.
    - Existing `.py` files (whether empty or already implemented) are NEVER overwritten.
    """
    os.makedirs(folder, exist_ok=True)
    discovered_filenames = {"Google-Gemini_4_Argon_Medium.py"}

    # 1. Try live registry (models.dev/api.json)
    try:
        req = urllib.request.Request(MODELS_DEV_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            root = json.loads(resp.read().decode("utf-8"))

        # Measure lab reach (canonical model creators vs resellers)
        reach = defaultdict(int)
        for host_id, pdata in root.items():
            if not isinstance(pdata, dict):
                continue
            for _, mdata in (pdata.get("models") or {}).items():
                if isinstance(mdata, dict):
                    cid = mdata.get("canonical_model_id") or ""
                    if "/" in cid:
                        lab_id = cid.split("/")[0]
                        if lab_id != host_id:
                            reach[lab_id] += 1

        for provider_id, pdata in root.items():
            if not isinstance(pdata, dict) or reach.get(provider_id, 0) == 0:
                continue
            provider_label = _clean_filename_token(pdata.get("name") or provider_id)
            for model_id, mdata in (pdata.get("models") or {}).items():
                if not isinstance(mdata, dict):
                    continue
                if EXCLUDED_MODEL_REGEX.search(model_id) or EXCLUDED_MODEL_REGEX.search(mdata.get("name") or ""):
                    continue
                output_modalities = (mdata.get("modalities") or {}).get("output") or ["text"]
                if "text" not in output_modalities:
                    continue
                cid = mdata.get("canonical_model_id") or ""
                if cid and not cid.startswith(provider_id + "/"):
                    continue
                model_label = _clean_filename_token(mdata.get("name") or model_id)
                if provider_label and model_label:
                    discovered_filenames.add(f"{provider_label}-{model_label}.py")
    except Exception as e:
        logging.debug(f"Live models.dev sync skipped ({e}), checking local fallback.")

    # 2. Check the optional local fallback catalog if available
    if LOCAL_PRICES_FALLBACK and os.path.exists(LOCAL_PRICES_FALLBACK):
        try:
            with open(LOCAL_PRICES_FALLBACK, "r", encoding="utf-8") as f:
                local_data = json.load(f).get("providers", {})
            provider_map = {
                "openai": "OpenAI",
                "google_gemini_api": "Google",
                "google_vertex_ai": "Google"
            }
            for pid, pblock in local_data.items():
                plabel = provider_map.get(pid, _clean_filename_token(pid))
                for mid in (pblock.get("models") or {}).keys():
                    if not EXCLUDED_MODEL_REGEX.search(mid):
                        mlabel = _clean_filename_token(mid)
                        discovered_filenames.add(f"{plabel}-{mlabel}.py")
        except Exception:
            pass

    # 3. Create 0-byte placeholder files for any missing models without touching existing files
    created_count = 0
    for fname in sorted(discovered_filenames):
        fpath = os.path.join(folder, fname)
        if not os.path.exists(fpath):
            try:
                with open(fpath, "w", encoding="utf-8"):
                    pass
                created_count += 1
            except Exception:
                pass

    return created_count


# ==============================================================================
# SECTION 3: ANTI-CHEAT SANDBOX & EXTERNAL LLM TACTIC LOADER
# ==============================================================================
FORBIDDEN_MODULES = {
    'sys', 'inspect', 'subprocess', 'shutil', 'socket', 'urllib',
    'requests', 'http', 'ctypes', 'multiprocessing', 'threading',
    'importlib', 'builtins', 'gc', 'os', 'pathlib', 'pickle'
}
FORBIDDEN_CALLS = {'eval', 'exec', 'compile', 'globals', 'locals', 'getattr', 'setattr', 'delattr', '__import__', 'open'}


class AntiCheatAuditor:
    """
    Statically audits the Abstract Syntax Tree (AST) of every non-empty file in `tacticas/`
    to guarantee no LLM reads rival files, mutates the game engine, or runs forbidden calls.
    """
    @staticmethod
    def audit_file(filepath):
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                source = f.read()
        except Exception as e:
            return False, f"Read error: {e}", True

        if not source.strip():
            return False, "Empty template file", True

        try:
            tree = ast.parse(source, filename=filepath)
        except Exception as e:
            return False, f"Syntax error: {e}", False

        # Check if AST only contains docstrings/comments/pass
        meaningful_nodes = [
            n for n in tree.body
            if not isinstance(n, (ast.Pass, ast.Expr))
        ]
        if not meaningful_nodes:
            return False, "Empty template file", True

        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    root_mod = alias.name.split('.')[0]
                    if root_mod in FORBIDDEN_MODULES:
                        return False, f"Forbidden import: '{alias.name}'", False
            elif isinstance(node, ast.ImportFrom):
                if node.module:
                    root_mod = node.module.split('.')[0]
                    if root_mod in FORBIDDEN_MODULES:
                        return False, f"Forbidden import from: '{node.module}'", False
            elif isinstance(node, ast.Call):
                if isinstance(node.func, ast.Name) and node.func.id in FORBIDDEN_CALLS:
                    return False, f"Forbidden call: '{node.func.id}()'", False
            elif isinstance(node, ast.Attribute):
                if node.attr.startswith("__") and node.attr not in {"__init__", "__name__", "__len__"}:
                    return False, f"Restricted dunder attribute access: '{node.attr}'", False

        return True, "OK", False


def load_tactic_file(fpath):
    """Audits and loads one tactic file. Returns (tactic_dict, None) or (None, reason)."""
    fname = os.path.basename(fpath)
    is_safe, reason, is_empty = AntiCheatAuditor.audit_file(fpath)
    if is_empty:
        return None, "empty"
    if not is_safe:
        logging.warning(f"[ANTI-CHEAT] '{fname}' DISQUALIFIED: {reason}")
        return None, reason
    try:
        stem = fname[:-3]
        spec = importlib.util.spec_from_file_location(f"tactic_{ _clean_filename_token(stem) }", fpath)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        model_name = getattr(module, "MODEL_NAME", getattr(module, "NOMBRE_MODELO", stem))
        if hasattr(module, "create_agent"):
            agent_instance = module.create_agent()
            decide_fn = getattr(agent_instance, "decide_action", getattr(agent_instance, "decidir_accion", None))
        elif hasattr(module, "crear_agente"):
            agent_instance = module.crear_agente()
            decide_fn = getattr(agent_instance, "decide_action", getattr(agent_instance, "decidir_accion", None))
        elif hasattr(module, "decide_action"):
            agent_instance = module
            decide_fn = module.decide_action
        elif hasattr(module, "decidir_accion"):
            agent_instance = module
            decide_fn = module.decidir_accion
        else:
            return None, "no create_agent()/decide_action()"
        if decide_fn is None:
            return None, "no decide_action method"
        with open(fpath, "r", encoding="utf-8") as fh:
            code_chars = len(fh.read())
        stat = os.stat(fpath)
        return {
            "name": str(stem)[:36],
            "declared_name": str(model_name)[:36],
            "file": fname,
            "instance": agent_instance,
            "decide": decide_fn,
            "code_chars": code_chars,
            "code_factor": 1.0,
            "stamp": (stat.st_mtime, stat.st_size),
        }, None
    except Exception as e:
        logging.warning(f"[LOADER] Failed loading '{fname}': {e}")
        return None, f"load error: {e}"


def apply_code_factors(tactics):
    """The shortest loaded tactic keeps 100%; each doubling of size costs CODE_SIZE_PENALTY."""
    if not tactics:
        return
    shortest = max(1, min(t["code_chars"] for t in tactics))
    for t in tactics:
        ratio = max(1.0, t["code_chars"] / shortest)
        t["code_factor"] = max(0.0, 1.0 - CODE_SIZE_PENALTY * math.log2(ratio))


def load_external_tactics(folder=TACTICS_FOLDER, sync=True):
    """
    Synchronizes the model catalog (optional) and loads all non-empty, AST-verified tactic files from
    `tacticas/`. Supports English (`MODEL_NAME`, `create_agent`, `decide_action`) and Spanish
    (`NOMBRE_MODELO`, `crear_agente`, `decidir_accion`) entry points.
    """
    if sync:
        sync_model_catalog_files(folder)
    loaded_tactics = []
    empty_count = 0
    total_files = 0
    for fname in sorted(os.listdir(folder)):
        if not fname.endswith(".py") or fname.startswith("_"):
            continue
        total_files += 1
        fpath = os.path.join(folder, fname)
        if os.path.getsize(fpath) == 0:
            empty_count += 1
            continue
        if fname in SUSPENDED_TACTICS:
            logging.warning(f"[LOADER] '{fname}' is suspended by the organizer; not loaded.")
            continue
        tactic, reason = load_tactic_file(fpath)
        if tactic is None:
            if reason == "empty":
                empty_count += 1
            continue
        loaded_tactics.append(tactic)
    apply_code_factors(loaded_tactics)
    return loaded_tactics, total_files, empty_count

# ==============================================================================
# SECTION 4: HYBRID DEEP REINFORCEMENT LEARNING (DOUBLE DQN + SHARED BUFFER)
# ==============================================================================
class DQN(nn.Module):
    """
    Clean Multilayer Perceptron for Q-value estimation (no Dropout to preserve
    deterministic Bellman targets over low-dimensional state vectors).
    """
    def __init__(self, input_size=STATE_SIZE, output_size=ACTION_SIZE, hidden_size=DEFAULT_HIDDEN_SIZE):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(input_size, hidden_size),
            nn.ReLU(),
            nn.Linear(hidden_size, hidden_size),
            nn.ReLU(),
            nn.Linear(hidden_size, output_size)
        )

    def forward(self, x):
        return self.net(x)


class SharedReplayBuffer:
    """
    Collective Experience Replay Buffer shared across all snakes for fast vectorized training.
    """
    def __init__(self, capacity=30000):
        self.buffer = deque(maxlen=capacity)

    def push(self, state, action_idx, reward, next_state, done):
        self.buffer.append((state, action_idx, reward, next_state, float(done)))

    def sample_tensors(self, batch_size):
        batch = random.sample(self.buffer, batch_size)
        states, actions, rewards, next_states, dones = zip(*batch)
        return (
            torch.tensor(states, dtype=torch.float32),
            torch.tensor(actions, dtype=torch.int64).unsqueeze(1),
            torch.tensor(rewards, dtype=torch.float32).unsqueeze(1),
            torch.tensor(next_states, dtype=torch.float32),
            torch.tensor(dones, dtype=torch.float32).unsqueeze(1)
        )

    def __len__(self):
        return len(self.buffer)


# ==============================================================================
# SECTION 5: GAME ENTITIES, 16-D STATE VECTOR & PROGRESSIVE RULES
# ==============================================================================
class Particle:
    def __init__(self, pos):
        self.pos = list(pos)
        self.radius = random.randint(2, 4)
        self.color = random.choice([(255, 255, 0), (255, 165, 0)])
        self.lifetime = random.uniform(0.4, 0.9)
        self.velocity = [random.uniform(-2.5, 2.5), random.uniform(-2.5, 2.5)]
        self.birth_time = time.time()

    def update(self):
        self.pos[0] += self.velocity[0]
        self.pos[1] += self.velocity[1]

    def is_alive(self):
        return (time.time() - self.birth_time) < self.lifetime

    _sprites = {}

    def draw(self, surface):
        alpha = max(0, int(255 * (1 - (time.time() - self.birth_time) / self.lifetime)))
        key = (self.radius, self.color)
        s = Particle._sprites.get(key)
        if s is None:
            s = pygame.Surface((self.radius * 2, self.radius * 2), pygame.SRCALPHA)
            pygame.draw.circle(s, self.color + (255,), (self.radius, self.radius), self.radius)
            Particle._sprites[key] = s
        s.set_alpha(alpha)
        surface.blit(s, (self.pos[0] - self.radius, self.pos[1] - self.radius))


def generate_distinct_colors(n, avoid_hue=None, margin=0.2):
    """Well-spread colors; with avoid_hue (0..1) no color is close to that hue (e.g. the human's green)."""
    if n == 0:
        return []
    golden_angle_conjugate = 0.618033988749895
    h = 0.0
    colors = []
    while len(colors) < n:
        too_close = avoid_hue is not None and min(abs(h - avoid_hue), 1 - abs(h - avoid_hue)) < margin
        if not too_close:
            r, g, b = colorsys.hsv_to_rgb(h, 0.88, 0.95)
            colors.append((int(r * 255), int(g * 255), int(b * 255)))
        h = (h + golden_angle_conjugate) % 1.0
    return colors


def get_contrasting_color(color):
    r, g, b = color
    brightness = (r * 299 + g * 587 + b * 114) / 1000
    return WHITE if brightness < 128 else BLACK


_TEXT_CACHE = {}


def render_text_cached(font, text, color):
    """font.render() is the most expensive HUD call; most lines are identical frame after frame."""
    key = (id(font), text, color)
    text_surface = _TEXT_CACHE.get(key)
    if text_surface is None:
        if len(_TEXT_CACHE) > 4000:
            _TEXT_CACHE.clear()
        text_surface = font.render(text, True, color)
        _TEXT_CACHE[key] = text_surface
    return text_surface


def draw_text(surface, text, font, color, pos, center=False, alpha=255):
    text_surface = render_text_cached(font, text, color)
    text_surface.set_alpha(alpha)
    rect = text_surface.get_rect(center=pos) if center else text_surface.get_rect(topleft=pos)
    surface.blit(text_surface, rect)


class Food:
    def __init__(self, width, height, position=None):
        self.width = width
        self.height = height
        if position is not None:
            self.posicion = position
        else:
            self.generate_new_position()

    def generate_new_position(self):
        cols = max(1, (self.width - BLOCK_SIZE) // BLOCK_SIZE)
        rows = max(1, (self.height - BLOCK_SIZE) // BLOCK_SIZE)
        self.posicion = (
            random.randint(0, cols) * BLOCK_SIZE,
            random.randint(0, rows) * BLOCK_SIZE
        )


class Snake:
    def __init__(self, color, initial_pos, game_ref, is_user=False, name=None, external_tactic=None,
                 hidden_size=DEFAULT_HIDDEN_SIZE, lr=DEFAULT_LR, gamma=DEFAULT_GAMMA, imitation_rate=DEFAULT_IMITATION_RATE):
        self.game = game_ref
        self.segmentos = [initial_pos]
        self.direccion = random.choice(ACTIONS)
        self.original_color = color
        self.color = color
        self.puntaje = 0.0
        self.max_score_achieved = 0.0
        self.viva = True
        self.ultimo_segmento = initial_pos
        self.death_reason = None
        self.es_usuario = is_user
        self.external_tactic = external_tactic
        self.nombre = HUMAN_NAME if is_user else (
            external_tactic["name"] if external_tactic else (name if name else "AI_0")
        )

        # Double DQN Networks (Online + Target)
        self.input_size = STATE_SIZE
        self.output_size = ACTION_SIZE
        self.hidden_size = hidden_size
        self.lr = lr
        self.dqn = DQN(self.input_size, self.output_size, hidden_size=hidden_size)
        self.target_dqn = DQN(self.input_size, self.output_size, hidden_size=hidden_size)
        self.target_dqn.load_state_dict(self.dqn.state_dict())
        self.optimizer = torch.optim.Adam(self.dqn.parameters(), lr=lr)

        self.gamma = gamma
        self.epsilon = DEFAULT_EPSILON_START
        self.imitation_rate = imitation_rate
        self.steps_done = 0
        self.memory = deque(maxlen=4000)

        self.prev_state = None
        self.prev_action = self.direccion
        self.prev_head = initial_pos
        self.last_reward = 0.0
        self.died_last_step = False
        self.last_learning_message = ""

        # Progressive collision flags
        self.self_collision_warning_active = False
        self.self_collision_warning_end_time = 0.0
        self.self_collision_enabled = False
        self.is_spawning = False
        self.spawn_end_time = 0.0
        self.time_to_10_points = None
        self.life_start_time = time.time()

        # Anti-cheat CPU telemetry
        self.timeout_penalties = 0
        self.turns_without_food = 0
        self.total_decision_time_ms = 0.0
        self.decisions_count = 0

        # Telemetry (see Game.write_telemetry)
        self.decision_ms = deque(maxlen=20000)  # recent decisions only (percentiles)
        self.dec_count = 0
        self.dec_sum = 0.0
        self.dec_max = 0.0
        self.dec_over = 0
        self.tactic_exceptions = 0
        self.invalid_actions = 0
        self.lives = []
        self.death_code = None
        self.killer = None
        self.life_start_turn = 0
        self.life_foods = 0
        self.life_milestones = {}

    def best_score(self):
        return max(self.max_score_achieved, self.puntaje)

    def code_factor(self):
        return self.external_tactic.get("code_factor", 1.0) if self.external_tactic else 1.0

    def ranking_score(self):
        """Tournament ranking: best score reached, scaled by the code-size factor of LLM tactics."""
        return self.best_score() * self.code_factor()

    def valid_non_reverse_actions(self):
        """Prevents suicidal 180-degree turns when the snake has length >= 2."""
        if len(self.segmentos) >= 2:
            opposite = OPPOSITES.get(self.direccion)
            return [a for a in ACTIONS if a != opposite]
        return ACTIONS

    def decay_epsilon(self):
        """Smooth exponential decay by accumulated experience steps (survives across deaths)."""
        self.steps_done += 1
        decay_factor = math.exp(-self.steps_done / EPSILON_DECAY_STEPS)
        self.epsilon = max(
            DEFAULT_EPSILON_MIN,
            DEFAULT_EPSILON_MIN + (DEFAULT_EPSILON_START - DEFAULT_EPSILON_MIN) * decay_factor
        )

    @staticmethod
    def shortest_delta(coord_from, coord_to, max_size, toroidal):
        direct = coord_to - coord_from
        if not toroidal:
            return direct
        wrap_pos = direct + max_size
        wrap_neg = direct - max_size
        return min((direct, wrap_pos, wrap_neg), key=abs)

    @staticmethod
    def compute_effective_distance(pos1, pos2, width, height, toroidal):
        dx = Snake.shortest_delta(pos1[0], pos2[0], width, toroidal)
        dy = Snake.shortest_delta(pos1[1], pos2[1], height, toroidal)
        return math.hypot(dx, dy)

    def get_danger_level(self, candidate_pos, width, height, all_snake_bodies):
        x, y = candidate_pos
        if x < 0 or x >= width or y < 0 or y >= height:
            if self.puntaje >= WALL_COLLISION_THRESHOLD:
                return 1.0
            x %= width
            y %= height
            candidate_pos = (x, y)

        if candidate_pos in self.segmentos[1:]:
            if self.self_collision_enabled:
                return 1.0
            elif self.self_collision_warning_active or self.puntaje >= 60:
                return 0.5

        if self.puntaje >= ENEMY_COLLISION_THRESHOLD:
            other_names = all_snake_bodies.get(candidate_pos, set())
            if any(name != self.nombre for name in other_names):
                return 1.0

        return 0.0

    def free_space(self, start, width, height, grid, cap):
        """Cells reachable from `start` (capped) under the rules currently lethal for this snake."""
        toroidal = self.puntaje < WALL_COLLISION_THRESHOLD
        avoid_self = self.self_collision_enabled or self.self_collision_warning_active or self.puntaje >= 90
        avoid_enemy = self.puntaje >= ENEMY_COLLISION_THRESHOLD - 10
        tail = self.segmentos[-1]
        me = self.nombre

        def blocked(pos):
            names = grid.get(pos)
            if not names:
                return False
            if me in names and avoid_self and pos != tail:
                return True
            return avoid_enemy and any(n != me for n in names)

        x, y = start
        if toroidal:
            start = (x % width, y % height)
        elif not (0 <= x < width and 0 <= y < height):
            return 0
        if blocked(start):
            return 0
        seen = {start}
        stack = [start]
        while stack and len(seen) < cap:
            cx, cy = stack.pop()
            for ox, oy in DELTAS.values():
                nx, ny = cx + ox, cy + oy
                if toroidal:
                    nx %= width
                    ny %= height
                elif not (0 <= nx < width and 0 <= ny < height):
                    continue
                pos = (nx, ny)
                if pos not in seen and not blocked(pos):
                    seen.add(pos)
                    stack.append(pos)
        return len(seen)

    def get_state(self, target_pos, width, height, all_snake_bodies=None):
        """23-value normalized state: the classic 16 features (also given to LLM tactics as
        state_vector_16) + free space per direction, length, enemy-collision phase and hunger."""
        if all_snake_bodies is None:
            all_snake_bodies = self.game.collision_grid

        head = self.segmentos[0]
        toroidal = self.puntaje < WALL_COLLISION_THRESHOLD

        dx = self.shortest_delta(head[0], target_pos[0], width, toroidal)
        dy = self.shortest_delta(head[1], target_pos[1], height, toroidal)

        norm_dx = max(-1.0, min(1.0, dx / max(1.0, width / 2.0)))
        norm_dy = max(-1.0, min(1.0, dy / max(1.0, height / 2.0)))

        food_up = 1.0 if dy < 0 else 0.0
        food_down = 1.0 if dy > 0 else 0.0
        food_left = 1.0 if dx < 0 else 0.0
        food_right = 1.0 if dx > 0 else 0.0

        dangers = []
        for acc in ACTIONS:
            ox, oy = DELTAS[acc]
            next_pos = (head[0] + ox, head[1] + oy)
            dangers.append(self.get_danger_level(next_pos, width, height, all_snake_bodies))

        return [
            norm_dx,
            norm_dy,
            food_up,
            food_down,
            food_left,
            food_right,
            dangers[0],  # UP
            dangers[1],  # DOWN
            dangers[2],  # LEFT
            dangers[3],  # RIGHT
            1.0 if self.direccion == 'UP' else 0.0,
            1.0 if self.direccion == 'DOWN' else 0.0,
            1.0 if self.direccion == 'LEFT' else 0.0,
            1.0 if self.direccion == 'RIGHT' else 0.0,
            1.0 if self.self_collision_enabled else (0.5 if self.self_collision_warning_active else 0.0),
            1.0 if self.puntaje >= WALL_COLLISION_THRESHOLD else 0.0
        ] + self.planning_features(head, width, height, all_snake_bodies, dangers)

    def planning_features(self, head, width, height, grid, dangers):
        # Built-in DQN snakes look as far as their full length (a long snake must not enter a pocket
        # smaller than itself); other snakes keep the cheap capped value (CPU).
        cap = FLOOD_CAP_DQN if (self.external_tactic is None and not self.es_usuario) else FLOOD_CAP
        need = max(1, min(len(self.segmentos) + 5, cap))
        spaces = []
        for i, acc in enumerate(ACTIONS):
            if dangers[i] >= 1.0:
                spaces.append(0.0)
                continue
            ox, oy = DELTAS[acc]
            spaces.append(min(1.0, self.free_space((head[0] + ox, head[1] + oy), width, height, grid, need) / need))
        return spaces + [
            min(1.0, len(self.segmentos) / 300.0),
            1.0 if self.puntaje >= ENEMY_COLLISION_THRESHOLD else 0.0,
            min(1.0, self.turns_without_food / STARVATION_TURNS),
        ]

    def build_sanitized_perception(self, state_vec):
        """
        Builds a strictly read-only perception dictionary (tuples & primitives) for external LLM agents.
        Includes both English keys (primary) and Spanish aliases for backward compatibility.
        """
        enemies_en = []
        enemies_es = []
        for idx, other in enumerate(self.game.snakes):
            if other is not self and other.viva:
                enemies_en.append({
                    "id": idx,
                    "head": tuple(other.segmentos[0]),
                    "body": tuple(other.segmentos),
                    "direction": other.direccion,
                    "score": float(other.puntaje),
                    "cabeza": tuple(other.segmentos[0]),
                    "cuerpo": tuple(other.segmentos),
                    "direccion": other.direccion,
                    "puntaje": float(other.puntaje)
                })
                enemies_es.append(enemies_en[-1])

        active_rules = {
            "self_collision": bool(self.self_collision_enabled),
            "self_collision_warning": bool(self.self_collision_warning_active),
            "wall_collision": bool(self.puntaje >= WALL_COLLISION_THRESHOLD),
            "enemy_collision": bool(self.puntaje >= ENEMY_COLLISION_THRESHOLD),
            "toroidal_world": bool(self.puntaje < WALL_COLLISION_THRESHOLD),
            # Spanish compatibility aliases
            "auto_colision": bool(self.self_collision_enabled),
            "advertencia_auto_colision": bool(self.self_collision_warning_active),
            "pared_mortal": bool(self.puntaje >= WALL_COLLISION_THRESHOLD),
            "colision_enemigos": bool(self.puntaje >= ENEMY_COLLISION_THRESHOLD),
            "mundo_toroidal": bool(self.puntaje < WALL_COLLISION_THRESHOLD)
        }

        valid_en = tuple(self.valid_non_reverse_actions())
        foods_tuple = tuple(f.posicion for f in self.game.foods)

        return {
            # Primary English API
            "my_name": self.nombre,
            "my_head": tuple(self.segmentos[0]),
            "my_body": tuple(self.segmentos),
            "my_direction": self.direccion,
            "my_score": float(self.puntaje),
            "width": int(self.game.ANCHO),
            "height": int(self.game.ALTO),
            "block_size": int(BLOCK_SIZE),
            "foods": foods_tuple,
            "enemies": tuple(enemies_en),
            "active_rules": active_rules,
            "state_vector_16": tuple(state_vec[:16]),
            "previous_reward": float(self.last_reward),
            "died_last_step": bool(self.died_last_step),
            "valid_actions": valid_en,
            "turns_without_food": int(self.turns_without_food),
            "starvation_turns": int(STARVATION_TURNS),
            # Spanish aliases
            "mi_nombre": self.nombre,
            "mi_cabeza": tuple(self.segmentos[0]),
            "mi_cuerpo": tuple(self.segmentos),
            "mi_direccion": self.direccion,
            "mi_puntaje": float(self.puntaje),
            "ancho": int(self.game.ANCHO),
            "alto": int(self.game.ALTO),
            "tamano_bloque": int(BLOCK_SIZE),
            "comidas": foods_tuple,
            "enemigos": tuple(enemies_es),
            "reglas_activas": active_rules,
            "vector_estado_16": tuple(state_vec[:16]),
            "recompensa_anterior": float(self.last_reward),
            "murio_en_ultimo_paso": bool(self.died_last_step),
            "acciones_validas": valid_en
        }

    def choose_action(self, state):
        valid_actions = self.valid_non_reverse_actions()

        # 1. External LLM Competitor Tactic
        if self.external_tactic is not None:
            perception = self.build_sanitized_perception(state)
            # Python's garbage collector can pause the whole process for 50+ ms (torch objects),
            # which would randomly penalize whichever tactic happens to be running: keep it out
            # of the timed window; it runs normally right after the call.
            gc_was_enabled = gc.isenabled()
            gc.disable()
            t0 = time.perf_counter()
            try:
                raw_choice = self.external_tactic["decide"](perception)
                chosen = ACTION_ALIASES.get(str(raw_choice).upper().strip(), None)
            except Exception as e:
                logging.debug(f"Exception in tactic {self.nombre}: {e}")
                self.tactic_exceptions += 1
                chosen = random.choice(valid_actions)
            dt_sec = time.perf_counter() - t0
            if gc_was_enabled:
                gc.enable()
            self.total_decision_time_ms += dt_sec * 1000.0
            self.decisions_count += 1
            ms = dt_sec * 1000.0
            self.decision_ms.append(ms)
            self.dec_count += 1
            self.dec_sum += ms
            self.dec_max = max(self.dec_max, ms)
            if dt_sec > MAX_DECISION_TIME_SEC:
                self.dec_over += 1
            if chosen not in valid_actions:
                self.invalid_actions += 1

            if dt_sec > MAX_DECISION_TIME_SEC:
                self.timeout_penalties += 1
                chosen = random.choice(valid_actions)

            if chosen not in valid_actions:
                chosen = random.choice(valid_actions)
            return chosen

        # 2. Built-in Hybrid Double DQN Agent with a safety shield: exploration and the greedy
        # choice only consider moves that do not kill at once and do not enter a dead-end pocket
        # (free-space features), unless every move is bad.
        if len(state) >= 20:
            not_lethal = [a for a in valid_actions if state[6 + ACTIONS.index(a)] < 1.0]
            roomy = [a for a in not_lethal if state[16 + ACTIONS.index(a)] >= 1.0]
            allowed = roomy or not_lethal or list(valid_actions)
            # Hunger drive: after half the starvation limit without food, only moves toward the
            # target food are allowed (if any is safe), so the shield cannot make it circle forever.
            if len(state) >= 23 and state[22] >= 0.33:
                toward = [a for a in allowed if state[2 + ACTIONS.index(a)] > 0.0]
                allowed = toward or allowed
        else:
            allowed = list(valid_actions)
        if random.random() < self.epsilon:
            return random.choice(allowed)

        with torch.no_grad():
            state_tensor = torch.tensor(state, dtype=torch.float32).unsqueeze(0)
            q_values = self.dqn(state_tensor).squeeze(0).clone()
            for idx, acc in enumerate(ACTIONS):
                if acc not in allowed:
                    q_values[idx] = -1e9
            best_index = int(q_values.argmax().item())
            return ACTIONS[best_index]

    def update_dqn_step(self, state, action, reward, next_state, done):
        action_index = ACTIONS.index(action)
        if self.game and hasattr(self.game, "shared_buffer"):
            self.game.shared_buffer.push(state, action_index, reward, next_state, done)
        if self.external_tactic is not None:
            # LLM tactics never use this network: their experience only feeds the shared
            # buffer the built-in DQN snakes learn from, so skip the useless training.
            return
        if self.game is not None and getattr(self.game, "dqn_mode", "separate") == "unified":
            # The shared network is trained once per frame in Game.step_simulation.
            self.decay_epsilon()
            return
        self.memory.append((state, action_index, reward, next_state, float(done)))

        self.decay_epsilon()

        state_t = torch.tensor(state, dtype=torch.float32).unsqueeze(0)
        next_state_t = torch.tensor(next_state, dtype=torch.float32).unsqueeze(0)
        q_pred = self.dqn(state_t)[0, action_index]

        with torch.no_grad():
            best_next_action = self.dqn(next_state_t).argmax(dim=1, keepdim=True)
            next_q = self.target_dqn(next_state_t).gather(1, best_next_action).squeeze()
            target = reward + (1.0 - float(done)) * self.gamma * next_q

        loss = F.smooth_l1_loss(q_pred, target)
        if self.game is not None and hasattr(self.game, "record_dqn_loss"):
            self.game.record_dqn_loss(loss.item())
        self.optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(self.dqn.parameters(), 2.0)
        self.optimizer.step()
        self.soft_update_target()
        self.last_learning_message = f"R:{reward:+.1f} | L:{loss.item():.3f}"

    def replay_experience(self, batch_size=REPLAY_BATCH_SIZE):
        shared = self.game.shared_buffer if (self.game and hasattr(self.game, "shared_buffer")) else None
        if shared is not None and len(shared) >= batch_size:
            states, actions, rewards, next_states, dones = shared.sample_tensors(batch_size)
        elif len(self.memory) >= batch_size:
            batch = random.sample(self.memory, batch_size)
            s, a, r, ns, d = zip(*batch)
            states = torch.tensor(s, dtype=torch.float32)
            actions = torch.tensor(a, dtype=torch.int64).unsqueeze(1)
            rewards = torch.tensor(r, dtype=torch.float32).unsqueeze(1)
            next_states = torch.tensor(ns, dtype=torch.float32)
            dones = torch.tensor(d, dtype=torch.float32).unsqueeze(1)
        else:
            return

        q_values = self.dqn(states).gather(1, actions)
        with torch.no_grad():
            best_next_actions = self.dqn(next_states).argmax(dim=1, keepdim=True)
            next_q_values = self.target_dqn(next_states).gather(1, best_next_actions)
            targets = rewards + (1.0 - dones) * self.gamma * next_q_values

        loss = F.smooth_l1_loss(q_values, targets)
        if self.game is not None and hasattr(self.game, "record_dqn_loss"):
            self.game.record_dqn_loss(loss.item())
        self.optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(self.dqn.parameters(), 2.0)
        self.optimizer.step()
        self.soft_update_target()
        self.last_learning_message = f"BatchLoss:{loss.item():.3f}"

    def soft_update_target(self, tau=TARGET_UPDATE_TAU):
        for target_param, local_param in zip(self.target_dqn.parameters(), self.dqn.parameters()):
            target_param.data.copy_(tau * local_param.data + (1.0 - tau) * target_param.data)

    def move(self):
        if not self.viva:
            return
        head_x, head_y = self.segmentos[0]
        dx, dy = DELTAS[self.direccion]
        head_x += dx
        head_y += dy

        if self.puntaje < WALL_COLLISION_THRESHOLD:
            head_x %= self.game.ANCHO
            head_y %= self.game.ALTO

        new_head = (head_x, head_y)
        self.ultimo_segmento = self.segmentos[-1]
        self.segmentos.insert(0, new_head)
        self.segmentos.pop()

    def grow(self):
        self.segmentos.append(self.ultimo_segmento)

    def respawn(self):
        if self.game.analytics:
            self.game.analytics.log_death(self)

        turn = self.game.frame_counter
        head = self.segmentos[0]
        self.lives.append({
            "start_turn": self.life_start_turn,
            "end_turn": turn,
            "score": self.puntaje,
            "length": len(self.segmentos),
            "foods": self.life_foods,
            "cause": self.death_code or "episode_reset",
            "killer": self.killer,
            "death_cell": [head[0] // BLOCK_SIZE, head[1] // BLOCK_SIZE] if self.death_code else None,
            "turns_to_score": {str(k): v for k, v in sorted(self.life_milestones.items())},
        })
        self.death_code = None
        self.killer = None
        self.life_start_turn = turn
        self.life_foods = 0
        self.life_milestones = {}

        if self.puntaje > self.max_score_achieved:
            self.max_score_achieved = self.puntaje

        width, height = self.game.ANCHO, self.game.ALTO
        cols = max(1, (width - BLOCK_SIZE) // BLOCK_SIZE)
        rows = max(1, (height - BLOCK_SIZE) // BLOCK_SIZE)
        new_pos = (
            random.randint(0, cols) * BLOCK_SIZE,
            random.randint(0, rows) * BLOCK_SIZE
        )

        self.segmentos = [new_pos]
        self.direccion = random.choice(ACTIONS)
        self.viva = True
        self.death_reason = None
        self.puntaje = 0.0
        self.self_collision_warning_active = False
        self.self_collision_enabled = False
        self.time_to_10_points = None
        self.turns_without_food = 0
        self.is_spawning = True
        self.spawn_end_time = time.time() + 3.0
        self.life_start_time = time.time()


# ==============================================================================
# SECTION 6: SCIENTIFIC TELEMETRY & LEARNING ANALYTICS (GAME ANALYTICS)
# ==============================================================================
class GameAnalytics:
    def __init__(self, snakes):
        self.start_time = time.time()
        self.snakes = snakes
        self.scores_history = defaultdict(list)
        self.death_reasons = defaultdict(lambda: defaultdict(int))
        self.imitation_events = []
        self.periodic_snapshots = []
        self.time_as_leader = defaultdict(float)
        self.food_eaten_count = defaultdict(int)
        self.survival_times = defaultdict(list)

    def log_survival(self, snake_name, duration):
        if duration > 1.0:
            self.survival_times[snake_name].append(duration)

    def log_death(self, snake):
        if snake.puntaje > 0:
            self.scores_history[snake.nombre].append(snake.puntaje)
        if snake.death_reason:
            self.death_reasons[snake.nombre][snake.death_reason] += 1

    def log_imitation(self, student, mentor):
        self.imitation_events.append({'student': student.nombre, 'mentor': mentor.nombre})

    def log_periodic_snapshot(self):
        snapshot = {
            'timestamp': time.time() - self.start_time,
            'snakes': [
                {
                    'name': s.nombre,
                    'score': s.puntaje,
                    'max_score': max(s.max_score_achieved, s.puntaje),
                    'epsilon': s.epsilon,
                    'length': len(s.segmentos)
                }
                for s in self.snakes
            ]
        }
        self.periodic_snapshots.append(snapshot)

    @staticmethod
    def _analyze_improvement(scores):
        if len(scores) < 2:
            return "Insufficient data", 0.0, 0.0
        mid = len(scores) // 2
        first_half = scores[:mid]
        second_half = scores[mid:]
        avg_first = sum(first_half) / len(first_half)
        avg_second = sum(second_half) / len(second_half)
        if avg_second > avg_first * 1.15:
            return "Significant Improvement", avg_first, avg_second
        elif avg_second > avg_first:
            return "Slight Improvement", avg_first, avg_second
        return "Stable / No Clear Gain", avg_first, avg_second

    def generate_report(self, filename="game_analysis_report.txt"):
        for s in self.snakes:
            if s.viva:
                if s.puntaje > s.max_score_achieved:
                    s.max_score_achieved = s.puntaje
                self.log_survival(s.nombre, time.time() - s.life_start_time)

        fastest_10 = [s for s in self.snakes if s.time_to_10_points is not None and s.time_to_10_points > 0.2]
        sorted_snakes = sorted([s for s in self.snakes if not s.es_usuario], key=lambda s: s.ranking_score(), reverse=True)
        user_snake = next((s for s in self.snakes if s.es_usuario), None)

        with open(filename, "w", encoding="utf-8") as f:
            f.write(f"{tr('report_title')} - {time.ctime()}\n")
            f.write(f"Session Duration: {time.time() - self.start_time:.1f} seconds\n")
            f.write("=" * 75 + "\n\n")

            f.write("--- Ranking by Adjusted Score = Max Score x Code-Size Factor ---\n")
            f.write(f"(Code factor = 1 - {CODE_SIZE_PENALTY} x log2(chars / chars of the shortest LLM tactic); built-in DQN = 1.0)\n")
            if user_snake:
                f.write(f"Human Player ({user_snake.nombre}): Max Score = {user_snake.max_score_achieved:.1f}\n")
                f.write("-" * 45 + "\n")

            for i, s in enumerate(sorted_snakes):
                kind = f"[LLM:{s.external_tactic['file']}]" if s.external_tactic else "[Built-in-DQN]"
                avg_ms = (s.total_decision_time_ms / s.decisions_count) if s.decisions_count > 0 else 0.0
                if s.external_tactic:
                    learn = f"Code: {s.external_tactic.get('code_chars', 0)} chars x{s.code_factor():.3f}"
                else:
                    learn = f"Eps: {s.epsilon:.3f}"
                f.write(
                    f"Rank {i+1:02d}: {s.nombre:<32} {kind:<34} | Adjusted: {s.ranking_score():<7.1f} | "
                    f"Max: {s.max_score_achieved:<6.0f} | Curr: {s.puntaje:<5.0f} | {learn} | "
                    f"CPU: {avg_ms:.2f}ms (Timeouts: {s.timeout_penalties})\n"
                )

            f.write("\n" + "=" * 75 + "\n")
            f.write("\n--- Highlight Metrics ---\n")
            capable = [s for s in self.snakes if s.max_score_achieved > 0]
            if capable:
                best = max(capable, key=lambda s: s.max_score_achieved)
                f.write(f"Most Capable Agent: {best.nombre} ({best.max_score_achieved:.0f} points).\n")
            if fastest_10:
                winner = min(fastest_10, key=lambda s: s.time_to_10_points)
                f.write(f"Fastest to 10 Points: {winner.nombre} ({winner.time_to_10_points:.2f} s).\n")
            if self.time_as_leader:
                king = max(self.time_as_leader, key=self.time_as_leader.get)
                f.write(f"King of the Hill: {king} ({self.time_as_leader[king]:.1f} s in 1st place).\n")
            if self.food_eaten_count:
                voracious = max(self.food_eaten_count, key=self.food_eaten_count.get)
                f.write(f"Most Voracious: {voracious} ({self.food_eaten_count[voracious]} total foods eaten).\n")

            f.write("\n--- Machine Learning & Adaptation Evidence ---\n")
            if not self.death_reasons:
                f.write("Recorded deaths: None yet.\n")
            else:
                f.write("Causes of death per agent:\n")
                for name, reasons in self.death_reasons.items():
                    f.write(f"  - {name}: {dict(reasons)}\n")

            if self.scores_history:
                f.write("\nScore progression between 1st half and 2nd half of lives:\n")
                for name, scores in self.scores_history.items():
                    cat, a1, a2 = self._analyze_improvement(scores)
                    if cat != "Insufficient data":
                        f.write(f"  - {name:<28}: {cat} (Mean rose from {a1:.1f} to {a2:.1f} pts)\n")

            if self.imitation_events:
                f.write(f"\nMentor Knowledge Distillation events recorded: {len(self.imitation_events)}\n")

            if len(self.periodic_snapshots) >= 2:
                s0 = sum(s['max_score'] for s in self.periodic_snapshots[0]['snakes']) / len(self.periodic_snapshots[0]['snakes'])
                s1 = sum(s['max_score'] for s in self.periodic_snapshots[-1]['snakes']) / len(self.periodic_snapshots[-1]['snakes'])
                f.write(f"\nPopulation Mean Max-Score Trend: {s0:.2f} -> {s1:.2f} pts\n")

        game = self.snakes[0].game if self.snakes else None
        if game is not None:
            game.write_telemetry()


# ==============================================================================
# SECTION 7: MAIN PYGAME ENGINE, CONVERGENCE GRAPH & TURBO TRAINING MODE
# ==============================================================================
def make_tone(freq, ms, volume=0.2):
    """Minimal synthesized beep (no sound files): a short decaying square wave, built once at start-up."""
    rate, size, channels = pygame.mixer.get_init()
    if size != -16:
        return None
    n = int(rate * ms / 1000)
    half = max(1, int(rate / freq) // 2)
    amp = 32767 * volume
    wave = array.array("h", (int(amp * (1 - i / n)) * (1 - 2 * ((i // half) & 1)) for i in range(n)))
    if channels > 1:
        wave = array.array("h", (s for s in wave for _ in range(channels)))
    return pygame.mixer.Sound(buffer=wave.tobytes())


class Game:
    def __init__(self, num_snakes, num_food, play_as_user, fps=FPS, headless=False, dqn_mode=DEFAULT_DQN_MODE,
                 arena_size=None, sync_catalog=True, round_info=None):
        self.dqn_mode = dqn_mode if dqn_mode in DQN_MODES else DEFAULT_DQN_MODE
        self.sync_catalog = sync_catalog
        self.round_info = round_info
        self.headless = headless
        if headless:
            os.environ["SDL_VIDEODRIVER"] = "dummy"
            os.environ["SDL_AUDIODRIVER"] = "dummy"
        pygame.init()
        self.fps = fps
        if headless:
            self.ANCHO, self.ALTO = arena_size or (1000, 700)
            self.ventana = pygame.display.set_mode((self.ANCHO, self.ALTO))
        else:
            info = pygame.display.Info()
            self.ANCHO = info.current_w
            self.ALTO = info.current_h
            self.ventana = pygame.display.set_mode((self.ANCHO, self.ALTO), pygame.FULLSCREEN)
        pygame.display.set_caption(tr("window_title"))

        self.clock = pygame.time.Clock()
        self.running = True
        self.game_state = "PLAYING"
        self.snakes_fallen_this_episode = set()
        self.paused = False
        self.turbo_mode = False
        self.episode_count = 0
        self.episode_start = time.time()
        self.pause_start_time = None
        self.speed_multiplier = 1.0
        self.frame_counter = 0
        self.active_llm_count = 0

        # UI Timers
        self.ranking_message_timer = 0.0
        self.best_q_timer = 0.0
        self.show_ranking_message = False
        self.ranking_message_end = 0.0
        self.leader_message = ""
        self.leader_message_color = WHITE
        self.leader_message_end = 0.0
        self.best_q_message = ""
        self.best_q_message_color = WHITE
        self.best_q_message_end = 0.0
        self.fastest_snake_message = ""
        self.fastest_snake_message_color = WHITE
        self.fastest_snake_message_end_time = 0.0
        self.fastest_snake_ever = None
        self.fastest_time_ever = float('inf')

        # Fonts
        self.font_info_alive = pygame.font.SysFont(None, 18)
        self.font_info_dead = pygame.font.SysFont(None, 14)
        self.font_rank = pygame.font.SysFont(None, 20)
        self.font_instr = pygame.font.SysFont(None, 18)
        self.font_msg = pygame.font.SysFont(None, 30)
        self.font_leader = pygame.font.SysFont(None, 34)
        self.font_death = pygame.font.SysFont(None, 20)

        self.learning_log = []
        self.show_logs = False
        self.num_snakes = num_snakes
        self.num_food = num_food
        self.play_as_user = play_as_user
        self.death_messages = []
        self.snakes = []
        self.foods = []
        self.collision_grid = defaultdict(set)
        self.particles = []
        self.convergence_data = []
        self.shared_buffer = SharedReplayBuffer(capacity=30000)
        self.dqn_loss_windows = defaultdict(lambda: [0.0, 0])  # window -> [loss sum, count]

        self.eat_sound = None
        self.collision_sound = None
        if not headless:
            try:
                pygame.mixer.init(frequency=22050, size=-16, channels=1)
                self.eat_sound = make_tone(880, 40)
                self.collision_sound = make_tone(140, 120)
            except Exception:
                pass

        self.analytics = None
        self.setup_game()
        self.analytics = GameAnalytics(self.snakes)

    def setup_game(self):
        external_tactics, _, _ = load_external_tactics(TACTICS_FOLDER, sync=self.sync_catalog)
        self.active_llm_count = len(external_tactics)
        positions = [
            (
                ((int((i + 1) * self.ANCHO / (self.num_snakes + 1))) // BLOCK_SIZE) * BLOCK_SIZE,
                ((self.ALTO // 2) // BLOCK_SIZE) * BLOCK_SIZE
            )
            for i in range(self.num_snakes)
        ]

        start_idx = 0
        if self.play_as_user:
            user_snake = Snake(MATRIX_GREEN, positions[0], self, is_user=True, name=HUMAN_NAME)
            self.snakes.append(user_snake)
            start_idx = 1

        ai_count = self.num_snakes - start_idx
        human_hue = colorsys.rgb_to_hsv(*(c / 255 for c in MATRIX_GREEN))[0] if self.play_as_user else None
        ai_colors = generate_distinct_colors(ai_count, avoid_hue=human_hue)
        used_names = {s.nombre for s in self.snakes}

        for i in range(ai_count):
            tactic = external_tactics[i] if i < len(external_tactics) else None
            if tactic:
                base_name = tactic["name"]
            else:
                base_name = next((n for n in PHILOSOPHER_NAMES if n not in used_names), f"Philosopher_{i}")
            used_names.add(base_name)

            snake = Snake(
                ai_colors[i],
                positions[start_idx + i],
                self,
                is_user=False,
                name=base_name,
                external_tactic=tactic
            )
            self.snakes.append(snake)

        for _ in range(self.num_food):
            self.foods.append(Food(self.ANCHO, self.ALTO))

        self.update_collision_grid()
        for s in self.snakes:
            target = self.get_target_food(s)
            s.prev_state = s.get_state(target.posicion, self.ANCHO, self.ALTO, self.collision_grid)
            s.prev_action = s.direccion
            s.prev_head = s.segmentos[0]

        self.share_dqn_if_unified()
        self.load_q_tables()

    def learning_window_turns(self):
        return max(1, int(LEARNING_WINDOW_MIN * 60 * self.fps))

    def record_dqn_loss(self, value):
        acc = self.dqn_loss_windows[self.frame_counter // self.learning_window_turns()]
        acc[0] += value
        acc[1] += 1

    def dqn_learning_curve(self):
        """Rows [from_min, to_min, lives_ended, avg_life_score, best_life_score, avg_train_loss, self_deaths, starved]."""
        wt = self.learning_window_turns()
        lives = defaultdict(list)
        causes = defaultdict(lambda: defaultdict(int))
        for s in self.dqn_learners():
            for l in s.lives:
                if l["cause"] != "episode_reset":
                    lives[l["end_turn"] // wt].append(l["score"])
                    causes[l["end_turn"] // wt][l["cause"]] += 1
        rows = []
        for w in range(self.frame_counter // wt + 1):
            sc = lives.get(w, [])
            loss_sum, loss_n = self.dqn_loss_windows.get(w, (0.0, 0))
            rows.append([w * LEARNING_WINDOW_MIN, (w + 1) * LEARNING_WINDOW_MIN, len(sc),
                         round(sum(sc) / len(sc), 1) if sc else None, max(sc) if sc else None,
                         round(loss_sum / loss_n, 4) if loss_n else None,
                         causes[w].get("self", 0), causes[w].get("starvation", 0)])
        return rows

    def save_all(self):
        """Writes q_tables.pkl, the report and telemetry.json. Never raises, so it is safe on any exit path."""
        try:
            self.save_q_tables()
        except Exception as e:
            logging.warning(f"[SAVE] q_tables.pkl failed: {e}")
        try:
            self.analytics.generate_report()
        except Exception as e:
            logging.warning(f"[SAVE] report/telemetry failed: {e}")
        self._saved_turn = self.frame_counter

    def dqn_learners(self):
        return [s for s in self.snakes if not s.es_usuario and s.external_tactic is None]

    def share_dqn_if_unified(self):
        """In unified mode every built-in DQN snake uses the same network, target and optimizer."""
        learners = self.dqn_learners()
        if self.dqn_mode != "unified" or not learners:
            return
        lead = learners[0]
        for s in learners[1:]:
            s.dqn, s.target_dqn, s.optimizer = lead.dqn, lead.target_dqn, lead.optimizer

    def hot_reload_tactics(self):
        """Swaps in a tactic file that changed on disk (audited first) without restarting the game:
        the snake keeps its body, score and history. A rejected file keeps the previous version."""
        log = INTEGRITY._log if INTEGRITY else (lambda kind, path, detail="": logging.warning(f"[RELOAD] {kind} {path} {detail}"))
        changed = False
        for s in self.snakes:
            t = s.external_tactic
            if not t:
                continue
            fpath = os.path.join(TACTICS_FOLDER, t["file"])
            try:
                stat = os.stat(fpath)
            except OSError:
                if t.get("stamp") != "missing":
                    log("tactic_missing_kept_old", fpath)
                    t["stamp"] = "missing"
                continue
            stamp = (stat.st_mtime, stat.st_size)
            if stamp == t.get("stamp"):
                continue
            new, reason = load_tactic_file(fpath)
            if new is None:
                log("tactic_reload_rejected", fpath, str(reason))
                t["stamp"] = stamp
                continue
            for key in ("instance", "decide", "code_chars", "declared_name", "stamp"):
                t[key] = new[key]
            changed = True
            log("tactic_reloaded", fpath, f"{new['code_chars']} chars")
        if changed:
            apply_code_factors([s.external_tactic for s in self.snakes if s.external_tactic])

    def write_telemetry(self, filename=TELEMETRY_FILE):
        """ONE compact, human/LLM-readable file with what competitors need to improve.
        Layout: one line per ranking row / per snake; see "readme" inside the file."""
        def phase(score):
            return "0-99" if score < 100 else "100-199" if score < 200 else "200-299" if score < 300 else "300+"

        def median(values):
            v = sorted(values)
            return v[len(v) // 2] if v else None

        def count(items):
            out = defaultdict(int)
            for it in items:
                if it is not None:
                    out[it] += 1
            return dict(out)

        kills = defaultdict(lambda: defaultdict(int))
        for s in self.snakes:
            for life in s.lives:
                if life["killer"]:
                    kills[life["killer"]][s.nombre] += 1
        ranked = sorted(self.snakes, key=lambda s: s.ranking_score(), reverse=True)
        ranking = [[i + 1, s.nombre, round(s.ranking_score(), 1), s.best_score(),
                    s.external_tactic.get("code_chars") if s.external_tactic else None, round(s.code_factor(), 3),
                    "human" if s.es_usuario else ("llm" if s.external_tactic else "dqn")] for i, s in enumerate(ranked)]
        llm = {}
        for s in ranked:
            if not s.external_tactic:
                continue
            ended = [l for l in s.lives if l["cause"] != "episode_reset"]
            milestones = defaultdict(list)
            for l in s.lives + [{"turns_to_score": {str(k): v for k, v in s.life_milestones.items()}}]:
                for k, v in l["turns_to_score"].items():
                    milestones[k].append(v)
            recent = sorted(s.decision_ms)
            llm[s.nombre] = {
                "max": s.best_score(), "now": s.puntaje, "len": len(s.segmentos),
                "foods": self.analytics.food_eaten_count.get(s.nombre, 0) if self.analytics else None,
                "deaths": count(l["cause"] for l in ended),
                "death_score_phase": count(phase(l["score"]) for l in ended),
                "killed_by": count(l["killer"] for l in ended),
                "kills": dict(kills.get(s.nombre, {})),
                "ms": {"avg": round(s.dec_sum / s.dec_count, 2) if s.dec_count else None,
                       "p99": round(recent[int(0.99 * (len(recent) - 1))], 2) if recent else None,
                       "max": round(s.dec_max, 2), "over15": s.dec_over},
                "timeouts": s.timeout_penalties, "errors": s.tactic_exceptions, "invalid": s.invalid_actions,
                "median_turns_to": {k: median(v) for k, v in sorted(milestones.items(), key=lambda kv: int(kv[0]))},
                "last_lives": [[l["score"], l["cause"], l["killer"], l["end_turn"] - l["start_turn"]] for l in ended[-8:]],
            }
        dqn = [s for s in self.snakes if not s.es_usuario and not s.external_tactic]
        dqn_deaths = count(l["cause"] for s in dqn for l in s.lives if l["cause"] != "episode_reset")
        data = {
            "readme": ("Compact referee summary for competitors (rules: tacticas/rules.txt). "
                       "ranking rows = [rank, name, ranking_score, max_score, code_chars, code_factor, type]; "
                       "ranking_score = max_score * code_factor. llm.<name>: max/now score, len = body length, "
                       "deaths by cause (self, wall, enemy, starvation = no food for STARVATION_TURNS), death_score_phase = score when dying, killed_by/kills, ms = decision time "
                       "(avg, p99, max, calls over 15 ms), median_turns_to = turns of one life to reach 10/100/200/300, "
                       "last_lives rows = [score, cause, killer, turns lived]. dqn.learning_curve rows = "
                       "[from_min, to_min, lives_ended, avg_life_score, best_life_score, avg_train_loss, self_deaths, starved] per "
                       f"{LEARNING_WINDOW_MIN} min of game time (rising scores / falling loss = learning). 15 turns = 1 second."),
            "updated": time.strftime("%Y-%m-%d %H:%M:%S"),
            "arena": {"cells": [self.ANCHO // BLOCK_SIZE, self.ALTO // BLOCK_SIZE], "snakes": len(self.snakes),
                      "foods": self.num_food, "fps": self.fps, "turns": self.frame_counter,
                      "episodes": self.episode_count, "dqn_mode": self.dqn_mode,
                      "run": self.round_info},
            "ranking": ranking,
            "llm": llm,
            "dqn": {"snakes": len(dqn), "best_max": max((s.best_score() for s in dqn), default=None),
                    "deaths": dqn_deaths, "epsilon": round(dqn[0].epsilon, 3) if dqn else None,
                    "replay_buffer": len(self.shared_buffer), "learning_curve": self.dqn_learning_curve()},
            "integrity": ["%s %s %s %s" % (e["time"], e["event"], e["path"], e["detail"]) for e in (INTEGRITY.events[-30:] if INTEGRITY else [])],
            "system": {"last": SYSTEM_SAMPLES[-1] if SYSTEM_SAMPLES else None,
                       "max_thermal_c": max((v for x in SYSTEM_SAMPLES for v in x["thermal_zones_c"].values()), default=None),
                       "samples": len(SYSTEM_SAMPLES)},
        }
        parts = []
        for key, value in data.items():
            if key in ("ranking", "integrity") and value:
                rows = ",\n  ".join(json.dumps(r, ensure_ascii=False) for r in value)
                parts.append(f"{json.dumps(key)}: [\n  {rows}\n ]")
            elif key == "llm" and value:
                rows = ",\n  ".join(f"{json.dumps(n, ensure_ascii=False)}: {json.dumps(e, ensure_ascii=False)}" for n, e in value.items())
                parts.append(f'"llm": {{\n  {rows}\n }}')
            else:
                parts.append(f"{json.dumps(key)}: {json.dumps(value, ensure_ascii=False)}")
        text = "{\n " + ",\n ".join(parts) + "\n}\n"
        json.loads(text)
        tmp = filename + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(text)
        os.replace(tmp, filename)

    def get_target_food(self, snake):
        toroidal = snake.puntaje < WALL_COLLISION_THRESHOLD
        head = snake.segmentos[0]
        if snake.external_tactic is None and not snake.es_usuario and len(self.foods) > 1:
            # DQN snakes aim at the nearest food no rival head is closer to (no lost races -> no starvation)
            dist = lambda a, b: Snake.compute_effective_distance(a, b, self.ANCHO, self.ALTO, toroidal)
            rivals = [o.segmentos[0] for o in self.snakes if o is not snake and o.viva]
            mine = [f for f in self.foods
                    if dist(head, f.posicion) < min((dist(r, f.posicion) for r in rivals), default=float("inf"))]
            if mine:
                return min(mine, key=lambda f: dist(head, f.posicion))
        return min(
            self.foods,
            key=lambda food: Snake.compute_effective_distance(
                head, food.posicion, self.ANCHO, self.ALTO, toroidal
            )
        )

    def load_q_tables(self, filename="q_tables.pkl"):
        if not os.path.exists(filename):
            return
        try:
            with open(filename, "rb") as f:
                data = pickle.load(f)
            self.convergence_data = data.get("convergence_data", [])
            self.episode_count = data.get("episode_count", 0)
            snakes_data = data.get("snakes", {})
            loaded_count = 0
            for s in self.snakes:
                if not s.es_usuario and s.nombre in snakes_data:
                    sd = snakes_data[s.nombre]
                    s.epsilon = sd.get("epsilon", DEFAULT_EPSILON_START)
                    s.gamma = sd.get("gamma", DEFAULT_GAMMA)
                    s.imitation_rate = sd.get("imitation_rate", DEFAULT_IMITATION_RATE)
                    s.steps_done = sd.get("steps_done", 0)
                    s.max_score_achieved = sd.get("max_score_achieved", 0.0)
                    if "dqn_state" in sd:
                        try:
                            s.dqn.load_state_dict(sd["dqn_state"])
                            s.target_dqn.load_state_dict(sd.get("target_dqn_state", sd["dqn_state"]))
                            loaded_count += 1
                        except Exception:
                            # saved for a different network/state size: relearn from scratch
                            s.epsilon = DEFAULT_EPSILON_START
                            s.steps_done = 0
            if loaded_count > 0:
                self.learning_log.append(f"Loaded neural weights for {loaded_count} agents.")
        except Exception as e:
            logging.info(f"Starting fresh neural weights ({e}).")

    def save_q_tables(self, filename="q_tables.pkl"):
        data = {
            "episode_count": self.episode_count,
            "convergence_data": self.convergence_data[-500:],
            "snakes": {}
        }
        for s in self.snakes:
            if not s.es_usuario:
                data["snakes"][s.nombre] = {
                    "epsilon": s.epsilon,
                    "gamma": s.gamma,
                    "imitation_rate": s.imitation_rate,
                    "steps_done": s.steps_done,
                    "max_score_achieved": max(s.max_score_achieved, s.puntaje),
                    "dqn_state": s.dqn.state_dict(),
                    "target_dqn_state": s.target_dqn.state_dict()
                }
        with open(filename, "wb") as f:
            pickle.dump(data, f)
        self.learning_log.append(f"Saved neural weights to {filename}")

    def update_collision_grid(self):
        self.collision_grid.clear()
        for snake in self.snakes:
            if snake.viva:
                for seg in snake.segmentos:
                    self.collision_grid[seg].add(snake.nombre)

    def mentor_based_learning(self):
        """
        Knowledge Distillation from Mentor to Student:
        Students query what Q-values the top mentor's brain (`mentor.dqn`) assigns to
        the student's own state (`student_state`).
        """
        ai_snakes = [s for s in self.snakes if not s.es_usuario and s.external_tactic is None and s.viva]
        if len(ai_snakes) < 2:
            return

        mentors = [s for s in ai_snakes if len(s.segmentos) >= 5 and s.puntaje > 5]
        if not mentors:
            return
        mentor = max(mentors, key=lambda x: x.puntaje)

        for student in ai_snakes:
            if student is mentor or student.puntaje >= mentor.puntaje * 0.85:
                continue
            if random.random() < student.imitation_rate and student.prev_state is not None:
                st_tensor = torch.tensor(student.prev_state, dtype=torch.float32).unsqueeze(0)
                with torch.no_grad():
                    mentor_q = mentor.dqn(st_tensor)
                student_q = student.dqn(st_tensor)
                loss = F.smooth_l1_loss(student_q, mentor_q)
                student.optimizer.zero_grad()
                loss.backward()
                student.optimizer.step()
                if self.analytics:
                    self.analytics.log_imitation(student, mentor)

    def step_simulation(self, dt):
        now = time.time()
        self.frame_counter += 1
        self.update_collision_grid()

        for s in self.snakes:
            if s.is_spawning and now > s.spawn_end_time:
                s.is_spawning = False
                s.color = s.original_color
            if s.self_collision_warning_active:
                if self.frame_counter >= s.self_collision_warning_end_time:
                    s.self_collision_warning_active = False
                    s.self_collision_enabled = True
                    s.color = s.original_color
                    self.learning_log.append(tr("msg_rule_100_on", name=s.nombre))
                else:
                    s.color = ALERT_RED if (now % 0.8 < 0.4) else s.original_color

        # 1. OBSERVE S_t AND SELECT ACTION A_t
        targets_before_move = {}
        for s in self.snakes:
            if not s.viva:
                continue
            target_food = self.get_target_food(s)
            targets_before_move[s.nombre] = target_food.posicion
            state = s.get_state(target_food.posicion, self.ANCHO, self.ALTO, self.collision_grid)
            s.prev_state = state
            s.prev_head = s.segmentos[0]
            if not s.es_usuario:
                action = s.choose_action(state)
                s.direccion = action
            s.prev_action = s.direccion

        # 2. MOVE ALL SNAKES
        for s in self.snakes:
            if s.viva:
                s.move()

        self.update_collision_grid()

        # 3. EVALUATE FOOD & COLLISIONS ATOMICALLY IN THE SAME TICK
        ate_map = {s.nombre: False for s in self.snakes}
        died_map = {s.nombre: False for s in self.snakes}

        for s in self.snakes:
            if not s.viva:
                continue
            head = s.segmentos[0]
            head_rect = pygame.Rect(head[0], head[1], BLOCK_SIZE, BLOCK_SIZE)

            # A. Did it eat food?
            for food in self.foods[:]:
                food_rect = pygame.Rect(food.posicion[0], food.posicion[1], BLOCK_SIZE, BLOCK_SIZE)
                if head_rect.colliderect(food_rect):
                    ate_map[s.nombre] = True
                    s.puntaje += 1.0
                    if s.puntaje > s.max_score_achieved:
                        s.max_score_achieved = s.puntaje
                    s.life_foods += 1
                    s.turns_without_food = 0
                    for milestone in (10, 100, 200, 300):
                        if s.puntaje >= milestone and milestone not in s.life_milestones:
                            s.life_milestones[milestone] = self.frame_counter - s.life_start_turn

                    if s.puntaje >= 10 and s.time_to_10_points is None:
                        s.time_to_10_points = now - s.life_start_time
                        if s.time_to_10_points < self.fastest_time_ever:
                            self.fastest_time_ever = s.time_to_10_points
                            self.fastest_snake_ever = s.nombre
                            self.fastest_snake_message = tr("msg_record_10", name=s.nombre, sec=s.time_to_10_points)
                            self.fastest_snake_message_color = s.original_color
                            self.fastest_snake_message_end_time = now + 6.0

                    if s.puntaje >= SELF_COLLISION_THRESHOLD and not s.self_collision_warning_active and not s.self_collision_enabled:
                        s.self_collision_warning_active = True
                        s.self_collision_warning_end_time = self.frame_counter + SELF_COLLISION_WARNING_TURNS
                        self.learning_log.append(tr("msg_warn_100", name=s.nombre))

                    eaten_pos = food.posicion
                    s.grow()
                    self.foods.remove(food)
                    if len(self.foods) < self.num_food:
                        self.foods.append(Food(self.ANCHO, self.ALTO))
                    if self.eat_sound and not self.turbo_mode:
                        self.eat_sound.play()
                    if self.analytics:
                        self.analytics.food_eaten_count[s.nombre] += 1
                    if not self.turbo_mode and not self.headless:
                        self.create_particles(eaten_pos, count=12)
                    break

            if not ate_map[s.nombre]:
                s.turns_without_food += 1

            # B. Did it die under the active progressive rules?
            if s.self_collision_enabled and head in s.segmentos[1:]:
                s.viva = False
                s.death_reason = tr("death_self")
                s.death_code = "self"
                died_map[s.nombre] = True
            elif s.puntaje >= WALL_COLLISION_THRESHOLD and (head[0] < 0 or head[0] >= self.ANCHO or head[1] < 0 or head[1] >= self.ALTO):
                s.viva = False
                s.death_reason = tr("death_wall")
                s.death_code = "wall"
                died_map[s.nombre] = True
            elif s.puntaje >= ENEMY_COLLISION_THRESHOLD:
                others = [name for name in self.collision_grid.get(head, set()) if name != s.nombre]
                if others:
                    s.viva = False
                    s.killer = sorted(others)[0]
                    s.death_reason = tr("death_enemy", other=s.killer)
                    s.death_code = "enemy"
                    died_map[s.nombre] = True
            if not died_map[s.nombre] and s.turns_without_food >= STARVATION_TURNS:
                s.viva = False
                s.death_reason = tr("death_starvation", turns=STARVATION_TURNS)
                s.death_code = "starvation"
                died_map[s.nombre] = True

        # 4. COMPUTE EXACT REWARD & TRAIN NETWORKS (BEFORE RESPAWN)
        for s in self.snakes:
            target_before = targets_before_move.get(s.nombre, self.foods[0].posicion)
            new_head = s.segmentos[0]
            toroidal = s.puntaje < WALL_COLLISION_THRESHOLD

            if died_map[s.nombre]:
                reward = -15.0
            elif ate_map[s.nombre]:
                reward = +15.0
            else:
                old_dist = Snake.compute_effective_distance(s.prev_head, target_before, self.ANCHO, self.ALTO, toroidal)
                new_dist = Snake.compute_effective_distance(new_head, target_before, self.ANCHO, self.ALTO, toroidal)
                reward = +1.0 if new_dist < old_dist else -1.2
                if (s.self_collision_warning_active or s.puntaje >= 60) and new_head in s.segmentos[1:]:
                    reward -= 3.0

            s.last_reward = reward
            s.died_last_step = died_map[s.nombre]

            if not s.es_usuario:
                next_target = self.get_target_food(s)
                next_state = s.get_state(next_target.posicion, self.ANCHO, self.ALTO, self.collision_grid)
                s.update_dqn_step(s.prev_state, s.prev_action, reward, next_state, died_map[s.nombre])

                if self.frame_counter % 4 == 0 and s.external_tactic is None and self.dqn_mode != "unified":
                    s.replay_experience(batch_size=REPLAY_BATCH_SIZE)

        if self.dqn_mode == "unified":
            learners = self.dqn_learners()
            if learners:
                learners[0].replay_experience(batch_size=REPLAY_BATCH_SIZE)
                for s in learners[1:]:
                    s.last_learning_message = learners[0].last_learning_message
        elif self.frame_counter % 10 == 0:
            self.mentor_based_learning()

        # 5. PROCESS DEATHS & RESPAWN
        for s in self.snakes:
            if died_map[s.nombre]:
                life_duration = now - s.life_start_time
                if self.analytics:
                    self.analytics.log_survival(s.nombre, life_duration)
                self.death_messages.append((tr("msg_died", name=s.nombre, reason=s.death_reason), s.original_color, now + 5.0))
                if self.collision_sound and not self.turbo_mode:
                    self.collision_sound.play()
                if not self.turbo_mode and not self.headless:
                    self.create_particles(s.segmentos[0], count=18)

                for seg in s.segmentos[::2]:
                    # a wall death leaves the head outside the arena: never drop unreachable food there
                    if len(self.foods) < self.num_food + 25 and 0 <= seg[0] < self.ANCHO and 0 <= seg[1] < self.ALTO:
                        self.foods.append(Food(self.ANCHO, self.ALTO, position=seg))

                self.snakes_fallen_this_episode.add(s.nombre)
                s.respawn()

                if len(self.snakes_fallen_this_episode) >= len(self.snakes):
                    self.record_convergence_point()
                    self.save_all()
                    self.episode_end_time = time.time()
                    if self.headless or self.turbo_mode:
                        self.start_new_episode()
                    else:
                        self.game_state = "EPISODE_END"

        # 6. UPDATE UI METRICS & TIMERS
        self.ranking_message_timer += dt
        self.best_q_timer += dt

        if self.frame_counter % (self.fps * 5) == 0:
            self.analytics.log_periodic_snapshot()
            self.record_convergence_point()

        if self.ranking_message_timer >= 60:
            self.show_ranking_message = True
            self.ranking_message_end = now + 5.0
            self.ranking_message_timer = 0.0

        if self.best_q_timer >= 30:
            candidates = [s for s in self.snakes if not s.es_usuario]
            if candidates:
                best_c = max(candidates, key=lambda s: max(s.max_score_achieved, s.puntaje))
                self.best_q_message = tr("msg_best_learner", name=best_c.nombre, score=max(best_c.max_score_achieved, best_c.puntaje))
                self.best_q_message_color = best_c.original_color
                self.best_q_message_end = now + 5.0
            self.best_q_timer = 0.0

        alive = [s for s in self.snakes if s.viva]
        if alive:
            current_leader = max(alive, key=lambda s: s.puntaje)
            self.analytics.time_as_leader[current_leader.nombre] += dt
            if len(current_leader.segmentos) >= 5:
                if not hasattr(self, 'last_leader') or self.last_leader != current_leader.nombre:
                    self.last_leader = current_leader.nombre
                    self.leader_message = tr("msg_new_leader", name=current_leader.nombre)
                    self.leader_message_color = current_leader.original_color
                    self.leader_message_end = now + 4.0

    def record_convergence_point(self):
        ai_scores = [max(s.max_score_achieved, s.puntaje) for s in self.snakes if not s.es_usuario]
        if ai_scores:
            self.convergence_data.append(sum(ai_scores) / len(ai_scores))

    def start_new_episode(self):
        self.episode_count += 1
        self.snakes_fallen_this_episode.clear()
        self.episode_start = time.time()
        for s in self.snakes:
            s.respawn()
            s.puntaje = 0.0
        self.game_state = "PLAYING"

    def create_particles(self, pos, count=10):
        for _ in range(count):
            self.particles.append(Particle(pos))

    def draw_particles(self):
        for particle in self.particles[:]:
            particle.update()
            particle.draw(self.ventana)
            if not particle.is_alive():
                self.particles.remove(particle)

    def draw_convergence_graph(self):
        if not self.convergence_data:
            return
        graph_width = 250
        cache_key = (len(self.convergence_data), self.convergence_data[-1], CURRENT_LANG)
        cached = getattr(self, "_graph_cache", None)
        if cached is not None and cached[0] == cache_key:
            self.ventana.blit(cached[1], (self.ANCHO - graph_width - 12, 12))
            return
        graph_height = 110
        margin = 14
        graph_surface = pygame.Surface((graph_width, graph_height))
        graph_surface.fill((30, 30, 38))
        pygame.draw.rect(graph_surface, (90, 90, 110), (0, 0, graph_width, graph_height), 1)
        pygame.draw.line(graph_surface, WHITE, (margin, graph_height - margin), (graph_width - margin, graph_height - margin), 1)
        pygame.draw.line(graph_surface, WHITE, (margin, margin), (margin, graph_height - margin), 1)

        data = self.convergence_data[-(graph_width - 2 * margin):]
        if data:
            max_val = max(data)
            min_val = min(data)
            if max_val == min_val:
                max_val += 1.0
            points = []
            for i, val in enumerate(data):
                x = margin + i
                y = graph_height - margin - int((val - min_val) / (max_val - min_val) * (graph_height - 2 * margin))
                points.append((x, y))
            if len(points) > 1:
                pygame.draw.lines(graph_surface, (0, 255, 120), False, points, 2)
            draw_text(graph_surface, tr("graph_title", val=data[-1]), self.font_info_dead, GOLD, (margin + 4, 2))
        self._graph_cache = (cache_key, graph_surface)
        self.ventana.blit(graph_surface, (self.ANCHO - graph_width - 12, 12))

    def process_events(self):
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                self.save_all()
                self.running = False
            elif event.type == pygame.KEYDOWN:
                if self.game_state == "EPISODE_END" and event.key == pygame.K_RETURN:
                    self.start_new_episode()
                elif event.key == pygame.K_ESCAPE:
                    self.save_all()
                    self.running = False
                elif event.key == pygame.K_i:
                    new_lang = cycle_language()
                    pygame.display.set_caption(tr("window_title"))
                    self.learning_log.append(f"Language switched to {STRINGS[new_lang]['lang_name']}")
                elif event.key == pygame.K_t:
                    self.turbo_mode = not self.turbo_mode
                    self.learning_log.append(f"Turbo Training Mode: {'ON' if self.turbo_mode else 'OFF'}")
                elif event.key == pygame.K_r:
                    for s in self.snakes:
                        s.dqn = DQN(s.input_size, s.output_size, hidden_size=s.hidden_size)
                        s.target_dqn.load_state_dict(s.dqn.state_dict())
                        s.optimizer = torch.optim.Adam(s.dqn.parameters(), lr=s.lr)
                        s.epsilon = DEFAULT_EPSILON_START
                        s.steps_done = 0
                    self.share_dqn_if_unified()
                    self.shared_buffer.buffer.clear()
                    self.learning_log.append("All DQN networks reset from scratch.")
                elif event.key == pygame.K_p:
                    self.paused = not self.paused
                    if self.paused:
                        self.pause_start_time = time.time()
                    elif self.pause_start_time is not None:
                        self.episode_start += (time.time() - self.pause_start_time)
                        self.pause_start_time = None
                elif event.key == pygame.K_l:
                    self.show_logs = not self.show_logs
                elif event.key == pygame.K_1:
                    self.speed_multiplier += 0.5
                elif event.key == pygame.K_0:
                    self.speed_multiplier = max(0.5, self.speed_multiplier - 0.5)
                elif self.play_as_user and event.key in [pygame.K_UP, pygame.K_DOWN, pygame.K_LEFT, pygame.K_RIGHT]:
                    key_map = {
                        pygame.K_UP: "UP",
                        pygame.K_DOWN: "DOWN",
                        pygame.K_LEFT: "LEFT",
                        pygame.K_RIGHT: "RIGHT"
                    }
                    desired = key_map[event.key]
                    if desired in self.snakes[0].valid_non_reverse_actions():
                        self.snakes[0].direccion = desired

    def update(self, dt):
        if self.paused:
            return
        steps = 12 if self.turbo_mode else 1
        for _ in range(steps):
            if self.game_state == "PLAYING":
                self.step_simulation(dt)

    def draw(self):
        if self.headless:
            return
        self.ventana.fill(BLACK)
        for food in self.foods:
            pygame.draw.rect(self.ventana, FOOD_WHITE, (food.posicion[0], food.posicion[1], BLOCK_SIZE, BLOCK_SIZE))

        for s in self.snakes:
            if s.viva:
                color_to_use = WHITE if (s.is_spawning and time.time() % 0.4 < 0.2) else s.color
                for seg in s.segmentos:
                    pygame.draw.rect(self.ventana, color_to_use, (seg[0], seg[1], BLOCK_SIZE, BLOCK_SIZE))

        self.draw_particles()

        elapsed_time = int(time.time() - self.episode_start)
        turbo_badge = tr("turbo_badge") if self.turbo_mode else ""
        episode_info = tr("hud_ep_info", ep=self.episode_count, t=elapsed_time, mem=len(self.shared_buffer), turbo=turbo_badge)
        draw_text(self.ventana, episode_info, self.font_instr, GOLD if self.turbo_mode else WHITE, (10, 10), alpha=240)

        survivors_count = len(self.snakes) - len(self.snakes_fallen_this_episode)
        draw_text(
            self.ventana,
            tr("hud_survivors", surv=survivors_count, total=len(self.snakes), llms=self.active_llm_count),
            self.font_instr, WHITE, (10, 30), alpha=240
        )

        y_offset = 55
        # Every snake is listed (the human player too), each line in that snake's own color.
        sorted_snakes = sorted(self.snakes, key=lambda s: (-s.puntaje, -s.max_score_achieved))
        for idx, s in enumerate(sorted_snakes[:25]):
            pts = f"Pts: {s.puntaje:.0f} (Max:{max(s.max_score_achieved, s.puntaje):.0f})"
            if s.es_usuario:
                info = f"#{idx+1:02d} {s.nombre} [YOU] | {pts}"
            elif s.external_tactic:
                info = f"#{idx+1:02d} {s.nombre} [LLM] | {pts} | Rank: {s.ranking_score():.1f}"
            else:
                info = f"#{idx+1:02d} {s.nombre} [DQN] | {pts} | Eps: {s.epsilon:.2f} | {s.last_learning_message}"
            draw_text(self.ventana, info, self.font_info_alive, s.original_color, (10, y_offset), alpha=215)
            y_offset += 19

        current_time = time.time()
        y_offset_death = self.ALTO - 55
        for msg, col, end_time in self.death_messages[:]:
            if current_time < end_time:
                draw_text(self.ventana, msg, self.font_death, col, (10, y_offset_death), alpha=240)
                y_offset_death -= 22
            else:
                self.death_messages.remove((msg, col, end_time))

        alive_snakes = [s for s in self.snakes if s.viva]
        sorted_alive = sorted(alive_snakes, key=lambda s: s.puntaje, reverse=True)
        for idx, s in enumerate(sorted_alive):
            head_x, head_y = s.segmentos[0]
            contrast_color = get_contrasting_color(s.color)
            rank_text = render_text_cached(self.font_rank, str(idx + 1), contrast_color)
            x = head_x + BLOCK_SIZE // 2 - rank_text.get_width() // 2
            y = head_y + BLOCK_SIZE // 2 - rank_text.get_height() // 2
            self.ventana.blit(rank_text, (x, y))

        instructions = tr("hud_instructions")
        draw_text(
            self.ventana, instructions, self.font_instr, WHITE,
            (self.ANCHO - self.font_instr.size(instructions)[0] - 12, self.ALTO - 28), alpha=240
        )

        if self.show_ranking_message and current_time < self.ranking_message_end:
            top3 = sorted(self.snakes, key=lambda s: s.ranking_score(), reverse=True)[:3]
            ranking_msg = tr("msg_top3") + " | ".join([f"{i+1}. {s.nombre} ({s.ranking_score():.1f})" for i, s in enumerate(top3)])
            draw_text(self.ventana, ranking_msg, self.font_msg, WHITE, (self.ANCHO // 2, 45), center=True)

        if current_time < self.leader_message_end:
            draw_text(self.ventana, self.leader_message, self.font_leader, self.leader_message_color, (self.ANCHO // 2, 85), center=True)
        if current_time < self.best_q_message_end:
            draw_text(self.ventana, self.best_q_message, self.font_msg, self.best_q_message_color, (self.ANCHO // 2, 125), center=True)
        if current_time < self.fastest_snake_message_end_time:
            draw_text(self.ventana, self.fastest_snake_message, self.font_msg, self.fastest_snake_message_color, (self.ANCHO // 2, 165), center=True)

        self.draw_convergence_graph()

        if self.show_logs:
            log_surface = getattr(self, "_log_surface", None)
            if log_surface is None:
                log_surface = pygame.Surface((self.ANCHO - 20, 140))
                log_surface.set_alpha(170)
                log_surface.fill((0, 0, 0))
                self._log_surface = log_surface
            self.ventana.blit(log_surface, (10, self.ALTO - 190))
            for i, line in enumerate(self.learning_log[-7:]):
                draw_text(self.ventana, line, self.font_info_alive, WHITE, (20, self.ALTO - 180 + i * 18))

        pygame.display.update()

    def draw_episode_end_screen(self):
        overlay = pygame.Surface((self.ANCHO, self.ALTO), pygame.SRCALPHA)
        overlay.fill((0, 0, 0, 185))
        self.ventana.blit(overlay, (0, 0))

        draw_text(self.ventana, tr("ep_end_title", ep=self.episode_count), self.font_leader, MATRIX_GREEN, (self.ANCHO // 2, 50), center=True)
        draw_text(self.ventana, tr("ep_end_subtitle"), self.font_msg, WHITE, (self.ANCHO // 2, 105), center=True)

        sorted_by_max = sorted(self.snakes, key=lambda s: s.ranking_score(), reverse=True)
        y_offset = 145
        for i, s in enumerate(sorted_by_max[:15]):
            tag = " [LLM]" if s.external_tactic else ""
            rank_text = f"#{i+1:02d}: {s.nombre + tag:<34} - Score: {s.ranking_score():.1f} (Max {s.best_score():.0f} x{s.code_factor():.3f})"
            draw_text(self.ventana, rank_text, self.font_rank, s.original_color, (self.ANCHO // 2, y_offset), center=True)
            y_offset += 24

        y_offset += 25
        draw_text(self.ventana, tr("ep_end_prompt"), self.font_msg, GOLD, (self.ANCHO // 2, y_offset), center=True)
        left = max(0, int(EPISODE_AUTO_CONTINUE_SEC - (time.time() - getattr(self, "episode_end_time", time.time()))))
        draw_text(self.ventana, f"Auto-continue in {left} s", self.font_msg, WHITE, (self.ANCHO // 2, y_offset + 36), center=True)
        pygame.display.update()

    def run(self, max_episodes=None, max_seconds=None, fps_cap=None, save_every=None, on_save=None):
        started = last_save = time.time()
        if save_every is None:
            save_every = AUTOSAVE_SEC
        self._saved_turn = -1
        try:
            while self.running:
                if fps_cap is not None:
                    target_fps = fps_cap  # 0 = uncapped
                else:
                    target_fps = 240 if (self.turbo_mode or self.headless) else int(self.fps * self.speed_multiplier)
                dt = self.clock.tick(target_fps) / 1000.0
                self.process_events()
                if not self.running:
                    break

                if self.game_state == "PLAYING":
                    self.update(dt)
                    if not self.headless:
                        self.draw()
                elif self.game_state == "EPISODE_END":
                    waited = time.time() - getattr(self, "episode_end_time", 0.0)
                    if self.headless or waited >= EPISODE_AUTO_CONTINUE_SEC:
                        self.start_new_episode()
                    else:
                        self.draw_episode_end_screen()

                if max_episodes is not None and self.episode_count >= max_episodes:
                    break

                now = time.time()
                if save_every and now - last_save >= save_every:
                    if on_save:
                        on_save()
                    self.save_all()
                    last_save = now
                if max_seconds is not None and now - started >= max_seconds:
                    break
        finally:
            # Any exit (ESC, window closed, error, Ctrl+C, time limit) leaves the data on disk.
            if on_save:
                try:
                    on_save()
                except Exception as e:
                    logging.warning(f"[SAVE] final integrity/reload hook failed: {e}")
            if self._saved_turn != self.frame_counter:
                self.save_all()
            pygame.quit()


# ==============================================================================
# SECTION 8: UNIFIED HEADLESS SIMULATOR, OPTUNA OPTIMIZER & INTERACTIVE CLI MENU
# ==============================================================================
def run_headless_evaluation(lr, hidden_size, gamma, imitation_rate, episodes=2, max_steps_per_ep=1500):
    game = Game(num_snakes=4, num_food=8, play_as_user=False, headless=True)
    for s in game.snakes:
        s.lr = lr
        s.hidden_size = hidden_size
        s.gamma = gamma
        s.imitation_rate = imitation_rate
        s.dqn = DQN(STATE_SIZE, ACTION_SIZE, hidden_size=hidden_size)
        s.target_dqn = DQN(STATE_SIZE, ACTION_SIZE, hidden_size=hidden_size)
        s.target_dqn.load_state_dict(s.dqn.state_dict())
        s.optimizer = torch.optim.Adam(s.dqn.parameters(), lr=lr)
        s.epsilon = DEFAULT_EPSILON_START
        s.steps_done = 0
    game.share_dqn_if_unified()

    scores = []
    for _ in range(episodes):
        for _ in range(max_steps_per_ep):
            game.step_simulation(0.05)
        ep_mean = sum(max(s.max_score_achieved, s.puntaje) for s in game.snakes) / len(game.snakes)
        scores.append(ep_mean)
        game.start_new_episode()

    pygame.quit()
    return sum(scores) / len(scores) if scores else 0.0


# ==============================================================================
# UNATTENDED TOURNAMENT MODE (no window, capped speed, low priority) + INTEGRITY MONITOR
# ==============================================================================
GAME_OUTPUT_FILES = {"game_analysis_report.txt", TELEMETRY_FILE, "q_tables.pkl", TELEMETRY_FILE + ".tmp",
                     # written by the Kotlin arena (kotlin/, same game for kotlin/tactics/*.kt)
                     "kotlin/telemetry_kotlin.json", "kotlin/telemetry_kotlin.json.tmp",
                     "kotlin/q_tables_kotlin.bin", "kotlin/q_tables_kotlin.bin.tmp"}
# Build output of the Kotlin arena (Gradle, compiled tactics): not watched
UNWATCHED_DIRS = {"kotlin/build", "kotlin/.gradle", "kotlin/.kotlin"}


class IntegrityMonitor:
    """Referee watchdog: SNAKES.py must not change while running, tactics may only be replaced by
    their owners (every change is logged with time and size for the judge), no other files may
    appear, and Python caches are removed so the folder stays clean."""

    def __init__(self, root="."):
        self.root = root
        self.events = []
        self.baseline = self._hash(os.path.join(root, "SNAKES.py"))
        self.files = self._scan()

    @staticmethod
    def _hash(path):
        try:
            with open(path, "rb") as f:
                return hashlib.sha256(f.read()).hexdigest()
        except OSError:
            return None

    def _scan(self):
        found = {}
        for dirpath, dirnames, filenames in os.walk(self.root):
            rel_dir = os.path.relpath(dirpath, self.root).replace(os.sep, "/")
            dirnames[:] = [d for d in dirnames if d not in ("__pycache__", ".git")  # .git is the organizer's repo
                           and (d if rel_dir == "." else rel_dir + "/" + d) not in UNWATCHED_DIRS]
            for name in filenames:
                path = os.path.join(dirpath, name)
                rel = os.path.relpath(path, self.root).replace(os.sep, "/")
                try:
                    size = os.path.getsize(path)
                except OSError:
                    continue
                digest = self._hash(path) if (rel == "SNAKES.py" or (name.endswith((".py", ".kt")) and size > 0)) else None
                found[rel] = (size, digest)
        return found

    def _log(self, kind, path, detail=""):
        event = {"time": time.strftime("%Y-%m-%d %H:%M:%S"), "event": kind, "path": path, "detail": detail}
        self.events.append(event)
        logging.warning(f"[INTEGRITY] {kind}: {path} {detail}")

    def check(self):
        for dirpath, dirnames, _ in os.walk(self.root):
            if ".git" in dirnames:
                dirnames.remove(".git")
            rel_dir = os.path.relpath(dirpath, self.root).replace(os.sep, "/")
            dirnames[:] = [d for d in dirnames if (d if rel_dir == "." else rel_dir + "/" + d) not in UNWATCHED_DIRS]
            for d in list(dirnames):
                if d == "__pycache__":
                    cache = os.path.join(dirpath, d)
                    try:
                        shutil.rmtree(cache)
                        self._log("removed_python_cache", os.path.relpath(cache, self.root))
                    except OSError as e:
                        self._log("cache_not_removed", os.path.relpath(cache, self.root), str(e))
                    dirnames.remove(d)
        current = self._scan()
        snakes_now = current.get("SNAKES.py", (0, None))[1]
        if snakes_now != self.baseline:
            self._log("ALERT_SNAKES_PY_MODIFIED", "SNAKES.py", "changed since launch (the running copy is unaffected)")
            self.baseline = snakes_now
        for rel, (size, digest) in current.items():
            if rel in GAME_OUTPUT_FILES or rel == "SNAKES.py":
                continue
            old = self.files.get(rel)
            if old is None:
                if (rel.startswith(TACTICS_FOLDER + "/") and rel.endswith(".py")) or (
                        rel.startswith("kotlin/tactics/") and rel.endswith(".kt")):
                    self._log("new_tactic_file" if size else "new_empty_template", rel, f"{size} bytes")
                else:
                    self._log("ALERT_UNEXPECTED_FILE", rel, f"{size} bytes")
            elif old != (size, digest):
                self._log("file_changed", rel, f"{old[0]} -> {size} bytes")
        for rel in self.files:
            if rel not in current and rel not in GAME_OUTPUT_FILES:
                self._log("file_removed", rel)
        self.files = current


INTEGRITY = None
THERMAL_PAUSE_C = 85.0  # unattended runs pause while any readable sensor is at or above this
SYSTEM_SAMPLES = []


def read_system_status():
    """Best-effort Windows readings (no admin needed): ACPI thermal zones and total CPU load."""
    sample = {"time": time.strftime("%Y-%m-%d %H:%M:%S"), "thermal_zones_c": {}, "cpu_load_pct": None}
    if os.name != "nt":
        return sample
    import subprocess
    cmd = ("$t = Get-CimInstance Win32_PerfFormattedData_Counters_ThermalZoneInformation -ErrorAction SilentlyContinue | "
           "ForEach-Object { $_.Name + '=' + [math]::Round($_.HighPrecisionTemperature / 10 - 273.15, 1) }; "
           "$l = (Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average; "
           "($t -join ';') + '|' + $l")
    try:
        out = subprocess.run(["powershell", "-NoProfile", "-Command", cmd], capture_output=True, text=True,
                             timeout=30, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)).stdout.strip()
        zones, _, load = out.rpartition("|")
        for item in filter(None, zones.split(";")):
            name, _, value = item.partition("=")
            sample["thermal_zones_c"][name.strip()] = float(value.replace(",", "."))
        sample["cpu_load_pct"] = float(load.replace(",", ".")) if load.strip() else None
    except Exception as e:
        sample["error"] = str(e)[:120]
    return sample


def system_check():
    """Logs a sample (shown in telemetry.json) and pauses while the PC is too hot."""
    sample = read_system_status()
    SYSTEM_SAMPLES.append(sample)
    del SYSTEM_SAMPLES[:-300]
    hottest = max(sample["thermal_zones_c"].values(), default=None)
    while hottest is not None and hottest >= THERMAL_PAUSE_C:
        if INTEGRITY:
            INTEGRITY._log("THERMAL_PAUSE", "system", f"{hottest} C >= {THERMAL_PAUSE_C} C, pausing 120 s")
        time.sleep(120)
        sample = read_system_status()
        SYSTEM_SAMPLES.append(sample)
        hottest = max(sample["thermal_zones_c"].values(), default=None)


def lower_process_priority():
    """Keeps the PC responsive during long unattended runs."""
    try:
        if os.name == "nt":
            import ctypes
            kernel32 = ctypes.windll.kernel32
            kernel32.GetCurrentProcess.restype = ctypes.c_void_p
            kernel32.SetPriorityClass.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
            if not kernel32.SetPriorityClass(kernel32.GetCurrentProcess(), 0x00004000):  # BELOW_NORMAL_PRIORITY_CLASS
                raise OSError("SetPriorityClass failed")
        else:
            os.nice(10)
    except Exception as e:
        logging.info(f"Could not lower process priority ({e}).")


def run_unattended(argv):
    global INTEGRITY
    ap = argparse.ArgumentParser(description="Unattended Snake tournament (no window, low CPU).")
    ap.add_argument("--hours", type=float, default=0.0, help="total running time; 0 = run until stopped")
    ap.add_argument("--round-minutes", type=float, default=0.0,
                    help="0 = one continuous game (changed tactics are hot-reloaded); >0 = restart the game every N minutes")
    ap.add_argument("--snakes", type=int, default=0, help="0 = tactics + 2 (min 10)")
    ap.add_argument("--food", type=int, default=0, help="0 = 0.8 x snakes (min 8)")
    ap.add_argument("--fps", type=int, default=FPS, help="turns per second (15 = live pace, 0 = as fast as possible)")
    ap.add_argument("--size", default="3840x2160", help="arena in pixels (3840x2160 = live grid, 1000x700 = small grid)")
    ap.add_argument("--dqn", choices=DQN_MODES, default=DEFAULT_DQN_MODE)
    ap.add_argument("--save-minutes", type=float, default=5.0, help="telemetry refresh and tactic hot-reload period")
    args = ap.parse_args(argv)
    if args.snakes <= 0:
        args.snakes = default_arena(count_tactic_files())[0]
    if args.food <= 0:
        args.food = default_arena(args.snakes - 3)[1]
    w, h = (int(v) for v in args.size.lower().split("x"))
    w, h = w // BLOCK_SIZE * BLOCK_SIZE, h // BLOCK_SIZE * BLOCK_SIZE
    lower_process_priority()
    INTEGRITY = IntegrityMonitor(".")
    system_check()
    end = time.time() + args.hours * 3600 if args.hours > 0 else float("inf")
    round_no = 0
    while time.time() < end - 5:
        round_no += 1
        INTEGRITY.check()
        info = {"number": round_no, "started": time.strftime("%Y-%m-%d %H:%M:%S"), "mode": "unattended",
                "restart_every_min": args.round_minutes or None, "ends": "when stopped" if end == float("inf") else
                time.strftime("%Y-%m-%d %H:%M", time.localtime(end))}
        print(f"[UNATTENDED] game {round_no} starting ({info['started']})", flush=True)
        info["speed"] = "uncapped" if args.fps == 0 else f"{args.fps} turns/s"
        game = Game(max(1, min(50, args.snakes)), max(1, min(50, args.food)), False, fps=FPS, headless=True,
                    dqn_mode=args.dqn, arena_size=(w, h), sync_catalog=False, round_info=info)
        limit = args.round_minutes * 60 if args.round_minutes > 0 else None
        remaining = end - time.time()
        if remaining != float("inf"):
            limit = min(limit or remaining, max(1.0, remaining))

        def on_save(g=game):
            INTEGRITY.check()
            g.hot_reload_tactics()
            system_check()

        game.run(max_seconds=limit, fps_cap=args.fps, save_every=args.save_minutes * 60, on_save=on_save)
        top = sorted(game.snakes, key=lambda s: s.ranking_score(), reverse=True)[:3]
        print("[UNATTENDED] game %d done: %s" % (round_no, ", ".join(f"{s.nombre} {s.ranking_score():.1f}" for s in top)), flush=True)
    INTEGRITY.check()
    print("[UNATTENDED] finished.", flush=True)

def objective(trial, episodes_per_trial):
    lr = trial.suggest_float('lr', 5e-5, 2e-3, log=True)
    hidden_size = trial.suggest_categorical('hidden_size', [64, 128, 256])
    imitation_rate = trial.suggest_float('imitation_rate', 0.05, 0.4)
    gamma = trial.suggest_float('gamma', 0.85, 0.98)
    return run_headless_evaluation(lr, hidden_size, gamma, imitation_rate, episodes=episodes_per_trial)


def optuna_callback(study, trial):
    val = trial.value if trial.value is not None else 0.0
    best = study.best_value if study.best_value is not None else 0.0
    color = Fore.GREEN if val >= best else Fore.YELLOW
    p = trial.params
    print(
        f"[{Fore.BLUE}TRIAL {trial.number:02d}{Style.RESET_ALL}] "
        f"Mean Score: {color}{val:.2f}{Style.RESET_ALL} | Best: {Fore.CYAN}{best:.2f}{Style.RESET_ALL} | "
        f"lr={p['lr']:.5f}, hidden={p['hidden_size']}, gamma={p['gamma']:.3f}, imit={p['imitation_rate']:.2f}"
    )


def print_llm_challenge_prompt():
    prompt_text = f"""
================================================================================
OFFICIAL BLIND TOURNAMENT PROMPT FOR RIVAL LLMs (COPY & PASTE TO ANY AI MODEL)
================================================================================
You are competing in the blind scientific tournament "Snake Battle Royale AI Arena".
You must deliver a SINGLE Python file corresponding to your exact provider and model
name inside `{TACTICS_FOLDER}/` (for example: `Provider-Model_Name.py`).

GAME RULES & ANTI-CHEAT SANDBOX SPECIFICATION:
1. Required Interface:
   Your file must define:
   - `MODEL_NAME = "Provider-Model_Name"`
   - A function `create_agent()` returning an object with method `decide_action(self, perception) -> str`
     (or a top-level function `decide_action(perception) -> str`), returning one of:
     `'UP'`, `'DOWN'`, `'LEFT'`, `'RIGHT'`.
2. No 180-Degree Reversals:
   Your chosen action must be in `perception["valid_actions"]`.
3. Strict Per-Turn CPU Quota:
   Maximum `15 milliseconds` (`0.015s`) per turn. Exceeding this limit triggers a
   timeout penalty and forces a random move.
4. AST Anti-Cheat Static Audit:
   Importing `sys`, `inspect`, `subprocess`, `os`, `pathlib`, `importlib`, `builtins`,
   `pickle`, `socket`, `urllib`, `requests`, or calling `open()`, `eval()`, `exec()`,
   `globals()`, `getattr()`, `setattr()`, or accessing `__dunder__` attributes (except
   `__init__`, `__name__`, `__len__`) will immediately DISQUALIFY your file before execution.
5. Progressive Curriculum Collision Rules (Grid step = 20 px):
   - `< 100 points`: Toroidal wrap-around world (crossing screen edges wraps to the opposite side)
     and collisions are non-lethal.
   - `>= 100 points`: After a 40-second warning phase, colliding with your own body (`my_body[1:]`) KILLS you.
   - `>= 200 points`: The world stops wrapping; colliding with screen boundaries (`x < 0`, `x >= width`,
     `y < 0`, `y >= height`) KILLS you.
   - `>= 300 points`: Colliding with any enemy snake's segments KILLS you.
6. Read-Only `perception` Dictionary Received Every Turn:
   - `my_name`: `str`
   - `my_head`: `(x, y)` tuple of ints
   - `my_body`: tuple of `(x, y)` segments ordered from head to tail
   - `my_direction`: `'UP' | 'DOWN' | 'LEFT' | 'RIGHT'`
   - `my_score`: `float`
   - `width`, `height`, `block_size`: `int` (`block_size = 20`)
   - `foods`: tuple of `(x, y)` positions of all active food items
   - `enemies`: tuple of dicts `{{'id': int, 'head': (x, y), 'body': tuple, 'direction': str, 'score': float}}`
   - `active_rules`: dict with booleans `self_collision`, `self_collision_warning`,
     `wall_collision`, `enemy_collision`, `toroidal_world`
   - `state_vector_16`: 16-float normalized tuple `(norm_dx, norm_dy, food_up, food_down,
     food_left, food_right, danger_up, danger_down, danger_left, danger_right,
     dir_up, dir_down, dir_left, dir_right, rule_100_state, rule_200_state)`
   - `previous_reward`: `float` (`+15.0` ate food, `-15.0` died, `+1.0` moved closer, `-1.2` moved farther)
   - `died_last_step`: `bool`
   - `valid_actions`: tuple of valid non-reversing actions for this turn

You may use spatial algorithms (BFS, A*, Flood-Fill cavity analysis), online Deep Reinforcement
Learning with PyTorch (`torch`, `torch.nn`, `torch.nn.functional`), or a hybrid architecture.
Output ONLY the complete Python code ready to paste into your `.py` file!
================================================================================
"""
    print(Fore.CYAN + prompt_text + Style.RESET_ALL)


if __name__ == "__main__" and "--unattended" in sys.argv:
    run_unattended([a for a in sys.argv[1:] if a != "--unattended"])
    sys.exit(0)

if __name__ == "__main__":
    while True:
        print("\n" + "=" * 72)
        print(f"🐍 {Fore.GREEN}{tr('cli_header')}{Style.RESET_ALL} 🐍")
        print("=" * 72)

        active_tactics, total_catalog_files, empty_slots = load_external_tactics(TACTICS_FOLDER)
        print(tr("cli_sync_status", folder=TACTICS_FOLDER, total=total_catalog_files, active=len(active_tactics), empty=empty_slots))
        for t in active_tactics:
            print(f"  ✓ {Fore.CYAN}{t['name']}{Style.RESET_ALL} ({t['file']})")

        print("\n" + tr("cli_select_mode"))
        print(f"{Fore.CYAN}{tr('cli_mode_1')}{Style.RESET_ALL}")
        print(f"{Fore.MAGENTA}{tr('cli_mode_2')}{Style.RESET_ALL}")
        print(f"{Fore.YELLOW}{tr('cli_mode_3')}{Style.RESET_ALL}")
        print(f"{Fore.GREEN}{tr('cli_mode_4')}{Style.RESET_ALL}")
        print(f"{Fore.BLUE}{tr('cli_mode_5')}{Style.RESET_ALL}")

        mode = input("\n" + tr("cli_input_option")).strip()

        if mode == '5':
            cycle_language()
            continue

        if mode == '4':
            print_llm_challenge_prompt()
            continue

        if mode in {'1', '2', '3'}:
            num_snakes, num_food = default_arena(len(active_tactics))
            try:
                ns_in = input(tr("cli_num_snakes", default=num_snakes)).strip()
                if ns_in:
                    num_snakes = max(1, min(50, int(ns_in)))
                    num_food = default_arena(num_snakes - 3)[1]
                nf_in = input(tr("cli_num_food", default=num_food)).strip()
                if nf_in:
                    num_food = max(1, min(50, int(nf_in)))
            except Exception:
                pass
            dqn_mode = DEFAULT_DQN_MODE
            if mode in {'1', '3'}:
                dqn_mode = "separate" if input(tr("cli_dqn_mode")).strip().lower().startswith('s') else "unified"

            if mode == '1':
                ans = input(tr("cli_play_user")).strip().lower()
                play_user = ans in {'y', 's', 'yes', 'si', 'sim'}
                free = num_snakes - (1 if play_user else 0)
                if free < len(active_tactics):
                    left_out = [t["name"] for t in active_tactics[free:]]
                    print(f"{Fore.YELLOW}Warning: {num_snakes} snakes leave no slot for {len(left_out)} tactic(s): "
                          f"{', '.join(left_out)}. Use at least {len(active_tactics) + 3} snakes.{Style.RESET_ALL}")
                elif free == len(active_tactics):
                    print(f"{Fore.YELLOW}Note: no slot left for a learning DQN snake.{Style.RESET_ALL}")
                game = Game(num_snakes, num_food, play_user, headless=False, dqn_mode=dqn_mode)
                INTEGRITY = IntegrityMonitor(".")
                game.run(on_save=lambda: (INTEGRITY.check(), game.hot_reload_tactics()))
                break

            elif mode == '2':
                try:
                    n_trials = int(input("Number of Optuna trials (default 15): ").strip() or "15")
                    ep_per_trial = int(input("Episodes per trial (default 2): ").strip() or "2")
                except Exception:
                    n_trials, ep_per_trial = 15, 2

                print(f"\n{Fore.MAGENTA}Running Optuna ({n_trials} trials)...{Style.RESET_ALL}")
                optuna.logging.set_verbosity(optuna.logging.WARNING)
                study = optuna.create_study(direction="maximize")
                study.optimize(lambda tr_obj: objective(tr_obj, ep_per_trial), n_trials=n_trials, callbacks=[optuna_callback])

                print(f"\n{Fore.GREEN}✓ Optimization completed. Best Mean Score: {study.best_value:.2f}{Style.RESET_ALL}")
                print("Best Hyperparameters:", study.best_params)
                break

            elif mode == '3':
                try:
                    eps = int(input("Number of episodes for fast headless training (default 5): ").strip() or "5")
                except Exception:
                    eps = 5
                print(f"\n{Fore.YELLOW}Training {num_snakes} snakes for {eps} episodes in headless mode...{Style.RESET_ALL}")
                game = Game(num_snakes, num_food, play_as_user=False, headless=True, dqn_mode=dqn_mode)
                INTEGRITY = IntegrityMonitor(".")
                game.run(max_episodes=eps, on_save=lambda: (INTEGRITY.check(), game.hot_reload_tactics()))
                print(f"{Fore.GREEN}✓ Training finished. Weights saved to 'q_tables.pkl', report to 'game_analysis_report.txt', telemetry to '{TELEMETRY_FILE}'.{Style.RESET_ALL}")
                break
        else:
            break
"""Arena Prompter: ask any AI model for a Kotlin Snake tactic, from a local web page.

    python tools/arena_prompter.py            # opens http://127.0.0.1:8765 in the browser
    python tools/arena_prompter.py --port 9000 --no-browser
    python tools/arena_prompter.py auto --dry-run   # what every free model would write (sends nothing)
    python tools/arena_prompter.py auto             # every free model with a key writes its .kt and .py
    python tools/arena_prompter.py auto --host llmtr --skip-existing   # only llmtr's free models, only its key

Automatic mode: one offer per tactic name among the free models (a key for its host first, then the lab's own host,
a host EmuGaMind reviewed, no daily quota); files with code are asked about before anything is sent; each answer
gets 4 minutes (the arena's rule), the arena's own check on a staged copy (Kotlin: `check`; Python:
`SNAKES.py --check`) and is written only if it passes. Answers and results go to the cache folder, never the arena.
No cost: a real run downloads the catalogs again and only uses models whose input and output price is exactly 0 now
and whose free period (if any) lasts at least another hour; chat models only (no image, voice, translation, safety
or embedding models, no routers). A host that asks for consent (HTTP 403) is skipped for that model.

What it does:
 - Model catalog, as EmuGaMind builds it (models.dev: providers whose API the app can speak, majors pinned to their
   host), completed with every provider's own public GET /models (no key: free) and the rich public lists of llmtr
   (free models and until when, parsed from each description) and OpenRouter (":free" models, expiration_date).
 - Prompt: kotlin/tactics/rules_kotlin.txt + the entry's current code (if any) + the code of the top 3 of
   kotlin/telemetry_kotlin.json + "only your code; copying is allowed, the goal is to win". Editable before sending.
 - Sends it with the user's key (D:\\SC\\AK\\A, or one pasted for this session), shows the answer and the Kotlin block,
   copies it, and names the file for kotlin/tactics.

Keys stay in this process: the page never receives them, a key goes only to the host of the provider it belongs to,
and nothing is sent until the user presses Send. The server listens on 127.0.0.1 only and every API call needs the
per-run token embedded in the page. Standard library only.
"""
import argparse
import concurrent.futures
import datetime as dt
import json
import math
import os
import re
import secrets
import sys
import threading
import time
import urllib.error
import urllib.request
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

HERE = Path(__file__).resolve().parent
ARENA = HERE.parent
TACTICS = ARENA / "kotlin" / "tactics"
RULES = TACTICS / "rules_kotlin.txt"
TELEMETRY = ARENA / "kotlin" / "telemetry_kotlin.json"
PAGE = HERE / "arena_prompter.html"
KEYS_FILE = Path(os.environ.get("ISSASODA_KEYS_FILE", r"D:\SC\AK\A"))
PINNED_FILE = Path(r"C:\Users\24\AndroidStudioProjects\EmuGaMind\app\src\main\assets\ai_pinned_hosts.json")
CACHE = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "arena_prompter"
CACHE_HOURS = 12
UA = {"User-Agent": "Mozilla/5.0 (arena_prompter)", "Accept": "application/json"}

MODELS_DEV = "https://models.dev/api.json"
LLMTR_MODELS = "https://llmtr.com/v1/models"
OPENROUTER_MODELS = "https://openrouter.ai/api/v1/models"

# ---------------------------------------------------------------------------------------------------------------
# Providers: EmuGaMind's SdkFamilies (data/ai/AiEndpoint.kt) and PinnedHosts.MAJORS
# ---------------------------------------------------------------------------------------------------------------
OPENAI, ANTHROPIC = "openai", "anthropic"
FAMILIES = {
    "@ai-sdk/openai-compatible": (OPENAI, None),
    "@openrouter/ai-sdk-provider": (OPENAI, None),
    "@ai-sdk/openai": (OPENAI, "https://api.openai.com/v1"),
    "@ai-sdk/google": (OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai"),
    "@ai-sdk/mistral": (OPENAI, "https://api.mistral.ai/v1"),
    "@ai-sdk/xai": (OPENAI, "https://api.x.ai/v1"),
    "@ai-sdk/groq": (OPENAI, "https://api.groq.com/openai/v1"),
    "@ai-sdk/cerebras": (OPENAI, "https://api.cerebras.ai/v1"),
    "@ai-sdk/cohere": (OPENAI, "https://api.cohere.ai/compatibility/v1"),
    "@ai-sdk/anthropic": (ANTHROPIC, "https://api.anthropic.com/v1"),
}
MAJORS = {
    "anthropic": "api.anthropic.com", "openai": "api.openai.com", "google": "generativelanguage.googleapis.com",
    "xai": "api.x.ai", "mistral": "api.mistral.ai", "groq": "api.groq.com", "deepseek": "api.deepseek.com",
    "cerebras": "api.cerebras.ai", "cohere": "api.cohere.ai", "openrouter": "openrouter.ai",
}
# Labels of D:\SC\AK\A per provider id (a label equal to the provider id also works).
KEY_LABELS = {
    "google": "gemini", "alibaba": "qwen", "alibaba-cn": "qwen", "moonshotai": "kimi", "moonshotai-cn": "kimi",
    "kimi-code-plan-global": "kimi", "kimi-code-plan-cn": "kimi", "zai": "zhipuai", "zhipuai-coding-plan": "zhipuai",
    "minimax-cn": "minimax", "minimax-coding-plan": "minimax", "minimax-cn-coding-plan": "minimax",
}
# What a chat cannot use (Catalog.kt / EmuGaMind ModelCatalog).
EXCLUDED = re.compile(
    r"embed|whisper|tts|audio|speech|moderation|rerank|image|dall-?e|imagen|veo|sora|realtime|transcribe|guard|aqa|"
    r"search|batch|lyria|banana|asr|ocr|video|music|t2v|i2v|stt", re.I)

# ---------------------------------------------------------------------------------------------------------------
# Labs (who made a model) and file names
# ---------------------------------------------------------------------------------------------------------------
LAB_ALIASES = {
    "mistralai": "mistral", "z-ai": "zhipuai", "zai": "zhipuai", "zai-org": "zhipuai", "thudm": "zhipuai",
    "qwen": "alibaba", "x-ai": "xai", "meta-llama": "meta", "moonshot": "moonshotai", "mimo": "xiaomi",
    "xiaomimimo": "xiaomi", "gemma": "google", "bytedance-seed": "bytedance", "rekaai": "reka", "arcee-ai": "arcee",
    "ibm-granite": "ibm", "deepseek-ai": "deepseek", "minimaxai": "minimax", "stepfun-ai": "stepfun",
    "cohereforai": "cohere", "coherelabs": "cohere", "liquidai": "liquid", "sakanaai": "sakana",
    "nousresearch": "nousresearch", "inceptionlabs": "inception", "poolside-ai": "poolside",
}
# Model families -> lab, for hosts that list other labs' models without a "lab/" prefix.
FAMILY_LABS = [
    (r"nemotron", "nvidia"), (r"claude|fable", "anthropic"), (r"kimi", "moonshotai"), (r"\bgpt|^o\d\b|^o\d-|codex", "openai"),
    (r"gemini|gemma", "google"), (r"deepseek", "deepseek"), (r"qwen|qwq|qvq", "alibaba"), (r"glm", "zhipuai"),
    (r"allam", "sdaia"),
    (r"grok", "xai"), (r"mistral|codestral|devstral|magistral|ministral|pixtral|voxtral|leanstral", "mistral"),
    (r"^(meta[ _-]?)?llama|^muse", "meta"), (r"minimax", "minimax"), (r"mimo", "xiaomi"),
    (r"step-?\d", "stepfun"), (r"laguna", "poolside"), (r"mercury", "inception"), (r"solar", "upstage"),
    (r"command|\baya", "cohere"), (r"seed|doubao", "bytedance"), (r"hunyuan", "tencent"), (r"ernie", "baidu"),
    (r"sensenova", "sensetime"), (r"minicpm", "openbmb"), (r"(^|/)(ling|ring)-\d", "inclusionai"),
    (r"longcat", "meituan"),
    (r"jais|k2[- _]?horizon", "ifm"), (r"intern-?s|atria|sage-a|agents-a", "internlm"), (r"lfm", "liquid"),
    (r"phi-?\d", "microsoft"), (r"granite", "ibm"), (r"(^|[/_. ])nova-", "amazon"), (r"sonar", "perplexity"),
    (r"hermes", "nousresearch"), (r"inkling", "thinkingmachines"), (r"fugu|namazu", "sakana"),
]
# The arena catalogs' rule (SNAKES.py MODEL_MAKERS, Catalog.kt MAKERS) for a lab that serves other labs' models.
CATALOG_MAKERS = [(re.compile(rx, re.I), lab) for rx, lab in (
    ("nemotron", "nvidia"), ("claude", "anthropic"), ("kimi", "moonshotai"), (r"\bgpt|^o\d", "openai"),
    ("gemini|gemma", "google"), ("qwen|qwq|qvq", "alibaba"), ("deepseek", "deepseek"), (r"\bglm", "zhipuai"),
    ("grok", "xai"), ("mistral|mixtral|codestral|devstral|magistral|ministral|pixtral|voxtral", "mistral"),
    (r"^(meta[ _-]?)?llama", "meta"), ("minimax", "minimax"), ("mimo", "xiaomi"), (r"\bstep[ _-]?\d", "stepfun"),
    (r"\bseed", "bytedance"), (r"\bphi[ _-]?\d", "microsoft"), (r"\bsolar", "upstage"))]
# One word per lab, as the lab writes its brand: file names (names v2, see arena/engine/Names.kt) start with it.
LAB_BRAND = {
    "openai": "OpenAI", "anthropic": "Anthropic", "google": "Google", "xai": "xAI", "deepseek": "DeepSeek",
    "alibaba": "Alibaba", "moonshotai": "MoonshotAI", "zhipuai": "ZhipuAI", "minimax": "MiniMax", "mistral": "Mistral",
    "meta": "Meta", "nvidia": "Nvidia", "xiaomi": "Xiaomi", "stepfun": "StepFun", "cohere": "Cohere",
    "poolside": "Poolside", "inception": "Inception", "upstage": "Upstage", "bytedance": "ByteDance",
    "tencent": "Tencent", "ifm": "IFM", "sakana": "SakanaAI", "sarvam": "SarvamAI",
    "thinkingmachines": "ThinkingMachines", "liquid": "LiquidAI", "perplexity": "Perplexity", "morph": "Morph",
    "reflection": "Reflection", "internlm": "InternLM", "microsoft": "Microsoft", "ibm": "IBM", "amazon": "Amazon",
    "baidu": "Baidu", "reka": "Reka", "arcee": "Arcee", "nousresearch": "NousResearch", "ai21": "AI21",
    "llmtr": "LLMTR", "sdaia": "SDAIA", "sensetime": "SenseTime", "openbmb": "OpenBMB", "inclusionai": "InclusionAI",
    "meituan": "Meituan",
}
EFFORTS = ["minimal", "low", "medium", "high", "xhigh", "max"]
EFFORT_WORD = {"minimal": "Minimal", "low": "Low", "medium": "Medium", "high": "High", "xhigh": "XHigh", "max": "Max"}
FREE_WORDS = re.compile(r"\s*[\(\[](free|ücretsiz|ucretsiz|gratis)\b[^\)\]]{0,30}[\)\]]\s*", re.I)
TRAILING_FREE = re.compile(r"[\s_-]+free([\s,_-]*\(?\s*unlimited\s*\)?)?\s*$", re.I)


def clean(text):
    """Catalog.kt clean(): what SNAKES.py and the Kotlin arena use for file names."""
    return re.sub(r"_+", "_", re.sub(r"[^A-Za-z0-9._-]+", "_", text.strip())).strip("_")


def brand(lab):
    if lab in LAB_BRAND:
        return LAB_BRAND[lab]
    return "".join(p[:1].upper() + p[1:] for p in re.split(r"[^A-Za-z0-9]+", lab) if p) or "Unknown"


def lab_brand(name):
    """SNAKES.py _lab_brand for a models.dev provider name: "Moonshot AI" -> MoonshotAI, "StepFun (China)" -> StepFun."""
    return re.sub(r"[^A-Za-z0-9]", "", re.sub(r"\(.*?\)", "", name))


def speakable(name):
    """EmuGaMind SnakeWatch.speakable(): what the phone's voice says for a snake."""
    dash = name.find("-")
    provider = dash > 0 and "_" not in name[:dash] and name[dash + 1:dash + 2].isalpha()
    model = name[dash + 1:] if provider else name
    words = [w for w in re.split(r"[_\- ]", model) if w.strip() and w.upper() != "UNKNOWN"]
    return " ".join(words) or name


def base_model_id(mid):
    """'~anthropic/claude-x:free' -> 'claude-x'."""
    mid = mid.lstrip("~").split(":")[0]
    mid = mid.rsplit("/", 1)[-1]
    return re.sub(r"-free$", "", mid, flags=re.I)


def lab_of_prefix(prefix):
    p = prefix.lstrip("~").lower()
    return LAB_ALIASES.get(p, p)


def lab_by_family(text):
    t = text.lower()
    for rx, lab in FAMILY_LABS:
        if re.search(rx, t):
            return lab
    return None


# ---------------------------------------------------------------------------------------------------------------
# Downloads with a disk cache
# ---------------------------------------------------------------------------------------------------------------
def http_json(url, headers=None, body=None, timeout=30, method=None):
    """(status, parsed JSON or None, raw text)."""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    h = dict(UA)
    h.update(headers or {})
    if data is not None:
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=h, method=method or ("POST" if data else "GET"))
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode("utf-8", "replace")
            status = r.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        status = e.code
    try:
        return status, json.loads(raw), raw
    except ValueError:
        return status, None, raw


def cached(name, url, refresh, timeout=40):
    """JSON of url, from the cache when younger than CACHE_HOURS; stale cache when offline. (json, 'net'|'cache')."""
    CACHE.mkdir(parents=True, exist_ok=True)
    f = CACHE / name
    fresh = f.exists() and time.time() - f.stat().st_mtime < CACHE_HOURS * 3600
    if f.exists() and fresh and not refresh:
        return json.loads(f.read_text(encoding="utf-8")), "cache"
    try:
        status, js, _ = http_json(url, timeout=timeout)
        if status == 200 and js is not None:
            tmp = f.with_suffix(".tmp")
            tmp.write_text(json.dumps(js), encoding="utf-8")
            tmp.replace(f)
            return js, "net"
    except (OSError, ValueError):
        pass
    if f.exists():
        return json.loads(f.read_text(encoding="utf-8")), "cache (stale)"
    return None, "unreachable"


# ---------------------------------------------------------------------------------------------------------------
# Free models: llmtr writes "ücretsiz" and the end date in Turkish inside each description
# ---------------------------------------------------------------------------------------------------------------
TR_MONTHS = {"ocak": 1, "şubat": 2, "subat": 2, "mart": 3, "nisan": 4, "mayıs": 5, "mayis": 5, "haziran": 6,
             "temmuz": 7, "ağustos": 8, "agustos": 8, "eylül": 9, "eylul": 9, "ekim": 10, "kasım": 11, "kasim": 11,
             "aralık": 12, "aralik": 12}
TR_DATE = re.compile(r"(\d{1,2}) (" + "|".join(TR_MONTHS) + r") (\d{4})(?: (\d{1,2}):(\d{2}))?[^ ;.,]*"
                     r"(?: \(TS[İI]\))? kadar", re.I)


def llmtr_terms(desc, zero_price):
    """(free, free_until 'YYYY-MM-DD HH:MM' or None, note in Spanish)."""
    d = desc or ""
    low = d.lower()
    says_free = bool(re.search(r"ücretsiz|ucretsiz", low))
    byok = re.search(r"kendi (\w+) anahtar", d, re.I)
    if byok and not says_free:  # "kendi EVREN anahtarınızla": price 0 on llmtr, paid with your own key of that host
        return False, None, f"requiere tu propia clave de {byok.group(1)}"
    free = zero_price or says_free
    until, note = None, ""
    m = TR_DATE.search(d)
    if m:
        day, mon, year = int(m.group(1)), TR_MONTHS[m.group(2).lower()], int(m.group(3))
        hh, mm = (int(m.group(4)), int(m.group(5))) if m.group(4) else (23, 59)
        stamp = f"{year:04d}-{mon:02d}-{day:02d} {hh:02d}:{mm:02d}"
        if free:
            until = stamp
        elif re.search(r"geçin|gecin", low[m.end():m.end() + 60]):
            note = f"se retira {stamp[:10]}"
    if free:
        quota = re.search(r"günde ([\d.,]+ ?(?:milyon|bin)? ?(?:token|istek)?)", low)
        if "kota" in low or quota:
            note = "cuota diaria" + (f" ({quota.group(1).replace('milyon', 'millones').replace('bin', 'mil').replace('istek', 'pedidos')})" if quota else "")
    return free, until, note


def price_per_m(value):
    try:
        return float(value) * 1e6
    except (TypeError, ValueError):
        return None


# ---------------------------------------------------------------------------------------------------------------
# The catalog
# ---------------------------------------------------------------------------------------------------------------
class Catalog:
    def __init__(self):
        self.lock = threading.Lock()
        self.data = None        # what the page gets
        self.providers = {}     # id -> provider dict with models dict (server side)
        self.labs = {}          # lab id -> {"name": models.dev name, "models": {id_lower: name}, "names": {norm: name}}
        self.status = {"state": "empty", "sources": {}}
        self.pinned = self._load_pinned()

    @staticmethod
    def _load_pinned():
        hosts = dict(MAJORS)
        try:
            snap = json.loads(PINNED_FILE.read_text(encoding="utf-8")).get("hosts", {})
            hosts = {**snap, **MAJORS}
        except (OSError, ValueError):
            pass
        return hosts

    def host_check(self, pid, host):
        pinned = self.pinned.get(pid)
        if pinned is None:
            return "unreviewed"
        return "reviewed" if pinned == host else "changed"

    def build(self, refresh=False, progress=None):
        def step(text):
            self.status["state"] = text
            if progress:
                progress(text)

        sources = {}
        step("models.dev")
        md, how = cached("models_dev.json", MODELS_DEV, refresh, timeout=60)
        sources["models.dev"] = how
        if not md:
            self.status = {"state": "error: models.dev not reached and no cache", "sources": sources}
            return
        providers, labs = self._from_models_dev(md)
        step("llmtr / OpenRouter")
        lt, how = cached("llmtr.json", LLMTR_MODELS, refresh)
        sources["llmtr /v1/models"] = how
        if lt and "llmtr" in providers:
            self._merge_llmtr(providers["llmtr"], lt)
        orr, how = cached("openrouter.json", OPENROUTER_MODELS, refresh)
        sources["openrouter /api/v1/models"] = how
        if orr and "openrouter" in providers:
            self._merge_openrouter(providers["openrouter"], orr)
        step("public /models of every provider")
        live = self._public_lists(providers, refresh)
        sources["/models without key"] = f"{live['ok']} providers answered, {live['added']} models added"
        for p in providers.values():
            for m in p["models"].values():
                self._fill_lab(m, p, labs)
        with self.lock:
            self.providers, self.labs = providers, labs
            self.data = self._page_data()
            self.status = {"state": "ready", "sources": sources, "built": dt.datetime.now().strftime("%Y-%m-%d %H:%M")}

    # -- models.dev, the way EmuGaMind's ProviderRegistry reads it --
    def _from_models_dev(self, md):
        reach = {}
        for host, p in md.items():
            for m in (p.get("models") or {}).values():
                cid = m.get("canonical_model_id") or ""
                lab = cid.split("/")[0] if "/" in cid else ""
                if lab and lab != host:
                    reach[lab] = reach.get(lab, 0) + 1
        labs = {}
        for pid, p in md.items():
            if not reach.get(pid):
                continue
            entry = labs.setdefault(pid, {"name": p.get("name") or pid, "models": {}, "names": {}})
            for mid, m in (p.get("models") or {}).items():
                cid = m.get("canonical_model_id") or ""
                if cid and not cid.startswith(pid + "/"):
                    continue
                name = m.get("name") or mid
                entry["models"][mid.lower()] = name
                entry["models"][base_model_id(mid).lower()] = name
                entry["names"][norm(name)] = name
        providers = {}
        for pid, p in md.items():
            fam = FAMILIES.get(p.get("npm"))
            if not fam:
                continue
            base = (p.get("api") or fam[1] or "").rstrip("/")
            if not base.startswith("https://") or "{" in base:
                continue
            host = urlparse(base).hostname or ""
            check = self.host_check(pid, host)
            if check == "changed":
                continue
            prov = {"id": pid, "name": p.get("name") or pid, "protocol": fam[0], "base": base, "host": host,
                    "check": check, "doc": p.get("doc") or "", "env": p.get("env") or [], "isLab": pid in labs,
                    "models": {}, "keyModels": []}
            # coding/token plans list price 0: the model is paid by the subscription, not free
            plan = bool(re.search(r"plan|subscription", pid + " " + (p.get("name") or ""), re.I))
            for mid, m in (p.get("models") or {}).items():
                out = ((m.get("modalities") or {}).get("output")) or ["text"]
                if "text" not in out or EXCLUDED.search(mid) or EXCLUDED.search(m.get("name") or ""):
                    continue
                cost = m.get("cost") or {}
                cin, cout = cost.get("input"), cost.get("output")
                free = cin == 0 and cout == 0 and cost != {} and not plan
                efforts = []
                for r in m.get("reasoning_options") or []:
                    if r.get("type") == "effort" and isinstance(r.get("values"), list):
                        efforts = [v for v in r["values"] if v in EFFORTS or v == "none"]
                prov["models"][mid] = {
                    "id": mid, "name": m.get("name") or mid, "canonical": m.get("canonical_model_id") or "",
                    "free": free, "until": None, "ctx": (m.get("limit") or {}).get("context"),
                    "in": None if plan else cin, "out": None if plan else cout, "note": "incluido en la suscripción" if plan else "",
                    "release": m.get("release_date") or "", "efforts": efforts,
                    "reasoning": bool(m.get("reasoning")), "src": ["models.dev"],
                    "inMods": (m.get("modalities") or {}).get("input"), "outMods": out,
                    "family": m.get("family") or ""}
            providers[pid] = prov
        return providers, labs

    @staticmethod
    def _merge_llmtr(prov, js):
        for m in js.get("data") or []:
            mid = m.get("id") or ""
            arch = m.get("architecture") or {}
            chat = "/v1/chat/completions" in (m.get("supported_endpoints") or []) or \
                   "CHAT_COMPLETIONS" in (m.get("supported_operations") or [])
            if not mid or not chat or "text" not in (arch.get("output_modalities") or ["text"]) or EXCLUDED.search(mid):
                continue
            pr = m.get("pricing") or {}
            zero = bool(pr) and all(_num(v) == 0 for v in pr.values())
            free, until, note = llmtr_terms(m.get("description"), zero)
            efforts = [e for e in ((m.get("reasoning") or {}).get("supported_efforts") or []) if e in EFFORTS]
            cur = prov["models"].get(mid) or {"id": mid, "src": [], "canonical": "", "release": ""}
            cur.update({"name": m.get("name") or mid, "free": free, "until": until, "note": note,
                        "ctx": m.get("context_length"), "in": price_per_m(pr.get("prompt")),
                        "out": price_per_m(pr.get("completion")), "efforts": efforts or cur.get("efforts", []),
                        "reasoning": bool(m.get("reasoning")) or cur.get("reasoning", False),
                        "desc": (m.get("description") or "")[:300],
                        "inMods": arch.get("input_modalities") or cur.get("inMods"),
                        "outMods": arch.get("output_modalities") or cur.get("outMods")})
            if "llmtr" not in cur["src"]:
                cur["src"].append("llmtr")
            prov["models"][mid] = cur

    @staticmethod
    def _merge_openrouter(prov, js):
        for m in js.get("data") or []:
            mid = m.get("id") or ""
            arch = m.get("architecture") or {}
            if not mid or "text" not in (arch.get("output_modalities") or ["text"]) or EXCLUDED.search(mid):
                continue
            pr = m.get("pricing") or {}
            zero = _num(pr.get("prompt")) == 0 and _num(pr.get("completion")) == 0
            cur = prov["models"].get(mid) or {"id": mid, "src": [], "canonical": "", "efforts": [], "note": ""}
            exp = m.get("expiration_date")
            cur.update({"name": m.get("name") or mid, "free": zero or mid.endswith(":free"),
                        "until": (exp + " 23:59") if exp and (zero or mid.endswith(":free")) else None,
                        "ctx": m.get("context_length"), "in": price_per_m(pr.get("prompt")),
                        "out": price_per_m(pr.get("completion")),
                        "canonical": cur.get("canonical") or m.get("canonical_slug") or "",
                        "reasoning": "reasoning" in (m.get("supported_parameters") or []),
                        "inMods": arch.get("input_modalities") or cur.get("inMods"),
                        "outMods": arch.get("output_modalities") or cur.get("outMods"),
                        "release": cur.get("release") or (dt.datetime.fromtimestamp(m["created"]).strftime("%Y-%m-%d")
                                                          if m.get("created") else "")})
            if exp and not cur["until"]:
                cur["note"] = f"se retira {exp}"
            if "openrouter" not in cur["src"]:
                cur["src"].append("openrouter")
            prov["models"][mid] = cur

    def _public_lists(self, providers, refresh):
        """GET {base}/models without any key, for every provider (free; most answer 401, many answer the list)."""
        CACHE.mkdir(parents=True, exist_ok=True)
        f = CACHE / "public_models.json"
        saved = {}
        if f.exists():
            try:
                saved = json.loads(f.read_text(encoding="utf-8"))
            except ValueError:
                saved = {}
        fresh = f.exists() and time.time() - f.stat().st_mtime < CACHE_HOURS * 3600
        todo = [p for p in providers.values() if p["id"] not in ("llmtr", "openrouter")]
        if refresh or not fresh:
            def one(p):
                url = p["base"] + "/models"
                try:
                    status, js, _ = http_json(url, timeout=8)
                except (OSError, ValueError):
                    return p["id"], None
                items = js.get("data") if isinstance(js, dict) else (js if isinstance(js, list) else None)
                if status != 200 or not isinstance(items, list):
                    return p["id"], None
                return p["id"], [x for x in items if isinstance(x, dict) and x.get("id")][:3000]

            got = {}
            with concurrent.futures.ThreadPoolExecutor(max_workers=24) as ex:
                for pid, items in ex.map(one, todo):
                    if items is not None:
                        got[pid] = items
            saved = got
            tmp = f.with_suffix(".tmp")
            tmp.write_text(json.dumps(saved), encoding="utf-8")
            tmp.replace(f)
        added = 0
        for pid, items in saved.items():
            prov = providers.get(pid)
            if not prov:
                continue
            for x in items:
                mid = str(x["id"])
                kind = str(x.get("type") or "")
                arch = x.get("architecture") if isinstance(x.get("architecture"), dict) else {}
                outs = arch.get("output_modalities") or ["text"]
                if EXCLUDED.search(mid) or (kind and kind not in ("language", "chat", "text", "model")) or \
                        "text" not in outs:
                    continue
                if mid in prov["models"]:
                    if "live" not in prov["models"][mid]["src"]:
                        prov["models"][mid]["src"].append("live")
                    continue
                pr = x.get("pricing") if isinstance(x.get("pricing"), dict) else {}
                pin = price_per_m(pr.get("prompt", pr.get("input")))
                pout = price_per_m(pr.get("completion", pr.get("output")))
                prov["models"][mid] = {
                    "id": mid, "name": x.get("name") or mid, "canonical": "", "free": pin == 0 and pout == 0,
                    "until": None, "note": "", "ctx": x.get("context_length") or x.get("context_window"),
                    "in": pin, "out": pout, "release": "", "efforts": [], "reasoning": False, "src": ["live"]}
                added += 1
        return {"ok": len(saved), "added": added}

    @staticmethod
    def _fill_lab(m, prov, labs):
        """Who made the model: models.dev canonical id, the 'lab/' prefix, the provider itself, or the family name."""
        lab = None
        for ref in (m.get("canonical") or "", m["id"]):
            if "/" in ref:
                cand = lab_of_prefix(ref.split("/")[0])
                if cand in labs or cand in LAB_BRAND:
                    lab = cand
                    break
        if not lab and prov["isLab"]:
            maker = next((lb for rx, lb in CATALOG_MAKERS if rx.search(m.get("name") or m["id"])), None)
            # a lab's host also serves other labs' models (Groq: SDAIA's ALLaM; Alibaba: DeepSeek's distills)
            family = lab_by_family(m["id"] + " " + (m.get("name") or ""))
            lab = maker or (family if family in LAB_BRAND else None) or prov["id"]
        if not lab:
            lab = lab_by_family(m["id"] + " " + (m.get("name") or ""))
        if not lab and "/" in m["id"]:
            lab = lab_of_prefix(m["id"].split("/")[0])
        m["lab"] = lab or prov["id"]

    def _page_data(self):
        keys = load_keys()
        out = []
        for p in sorted(self.providers.values(), key=lambda x: x["name"].lower()):
            label = key_label(p, keys)
            out.append({
                "id": p["id"], "name": p["name"], "protocol": p["protocol"], "host": p["host"], "check": p["check"],
                "doc": p["doc"], "isLab": p["isLab"], "keyLabel": label, "hasKey": bool(label and keys.get(label)),
                "models": sorted(p["models"].values(), key=lambda m: (m.get("release") or "", m["id"]), reverse=True)})
        return {"providers": out, "efforts": EFFORTS, "status": self.status}

    # -- names --
    def names_for(self, pid, mid, effort, tag):
        prov = self.providers.get(pid)
        m = prov and prov["models"].get(mid)
        if not m:
            raise KeyError("modelo no encontrado")
        lab = m.get("lab") or pid
        model_name = self._lab_model_name(lab, m)
        lab_entry = self.labs.get(lab)
        lab_word = LAB_BRAND.get(lab) or (lab_brand(lab_entry["name"]) if lab_entry else brand(lab))
        tail = ""
        if effort and effort in EFFORT_WORD:
            tail += "_" + EFFORT_WORD[effort]
        tag = re.sub(r"[^A-Za-z0-9]", "", tag or "").upper()
        if tag:
            tail += "_" + tag
        name = f"{lab_word}-{clean(model_name)}{tail}"
        return {"lab": lab, "labBrand": lab_word, "model": model_name, "name": name, "file": file_state(name),
                "similar": similar_files(model_name, {name}), "speak": speakable(name)}

    def _lab_model_name(self, lab, m):
        """The lab's own name for the model (so names match the placeholders made from models.dev)."""
        entry = self.labs.get(lab)
        raw = FREE_WORDS.sub(" ", m.get("name") or m["id"]).strip()
        raw = re.sub(r"^[^:]{2,40}:\s+", "", raw)  # OpenRouter "Lab: Model"
        raw = TRAILING_FREE.sub("", raw) or raw  # "Kimi K2.5 Free", "Exo Free Unlimited": the price is not the name
        if entry:
            # the model's own id first: models.dev gives variants (Highspeed, FlashX) the base model's canonical id
            for ref in (m["id"], m.get("canonical") or ""):
                if not ref:
                    continue
                for k in (ref.lower(), base_model_id(ref).lower()):
                    if k in entry["models"]:
                        return self._no_price(entry["models"][k])
            hit = entry["names"].get(norm(raw))
            if hit:
                return self._no_price(hit)
        return raw

    @staticmethod
    def _no_price(name):
        """A catalog name without its price words: 'Ox Alpha (Free, Unlimited)' -> 'Ox Alpha'."""
        clean_name = TRAILING_FREE.sub("", FREE_WORDS.sub(" ", name).strip())
        return clean_name or name


def _num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def norm(text):
    return re.sub(r"[^a-z0-9.]", "", FREE_WORDS.sub(" ", text or "").lower())


def file_state(name):
    f = TACTICS / f"{name}.kt"
    if not f.exists():
        return {"exists": False}
    size = f.stat().st_size
    return {"exists": True, "chars": len(f.read_text(encoding="utf-8", errors="replace")) if size else 0}


def similar_files(model_name, skip):
    """Existing tactic files of the same model: another lab or host (Nvidia-Kimi_K2.6), an effort or tag
    (Anthropic-Claude_Opus_5.5_High_IAN) or a tool's name (Cursor_Grok_4.6_Medium). Exact model first."""
    want = tokens(model_name)
    if not want:
        return []
    out = []
    for f in sorted(TACTICS.glob("*.kt")):
        if f.stem in skip:
            continue
        have = tokens(f.stem)
        at = next((i for i in range(len(have) - len(want) + 1) if have[i:i + len(want)] == want), -1)
        if at >= 0:
            rest = len(have) - len(want) - at
            out.append((rest, -f.stat().st_size, {"name": f.stem, "chars": f.stat().st_size}))
    return [x[2] for x in sorted(out, key=lambda x: (x[0], x[1]))][:8]


def tokens(text):
    return [t for t in re.split(r"[_\- ]+", FREE_WORDS.sub(" ", text or "").lower()) if t]


# ---------------------------------------------------------------------------------------------------------------
# Keys (never printed, never sent to the page)
# ---------------------------------------------------------------------------------------------------------------
SESSION_KEYS = {}  # provider id -> key pasted in the page for this run only
ONLY_KEY_LABELS = None  # auto --host: only these labels are kept from the keys file


def load_keys():
    out = {}
    try:
        lines = KEYS_FILE.read_text(encoding="utf-8").splitlines()
    except OSError:
        return out
    for line in lines:
        line = line.strip()
        if not line or line.startswith("#") or ":" not in line:
            continue
        label, values = line.split(":", 1)
        if ONLY_KEY_LABELS is not None and label.strip().lower() not in ONLY_KEY_LABELS:
            continue
        found = out.setdefault(label.strip().lower(), [])
        found += [v.strip() for v in values.split(";") if v.strip() and v.strip() not in found]
    return out


def key_label(p, keys):
    for cand in (KEY_LABELS.get(p["id"]), p["id"], p["id"].split("-")[0]):
        if cand and cand in keys:
            return cand
    return KEY_LABELS.get(p["id"]) or None


def key_for(pid, prov):
    if SESSION_KEYS.get(pid):
        return SESSION_KEYS[pid], "pegada en esta sesión"
    keys = load_keys()
    label = key_label(prov, keys)
    if label and keys.get(label):
        return keys[label][0], f"A:{label}"
    return None, None


# How each host's keys start: a key of another provider is caught before it is sent (the host is who checks the key,
# not the lab that made the model: Kimi served by NVIDIA needs an NVIDIA key).
KEY_PREFIX = {"integrate.api.nvidia.com": "nvapi-", "api.anthropic.com": "sk-ant-", "openrouter.ai": "sk-or-",
              "api.openai.com": "sk-", "api.groq.com": "gsk_", "generativelanguage.googleapis.com": "AIza",
              "api.x.ai": "xai-", "llmtr.com": "llmtr-", "api.cerebras.ai": "csk-"}


def key_problem(prov, key):
    """None if the key may belong to this host, else what is wrong (never the key itself)."""
    want = KEY_PREFIX.get(prov["host"])
    if want and not key.startswith(want):
        return (f"Esa clave no es de {prov['name']}: {prov['host']} usa claves que empiezan con «{want}». "
                f"El modelo puede ser de otro laboratorio, pero la clave es la del host que lo sirve.")
    return None


def redact(text, key):
    return text.replace(key, "***") if key else text


# ---------------------------------------------------------------------------------------------------------------
# Prompt
# ---------------------------------------------------------------------------------------------------------------
# The two arenas: the same names, ranking rows and prompt; their own folder, rules, telemetry and code fence.
LANGS = {
    "kotlin": {"dir": TACTICS, "ext": ".kt", "rules": RULES, "rules_label": "kotlin/tactics/rules_kotlin.txt",
               "telemetry": TELEMETRY, "fence": "kotlin", "arena": "Kotlin Snake arena"},
    "python": {"dir": ARENA / "tacticas", "ext": ".py", "rules": ARENA / "tacticas" / "rules.txt",
               "rules_label": "tacticas/rules.txt", "telemetry": ARENA / "telemetry.json", "fence": "python",
               "arena": "Python Snake arena (SNAKES.py)"},
}


def standings(lang="kotlin"):
    try:
        t = json.loads(LANGS[lang]["telemetry"].read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    return t


def tactic_code(name, lang="kotlin"):
    f = LANGS[lang]["dir"] / f"{name}{LANGS[lang]['ext']}"
    try:
        return f.read_text(encoding="utf-8").strip()
    except OSError:
        return ""


# The rules file is written for every reader (disk agents, IDEs, pull requests). A chat model reads none of that: it
# gets only the sections that shape its code, and from the per-entry recommendations only its own line.
CHAT_SECTIONS = re.compile(r"INTERFACE|API|LIMITS|PHYSICS|PERCEPTION|LESSONS|RESEARCH")
SECTION = re.compile(r"^(\d+[a-z]?)\.\s+([A-Z][A-Z ]+)")


def chat_rules(lang, name):
    """The rules a chat model needs, from the same rules file the arena uses (one source, never two copies)."""
    f = LANGS[lang]["rules"]
    if not f.exists():
        return f"({LANGS[lang]['rules_label']} not found)"
    sections, cur = [], None
    for line in f.read_text(encoding="utf-8").splitlines():
        m = SECTION.match(line)
        if m:
            cur = [m.group(2).strip(), [line]]
            sections.append(cur)
        elif cur:
            cur[1].append(line)
    out = []
    for title, lines in sections:
        if title.startswith("RECOMMENDATIONS"):
            bullets, b = [], None
            for ln in lines[1:]:
                if ln.startswith(" - "):
                    b = [ln]
                    bullets.append(b)
                elif b is not None and ln.startswith("   "):
                    b.append(ln)
            mine = [b for b in bullets if re.search(rf"(?<![\w-]){re.escape(name)}(?![\w-])", "".join(b))]
            mine = mine or [b for b in bullets if b[0].startswith(" - New entries")]
            if mine:
                out.append("RECOMMENDATION FOR YOU (from the telemetry)")
                out += [ln for b in mine for ln in b]
        elif CHAT_SECTIONS.search(title):
            out += [ln for ln in lines if ln.strip()]
    return "\n".join(out)


def build_prompt(name, top_n=1, include_own=True, lang="kotlin"):
    L = LANGS[lang]
    t = standings(lang)
    rules = chat_rules(lang, name)
    rows = [r for r in (t or {}).get("ranking", []) if len(r) >= 7 and r[6] == "llm"]
    llm = (t or {}).get("llm", {})
    total = len(rows)

    def row_line(r):
        return f"rank {r[0]} of {total}, rank score {r[2]}, best {r[3]}, {r[4]} chars"

    ext, fence = L["ext"], L["fence"]
    parts = [f"{L['arena']}. You compete as: {name}. Everything you need is below.", "",
             "=== RULES ===", rules, ""]
    own = tactic_code(name, lang) if include_own else ""
    mine = next((r for r in rows if r[1] == name), None)
    if own:
        head = f"=== YOUR CURRENT CODE: {name}{ext}"
        if mine:
            head += f" ({row_line(mine)})"
            e = llm.get(name) or {}
            if e.get("deaths"):
                head += "; deaths " + ", ".join(f"{k} {v}" for k, v in e["deaths"].items())
            if e.get("death_score_phase"):
                head += "; by score " + ", ".join(f"{k}: {v}" for k, v in sorted(e["death_score_phase"].items()))
        parts += [head + " ===", f"```{fence}", own, "```", ""]
    else:
        parts += ["=== YOUR CURRENT CODE: none yet (this is your first entry) ===", ""]
    leaders = [r for r in rows if r[1] != name and tactic_code(r[1], lang)][:top_n]
    if leaders:
        when = (t or {}).get("updated", "")
        parts.append(f"=== TOP {len(leaders)} NOW (PC arena, {when}) ===")
        for r in leaders:
            parts += [f"#{r[0]} {r[1]} ({row_line(r)})", f"```{fence}", tactic_code(r[1], lang), "```", ""]
    parts += ["=== YOUR TASK ===",
              f"Write your best {name}{ext}. Copying from the leaders is allowed, but a copy only ties them: the goal "
              "is to beat them (a higher best score, fewer deaths, shorter code), not to tie.",
              f"Reply with ONLY the complete {fence.capitalize()} file in one ```{fence} block, with no text before or "
              "after it."]
    return "\n".join(parts), {"leaders": [r[1] for r in leaders], "mine": mine, "hasCode": bool(own),
                              "updated": (t or {}).get("updated")}


# A block ends at its closing fence, or at the end of the answer when the model never closed it.
CODE_BLOCK = {"kotlin": re.compile(r"```[ \t]*(kotlin|kt)?[^\n]*\n(.*?)(?:```|\Z)", re.S | re.I),
              "python": re.compile(r"```[ \t]*(python|py)?[^\n]*\n(.*?)(?:```|\Z)", re.S | re.I)}
CODE_MARK = {"kotlin": "class Tactic", "python": "def create_agent"}


def extract_code(text, lang="kotlin"):
    mark = CODE_MARK[lang]
    blocks = [(tag, body.strip()) for tag, body in CODE_BLOCK[lang].findall(text or "")]
    good = [b for tag, b in blocks if mark in b] or [b for tag, b in blocks if tag]
    if good:
        return max(good, key=len)
    if mark in (text or ""):  # no fences: the whole answer is code
        return text.strip()
    return ""


FORBIDDEN = [
    (r"^\s*package\s", "tiene línea package"),
    (r"^\s*import\s+(?!kotlin\.(math|random|collections)\.)", "import fuera de kotlin.math/random/collections"),
    (r"\b(java|javax|android|kotlinx|dalvik)\.", "usa java./javax./android./kotlinx./dalvik."),
    (r"\b(System|Runtime|Thread|ThreadLocal|Process|ClassLoader|Unsafe)\b", "usa System/Runtime/Thread/…"),
    (r"\b(print|println|readLine)\s*\(", "usa print/readLine"),
    (r"@JvmStatic|@Volatile|\bsynchronized\b|\bexternal\b", "usa @JvmStatic/@Volatile/synchronized/external"),
]


def check_code(code):
    notes = []
    if not code:
        return ["no se encontró un bloque de código Kotlin en la respuesta"]
    if not re.search(r"class\s+Tactic\b", code):
        notes.append("falta class Tactic")
    if not re.search(r"fun\s+decide\s*\(", code):
        notes.append("falta fun decide(p: Perception)")
    for rx, msg in FORBIDDEN:
        if re.search(rx, code, re.M):
            notes.append(msg)
    return notes


def check_code_py(code):
    """Quick look before the real audit (SNAKES.py --check, which also runs the timing battery)."""
    if not code:
        return ["no se encontró un bloque de código Python en la respuesta"]
    try:
        compile(code, "<respuesta>", "exec", dont_inherit=True)
    except SyntaxError as e:
        return [f"error de sintaxis en la línea {e.lineno}: {e.msg}"]
    return [] if re.search(r"def\s+create_agent\s*\(", code) else ["falta def create_agent()"]


# ---------------------------------------------------------------------------------------------------------------
# Sending (only when the user presses Send in the page)
# ---------------------------------------------------------------------------------------------------------------
def send_chat(prov, model, prompt, effort, key, timeout=900):
    base = prov["base"]
    if prov["protocol"] == ANTHROPIC:
        body = {"model": model, "max_tokens": 32000, "messages": [{"role": "user", "content": prompt}]}
        if effort and prov["host"] == "api.anthropic.com":
            body["output_config"] = {"effort": effort}
        headers = {"x-api-key": key, "anthropic-version": "2023-06-01"}
        status, js, raw = http_json(base + "/messages", headers=headers, body=body, timeout=timeout)
        if status != 200 or not isinstance(js, dict):
            return {"ok": False, "status": status, "error": redact(raw[:3000], key)}
        text = "".join(c.get("text", "") for c in js.get("content") or [] if c.get("type") == "text")
        u = js.get("usage") or {}
        return {"ok": True, "text": text, "model": js.get("model"), "finish": js.get("stop_reason"),
                "usage": {"in": u.get("input_tokens"), "out": u.get("output_tokens")}}
    body = {"model": model, "messages": [{"role": "user", "content": prompt}]}
    if effort:
        if prov["id"] in ("openrouter", "llmtr"):
            body["reasoning"] = {"effort": effort}
        else:
            body["reasoning_effort"] = effort
    status, js, raw = http_json(base + "/chat/completions", headers={"Authorization": f"Bearer {key}"}, body=body,
                                timeout=timeout)
    if status != 200 or not isinstance(js, dict) or not js.get("choices"):
        return {"ok": False, "status": status, "error": redact(raw[:3000], key)}
    ch = js["choices"][0]
    msg = ch.get("message") or {}
    content = msg.get("content")
    if isinstance(content, list):
        content = "".join(p.get("text", "") for p in content if isinstance(p, dict))
    thought = msg.get("reasoning_content") or msg.get("reasoning") or ""
    u = js.get("usage") or {}
    return {"ok": True, "text": content or "", "model": js.get("model"), "finish": ch.get("finish_reason"),
            "reasoningChars": len(thought) if isinstance(thought, str) else 0,
            "usage": {"in": u.get("prompt_tokens"), "out": u.get("completion_tokens"),
                      "cost": u.get("cost")}}


def list_with_key(prov, key):
    if prov["protocol"] == ANTHROPIC:
        headers = {"x-api-key": key, "anthropic-version": "2023-06-01"}
    else:
        headers = {"Authorization": f"Bearer {key}"}
    status, js, raw = http_json(prov["base"] + "/models", headers=headers, timeout=30)
    items = js.get("data") if isinstance(js, dict) else None
    if status != 200 or not isinstance(items, list):
        return {"ok": False, "status": status, "error": redact(raw[:1500], key)}
    ids = sorted({str(x.get("id")) for x in items if isinstance(x, dict) and x.get("id")})
    return {"ok": True, "ids": [i for i in ids if not EXCLUDED.search(i)]}


# ---------------------------------------------------------------------------------------------------------------
# Automatic mode: every free model writes its own .kt and .py
# ---------------------------------------------------------------------------------------------------------------
# Who plays (automatic mode only; the page still lists every model). A player is a general chat model that reads
# text and answers text: the catalogs' modalities decide first, then these families, which hosts often list as
# "text" anyway. Each pattern names why the model cannot play.
NOT_PLAYERS = [
    ("imagen o video", r"seedance|seedream|flux|kling|hailuo|\bwan[ -]?2|stable[ -]?diffusion|sdxl|recraft|ideogram|"
                       r"midjourney|dall-?e|imagen|\bveo\b|sora|\bimage\b|t2v|i2v|\bvideo\b|cosmos-(predict|transfer)|"
                       r"cogview|cogvideo|hunyuan-?(image|video)|qwen-?image|"
                       r"\d{3,4}x\d{3,4}|_\d{3,4}_\d{3,4}"),
    ("voz o audio", r"\btts\b|text-to-speech|speech|\basr\b|\bstt\b|whisper|audio|voice|music|lyria|transcri|riva|"
                    r"parakeet|canary|fastpitch|magpie|studiovoice|maxine|audio2face|speaker|deepgram|elevenlabs|"
                    r"scribe|cartesia|playht|kokoro|realtime|[-_ ]live\b"),
    ("traducción", r"translat"),
    ("seguridad o clasificador", r"guard|safety|moderat|shield|jailbreak|topic-control|aegis|\bpii\b|reward|classif"),
    ("embeddings o búsqueda", r"embed|rerank|retriev|\bclip\b|siglip"),
    ("visión, OCR o conducción", r"\bocr\b|pars(e|er)\b|detect|segment|tracking|\bdepth\b|bevformer|sparsedrive|"
                                 r"streampetr|occupancy|paligemma|\bdino|\bsam\b|yolo|owl-?vit|grounding|"
                                 r"usdvalidate|usdsearch"),
    ("ciencia (proteínas, clima)", r"\besm-?\d|esmfold|alphafold|diffdock|molmim|\bevo-?2|genmol|fourcastnet|"
                                   r"weathernext|timesfm"),
    ("enrutador, no un modelo", r"^openrouter/(free|auto)\b|router|\bauto\b"),
]
NOT_PLAYERS = [(why, re.compile(rx, re.I)) for why, rx in NOT_PLAYERS]
# Names that only a chat model has: the proof needed when a host lists a model without any metadata.
CHAT_FAMILY = re.compile(r"instruct|chat|\bit\b|-it\b|coder|code|thinking|reason|\bgpt|claude|gemini|gemma|qwen|qwq|"
                         r"llama|mistral|mixtral|magistral|devstral|ministral|deepseek|kimi|glm|grok|minimax|mimo|"
                         r"nemotron|phi-?\d|granite|command|\baya|hermes|olmo|step-?\d|seed-oss|hunyuan|ernie|"
                         r"internlm|intern-s|yi-|baichuan|falcon|jamba|solar|lfm|exaone|sarvam|mercury|laguna", re.I)
MIN_CONTEXT = 16000  # the prompt (rules + own code + the top 3) is about 8k tokens; the answer needs room too


def player_problem(m):
    """None if the model can play, else why not (for --show-excluded)."""
    text = f"{m['id']} {m.get('name') or ''} {m.get('family') or ''}"
    for why, rx in NOT_PLAYERS:
        if rx.search(text):
            return why
    outs, ins = m.get("outMods"), m.get("inMods")
    if outs and any(o != "text" for o in outs):
        return "no responde solo texto (" + "+".join(outs) + ")"
    if ins and "text" not in ins:
        return "no lee texto"
    # free means a price of exactly 0 for input and output, now (a free period that ends within the hour is over)
    if m.get("in") != 0 or m.get("out") != 0:
        return "precio no confirmado en 0"
    if m.get("until") and m["until"] <= (dt.datetime.now() + dt.timedelta(hours=1)).strftime("%Y-%m-%d %H:%M"):
        return f"la gratuidad termina ya ({m['until']})"
    if m.get("ctx") and m["ctx"] < MIN_CONTEXT:
        return f"contexto corto ({m['ctx']} tokens, mínimo {MIN_CONTEXT})"
    if m.get("src") == ["live"] and not CHAT_FAMILY.search(text):
        return "sin datos y el nombre no es de un modelo de chat"
    return None


# A host that writes the full mixture-of-experts size (-120b-a12b) next to one that leaves it out: the same model.
# Only total + active sizes count; a lone size (qwen3.8 vs qwen3.8-27b) can be another model of the family.
SIZE_TAIL = re.compile(r"^-\d+(\.\d+)?b-?a\d+(\.\d+)?b(-(instruct|it))?$")


# What a host adds to a model's id that does not make it another model: quantization, a mode, a region.
HOST_VARIANT = re.compile(r"[-_](fp8|fp16|bf16|int4|int8|awq|gptq|autoround|mlx|gguf|no-?thinking)\b|@\w+$", re.I)


def same_model_keys(name, mid):
    """Keys that identify one model across hosts: the tactic name and the host's id without lab, ':free', '-free',
    quantization or region."""
    # only ':free' is a price; other tags are part of the model (Ollama style: qwen3.6:27b)
    mid = re.sub(r":(free|beta|exacto|nitro|floor|online)$", "", mid.lstrip("~"), flags=re.I).replace(":", "-")
    return (re.sub(r"[^a-z0-9]", "", HOST_VARIANT.sub("", name).lower()),
            HOST_VARIANT.sub("", base_model_id(mid)).lower())
AUTO_SECONDS = 240  # the arena's 4-minute rule, per answer
RUNS = CACHE / "auto_runs"


def file_of(name, lang):
    return LANGS[lang]["dir"] / f"{name}{LANGS[lang]['ext']}"


def chars_of(name, lang):
    """0 if the file is missing or empty, else its characters."""
    f = file_of(name, lang)
    return len(f.read_text(encoding="utf-8", errors="replace")) if f.exists() and f.stat().st_size else 0


def free_candidates(trust_unreviewed=False, excluded=None, hosts=None):
    """One offer per model: the free offers that can play, grouped when they are the same model on several hosts
    (same tactic name, same host id, or the same id plus a size such as -120b-a12b), the best offer first (a key
    for it, the lab's own host, a reviewed host, no daily quota). [excluded] collects (name, model, host, why)."""
    offers = []
    for p in CATALOG.providers.values():
        if (p["check"] == "unreviewed" and not trust_unreviewed) or (hosts and p["id"] not in hosts):
            continue
        has_key = key_for(p["id"], p)[0] is not None
        for m in p["models"].values():
            if not m.get("free"):
                continue
            try:
                n = CATALOG.names_for(p["id"], m["id"], None, None)
            except KeyError:
                continue
            why = player_problem(m)
            if why:
                if excluded is not None:
                    excluded.append((n["name"], m["id"], p["host"], why))
                continue
            rank = (not has_key, m.get("lab") != p["id"], p["check"] != "reviewed", bool(m.get("note")))
            offers.append({"name": n["name"], "provider": p["id"], "host": p["host"], "model": m["id"],
                           "hasKey": has_key, "until": m.get("until"), "note": m.get("note") or "", "rank": rank,
                           "keys": same_model_keys(n["name"], m["id"])})
    # union of offers that share a key, or whose host ids differ only by a size tail
    parent = list(range(len(offers)))

    def root(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    seen = {}
    for i, o in enumerate(offers):
        for k in o["keys"]:
            if k in seen:
                parent[root(i)] = root(seen[k])
            else:
                seen[k] = i
    bases = sorted({o["keys"][1] for o in offers}, key=len)
    for i, o in enumerate(offers):
        b = o["keys"][1]
        for short in bases:
            if len(short) < len(b) and b.startswith(short) and SIZE_TAIL.match(b[len(short):]):
                j = seen[short]
                # the names must agree too: "qwen3.6" listed as Qwen3.6 27B is not Qwen3.6-35B-A3B
                if o["keys"][0].startswith(offers[j]["keys"][0]):
                    parent[root(i)] = root(j)
    groups = {}
    for i, o in enumerate(offers):
        groups.setdefault(root(i), []).append(o)
    out = []
    for group in groups.values():
        group.sort(key=lambda o: o["rank"])
        best = dict(group[0])
        # the name that already has code, else an existing placeholder, else a clean name (no quantization or
        # region), the most specific one (with its size)
        names = sorted({o["name"] for o in group}, key=lambda n: (-max(chars_of(n, lg) for lg in LANGS),
                                                                   not any(file_of(n, lg).exists() for lg in LANGS),
                                                                   len(HOST_VARIANT.findall(n)), -len(n)))
        best["name"] = names[0]
        best["aliases"] = names[1:]
        best["others"] = len(group) - 1
        out.append(best)
    return sorted(out, key=lambda o: o["name"].lower())


def arena_check(name, lang, code, workdir):
    """(passed, report): the arena's own check on a staged copy, so nothing reaches the arena before it passes."""
    import subprocess
    if lang == "kotlin":
        stage = workdir / "kotlin" / "tactics" / f"{name}.kt"
        stage.parent.mkdir(parents=True, exist_ok=True)
        stage.write_text(code.rstrip() + "\n", encoding="utf-8", newline="\n")
        gradlew = ARENA / "kotlin" / ("gradlew.bat" if os.name == "nt" else "gradlew")
        cmd = [str(gradlew), "-q", "--warning-mode", "all", "run", f"--args=check {stage.as_posix()}"]
        cwd = ARENA / "kotlin"
    else:
        stage = workdir / "tacticas" / f"{name}.py"
        stage.parent.mkdir(parents=True, exist_ok=True)
        stage.write_text(code.rstrip() + "\n", encoding="utf-8", newline="\n")
        engine = workdir / "SNAKES.py"
        if not engine.exists():
            engine.write_bytes((ARENA / "SNAKES.py").read_bytes())
        cmd = [sys.executable, "-I", "-B", str(engine), "--check", str(stage)]
        cwd = workdir
    try:
        r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=600, shell=False)
    except (OSError, subprocess.TimeoutExpired) as e:
        return False, f"no se pudo ejecutar el check: {e}"
    out = (r.stdout + r.stderr).strip()
    line = next((x for x in out.splitlines() if x.startswith(("PASS", "FAIL"))), out[-400:])
    return r.returncode == 0 and line.startswith("PASS"), line


def ask(question):
    try:
        return input(question).strip().lower() in ("s", "si", "sí", "y", "yes")
    except EOFError:
        return False


def brief_http(status, err):
    """One short reason for a failed request."""
    e = (err or "").lower()
    if status == 401:
        return "error", "clave rechazada por el host"
    if status == 402:
        return "error", "el host pide saldo o llegó al límite de gasto (no se cobró)"
    if status == 403 and "consent" in e:
        return "error", "pide tu consentimiento en el panel del host"
    if status == 403:
        return "error", "sin permiso para este modelo"
    if status in (404, 410):
        return "error", "el host ya no tiene este modelo"
    if status == 429:
        return "error", "límite de pedidos: se salta el resto de este host"
    if status is None and "timed out" in e:
        return "error", f"más de {AUTO_SECONDS // 60} min sin respuesta"
    if status is None:
        return "error", "sin conexión con el host"
    if status >= 500:
        return "error", f"error del host ({status})"
    return "error", f"HTTP {status}"


def rate_limit(err):
    """(scope, wait seconds or None, local reset time or '') from a 429 body: 'host' when the account's quota is
    used up (llmtr: free_quota_exhausted, scope user), else 'model' (a short per-model limit)."""
    try:
        e = json.loads(err).get("error") or {}
    except (ValueError, AttributeError):
        e = {}
    d = e.get("details") if isinstance(e.get("details"), dict) else {}
    text = (str(e.get("message") or err)).lower()
    wait = d.get("retryAfterSeconds")
    reset = ""
    if d.get("resetAt"):
        try:
            when = dt.datetime.fromisoformat(str(d["resetAt"]).replace("Z", "+00:00")).astimezone()
            reset = when.strftime("%d-%m %H:%M")
            wait = wait if isinstance(wait, (int, float)) else (when - dt.datetime.now().astimezone()).total_seconds()
        except ValueError:
            pass
    quota = d.get("scope") in ("user", "account", "key") or "quota" in text or "daily" in text or \
        (isinstance(wait, (int, float)) and wait > 120)
    return ("host" if quota else "model"), (wait if isinstance(wait, (int, float)) else None), reset


def brief_check(line):
    """One short reason from the arena check's PASS/FAIL line."""
    msg = re.sub(r"^(PASS|FAIL)\s+\S+?:\s*", "", line or "")
    m = re.search(r"compile error:\s*:?(\d+):\d+:\s*error:\s*(.*)", msg)
    if m:
        return f"no compila (línea {m.group(1)}): {m.group(2).strip()[:60]}"
    m = re.search(r"DISQUALIFIED[^(]*\((.*)\)", msg) or re.search(r"cannot be loaded \((.*)\)", msg)
    if m:
        return ("auditoría: " if "DISQUALIFIED" in msg else "no carga: ") + m.group(1)[:60]
    m = re.search(r"errors=(\d+), invalid=(\d+).*?FAIL\s*(.*)", msg)
    if m:
        return f"falla en la prueba ({m.group(1)} errores, {m.group(2)} inválidas): {m.group(3)[:50]}"
    m = re.search(r"FAIL\s+(.*)$", msg)
    return (m.group(1) if m else msg)[:80] or "no pasó el check"


def kind_of(reason):
    """The group a reason belongs to in the summary."""
    return re.sub(r" ?\(.*|: .*", "", reason)


def auto_main(argv):
    ap = argparse.ArgumentParser(prog="arena_prompter.py auto",
                                 description="Every free model writes its own Kotlin and Python tactic.")
    ap.add_argument("--lang", default="kotlin,python", help="kotlin, python or both (default)")
    ap.add_argument("--only", nargs="*", default=[], help="only names containing these words")
    ap.add_argument("--limit", type=int, default=0, help="at most this many models")
    ap.add_argument("--dry-run", action="store_true", help="show the plan; send nothing, write nothing")
    ap.add_argument("--skip-existing", action="store_true", help="never ask: files with code are left alone")
    ap.add_argument("--trust-unreviewed", action="store_true", help="also use hosts not in EmuGaMind's pinned list")
    ap.add_argument("--refresh", action="store_true", help="download every catalog again")
    ap.add_argument("--show-excluded", action="store_true", help="list the free models that cannot play, and why")
    ap.add_argument("--host", nargs="*", default=[], help="only these providers (e.g. llmtr); only their keys are read")
    args = ap.parse_args(argv)
    langs = [x for x in (s.strip() for s in args.lang.split(",")) if x]
    if not langs or any(x not in LANGS for x in langs):
        sys.exit("--lang: kotlin, python o kotlin,python")
    sys.stdout.reconfigure(line_buffering=True)
    global ONLY_KEY_LABELS
    if args.host:
        ONLY_KEY_LABELS = {c for h in args.host for c in (KEY_LABELS.get(h), h, h.split("-")[0]) if c}
    started = time.time()
    print(f"Arena Prompter, modo automático{' (simulacro: no envía ni escribe)' if args.dry_run else ''} · "
          f"{dt.datetime.now():%Y-%m-%d %H:%M}")
    # Prices and free periods change: a real run always reads the catalogs again; a dry run may use the cache.
    fresh = args.refresh or not args.dry_run
    print("Catálogo: " + ("descargando de nuevo (precios al día)…" if fresh else "desde la caché…"), end=" ")
    CATALOG.build(refresh=fresh)
    print(f"{len(CATALOG.providers)} hosts.")
    unknown = [h for h in args.host if h not in CATALOG.providers]
    if unknown:
        sys.exit(f"--host desconocido: {', '.join(unknown)}")
    excluded = []
    cands = free_candidates(args.trust_unreviewed, excluded, hosts=set(args.host) or None)
    if args.only:
        cands = [c for c in cands if any(w.lower() in c["name"].lower() for w in args.only)]
    no_key = [c for c in cands if not c["hasKey"]]
    cands = [c for c in cands if c["hasKey"]]
    if args.limit:
        cands = cands[:args.limit]
    reasons = {}
    for name, mid, host, why in excluded:
        reasons.setdefault(kind_of(why), []).append((name, mid, host, why))
    scope = f" en {', '.join(args.host)}" if args.host else ""
    print(f"Modelos gratis de chat{scope}: {len(cands) + len(no_key)} · con clave: {len(cands)} · "
          f"no participan: {len(excluded)}" + (" (" + ", ".join(f"{k} {len(v)}" for k, v in
                                                sorted(reasons.items(), key=lambda x: -len(x[1]))) + ")"
                                                if excluded else ""))
    if no_key:
        hosts = sorted({c["provider"] for c in no_key})
        print(f"Sin clave en D:\\SC\\AK\\A ({len(no_key)} modelos): " + ", ".join(hosts[:12]) +
              (f" y {len(hosts) - 12} más" if len(hosts) > 12 else ""))
    if args.show_excluded:
        for kind, items in sorted(reasons.items()):
            print(f"  [{kind}]")
            for name, mid, host, why in sorted(set(items)):
                print(f"    {mid} @ {host}" + (f"  ({why})" if why != kind else ""))
    missing = [LANGS[lg]["telemetry"].name for lg in langs if standings(lg) is None]
    if missing:
        print(f"Aviso: falta {' y '.join(missing)}: los prompts van sin el top 3 (juega una partida antes).")

    # Plan: what each model will write; files that already have code are decided now, before anything is sent.
    plan = []
    for c in cands:
        todo = []
        for lang in langs:
            have = chars_of(c["name"], lang)
            if have and args.skip_existing:
                continue
            if have and not args.dry_run:
                f = file_of(c["name"], lang)
                if not ask(f"¿Reescribir {f.name} ({have} car.) con {c['model']} @ {c['host']}? [s/N] "):
                    continue
            todo.append((lang, have))
        if todo:
            plan.append((c, todo))
    files = [(c, lg, have) for c, todo in plan for lg, have in todo]
    news = sum(1 for c, lg, have in files if not have and not file_of(c["name"], lg).exists())
    rewrites = sum(1 for _, _, have in files if have)
    print(f"Plan: {len(plan)} modelos, {len(files)} archivos ({news} nuevos, {len(files) - news - rewrites} "
          f"plantillas vacías, {rewrites} reescrituras).")
    if args.dry_run:
        width = max((len(c["name"]) for c, _ in plan), default=10)
        for c, todo in plan:
            what = " · ".join(f"{LANGS[lg]['ext'][1:]} " + ("reescribe" if have else ("plantilla" if
                              file_of(c["name"], lg).exists() else "nuevo")) for lg, have in todo)
            until = f"  gratis hasta {c['until'][:10]}" if c["until"] else ""
            print(f"  {c['name']:<{width}}  {what:<22} {c['model']} @ {c['host']}{until}")
        return
    if not plan:
        return

    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    run_dir = RUNS / stamp
    run_dir.mkdir(parents=True, exist_ok=True)
    results, limited = [], set()
    print()
    try:
        run_plan(plan, run_dir, results, limited)
    except KeyboardInterrupt:
        print("\nDetenido con Ctrl+C: lo escrito queda completo; nada quedó a medias.")
    summary = summarize(results, time.time() - started, run_dir)
    (run_dir / "resumen.txt").write_text(summary + "\n", encoding="utf-8")
    print("\n" + summary)


def summarize(results, seconds, run_dir):
    """The end of a run in a few lines: what was written, why the rest was not."""
    by = {}
    for r in results:
        by.setdefault(r["status"], []).append(r)
    plural = {"escrito": "escritos", "rechazado": "rechazados", "error": "errores", "omitido": "omitidos"}
    head = " · ".join(f"{len(by[s])} {plural[s]}" for s in plural if by.get(s))
    lines = [f"Resultado: {head or 'nada'}  ({seconds / 60:.0f} min)"]
    if by.get("escrito"):
        lines.append("Escritos: " + ", ".join(f"{r['name']}{LANGS[r['lang']]['ext']}" for r in by["escrito"]))
    for status, label in (("rechazado", "Rechazados"), ("error", "Errores"), ("omitido", "Omitidos")):
        groups = {}
        for r in by.get(status, []):
            groups.setdefault(kind_of(r["brief"]), []).append(r)
        if groups:
            lines.append(f"{label}: " + " · ".join(f"{k} {len(v)}" for k, v in
                                                    sorted(groups.items(), key=lambda x: -len(x[1]))))
    if any(r["brief"].startswith("pide tu consentimiento") for r in results):
        hosts = sorted({r["provider"] for r in results if r["brief"].startswith("pide tu consentimiento")})
        lines.append("Para que entren: aprueba esos proveedores en el panel de " + ", ".join(hosts) + ".")
    lines.append(f"Detalle y respuestas completas: {run_dir}")
    return "\n".join(lines)


def unhide_in_git(paths):
    """Empty placeholders are kept out of git with .git/info/exclude; a file that now has code must show up."""
    exclude = ARENA / ".git" / "info" / "exclude"
    if not exclude.exists():
        return []
    root = ARENA.resolve()
    rel = {p.resolve().relative_to(root).as_posix() for p in paths if p.resolve().is_relative_to(root)}
    lines = exclude.read_text(encoding="utf-8").splitlines()
    keep = [ln for ln in lines if ln.strip() not in rel]
    if len(keep) != len(lines):
        exclude.write_text("\n".join(keep) + "\n", encoding="utf-8", newline="\n")
    return sorted(rel & {ln.strip() for ln in lines})


def save_results(run_dir, results):
    tmp = run_dir / "results.json.tmp"
    tmp.write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
    tmp.replace(run_dir / "results.json")


def run_plan(plan, run_dir, results, limited):
    total = sum(len(todo) for _, todo in plan)
    width = max(len(c["name"]) + 3 for c, _ in plan)
    n = 0

    skipped = {}  # host -> files left out because its quota ran out (one line at the end, not one per file)

    def add(rec, status, brief, extra="", quiet=False):  # one short line per file; results.json always current
        rec.update(status=status, brief=brief)
        if quiet:
            skipped[rec["provider"]] = skipped.get(rec["provider"], 0) + 1
        else:
            mark = {"escrito": "✓", "rechazado": "✗", "error": "!", "omitido": "–"}[status]
            secs = f"  {rec['seconds']:.0f} s" if rec.get("seconds") else ""
            print(f"[{n:>{len(str(total))}}/{total}] {rec['file']:<{width}} {mark} {brief}{extra}{secs}")
        results.append(rec)
        save_results(run_dir, results)

    for c, todo in plan:
        prov = CATALOG.providers[c["provider"]]
        key, _ = key_for(prov["id"], prov)
        for lang, had in todo:
            n += 1
            target = file_of(c["name"], lang)
            rec = {"name": c["name"], "lang": lang, "file": target.name, "provider": c["provider"],
                   "model": c["model"]}
            if c["provider"] in limited:
                add(rec, "omitido", "cuota del host agotada", quiet=True)
                continue
            if c["model"] in limited:
                add(rec, "omitido", "el modelo no aceptó el pedido anterior (consentimiento o límite)")
                continue
            if key_problem(prov, key):
                limited.add(c["provider"])
                add(rec, "error", f"la clave de {c['provider']} no tiene el formato de {prov['host']}: no se envió")
                continue
            prompt, _ = build_prompt(c["name"], lang=lang)
            for attempt in (1, 2):  # a short per-model limit gets one retry after its wait
                t0 = time.time()
                try:
                    out = send_chat(prov, c["model"], prompt, None, key, timeout=AUTO_SECONDS)
                except (OSError, ValueError) as e:
                    out = {"ok": False, "status": None, "error": redact(f"{type(e).__name__}: {e}", key)}
                rec["seconds"] = round(time.time() - t0, 1)
                if out.get("status") != 429:
                    break
                scope, wait, reset = rate_limit(out.get("error", ""))
                if scope == "host" or attempt == 2:
                    break
                pause = min(max(wait or 30, 5), 120)
                print(f"   (límite breve en {c['model']}: espera {pause:.0f} s y reintenta)")
                time.sleep(pause)
            if not out.get("ok"):
                err = out.get("error", "")
                rec["detail"] = f"HTTP {out.get('status')}: {err[:600]}"
                status, brief = brief_http(out.get("status"), err)
                if out.get("status") == 429:
                    scope, wait, reset = rate_limit(err)
                    if scope == "host":
                        limited.add(c["provider"])
                        limited.add(("reset", c["provider"], reset))
                        brief = f"cuota gratis de {c['provider']} agotada" + (f" hasta el {reset}" if reset else "") + \
                                ": se salta el resto de este host"
                    else:
                        limited.add(c["model"])
                        brief = "límite de pedidos de este modelo"
                if brief.startswith("pide tu consentimiento"):
                    limited.add(c["model"])  # approve the provider in the host's dashboard, then run again
                add(rec, status, brief)
                continue
            rec.update(finish=out.get("finish"), usage=out.get("usage"))
            code = extract_code(out["text"], lang)
            (run_dir / f"{target.name}.answer.txt").write_text(out["text"], encoding="utf-8")
            notes = check_code(code) if lang == "kotlin" else check_code_py(code)
            if notes:
                if not out["text"].strip():
                    brief = "respuesta vacía" + (" (solo razonamiento)" if out.get("reasoningChars") else "")
                elif out.get("finish") == "length":
                    brief = "respuesta cortada por largo"
                elif not code:
                    brief = "sin código en la respuesta"
                else:
                    brief = "código incompleto: " + notes[0]
                rec["detail"] = "; ".join(notes)
                add(rec, "rechazado", brief)
                continue
            good, line = arena_check(c["name"], lang, code, run_dir / "stage")
            rec["detail"] = line
            if not good:
                add(rec, "rechazado", brief_check(line))
                continue
            if chars_of(c["name"], lang) != had:  # someone changed it while the model was answering
                add(rec, "omitido", "el archivo cambió durante la corrida; no se tocó")
                continue
            tmp = target.with_name(target.name + ".tmp")
            tmp.write_text(code.rstrip() + "\n", encoding="utf-8", newline="\n")
            tmp.replace(target)
            unhide_in_git([target])
            rec["chars"] = len(code.rstrip()) + 1
            med = re.search(r"median=([\d.]+) ms", line)
            add(rec, "escrito", f"escrito, {rec['chars']} car." + (f", {med.group(1)} ms por jugada" if med else ""))
    for host, count in skipped.items():
        print(f"   … y {count} archivos más de {host} sin enviar: su cuota gratis está agotada.")


# ---------------------------------------------------------------------------------------------------------------
# Web server
# ---------------------------------------------------------------------------------------------------------------
CATALOG = Catalog()
TOKEN = secrets.token_urlsafe(24)
NAME_OK = re.compile(r"^[A-Za-z0-9._-]{3,120}$")


class Handler(BaseHTTPRequestHandler):
    server_version = "ArenaPrompter"

    def log_message(self, fmt, *args):
        if "/api/" in (args[0] if args else ""):
            sys.stderr.write("  " + (fmt % args) + "\n")

    def _host_ok(self):
        host = (self.headers.get("Host") or "").split(":")[0]
        return host in ("127.0.0.1", "localhost")

    def _send(self, code, payload, ctype="application/json; charset=utf-8"):
        data = payload if isinstance(payload, bytes) else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if not self._host_ok():
            return self._send(403, {"error": "host"})
        path = urlparse(self.path).path
        if path == "/":
            html = PAGE.read_text(encoding="utf-8").replace("__TOKEN__", TOKEN)
            return self._send(200, html.encode("utf-8"), "text/html; charset=utf-8")
        if path == "/favicon.ico":
            return self._send(204, b"", "image/x-icon")
        return self._send(404, {"error": "not found"})

    def do_POST(self):
        if not self._host_ok() or not secrets.compare_digest(self.headers.get("X-Token", ""), TOKEN):
            return self._send(403, {"error": "token"})
        try:
            n = int(self.headers.get("Content-Length") or 0)
            req = json.loads(self.rfile.read(n).decode("utf-8") or "{}") if n else {}
        except ValueError:
            return self._send(400, {"error": "JSON inválido"})
        route = urlparse(self.path).path
        try:
            handler = ROUTES.get(route)
            if not handler:
                return self._send(404, {"error": "ruta"})
            return self._send(200, handler(req))
        except KeyError as e:
            return self._send(400, {"error": str(e).strip("'")})
        except Exception as e:  # noqa: BLE001 - shown in the page, never a key
            return self._send(500, {"error": f"{type(e).__name__}: {e}"})


def r_catalog(req):
    if req.get("refresh") or CATALOG.data is None:
        CATALOG.build(refresh=bool(req.get("refresh")))
    with CATALOG.lock:
        data = CATALOG.data or {"providers": [], "efforts": EFFORTS}
        data = dict(data)
        data["status"] = CATALOG.status
        # key availability can change while the server runs (a key added to A)
        keys = load_keys()
        for p in data["providers"]:
            label = key_label(CATALOG.providers[p["id"]], keys)
            p["keyLabel"], p["hasKey"] = label, bool(label and keys.get(label)) or bool(SESSION_KEYS.get(p["id"]))
    return data


def r_names(req):
    return CATALOG.names_for(req["provider"], req["model"], req.get("effort"), req.get("tag"))


def r_prompt(req):
    name = req.get("name") or ""
    if not NAME_OK.match(name):
        raise KeyError("nombre de archivo inválido")
    text, info = build_prompt(name, int(req.get("top", 1)), bool(req.get("own", True)))
    info["file"] = file_state(name)
    return {"prompt": text, "info": info}


def _provider(req):
    prov = CATALOG.providers.get(req.get("provider") or "")
    if not prov:
        raise KeyError("proveedor desconocido")
    if CATALOG.host_check(prov["id"], prov["host"]) == "changed":
        raise KeyError(f"el host de {prov['name']} cambió respecto al revisado: no se envía la clave")
    return prov


def r_key(req):
    prov = _provider(req)
    key = (req.get("key") or "").strip()
    if key and (re.search(r"[\s,;]", key) or len(key) < 8):
        raise KeyError("pega una sola clave, sin espacios ni comas")
    if key and key_problem(prov, key):
        raise KeyError(key_problem(prov, key))
    if key:
        SESSION_KEYS[prov["id"]] = key
    else:
        SESSION_KEYS.pop(prov["id"], None)
    return {"ok": True, "hasKey": key_for(prov["id"], prov)[0] is not None}


def r_key_models(req):
    prov = _provider(req)
    key, where = key_for(prov["id"], prov)
    if not key:
        raise KeyError(f"no hay clave para {prov['name']}")
    out = list_with_key(prov, key)
    out["keyFrom"] = where
    if out.get("ok"):
        prov["keyModels"] = out["ids"]
        for mid in out["ids"]:
            m = prov["models"].get(mid)
            if m:
                if "key" not in m["src"]:
                    m["src"].append("key")
            else:
                m = {"id": mid, "name": mid, "canonical": "", "free": False, "until": None, "note": "", "ctx": None,
                     "in": None, "out": None, "release": "", "efforts": [], "reasoning": False, "src": ["key"]}
                Catalog._fill_lab(m, prov, CATALOG.labs)
                prov["models"][mid] = m
        with CATALOG.lock:
            CATALOG.data = CATALOG._page_data()
    return out


def r_send(req):
    prov = _provider(req)
    model = req.get("model") or ""
    prompt = req.get("prompt") or ""
    if not model or not prompt.strip():
        raise KeyError("falta el modelo o el prompt")
    if prov["check"] == "unreviewed" and not req.get("trustHost"):
        raise KeyError(f"confirma que confías en {prov['host']} antes de enviar tu clave")
    effort = req.get("effort") or None
    if effort and effort not in EFFORTS + ["none"]:
        raise KeyError("esfuerzo inválido")
    key, where = key_for(prov["id"], prov)
    if not key:
        raise KeyError(f"no hay clave para {prov['name']}: agrégala en D:\\SC\\AK\\A o pégala en la página")
    if key_problem(prov, key):
        raise KeyError(f"No se envió nada: la clave {where} no sirve. " + key_problem(prov, key) +
                       (" Para volver a la de D:\\SC\\AK\\A, deja el campo de clave vacío y pulsa Usar."
                        if where.startswith("pegada") else ""))
    t0 = time.time()
    try:
        out = send_chat(prov, model, prompt, effort, key)
    except (OSError, ValueError) as e:
        out = {"ok": False, "status": None, "error": redact(f"{type(e).__name__}: {e}", key)}
    out["seconds"] = round(time.time() - t0, 1)
    out["keyFrom"] = where
    out["host"] = prov["host"]
    if out.get("status") in (401, 403):
        out["hint"] = (f"{prov['host']} rechazó la clave ({where}): tiene que ser una clave vigente de {prov['name']}, "
                       "el host que sirve el modelo." + (" Para volver a la de D:\\SC\\AK\\A, deja el campo de clave "
                                                         "vacío y pulsa Usar." if where.startswith("pegada") else ""))
    if out.get("ok"):
        out["code"] = extract_code(out["text"])
        out["checks"] = check_code(out["code"])
        out["chars"] = len(out["code"])
    return out


def r_check(req):
    code = req.get("code") or ""
    return {"checks": check_code(code), "chars": len(code)}


def r_save(req):
    name = req.get("name") or ""
    code = req.get("code") or ""
    if not NAME_OK.match(name):
        raise KeyError("nombre de archivo inválido")
    if not code.strip():
        raise KeyError("no hay código para guardar")
    f = TACTICS / f"{name}.kt"
    if f.resolve().parent != TACTICS.resolve():
        raise KeyError("ruta inválida")
    if f.exists() and f.stat().st_size > 0 and not req.get("overwrite"):
        return {"ok": False, "exists": True, "chars": f.stat().st_size}
    tmp = f.with_suffix(".kt.tmp")
    tmp.write_text(code.rstrip() + "\n", encoding="utf-8", newline="\n")
    tmp.replace(f)
    return {"ok": True, "path": str(f)}


ROUTES = {"/api/catalog": r_catalog, "/api/names": r_names, "/api/prompt": r_prompt, "/api/key": r_key,
          "/api/key-models": r_key_models, "/api/send": r_send, "/api/check": r_check, "/api/save": r_save}


def main():
    if sys.argv[1:2] == ["auto"]:
        return auto_main(sys.argv[2:])
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--no-browser", action="store_true")
    ap.add_argument("--refresh", action="store_true", help="download every catalog again")
    args = ap.parse_args()
    sys.stdout.reconfigure(line_buffering=True)
    print("Arena Prompter: building the model catalog…")
    CATALOG.build(refresh=args.refresh, progress=lambda s: print("  ·", s))
    total = sum(len(p["models"]) for p in CATALOG.providers.values())
    print(f"  {len(CATALOG.providers)} providers, {total} models — {CATALOG.status.get('sources')}")
    srv = None
    for port in range(args.port, args.port + 20):
        try:
            srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
            break
        except OSError:
            continue
    if not srv:
        sys.exit("no free port")
    url = f"http://127.0.0.1:{srv.server_address[1]}/"
    print(f"Open {url}  (Ctrl+C to stop)")
    if not args.no_browser:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()

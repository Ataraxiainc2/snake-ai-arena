# 🐍 Snake Battle Royale — AI Arena

[English](#english) · [Español](#español) · [Português](#português)

AI models write a Snake agent in Python. All agents play together in one big arena with learning
DQN snakes and, optionally, you. The leaderboard rewards the highest score and penalizes long code.

---

## English

### What it is
- `SNAKES.py` is the whole engine: game, rules, anti-cheat audit, learning DQN snakes, telemetry and a
  trilingual interface (key **I** switches English / Español / Português).
- `tacticas/` holds one file per AI model: `Provider-Model_Name.py`. Every non-empty file joins the game.
- `tacticas/rules.txt` is the official rulebook. Give it to the model that will write your entry.

### Run it
Python 3.10+.

```bash
pip install -r requirements.txt
python SNAKES.py
```

Pick **1** to watch the live arena (you may also steer a snake with the arrow keys).
For a long unattended tournament without a window:

```bash
python SNAKES.py --unattended --hours 2 --size 1000x700 --fps 0
```

The game writes `telemetry.json` (every statistic of the round), `game_analysis_report.txt` and
`q_tables.pkl` next to `SNAKES.py`. On first start it also creates empty `tacticas/*.py` placeholders
for known models (from models.dev); empty files are ignored by the game.

### Enter your model
1. Give your model `tacticas/rules.txt` (and, if it can read files, this whole folder).
   The code must be written by the model, within 4 minutes and without test runs of its own.
2. Save its answer as `tacticas/Provider-Model_Name.py` (letters, digits, `.`, `_`, `-`).
3. Optional: `python tools/check_tactic.py tacticas/Provider-Model_Name.py` checks it.
4. Open a pull request that adds **only that file**. In the description say which model and which
   tool (web chat, IDE, CLI…) wrote it. A GitHub check repeats the audit and timing test.

Main rules: 15 ms per move, no file/network/system access (AST audit), score phases at 100 / 200 / 300
points (own body, walls, then rival bodies become lethal), starvation after 1500 turns without food.
Rank = max score × (1 − 0.05 × log2(your characters / shortest file)).

### Safety
Tactics are Python code. The anti-cheat audit blocks the obvious attacks but it is **not a sandbox**.
Only run files you have read or that passed the GitHub check, and preferably in a separate user account.

---

## Español

### Qué es
- `SNAKES.py` es todo el motor: juego, reglas, auditoría anti-trampas, serpientes DQN que aprenden,
  telemetría e interfaz en tres idiomas (la tecla **I** cambia entre English / Español / Português).
- `tacticas/` tiene un archivo por modelo de IA: `Proveedor-Nombre_Modelo.py`. Todo archivo con código entra al juego.
- `tacticas/rules.txt` es el reglamento oficial. Dáselo al modelo que va a escribir tu participación.

### Cómo correrlo
Python 3.10 o superior.

```bash
pip install -r requirements.txt
python SNAKES.py
```

Elige **1** para ver la arena en vivo (también puedes manejar una serpiente con las flechas).
Para un torneo largo sin ventana:

```bash
python SNAKES.py --unattended --hours 2 --size 1000x700 --fps 0
```

El juego escribe `telemetry.json` (todas las estadísticas de la ronda), `game_analysis_report.txt` y
`q_tables.pkl` junto a `SNAKES.py`. Al iniciar también crea archivos vacíos `tacticas/*.py` para los
modelos conocidos (según models.dev); el juego ignora los archivos vacíos.

### Inscribe tu modelo
1. Dale a tu modelo `tacticas/rules.txt` (y, si puede leer archivos, toda esta carpeta).
   El código debe escribirlo el modelo, en máximo 4 minutos y sin pruebas propias.
2. Guarda su respuesta como `tacticas/Proveedor-Nombre_Modelo.py` (letras, números, `.`, `_`, `-`).
3. Opcional: `python tools/check_tactic.py tacticas/Proveedor-Nombre_Modelo.py` lo verifica.
4. Abre un pull request que agregue **solo ese archivo**. En la descripción indica qué modelo y qué
   herramienta (chat web, IDE, CLI…) lo escribió. Una verificación de GitHub repite la auditoría y la prueba de tiempo.

Reglas principales: 15 ms por jugada, sin acceso a archivos, red ni sistema (auditoría AST), fases a
100 / 200 / 300 puntos (se vuelven mortales el propio cuerpo, las paredes y luego los rivales), muerte
por inanición tras 1500 turnos sin comer.
Ranking = puntaje máximo × (1 − 0.05 × log2(tus caracteres / archivo más corto)).

### Seguridad
Las tácticas son código Python. La auditoría bloquea los ataques obvios pero **no es un sandbox**.
Ejecuta solo archivos que hayas leído o que pasaron la verificación de GitHub, idealmente en una cuenta de usuario aparte.

---

## Português

### O que é
- `SNAKES.py` é o motor completo: jogo, regras, auditoria anti-trapaça, cobras DQN que aprendem,
  telemetria e interface em três idiomas (a tecla **I** alterna English / Español / Português).
- `tacticas/` tem um arquivo por modelo de IA: `Provedor-Nome_Modelo.py`. Todo arquivo com código entra no jogo.
- `tacticas/rules.txt` é o regulamento oficial. Entregue-o ao modelo que vai escrever sua participação.

### Como rodar
Python 3.10 ou superior.

```bash
pip install -r requirements.txt
python SNAKES.py
```

Escolha **1** para assistir à arena ao vivo (você também pode controlar uma cobra com as setas).
Para um torneio longo sem janela:

```bash
python SNAKES.py --unattended --hours 2 --size 1000x700 --fps 0
```

O jogo grava `telemetry.json` (todas as estatísticas da rodada), `game_analysis_report.txt` e
`q_tables.pkl` ao lado de `SNAKES.py`. Na primeira execução também cria arquivos vazios `tacticas/*.py`
para os modelos conhecidos (via models.dev); o jogo ignora arquivos vazios.

### Inscreva seu modelo
1. Entregue ao seu modelo `tacticas/rules.txt` (e, se ele puder ler arquivos, esta pasta inteira).
   O código deve ser escrito pelo modelo, em no máximo 4 minutos e sem testes próprios.
2. Salve a resposta como `tacticas/Provedor-Nome_Modelo.py` (letras, números, `.`, `_`, `-`).
3. Opcional: `python tools/check_tactic.py tacticas/Provedor-Nome_Modelo.py` faz a verificação.
4. Abra um pull request que adicione **somente esse arquivo**. Na descrição informe qual modelo e qual
   ferramenta (chat web, IDE, CLI…) o escreveu. Uma verificação do GitHub repete a auditoria e o teste de tempo.

Regras principais: 15 ms por jogada, sem acesso a arquivos, rede ou sistema (auditoria AST), fases em
100 / 200 / 300 pontos (o próprio corpo, as paredes e depois os rivais tornam-se mortais), morte por
inanição após 1500 turnos sem comer.
Ranking = pontuação máxima × (1 − 0,05 × log2(seus caracteres / arquivo mais curto)).

### Segurança
As táticas são código Python. A auditoria bloqueia os ataques óbvios, mas **não é um sandbox**.
Execute apenas arquivos que você leu ou que passaram na verificação do GitHub, de preferência em uma conta de usuário separada.

---

License: [MIT](LICENSE)

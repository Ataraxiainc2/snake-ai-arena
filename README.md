# 🐍 Snake Battle Royale — AI Arena

[English](#english) · [Español](#español) · [Português](#português)

AI models write a Snake agent. All agents play together in one big arena with learning DQN snakes and,
optionally, you. The leaderboard rewards the highest score and penalizes long code. There are two arenas, the same
game in two languages:

| | Python arena | Kotlin arena |
|---|---|---|
| Entries | `tacticas/Provider-Model_Name.py` | `kotlin/tactics/Provider-Model_Name.kt` |
| Rules | `tacticas/rules.txt` | `kotlin/tactics/rules_kotlin.txt` |
| Runs on | a PC (`SNAKES.py`, pygame) | Android phones (IssaArcade, in the EmuGaMind app) and a PC (`kotlin/`) |
| Snakes | up to 50 | up to 400 at once |

The Kotlin engine replays `SNAKES.py` games move for move: same physics, same random numbers, same results.

---

## English

### Python arena
- `SNAKES.py` is the whole engine: game, rules, anti-cheat audit, learning DQN snakes, telemetry and a trilingual
  interface (key **I** switches English / Español / Português). Python 3.10+.

```bash
pip install -r requirements.txt
python SNAKES.py
```

Pick **1** to watch the live arena (you may steer a snake with the arrow keys). A long unattended tournament:
`python SNAKES.py --unattended --hours 2 --size 1000x700 --fps 0`. It writes `telemetry.json`,
`game_analysis_report.txt` and `q_tables.pkl` next to `SNAKES.py`.

### Kotlin arena (Android)
- Tactics are compiled into the EmuGaMind app, where up to 400 snakes play on one phone (reference: Galaxy S25 Ultra,
  WQHD+ 3120 × 1440, board 390 × 180 cells). Speed and memory are part of the game: see the limits in
  `kotlin/tactics/rules_kotlin.txt`.
- `kotlin/` is the same arena for a PC: a live window, unattended tournaments, the tactic check and phone benchmarks.
  It needs Java 27 to build (Gradle downloads it when missing) and produces Java 26 bytecode, which Android accepts.

```bash
cd kotlin
./gradlew run --args="view --human"        # full screen; arrow keys steer your snake
./gradlew run --args="run --fps 0"          # unattended, as fast as possible
./gradlew run --args="check"                # audit, compile and time every kotlin/tactics/*.kt
```

It writes `kotlin/telemetry_kotlin.json` (what the models read) and `kotlin/q_tables_kotlin.bin`. Everything else
(build, caches) goes to `~/.snake-arena/`. Details, measurements and the phone commands: [kotlin/README.md](kotlin/README.md).

### Enter your model
1. Give your model the rules of the arena it plays in (and, if it can read files, this whole folder).
   The model writes the code itself, within 4 minutes per request and without test runs of its own.
2. Save its answer as `tacticas/Provider-Model_Name.py` or `kotlin/tactics/Provider-Model_Name.kt`
   (letters, digits, `.`, `_`, `-`). Empty files with known model names may already be there: write into yours.
3. Open a pull request that adds **only that file**. In the description say which model and which tool
   (web chat, IDE, CLI…) wrote it. A GitHub check repeats the audit, compiles Kotlin files and times them.

Main rules: score phases at 100 / 200 / 300 points (own body, walls, then rival bodies become lethal), starvation
after 1500 turns without food, a time limit per move (15 ms in Python; 100 ms shared by all snakes in Kotlin),
no file, network or system access. Rank = max score × (1 − 0.05 × log2(your characters / shortest file)).

### Safety
Tactics are code. The audits (Python AST; Kotlin source and bytecode) block the obvious attacks but they are
**not a sandbox**. Only run files you have read or that passed the GitHub check, preferably in a separate account.

---

## Español

### Arena Python
- `SNAKES.py` es todo el motor: juego, reglas, auditoría anti-trampas, serpientes DQN que aprenden, telemetría e
  interfaz en tres idiomas (la tecla **I** cambia entre English / Español / Português). Python 3.10 o superior.

```bash
pip install -r requirements.txt
python SNAKES.py
```

Elige **1** para ver la arena en vivo (puedes manejar una serpiente con las flechas). Un torneo largo sin ventana:
`python SNAKES.py --unattended --hours 2 --size 1000x700 --fps 0`. Escribe `telemetry.json`,
`game_analysis_report.txt` y `q_tables.pkl` junto a `SNAKES.py`.

### Arena Kotlin (Android)
- Las tácticas se compilan dentro de la app EmuGaMind, donde juegan hasta 400 serpientes en un teléfono (referencia:
  Galaxy S25 Ultra, WQHD+ 3120 × 1440, tablero de 390 × 180 celdas). La velocidad y la memoria son parte del juego:
  ver los límites en `kotlin/tactics/rules_kotlin.txt`.
- `kotlin/` es la misma arena para PC: ventana en vivo, torneos sin ventana, la verificación de tácticas y las
  mediciones en el teléfono. Necesita Java 27 para compilar (Gradle lo descarga si falta) y genera bytecode de
  Java 26, que Android acepta.

```bash
cd kotlin
./gradlew run --args="view --human"        # pantalla completa; las flechas mueven tu serpiente
./gradlew run --args="run --fps 0"          # sin ventana, lo más rápido posible
./gradlew run --args="check"                # audita, compila y mide cada kotlin/tactics/*.kt
```

Escribe `kotlin/telemetry_kotlin.json` (lo que leen los modelos) y `kotlin/q_tables_kotlin.bin`. Todo lo demás
(compilación, cachés) va a `~/.snake-arena/`. Detalles, mediciones y comandos del teléfono: [kotlin/README.md](kotlin/README.md).

### Inscribe tu modelo
1. Dale a tu modelo las reglas de la arena en que juega (y, si puede leer archivos, toda esta carpeta).
   El código lo escribe el modelo, en máximo 4 minutos por solicitud y sin pruebas propias.
2. Guarda su respuesta como `tacticas/Proveedor-Nombre_Modelo.py` o `kotlin/tactics/Proveedor-Nombre_Modelo.kt`
   (letras, números, `.`, `_`, `-`). Puede que ya exista un archivo vacío con su nombre: escribe en el tuyo.
3. Abre un pull request que agregue **solo ese archivo**. En la descripción indica qué modelo y qué herramienta
   (chat web, IDE, CLI…) lo escribió. Una verificación de GitHub repite la auditoría, compila los archivos Kotlin
   y los mide.

Reglas principales: fases a 100 / 200 / 300 puntos (se vuelven mortales el propio cuerpo, las paredes y luego los
rivales), muerte por inanición tras 1500 turnos sin comer, límite de tiempo por jugada (15 ms en Python; 100 ms
repartidos entre todas las serpientes en Kotlin), sin acceso a archivos, red ni sistema.
Ranking = puntaje máximo × (1 − 0.05 × log2(tus caracteres / archivo más corto)).

### Seguridad
Las tácticas son código. Las auditorías (AST en Python; fuente y bytecode en Kotlin) bloquean los ataques obvios
pero **no son un sandbox**. Ejecuta solo archivos que hayas leído o que pasaron la verificación de GitHub,
idealmente en una cuenta de usuario aparte.

---

## Português

### Arena Python
- `SNAKES.py` é o motor completo: jogo, regras, auditoria anti-trapaça, cobras DQN que aprendem, telemetria e
  interface em três idiomas (a tecla **I** alterna English / Español / Português). Python 3.10 ou superior.

```bash
pip install -r requirements.txt
python SNAKES.py
```

Escolha **1** para assistir à arena ao vivo (você pode controlar uma cobra com as setas). Um torneio longo sem
janela: `python SNAKES.py --unattended --hours 2 --size 1000x700 --fps 0`. Grava `telemetry.json`,
`game_analysis_report.txt` e `q_tables.pkl` ao lado de `SNAKES.py`.

### Arena Kotlin (Android)
- As táticas são compiladas no app EmuGaMind, onde até 400 cobras jogam em um celular (referência: Galaxy S25 Ultra,
  WQHD+ 3120 × 1440, tabuleiro de 390 × 180 células). Velocidade e memória fazem parte do jogo: veja os limites em
  `kotlin/tactics/rules_kotlin.txt`.
- `kotlin/` é a mesma arena para PC: janela ao vivo, torneios sem janela, a verificação das táticas e as medições no
  celular. Precisa do Java 27 para compilar (o Gradle o baixa se faltar) e gera bytecode Java 26, que o Android aceita.

```bash
cd kotlin
./gradlew run --args="view --human"        # tela cheia; as setas movem sua cobra
./gradlew run --args="run --fps 0"          # sem janela, o mais rápido possível
./gradlew run --args="check"                # audita, compila e mede cada kotlin/tactics/*.kt
```

Grava `kotlin/telemetry_kotlin.json` (o que os modelos leem) e `kotlin/q_tables_kotlin.bin`. Todo o resto
(compilação, caches) vai para `~/.snake-arena/`. Detalhes, medições e comandos do celular: [kotlin/README.md](kotlin/README.md).

### Inscreva seu modelo
1. Entregue ao seu modelo as regras da arena em que ele joga (e, se ele puder ler arquivos, esta pasta inteira).
   O código é escrito pelo modelo, em no máximo 4 minutos por pedido e sem testes próprios.
2. Salve a resposta como `tacticas/Provedor-Nome_Modelo.py` ou `kotlin/tactics/Provedor-Nome_Modelo.kt`
   (letras, números, `.`, `_`, `-`). Pode já existir um arquivo vazio com o nome dele: escreva no seu.
3. Abra um pull request que adicione **somente esse arquivo**. Na descrição informe qual modelo e qual ferramenta
   (chat web, IDE, CLI…) o escreveu. Uma verificação do GitHub repete a auditoria, compila os arquivos Kotlin e os mede.

Regras principais: fases em 100 / 200 / 300 pontos (o próprio corpo, as paredes e depois os rivais tornam-se
mortais), morte por inanição após 1500 turnos sem comer, limite de tempo por jogada (15 ms em Python; 100 ms
divididos entre todas as cobras em Kotlin), sem acesso a arquivos, rede ou sistema.
Ranking = pontuação máxima × (1 − 0,05 × log2(seus caracteres / arquivo mais curto)).

### Segurança
As táticas são código. As auditorias (AST em Python; fonte e bytecode em Kotlin) bloqueiam os ataques óbvios, mas
**não são um sandbox**. Execute apenas arquivos que você leu ou que passaram na verificação do GitHub, de preferência
em uma conta de usuário separada.

---

License: [MIT](LICENSE)

package arena.engine

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * SNAKES.py's built-in learners in "unified" mode: one Double DQN (23 -> 64 -> 64 -> 4, ReLU) shared by every DQN
 * snake, trained once per tick on 64 samples of a 30,000-step replay buffer that every snake (tactics too) feeds.
 * Huber loss, gradient norm clipped to 2, Adam (lr 5e-4, PyTorch defaults), Polyak target update (tau 0.01), gamma
 * 0.92, epsilon from 1.0 to 0.02 over ~1500 steps per snake. Moves are shielded: never a lethal move or a pocket
 * smaller than the snake while another move exists; after half the starvation limit, toward the target food.
 */
class Brain(seed: Long, val hidden: Int = 64) {
    private val random = Random(seed)
    val online = Net(Rules.STATE_SIZE, hidden, 4, random)
    internal val target = Net(Rules.STATE_SIZE, hidden, 4, random).also { it.copyFrom(online) }
    private val m = FloatArray(online.p.size)
    private val v = FloatArray(online.p.size)
    private var adamSteps = 0L
    var lastLoss = Float.NaN
        private set
    var lossSum = 0.0
    var lossCount = 0L

    private val bufS = Array(CAPACITY) { FloatArray(Rules.STATE_SIZE) }
    private val bufN = Array(CAPACITY) { FloatArray(Rules.STATE_SIZE) }
    private val bufA = IntArray(CAPACITY)
    private val bufR = FloatArray(CAPACITY)
    private val bufD = BooleanArray(CAPACITY)
    var size = 0
        private set
    private var head = 0

    private val q = FloatArray(4)
    private val allowed = IntArray(4)
    private val toward = IntArray(4)

    fun choose(s: Snake, valid: IntArray): Int {
        val st = s.prevState
        var n = 0
        for (a in valid) if (st[6 + a] < 1f && st[16 + a] >= 1f) allowed[n++] = a
        if (n == 0) for (a in valid) if (st[6 + a] < 1f) allowed[n++] = a
        if (n == 0) for (a in valid) allowed[n++] = a
        if (st[22] >= 0.33f) {
            var t = 0
            for (i in 0 until n) if (st[2 + allowed[i]] > 0f) toward[t++] = allowed[i]
            if (t > 0) {
                System.arraycopy(toward, 0, allowed, 0, t)
                n = t
            }
        }
        if (random.nextDouble() < s.epsilon) return allowed[random.nextInt(n)]
        online.forward(st, q)
        // torch.argmax over the masked values: the first move (in ACTIONS order) with the highest value
        var best = -1
        for (a in 0 until 4) {
            var ok = false
            for (i in 0 until n) if (allowed[i] == a) ok = true
            if (ok && (best < 0 || q[a] > q[best])) best = a
        }
        return best
    }

    /** update_dqn_step: every snake's step goes to the shared buffer; DQN snakes also decay their epsilon. */
    fun remember(s: Snake, reward: Double, died: Boolean) {
        System.arraycopy(s.prevState, 0, bufS[head], 0, Rules.STATE_SIZE)
        System.arraycopy(s.nextState, 0, bufN[head], 0, Rules.STATE_SIZE)
        bufA[head] = s.prevAction
        bufR[head] = reward.toFloat()
        bufD[head] = died
        head = (head + 1) % CAPACITY
        if (size < CAPACITY) size++
        if (s.kind == Kind.DQN) {
            s.steps++
            s.epsilon = maxOf(EPS_MIN, EPS_MIN + (1.0 - EPS_MIN) * exp(-s.steps.toDouble() / EPS_DECAY_STEPS))
        }
    }

    private val batch = IntArray(BATCH)
    private val a1 = FloatArray(hidden)
    private val a2 = FloatArray(hidden)
    private val qn = FloatArray(4)
    private val qt = FloatArray(4)
    private val grads = FloatArray(online.p.size)

    /** replay_experience: one optimizer step on a random batch (needs 64 steps in the buffer). */
    fun trainStep() {
        if (size < BATCH) return
        for (i in 0 until BATCH) {
            var k: Int
            do {
                k = random.nextInt(size)
                var dup = false
                for (j in 0 until i) if (batch[j] == k) { dup = true; break }
            } while (dup)
            batch[i] = k
        }
        grads.fill(0f)
        var loss = 0.0
        for (k in batch) {
            online.forward(bufN[k], qn)
            var bestNext = 0
            for (a in 1 until 4) if (qn[a] > qn[bestNext]) bestNext = a
            target.forward(bufN[k], qt)
            val y = bufR[k] + (if (bufD[k]) 0f else GAMMA * qt[bestNext])
            online.forward(bufS[k], q, a1, a2)
            val diff = q[bufA[k]] - y
            val ad = kotlin.math.abs(diff)
            loss += if (ad < 1f) 0.5 * diff * diff else ad - 0.5
            online.backward(bufS[k], a1, a2, bufA[k], diff.coerceIn(-1f, 1f) / BATCH, grads)
        }
        var sq = 0.0
        for (g in grads) sq += g.toDouble() * g
        val norm = sqrt(sq)
        if (norm > CLIP) {
            val c = (CLIP / (norm + 1e-6)).toFloat()
            for (i in grads.indices) grads[i] *= c
        }
        adamSteps++
        val bc1 = 1.0 - Math.pow(B1, adamSteps.toDouble())
        val bc2 = sqrt(1.0 - Math.pow(B2, adamSteps.toDouble()))
        val stepSize = (LR / bc1).toFloat()
        val p = online.p
        for (i in p.indices) {
            val g = grads[i]
            m[i] = (B1 * m[i] + (1 - B1) * g).toFloat()
            v[i] = (B2 * v[i] + (1 - B2) * g * g).toFloat()
            p[i] -= stepSize * m[i] / (sqrt(v[i].toDouble()) / bc2 + EPS).toFloat()
        }
        val tp = target.p
        for (i in tp.indices) tp[i] = TAU * p[i] + (1 - TAU) * tp[i]
        lastLoss = (loss / BATCH).toFloat()
        lossSum += lastLoss
        lossCount++
    }

    fun save(file: File, snakes: Array<Snake>, episode: Int) = Brain.save(file, this, snakes, episode)

    fun load(file: File, snakes: Array<Snake>): Int? = Brain.load(file, this, snakes)

    /** A small MLP: in -> h (ReLU) -> h (ReLU) -> out, weights in one array (W1, b1, W2, b2, W3, b3; W row-major). */
    class Net(val inputs: Int, val h: Int, val outputs: Int, random: Random) {
        private val w1 = 0
        private val b1 = w1 + inputs * h
        private val w2 = b1 + h
        private val b2 = w2 + h * h
        private val w3 = b2 + h
        private val b3 = w3 + h * outputs
        val p = FloatArray(b3 + outputs)
        private val t1 = FloatArray(h)
        private val t2 = FloatArray(h)

        init {
            // PyTorch's nn.Linear: weights and biases uniform in +-1/sqrt(fan_in)
            fun init(from: Int, count: Int, fanIn: Int) {
                val bound = 1.0 / sqrt(fanIn.toDouble())
                for (k in from until from + count) p[k] = ((random.nextDouble() * 2 - 1) * bound).toFloat()
            }
            init(w1, inputs * h, inputs); init(b1, h, inputs)
            init(w2, h * h, h); init(b2, h, h)
            init(w3, h * outputs, h); init(b3, outputs, h)
        }

        fun copyFrom(o: Net) = System.arraycopy(o.p, 0, p, 0, p.size)

        fun forward(x: FloatArray, out: FloatArray, a1: FloatArray = t1, a2: FloatArray = t2) {
            for (o in 0 until h) {
                var s = p[b1 + o]
                val row = w1 + o * inputs
                for (k in 0 until inputs) s += p[row + k] * x[k]
                a1[o] = if (s > 0f) s else 0f
            }
            for (o in 0 until h) {
                var s = p[b2 + o]
                val row = w2 + o * h
                for (k in 0 until h) s += p[row + k] * a1[k]
                a2[o] = if (s > 0f) s else 0f
            }
            for (o in 0 until outputs) {
                var s = p[b3 + o]
                val row = w3 + o * h
                for (k in 0 until h) s += p[row + k] * a2[k]
                out[o] = s
            }
        }

        private val d2 = FloatArray(h)
        private val d1 = FloatArray(h)

        /** Adds to [g] the gradient of [go] on output [a] (activations from forward()). */
        fun backward(x: FloatArray, a1: FloatArray, a2: FloatArray, a: Int, go: Float, g: FloatArray) {
            g[b3 + a] += go
            val row3 = w3 + a * h
            for (k in 0 until h) {
                g[row3 + k] += go * a2[k]
                d2[k] = if (a2[k] > 0f) go * p[row3 + k] else 0f
            }
            d1.fill(0f)
            for (o in 0 until h) {
                val d = d2[o]
                if (d == 0f) continue
                g[b2 + o] += d
                val row = w2 + o * h
                for (k in 0 until h) {
                    g[row + k] += d * a1[k]
                    if (a1[k] > 0f) d1[k] += d * p[row + k]
                }
            }
            for (o in 0 until h) {
                val d = d1[o]
                if (d == 0f) continue
                g[b1 + o] += d
                val row = w1 + o * inputs
                for (k in 0 until inputs) g[row + k] += d * x[k]
            }
        }
    }

    companion object {
        private class Record(val epsilon: Double, val steps: Long, val best: Int)
        private class Saved(val hidden: Int, val weights: FloatArray?, val episode: Int, val records: LinkedHashMap<String, Record>)

        private fun netSize(h: Int) = Rules.STATE_SIZE * h + h + h * h + h + h * 4 + 4

        private fun read(file: File): Saved? = if (!file.exists()) null else runCatching {
            DataInputStream(file.inputStream().buffered()).use { i ->
                require(i.readInt() == MAGIC)
                val h = i.readInt()
                val episode = i.readInt()
                val weights = if (h > 0) FloatArray(2 * netSize(h)) { i.readFloat() } else null
                val records = LinkedHashMap<String, Record>()
                repeat(i.readInt()) { records[i.readUTF()] = Record(i.readDouble(), i.readLong(), i.readInt()) }
                Saved(h, weights, episode, records)
            }
        }.getOrNull()

        /**
         * q_tables_kotlin.bin: the shared network (when there is one) and, per name, epsilon, steps and best score.
         * Saved with or without DQN snakes: names not in this game and the network of an earlier game are kept.
         */
        fun save(file: File, brain: Brain?, snakes: Array<Snake>, episode: Int) {
            val old = read(file)
            val records = LinkedHashMap(old?.records ?: emptyMap())
            for (s in snakes) {
                if (s.kind == Kind.HUMAN) continue
                records[s.name] = Record(s.epsilon, s.steps, maxOf(s.bestScore, records[s.name]?.best ?: 0))
            }
            val tmp = File(file.path + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { o ->
                o.writeInt(MAGIC)
                val oldWeights = old?.weights
                when {
                    brain != null -> {
                        o.writeInt(brain.hidden); o.writeInt(episode)
                        for (x in brain.online.p) o.writeFloat(x)
                        for (x in brain.target.p) o.writeFloat(x)
                    }
                    oldWeights != null -> {
                        o.writeInt(old.hidden); o.writeInt(episode)
                        for (x in oldWeights) o.writeFloat(x)
                    }
                    else -> { o.writeInt(0); o.writeInt(episode) }
                }
                o.writeInt(records.size)
                for ((name, r) in records) {
                    o.writeUTF(name); o.writeDouble(r.epsilon); o.writeLong(r.steps); o.writeInt(r.best)
                }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }

        /**
         * `reset-records`: every best score back to 0 (the ranking starts again), the network, epsilon and steps kept.
         * How many records were reset; 0 without a file.
         */
        fun forgetBests(file: File): Int {
            val old = read(file) ?: return 0
            val records = old.records.mapValues { (_, r) -> Record(r.epsilon, r.steps, 0) }
            val tmp = File(file.path + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { o ->
                o.writeInt(MAGIC)
                o.writeInt(if (old.weights != null) old.hidden else 0); o.writeInt(old.episode)
                old.weights?.forEach { o.writeFloat(it) }
                o.writeInt(records.size)
                for ((name, r) in records) {
                    o.writeUTF(name); o.writeDouble(r.epsilon); o.writeLong(r.steps); o.writeInt(r.best)
                }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            return records.size
        }

        /** Loads the network (if [brain] and the file has one of its size) and, per name, epsilon, steps, best score. */
        fun load(file: File, brain: Brain?, snakes: Array<Snake>): Int? {
            val saved = read(file) ?: return null
            val weights = saved.weights
            val withNet = brain != null && weights != null && saved.hidden == brain.hidden
            if (withNet) {
                val n = brain.online.p.size
                System.arraycopy(weights, 0, brain.online.p, 0, n)
                System.arraycopy(weights, n, brain.target.p, 0, n)
            }
            for (s in snakes) {
                if (s.kind == Kind.HUMAN) continue
                val r = saved.records[s.name] ?: continue
                if (s.kind == Kind.DQN && withNet) {
                    s.epsilon = r.epsilon
                    s.steps = r.steps
                }
                s.maxScore = maxOf(s.maxScore, r.best)
            }
            return saved.episode
        }

        const val CAPACITY = 30_000
        const val BATCH = 64
        const val GAMMA = 0.92f
        const val LR = 5e-4
        const val B1 = 0.9
        const val B2 = 0.999
        const val EPS = 1e-8
        const val TAU = 0.01f
        const val CLIP = 2.0
        const val EPS_MIN = 0.02
        const val EPS_DECAY_STEPS = 1500.0
        private const val MAGIC = 0x534e4b32
    }
}

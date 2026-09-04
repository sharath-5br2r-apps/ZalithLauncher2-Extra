/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

data class BenchmarkResult(
    val avgFps: Int,
    val minFps: Int,
    val p99Fps: Int,
    val stabilityPct: Int,
    val score: Int,
    val frameCount: Int
)

class BenchmarkGLRenderer(
    private val durationMs: Long = 15_000L,
    val onProgress: (secondsLeft: Int) -> Unit = {},
    val onComplete: (BenchmarkResult) -> Unit = {}
) : GLSurfaceView.Renderer {

    private val frameTimes = mutableListOf<Long>()
    private var lastNanos = 0L
    private var startNanos = 0L
    private var programId = 0
    private var vboId = 0
    private var rotation = 0f
    private var done = false

    private val VERT = """
        attribute vec2 aPos;
        uniform float uRot;
        uniform vec2 uOff;
        void main() {
            float r = uRot * 3.14159 / 180.0;
            float c = cos(r); float s = sin(r);
            vec2 p = vec2(aPos.x*c - aPos.y*s, aPos.x*s + aPos.y*c);
            gl_Position = vec4(p * 0.035 + uOff, 0.0, 1.0);
        }
    """.trimIndent()

    private val FRAG = """
        precision mediump float;
        uniform float uRot;
        void main() {
            float t = mod(uRot, 360.0) / 360.0;
            gl_FragColor = vec4(t, 0.6 - t*0.4, 1.0 - t, 0.9);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.08f, 0.08f, 0.12f, 1f)
        programId = compileProgram()
        vboId = makeQuadVBO()
        startNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (done) return
        val now = System.nanoTime()
        if (lastNanos > 0) frameTimes.add(now - lastNanos)
        lastNanos = now

        val elapsedMs = (now - startNanos) / 1_000_000L
        val remainingSec = ((durationMs - elapsedMs) / 1000L).toInt().coerceAtLeast(0)
        onProgress(remainingSec)

        if (elapsedMs >= durationMs && frameTimes.size > 20) {
            done = true
            onComplete(computeResult())
            return
        }

        rotation += 2f
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(programId)

        val aPos = GLES20.glGetAttribLocation(programId, "aPos")
        val uRot = GLES20.glGetUniformLocation(programId, "uRot")
        val uOff = GLES20.glGetUniformLocation(programId, "uOff")

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 8, 0)

        repeat(500) { i ->
            val rot = (rotation + i * 0.72f) % 360f
            val col = i % 25 - 12
            val row = i / 25 - 10
            GLES20.glUniform1f(uRot, rot)
            GLES20.glUniform2f(uOff, col * 0.082f, row * 0.1f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun computeResult(): BenchmarkResult {
        val ms = frameTimes.map { it / 1_000_000.0 }.sorted()
        val avgMs  = ms.average()
        val p99Ms  = ms[(ms.size * 0.99).toInt()]
        val maxMs  = ms.max()

        val avgFps = (1000.0 / avgMs).roundToInt()
        val minFps = (1000.0 / maxMs).roundToInt()
        val p99Fps = (1000.0 / p99Ms).roundToInt()
        val stab   = ((1000.0 / maxMs) / (1000.0 / avgMs) * 100).roundToInt().coerceIn(0, 100)
        val score  = (avgFps * stab / 100.0).roundToInt()

        return BenchmarkResult(avgFps, minFps, p99Fps, stab, score, ms.size)
    }

    private fun compileProgram(): Int {
        fun shader(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            return id
        }
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, shader(GLES20.GL_VERTEX_SHADER, VERT))
        GLES20.glAttachShader(prog, shader(GLES20.GL_FRAGMENT_SHADER, FRAG))
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun makeQuadVBO(): Int {
        val data = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)
        val buf  = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data).position(0)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, buf, GLES20.GL_STATIC_DRAW)
        return ids[0]
    }
}

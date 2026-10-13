package com.wifichat.mesh.ui.mesh

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.wifichat.mesh.R
import com.wifichat.mesh.network.PeerLink
import com.wifichat.mesh.util.Ids
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight network map. It draws exactly what the networking layer reports:
 * the local device at the centre, one node per known mesh peer arranged in a
 * ring, and a line for each live link. It NEVER invents nodes or connections —
 * if there are no peers, only the centre node is drawn.
 *
 * A custom Canvas view is used (rather than a graph library) to keep the APK
 * small and redraw cheap on a low-end device.
 */
class MeshGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
    }

    private var selfLabel = "You"
    private var links: List<PeerLink> = emptyList()
    private var knownPeerIds: List<String> = emptyList()

    private val colorAccent = ContextCompat.getColor(context, R.color.accent_primary)
    private val colorSecondary = ContextCompat.getColor(context, R.color.accent_secondary)
    private val colorLink = ContextCompat.getColor(context, R.color.divider)
    private val colorText = ContextCompat.getColor(context, R.color.text_secondary)

    fun setData(selfLabel: String, links: List<PeerLink>, knownPeerIds: Set<String>) {
        this.selfLabel = selfLabel
        this.links = links
        this.knownPeerIds = knownPeerIds.toList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(28f)
        val nodeR = dp(14f)

        // Positions: self at centre, each known peer on a ring.
        val positions = HashMap<String, Pair<Float, Float>>()
        val count = knownPeerIds.size
        knownPeerIds.forEachIndexed { i, peerId ->
            val angle = (2.0 * Math.PI * i / count) - Math.PI / 2
            positions[peerId] = (cx + (radius * cos(angle))).toFloat() to
                (cy + (radius * sin(angle))).toFloat()
        }

        // Links: only for peers we are directly connected to.
        linkPaint.color = colorLink
        for (link in links) {
            val peerId = link.peerId ?: continue
            val pos = positions[peerId] ?: continue
            canvas.drawLine(cx, cy, pos.first, pos.second, linkPaint)
        }

        // Peer nodes.
        nodePaint.color = colorSecondary
        textPaint.color = colorText
        for ((peerId, pos) in positions) {
            canvas.drawCircle(pos.first, pos.second, nodeR, nodePaint)
            canvas.drawText(Ids.shortTag(peerId), pos.first, pos.second + nodeR + dp(12f), textPaint)
        }

        // Self node.
        nodePaint.color = colorAccent
        ringPaint.color = colorAccent
        canvas.drawCircle(cx, cy, nodeR, nodePaint)
        canvas.drawCircle(cx, cy, nodeR + dp(6f), ringPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(selfLabel.take(1).uppercase(), cx, cy + dp(4f), textPaint)

        if (positions.isEmpty()) {
            textPaint.color = colorText
            canvas.drawText(
                context.getString(R.string.mesh_no_peers),
                cx, cy + radius + dp(20f), textPaint
            )
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}

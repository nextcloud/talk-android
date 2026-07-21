/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Patterns
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.events.UserMentionClickEvent
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.message.MessageUtils
import io.noties.markwon.core.spans.LinkSpan
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

private const val TEXT_SIZE_SP = 16f
private const val LINE_HEIGHT_MULTIPLIER = 1.4f
private const val AVATAR_SIZE_DP = 20f
private const val AVATAR_GAP_DP = 4f
private const val CHIP_START_PADDING_DP = 2f
private const val CHIP_END_PADDING_DP = 5f
private const val CHIP_VERTICAL_PADDING_DP = 2f
private const val CHIP_CORNER_RADIUS_DP = 16f
private const val TASK_CHECKBOX_TOUCH_TARGET_DP = 48f
private const val INT_3 = 3

// GFM table separator row: starts with | followed by optional spaces/colons and at least 3 dashes
private val tableSeparatorRegex = Regex("""^\|[ :]*-{3,}""", RegexOption.MULTILINE)

val validLinkRegex = Regex(
    """(?<!\w)https?://(?:www\.)?[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)+(?:/[^\s)]*)?""",
    RegexOption.IGNORE_CASE
)

private val markdownTaskLineRegex = Regex("""^(\s*(?:[-*+]\s+|\d+[.)]\s+)\[)([ xX])(]\s+.*)$""")

@Suppress("LongMethod", "LongParameterList")
@Composable
fun MarkdownText(
    message: ChatMessageUi,
    textColor: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    textSizeSp: Float = TEXT_SIZE_SP,
    highlightSearchTerm: String? = null
) {
    val context = LocalContext.current
    val textColorArgb = textColor.toArgb()
    val themedColors = LocalViewThemeUtils.current.getColorScheme(context)
    val linkColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    val chipBgColor = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    val chipTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val selfChipBgColor = themedColors.primary.toArgb()
    val selfChipTextColor = themedColors.onPrimary.toArgb()
    val density = LocalDensity.current
    val chipStartPaddingPx = with(density) { CHIP_START_PADDING_DP.dp.toPx() }
    val chipEndPaddingPx = with(density) { CHIP_END_PADDING_DP.dp.toPx() }
    val chipVerticalPaddingPx = with(density) { CHIP_VERTICAL_PADDING_DP.dp.toPx() }
    val chipCornerRadiusPx = with(density) { CHIP_CORNER_RADIUS_DP.dp.toPx() }
    val avatarSizePx = with(density) { AVATAR_SIZE_DP.dp.toPx() }
    val avatarGapPx = with(density) { AVATAR_GAP_DP.dp.toPx() }
    val messageId = message.id
    val onMessageLongClick = LocalMessageLongClickHandler.current
    val onMarkdownTaskToggle = LocalMarkdownTaskToggleHandler.current
    val onLongClickState = rememberUpdatedState(onMessageLongClick)
    val hasMarkdownTasks = remember(message.plainMessage) {
        message.plainMessage.lineSequence().any { markdownTaskLineRegex.matches(it) }
    }
    val hasTable = remember(message.plainMessage) {
        message.plainMessage.contains(tableSeparatorRegex)
    }

    if (LocalInspectionMode.current) {
        Text(
            text = message.plainMessage,
            color = textColor,
            modifier = modifier,
            maxLines = maxLines,
            fontSize = textSizeSp.sp
        )
    } else {
        AndroidView(
            modifier = if (hasTable) modifier.fillMaxWidth() else modifier,
            factory = { ctx ->
                val gestureDetector = GestureDetector(
                    ctx,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onLongPress(e: MotionEvent) {
                            onLongClickState.value(messageId)
                        }
                    }
                )
                val longPressListener = View.OnTouchListener { view, event ->
                    val textView = view as? LongPressTextView ?: return@OnTouchListener false
                    val currentMessage = textView.currentMessage ?: return@OnTouchListener false
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val taskLine = findMarkdownTaskLineAtTouch(
                                textView = textView,
                                event = event,
                                message = currentMessage,
                                requireCheckboxHit = true
                            )
                            textView.pendingMarkdownTaskLine = taskLine
                            if (taskLine != null) {
                                return@OnTouchListener true
                            }
                        }

                        MotionEvent.ACTION_UP -> {
                            val pendingTaskLine = textView.pendingMarkdownTaskLine
                            textView.pendingMarkdownTaskLine = null
                            if (pendingTaskLine != null) {
                                handleMarkdownTaskToggle(
                                    message = currentMessage,
                                    clickedSourceLine = pendingTaskLine,
                                    onMarkdownTaskToggle = textView.onMarkdownTaskToggle
                                )
                                return@OnTouchListener true
                            }
                            view.performClick()
                        }

                        MotionEvent.ACTION_CANCEL -> textView.pendingMarkdownTaskLine = null
                    }
                    gestureDetector.onTouchEvent(event)
                    false
                }
                LongPressTextView(ctx).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    tag = longPressListener
                }
            },
            update = { textView ->
                textView.currentMessage = message
                textView.onMarkdownTaskToggle = onMarkdownTaskToggle
                textView.setTextColor(textColorArgb)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                textView.maxLines = maxLines
                textView.ellipsize = if (maxLines != Int.MAX_VALUE) TextUtils.TruncateAt.END else null
                textView.setLineSpacing(0f, textView.textSize * LINE_HEIGHT_MULTIPLIER / textView.paint.fontSpacing)
                val markwon = MessageUtils.buildMarkwon(context, textColorArgb)
                markwon.setMarkdown(textView, resolveNonMentionParams(message))
                val ssb = SpannableStringBuilder(textView.text)
                val hasClickableChips = applyMentionChips(
                    spannable = ssb,
                    message = message,
                    context = context,
                    textView = textView,
                    chipBgColor = chipBgColor,
                    chipTextColor = chipTextColor,
                    selfChipBgColor = selfChipBgColor,
                    selfChipTextColor = selfChipTextColor,
                    textSizePx = textView.textSize,
                    startPaddingPx = chipStartPaddingPx,
                    endPaddingPx = chipEndPaddingPx,
                    verticalPaddingPx = chipVerticalPaddingPx,
                    cornerRadiusPx = chipCornerRadiusPx,
                    avatarSizePx = avatarSizePx,
                    avatarGapPx = avatarGapPx
                )

                val skipExistingLinks = Linkify.MatchFilter { s, start, end ->
                    s !is Spanned || s.getSpans(start, end, URLSpan::class.java).isEmpty()
                }
                val hasUrlLinks = Linkify.addLinks(ssb, validLinkRegex.toPattern(), null, null) { _, url ->
                    val schemeEnd = url.indexOf("://")
                    if (schemeEnd > 0) url.substring(0, schemeEnd).lowercase() + url.substring(schemeEnd) else url
                }
                val hasPhoneLinks = Linkify.addLinks(
                    ssb,
                    Patterns.PHONE,
                    "tel:",
                    null,
                    skipExistingLinks,
                    null
                )
                val hasEmailLinks = Linkify.addLinks(
                    ssb,
                    Patterns.EMAIL_ADDRESS,
                    "mailto:",
                    null,
                    skipExistingLinks,
                    null
                )
                val hasMarkdownLinks = ssb.getSpans(0, ssb.length, LinkSpan::class.java).isNotEmpty()
                val hasLinks = hasUrlLinks || hasPhoneLinks || hasEmailLinks || hasMarkdownLinks

                resolveFileParams(ssb, message)
                applySearchHighlight(ssb, highlightSearchTerm, searchHighlightColorArgb)
                markwon.setParsedMarkdown(textView, ssb)
                textView.setLinkTextColor(linkColorArgb)
                val needsMovementMethod = (hasClickableChips || hasLinks || hasMarkdownTasks) &&
                    maxLines == Int.MAX_VALUE
                if (needsMovementMethod) {
                    textView.movementMethod = LinkMovementMethod.getInstance()
                    textView.setOnTouchListener(textView.tag as? View.OnTouchListener)
                } else {
                    textView.movementMethod = null
                    textView.setOnTouchListener(null)
                }
            }
        )
    }
}

private fun applySearchHighlight(spannable: SpannableStringBuilder, searchTerm: String?, highlightColor: Int) {
    if (searchTerm.isNullOrBlank()) {
        return
    }
    val term = searchTerm.lowercase()
    if (term.isEmpty()) {
        return
    }

    val text = spannable.toString()
    val lowerText = text.lowercase()
    var startIndex = 0
    var matchIndex = lowerText.indexOf(term, startIndex)
    while (matchIndex != -1) {
        val matchEnd = matchIndex + term.length
        spannable.setSpan(
            BackgroundColorSpan(highlightColor),
            matchIndex,
            matchEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        startIndex = matchEnd
        matchIndex = lowerText.indexOf(term, startIndex)
    }
}

@Suppress("ReturnCount")
private fun findMarkdownTaskLineAtTouch(
    textView: TextView,
    event: MotionEvent,
    message: ChatMessageUi,
    requireCheckboxHit: Boolean
): Int? {
    if (!message.renderMarkdown || message.isDeleted || message.plainMessage.isBlank()) {
        return null
    }

    val layout = textView.layout ?: return null
    val verticalPosition = (event.y - textView.totalPaddingTop + textView.scrollY).roundToInt()
    val clickedRenderedLine = layout.getLineForVertical(verticalPosition)
    if (requireCheckboxHit && !isInsideTaskCheckboxTouchTarget(textView, event, layout, clickedRenderedLine)) {
        return null
    }

    val renderedText = textView.text?.toString().orEmpty()
    val clickedSourceLine = renderedText
        .take(layout.getLineStart(clickedRenderedLine).coerceAtMost(renderedText.length))
        .count { it == '\n' }

    return clickedSourceLine.takeIf { sourceLineIndex ->
        message.plainMessage
            .lineSequence()
            .elementAtOrNull(sourceLineIndex)
            ?.let { markdownTaskLineRegex.matchEntire(it) } != null
    }
}

private fun isInsideTaskCheckboxTouchTarget(
    textView: TextView,
    event: MotionEvent,
    layout: android.text.Layout,
    clickedRenderedLine: Int
): Boolean {
    val density = textView.resources.displayMetrics.density
    val horizontalPosition = event.x - textView.totalPaddingLeft + textView.scrollX
    val lineStart = layout.getLineStart(clickedRenderedLine)
    val textStart = layout.getPrimaryHorizontal(lineStart)
    val touchTargetStart = (textStart - TASK_CHECKBOX_TOUCH_TARGET_DP * density).coerceAtLeast(0f)
    val touchTargetEnd = textStart + TASK_CHECKBOX_TOUCH_TARGET_DP * density / 2
    return horizontalPosition in touchTargetStart..touchTargetEnd
}

private fun handleMarkdownTaskToggle(
    message: ChatMessageUi,
    clickedSourceLine: Int,
    onMarkdownTaskToggle: (Int, String) -> Unit
) {
    val sourceLines = message.plainMessage.split('\n')
    val sourceLine = sourceLines.getOrNull(clickedSourceLine) ?: return
    val match = markdownTaskLineRegex.matchEntire(sourceLine) ?: return
    val replacement = if (match.groupValues[2].equals("x", ignoreCase = true)) " " else "x"
    val updatedLine = match.groupValues[1] + replacement + match.groupValues[INT_3]
    val updatedMessage = sourceLines.toMutableList().also { it[clickedSourceLine] = updatedLine }.joinToString("\n")

    onMarkdownTaskToggle(message.id, updatedMessage)
}

private fun resolveNonMentionParams(message: ChatMessageUi): String {
    var result = message.plainMessage
    for ((key, params) in message.messageParameters) {
        if (params["type"] in mentionParameterTypes) continue
        if (params["type"] == "file") continue
        val token = "{$key}"
        if (result.contains(token)) {
            result = result.replace(token, params["name"].orEmpty())
        }
    }
    return result
}

private fun resolveFileParams(spannable: SpannableStringBuilder, message: ChatMessageUi) {
    for ((key, params) in message.messageParameters) {
        if (params["type"] != "file") continue
        val token = "{$key}"
        val name = params["name"].orEmpty()
        var searchFrom = 0
        while (true) {
            val start = spannable.indexOf(token, searchFrom)
            if (start < 0) break
            spannable.replace(start, start + token.length, name)
            searchFrom = start + name.length
        }
    }
}

@Suppress("LongParameterList", "CyclomaticComplexMethod")
private fun applyMentionChips(
    spannable: SpannableStringBuilder,
    message: ChatMessageUi,
    context: Context,
    textView: TextView,
    chipBgColor: Int,
    chipTextColor: Int,
    selfChipBgColor: Int,
    selfChipTextColor: Int,
    textSizePx: Float,
    startPaddingPx: Float,
    endPaddingPx: Float,
    verticalPaddingPx: Float,
    cornerRadiusPx: Float,
    avatarSizePx: Float,
    avatarGapPx: Float
): Boolean {
    val text = spannable.toString()
    var hasClickableChips = false
    for ((key, params) in message.messageParameters) {
        val type = params["type"] ?: continue
        if (type !in mentionParameterTypes) continue
        val name = params["name"].orEmpty()
        val rawId = params["id"].orEmpty()
        val server = params["server"]
        val isFederated = !server.isNullOrEmpty()
        val isSelfMention = rawId == message.activeUserId
        val isClickable = type == "user" && !isSelfMention && !isFederated
        val mentionId = if (isFederated) "$rawId@$server" else rawId
        val bgColor = if (isSelfMention) selfChipBgColor else chipBgColor
        val fgColor = if (isSelfMention) selfChipTextColor else chipTextColor
        val avatarUrl = resolveMentionAvatarUrl(
            rawId = rawId,
            name = name,
            type = type,
            mentionId = mentionId,
            isFederated = isFederated,
            activeUserBaseUrl = message.activeUserBaseUrl,
            roomToken = message.roomToken
        )
        val fallbackIconRes = resolveMentionFallbackIcon(
            MentionChipModel(
                id = mentionId,
                rawId = rawId,
                name = name,
                type = type,
                isFederated = isFederated,
                isSelfMention = isSelfMention,
                isClickableUserMention = isClickable,
                avatarUrl = avatarUrl
            )
        )
        val fallbackDrawable = ContextCompat.getDrawable(context, fallbackIconRes)?.mutate() ?: continue
        val token = "{$key}"
        var searchFrom = 0
        while (true) {
            val start = text.indexOf(token, searchFrom)
            if (start < 0) break
            val end = start + token.length
            val chipSpan = MentionChipSpan(
                label = name,
                fallbackDrawable = fallbackDrawable,
                avatarUrl = avatarUrl,
                backgroundColor = bgColor,
                labelColor = fgColor,
                textSizePx = textSizePx,
                startPaddingPx = startPaddingPx,
                endPaddingPx = endPaddingPx,
                verticalPaddingPx = verticalPaddingPx,
                cornerRadiusPx = cornerRadiusPx,
                avatarSizePx = avatarSizePx,
                avatarGapPx = avatarGapPx
            )
            spannable.setSpan(chipSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            chipSpan.startLoading(context, textView)
            if (isClickable) {
                spannable.setSpan(
                    MentionClickSpan(mentionId),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                hasClickableChips = true
            }
            searchFrom = end
        }
    }
    return hasClickableChips
}

private class MentionClickSpan(private val mentionId: String) : ClickableSpan() {
    override fun onClick(widget: View) {
        EventBus.getDefault().post(UserMentionClickEvent(mentionId))
    }

    override fun updateDrawState(ds: TextPaint) {
        // MentionChipSpan handles all visual styling; suppress default link styling
    }
}

@Suppress("LongParameterList")
private class MentionChipSpan(
    private val label: String,
    private val fallbackDrawable: Drawable,
    private val avatarUrl: String?,
    private val backgroundColor: Int,
    private val labelColor: Int,
    private val textSizePx: Float,
    private val startPaddingPx: Float,
    private val endPaddingPx: Float,
    private val verticalPaddingPx: Float,
    private val cornerRadiusPx: Float,
    private val avatarSizePx: Float,
    private val avatarGapPx: Float
) : ReplacementSpan() {

    private var avatarDrawable: Drawable? = null
    private var targetView: WeakReference<View>? = null

    fun startLoading(context: Context, view: View) {
        targetView = WeakReference(view)
        if (avatarUrl == null) return
        val request = ImageRequest.Builder(context)
            .data(avatarUrl)
            .size(avatarSizePx.roundToInt())
            .transformations(CircleCropTransformation())
            .target { drawable ->
                avatarDrawable = drawable
                targetView?.get()?.invalidate()
            }
            .build()
        context.imageLoader.enqueue(request)
    }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val p = Paint(paint).apply { textSize = textSizePx }
        if (fm != null) {
            val metrics = p.fontMetricsInt
            // The chip is anchored so its centered label lands on the text baseline.
            // chipCenterY = baseline + (ascent + descent) / 2
            // chipTop = chipCenterY - chipHeight / 2  →  offset from baseline = midOffset - halfChip
            // chipBottom = chipCenterY + chipHeight / 2  →  offset from baseline = midOffset + halfChip
            val halfChip = (avatarSizePx + 2 * verticalPaddingPx) / 2f
            val midOffset = (metrics.ascent + metrics.descent) / 2f
            fm.ascent = minOf(metrics.ascent, (midOffset - halfChip).toInt())
            fm.descent = maxOf(metrics.descent, (midOffset + halfChip).roundToInt())
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        val textWidth = p.measureText(label)
        return (startPaddingPx + avatarSizePx + avatarGapPx + textWidth + endPaddingPx).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val textPaint = Paint(paint).apply {
            color = labelColor
            textSize = textSizePx
            isAntiAlias = true
        }
        val width = getSize(paint, text, start, end, null).toFloat()
        val chipHeight = avatarSizePx + 2 * verticalPaddingPx
        // Anchor chip so its centered label text sits exactly on the surrounding baseline (y).
        val chipCenterY = y + (textPaint.ascent() + textPaint.descent()) / 2f
        val chipTop = chipCenterY - chipHeight / 2f
        val chipBottom = chipTop + chipHeight

        val backgroundPaint = Paint().apply {
            color = backgroundColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(x, chipTop, x + width, chipBottom), cornerRadiusPx, cornerRadiusPx, backgroundPaint)

        val drawable = avatarDrawable ?: fallbackDrawable
        val avatarLeft = x + startPaddingPx
        val avatarTop = chipTop + verticalPaddingPx
        drawable.setBounds(
            avatarLeft.roundToInt(),
            avatarTop.roundToInt(),
            (avatarLeft + avatarSizePx).roundToInt(),
            (avatarTop + avatarSizePx).roundToInt()
        )
        drawable.draw(canvas)

        // textBaseline = chipCenterY - (ascent + descent) / 2 = y (by construction above)
        canvas.drawText(label, avatarLeft + avatarSizePx + avatarGapPx, y.toFloat(), textPaint)
    }
}

private class LongPressTextView(context: Context) : AppCompatTextView(context) {
    var currentMessage: ChatMessageUi? = null
    var onMarkdownTaskToggle: (Int, String) -> Unit = { _, _ -> }
    var pendingMarkdownTaskLine: Int? = null
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

private fun markdownTextPreviewMessage(): ChatMessageUi =
    createMarkdownMessage().copy(
        plainMessage = "Hello {user1}! Here is the list:\n- Item 1\n- Item 2\n\n> A blockquote",
        messageParameters = mapOf("user1" to mapOf("type" to "user", "name" to "alice", "id" to "alice"))
    )

@ChatMessagePreviews
@Composable
private fun MarkdownTextPreview() {
    PreviewContainer {
        MarkdownText(
            message = markdownTextPreviewMessage(),
            textColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

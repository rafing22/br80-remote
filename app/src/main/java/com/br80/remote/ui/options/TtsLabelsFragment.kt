package com.br80.remote.ui.options

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.br80.remote.ActionType
import com.br80.remote.Br80Button
import com.br80.remote.GestureType
import com.br80.remote.R
import com.br80.remote.ui.showCustomTtsLabelDialog

/** Schermata "Gestisci Testi TTS", raggruppata per tasto fisico (badge UP/CALL/CAMERA...),
 * tag di gesto in monospace (1x/2x/3x/LONG) e chip "personalizzato" solo quando serve.
 * Prima era un AlertDialog lanciato da un pulsante in Opzioni, ora è la schermata stessa. */
class TtsLabelsFragment : OptionsDetailFragment(R.layout.fragment_option_tts_labels, "Testi Annuncio Vocale") {

    private lateinit var container: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.llTtsLabelsContainer)
        populateTtsList()
    }

    private fun populateTtsList() {
        val ctx = requireContext()
        val entries = mutableListOf<Pair<Br80Button, GestureType>>()
        for (button in Br80Button.values()) {
            for (gesture in GestureType.values()) {
                val action = mappingStorage.getAction(button, gesture)
                if (action.type != ActionType.NONE) {
                    entries.add(button to gesture)
                }
            }
        }

        container.removeAllViews()

        if (entries.isEmpty()) {
            val empty = TextView(ctx).apply {
                text = "Nessuna azione mappata su cui personalizzare il TTS."
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_muted))
                setPadding(0, 24, 0, 0)
            }
            container.addView(empty)
            return
        }

        val grouped = entries.groupBy({ it.first }, { it.second })

        for ((button, gestures) in grouped) {
            val groupHead = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 20, 8, 8)
            }

            val badge = TextView(ctx).apply {
                text = button.name
                textSize = 11f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_card))
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_tts_button_badge)
                setPadding(14, 4, 14, 4)
            }

            val name = TextView(ctx).apply {
                text = "  ${button.displayName}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_ink))
            }

            groupHead.addView(badge)
            groupHead.addView(name)
            container.addView(groupHead)

            for (gesture in gestures) {
                val action = mappingStorage.getAction(button, gesture)
                val custom = mappingStorage.getCustomTtsLabel(button, gesture)
                val ttsText = custom ?: action.getReadableDescription()

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12, 14, 10, 14)
                    background = ContextCompat.getDrawable(ctx, R.drawable.bg_tts_row)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, 0, 0, 6)
                    layoutParams = params
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        showCustomTtsLabelDialog(ctx, mappingStorage, button, gesture, host::appendLog) { populateTtsList() }
                    }
                }

                val gestureTag = TextView(ctx).apply {
                    text = gesture.tag
                    textSize = 10.5f
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_accent_bright))
                    background = ContextCompat.getDrawable(ctx, R.drawable.bg_tts_gesture_tag)
                    setPadding(10, 4, 10, 4)
                    minWidth = 60
                    gravity = Gravity.CENTER
                }

                val mainCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    p.setMargins(16, 0, 8, 0)
                    layoutParams = p
                }

                val phrase = TextView(ctx).apply {
                    text = "“$ttsText”"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_ink))
                }
                mainCol.addView(phrase)

                if (custom != null) {
                    val customChip = TextView(ctx).apply {
                        text = "● PERSONALIZZATO"
                        textSize = 9f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(ctx, R.color.status_highlight))
                        background = ContextCompat.getDrawable(ctx, R.drawable.bg_tts_custom_chip)
                        setPadding(8, 2, 8, 2)
                        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        p.topMargin = 4
                        layoutParams = p
                    }
                    mainCol.addView(customChip)
                }

                val pencil = TextView(ctx).apply {
                    text = "✏️"
                    textSize = 15f
                }

                row.addView(gestureTag)
                row.addView(mainCol)
                row.addView(pencil)
                container.addView(row)
            }
        }
    }
}

package com.br80.remote.ui.options

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.br80.remote.ActionCategory
import com.br80.remote.ActionType
import com.br80.remote.Br80Button
import com.br80.remote.GestureType
import com.br80.remote.R
import com.br80.remote.ui.showCustomTtsLabelForActionDialog

/** Schermata "Gestisci Testi TTS": un testo per AZIONE (condiviso da qualunque tasto/gesto
 * la esegua), raggruppato per categoria. I trigger Tasker non compaiono qui: hanno la loro
 * schermata dedicata "Gestisci Tasti Tasker", dove il nome del Tasto Virtuale è anche il
 * testo pronunciato. */
class TtsLabelsFragment : OptionsDetailFragment(R.layout.fragment_option_tts_labels, "Testi Annuncio Vocale") {

    private lateinit var container: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.llTtsLabelsContainer)
        populateTtsList()
    }

    private fun populateTtsList() {
        val ctx = requireContext()

        val mappedTypes = mutableSetOf<ActionType>()
        for (button in Br80Button.values()) {
            for (gesture in GestureType.values()) {
                val type = mappingStorage.getAction(button, gesture).type
                if (type != ActionType.NONE && type != ActionType.TASKER_TRIGGER_EVENT && type != ActionType.TASKER_ONLY) {
                    mappedTypes.add(type)
                }
            }
        }

        container.removeAllViews()

        if (mappedTypes.isEmpty()) {
            val empty = TextView(ctx).apply {
                text = "Nessuna azione mappata su cui personalizzare il TTS."
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_muted))
                setPadding(0, 24, 0, 0)
            }
            container.addView(empty)
            return
        }

        val grouped = mappedTypes.groupBy { it.category }

        for (category in ActionCategory.values()) {
            val types = grouped[category] ?: continue

            val groupHead = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 20, 8, 8)
            }

            val icon = TextView(ctx).apply {
                text = category.icon
                textSize = 14f
            }

            val name = TextView(ctx).apply {
                text = "  ${category.displayName}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_ink))
            }

            groupHead.addView(icon)
            groupHead.addView(name)
            container.addView(groupHead)

            for (type in types.sortedBy { it.displayName }) {
                val custom = mappingStorage.getCustomTtsLabelForActionType(type)
                val ttsText = custom ?: type.displayName

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
                        showCustomTtsLabelForActionDialog(ctx, mappingStorage, type, host::appendLog) { populateTtsList() }
                    }
                }

                val mainCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    p.setMargins(16, 0, 8, 0)
                    layoutParams = p
                }

                val actionName = TextView(ctx).apply {
                    text = type.displayName
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_muted))
                }
                mainCol.addView(actionName)

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

                row.addView(mainCol)
                row.addView(pencil)
                container.addView(row)
            }
        }
    }
}

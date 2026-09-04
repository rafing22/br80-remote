package com.br80.remote.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.br80.remote.ActionCategory
import com.br80.remote.ActionType
import com.br80.remote.Br80Button
import com.br80.remote.GestureType
import com.br80.remote.R

/**
 * Selezione azione a due passi (categoria → azione) invece di un'unica lista lunga con
 * categorie collassabili: pensato per l'uso con i guanti (bersagli grandi, tile visive
 * al posto di una tastiera di ricerca sempre aperta). La ricerca testuale resta
 * disponibile ma opzionale, dietro l'icona 🔍, e mostra risultati come lista piatta
 * nello stesso stile del passo 2.
 */
object ActionPickerDialog {

    fun show(context: Context, button: Br80Button, gesture: GestureType, onActionSelected: (ActionType) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_action_picker, null)

        val headerStep1 = view.findViewById<View>(R.id.headerStep1)
        val tvStep1Title = view.findViewById<TextView>(R.id.tvStep1Title)
        val btnSearchToggle = view.findViewById<TextView>(R.id.btnSearchToggle)
        val etSearch = view.findViewById<EditText>(R.id.etActionSearch)
        val headerStep2 = view.findViewById<View>(R.id.headerStep2)
        val btnBack = view.findViewById<TextView>(R.id.btnBackToCategories)
        val tvStep2Title = view.findViewById<TextView>(R.id.tvStep2Title)
        val gridCategories = view.findViewById<GridLayout>(R.id.gridCategories)
        val llActionsList = view.findViewById<LinearLayout>(R.id.llActionsList)

        tvStep1Title.text = "${button.displayName} — ${gesture.displayName}"

        val dialog = AlertDialog.Builder(context, R.style.Theme_Br80_CockpitDialog)
            .setView(view)
            .setNegativeButton("Annulla", null)
            .create()

        fun showCategoryGrid() {
            headerStep1.visibility = View.VISIBLE
            headerStep2.visibility = View.GONE
            gridCategories.visibility = View.VISIBLE
            llActionsList.visibility = View.GONE
        }

        fun showActionList(title: String, actions: List<ActionType>) {
            headerStep1.visibility = View.GONE
            headerStep2.visibility = View.VISIBLE
            tvStep2Title.text = title
            gridCategories.visibility = View.GONE
            llActionsList.visibility = View.VISIBLE
            llActionsList.removeAllViews()

            for (action in actions) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp(context, 56)
                    setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    background = ContextCompat.getDrawable(context, R.drawable.bg_cockpit_card)
                    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    params.bottomMargin = dp(context, 6)
                    layoutParams = params
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        dialog.dismiss()
                        onActionSelected(action)
                    }
                }
                val tvTitle = TextView(context).apply {
                    text = action.displayName
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    // Rosso riservato ai soli elementi toccabili: qui è l'intera riga ad
                    // esserlo, quindi il titolo azione lo segnala sempre in accent, tranne
                    // "Nessuna Azione" che resta muted (non è una vera azione da eseguire).
                    setTextColor(ContextCompat.getColor(context, if (action == ActionType.NONE) R.color.cockpit_muted else R.color.cockpit_accent))
                }
                val tvDesc = TextView(context).apply {
                    text = action.description
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.cockpit_muted))
                    setPadding(0, dp(context, 2), 0, 0)
                }
                row.addView(tvTitle)
                row.addView(tvDesc)
                llActionsList.addView(row)
            }
        }

        // Griglia categorie (passo 1)
        gridCategories.columnCount = 2
        for (category in ActionCategory.values()) {
            val actionsInCategory = ActionType.values().filter { it.category == category }
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                background = ContextCompat.getDrawable(context, R.drawable.bg_cockpit_card)
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                )
                params.width = 0
                params.height = dp(context, 84)
                params.setMargins(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener { showActionList("${category.icon} ${category.displayName}", actionsInCategory) }
            }
            val icon = TextView(context).apply {
                text = category.icon
                textSize = 24f
                gravity = android.view.Gravity.CENTER
            }
            val label = TextView(context).apply {
                text = category.displayName
                textSize = 11.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.cockpit_ink))
                gravity = android.view.Gravity.CENTER
                setPadding(dp(context, 4), dp(context, 4), dp(context, 4), 0)
            }
            tile.addView(icon)
            tile.addView(label)
            gridCategories.addView(tile)
        }

        btnSearchToggle.setOnClickListener {
            etSearch.visibility = if (etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (etSearch.visibility == View.VISIBLE) etSearch.requestFocus()
        }

        btnBack.setOnClickListener { showCategoryGrid() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                if (query.isBlank()) {
                    showCategoryGrid()
                    etSearch.visibility = View.VISIBLE
                    return
                }
                val results = ActionType.values().filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.category.displayName.contains(query, ignoreCase = true)
                }
                showActionList("Risultati per \"$query\"", results)
                headerStep1.visibility = View.VISIBLE
                etSearch.visibility = View.VISIBLE
                headerStep2.visibility = View.GONE
            }
        })

        showCategoryGrid()
        dialog.show()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

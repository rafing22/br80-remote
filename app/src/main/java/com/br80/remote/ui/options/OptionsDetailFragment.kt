package com.br80.remote.ui.options

import android.view.View
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.br80.remote.MainActivity
import com.br80.remote.R

/**
 * Base per le sotto-schermate di Opzioni: gestisce l'header comune (freccia Indietro +
 * titolo). La freccia chiude sempre e solo questa sotto-schermata (pop del child
 * FragmentManager di OptionsFragment), mai il doppio-back-per-uscire dell'app.
 */
abstract class OptionsDetailFragment(@LayoutRes layoutId: Int, private val screenTitle: String) : Fragment(layoutId) {

    protected val host get() = requireActivity() as MainActivity
    protected val mappingStorage get() = host.mappingStorage

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tvDetailTitle).text = screenTitle
        view.findViewById<View>(R.id.btnDetailBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
}

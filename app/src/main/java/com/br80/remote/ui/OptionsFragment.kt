package com.br80.remote.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.br80.remote.R
import com.br80.remote.ui.options.OptionsListFragment

/**
 * Host della tab "Opzioni": gestisce la propria navigazione interna (child
 * FragmentManager, indipendente dal fragmentContainer principale di MainActivity che
 * ospita Controller/Opzioni/Log) tra l'elenco principale e le sotto-schermate di
 * dettaglio. Isolato apposta: le 3 tab principali usano add()+hide()/show() per non
 * distruggere mai le loro view, un replace() delle sotto-schermate qui non le tocca.
 */
class OptionsFragment : Fragment(R.layout.fragment_options_host) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (childFragmentManager.findFragmentById(R.id.optionsDetailContainer) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.optionsDetailContainer, OptionsListFragment())
                .commitNow()
        }
    }

    fun navigateToDetail(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.optionsDetailContainer, fragment)
            .addToBackStack("options_detail")
            .commit()
    }

    /** Chiamato da MainActivity prima di applicare il doppio-back-per-uscire: se siamo
     * in una sotto-schermata la chiude, altrimenti lascia gestire il back a MainActivity. */
    fun handleBackPressed(): Boolean {
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
            return true
        }
        return false
    }
}

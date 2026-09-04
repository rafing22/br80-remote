package com.br80.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.br80.remote.MainActivity
import com.br80.remote.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tab "Log": registro eventi in tempo reale, con copia/export/pulisci. */
class LogFragment : Fragment(R.layout.fragment_log) {

    private lateinit var tvLogFull: TextView
    private lateinit var svLogFull: ScrollView

    private val host get() = requireActivity() as MainActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvLogFull = view.findViewById(R.id.tvLogFull)
        svLogFull = view.findViewById(R.id.svLogFull)
        val btnLogCopy = view.findViewById<TextView>(R.id.btnLogCopy)
        val btnLogExport = view.findViewById<TextView>(R.id.btnLogExport)
        val btnLogClearTab = view.findViewById<TextView>(R.id.btnLogClearTab)

        btnLogCopy.setOnClickListener {
            val ctx = requireContext()
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("BR80 Log", tvLogFull.text)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(ctx, "Log copiato negli appunti!", Toast.LENGTH_SHORT).show()
        }

        btnLogClearTab.setOnClickListener {
            tvLogFull.text = "[LOG PULITO]"
        }

        btnLogExport.setOnClickListener {
            exportLogToFile()
        }
    }

    /** Aggiunge una riga al registro. Chiamato da MainActivity.appendLog(), che a sua volta
     * viene invocato da qualsiasi punto dell'app (fragment o callback del servizio BLE). */
    fun appendLog(line: String) {
        tvLogFull.append("\n$line")
        svLogFull.post {
            svLogFull.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun exportLogToFile() {
        val ctx = requireContext()
        try {
            val logsDir = File(ctx.cacheDir, "logs").apply { mkdirs() }
            val fileName = "BR80_Log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
            val logFile = File(logsDir, fileName)
            logFile.writeText(tvLogFull.text.toString())

            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                logFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Esporta Log Diagnostico"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Errore esportazione log: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

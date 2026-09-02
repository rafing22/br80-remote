package com.br80.remote

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object TaskerExporter {

    fun generateProjectXml(): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<TaskerData sr=\"\" dvi=\"1\" tv=\"6.2.22\">\n")
        sb.append("  <Project sr=\"proj_br80\" ve=\"2\">\n")
        sb.append("    <cdate>1700000000000</cdate>\n")
        sb.append("    <name>Livall BR80 Remote</name>\n")
        sb.append("    <scenes></scenes>\n")
        sb.append("    <tasks>100,101,102</tasks>\n")
        sb.append("    <scenes></scenes>\n")
        sb.append("  </Project>\n")

        // Task 100: Esempio Foto
        sb.append("  <Task sr=\"task100\">\n")
        sb.append("    <cdate>1700000000000</cdate>\n")
        sb.append("    <edate>1700000000000</edate>\n")
        sb.append("    <id>100</id>\n")
        sb.append("    <nme>BR80 Scatta Foto</nme>\n")
        sb.append("    <pri>100</pri>\n")
        sb.append("    <Action sr=\"act0\" ve=\"7\">\n")
        sb.append("      <code>101</code>\n") // Take Photo
        sb.append("      <Str sr=\"arg0\" ve=\"3\">DCIM/BR80</Str>\n")
        sb.append("      <Int sr=\"arg1\" val=\"0\"/>\n")
        sb.append("    </Action>\n")
        sb.append("  </Task>\n")

        // Task 101: Esempio Torcia
        sb.append("  <Task sr=\"task101\">\n")
        sb.append("    <cdate>1700000000000</cdate>\n")
        sb.append("    <edate>1700000000000</edate>\n")
        sb.append("    <id>101</id>\n")
        sb.append("    <nme>BR80 Toggle Torcia</nme>\n")
        sb.append("    <pri>100</pri>\n")
        sb.append("    <Action sr=\"act0\" ve=\"7\">\n")
        sb.append("      <code>511</code>\n") // Torch
        sb.append("      <Int sr=\"arg0\" val=\"2\"/>\n") // Toggle
        sb.append("    </Action>\n")
        sb.append("  </Task>\n")

        // Task 102: Log Generico
        sb.append("  <Task sr=\"task102\">\n")
        sb.append("    <cdate>1700000000000</cdate>\n")
        sb.append("    <edate>1700000000000</edate>\n")
        sb.append("    <id>102</id>\n")
        sb.append("    <nme>BR80 Notifica Evento</nme>\n")
        sb.append("    <pri>100</pri>\n")
        sb.append("    <Action sr=\"act0\" ve=\"7\">\n")
        sb.append("      <code>548</code>\n") // Flash message
        sb.append("      <Str sr=\"arg0\" ve=\"3\">BR80: Ricevuto %event_id (Batteria %battery%)</Str>\n")
        sb.append("      <Int sr=\"arg1\" val=\"0\"/>\n")
        sb.append("    </Action>\n")
        sb.append("  </Task>\n")

        // Profili Intent Received per ciascun tasto
        var profileId = 200
        for (btn in Br80Button.values()) {
            for (gesture in GestureType.values()) {
                val eventId = "${btn.name}_${gesture.name}"
                sb.append("  <Profile sr=\"prof$profileId\" ve=\"2\">\n")
                sb.append("    <cdate>1700000000000</cdate>\n")
                sb.append("    <clp>true</clp>\n")
                sb.append("    <id>$profileId</id>\n")
                sb.append("    <mid>102</mid>\n")
                sb.append("    <nme>BR80 $eventId</nme>\n")
                sb.append("    <Event sr=\"con0\" ve=\"2\">\n")
                sb.append("      <code>599</code>\n") // Intent Received
                sb.append("      <Str sr=\"arg0\" ve=\"3\">com.br80.remote.BUTTON_EVENT</Str>\n")
                sb.append("      <Str sr=\"arg1\" ve=\"3\"/>\n")
                sb.append("      <Str sr=\"arg2\" ve=\"3\"/>\n")
                sb.append("      <Str sr=\"arg3\" ve=\"3\">event_id:$eventId</Str>\n")
                sb.append("    </Event>\n")
                sb.append("  </Profile>\n")
                profileId++
            }
        }

        sb.append("</TaskerData>\n")
        return sb.toString()
    }

    fun exportAndShare(context: Context) {
        try {
            val xml = generateProjectXml()
            val fileName = "Livall_BR80_Tasker_Project.prj.xml"
            val file = File(context.cacheDir, fileName)
            file.writeText(xml)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Progetto Tasker per Livall BR80")
                putExtra(Intent.EXTRA_TEXT, "Importa questo file .xml in Tasker (Progetti -> Importa Progetto).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Salva o Importa in Tasker").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Fallback se fileprovider non è registrato: condividi come testo grezzo
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Configurazione Tasker BR80")
                putExtra(Intent.EXTRA_TEXT, "Action: com.br80.remote.BUTTON_EVENT\nExtras: button, gesture, event_id, battery")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(textIntent, "Condividi istruzioni Tasker").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

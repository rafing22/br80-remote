package com.br80.remote

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val RELEASES_API_URL = "https://api.github.com/repos/rafing22/br80-remote/releases/latest"
    private const val USER_AGENT = "Livall-BR80-Remote-App"
    private val handler = Handler(Looper.getMainLooper())

    fun checkForUpdates(activity: Activity, isManualCheck: Boolean = false) {
        Thread {
            try {
                val url = URL(RELEASES_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val code = conn.responseCode
                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)

                    val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                    val body = json.optString("body", "Miglioramenti e nuove funzionalità.")
                    val assets = json.optJSONArray("assets")

                    var apkDownloadUrl: String? = null
                    var apkName = "Livall-BR80-Remote-update.apk"

                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkDownloadUrl = asset.optString("browser_download_url")
                                apkName = name
                                break
                            }
                        }
                    }

                    val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()

                    if (isNewerVersion(currentVersion, tagName) && !apkDownloadUrl.isNullOrEmpty()) {
                        handler.post {
                            showUpdateDialog(activity, tagName, body, apkDownloadUrl, apkName)
                        }
                    } else if (isManualCheck) {
                        handler.post {
                            Toast.makeText(activity, "L'app è già aggiornata all'ultima versione (v$currentVersion).", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.w(TAG, "GitHub API HTTP $code: $errorText")
                    if (isManualCheck) {
                        handler.post {
                            val msg = when (code) {
                                404 -> "Nessuna release trovata su GitHub."
                                403 -> "Limite richieste GitHub superato o repository privata. Riprova più tardi."
                                else -> "Errore verifica aggiornamenti (Codice $code)."
                            }
                            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Check update failed: ${e.message}")
                if (isManualCheck) {
                    handler.post {
                        Toast.makeText(activity, "Impossibile verificare aggiornamenti: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun isNewerVersion(current: String, remote: String): Boolean {
        if (remote.isBlank() || current == remote) return false
        try {
            val curParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val remParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(curParts.size, remParts.size)

            for (i in 0 until maxLen) {
                val c = curParts.getOrElse(i) { 0 }
                val r = remParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (e: Exception) {
            return remote != current
        }
        return false
    }

    private fun showUpdateDialog(
        activity: Activity,
        newVersion: String,
        changelog: String,
        downloadUrl: String,
        apkName: String
    ) {
        AlertDialog.Builder(activity)
            .setTitle("Nuovo Aggiornamento: v$newVersion 🚀")
            .setMessage("È disponibile una nuova versione dell'applicazione su GitHub!\n\nNote di rilascio:\n$changelog\n\nVuoi scaricarla e aggiornare ora?")
            .setPositiveButton("Aggiorna Ora") { _, _ ->
                checkInstallPermissionAndDownload(activity, downloadUrl, apkName)
            }
            .setNegativeButton("Più Tardi", null)
            .show()
    }

    private fun checkInstallPermissionAndDownload(activity: Activity, downloadUrl: String, apkName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(activity)
                    .setTitle("Permesso Installazione")
                    .setMessage("Per aggiornare l'applicazione, autorizza l'installazione di app da questa sorgente nelle impostazioni di sistema.")
                    .setPositiveButton("Apri Impostazioni") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivity(intent)
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
                return
            }
        }
        downloadAndInstall(activity, downloadUrl, apkName)
    }

    @Suppress("DEPRECATION")
    private fun downloadAndInstall(activity: Activity, downloadUrl: String, apkName: String) {
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("Download Aggiornamento")
            setMessage("Download in corso da GitHub...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            max = 100
            setCancelable(false)
            show()
        }

        Thread {
            try {
                var currentUrl = downloadUrl
                var conn: HttpURLConnection
                var redirectCount = 0

                // Gestione robusta dei redirect 301/302/307/308 fino ad AWS S3
                while (true) {
                    val url = URL(currentUrl)
                    conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", USER_AGENT)
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.instanceFollowRedirects = true

                    val code = conn.responseCode
                    if (code in 300..399 && redirectCount < 5) {
                        val loc = conn.getHeaderField("Location")
                        if (!loc.isNullOrEmpty()) {
                            currentUrl = loc
                            redirectCount++
                            continue
                        }
                    }
                    break
                }

                val fileLength = conn.contentLength
                val outputFile = File(activity.cacheDir, apkName)

                val input: InputStream = conn.inputStream
                FileOutputStream(outputFile).use { output ->
                    val data = ByteArray(4096)
                    var total = 0L
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            val progress = (total * 100 / fileLength).toInt()
                            handler.post { progressDialog.progress = progress }
                        }
                        output.write(data, 0, count)
                    }
                }
                input.close()

                handler.post {
                    progressDialog.dismiss()
                    installApk(activity, outputFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download APK failed: ${e.message}", e)
                handler.post {
                    progressDialog.dismiss()
                    Toast.makeText(activity, "Errore nel download: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install intent failed: ${e.message}", e)
            Toast.makeText(context, "Errore avvio installazione: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

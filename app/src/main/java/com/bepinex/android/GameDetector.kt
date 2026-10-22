package com.bepinex.android

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Auto-detects Unity IL2CPP games installed on the device.
 *
 * Uses [PackageManager.queryIntentActivities] with MAIN/LAUNCHER intent
 * to discover launchable apps without requiring QUERY_ALL_PACKAGES permission.
 * The corresponding `<queries>` declaration in AndroidManifest.xml makes this
 * work on Android 11+ without any runtime permission dialogs.
 *
 * Detection algorithm:
 *   1. Query all launchable activities via PackageManager
 *   2. Deduplicate by package name, skip system + our own app
 *   3. For each candidate APK, open as ZIP and check for libil2cpp.so
 */
object GameDetector {

    private const val TAG = "GameDetector"

    /** Package name of Among Us on Android. */
    const val AMONG_US_PACKAGE = "com.innersloth.spacemafia"

    /** ABI directories to scan for libil2cpp.so */
    private val UNITY_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /** Cached detection results */
    private var cachedGames: List<DetectedGame>? = null

    data class DetectedGame(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val versionName: String,
        val versionCode: Long,
        val unityVersion: String?,
        val apkPath: String
    )

    /**
     * Scan installed apps and return detected Unity IL2CPP games.
     */
    suspend fun detectGames(context: Context): List<DetectedGame> = withContext(Dispatchers.IO) {
        cachedGames?.let { return@withContext it }

        val pm = context.packageManager
        val results = mutableListOf<DetectedGame>()
        val seenPackages = mutableSetOf<String>()

        // Query all launchable activities (works with <queries> intent filter)
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)

        BepInExLog.i("Scanning ${activities.size} launchable apps for Unity IL2CPP games...")

        for (resolveInfo in activities) {
            val packageName = resolveInfo.activityInfo.packageName

            // Deduplicate (some apps have multiple launcher activities)
            if (packageName in seenPackages) continue
            seenPackages.add(packageName)

            // Skip our own package
            if (packageName == context.packageName) continue

            // This build only supports Among Us
            if (packageName != AMONG_US_PACKAGE) continue

            // Get app info
            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }

            // Skip system apps for performance
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

            val apkPath = appInfo.sourceDir ?: continue
            if (!File(apkPath).exists()) continue

            // Check for libil2cpp.so in the APK
            val hasIl2Cpp = checkForIl2Cpp(apkPath)
            if (!hasIl2Cpp) {
                // Also try the native library directory
                val libCheck = File(appInfo.nativeLibraryDir, "libil2cpp.so")
                if (!libCheck.exists()) continue
            }

            // Found a Unity IL2CPP game!
            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }

            var versionName = "Unknown"
            var versionCode = 0L
            try {
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageInfo(packageName, 0)
                versionName = pkgInfo.versionName ?: "Unknown"
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to get package info for $packageName: ${e.message}")
            }

            val game = DetectedGame(
                packageName = packageName,
                label = label,
                icon = icon,
                versionName = versionName,
                versionCode = versionCode,
                unityVersion = null,
                apkPath = apkPath
            )

            BepInExLog.i("Detected: $label ($packageName) v$versionName")
            results.add(game)
        }

        BepInExLog.i("Found ${results.size} Unity IL2CPP game(s)")

        cachedGames = results
        results
    }

    /**
     * Open an APK as a ZIP and check for libil2cpp.so in any ABI directory.
     */
    private fun checkForIl2Cpp(apkPath: String): Boolean {
        try {
            ZipFile(apkPath).use { zip ->
                for (abi in UNITY_ABIS) {
                    val entry = zip.getEntry("lib/$abi/libil2cpp.so")
                    if (entry != null && !entry.isDirectory) return true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cannot scan APK: $apkPath — ${e.message}")
        }
        return false
    }

    fun invalidateCache() {
        cachedGames = null
    }
}/string>
    <string name="modpack_mod_sort">Sort</string>
    <string name="modpack_mod_sort_name">Display name</string>
    <string name="modpack_mod_sort_file">File name</string>
    <string name="modpack_mod_sort_enabled_first">Enabled first</string>
    <string name="modpack_mod_sort_disabled_first">Disabled first</string>
    <string name="modpack_search_mods">Search mods</string>
    <string name="modpack_search_clear">Clear search</string>
    <string name="modpack_search_no_results">No matching mods</string>
    <string name="modpack_scroll_to_top">Back to top</string>
    <string name="modpack_scrollbar">Scroll list</string>

    <!-- Config Editor -->
    <string name="config_editor_title">Config Editor</string>
    <string name="config_editor_save">Save</string>
    <string name="config_editor_save_failed">Failed to save the file</string>
    <string name="config_editor_unsaved">Unsaved changes</string>
    <string name="config_editor_discard_title">Discard Changes?</string>
    <string name="config_editor_discard_msg">You have unsaved changes. Discard them?</string>
    <string name="config_editor_no_configs">No config files</string>

    <!-- Viewer -->
    <string name="viewer_settings">Viewer Settings</string>
    <string name="viewer_word_wrap">Word Wrap</string>
    <string name="viewer_word_wrap_desc">Wrap long lines to fit the screen width</string>
    <string name="viewer_line_numbers">Line Numbers</string>
    <string name="viewer_line_numbers_desc">Show line numbers on the left side</string>
    <string name="viewer_empty">Empty file</string>
    <string name="viewer_cursor_position">Ln %1$d, Col %2$d</string>
    <string name="viewer_preview">Preview</string>
    <string name="viewer_preview_text">"Every art and every inquiry, and similarly every action and pursuit, is thought to aim at some good; and for this reason the good has rightly been declared to be that at which all things aim." — Aristotle, *Nicomachean Ethics*</string>

    <!-- Log Viewer -->
    <string name="log_settings">Log Settings</string>
    <string name="log_auto_scroll_desc">Automatically scroll to bottom when new content arrives</string>
    <string name="log_word_wrap">Word Wrap</string>
    <string name="log_word_wrap_desc">Wrap long lines to fit the screen width</string>
    <string name="log_line_numbers">Line Numbers</string>
    <string name="log_line_numbers_desc">Show line numbers on the left side</string>

    <!-- Mod File Browser -->
    <string name="mod_file_browser_title">Mod Files</string>
    <string name="mod_file_browser_empty">This directory is empty</string>
    <string name="mod_file_browser_missing">The profile directory does not exist</string>
    <string name="mod_file_browser_directory">Directory</string>
    <string name="mod_file_browser_editable_file">%1$s · Tap to view or edit</string>
    <string name="mod_file_browser_unsupported_file">%1$s · Preview not supported</string>
    <string name="mod_file_browser_edit_file">View or edit file</string>
    <string name="mod_file_browser_delete_file">Delete file</string>
    <string name="mod_file_browser_delete_title">Delete file?</string>
    <string name="mod_file_browser_delete_message">Delete "%1$s"? This cannot be undone.</string>
    <string name="mod_file_browser_delete_failed">Failed to delete the file</string>
    <string name="mod_file_browser_protected">Protected file</string>
    <string name="mod_file_browser_protected_file">Protected profile metadata · Cannot edit or delete</string>

    <!-- Settings sections -->
    <string name="settings_title">Settings</string>
    <string name="settings_section_appearance">Appearance</string>
    <string name="settings_section_ingame">In-Game</string>
    <string name="settings_section_maintenance">Maintenance</string>
    <string name="settings_section_saves">Save Data</string>
    <string name="settings_import_saves">Import Saves</string>
    <string name="settings_import_saves_desc">Import save data from the original game. Select Android/data/&lt;package&gt;/files/ when prompted.</string>
    <string name="save_import_success">Imported %d file(s)</string>
    <string name="save_import_failed">Import failed</string>
    <string name="save_import_nothing">No save data found in the selected directory</string>
    <string name="save_saf_title">Game Saves (via SAF)</string>
    <string name="save_direct_title">Game Saves</string>
    <string name="save_shizuku_title">Game Saves (via Shizuku)</string>
    <string name="save_saf_hint">Android 11 requires folder permission</string>
    <string name="save_shizuku_hint">Android 12+ uses privileged shell access</string>
    <string name="save_need_permission">Need permission to access game saves</string>
    <string name="save_not_found">Game files directory not found. Launch the game at least once.</string>
    <string name="save_empty">The game files/ directory is empty.</string>
    <string name="save_found">%d save(s) found</string>
    <string name="save_launch_game_prompt">Please launch the patched game at least once to create data in its files/ directory, then return here to back it up.</string>
    <string name="save_game_launched">Game has been launched</string>
    <string name="save_game_not_launched">Game not launched yet</string>
    <string name="save_grant_permission">Grant Folder Access</string>
    <string name="save_grant_permission_desc">Tap to open file picker, then select \"Use this folder\"</string>

    <string name="save_g2l_title">Backup Game Saves</string>
    <string name="save_g2l_desc">Copy game saves and playerData.json to safe location</string>
    <string name="save_g2l_button">Backup to Launcher</string>
    <string name="save_g2l_desc_detail">Game → PVZRH_Launcher/G2L/</string>
    <string name="save_g2l_done">Backed up %d item(s) to G2L</string>
    <string name="save_g2l_error">Backup failed: %s</string>
    <string name="save_g2l_exists">Backup exists (will overwrite)</string>
    <string name="save_g2l_contents">G2L contents:</string>
    <string name="save_g2l_import_button">Import from G2L</string>
    <string name="save_g2l_import_desc">Copy G2L backup to launcher storage</string>
    <string name="save_g2l_import_done">Imported %d item(s) from G2L</string>
    <string name="save_g2l_import_error">Import failed: %s</string>

    <string name="save_l2g_title">Restore to Game</string>
    <string name="save_restore_backup_title">Restore Backup</string>
    <string name="save_restore_backup_desc">Restore the latest launcher backup to this game</string>
    <string name="save_restore_backup_confirm">The current game save data will be overwritten by the selected backup.</string>
    <string name="save_l2g_desc">Copy saves from PVZRH_Launcher/L2G/ back to game</string>
    <string name="save_l2g_button">Restore to Game</string>
    <string name="save_l2g_desc_detail">PVZRH_Launcher/L2G/ → Game</string>
    <string name="save_l2g_done">Restored %d item(s) to game</string>
    <string name="save_l2g_error">Restore failed: %s</string>
    <string name="save_l2g_empty">No data to restore. Place saves in PVZRH_Launcher/L2G/ first.</string>
    <string name="save_l2g_contents">L2G contents:</string>
    <string name="save_l2g_export_button">Export to L2G</string>
    <string name="save_l2g_export_desc">Copy launcher saves to L2G folder</string>
    <string name="save_l2g_export_done">Exported %d item(s) to L2G</string>
    <string name="save_l2g_export_error">Export failed: %s</string>

    <string name="save_clear_g2l">Clear G2L Backup</string>
    <string name="save_clear_l2g">Clear L2G Data</string>

    <string name="save_permission_granted">Permission granted</string>
    <string name="save_permission_denied">Permission denied</string>
    <string name="save_backup_cleared">Backup cleared</string>
    <string name="save_manual_guide">Manual Guide</string>
    <string name="save_manual_direct_title">Direct Access (Android &lt; 11)</string>
    <string name="save_manual_direct_step1">1. Use a file manager to copy the entire folder: /sdcard/Android/data/%1$s/files/</string>
    <string name="save_manual_direct_step2">2. Save data is stored directly in this files/ folder, including playerData.json and any subfolders</string>
    <string name="save_manual_direct_step3">3. To restore, copy the files and subfolders back to the same files/ folder</string>
    <string name="save_manual_saf_title">Folder Permission (Android 11)</string>
    <string name="save_manual_saf_step1">1. Tap \"Grant Folder Access\" and select \"Use this folder\"</string>
    <string name="save_manual_saf_step2">2. Tap \"Backup to Launcher\" to save game data</string>
    <string name="save_manual_saf_step3">3. To restore, place files in L2G folder, then tap \"Restore to Game\"</string>
    <string name="save_manual_adb_alt">Or use ADB from PC:</string>
    <string name="save_adb_step1">1. Connect device to PC via USB</string>
    <string name="save_adb_backup_cmd">adb pull /sdcard/Android/data/%1$s/files/. ./PVZRH_Saves/</string>
    <string name="save_adb_restore_cmd">adb push ./PVZRH_Saves/. /sdcard/Android/data/%1$s/files/</string>
    <string name="save_copy_command">Copy command</string>
    <string name="save_command_copied">Command copied</string>
    <string name="save_manual_shizuku_title">Shizuku / ADB (Android 12+)</string>
    <string name="save_manual_shizuku_step1">Option A: Install Shizuku and grant permission above</string>
    <string name="save_manual_shizuku_step2">Option B: Use ADB from PC</string>
    <string name="save_shizuku_not_installed">Shizuku is not installed</string>
    <string name="save_shizuku_not_installed_desc">Install Shizuku to manage saves on Android 12+</string>
    <string name="save_shizuku_install">Install Shizuku</string>
    <string name="save_shizuku_not_running">Shizuku is not running</string>
    <string name="save_shizuku_not_running_desc">Start Shizuku via wireless debugging or root</string>
  

package io.github.xalrk.nudge.ui

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.xalrk.nudge.BuildConfig
import io.github.xalrk.nudge.NudgeApp
import io.github.xalrk.nudge.update.UpdateChecker
import io.github.xalrk.nudge.update.UpdateWorker
import io.github.xalrk.nudge.data.FiredEvent
import io.github.xalrk.nudge.data.FrequencyMode
import io.github.xalrk.nudge.data.Kind
import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.data.SettingsSnapshot
import io.github.xalrk.nudge.data.ThemeMode
import io.github.xalrk.nudge.domain.Colors
import io.github.xalrk.nudge.domain.ImportExport
import java.time.LocalDate
import io.github.xalrk.nudge.scheduler.Notifier
import io.github.xalrk.nudge.scheduler.ReminderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NudgeViewModel(app: Application) : AndroidViewModel(app) {
    private val nudge = app as NudgeApp
    private val dao = nudge.database.reminders()
    private val settingsStore = nudge.settings

    val reminders: StateFlow<List<Reminder>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Delivered notifications from the last ~13 months, for the calendar. */
    val history: StateFlow<List<FiredEvent>> = nudge.database.firedEvents()
        .observeSince(System.currentTimeMillis() - 400L * 24 * 3_600_000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<SettingsSnapshot> = settingsStore.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.snapshot())

    /** One-shot messages for the snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private fun ctx() = getApplication<Application>()

    fun save(r: Reminder, onDone: () -> Unit = {}) = viewModelScope.launch {
        try {
            ReminderEngine.save(ctx(), r)
            onDone()
        } catch (e: SQLiteConstraintException) {
            messages.tryEmit("An identical reminder already exists")
        } catch (e: Exception) {
            messages.tryEmit("Could not save: ${e.message}")
        }
    }

    fun delete(id: Long) = viewModelScope.launch { ReminderEngine.delete(ctx(), id) }

    fun editSeries(original: Reminder, edited: Reminder, occurrence: LocalDate, scope: ReminderEngine.SeriesScope, onDone: () -> Unit = {}) = viewModelScope.launch {
        try { ReminderEngine.editSeries(ctx(), original, edited, occurrence, scope); onDone() }
        catch (e: SQLiteConstraintException) { messages.tryEmit("An identical reminder already exists") }
        catch (e: Exception) { messages.tryEmit("Could not save: ${e.message}") }
    }

    fun deleteFromSeries(original: Reminder, occurrence: LocalDate, scope: ReminderEngine.SeriesScope, onDone: () -> Unit = {}) = viewModelScope.launch {
        ReminderEngine.deleteFromSeries(ctx(), original, occurrence, scope); onDone()
    }

    fun addCustomColor(argb: Int) { settingsStore.customColors = settingsStore.customColors + argb }
    fun removeCustomColor(argb: Int) { settingsStore.customColors = settingsStore.customColors - argb }

    /** The color a reminder's notification and calendar dot use. */
    fun colorOf(r: Reminder?): Int = r?.color ?: Colors.complementary(settingsStore.accentColor)

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch { ReminderEngine.setEnabled(ctx(), id, enabled) }

    fun refresh() = viewModelScope.launch {
        ReminderEngine.refresh(ctx())
        ReminderEngine.fireDue(ctx())
    }

    // ------------------------------------------------------------- settings

    fun setMeanInterval(ms: Long) = viewModelScope.launch {
        settingsStore.meanIntervalMillis = ms
        ReminderEngine.resampleAllRandomLocked(ctx())
    }

    fun setFrequencyMode(mode: FrequencyMode) = viewModelScope.launch {
        settingsStore.frequencyMode = mode
        ReminderEngine.resampleAllRandomLocked(ctx())
    }

    fun setActiveWindow(start: Int, end: Int) = viewModelScope.launch {
        settingsStore.activeStartHour = start
        settingsStore.activeEndHour = end
        ReminderEngine.resampleAllRandomLocked(ctx())
    }

    fun setShowNextRandom(show: Boolean) { settingsStore.showNextRandomTime = show }
    fun setAutoUpdateCheck(on: Boolean) {
        settingsStore.autoUpdateCheck = on
        if (on) UpdateWorker.schedule(ctx()) else UpdateWorker.cancel(ctx())
    }

    fun checkForUpdatesNow() = viewModelScope.launch {
        messages.tryEmit("Checking for updates…")
        when (val r = UpdateChecker.check()) {
            is UpdateChecker.Result.Available -> {
                UpdateChecker.notify(ctx(), r.info)
                settingsStore.lastNotifiedUpdate = r.info.version
                messages.tryEmit("Nudge ${r.info.version} is available. Tap the notification to download it.")
            }
            UpdateChecker.Result.UpToDate -> messages.tryEmit("You have the latest version (${BuildConfig.VERSION_NAME}).")
            is UpdateChecker.Result.Failed -> messages.tryEmit("Could not check: ${r.reason}")
        }
    }

    fun setThemeMode(mode: ThemeMode) { settingsStore.themeMode = mode }
    fun setDynamicColor(on: Boolean) { settingsStore.dynamicColor = on }
    fun setAccentColor(argb: Int) { settingsStore.accentColor = argb }

    fun fireRandomNow() = viewModelScope.launch {
        val ok = ReminderEngine.fireRandomNow(ctx())
        if (!ok) messages.tryEmit("No enabled random reminders yet")
    }

    fun testNotification() {
        Notifier.show(ctx(), Reminder(id = Int.MAX_VALUE.toLong(), title = "Nudge test", body = "Notifications are working.", kind = Kind.RANDOM))
    }

    fun rerollRandom() = viewModelScope.launch {
        ReminderEngine.resampleAllRandomLocked(ctx())
        messages.tryEmit("Random reminders re-rolled")
    }

    // ------------------------------------------------------------- import / export

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            runCatching { ctx().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        }
        if (text == null) { messages.tryEmit("Could not read that file"); return@launch }
        importText(text)
    }

    fun importText(text: String) = viewModelScope.launch {
        val parsed = ImportExport.parse(text)
        if (parsed.reminders.isEmpty() && parsed.errors.isNotEmpty()) {
            messages.tryEmit("Nothing imported. ${parsed.errors.first()}")
            return@launch
        }
        val (inserted, skipped) = ReminderEngine.importAll(ctx(), parsed.reminders)
        val parts = mutableListOf("Imported $inserted")
        if (skipped > 0) parts += "skipped $skipped duplicate${if (skipped == 1) "" else "s"}"
        if (parsed.errors.isNotEmpty()) parts += "${parsed.errors.size} row${if (parsed.errors.size == 1) "" else "s"} not understood (${parsed.errors.first()})"
        messages.tryEmit(parts.joinToString(", "))
    }

    fun exportTo(uri: Uri) = viewModelScope.launch {
        val csv = ImportExport.toCsv(dao.all())
        val ok = withContext(Dispatchers.IO) {
            runCatching { ctx().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(csv) } }.isSuccess
        }
        messages.tryEmit(if (ok) "Exported ${reminders.value.size} reminders" else "Export failed")
    }
}

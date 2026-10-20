package com.nahabit.tracker.core.backup

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Base64
import com.nahabit.tracker.core.database.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FullBackupPayload(
    val schemaVersion: Int = 9,
    val exportTimestamp: Long,
    val habits: List<HabitDefinitionEntity> = emptyList(),
    val habitLogs: List<HabitLogEntity> = emptyList(),
    val journals: List<JournalEntryEntity> = emptyList(),
    val mindfulness: List<MindfulnessSessionEntity> = emptyList(),
    val badHabits: List<BadHabitCounterEntity> = emptyList(),
    val workoutRoutines: List<WorkoutRoutineEntity> = emptyList(),
    val workoutSessions: List<WorkoutSessionEntity> = emptyList(),
    val dailyWaters: List<DailyWaterEntity> = emptyList(),
    val challenges: List<ChallengeEntity> = emptyList(),
    val bodyComposition: List<BodyCompositionEntity> = emptyList(),
    val goals: List<LongTermGoalEntity> = emptyList(),
    val tasks: List<TodoTaskEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList()
)

@Serializable
data class NahabitBackupEnvelope(
    val appIdentifier: String = "com.nahabit.tracker",
    val formatVersion: Int = 2,
    val isEncrypted: Boolean,
    val sha256Checksum: String,
    val saltBase64: String = "",
    val ivBase64: String = "",
    val payloadData: String
)

@Singleton
class BackupAndExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NahabitDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun buildEncryptedOrPlainEnvelope(password: String = ""): String = withContext(Dispatchers.IO) {
        val payload = FullBackupPayload(
            exportTimestamp = System.currentTimeMillis(),
            habits = dao.dumpHabitDefinitions(),
            habitLogs = dao.dumpHabitLogs(),
            journals = dao.dumpJournalEntries(),
            mindfulness = dao.dumpMindfulnessSessions(),
            badHabits = dao.dumpBadHabits(),
            workoutRoutines = dao.dumpWorkoutRoutines(),
            workoutSessions = dao.dumpWorkoutSessions(),
            dailyWaters = dao.dumpDailyWater(),
            challenges = dao.dumpChallenges(),
            bodyComposition = dao.dumpBodyComposition(),
            goals = dao.dumpLongTermGoals(),
            tasks = dao.dumpTodoTasks(),
            focusSessions = dao.dumpFocusSessions()
        )
        val rawPayloadJson = json.encodeToString(payload)
        val checksum = sha256Hex(rawPayloadJson)

        val cleanPass = password.trim()
        val envelope = if (cleanPass.isEmpty()) {
            NahabitBackupEnvelope(
                isEncrypted = false,
                sha256Checksum = checksum,
                payloadData = rawPayloadJson
            )
        } else {
            val encrypted = encryptAesGcm(rawPayloadJson, cleanPass)
            NahabitBackupEnvelope(
                isEncrypted = true,
                sha256Checksum = checksum,
                saltBase64 = encrypted.saltB64,
                ivBase64 = encrypted.ivB64,
                payloadData = encrypted.cipherTextB64
            )
        }
        json.encodeToString(envelope)
    }

    suspend fun exportMasterBackupToUri(uri: Uri, password: String = ""): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val serialized = buildEncryptedOrPlainEnvelope(password)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(serialized.toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    suspend fun importMasterBackupFromUri(uri: Uri, password: String = ""): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val rawText = readTextFromUri(uri) ?: return@withContext false
            val envelope = json.decodeFromString<NahabitBackupEnvelope>(rawText)

            val plainPayloadJson = if (envelope.isEncrypted) {
                val cleanPass = password.trim()
                if (cleanPass.isEmpty()) return@withContext false
                decryptAesGcm(
                    cipherB64 = envelope.payloadData,
                    password = cleanPass,
                    saltB64 = envelope.saltBase64,
                    ivB64 = envelope.ivBase64
                )
            } else {
                envelope.payloadData
            }

            if (sha256Hex(plainPayloadJson) != envelope.sha256Checksum) {
                return@withContext false
            }

            val payload = json.decodeFromString<FullBackupPayload>(plainPayloadJson)
            dao.wipeAllDataTransaction()
            dao.insertAllHabitDefs(payload.habits)
            dao.insertAllHabitLogs(payload.habitLogs)
            dao.insertAllJournals(payload.journals)
            dao.insertAllMindfulness(payload.mindfulness)
            dao.insertAllBadHabits(payload.badHabits)
            dao.insertAllRoutines(payload.workoutRoutines)
            dao.insertAllWorkoutSessions(payload.workoutSessions)
            dao.insertAllWater(payload.dailyWaters)
            dao.insertAllChallenges(payload.challenges)
            dao.insertAllBodyComp(payload.bodyComposition)
            dao.insertAllGoals(payload.goals)
            dao.insertAllTasks(payload.tasks)
            dao.insertAllFocusSessions(payload.focusSessions)
            true
        }.getOrDefault(false)
    }

    suspend fun createLocalRotatingSnapshot(): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "auto_backups").apply { mkdirs() }
        val serialized = buildEncryptedOrPlainEnvelope(password = "")
        val fileName = "nahabit_auto_${LocalDate.now()}_${System.currentTimeMillis()}.nahabit"
        File(dir, fileName).writeText(serialized, Charsets.UTF_8)

        val allFiles = dir.listFiles()?.filter { it.name.endsWith(".nahabit") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        if (allFiles.size > 7) {
            allFiles.drop(7).forEach { it.delete() }
        }
        dir.listFiles()?.count { it.name.endsWith(".nahabit") } ?: 0
    }

    fun getLocalAutoBackupCount(): Int {
        val dir = File(context.filesDir, "auto_backups")
        return dir.listFiles()?.count { it.name.endsWith(".nahabit") } ?: 0
    }

    suspend fun exportHabitsCsvToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val habits = dao.dumpHabitDefinitions()
            val logsByHabit = dao.dumpHabitLogs().filter { it.isCompleted }.groupBy { it.habitId }
            val sb = StringBuilder()
            sb.appendLine("id,title,category,targetValue,unit,totalCompletedDays")
            habits.forEach { h ->
                val cleanTitle = h.title.replace("\"", "\"\"")
                val totalDone = logsByHabit[h.id]?.size ?: 0
                sb.appendLine("${h.id},\"$cleanTitle\",\"${h.category}\",${h.targetValue},\"${h.unit}\",$totalDone")
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    suspend fun importGenericHabitsCsvFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val raw = readTextFromUri(uri) ?: return@withContext false
            val lines = raw.lines().filter { it.isNotBlank() }
            val today = LocalDate.now().toEpochDay()
            lines.forEachIndexed { idx, line ->
                if (idx == 0 && line.lowercase().contains("title")) return@forEachIndexed
                val parts = line.split(",")
                val title = parts.getOrNull(0)?.replace("\"", "")?.trim().orEmpty()
                val target = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                if (title.isNotEmpty()) {
                    dao.upsertHabitDefinition(
                        HabitDefinitionEntity(
                            title = title,
                            targetValue = target.coerceAtLeast(1),
                            createdEpochDay = today
                        )
                    )
                }
            }
            true
        }.getOrDefault(false)
    }

    suspend fun exportPdfReportToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val habits = dao.dumpHabitDefinitions()
            val logs = dao.dumpHabitLogs().filter { it.isCompleted }
            val workouts = dao.dumpWorkoutSessions()
            val journals = dao.dumpJournalEntries()
            val focus = dao.dumpFocusSessions()

            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(2, 132, 199)
                textSize = 20f
                isFakeBoldText = true
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 15f
                isFakeBoldText = true
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 12f
            }

            var y = 55f
            canvas.drawText("Nahabit - Reporte de Progreso y Bienestar (100% Local)", 40f, y, titlePaint)
            y += 28f
            canvas.drawText("Fecha de generación: ${LocalDate.now()}", 40f, y, bodyPaint)
            y += 32f

            canvas.drawText("Resumen General", 40f, y, headerPaint)
            y += 22f
            canvas.drawText("• Hábitos activos configurados: ${habits.size}", 50f, y, bodyPaint)
            y += 18f
            canvas.drawText("• Total de registros de hábitos cumplidos: ${logs.size}", 50f, y, bodyPaint)
            y += 18f
            canvas.drawText("• Sesiones de entrenamiento registradas: ${workouts.size}", 50f, y, bodyPaint)
            y += 18f
            canvas.drawText("• Minutos totales de enfoque Pomodoro: ${focus.sumOf { it.durationMinutes }} min", 50f, y, bodyPaint)
            y += 18f
            canvas.drawText("• Entradas reflexivas en el diario: ${journals.size}", 50f, y, bodyPaint)
            y += 32f

            canvas.drawText("Desglose de Hábitos Activos", 40f, y, headerPaint)
            y += 22f
            val logsByHabit = logs.groupBy { it.habitId }
            habits.take(22).forEach { habit ->
                val count = logsByHabit[habit.id]?.size ?: 0
                canvas.drawText("- ${habit.title} (Meta diaria: ${habit.targetValue}) -> $count días cumplidos", 50f, y, bodyPaint)
                y += 18f
            }

            pdf.finishPage(page)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                pdf.writeTo(out)
            }
            pdf.close()
            true
        }.getOrDefault(false)
    }

    private fun readTextFromUri(uri: Uri): String? {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    sb.appendLine(line)
                    line = reader.readLine()
                }
            }
        }
        return sb.toString().trim()
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class EncryptedBundle(
        val saltB64: String,
        val ivB64: String,
        val cipherTextB64: String
    )

    private fun encryptAesGcm(plainText: String, password: String): EncryptedBundle {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        val keySpec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val secretKeyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedBundle(
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            cipherTextB64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        )
    }

    private fun decryptAesGcm(
        cipherB64: String,
        password: String,
        saltB64: String,
        ivB64: String
    ): String {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(cipherB64, Base64.NO_WRAP)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val secretKeyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
}

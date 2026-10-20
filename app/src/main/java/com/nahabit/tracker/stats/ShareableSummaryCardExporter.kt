package com.nahabit.tracker.stats

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareableSummaryCardExporter {

    fun shareSummaryCard(context: Context, stats: StatsDashboardModel) {
        val width = 1080
        val height = 1080
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#0A0F18"))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141C2B")
        }
        canvas.drawRoundRect(RectF(64f, 64f, width - 64f, height - 64f), 56f, 56f, cardPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 68f
            isFakeBoldText = true
        }
        canvas.drawText("Tu Año en Nahabit", 120f, 190f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 38f
        }
        canvas.drawText("100% Local • Constancia y Bienestar", 120f, 255f, subPaint)

        val metricTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 86f
            isFakeBoldText = true
        }
        val emeraldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            textSize = 72f
            isFakeBoldText = true
        }

        canvas.drawText("Nivel ${stats.level} (${stats.totalXp} XP)", 120f, 430f, metricTitlePaint)
        canvas.drawText("Mejor Racha: ${stats.globalBestStreak} días", 120f, 560f, emeraldPaint)
        canvas.drawText("Cumplimiento 30d: ${stats.monthlyRatePercent}%", 120f, 680f, emeraldPaint)
        canvas.drawText("Hábitos Completados: ${stats.totalCompletedCheckins}", 120f, 800f, subPaint)
        canvas.drawText("Puntaje de Bienestar: ${stats.wellnessScoreToday}/100", 120f, 880f, subPaint)

        val dir = File(context.cacheDir, "achievements").apply { mkdirs() }
        val imageFile = File(dir, "nahabit_milestone.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Nahabit"))
    }
}

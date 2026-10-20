package com.nahabit.tracker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object NahabitMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `habit_definitions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `targetValue` INTEGER NOT NULL,
                    `unit` TEXT NOT NULL,
                    `frequencyDaysMask` INTEGER NOT NULL,
                    `graceDaysAllowed` INTEGER NOT NULL,
                    `isArchived` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `createdEpochDay` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_definitions_isArchived_sortOrder` ON `habit_definitions` (`isArchived`, `sortOrder`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `habit_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `habitId` INTEGER NOT NULL,
                    `epochDay` INTEGER NOT NULL,
                    `completedValue` INTEGER NOT NULL,
                    `isCompleted` INTEGER NOT NULL,
                    `note` TEXT NOT NULL,
                    FOREIGN KEY(`habitId`) REFERENCES `habit_definitions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_logs_habitId_epochDay` ON `habit_logs` (`habitId`, `epochDay`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_epochDay` ON `habit_logs` (`epochDay`)")
            db.execSQL(
                """
                INSERT INTO `habit_definitions` (`id`, `title`, `category`, `targetValue`, `unit`, `frequencyDaysMask`, `graceDaysAllowed`, `isArchived`, `sortOrder`, `createdEpochDay`)
                SELECT `id`, `title`, `type`, `targetCount`, '', 127, 0, 0, `id`, `dateEpochDay`
                FROM `habits_tasks`
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `habit_logs` (`habitId`, `epochDay`, `completedValue`, `isCompleted`, `note`)
                SELECT `id`, `dateEpochDay`, `currentCount`, `isCompleted`, ''
                FROM `habits_tasks`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE IF EXISTS `habits_tasks`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_timestamp` ON `journal_entries` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_metrics_dateEpochDay` ON `body_metrics` (`dateEpochDay`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exercise_library` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `primaryMuscle` TEXT NOT NULL,
                    `secondaryMuscle` TEXT NOT NULL,
                    `equipment` TEXT NOT NULL,
                    `difficulty` TEXT NOT NULL,
                    `isCustom` INTEGER NOT NULL,
                    `description` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `routineId` INTEGER NOT NULL,
                    `routineName` TEXT NOT NULL,
                    `dateEpochDay` INTEGER NOT NULL,
                    `durationSeconds` INTEGER NOT NULL,
                    `totalVolumeKg` REAL NOT NULL,
                    `notes` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_dateEpochDay` ON `workout_sessions` (`dateEpochDay`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_set_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sessionId` INTEGER NOT NULL,
                    `exerciseId` INTEGER NOT NULL,
                    `exerciseName` TEXT NOT NULL,
                    `setNumber` INTEGER NOT NULL,
                    `weightKg` REAL NOT NULL,
                    `repsOrSeconds` INTEGER NOT NULL,
                    `isTimeBased` INTEGER NOT NULL,
                    `rpe` REAL NOT NULL,
                    FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_logs_sessionId` ON `workout_set_logs` (`sessionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_logs_exerciseId` ON `workout_set_logs` (`exerciseId`)")
            db.execSQL("INSERT INTO `exercise_library` (`name`, `primaryMuscle`, `secondaryMuscle`, `equipment`, `difficulty`, `isCustom`, `description`) VALUES ('Dominadas (Pull-ups)', 'Espalda', 'Brazos', 'Peso corporal', 'Intermedio', 0, 'Dominada estricta con agarre prono.')")
            db.execSQL("INSERT INTO `exercise_library` (`name`, `primaryMuscle`, `secondaryMuscle`, `equipment`, `difficulty`, `isCustom`, `description`) VALUES ('Flexiones (Push-ups)', 'Pecho', 'Hombros', 'Peso corporal', 'Principiante', 0, 'Flexión de codos tradicional.')")
            db.execSQL("INSERT INTO `exercise_library` (`name`, `primaryMuscle`, `secondaryMuscle`, `equipment`, `difficulty`, `isCustom`, `description`) VALUES ('Sentadillas libres', 'Piernas', 'Core', 'Peso corporal', 'Principiante', 0, 'Sentadilla profunda sin peso.')")
            db.execSQL("INSERT INTO `exercise_library` (`name`, `primaryMuscle`, `secondaryMuscle`, `equipment`, `difficulty`, `isCustom`, `description`) VALUES ('Fondos en paralelas', 'Pecho', 'Brazos', 'Peso corporal', 'Intermedio', 0, 'Fondos para tríceps y pectoral inferior.')")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_water_logs` (
                    `epochDay` INTEGER NOT NULL,
                    `glassesCount` INTEGER NOT NULL,
                    `targetGlasses` INTEGER NOT NULL,
                    PRIMARY KEY(`epochDay`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `challenges` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `durationDays` INTEGER NOT NULL,
                    `startEpochDay` INTEGER NOT NULL,
                    `isCompleted` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `body_composition_extended` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dateEpochDay` INTEGER NOT NULL,
                    `weightKg` REAL NOT NULL,
                    `heightCm` REAL NOT NULL,
                    `neckCm` REAL NOT NULL,
                    `shouldersCm` REAL NOT NULL,
                    `chestCm` REAL NOT NULL,
                    `waistCm` REAL NOT NULL,
                    `hipsCm` REAL NOT NULL,
                    `thighCm` REAL NOT NULL,
                    `bicepCm` REAL NOT NULL,
                    `bodyFatPercent` REAL NOT NULL,
                    `muscleMassKg` REAL NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_composition_extended_dateEpochDay` ON `body_composition_extended` (`dateEpochDay`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sleep_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dateEpochDay` INTEGER NOT NULL,
                    `durationMinutes` INTEGER NOT NULL,
                    `sleepQuality` TEXT NOT NULL,
                    `notes` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `meal_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dateEpochDay` INTEGER NOT NULL,
                    `mealType` TEXT NOT NULL,
                    `foodName` TEXT NOT NULL,
                    `caloriesKcal` INTEGER NOT NULL,
                    `proteinGrams` REAL NOT NULL,
                    `carbsGrams` REAL NOT NULL,
                    `fatGrams` REAL NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `fasting_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `startTimestamp` INTEGER NOT NULL,
                    `targetHours` INTEGER NOT NULL,
                    `endTimestamp` INTEGER,
                    `isCompleted` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `medication_reminders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `medicineName` TEXT NOT NULL,
                    `dosage` TEXT NOT NULL,
                    `timeOfDay` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `moodScore` INTEGER NOT NULL DEFAULT 4")
            db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `templateType` TEXT NOT NULL DEFAULT 'FREE'")
            db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `photoUri` TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_isFavorite` ON `journal_entries` (`isFavorite`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `mindfulness_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `sessionType` TEXT NOT NULL,
                    `durationMinutes` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `long_term_goals` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `deadlineEpochDay` INTEGER NOT NULL,
                    `linkedHabitId` INTEGER,
                    `milestonesCsv` TEXT NOT NULL,
                    `completedMilestonesCount` INTEGER NOT NULL,
                    `totalMilestonesCount` INTEGER NOT NULL,
                    `isCompleted` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `todo_tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `priority` INTEGER NOT NULL,
                    `dueEpochDay` INTEGER NOT NULL,
                    `subtasksText` TEXT NOT NULL,
                    `isRecurringDaily` INTEGER NOT NULL,
                    `isCompleted` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_tasks_isCompleted_priority` ON `todo_tasks` (`isCompleted`, `priority`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `focus_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dateEpochDay` INTEGER NOT NULL,
                    `durationMinutes` INTEGER NOT NULL,
                    `projectTag` TEXT NOT NULL,
                    `completedTimestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_dateEpochDay` ON `focus_sessions` (`dateEpochDay`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chained_routines` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `timeOfDay` TEXT NOT NULL,
                    `stepsCsv` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO `chained_routines` (`title`, `timeOfDay`, `stepsCsv`) VALUES ('Ritual de Mañana Activa', 'MORNING', 'Beber 1 vaso de agua (1m)|Movilidad articular y estiramiento (5m)|Revisar las 3 prioridades del día (3m)')")
            db.execSQL("INSERT INTO `chained_routines` (`title`, `timeOfDay`, `stepsCsv`) VALUES ('Ritual de Desconexión Nocturna', 'EVENING', 'Alejar pantallas y preparar el día siguiente (5m)|Respiración 4-7-8 o lectura ligera (10m)|Registrar gratitud en el diario (3m)')")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `habitType` TEXT NOT NULL DEFAULT 'YES_NO'")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `goalComparison` TEXT NOT NULL DEFAULT 'AT_LEAST'")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `frequencyType` TEXT NOT NULL DEFAULT 'DAILY'")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `frequencyParam` INTEGER NOT NULL DEFAULT 7")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `timeOfDayGroup` TEXT NOT NULL DEFAULT 'ANYTIME'")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `iconEmoji` TEXT NOT NULL DEFAULT '✨'")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `colorHex` TEXT NOT NULL DEFAULT '#38BDF8'")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `isPaused` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `endEpochDay` INTEGER")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `subtasksCsv` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `reminderHour` INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE `habit_definitions` ADD COLUMN `reminderMinute` INTEGER NOT NULL DEFAULT 0")

            db.execSQL("ALTER TABLE `bad_habits` ADD COLUMN `dailyCostMoney` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `bad_habits` ADD COLUMN `dailyMinutesWasted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `bad_habits` ADD COLUMN `whyIStartedReason` TEXT NOT NULL DEFAULT ''")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `bad_habit_relapses` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `badHabitId` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `triggerReason` TEXT NOT NULL,
                    `note` TEXT NOT NULL,
                    FOREIGN KEY(`badHabitId`) REFERENCES `bad_habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bad_habit_relapses_badHabitId` ON `bad_habit_relapses` (`badHabitId`)")
        }
    }
}

package com.elchworks.tastyworkstaxcalculator.snapshot

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class SnapshotFileService(
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(SnapshotFileService::class.java)

    private val filenameDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        .withZone(ZoneId.of("CET")) // Use CET to match transaction dates

    fun saveSnapshot(snapshot: StateSnapshot, transactionsDir: String) {
        val snapshotDir = getSnapshotDirectory(transactionsDir)
        snapshotDir.mkdirs()

        val filename = generateFilename(snapshot.metadata.lastTransactionDate)
        val file = File(snapshotDir, filename)

        log.info("Saving snapshot to {}", file.absolutePath)
        objectMapper.writeValue(file, snapshot)
        log.info("Snapshot saved successfully")
    }

    fun loadLatestSnapshot(transactionsDir: String): StateSnapshot? {
        val snapshotDir = getSnapshotDirectory(transactionsDir)

        if (!snapshotDir.exists() || !snapshotDir.isDirectory) {
            log.info("No snapshot directory found at {}", snapshotDir.absolutePath)
            return null
        }

        val latestFile = findLatestSnapshotFile(snapshotDir)
            ?: return null.also { log.info("No snapshot files found in {}", snapshotDir.absolutePath) }

        log.info("Loading snapshot from {}", latestFile.absolutePath)
        val snapshot = objectMapper.readValue(latestFile, StateSnapshot::class.java)
        log.info("Snapshot loaded successfully. lastTransactionDate={}", snapshot.metadata.lastTransactionDate)
        return snapshot
    }

    private fun getSnapshotDirectory(transactionsDir: String): File {
        return File(transactionsDir, "snapshots")
    }

    private fun generateFilename(lastTransactionDate: Instant): String {
        val timestamp = filenameDateFormatter.format(lastTransactionDate)
        return "snapshot-$timestamp.json"
    }

    private fun findLatestSnapshotFile(snapshotDir: File): File? {
        val snapshotFiles = snapshotDir.listFiles { file ->
            file.isFile && file.name.startsWith("snapshot-") && file.name.endsWith(".json")
        } ?: return null

        // Sort by filename (which includes timestamp) and return the latest
        return snapshotFiles.sortedByDescending { it.name }.firstOrNull()
    }
}

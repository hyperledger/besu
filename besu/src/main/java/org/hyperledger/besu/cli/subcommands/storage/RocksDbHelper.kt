/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.cli.subcommands.storage

import org.bouncycastle.util.Arrays
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.rocksdb.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.util.function.BiConsumer
import java.util.stream.Stream

/** RocksDB subcommand helper methods.  */
object RocksDbHelper {
    private val LOG: Logger = LoggerFactory.getLogger(RocksDbHelper::class.java)

    @JvmStatic
    fun forEachColumnFamily(
        dbPath: String?, task: BiConsumer<RocksDB?, ColumnFamilyHandle?>
    ) {
        RocksDB.loadLibrary()
        val options = Options()
        options.setCreateIfMissing(true)

        // Open the RocksDB database with multiple column families
        val cfNames: List<ByteArray>
        try {
            cfNames = RocksDB.listColumnFamilies(options, dbPath)
        } catch (e: RocksDBException) {
            throw RuntimeException(e)
        }
        val cfHandles: List<ColumnFamilyHandle> = ArrayList()
        val cfDescriptors: MutableList<ColumnFamilyDescriptor> = ArrayList()
        for (cfName in cfNames) {
            cfDescriptors.add(ColumnFamilyDescriptor(cfName))
        }
        try {
            RocksDB.openReadOnly(dbPath, cfDescriptors, cfHandles).use { rocksdb ->
                for (cfHandle in cfHandles) {
                    task.accept(rocksdb, cfHandle)
                }
            }
        } catch (e: RocksDBException) {
            throw RuntimeException(e)
        } finally {
            for (cfHandle in cfHandles) {
                cfHandle.close()
            }
        }
    }

    @JvmStatic
    fun forFilteredColumnFamily(
        dbPath: String?,
        task: BiConsumer<RocksDB?, ColumnFamilyHandle?>,
        columnFamilyNameFilter: List<String?>
    ) {
        RocksDB.loadLibrary()
        val options = Options()
        options.setCreateIfMissing(true)

        // Open the RocksDB database with multiple column families
        val cfNames: List<ByteArray>
        try {
            cfNames = RocksDB.listColumnFamilies(options, dbPath)
        } catch (e: RocksDBException) {
            throw RuntimeException(e)
        }
        val cfHandles: List<ColumnFamilyHandle> = ArrayList()
        val cfDescriptors: MutableList<ColumnFamilyDescriptor> = ArrayList()
        cfDescriptors.add(ColumnFamilyDescriptor(cfNames[0])) // add default
        cfNames.stream()
            .filter { cfName: ByteArray -> columnFamilyNameFilter.contains(getNameById(cfName)) }
            .forEach { cfName: ByteArray? ->
                cfDescriptors.add(ColumnFamilyDescriptor(cfName))
            }
        try {
            RocksDB.openReadOnly(dbPath, cfDescriptors, cfHandles).use { rocksdb ->
                for (cfHandle in cfHandles) {
                    task.accept(rocksdb, cfHandle)
                }
            }
        } catch (e: RocksDBException) {
            throw RuntimeException(e)
        } finally {
            for (cfHandle in cfHandles) {
                cfHandle.close()
            }
        }
    }

    @JvmStatic
    @Throws(RocksDBException::class)
    fun printStatsForColumnFamily(
        rocksdb: RocksDB, cfHandle: ColumnFamilyHandle, out: PrintWriter
    ) {
        val size = rocksdb.getProperty(cfHandle, "rocksdb.estimate-live-data-size")
        val numberOfKeys = rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys")
        val sizeLong = size.toLong()
        val numberOfKeysLong = numberOfKeys.toLong()
        if (!size.isBlank() && !numberOfKeys.isBlank() && isPopulatedColumnFamily(sizeLong, numberOfKeysLong)) {
            out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=")
            out.println("Column Family: " + getNameById(cfHandle.name))

            val prefix = "rocksdb."
            val cfstats = "cfstats"
            val cfstats_no_file_histogram = "cfstats-no-file-histogram"
            val cf_file_histogram = "cf-file-histogram"
            val cf_write_stall_stats = "cf-write-stall-stats"
            val dbstats = "dbstats"
            val db_write_stall_stats = "db-write-stall-stats"
            val levelstats = "levelstats"
            val block_cache_entry_stats = "block-cache-entry-stats"
            val fast_block_cache_entry_stats = "fast-block-cache-entry-stats"
            val num_immutable_mem_table = "num-immutable-mem-table"
            val num_immutable_mem_table_flushed = "num-immutable-mem-table-flushed"
            val mem_table_flush_pending = "mem-table-flush-pending"
            val compaction_pending = "compaction-pending"
            val background_errors = "background-errors"
            val cur_size_active_mem_table = "cur-size-active-mem-table"
            val cur_size_all_mem_tables = "cur-size-all-mem-tables"
            val size_all_mem_tables = "size-all-mem-tables"
            val num_entries_active_mem_table = "num-entries-active-mem-table"
            val num_entries_imm_mem_tables = "num-entries-imm-mem-tables"
            val num_deletes_active_mem_table = "num-deletes-active-mem-table"
            val num_deletes_imm_mem_tables = "num-deletes-imm-mem-tables"
            val estimate_num_keys = "estimate-num-keys"
            val estimate_table_readers_mem = "estimate-table-readers-mem"
            val is_file_deletions_enabled = "is-file-deletions-enabled"
            val num_snapshots = "num-snapshots"
            val oldest_snapshot_time = "oldest-snapshot-time"
            val oldest_snapshot_sequence = "oldest-snapshot-sequence"
            val num_live_versions = "num-live-versions"
            val current_version_number = "current-super-version-number"
            val estimate_live_data_size = "estimate-live-data-size"
            val min_log_number_to_keep_str = "min-log-number-to-keep"
            val min_obsolete_sst_number_to_keep_str = "min-obsolete-sst-number-to-keep"
            val base_level_str = "base-level"
            val total_sst_files_size = "total-sst-files-size"
            val live_sst_files_size = "live-sst-files-size"
            val obsolete_sst_files_size = "obsolete-sst-files-size"
            val live_sst_files_size_at_temperature = "live-sst-files-size-at-temperature"
            val estimate_pending_comp_bytes = "estimate-pending-compaction-bytes"
            val aggregated_table_properties = "aggregated-table-properties"
            val num_running_compactions = "num-running-compactions"
            val num_running_flushes = "num-running-flushes"
            val actual_delayed_write_rate = "actual-delayed-write-rate"
            val is_write_stopped = "is-write-stopped"
            val estimate_oldest_key_time = "estimate-oldest-key-time"
            val block_cache_capacity = "block-cache-capacity"
            val block_cache_usage = "block-cache-usage"
            val block_cache_pinned_usage = "block-cache-pinned-usage"
            val options_statistics = "options-statistics"
            val num_blob_files = "num-blob-files"
            val blob_stats = "blob-stats"
            val total_blob_file_size = "total-blob-file-size"
            val live_blob_file_size = "live-blob-file-size"
            val live_blob_file_garbage_size = "live-blob-file-garbage-size"
            val blob_cache_capacity = "blob-cache-capacity"
            val blob_cache_usage = "blob-cache-usage"
            val blob_cache_pinned_usage = "blob-cache-pinned-usage"
            Stream.of(
                cfstats,
                cfstats_no_file_histogram,
                cf_file_histogram,
                cf_write_stall_stats,
                dbstats,
                db_write_stall_stats,
                levelstats,
                block_cache_entry_stats,
                fast_block_cache_entry_stats,
                num_immutable_mem_table,
                num_immutable_mem_table_flushed,
                mem_table_flush_pending,
                compaction_pending,
                background_errors,
                cur_size_active_mem_table,
                cur_size_all_mem_tables,
                size_all_mem_tables,
                num_entries_active_mem_table,
                num_entries_imm_mem_tables,
                num_deletes_active_mem_table,
                num_deletes_imm_mem_tables,
                estimate_num_keys,
                estimate_table_readers_mem,
                is_file_deletions_enabled,
                num_snapshots,
                oldest_snapshot_time,
                oldest_snapshot_sequence,
                num_live_versions,
                current_version_number,
                estimate_live_data_size,
                min_log_number_to_keep_str,
                min_obsolete_sst_number_to_keep_str,
                base_level_str,
                total_sst_files_size,
                live_sst_files_size,
                obsolete_sst_files_size,
                live_sst_files_size_at_temperature,
                estimate_pending_comp_bytes,
                aggregated_table_properties,
                num_running_compactions,
                num_running_flushes,
                actual_delayed_write_rate,
                is_write_stopped,
                estimate_oldest_key_time,
                block_cache_capacity,
                block_cache_usage,
                block_cache_pinned_usage,
                options_statistics,
                num_blob_files,
                blob_stats,
                total_blob_file_size,
                live_blob_file_size,
                live_blob_file_garbage_size,
                blob_cache_capacity,
                blob_cache_usage,
                blob_cache_pinned_usage
            )
                .forEach { prop: String ->
                    try {
                        val value = rocksdb.getProperty(cfHandle, prefix + prop)
                        if (!value.isBlank()) {
                            out.println("$prop: $value")
                        }
                    } catch (e: RocksDBException) {
                        LOG.debug("couldn't get property {}", prop)
                    }
                }
            out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=")
        }
    }

    @JvmStatic
    @Throws(RocksDBException::class, NumberFormatException::class)
    fun getAndPrintUsageForColumnFamily(
        rocksdb: RocksDB, cfHandle: ColumnFamilyHandle, out: PrintWriter
    ): ColumnFamilyUsage {
        val numberOfKeys = rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys")
        if (!numberOfKeys.isBlank()) {
            try {
                val numberOfKeysLong = numberOfKeys.toLong()
                val totalSstFilesSize =
                    rocksdb.getProperty(cfHandle, "rocksdb.total-sst-files-size")
                val totalSstFilesSizeLong =
                    if (!totalSstFilesSize.isBlank()) totalSstFilesSize.toLong() else 0

                val totalBlobFilesSize =
                    rocksdb.getProperty(cfHandle, "rocksdb.total-blob-file-size")
                val totalBlobFilesSizeLong =
                    if (!totalBlobFilesSize.isBlank()) totalBlobFilesSize.toLong() else 0

                val totalFilesSize = totalSstFilesSizeLong + totalBlobFilesSizeLong
                if (isPopulatedColumnFamily(0, numberOfKeysLong)) {
                    printLine(
                        out,
                        getNameById(cfHandle.name),
                        rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys"),
                        formatOutputSize(totalFilesSize),
                        formatOutputSize(totalSstFilesSizeLong),
                        formatOutputSize(totalBlobFilesSizeLong)
                    )
                }
                return ColumnFamilyUsage(
                    getNameById(cfHandle.name),
                    numberOfKeysLong,
                    totalFilesSize,
                    totalSstFilesSizeLong,
                    totalBlobFilesSizeLong
                )
            } catch (e: NumberFormatException) {
                LOG.error("Failed to parse string into long: " + e.message)
            }
        }
        // return empty usage on error
        return ColumnFamilyUsage(getNameById(cfHandle.name), 0, 0, 0, 0)
    }

    @JvmStatic
    fun printTotals(out: PrintWriter, columnFamilyUsages: List<ColumnFamilyUsage?>) {
        val totalKeys = columnFamilyUsages.stream().mapToLong { it?.keys ?: 0 }.sum()
        val totalSize =
            columnFamilyUsages.stream().mapToLong { it?.totalSize ?: 0 }.sum()
        val totalSsts =
            columnFamilyUsages.stream().mapToLong { it?.sstFilesSize ?: 0 }.sum()
        val totalBlobs =
            columnFamilyUsages.stream().mapToLong { it?.blobFilesSize ?: 0 }.sum()
        printSeparator(out)
        printLine(
            out,
            "ESTIMATED TOTAL",
            totalKeys.toString(),
            formatOutputSize(totalSize),
            formatOutputSize(totalSsts),
            formatOutputSize(totalBlobs)
        )
        printSeparator(out)
    }

    private fun isPopulatedColumnFamily(size: Long, numberOfKeys: Long): Boolean {
        return size != 0L || numberOfKeys != 0L
    }

    @JvmStatic
    fun formatOutputSize(size: Long): String {
        if (size > (1024 * 1024 * 1024)) {
            val sizeInGiB = size / (1024 * 1024 * 1024)
            return "$sizeInGiB GiB"
        } else if (size > (1024 * 1024)) {
            val sizeInMiB = size / (1024 * 1024)
            return "$sizeInMiB MiB"
        } else if (size > 1024) {
            val sizeInKiB = size / 1024
            return "$sizeInKiB KiB"
        } else {
            return "$size B"
        }
    }

    private fun getNameById(id: ByteArray): String? {
        for (segment in KeyValueSegmentIdentifier.entries) {
            if (Arrays.areEqual(segment.id, id)) {
                return segment.getName()
            }
        }
        return null // id not found
    }

    @JvmStatic
    fun printTableHeader(out: PrintWriter) {
        printSeparator(out)
        out.format(
            "| Column Family                  | Keys            | Total Size  | SST Files Size  | Blob Files Size  | \n"
        )
        printSeparator(out)
    }

    private fun printSeparator(out: PrintWriter) {
        out.format(
            "|--------------------------------|-----------------|-------------|-----------------|------------------|\n"
        )
    }

    fun printLine(
        out: PrintWriter,
        cfName: String?,
        keys: String?,
        totalFilesSize: String?,
        sstFilesSize: String?,
        blobFilesSize: String?
    ) {
        val format = "| %-30s | %-15s | %-11s | %-15s | %-16s |\n"
        out.format(format, cfName, keys, totalFilesSize, sstFilesSize, blobFilesSize)
    }

    @JvmRecord
    data class ColumnFamilyUsage(
        val name: String?,
        val keys: Long,
        val totalSize: Long,
        val sstFilesSize: Long,
        val blobFilesSize: Long
    )
}

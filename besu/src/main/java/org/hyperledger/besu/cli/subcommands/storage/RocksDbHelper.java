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

package org.hyperledger.besu.cli.subcommands.storage;

import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.bouncycastle.util.Arrays;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RocksDB subcommand helper methods. */
public class RocksDbHelper {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDbHelper.class);

  static void forEachColumnFamily(
      final String dbPath, final BiConsumer<RocksDB, ColumnFamilyHandle> task) {
    RocksDB.loadLibrary();
    Options options = new Options();
    options.setCreateIfMissing(true);

    // Open the RocksDB database with multiple column families
    List<byte[]> cfNames;
    try {
      cfNames = RocksDB.listColumnFamilies(options, dbPath);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
    final List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
    final List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
    for (byte[] cfName : cfNames) {
      cfDescriptors.add(new ColumnFamilyDescriptor(cfName));
    }
    try (final RocksDB rocksdb = RocksDB.openReadOnly(dbPath, cfDescriptors, cfHandles)) {
      for (ColumnFamilyHandle cfHandle : cfHandles) {
        task.accept(rocksdb, cfHandle);
      }
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    } finally {
      for (ColumnFamilyHandle cfHandle : cfHandles) {
        cfHandle.close();
      }
    }
  }

  static void printStatsForColumnFamily(
      final RocksDB rocksdb, final ColumnFamilyHandle cfHandle, final PrintWriter out)
      throws RocksDBException {
    final String size = rocksdb.getProperty(cfHandle, "rocksdb.estimate-live-data-size");
    final String numberOfKeys = rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys");
    final long sizeLong = Long.parseLong(size);
    final long numberOfKeysLong = Long.parseLong(numberOfKeys);
    if (!size.isBlank()
        && !numberOfKeys.isBlank()
        && !isEmptyColumnFamily(sizeLong, numberOfKeysLong)) {
      out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
      out.println("Column Family: " + getNameById(cfHandle.getName()));
      out.println(rocksdb.getProperty(cfHandle, "rocksdb.stats"));
      out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
    }
  }

  static void printUsageForColumnFamily(
      final RocksDB rocksdb, final ColumnFamilyHandle cfHandle, final PrintWriter out)
      throws RocksDBException, NumberFormatException {
    final String size = rocksdb.getProperty(cfHandle, "rocksdb.estimate-live-data-size");
    final String numberOfKeys = rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys");
    if (!size.isBlank() && !numberOfKeys.isBlank()) {
      try {
        final long sizeLong = Long.parseLong(size);
        final long numberOfKeysLong = Long.parseLong(numberOfKeys);
        final String totalSstFilesSize =
            rocksdb.getProperty(cfHandle, "rocksdb.total-sst-files-size");
        final long totalSstFilesSizeLong =
            !totalSstFilesSize.isBlank() ? Long.parseLong(totalSstFilesSize) : 0;

        final String totalBlobFilesSize =
            rocksdb.getProperty(cfHandle, "rocksdb.total-blob-file-size");
        final long totalBlobFilesSizeLong =
            !totalBlobFilesSize.isBlank() ? Long.parseLong(totalBlobFilesSize) : 0;

        if (!isEmptyColumnFamily(sizeLong, numberOfKeysLong)) {
          printLine(
              out,
              getNameById(cfHandle.getName()),
              rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys"),
              formatOutputSize(sizeLong),
              formatOutputSize(totalSstFilesSizeLong),
              formatOutputSize(totalBlobFilesSizeLong));
        }
      } catch (NumberFormatException e) {
        LOG.error("Failed to parse string into long: " + e.getMessage());
      }
    }
  }

  private static boolean isEmptyColumnFamily(final long size, final long numberOfKeys) {
    return size == 0 && numberOfKeys == 0;
  }

  static String formatOutputSize(final long size) {
    if (size > (1024 * 1024 * 1024)) {
      long sizeInGiB = size / (1024 * 1024 * 1024);
      return sizeInGiB + " GiB";
    } else if (size > (1024 * 1024)) {
      long sizeInMiB = size / (1024 * 1024);
      return sizeInMiB + " MiB";
    } else if (size > 1024) {
      long sizeInKiB = size / 1024;
      return sizeInKiB + " KiB";
    } else {
      return size + " B";
    }
  }

  private static String getNameById(final byte[] id) {
    for (KeyValueSegmentIdentifier segment : KeyValueSegmentIdentifier.values()) {
      if (Arrays.areEqual(segment.getId(), id)) {
        return segment.getName();
      }
    }
    return null; // id not found
  }

  static void printTableHeader(final PrintWriter out) {
    out.format(
        "| Column Family                  | Keys            | Column Size  | SST Files Size  | Blob Files Size  | \n");
    out.format(
        "|--------------------------------|-----------------|--------------|-----------------|------------------|\n");
  }

  static void printLine(
      final PrintWriter out,
      final String cfName,
      final String keys,
      final String columnSize,
      final String sstFilesSize,
      final String blobFilesSize) {
    final String format = "| %-30s | %-15s | %-12s | %-15s | %-16s |\n";
    out.format(format, cfName, keys, columnSize, sstFilesSize, blobFilesSize);
  }
}

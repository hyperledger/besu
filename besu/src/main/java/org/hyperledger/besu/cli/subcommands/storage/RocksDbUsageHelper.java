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

import org.bouncycastle.util.Arrays;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RocksDB Usage subcommand helper methods for formatting and printing. */
public class RocksDbUsageHelper {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDbUsageHelper.class);

  static void printUsageForColumnFamily(
      final RocksDB rocksdb, final ColumnFamilyHandle cfHandle, final PrintWriter out)
      throws RocksDBException, NumberFormatException {
    final String size = rocksdb.getProperty(cfHandle, "rocksdb.estimate-live-data-size");
    final String numberOfKeys = rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys");
    boolean emptyColumnFamily = false;
    if (!size.isEmpty() && !size.isBlank() && !numberOfKeys.isEmpty() && !numberOfKeys.isBlank()) {
      try {
        final long sizeLong = Long.parseLong(size);
        final long numberOfKeysLong = Long.parseLong(numberOfKeys);
        final String totalSstFilesSize =
            rocksdb.getProperty(cfHandle, "rocksdb.total-sst-files-size");
        final long totalSstFilesSizeLong =
            !totalSstFilesSize.isEmpty() && !totalSstFilesSize.isBlank()
                ? Long.parseLong(totalSstFilesSize)
                : 0;
        if (sizeLong == 0 && numberOfKeysLong == 0) {
          emptyColumnFamily = true;
        }

        if (!emptyColumnFamily) {
          printLine(
              out,
              getNameById(cfHandle.getName()),
              rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys"),
              formatOutputSize(sizeLong),
              formatOutputSize(totalSstFilesSizeLong));
        }
      } catch (NumberFormatException e) {
        LOG.error("Failed to parse string into long: " + e.getMessage());
      }
    }
  }

  private static String formatOutputSize(final long size) {
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
        "| Column Family                  | Keys            | Column Size  | SST Files Size  |\n");
    out.format(
        "|--------------------------------|-----------------|--------------|-----------------|\n");
  }

  static void printLine(
      final PrintWriter out,
      final String cfName,
      final String keys,
      final String columnSize,
      final String sstFilesSize) {
    final String format = "| %-30s | %-15s | %-12s | %-15s |\n";
    out.format(format, cfName, keys, columnSize, sstFilesSize);
  }
}

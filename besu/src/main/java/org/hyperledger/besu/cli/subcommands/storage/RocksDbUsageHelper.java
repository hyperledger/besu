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

/** RocksDB Usage subcommand helper methods for formatting and printing. */
public class RocksDbUsageHelper {

  static void printUsageForColumnFamily(
      final RocksDB rocksdb, final ColumnFamilyHandle cfHandle, final PrintWriter out)
      throws RocksDBException {
    final String size = rocksdb.getProperty(cfHandle, "rocksdb.estimate-live-data-size");
    boolean emptyColumnFamily = false;
    if (!size.isEmpty() && !size.isBlank()) {
      final long sizeLong = Long.parseLong(size);
      if (sizeLong == 0) {
        emptyColumnFamily = true;
      }
      String totolSstFilesSize = rocksdb.getProperty(cfHandle, "rocksdb.total-sst-files-size");
      if (!emptyColumnFamily) {
        printLine(
            out,
            getNameById(cfHandle.getName()),
            rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys"),
            formatOutputSize(sizeLong),
            formatOutputSize(
                !totolSstFilesSize.isEmpty() && !totolSstFilesSize.isBlank()
                    ? Long.parseLong(rocksdb.getProperty(cfHandle, "rocksdb.total-sst-files-size"))
                    : 0));
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

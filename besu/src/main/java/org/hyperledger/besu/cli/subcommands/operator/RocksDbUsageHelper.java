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

package org.hyperledger.besu.cli.subcommands.operator;

import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;

import java.io.PrintWriter;

import org.bouncycastle.util.Arrays;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyMetaData;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public class RocksDbUsageHelper {

  static void printUsageForColumnFamily(
      final RocksDB rocksdb, final ColumnFamilyHandle cfHandle, final PrintWriter out)
      throws RocksDBException {
    String size = rocksdb.getProperty(cfHandle, "rocksdb.estimate-live-data-size");
    boolean emptyColumnFamily = false;
    if (!size.isEmpty() && !size.isBlank()) {
      long sizeLong = Long.parseLong(size);
      if (sizeLong == 0) emptyColumnFamily = true;
      if (!emptyColumnFamily) {
        out.println(
            "****** Column family '"
                + getNameById(cfHandle.getName())
                + "' size: "
                + formatOutputSize(sizeLong)
                + " ******");
        // System.out.println("SST table : "+ rocksdb.getProperty(cfHandle,
        // "rocksdb.sstables"));

        out.println(
            "Number of live snapshots : " + rocksdb.getProperty(cfHandle, "rocksdb.num-snapshots"));
        out.println(
            "Number of keys : " + rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys"));

        String totolSstFilesSize = rocksdb.getProperty(cfHandle, "rocksdb.total-sst-files-size");
        if (!totolSstFilesSize.isEmpty() && !totolSstFilesSize.isBlank()) {
          out.println(
              "Total size of SST Files : " + formatOutputSize(Long.parseLong(totolSstFilesSize)));
        }
        String liveSstFilesSize = rocksdb.getProperty(cfHandle, "rocksdb.live-sst-files-size");
        if (!liveSstFilesSize.isEmpty() && !liveSstFilesSize.isBlank()) {
          out.println(
              "Size of live SST Filess : " + formatOutputSize(Long.parseLong(liveSstFilesSize)));
        }

        ColumnFamilyMetaData columnFamilyMetaData = rocksdb.getColumnFamilyMetaData(cfHandle);
        long sizeBytes = columnFamilyMetaData.size();
        out.println(
            "Column family size (with getColumnFamilyMetaData) : " + formatOutputSize(sizeBytes));
        out.println("");
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

  public static String getNameById(final byte[] id) {
    for (KeyValueSegmentIdentifier segment : KeyValueSegmentIdentifier.values()) {
      if (Arrays.areEqual(segment.getId(), id)) {
        return segment.getName();
      }
    }
    return null; // id not found
  }
}

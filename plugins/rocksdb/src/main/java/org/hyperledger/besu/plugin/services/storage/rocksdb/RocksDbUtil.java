/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import org.hyperledger.besu.util.InvalidConfigurationException;

import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The RocksDb util. */
public class RocksDbUtil {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDbUtil.class);

  private RocksDbUtil() {}

  /** Load native library. */
  public static void loadNativeLibrary() {
    try {
      RocksDB.loadLibrary();
    } catch (final ExceptionInInitializerError e) {
      if (e.getCause() instanceof UnsupportedOperationException) {
        LOG.info("Unable to load RocksDB library", e);
        throw new InvalidConfigurationException(
            "Unsupported platform detected. On Windows, ensure you have 64bit Java installed.");
      } else {
        throw e;
      }
    }
  }
}

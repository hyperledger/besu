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
package org.hyperledger.besu.util;

import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Ephemery Database Reset utility. Handles database reset functionality for the Ephemery
 * network based on the predefined period.
 */
public class EphemeryDatabaseReset {
  private static final Logger LOG = LoggerFactory.getLogger(EphemeryDatabaseReset.class);
  private static final int PERIOD_IN_DAYS = 28;

  /** Private constructor to prevent instantiation. */
  private EphemeryDatabaseReset() {
    // Private constructor to prevent instantiation
  }

  /**
   * Checks if the database needs to be reset based on the genesis timestamp and current time.
   *
   * @param genesisTimestamp The genesis block timestamp
   * @return true if database reset is needed, false otherwise
   */
  public static boolean isResetNeeded(final long genesisTimestamp) {
    long currentTimestamp = Instant.now().getEpochSecond();
    long periodInSeconds = PERIOD_IN_DAYS * 24 * 60 * 60;
    return (currentTimestamp - genesisTimestamp) >= periodInSeconds;
  }

  /**
   * Resets the database for Ephemery network.
   *
   * @param worldStateStorage The world state storage to reset
   */
  public static void resetDatabase(final PathBasedWorldStateKeyValueStorage worldStateStorage) {
    try {
      // Clear the flat database
      worldStateStorage.clearFlatDatabase();
      LOG.info("Cleared flat database");

      // Clear the trie log
      worldStateStorage.clearTrieLog();
      LOG.info("Cleared trie log");

      // Clear the trie
      worldStateStorage.clearTrie();
      LOG.info("Cleared trie");

      // If using Bonsai storage, upgrade to full flat database mode
      if (worldStateStorage instanceof BonsaiWorldStateKeyValueStorage bonsaiStorage) {
        bonsaiStorage.upgradeToFullFlatDbMode();
        LOG.info("Upgraded to full flat database mode");
      }
    } catch (Exception e) {
      LOG.error("Failed to reset database: {}", e.getMessage());
      throw new RuntimeException("Failed to reset database", e);
    }
  }
}

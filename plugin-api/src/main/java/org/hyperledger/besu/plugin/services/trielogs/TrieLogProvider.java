/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.plugin.services.trielogs;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.TrieLog;

import java.util.Optional;

/** Trielog provider interface for a given block hash. */
@FunctionalInterface
public interface TrieLogProvider {
  /**
   * Returns the TrieLog layer for the given block hash.
   *
   * @param blockHash the block hash
   * @return the TrieLog layer for the given block hash
   * @param <T> the type of the TrieLog
   */
  <T extends TrieLog.LogTuple<?>> Optional<TrieLog> getTrieLogLayer(final Hash blockHash);
}

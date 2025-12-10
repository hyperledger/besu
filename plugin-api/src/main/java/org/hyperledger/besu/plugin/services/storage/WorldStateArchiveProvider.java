/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugin.services.storage;

import org.hyperledger.besu.evm.internal.EvmConfiguration;

/**
 * Provider interface for world state archive implementations. Plugins can implement this to provide
 * alternative storage formats.
 */
public interface WorldStateArchiveProvider {

  /**
   * Creates a world state archive instance.
   *
   * @param worldStateKeyValueStorage the world state key value storage
   * @param preimageStorage the preimage storage
   * @param evmConfiguration the EVM configuration
   * @return a world state archive instance
   */
  WorldStateArchive create(
      WorldStateKeyValueStorage worldStateKeyValueStorage,
      WorldStatePreimageStorage preimageStorage,
      EvmConfiguration evmConfiguration);

  /**
   * Returns the data storage format this provider supports.
   *
   * @return the supported data storage format
   */
  DataStorageFormat getDataStorageFormat();
}

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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration.DiffBasedUnstable;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import org.immutables.value.Value;

@Value.Immutable
@Value.Enclosing
public interface DataStorageConfiguration {

  boolean DEFAULT_RECEIPT_COMPACTION_ENABLED = true;

  DataStorageConfiguration DEFAULT_CONFIG =
      ImmutableDataStorageConfiguration.builder()
          .dataStorageFormat(DataStorageFormat.BONSAI)
          .diffBasedSubStorageConfiguration(DiffBasedSubStorageConfiguration.DEFAULT)
          .build();

  DataStorageConfiguration DEFAULT_BONSAI_CONFIG = DEFAULT_CONFIG;

  DataStorageConfiguration DEFAULT_BONSAI_PARTIAL_DB_CONFIG =
      ImmutableDataStorageConfiguration.builder()
          .dataStorageFormat(DataStorageFormat.BONSAI)
          .diffBasedSubStorageConfiguration(
              ImmutableDiffBasedSubStorageConfiguration.builder()
                  .unstable(DiffBasedUnstable.PARTIAL_MODE)
                  .build())
          .build();

  DataStorageConfiguration DEFAULT_FOREST_CONFIG =
      ImmutableDataStorageConfiguration.builder()
          .dataStorageFormat(DataStorageFormat.FOREST)
          .diffBasedSubStorageConfiguration(DiffBasedSubStorageConfiguration.DISABLED)
          .build();

  DataStorageFormat getDataStorageFormat();

  @Value.Default
  default DiffBasedSubStorageConfiguration getDiffBasedSubStorageConfiguration() {
    return DiffBasedSubStorageConfiguration.DEFAULT;
  }

  @Value.Default
  default boolean getReceiptCompactionEnabled() {
    return DEFAULT_RECEIPT_COMPACTION_ENABLED;
  }
}

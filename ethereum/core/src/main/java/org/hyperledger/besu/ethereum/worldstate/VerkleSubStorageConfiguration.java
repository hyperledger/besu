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

import org.immutables.value.Value;

@Value.Immutable
@Value.Enclosing
public interface VerkleSubStorageConfiguration {

  VerkleSubStorageConfiguration DEFAULT = ImmutableVerkleSubStorageConfiguration.builder().build();

  @Value.Default
  default VerkleUnstable getUnstable() {
    return VerkleUnstable.DISABLED;
  }

  @Value.Immutable
  interface VerkleUnstable {

    VerkleSubStorageConfiguration.VerkleUnstable STEM_MODE =
        ImmutableVerkleSubStorageConfiguration.VerkleUnstable.builder()
            .stemFlatDbEnabled(true)
            .build();

    VerkleSubStorageConfiguration.VerkleUnstable DISABLED =
        ImmutableVerkleSubStorageConfiguration.VerkleUnstable.builder()
            .stemFlatDbEnabled(false)
            .build();

    boolean DEFAULT_STEM_FLAT_DB_ENABLED = false;

    @Value.Default
    default boolean getStemFlatDbEnabled() {
      return DEFAULT_STEM_FLAT_DB_ENABLED;
    }
  }
}

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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

public enum BonsaiSegmentIdentifier implements SegmentIdentifier {
  BLOCKCHAIN((byte) 1),
  //  WORLD_STATE((byte) 2),
  PRIVATE_TRANSACTIONS((byte) 3),
  PRIVATE_STATE((byte) 4),
  PRUNING_STATE((byte) 5),
  ACCOUNT_INFO_STATE((byte) 6),
  CODE_STORAGE((byte) 7),
  ACCOUNT_STORAGE_STORAGE((byte) 8),
  TRIE_BRANCH_STORAGE((byte) 9);

  private final byte[] id;

  BonsaiSegmentIdentifier(final byte... id) {
    this.id = id;
  }

  @Override
  public String getName() {
    return name();
  }

  @Override
  public byte[] getId() {
    return id;
  }
}

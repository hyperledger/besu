/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.trie.bonsai.BonsaiValue;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiPreImageProxy;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldView;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import org.apache.tuweni.bytes.Bytes;

public class BonsaiReferenceTestUpdateAccumulator extends BonsaiWorldStateUpdateAccumulator {
  private final BonsaiPreImageProxy preImageProxy;

  public BonsaiReferenceTestUpdateAccumulator(
      final BonsaiWorldView world,
      final Consumer<BonsaiValue<BonsaiAccount>> accountPreloader,
      final Consumer<StorageSlotKey> storagePreloader,
      final BonsaiPreImageProxy preImageProxy,
      final EvmConfiguration evmConfiguration) {
    super(world, accountPreloader, storagePreloader, evmConfiguration);
    this.preImageProxy = preImageProxy;
  }

  @Override
  protected Hash hashAndSavePreImage(final Bytes bytes) {
    // by default do not save hash preImages
    return preImageProxy.hashAndSavePreImage(bytes);
  }
}

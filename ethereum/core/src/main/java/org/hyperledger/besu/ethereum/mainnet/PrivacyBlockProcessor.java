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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;

import java.util.List;

public class PrivacyBlockProcessor implements BlockProcessor {
  private final BlockProcessor blockProcessor;
  private final PrivateStateStorage privateStateStorage;

  public PrivacyBlockProcessor(
      final BlockProcessor blockProcessor, final PrivateStateStorage privateStateStorage) {
    this.blockProcessor = blockProcessor;
    this.privateStateStorage = privateStateStorage;
  }

  @Override
  public Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        new PrivacyGroupHeadBlockMap(
            privateStateStorage
                .getPrivacyGroupHeadBlockMap(blockHeader.getParentHash())
                .orElse(PrivacyGroupHeadBlockMap.EMPTY));
    privateStateStorage
        .updater()
        .putPrivacyGroupHeadBlockMap(blockHeader.getHash(), privacyGroupHeadBlockMap)
        .commit();
    return blockProcessor.processBlock(blockchain, worldState, blockHeader, transactions, ommers);
  }
}

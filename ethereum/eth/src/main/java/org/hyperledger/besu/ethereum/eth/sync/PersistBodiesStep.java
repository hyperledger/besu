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
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.List;
import java.util.function.Function;

/** Persists block bodies as soon as they are downloaded. Idempotent: skips if already present. */
public class PersistBodiesStep implements Function<List<Block>, List<Block>> {

  private final MutableBlockchain blockchain;

  public PersistBodiesStep(final ProtocolContext protocolContext) {
    this.blockchain = protocolContext.getBlockchain();
  }

  @Override
  public List<Block> apply(final List<Block> blocks) {
    if (blocks == null || blocks.isEmpty()) {
      return blocks;
    }

    for (final Block block : blocks) {
      final Hash hash = block.getHash();
      if (blockchain.getBlockBody(hash).isEmpty()) {
        // Persist body via storage updater to avoid adding new public methods
        final var updater =
            ((org.hyperledger.besu.ethereum.chain.DefaultBlockchain) blockchain)
                .getBlockchainStorage()
                .updater();
        updater.putBlockBody(hash, block.getBody());
        updater.commit();
      }
    }
    return blocks;
  }
}

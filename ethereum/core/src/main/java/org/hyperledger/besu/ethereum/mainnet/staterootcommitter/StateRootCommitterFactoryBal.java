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
package org.hyperledger.besu.ethereum.mainnet.staterootcommitter;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.trie.forest.ForestWorldStateArchive;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BlockAccessListStateRootHashCalculator;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class StateRootCommitterFactoryBal implements StateRootCommitterFactory {

  private final boolean trustBalRoot;
  private final Duration balRootTimeout;

  public StateRootCommitterFactoryBal(final boolean trustBalRoot, final Duration balRootTimeout) {
    this.trustBalRoot = trustBalRoot;
    this.balRootTimeout = balRootTimeout;
  }

  @Override
  public StateRootCommitter forBlock(
      final ProtocolContext protocolContext,
      final BlockHeader blockHeader,
      final Optional<BlockAccessList> maybeBal) {
    if (maybeBal.isEmpty()) {
      throw new IllegalStateException(
          "No BAL present in the block, falling back to synchronous state root computation");
    }

    // This is temporary workaround to not launch state root pre-computation in Forest mode
    if (protocolContext.getWorldStateArchive() instanceof ForestWorldStateArchive) {
      return new StateRootCommitterImplSync();
    }

    final CompletableFuture<Hash> balRootFuture =
        BlockAccessListStateRootHashCalculator.computeStateRootFromBlockAccessListAsync(
            protocolContext, blockHeader, maybeBal.get());
    return new StateRootCommitterImplBal(balRootFuture, trustBalRoot, balRootTimeout);
  }
}

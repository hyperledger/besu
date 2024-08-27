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
package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class VerkleTransitionWorldStateProvider implements WorldStateArchive {

  private final BonsaiWorldStateProvider bonsaiWorldStateProvider;
  private final VerkleWorldStateProvider verkleWorldStateProvider;
  private final VerkleTransitionContext transitionContext;
  private final Blockchain blockchain;

  public VerkleTransitionWorldStateProvider(
      final Blockchain blockchain,
      final BonsaiWorldStateProvider bonsaiWorldStateProvider,
      final VerkleWorldStateProvider verkleWorldStateProvider,
      final VerkleTransitionContext verkleContext) {
    this.blockchain = blockchain;
    this.bonsaiWorldStateProvider = bonsaiWorldStateProvider;
    this.verkleWorldStateProvider = verkleWorldStateProvider;
    this.transitionContext = verkleContext;
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    // TODO: use verkle context to determine which to use
    return bonsaiWorldStateProvider.get(rootHash, blockHash);
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    // TODO: use verkle context to determine which to use
    return bonsaiWorldStateProvider.isWorldStateAvailable(rootHash, blockHash);
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final BlockHeader blockHeader, final boolean isPersistingState) {
    if (transitionContext.isVerkleForMutation(blockHeader)) {
      return verkleWorldStateProvider.getMutable(blockHeader, isPersistingState);
    }
    return bonsaiWorldStateProvider.getMutable(blockHeader, isPersistingState);
  }

  @Override
  public Optional<MutableWorldState> getMutable(final Hash rootHash, final Hash blockHash) {
    return blockchain
        .getBlockHeader(blockHash)
        .map(transitionContext::isVerkleForMutation)
        .flatMap(
            isVerkle -> {
              if (isVerkle) {
                return verkleWorldStateProvider.getMutable(rootHash, blockHash);
              } else {
                return bonsaiWorldStateProvider.getMutable(rootHash, blockHash);
              }
            });
  }

  @Override
  public MutableWorldState getMutable() {
    // rely on the verkle context to decide which mutable to get. 😬
    if (transitionContext.isBeforeTransition()) {
      return bonsaiWorldStateProvider.getMutable();
    } else {
      return verkleWorldStateProvider.getMutable();
    }
  }

  @Override
  public void resetArchiveStateTo(final BlockHeader blockHeader) {
    // TODO: write me (maybe?)
  }

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    // TODO: bonsai does not implement this, presumably verkle will not either
    return Optional.empty();
  }

  @Override
  public <U> Optional<U> getAccountProof(
      final BlockHeader blockHeader,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys,
      final Function<Optional<WorldStateProof>, ? extends Optional<U>> mapper) {
    if (transitionContext.isBeforeTransition(blockHeader.getTimestamp())) {
      return bonsaiWorldStateProvider.getAccountProof(
          blockHeader, accountAddress, accountStorageKeys, mapper);
    } else {
      // TODO: will we ever serve eth_getProof from verkle?  that is the only use for this
      return Optional.empty();
    }
  }

  @Override
  public void close() throws IOException {
    try {
      bonsaiWorldStateProvider.close();
      verkleWorldStateProvider.close();
    } catch (Exception e) {
      // no op
    }
  }
}

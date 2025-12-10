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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.NonceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.StorageChange;
import org.hyperledger.besu.ethereum.mainnet.staterootcommitter.BalRootComputation;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.account.MutableAccount;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.units.bigints.UInt256;

@SuppressWarnings("rawtypes")
public class BlockAccessListStateRootHashCalculator {

  private BlockAccessListStateRootHashCalculator() {}

  private static BonsaiWorldState prepareWorldState(
      final ProtocolContext protocolContext, final BlockHeader blockHeader) {
    final Hash parentHash = blockHeader.getParentHash();
    final Optional<BlockHeader> maybeParentHeader =
        protocolContext.getBlockchain().getBlockHeader(parentHash);
    if (maybeParentHeader.isEmpty()) {
      throw new IllegalStateException(
          String.format("Parent %s of block %s not found", parentHash, blockHeader.getHash()));
    }
    final BonsaiWorldState ws =
        (BonsaiWorldState)
            protocolContext
                .getWorldStateArchive()
                .getWorldState(
                    WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead(
                        maybeParentHeader.get()))
                .orElseThrow();
    ws.disableCacheMerkleTrieLoader();
    return ws;
  }

  private static BalRootComputation accumulateAccessListAndComputeRoot(
      final PathBasedWorldState worldState, final BlockAccessList blockAccessList) {

    final PathBasedWorldStateUpdateAccumulator accumulator = worldState.getAccumulator();
    PathBasedWorldStateKeyValueStorage.Updater worldStateKeyValueStorageUpdater =
        worldState.getWorldStateStorage().updater();

    for (AccountChanges accountChanges : blockAccessList.accountChanges()) {
      final Address address = accountChanges.address();

      final List<BalanceChange> balanceChanges = accountChanges.balanceChanges();
      final List<NonceChange> nonceChanges = accountChanges.nonceChanges();
      final List<CodeChange> codeChanges = accountChanges.codeChanges();
      final List<SlotChanges> storageChanges = accountChanges.storageChanges();

      final boolean anyChange =
          !balanceChanges.isEmpty()
              || !nonceChanges.isEmpty()
              || !codeChanges.isEmpty()
              || !storageChanges.isEmpty();

      if (!anyChange) {
        continue;
      }

      final MutableAccount account = accumulator.getOrCreate(address);

      if (!balanceChanges.isEmpty()) {
        final BalanceChange change = balanceChanges.get(balanceChanges.size() - 1);
        account.setBalance(Wei.wrap(change.postBalance()));
      }

      if (!nonceChanges.isEmpty()) {
        final NonceChange change = nonceChanges.get(nonceChanges.size() - 1);
        account.setNonce(change.newNonce());
      }

      if (!codeChanges.isEmpty()) {
        final CodeChange change = codeChanges.get(codeChanges.size() - 1);
        account.setCode(change.newCode());
      }

      for (SlotChanges slotChanges : storageChanges) {
        final List<StorageChange> changes = slotChanges.changes();
        if (!changes.isEmpty()) {
          final StorageChange change = changes.get(changes.size() - 1);
          final Optional<UInt256> maybeKey = slotChanges.slot().getSlotKey();
          if (maybeKey.isPresent()) {
            final UInt256 key = maybeKey.get();
            final UInt256 value = change.newValue();
            account.setStorageValue(key, value == null ? UInt256.ZERO : value);
          }
        }
      }
    }

    accumulator.clearAccountsThatAreEmpty();
    accumulator.commit();
    final Hash root =
        worldState.calculateRootHash(Optional.of(worldStateKeyValueStorageUpdater), accumulator);
    worldStateKeyValueStorageUpdater.commit();
    return new BalRootComputation(root, accumulator);
  }

  public static CompletableFuture<BalRootComputation> computeAsync(
      final ProtocolContext protocolContext,
      final BlockHeader blockHeader,
      final BlockAccessList bal) {
    return CompletableFuture.supplyAsync(
        () -> {
          try (BonsaiWorldState ws = prepareWorldState(protocolContext, blockHeader)) {
            return accumulateAccessListAndComputeRoot(ws, bal);
          }
        });
  }
}

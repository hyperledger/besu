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
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.evm.account.MutableAccount;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.units.bigints.UInt256;

public class BlockAccessListStateRootHashCalculator {

  private BlockAccessListStateRootHashCalculator() {}

  private static BonsaiWorldState prepareWorldState(
      final ProtocolContext protocolContext, final BlockHeader blockHeader) {
    final BlockHeader chainHeadHeader = protocolContext.getBlockchain().getChainHeadHeader();
    if (!chainHeadHeader.getHash().equals(blockHeader.getParentHash())) {
      throw new IllegalStateException("Chain head is not the parent of the processed block");
    }
    final BonsaiWorldState ws =
        (BonsaiWorldState)
            protocolContext
                .getWorldStateArchive()
                .getWorldState(
                    WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead(chainHeadHeader))
                .orElseThrow();
    ws.disableCacheMerkleTrieLoader();
    return ws;
  }

  private static Hash accumulateAccessListAndComputeRoot(
      final BonsaiWorldState worldState, final BlockAccessList blockAccessList) {
    final BonsaiWorldStateUpdateAccumulator accumulator =
        (BonsaiWorldStateUpdateAccumulator) worldState.getAccumulator();

    for (AccountChanges accountChanges : blockAccessList.accountChanges()) {
      final Address address = accountChanges.address();
      final MutableAccount account = accumulator.getOrCreate(address);

      final List<BalanceChange> balanceChanges = accountChanges.balanceChanges();
      if (!balanceChanges.isEmpty()) {
        final BalanceChange change = balanceChanges.get(balanceChanges.size() - 1);
        account.setBalance(Wei.wrap(change.postBalance()));
      }

      final List<NonceChange> nonceChanges = accountChanges.nonceChanges();
      if (!nonceChanges.isEmpty()) {
        final NonceChange change = nonceChanges.get(nonceChanges.size() - 1);
        account.setNonce(change.newNonce());
      }

      final List<CodeChange> codeChanges = accountChanges.codeChanges();
      if (!codeChanges.isEmpty()) {
        final CodeChange change = codeChanges.get(codeChanges.size() - 1);
        account.setCode(change.newCode());
      }

      for (SlotChanges slotChanges : accountChanges.storageChanges()) {
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

    accumulator.commit();
    return worldState.calculateRootHash(Optional.empty(), accumulator);
  }

  public static CompletableFuture<Hash> computeStateRootFromBlockAccessListAsync(
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

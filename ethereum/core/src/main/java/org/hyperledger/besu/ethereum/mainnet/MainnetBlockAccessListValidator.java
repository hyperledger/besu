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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mainnet implementation of BlockAccessListValidator that validates block access lists according to
 * EIP-7928.
 */
public class MainnetBlockAccessListValidator implements BlockAccessListValidator {

  private static final Logger LOG = LoggerFactory.getLogger(MainnetBlockAccessListValidator.class);

  private final ProtocolSchedule protocolSchedule;

  /**
   * Creates a block access list validator for the given protocol schedule and optional BAL factory.
   * Use as method reference: {@code MainnetBlockAccessListValidator::create}.
   *
   * @param protocolSchedule the protocol schedule
   * @return a validator instance or no-op when factory is empty
   */
  public static BlockAccessListValidator create(final ProtocolSchedule protocolSchedule) {
    return new MainnetBlockAccessListValidator(protocolSchedule);
  }

  /**
   * Creates a new MainnetBlockAccessListValidator.
   *
   * @param protocolSchedule the protocol schedule to get protocol specs from
   */
  public MainnetBlockAccessListValidator(final ProtocolSchedule protocolSchedule) {
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public boolean validate(
      final Optional<BlockAccessList> blockAccessList, final BlockHeader blockHeader) {
    if (blockAccessList.isEmpty()) {
      return true;
    }
    final BlockAccessList bal = blockAccessList.get();
    final Optional<Hash> headerBalHash = blockHeader.getBalHash();

    if (headerBalHash.isEmpty()) {
      LOG.warn("Header is missing balHash for block {}", blockHeader.toLogString());
      return false;
    }

    // Validate BAL hash matches header (EIP-7928)
    final Hash providedBalHash = BodyValidation.balHash(bal);
    if (!headerBalHash.get().equals(providedBalHash)) {
      LOG.warn(
          "Block access list hash mismatch for block {}: provided={}, header={}",
          blockHeader.toLogString(),
          providedBalHash.toHexString(),
          headerBalHash.get().toHexString());
      return false;
    }

    // Validate BAL size constraint (EIP-7928): bal_items <= block_gas_limit / ITEM_COST
    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(blockHeader);
    final long itemCost = protocolSpec.getGasCalculator().getBlockAccessListItemCost();

    long totalStorageKeys = 0;
    for (BlockAccessList.AccountChanges accountChange : bal.accountChanges()) {
      totalStorageKeys += accountChange.storageChanges().size();
      totalStorageKeys += accountChange.storageReads().size();
    }
    final long totalAddresses = bal.accountChanges().size();
    final long balItems = totalStorageKeys + totalAddresses;
    final long maxItems = blockHeader.getGasLimit() / itemCost;
    if (balItems > maxItems) {
      LOG.warn(
          "Block access list size exceeds maximum allowed items for block {} with gas limit {}",
          blockHeader.toLogString(),
          blockHeader.getGasLimit());
      return false;
    }

    // Uniqueness constraints
    if (!validateUniquenessConstraints(bal, blockHeader)) {
      return false;
    }

    return true;
  }

  /**
   * Validates all EIP-7928 uniqueness constraints in a single pass per account: - Each address
   * exactly once in BlockAccessList - Each storage key at most once in storage_changes /
   * storage_reads per account - No storage key in both storage_changes and storage_reads - Each
   * block_access_index at most once per change list
   */
  private boolean validateUniquenessConstraints(
      final BlockAccessList blockAccessList, final BlockHeader blockHeader) {
    final Set<Address> seenAddresses = new HashSet<>();
    final Set<Integer> seenTxIndices = new HashSet<>();
    for (BlockAccessList.AccountChanges account : blockAccessList.accountChanges()) {
      if (!seenAddresses.add(account.address())) {
        LOG.warn(
            "Block access list has duplicate address {} for block {}",
            account.address().toHexString(),
            blockHeader.toLogString());
        return false;
      }

      final Set<StorageSlotKey> storageChangeSlots = new HashSet<>();
      for (BlockAccessList.SlotChanges slotChanges : account.storageChanges()) {
        if (!storageChangeSlots.add(slotChanges.slot())) {
          LOG.warn(
              "Block access list has duplicate storage key in storage_changes for address {} block {}",
              account.address().toHexString(),
              blockHeader.toLogString());
          return false;
        }
        seenTxIndices.clear();
        for (BlockAccessList.StorageChange ch : slotChanges.changes()) {
          if (!seenTxIndices.add(ch.txIndex())) {
            LOG.warn(
                "Block access list has duplicate block_access_index in storage_changes for address {} block {}",
                account.address().toHexString(),
                blockHeader.toLogString());
            return false;
          }
        }
      }

      final Set<StorageSlotKey> storageReadSlots = new HashSet<>();
      for (BlockAccessList.SlotRead slotRead : account.storageReads()) {
        final StorageSlotKey slot = slotRead.slot();
        if (storageChangeSlots.contains(slot)) {
          LOG.warn(
              "Block access list has storage key in both storage_changes and storage_reads for address {} block {}",
              account.address().toHexString(),
              blockHeader.toLogString());
          return false;
        }
        if (!storageReadSlots.add(slot)) {
          LOG.warn(
              "Block access list has duplicate storage key in storage_reads for address {} block {}",
              account.address().toHexString(),
              blockHeader.toLogString());
          return false;
        }
      }

      seenTxIndices.clear();
      for (BlockAccessList.BalanceChange ch : account.balanceChanges()) {
        if (!seenTxIndices.add(ch.txIndex())) {
          LOG.warn(
              "Block access list has duplicate block_access_index in balance_changes for address {} block {}",
              account.address().toHexString(),
              blockHeader.toLogString());
          return false;
        }
      }
      seenTxIndices.clear();
      for (BlockAccessList.NonceChange ch : account.nonceChanges()) {
        if (!seenTxIndices.add(ch.txIndex())) {
          LOG.warn(
              "Block access list has duplicate block_access_index in nonce_changes for address {} block {}",
              account.address().toHexString(),
              blockHeader.toLogString());
          return false;
        }
      }
      seenTxIndices.clear();
      for (BlockAccessList.CodeChange ch : account.codeChanges()) {
        if (!seenTxIndices.add(ch.txIndex())) {
          LOG.warn(
              "Block access list has duplicate block_access_index in code_changes for address {} block {}",
              account.address().toHexString(),
              blockHeader.toLogString());
          return false;
        }
      }
    }
    return true;
  }
}

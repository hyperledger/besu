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
package org.hyperledger.besu.evm.gascalculator.stateless;

import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.BASIC_DATA_LEAF_KEY;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.CODE_HASH_LEAF_KEY;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.CODE_OFFSET;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.VERKLE_NODE_WIDTH;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;

import org.hyperledger.besu.datatypes.AccessEvent;
import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implementation of the AccessWitness as per EIP-4762. */
public class Eip4762AccessWitness implements AccessWitness {

  private static final Logger LOG = LoggerFactory.getLogger(Eip4762AccessWitness.class);

  private static final UInt256 zeroTreeIndex = UInt256.ZERO;
  private final Map<AccessEvent<?>, AccessEvent<?>> accesses;
  private final List<AccessEvent<?>> revertableEvents;

  /** Instantiates a new EIP-4762 access witness. */
  public Eip4762AccessWitness() {
    this(new HashMap<>(), new ArrayList<>());
  }

  /**
   * Instantiates a new EIP-4762 access witness.
   *
   * @param accesses Collection the controls access to branches and leaves in the trie.
   * @param revertableEvents access events that can be reverted.
   */
  public Eip4762AccessWitness(
      final Map<AccessEvent<?>, AccessEvent<?>> accesses,
      final List<AccessEvent<?>> revertableEvents) {
    this.accesses = accesses;
    this.revertableEvents = revertableEvents;
  }

  private long touchAddressAtomic(final LongSupplier gasSupplier, final long remainingGas) {
    enterWitness();
    long gas = gasSupplier.getAsLong();
    if (remainingGas < gas) {
      revertWitnesses();
    }
    return gas;
  }

  @Override
  public long touchAddressAndChargeRead(
      final Address address, final UInt256 leafKey, final long remainingGas) {
    return touchAddressAtomic(
        () -> touchAddressOnReadAndComputeGas(address, zeroTreeIndex, leafKey), remainingGas);
  }

  @Override
  public long touchAndChargeValueTransfer(
      final Address caller,
      final Address target,
      final boolean isAccountCreation,
      final long warmReadCost,
      final long remainingGas) {
    long gasRemaining = remainingGas;
    long gas =
        touchAddressAtomic(
            () -> touchAddressOnWriteResetAndComputeGas(caller, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
            gasRemaining);
    gasRemaining -= gas;

    if (isAccountCreation) {
      gas =
          clampedAdd(
              gas,
              touchAddressAtomic(
                  () ->
                      clampedAdd(
                          touchAddressOnWriteSetAndComputeGas(
                              target, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
                          touchAddressOnWriteSetAndComputeGas(
                              target, zeroTreeIndex, CODE_HASH_LEAF_KEY)),
                  gasRemaining));
      return gas;
    }

    long readTargetStatelessGas =
        touchAddressAtomic(
            () -> touchAddressOnReadAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
            gasRemaining);
    if (readTargetStatelessGas == 0) {
      readTargetStatelessGas = clampedAdd(readTargetStatelessGas, warmReadCost);
    }
    gasRemaining -= readTargetStatelessGas;

    gas = clampedAdd(gas, readTargetStatelessGas);

    return clampedAdd(
        gas,
        touchAddressAtomic(
            () -> touchAddressOnWriteResetAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
            gasRemaining));
  }

  @Override
  public long touchAndChargeValueTransferSelfDestruct(
      final Address caller,
      final Address target,
      final boolean isAccountCreation,
      final long warmReadCost,
      final long remainingGas) {

    long gasRemaining = remainingGas;
    long gas =
        touchAddressAtomic(
            () -> touchAddressOnWriteResetAndComputeGas(caller, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
            gasRemaining);
    gasRemaining -= gas;

    if (caller.equals(target)) {
      return gas;
    }

    if (isAccountCreation) {
      gas =
          clampedAdd(
              gas,
              touchAddressAtomic(
                  () ->
                      clampedAdd(
                          touchAddressOnWriteSetAndComputeGas(
                              target, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
                          touchAddressOnWriteSetAndComputeGas(
                              target, zeroTreeIndex, CODE_HASH_LEAF_KEY)),
                  gasRemaining));
      return gas;
    }

    long readTargetStatelessGas =
        touchAddressAtomic(
            () -> touchAddressOnReadAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
            gasRemaining);
    if (readTargetStatelessGas == 0) {
      readTargetStatelessGas = clampedAdd(readTargetStatelessGas, warmReadCost);
    }
    gasRemaining -= readTargetStatelessGas;

    gas = clampedAdd(gas, readTargetStatelessGas);

    return clampedAdd(
        gas,
        touchAddressAtomic(
            () -> touchAddressOnWriteResetAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
            gasRemaining));
  }

  @Override
  public long touchAndChargeProofOfAbsence(final Address address, final long remainingGas) {
    return touchAddressAtomic(
        () ->
            clampedAdd(
                touchAddressOnReadAndComputeGas(address, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
                touchAddressOnReadAndComputeGas(address, zeroTreeIndex, CODE_HASH_LEAF_KEY)),
        remainingGas);
  }

  @Override
  public long touchAndChargeContractCreateCompleted(
      final Address address, final long remainingGas) {
    return touchAddressAtomic(
        () ->
            clampedAdd(
                touchAddressOnWriteResetAndComputeGas(address, zeroTreeIndex, BASIC_DATA_LEAF_KEY),
                touchAddressOnWriteResetAndComputeGas(address, zeroTreeIndex, CODE_HASH_LEAF_KEY)),
        remainingGas);
  }

  @Override
  public void touchBaseTx(final Address origin, final Optional<Address> target, final Wei value) {
    LOG.atDebug().log("START OF UNCHARGED COSTS");
    touchTxOrigin(origin);
    if (target.isPresent()) { // is not contract creation
      final Address to = target.get();
      final boolean sendsValue = !Wei.ZERO.equals(value);
      touchTxExisting(to, sendsValue);
    }
    LOG.atDebug().log("END OF UNCHARGED COSTS");
  }

  private void touchTxOrigin(final Address origin) {
    touchAddressOnWriteResetAndComputeGas(origin, zeroTreeIndex, BASIC_DATA_LEAF_KEY);
    touchAddressOnReadAndComputeGas(origin, zeroTreeIndex, CODE_HASH_LEAF_KEY);
  }

  private void touchTxExisting(final Address target, final boolean sendsValue) {
    touchAddressOnReadAndComputeGas(target, zeroTreeIndex, CODE_HASH_LEAF_KEY);
    if (!sendsValue) {
      touchAddressOnReadAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY);
      return;
    }
    // TODO: not done right now on Geth either - implement case if target does not exist yet - a
    // CODEHASH_LEAF will be touched too and WriteSet will be called
    touchAddressOnWriteResetAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY);
  }

  @Override
  public long touchCodeChunksUponContractCreation(
      final Address address, final long codeLength, final long remainingGas) {
    long gasRemaining = remainingGas;
    long gas = 0;
    for (long i = 0; i < (codeLength + 30) / 31; i++) {
      final long temp_i = i;
      final long statelessGas =
          touchAddressAtomic(
              () ->
                  touchAddressOnWriteSetAndComputeGas(
                      address,
                      CODE_OFFSET.add(temp_i).divide(VERKLE_NODE_WIDTH),
                      CODE_OFFSET.add(temp_i).mod(VERKLE_NODE_WIDTH)),
              gasRemaining);
      gasRemaining -= statelessGas;

      gas = clampedAdd(gas, statelessGas);
    }
    return gas;
  }

  @Override
  public long touchCodeChunks(
      final Address contractAddress,
      final boolean isContractInDeployment,
      final long startPc,
      final long readSize,
      final long codeLength,
      final long remainingGas) {
    long gasRemaining = remainingGas;
    long gas = 0;

    if (isContractInDeployment || readSize == 0 || startPc >= codeLength) {
      return 0;
    }

    // last byte read is limited by code length, and it is an index, hence the decrement
    long endPc = Math.min(startPc + readSize, codeLength) - 1L;
    for (long i = startPc / 31L; i <= endPc / 31L; i++) {
      final long temp_i = i;
      final long statelessGas =
          touchAddressAtomic(
              () ->
                  touchAddressOnReadAndComputeGas(
                      contractAddress,
                      CODE_OFFSET.add(temp_i).divide(VERKLE_NODE_WIDTH),
                      CODE_OFFSET.add(temp_i).mod(VERKLE_NODE_WIDTH)),
              gasRemaining);
      gasRemaining -= statelessGas;

      gas = clampedAdd(gas, statelessGas);
    }

    return gas;
  }

  @Override
  public long touchAndChargeStorageLoad(
      final Address address, final UInt256 storageKey, final long remainingGas) {
    List<UInt256> treeIndexes = getStorageSlotTreeIndexes(storageKey);
    return touchAddressAtomic(
        () -> touchAddressOnReadAndComputeGas(address, treeIndexes.getFirst(), treeIndexes.get(1)),
        remainingGas);
  }

  @Override
  public long touchAndChargeStorageStore(
      final Address address,
      final UInt256 storageKey,
      final boolean hasPreviousValue,
      final long remainingGas) {
    List<UInt256> treeIndexes = getStorageSlotTreeIndexes(storageKey);
    return touchAddressAtomic(
        () -> {
          if (!hasPreviousValue) {
            return touchAddressOnWriteSetAndComputeGas(
                address, treeIndexes.get(0), treeIndexes.get(1));
          }
          return touchAddressOnWriteResetAndComputeGas(
              address, treeIndexes.get(0), treeIndexes.get(1));
        },
        remainingGas);
  }

  private long touchAddressOnWriteResetAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return touchAddressAndChargeGas(address, treeIndex, subIndex, AccessEvent.LEAF_RESET);
  }

  private long touchAddressOnWriteSetAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    // TODO: change to LEAF_SET when CHUNK_FILL is implemented. Still not implemented in devnet-7
    return touchAddressAndChargeGas(address, treeIndex, subIndex, AccessEvent.LEAF_RESET);
  }

  private long touchAddressOnReadAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return touchAddressAndChargeGas(address, treeIndex, subIndex, AccessEvent.LEAF_READ);
  }

  private List<UInt256> getStorageSlotTreeIndexes(final UInt256 storageKey) {
    return List.of(
        UInt256.fromBytes(TrieKeyUtils.getStorageKeyTrieIndex(storageKey)),
        UInt256.fromBytes(TrieKeyUtils.getStorageKeySuffix(storageKey)));
  }

  private long touchAddressAndChargeGas(
      final Address address,
      final UInt256 treeIndex,
      final UInt256 subIndex,
      final int accessMode) {

    BranchAccessEvent branchAccess = new BranchAccessEvent(address, treeIndex);
    touchAddressForBranch(branchAccess, accessMode);
    AccessEvent<?> witnessAccess = branchAccess;
    if (subIndex != null) {
      // create a leaf access
      LeafAccessEvent leafAccessEvent = new LeafAccessEvent(branchAccess, subIndex);
      touchAddressForLeaf(leafAccessEvent, accessMode);
      witnessAccess = leafAccessEvent;
    }

    long gas = 0;
    if (witnessAccess.getBranchEvent().isBranchRead()) {
      gas = clampedAdd(gas, AccessEvent.getBranchReadCost());
    }
    if (witnessAccess.isLeafRead()) {
      gas = clampedAdd(gas, AccessEvent.getLeafReadCost());
    }
    if (witnessAccess.getBranchEvent().isBranchWrite()) {
      gas = clampedAdd(gas, AccessEvent.getBranchWriteCost());
    }
    if (witnessAccess.isLeafReset()) {
      gas = clampedAdd(gas, AccessEvent.getLeafResetCost());
    }
    if (witnessAccess.isLeafSet()) {
      gas = clampedAdd(gas, AccessEvent.getLeafSetCost());
    }

    final long gasView = gas;
    final AccessEvent<?> witnessAccessView = witnessAccess;
    LOG.atDebug().log(
        () ->
            "touch witness "
                + witnessAccessView
                + "\ntotal charges "
                + gasView
                + witnessAccessView.costSchedulePrettyPrint());

    revertableEvents.add(witnessAccess);

    return gas;
  }

  private void touchAddressForBranch(final BranchAccessEvent accessEvent, final int accessMode) {
    AccessEvent<?> currentAccess = accesses.putIfAbsent(accessEvent, accessEvent);
    if (currentAccess == null) {
      currentAccess = accessEvent;
      accessEvent.branchRead();
    }

    // A write is always a read
    if (AccessEvent.isWrite(accessMode)) {
      if (!currentAccess.isBranchWrite()) {
        accessEvent.branchWrite();
      }
    }

    currentAccess.seenAccess();
    currentAccess.mergeFlags(accessEvent);
    accesses.put(currentAccess, currentAccess);
  }

  private void touchAddressForLeaf(final LeafAccessEvent accessEvent, final int accessMode) {
    AccessEvent<?> currentAccess = accesses.putIfAbsent(accessEvent, accessEvent);
    if (currentAccess == null) {
      currentAccess = accessEvent;
      accessEvent.leafRead();
    }

    // A write is always a read
    if (AccessEvent.isWrite(accessMode)) {
      if (!currentAccess.isLeafReset()) {
        accessEvent.leafReset();
      }
      if (AccessEvent.isLeafSet(accessMode) && !currentAccess.isLeafSet()) {
        accessEvent.leafSet();
      }
    }

    currentAccess.seenAccess();
    currentAccess.mergeFlags(accessEvent);
    accesses.put(currentAccess, currentAccess);
  }

  private void revertWitnesses() {
    revertableEvents.forEach(
        key -> {
          if (accesses.containsKey(key)) {
            LOG.atDebug().log("rolling back {}", key);
            rollbackAccess(key.getBranchEvent());
            rollbackAccess(key);
          }
        });
  }

  private void rollbackAccess(final AccessEvent<?> key) {
    if (accesses.get(key).rollbackAccessAndGet() > 0) {
      return;
    }
    LOG.atDebug().log("removed {}", key);
    accesses.remove(key);
  }

  private void enterWitness() {
    revertableEvents.clear();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Eip4762AccessWitness that = (Eip4762AccessWitness) o;
    return Objects.equals(accesses, that.accesses);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accesses, accesses);
  }

  @Override
  public String toString() {
    return String.format(
        "AccessWitness { leaves=%s, branches=%s }", getLeafAccesses(), getBranchAccesses());
  }

  private List<AccessEvent<?>> getBranchAccesses() {
    return accesses.keySet().stream()
        .filter(access -> access instanceof BranchAccessEvent)
        .toList();
  }

  @Override
  public List<AccessEvent<?>> getLeafAccesses() {
    return accesses.keySet().stream().filter(access -> access instanceof LeafAccessEvent).toList();
  }
}

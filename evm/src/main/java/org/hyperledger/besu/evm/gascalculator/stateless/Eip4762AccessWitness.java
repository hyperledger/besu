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

import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.PedersenHasher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Eip4762AccessWitness implements AccessWitness {

  private static final Logger LOG = LoggerFactory.getLogger(Eip4762AccessWitness.class);
  private static final TrieKeyAdapter TRIE_KEY_ADAPTER = new TrieKeyAdapter(new PedersenHasher());
  private static final long WITNESS_BRANCH_COST = 1900;
  private static final long WITNESS_CHUNK_COST = 200;
  private static final long SUBTREE_EDIT_COST = 3000;
  private static final long CHUNK_EDIT_COST = 500;
  private static final long CHUNK_FILL_COST = 6200;

  private static final UInt256 zeroTreeIndex = UInt256.ZERO;
  private final Map<LeafAccessKey, Integer> leaves;
  private final Map<BranchAccessKey, Integer> branches;

  public Eip4762AccessWitness() {
    this(new HashMap<>(), new HashMap<>());
  }

  public Eip4762AccessWitness(
      final Map<LeafAccessKey, Integer> leaves, final Map<BranchAccessKey, Integer> branches) {
    this.branches = branches;
    this.leaves = leaves;
  }

  @Override
  public long touchAddressAndChargeRead(final Address address, final UInt256 leafKey) {
    return touchAddressOnReadAndComputeGas(address, zeroTreeIndex, leafKey);
  }

  @Override
  public long touchAndChargeValueTransfer(
      final Address caller,
      final Address target,
      final boolean isAccountCreation,
      final long warmReadCost) {

    long gas = 0;

    gas =
        clampedAdd(
            gas, touchAddressOnWriteResetAndComputeGas(caller, zeroTreeIndex, BASIC_DATA_LEAF_KEY));

    if (isAccountCreation) {
      gas =
          clampedAdd(
              gas, touchAddressOnWriteSetAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY));
      gas =
          clampedAdd(
              gas, touchAddressOnWriteSetAndComputeGas(target, zeroTreeIndex, CODE_HASH_LEAF_KEY));
      return gas;
    }

    long readTargetStatelessGas =
        touchAddressOnReadAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY);
    if (readTargetStatelessGas == 0) {
      readTargetStatelessGas = clampedAdd(readTargetStatelessGas, warmReadCost);
    }

    gas =
        clampedAdd(
            gas, touchAddressOnWriteResetAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY));

    return clampedAdd(gas, readTargetStatelessGas);
  }

  @Override
  public long touchAndChargeValueTransferSelfDestruct(
      final Address caller,
      final Address target,
      final boolean isAccountCreation,
      final long warmReadCost) {

    long gas = 0;

    gas =
        clampedAdd(
            gas, touchAddressOnWriteResetAndComputeGas(caller, zeroTreeIndex, BASIC_DATA_LEAF_KEY));

    if (caller.equals(target)) {
      return gas;
    }

    if (isAccountCreation) {
      gas =
          clampedAdd(
              gas, touchAddressOnWriteSetAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY));
      gas =
          clampedAdd(
              gas, touchAddressOnWriteSetAndComputeGas(target, zeroTreeIndex, CODE_HASH_LEAF_KEY));
      return gas;
    }

    long readTargetStatelessGas =
        touchAddressOnReadAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY);
    if (readTargetStatelessGas == 0) {
      readTargetStatelessGas = clampedAdd(readTargetStatelessGas, warmReadCost);
    }

    gas =
        clampedAdd(
            gas, touchAddressOnWriteResetAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY));

    return clampedAdd(gas, readTargetStatelessGas);
  }

  @Override
  public long touchAndChargeProofOfAbsence(final Address address) {
    long gas = 0;

    gas =
        clampedAdd(
            gas, touchAddressOnReadAndComputeGas(address, zeroTreeIndex, BASIC_DATA_LEAF_KEY));

    gas =
        clampedAdd(
            gas, touchAddressOnReadAndComputeGas(address, zeroTreeIndex, CODE_HASH_LEAF_KEY));

    return gas;
  }

  @Override
  public long touchAndChargeContractCreateCompleted(final Address address) {

    long gas = 0;

    gas =
        clampedAdd(
            gas,
            touchAddressOnWriteResetAndComputeGas(address, zeroTreeIndex, BASIC_DATA_LEAF_KEY));
    gas =
        clampedAdd(
            gas, touchAddressOnWriteResetAndComputeGas(address, zeroTreeIndex, CODE_HASH_LEAF_KEY));

    return gas;
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
  public long touchCodeChunksUponContractCreation(final Address address, final long codeLength) {
    long gas = 0;
    for (long i = 0; i < (codeLength + 30) / 31; i++) {
      gas =
          clampedAdd(
              gas,
              touchAddressOnWriteSetAndComputeGas(
                  address,
                  CODE_OFFSET.add(i).divide(VERKLE_NODE_WIDTH),
                  CODE_OFFSET.add(i).mod(VERKLE_NODE_WIDTH)));
    }
    return gas;
  }

  @Override
  public long touchCodeChunks(
      final Address contractAddress,
      final boolean isContractInDeployment,
      final long startPc,
      final long readSize,
      final long codeLength) {
    long gas = 0;

    if (isContractInDeployment || readSize == 0 || startPc >= codeLength) {
      return 0;
    }

    // last byte read is limited by code length, and it is an index, hence the decrement
    long endPc = Math.min(startPc + readSize, codeLength) - 1L;
    for (long i = startPc / 31L; i <= endPc / 31L; i++) {
      gas =
          clampedAdd(
              gas,
              touchAddressOnReadAndComputeGas(
                  contractAddress,
                  CODE_OFFSET.add(i).divide(VERKLE_NODE_WIDTH),
                  CODE_OFFSET.add(i).mod(VERKLE_NODE_WIDTH)));
    }

    return gas;
  }

  private long touchAddressOnWriteResetAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return touchAddressAndChargeGas(address, treeIndex, subIndex, AccessMode.WRITE_RESET);
  }

  private long touchAddressOnWriteSetAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    // TODO: change to WRITE_SET when CHUNK_FILL is implemented. Still not implemented in devnet-7
    return touchAddressAndChargeGas(address, treeIndex, subIndex, AccessMode.WRITE_RESET);
  }

  private long touchAddressOnReadAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return touchAddressAndChargeGas(address, treeIndex, subIndex, AccessMode.READ);
  }

  private List<UInt256> getStorageSlotTreeIndexes(final UInt256 storageKey) {
    return List.of(
        TRIE_KEY_ADAPTER.locateStorageKeyOffset(storageKey),
        TRIE_KEY_ADAPTER.locateStorageKeySuffix(storageKey));
  }

  @Override
  public long touchAndChargeStorageLoad(final Address address, final UInt256 storageKey) {
    List<UInt256> treeIndexes = getStorageSlotTreeIndexes(storageKey);
    return touchAddressOnReadAndComputeGas(address, treeIndexes.get(0), treeIndexes.get(1));
  }

  @Override
  public long touchAndChargeStorageStore(
      final Address address, final UInt256 storageKey, final boolean hasPreviousValue) {
    List<UInt256> treeIndexes = getStorageSlotTreeIndexes(storageKey);
    if (!hasPreviousValue) {
      return touchAddressOnWriteSetAndComputeGas(address, treeIndexes.get(0), treeIndexes.get(1));
    }
    return touchAddressOnWriteResetAndComputeGas(address, treeIndexes.get(0), treeIndexes.get(1));
  }

  public long touchAddressAndChargeGas(
      final Address address,
      final UInt256 treeIndex,
      final UInt256 subIndex,
      final int accessMode) {
    final short accessEvents = touchAddress(address, treeIndex, subIndex, accessMode);
    long gas = 0;
    LOG.atDebug().log(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
    if (AccessEvents.isBranchRead(accessEvents)) {
      gas = clampedAdd(gas, WITNESS_BRANCH_COST);
      final long gasView = gas;
      LOG.atDebug().log(
          () ->
              "touchAddressAndChargeGas WITNESS_BRANCH_COST "
                  + address
                  + " "
                  + treeIndex
                  + " "
                  + subIndex
                  + " "
                  + AccessMode.toString(accessMode)
                  + " "
                  + gasView);
    }
    if (AccessEvents.isLeafRead(accessEvents)) {
      gas = clampedAdd(gas, WITNESS_CHUNK_COST);
      final long gasView = gas;
      LOG.atDebug().log(
          () ->
              "touchAddressAndChargeGas WITNESS_CHUNK_COST "
                  + address
                  + " "
                  + treeIndex
                  + " "
                  + subIndex
                  + " "
                  + AccessMode.toString(accessMode)
                  + " "
                  + gasView);
    }
    if (AccessEvents.isBranchWrite(accessEvents)) {
      gas = clampedAdd(gas, SUBTREE_EDIT_COST);
      final long gasView = gas;
      LOG.atDebug().log(
          () ->
              "touchAddressAndChargeGas SUBTREE_EDIT_COST "
                  + address
                  + " "
                  + treeIndex
                  + " "
                  + subIndex
                  + " "
                  + AccessMode.toString(accessMode)
                  + " "
                  + gasView);
    }
    if (AccessEvents.isLeafReset(accessEvents)) {
      gas = clampedAdd(gas, CHUNK_EDIT_COST);
      final long gasView = gas;
      LOG.atDebug().log(
          () ->
              "touchAddressAndChargeGas CHUNK_EDIT_COST "
                  + address
                  + " "
                  + treeIndex
                  + " "
                  + subIndex
                  + " "
                  + AccessMode.toString(accessMode)
                  + " "
                  + gasView);
    }
    if (AccessEvents.isLeafSet(accessEvents)) {
      gas = clampedAdd(gas, CHUNK_FILL_COST);
      final long gasView = gas;
      LOG.atDebug().log(
          () ->
              "touchAddressAndChargeGas CHUNK_FILL_COST "
                  + address
                  + " "
                  + treeIndex
                  + " "
                  + subIndex
                  + " "
                  + AccessMode.toString(accessMode)
                  + " "
                  + gasView);
    }
    LOG.atDebug().log("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
    return gas;
  }

  public short touchAddress(
      final Address addr, final UInt256 treeIndex, final UInt256 leafIndex, final int accessMode) {
    short accessEvents = AccessEvents.NONE;
    BranchAccessKey branchKey = new BranchAccessKey(addr, treeIndex);

    accessEvents |= touchAddressForBranch(branchKey, accessMode);
    if (leafIndex != null) {
      accessEvents |= touchAddressForLeaf(branchKey, leafIndex, accessMode);
    }

    return accessEvents;
  }

  private short touchAddressForBranch(final BranchAccessKey branchKey, final int accessMode) {
    short accessEvents = AccessEvents.NONE;

    if (!this.branches.containsKey(branchKey)) {
      accessEvents |= AccessEvents.BRANCH_READ;
      this.branches.put(branchKey, AccessMode.READ);
    }

    // A write is always a read
    if (AccessMode.isWrite(accessMode)) {
      int previousAccessMode =
          !this.branches.containsKey(branchKey) ? AccessMode.NONE : this.branches.get(branchKey);
      if (!AccessMode.isWrite(previousAccessMode)) {
        accessEvents |= AccessEvents.BRANCH_WRITE;
        this.branches.put(branchKey, (previousAccessMode | accessMode));
      }
    }

    return accessEvents;
  }

  private short touchAddressForLeaf(
      final BranchAccessKey branchKey, final UInt256 subIndex, final int accessMode) {
    LeafAccessKey leafKey = new LeafAccessKey(branchKey, subIndex);
    short accessEvents = AccessEvents.NONE;

    if (!this.leaves.containsKey(leafKey)) {
      accessEvents |= AccessEvents.LEAF_READ;
      this.leaves.put(leafKey, AccessMode.READ);
    }

    // A write is always a read
    if (AccessMode.isWrite(accessMode)) {
      int previousAccessMode =
          !this.leaves.containsKey(leafKey) ? AccessMode.NONE : this.leaves.get(leafKey);
      if (!AccessMode.isWrite(previousAccessMode)) {
        accessEvents |= AccessEvents.LEAF_RESET;
        if (AccessMode.isWriteSet(accessMode)) {
          accessEvents |= AccessEvents.LEAF_SET;
        }
        this.leaves.put(leafKey, (previousAccessMode | accessMode));
      }
    }
    return accessEvents;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Eip4762AccessWitness that = (Eip4762AccessWitness) o;
    return Objects.equals(branches, that.branches) && Objects.equals(leaves, that.leaves);
  }

  @Override
  public int hashCode() {
    return Objects.hash(branches, leaves);
  }

  @Override
  public String toString() {
    return "AccessWitness{" + "leaves=" + leaves + ", branches=" + branches + '}';
  }

  public record BranchAccessKey(Address address, UInt256 treeIndex) {}

  public record LeafAccessKey(BranchAccessKey branchAccessKey, UInt256 leafIndex) {}
}

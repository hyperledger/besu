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
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.PedersenHasher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class Eip4762AccessWitness implements AccessWitness {

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
  public List<Address> keys() {
    return this.leaves.keySet().stream()
        .map(leafAccessKey -> leafAccessKey.branchAccessKey().address())
        .toList();
  }

  @Override
  public long touchAndChargeProofOfAbsence(final Address address) {
    long gas = 0;
    gas =
        clampedAdd(
            gas, touchAddressOnReadAndComputeGas(address, zeroTreeIndex, BASIC_DATA_LEAF_KEY));
    return gas;
  }

  @Override
  public long touchAndChargeMessageCall(final Address address) {
    return touchAddressOnReadAndComputeGas(address, zeroTreeIndex, BASIC_DATA_LEAF_KEY);
  }

  @Override
  public long touchAndChargeValueTransfer(
      final Address caller, final Address target, final boolean isAccountCreation) {

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
    } else {
      gas =
          clampedAdd(
              gas,
              touchAddressOnWriteResetAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY));
    }

    return gas;
  }

  @Override
  public long touchAndChargeContractCreateInit(final Address address) {
    return touchAddressOnWriteResetAndComputeGas(address, zeroTreeIndex, BASIC_DATA_LEAF_KEY);
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
  public long touchTxOriginAndComputeGas(final Address origin) {
    long gas = 0;
    gas =
        clampedAdd(
            gas, touchAddressOnWriteResetAndComputeGas(origin, zeroTreeIndex, BASIC_DATA_LEAF_KEY));
    gas =
        clampedAdd(gas, touchAddressOnReadAndComputeGas(origin, zeroTreeIndex, CODE_HASH_LEAF_KEY));
    return gas;
  }

  @Override
  public long touchTxExistingAndComputeGas(final Address target, final boolean sendsValue) {
    long gas = touchAddressOnReadAndComputeGas(target, zeroTreeIndex, CODE_HASH_LEAF_KEY);
    if (!sendsValue) {
      gas =
          clampedAdd(
              gas, touchAddressOnReadAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY));
    } else {
      gas =
          clampedAdd(
              gas,
              touchAddressOnWriteResetAndComputeGas(target, zeroTreeIndex, BASIC_DATA_LEAF_KEY));
    }
    return gas;
  }

  @Override
  public long touchCodeChunksUponContractCreation(final Address address, final long codeLength) {
    long gas = 0;
    for (long i = 0; i < (codeLength + 30) / 31; i++) {
      gas =
          clampedAdd(
              gas,
              touchAddressOnWriteResetAndComputeGas(
                  address,
                  CODE_OFFSET.add(i).divide(VERKLE_NODE_WIDTH),
                  CODE_OFFSET.add(i).mod(VERKLE_NODE_WIDTH)));
    }
    return gas;
  }

  @Override
  public long touchCodeChunks(
      final Address address, final long startPc, final long readSize, final long codeLength) {
    long gas = 0;
    if (readSize == 0 || startPc > codeLength) {
      return 0;
    }
    long endPc = Math.min(startPc + readSize, codeLength - 1);
    for (long i = startPc / 31; i <= endPc / 31; i++) {
      gas =
          clampedAdd(
              gas,
              touchAddressOnReadAndComputeGas(
                  address,
                  CODE_OFFSET.add(i).divide(VERKLE_NODE_WIDTH),
                  CODE_OFFSET.add(i).mod(VERKLE_NODE_WIDTH)));
    }
    return gas;
  }

  @Override
  public long touchAddressOnWriteResetAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return touchAddressAndChargeGas(address, treeIndex, subIndex, AccessMode.WRITE_RESET);
  }

  @Override
  public long touchAddressOnWriteSetAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    // TODO: change to WRITE_SET when CHUNK_FILL is implemented. Still not implemented in devnet-7
    return touchAddressAndChargeGas(address, treeIndex, subIndex, AccessMode.WRITE_RESET);
  }

  @Override
  public long touchAddressOnReadAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return touchAddressAndChargeGas(address, treeIndex, subIndex, AccessMode.READ);
  }

  public long touchAddressAndChargeGas(
      final Address address,
      final UInt256 treeIndex,
      final UInt256 subIndex,
      final int accessMode) {
    // TODO: consider getting rid of accessEvents and compute gas inside touchAddress
    final short accessEvents = touchAddress(address, treeIndex, subIndex, accessMode);
    boolean logEnabled = true;
    if (logEnabled) {
      System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
    }
    long gas = 0;
    if (AccessEvents.isBranchRead(accessEvents)) {
      gas = clampedAdd(gas, WITNESS_BRANCH_COST);
      if (logEnabled) {
        System.out.println(
            "touchAddressAndChargeGas WITNESS_BRANCH_COST "
                + address
                + " "
                + treeIndex
                + " "
                + subIndex
                + " "
                + AccessMode.toString(accessMode)
                + " "
                + gas);
      }
    }
    if (AccessEvents.isLeafRead(accessEvents)) {
      gas = clampedAdd(gas, WITNESS_CHUNK_COST);
      if (logEnabled) {
        System.out.println(
            "touchAddressAndChargeGas WITNESS_CHUNK_COST "
                + address
                + " "
                + treeIndex
                + " "
                + subIndex
                + " "
                + AccessMode.toString(accessMode)
                + " "
                + gas);
      }
    }
    if (AccessEvents.isBranchWrite(accessEvents)) {
      gas = clampedAdd(gas, SUBTREE_EDIT_COST);
      if (logEnabled) {
        System.out.println(
            "touchAddressAndChargeGas SUBTREE_EDIT_COST "
                + address
                + " "
                + treeIndex
                + " "
                + subIndex
                + " "
                + AccessMode.toString(accessMode)
                + " "
                + gas);
      }
    }
    if (AccessEvents.isLeafReset(accessEvents)) {
      gas = clampedAdd(gas, CHUNK_EDIT_COST);
      if (logEnabled) {
        System.out.println(
            "touchAddressAndChargeGas CHUNK_EDIT_COST "
                + address
                + " "
                + treeIndex
                + " "
                + subIndex
                + " "
                + AccessMode.toString(accessMode)
                + " "
                + gas);
      }
    }
    if (AccessEvents.isLeafSet(accessEvents)) {
      gas = clampedAdd(gas, CHUNK_FILL_COST);
      if (logEnabled) {
        System.out.println(
            "touchAddressAndChargeGas CHUNK_FILL_COST "
                + address
                + " "
                + treeIndex
                + " "
                + subIndex
                + " "
                + AccessMode.toString(accessMode)
                + " "
                + gas);
      }
    }

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

  public record LeafAccessKey(BranchAccessKey branchAccessKey, Bytes leafIndex) {}

  @Override
  public List<UInt256> getStorageSlotTreeIndexes(final UInt256 storageKey) {
    return List.of(
        TRIE_KEY_ADAPTER.getStorageKeyTrieIndex(storageKey),
        UInt256.fromBytes(TRIE_KEY_ADAPTER.getStorageKeySuffix(storageKey)));
  }
}

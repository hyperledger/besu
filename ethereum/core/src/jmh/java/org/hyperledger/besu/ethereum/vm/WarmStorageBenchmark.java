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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.collections.undo.UndoSet;
import org.hyperledger.besu.collections.undo.UndoTable;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.internal.WarmStorageKeyV2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.TreeBasedTable;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * One transaction's worth of EIP-2929 warm-slot bookkeeping: a fresh table, a mix of first and
 * repeat accesses across a few contracts, and one revert in the middle. Reports nanoseconds per
 * access, including the table's creation amortised over the transaction.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class WarmStorageBenchmark {

  private static final int ACCESSES = 3000;
  private static final int REVERT_AT = 1000;
  private static final int REVERT_LENGTH = 500;

  /** Warm-slot implementations under test: EVM v1's table of tree maps and EVM v2's hash set. */
  public enum Implementation {
    TREE_TABLE,
    HASH_SET
  }

  /** Minimal view of a warm-slot table. */
  interface WarmSlots {
    boolean warmUp(Address address, Bytes32 slot);

    long mark();

    void undo(long mark);
  }

  @Param({"TREE_TABLE", "HASH_SET"})
  private Implementation implementation;

  @Param({"1", "8"})
  private int contracts;

  @Param({"30", "1500"})
  private int distinctSlots;

  private Address[] addresses;
  private Bytes32[] slots;

  @Setup(Level.Trial)
  public void setUp() {
    final Random random = new Random(42);
    final Address[] contractAddresses = new Address[contracts];
    for (int i = 0; i < contracts; i++) {
      contractAddresses[i] = Address.wrap(Bytes.random(20, random));
    }
    final List<Integer> sequence = new ArrayList<>(ACCESSES);
    // every distinct slot once, then repeats until the transaction is full
    for (int i = 0; i < ACCESSES; i++) {
      sequence.add(i % distinctSlots);
    }
    Collections.shuffle(sequence, random);
    final Bytes32[] distinct = new Bytes32[distinctSlots];
    for (int i = 0; i < distinctSlots; i++) {
      distinct[i] = Bytes32.random(random);
    }
    addresses = new Address[ACCESSES];
    slots = new Bytes32[ACCESSES];
    for (int i = 0; i < ACCESSES; i++) {
      final int slot = sequence.get(i);
      addresses[i] = contractAddresses[slot % contracts];
      slots[i] = distinct[slot];
    }
    verifyAgainstTreeTable();
  }

  private void verifyAgainstTreeTable() {
    final WarmSlots reference = create(Implementation.TREE_TABLE);
    final WarmSlots candidate = create(implementation);
    long referenceMark = 0L;
    long candidateMark = 0L;
    for (int i = 0; i < ACCESSES; i++) {
      if (i == REVERT_AT) {
        referenceMark = reference.mark();
        candidateMark = candidate.mark();
      }
      if (i == REVERT_AT + REVERT_LENGTH) {
        reference.undo(referenceMark);
        candidate.undo(candidateMark);
      }
      if (reference.warmUp(addresses[i], slots[i]) != candidate.warmUp(addresses[i], slots[i])) {
        throw new IllegalStateException(implementation + " disagrees with the tree table at " + i);
      }
    }
  }

  @Benchmark
  @OperationsPerInvocation(ACCESSES)
  public void transaction(final Blackhole blackhole) {
    final WarmSlots table = create(implementation);
    long mark = 0L;
    for (int i = 0; i < ACCESSES; i++) {
      if (i == REVERT_AT) {
        mark = table.mark();
      }
      if (i == REVERT_AT + REVERT_LENGTH) {
        table.undo(mark);
      }
      blackhole.consume(table.warmUp(addresses[i], slots[i]));
    }
  }

  private static WarmSlots create(final Implementation implementation) {
    return switch (implementation) {
      case TREE_TABLE -> undoTable(UndoTable.of(TreeBasedTable.create()));
      case HASH_SET -> undoSet(UndoSet.of(new HashSet<>()));
    };
  }

  private static WarmSlots undoTable(final UndoTable<Address, Bytes32, Boolean> table) {
    return new WarmSlots() {
      @Override
      public boolean warmUp(final Address address, final Bytes32 slot) {
        return table.put(address, slot, Boolean.TRUE) != null;
      }

      @Override
      public long mark() {
        return table.mark();
      }

      @Override
      public void undo(final long mark) {
        table.undo(mark);
      }
    };
  }

  private static WarmSlots undoSet(final UndoSet<WarmStorageKeyV2> set) {
    return new WarmSlots() {
      @Override
      public boolean warmUp(final Address address, final Bytes32 slot) {
        return !set.add(new WarmStorageKeyV2(address, slot));
      }

      @Override
      public long mark() {
        return set.mark();
      }

      @Override
      public void undo(final long mark) {
        set.undo(mark);
      }
    };
  }
}

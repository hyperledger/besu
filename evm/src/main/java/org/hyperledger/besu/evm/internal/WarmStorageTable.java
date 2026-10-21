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
package org.hyperledger.besu.evm.internal;

import org.hyperledger.besu.collections.undo.Undoable;
import org.hyperledger.besu.crypto.SecureRandomProvider;
import org.hyperledger.besu.datatypes.Address;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.BiConsumer;

import org.apache.tuweni.bytes.Bytes32;

/**
 * The set of storage slots a transaction has already touched, as EIP-2929 defines warmth, on EVM
 * v2.
 *
 * <p>Every SLOAD and SSTORE consults it, so it is a flat open-addressing table keyed by a hash of
 * the address and the slot rather than a map of maps, and it supports nothing beyond membership,
 * insertion, iteration and rolling back to an earlier mark. The hash is a universal family with
 * secret constants drawn at start-up: slot keys are chosen by whoever sends the transaction, so a
 * fixed hash would let them collide every probe into one chain.
 */
public final class WarmStorageTable implements Undoable {

  private static final int INITIAL_CAPACITY = 64;

  // a cell whose hash is zero is empty, so no entry may hash to zero
  private static final long EMPTY_CELL = 0L;

  // a key as words: the slot's four, then the address's two and a half
  private static final int KEY_WORDS = 7;

  private static final long LOW_32_BITS = 0xffffffffL;

  private static final long[] HASH_SEEDS = new long[15];

  static {
    final SecureRandom random = SecureRandomProvider.createSecureRandom();
    for (int i = 0; i < HASH_SEEDS.length; i++) {
      HASH_SEEDS[i] = random.nextLong();
    }
  }

  private static final VarHandle LONG_BIG_ENDIAN =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

  private static final VarHandle INT_BIG_ENDIAN =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

  // The table: one entry per cell, spread over parallel arrays.
  private long[] cellHashes;

  // compared instead of the keys themselves, whose equals reads them a byte at a time
  private long[] cellKeys;

  private Address[] cellAddresses;
  private Bytes32[] cellSlots;
  private int indexMask;
  private int size;

  // a table belongs to one transaction and so to one thread, which lets lookups share one key
  private final long[] lookupKey = new long[KEY_WORDS];

  // The undo log: every inserted entry with the mark it was inserted at, oldest first.
  private long[] undoLogMarks;
  private Address[] undoLogAddresses;
  private Bytes32[] undoLogSlots;
  private int undoLogSize;

  /** Creates an empty table. */
  public WarmStorageTable() {
    allocateCells(INITIAL_CAPACITY);

    undoLogMarks = new long[INITIAL_CAPACITY];
    undoLogAddresses = new Address[INITIAL_CAPACITY];
    undoLogSlots = new Bytes32[INITIAL_CAPACITY];
  }

  /**
   * Marks a slot as warm.
   *
   * @param address the account the slot belongs to
   * @param slot the slot
   * @return true if the slot was already warm
   */
  public boolean warmUp(final Address address, final Bytes32 slot) {
    final long[] key = readKey(address, slot);
    final long hash = hash(key);

    final int cell = findCell(key, hash);
    if (cellHashes[cell] != EMPTY_CELL) {
      return true;
    }

    fillCell(cell, hash, key, address, slot);
    size++;
    appendToUndoLog(address, slot);

    // at most half full, so probe runs stay short
    if (size * 2 > cellHashes.length) {
      doubleCapacity();
    }
    return false;
  }

  /**
   * Whether a slot is warm.
   *
   * @param address the account the slot belongs to
   * @param slot the slot
   * @return true if the slot is warm
   */
  public boolean contains(final Address address, final Bytes32 slot) {
    final long[] key = readKey(address, slot);
    final int cell = findCell(key, hash(key));
    return cellHashes[cell] != EMPTY_CELL;
  }

  /**
   * Number of warm slots.
   *
   * @return the count
   */
  public int size() {
    return size;
  }

  /**
   * Whether no slot is warm.
   *
   * @return true if empty
   */
  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * Visits every warm slot, in no particular order.
   *
   * @param visitor receives the account and the slot
   */
  public void forEach(final BiConsumer<Address, Bytes32> visitor) {
    for (int cell = 0; cell < cellHashes.length; cell++) {
      if (cellHashes[cell] != EMPTY_CELL) {
        visitor.accept(cellAddresses[cell], cellSlots[cell]);
      }
    }
  }

  @Override
  public long lastUpdate() {
    return undoLogSize == 0 ? 0L : undoLogMarks[undoLogSize - 1];
  }

  @Override
  public void undo(final long mark) {
    while (undoLogSize > 0 && undoLogMarks[undoLogSize - 1] > mark) {
      undoLogSize--;

      final Address address = undoLogAddresses[undoLogSize];
      final Bytes32 slot = undoLogSlots[undoLogSize];
      undoLogAddresses[undoLogSize] = null;
      undoLogSlots[undoLogSize] = null;

      final long[] key = readKey(address, slot);
      removeCell(findCell(key, hash(key)));
    }
  }

  /**
   * Returns the cell holding the key, or, if the table does not hold it, the empty cell that ends
   * its probe run.
   */
  private int findCell(final long[] key, final long hash) {
    int cell = homeCell(hash);
    while (cellHashes[cell] != EMPTY_CELL) {
      if (cellHashes[cell] == hash && keyEquals(cell, key)) {
        return cell;
      }
      cell = nextCell(cell);
    }
    return cell;
  }

  private int homeCell(final long hash) {
    return (int) hash & indexMask;
  }

  private int nextCell(final int cell) {
    return (cell + 1) & indexMask;
  }

  private boolean keyEquals(final int cell, final long[] key) {
    final int offset = cell * KEY_WORDS;
    return cellKeys[offset] == key[0]
        && cellKeys[offset + 1] == key[1]
        && cellKeys[offset + 2] == key[2]
        && cellKeys[offset + 3] == key[3]
        && cellKeys[offset + 4] == key[4]
        && cellKeys[offset + 5] == key[5]
        && cellKeys[offset + 6] == key[6];
  }

  private void fillCell(
      final int cell,
      final long hash,
      final long[] key,
      final Address address,
      final Bytes32 slot) {
    cellHashes[cell] = hash;
    System.arraycopy(key, 0, cellKeys, cell * KEY_WORDS, KEY_WORDS);
    cellAddresses[cell] = address;
    cellSlots[cell] = slot;
  }

  private void moveCell(final int from, final int to) {
    cellHashes[to] = cellHashes[from];
    System.arraycopy(cellKeys, from * KEY_WORDS, cellKeys, to * KEY_WORDS, KEY_WORDS);
    cellAddresses[to] = cellAddresses[from];
    cellSlots[to] = cellSlots[from];
  }

  private void clearCell(final int cell) {
    cellHashes[cell] = EMPTY_CELL;
    cellAddresses[cell] = null;
    cellSlots[cell] = null;
  }

  /**
   * Empties a cell and shifts the later entries of its probe run back, so the table needs no
   * tombstones and every remaining entry stays reachable from its home cell.
   */
  private void removeCell(final int removed) {
    int hole = removed;
    int cell = nextCell(hole);

    while (cellHashes[cell] != EMPTY_CELL) {
      final int home = homeCell(cellHashes[cell]);
      // a lookup walks from the home cell onwards, so the entry may only move back as far as that
      if (probeDistance(home, hole) < probeDistance(home, cell)) {
        moveCell(cell, hole);
        hole = cell;
      }
      cell = nextCell(cell);
    }

    clearCell(hole);
    size--;
  }

  private int probeDistance(final int home, final int cell) {
    return (cell - home) & indexMask;
  }

  private void allocateCells(final int capacity) {
    cellHashes = new long[capacity];
    cellKeys = new long[capacity * KEY_WORDS];
    cellAddresses = new Address[capacity];
    cellSlots = new Bytes32[capacity];
    indexMask = capacity - 1;
  }

  private void doubleCapacity() {
    final long[] oldHashes = cellHashes;
    final long[] oldKeys = cellKeys;
    final Address[] oldAddresses = cellAddresses;
    final Bytes32[] oldSlots = cellSlots;

    allocateCells(oldHashes.length * 2);

    for (int oldCell = 0; oldCell < oldHashes.length; oldCell++) {
      final long hash = oldHashes[oldCell];
      if (hash == EMPTY_CELL) {
        continue;
      }

      int cell = homeCell(hash);
      while (cellHashes[cell] != EMPTY_CELL) {
        cell = nextCell(cell);
      }

      cellHashes[cell] = hash;
      System.arraycopy(oldKeys, oldCell * KEY_WORDS, cellKeys, cell * KEY_WORDS, KEY_WORDS);
      cellAddresses[cell] = oldAddresses[oldCell];
      cellSlots[cell] = oldSlots[oldCell];
    }
  }

  private void appendToUndoLog(final Address address, final Bytes32 slot) {
    if (undoLogSize == undoLogMarks.length) {
      final int capacity = undoLogSize * 2;
      undoLogMarks = Arrays.copyOf(undoLogMarks, capacity);
      undoLogAddresses = Arrays.copyOf(undoLogAddresses, capacity);
      undoLogSlots = Arrays.copyOf(undoLogSlots, capacity);
    }

    undoLogMarks[undoLogSize] = Undoable.incrementMarkStatic();
    undoLogAddresses[undoLogSize] = address;
    undoLogSlots[undoLogSize] = slot;
    undoLogSize++;
  }

  private long[] readKey(final Address address, final Bytes32 slot) {
    final byte[] slotBytes = slot.toArrayUnsafe();
    final byte[] addressBytes = address.getBytes().toArrayUnsafe();

    final long[] key = lookupKey;
    key[0] = (long) LONG_BIG_ENDIAN.get(slotBytes, 0);
    key[1] = (long) LONG_BIG_ENDIAN.get(slotBytes, 8);
    key[2] = (long) LONG_BIG_ENDIAN.get(slotBytes, 16);
    key[3] = (long) LONG_BIG_ENDIAN.get(slotBytes, 24);
    key[4] = (long) LONG_BIG_ENDIAN.get(addressBytes, 0);
    key[5] = (long) LONG_BIG_ENDIAN.get(addressBytes, 8);
    key[6] = (int) INT_BIG_ENDIAN.get(addressBytes, 16) & LOW_32_BITS;
    return key;
  }

  private static long hash(final long[] key) {
    final long hash = pairMultiplyShift(key);
    return hash == EMPTY_CELL ? 1L : hash;
  }

  /**
   * Pair-multiply-shift over the 32-bit lanes of the slot and the address: each pair of lanes is
   * offset by secret seeds, multiplied, and the products summed; the top 32 bits of the sum are the
   * hash. Summing single-lane products modulo 2^64 would let two keys that differ only in the top
   * bits of two lanes collide regardless of the seeds, which is why the lanes are paired and the
   * high half is taken.
   */
  private static long pairMultiplyShift(final long[] key) {
    long sum = HASH_SEEDS[14];
    sum += multiplyLanes(key[0], HASH_SEEDS[0], HASH_SEEDS[1]);
    sum += multiplyLanes(key[1], HASH_SEEDS[2], HASH_SEEDS[3]);
    sum += multiplyLanes(key[2], HASH_SEEDS[4], HASH_SEEDS[5]);
    sum += multiplyLanes(key[3], HASH_SEEDS[6], HASH_SEEDS[7]);
    sum += multiplyLanes(key[4], HASH_SEEDS[8], HASH_SEEDS[9]);
    sum += multiplyLanes(key[5], HASH_SEEDS[10], HASH_SEEDS[11]);
    // the last key word holds a single lane, the address's final four bytes
    sum += (HASH_SEEDS[12] + key[6]) * HASH_SEEDS[13];
    return sum >>> 32;
  }

  private static long multiplyLanes(final long word, final long highSeed, final long lowSeed) {
    return (highSeed + (word >>> 32)) * (lowSeed + (word & LOW_32_BITS));
  }
}

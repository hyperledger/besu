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
  private static final long[] MULTIPLIERS = new long[15];

  static {
    final SecureRandom random = SecureRandomProvider.createSecureRandom();
    for (int i = 0; i < MULTIPLIERS.length; i++) {
      MULTIPLIERS[i] = random.nextLong();
    }
  }

  private static final VarHandle LONG_BE =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
  private static final VarHandle INT_BE =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

  // a key as words: the slot's four, then the address's two and a half
  private static final int WORDS = 7;

  private long[] hashes;
  // compared instead of the keys themselves, whose equals reads them a byte at a time
  private long[] words;
  private Address[] addresses;
  private Bytes32[] slots;
  private int mask;
  private int size;
  // the key being looked up; a table belongs to one transaction, so one thread
  private final long[] probe = new long[WORDS];

  private long[] logLevels;
  private Address[] logAddresses;
  private Bytes32[] logSlots;
  private int logSize;

  /** Creates an empty table. */
  public WarmStorageTable() {
    allocate(INITIAL_CAPACITY);
    logLevels = new long[INITIAL_CAPACITY];
    logAddresses = new Address[INITIAL_CAPACITY];
    logSlots = new Bytes32[INITIAL_CAPACITY];
  }

  private void allocate(final int capacity) {
    hashes = new long[capacity];
    words = new long[capacity * WORDS];
    addresses = new Address[capacity];
    slots = new Bytes32[capacity];
    mask = capacity - 1;
  }

  /**
   * Marks a slot as warm.
   *
   * @param address the account the slot belongs to
   * @param slot the slot
   * @return true if the slot was already warm
   */
  public boolean warmUp(final Address address, final Bytes32 slot) {
    final long[] key = load(address, slot);
    final long hash = hash(key);
    int index = (int) hash & mask;
    while (true) {
      final long candidate = hashes[index];
      if (candidate == 0L) {
        break;
      }
      if (candidate == hash && holds(index, key)) {
        return true;
      }
      index = (index + 1) & mask;
    }
    hashes[index] = hash;
    System.arraycopy(key, 0, words, index * WORDS, WORDS);
    addresses[index] = address;
    slots[index] = slot;
    size++;
    log(address, slot);
    if (size * 2 > hashes.length) {
      grow();
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
    final long[] key = load(address, slot);
    return find(key, hash(key)) >= 0;
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
    for (int i = 0; i < hashes.length; i++) {
      if (hashes[i] != 0L) {
        visitor.accept(addresses[i], slots[i]);
      }
    }
  }

  @Override
  public long lastUpdate() {
    return logSize == 0 ? 0L : logLevels[logSize - 1];
  }

  @Override
  public void undo(final long mark) {
    while (logSize > 0 && logLevels[logSize - 1] > mark) {
      logSize--;
      final Address address = logAddresses[logSize];
      final Bytes32 slot = logSlots[logSize];
      logAddresses[logSize] = null;
      logSlots[logSize] = null;
      final long[] key = load(address, slot);
      remove(find(key, hash(key)));
    }
  }

  private void log(final Address address, final Bytes32 slot) {
    if (logSize == logLevels.length) {
      final int capacity = logSize * 2;
      logLevels = Arrays.copyOf(logLevels, capacity);
      logAddresses = Arrays.copyOf(logAddresses, capacity);
      logSlots = Arrays.copyOf(logSlots, capacity);
    }
    logLevels[logSize] = Undoable.incrementMarkStatic();
    logAddresses[logSize] = address;
    logSlots[logSize] = slot;
    logSize++;
  }

  private int find(final long[] key, final long hash) {
    int index = (int) hash & mask;
    while (true) {
      final long candidate = hashes[index];
      if (candidate == 0L) {
        return -1;
      }
      if (candidate == hash && holds(index, key)) {
        return index;
      }
      index = (index + 1) & mask;
    }
  }

  private boolean holds(final int index, final long[] key) {
    final int base = index * WORDS;
    return words[base] == key[0]
        && words[base + 1] == key[1]
        && words[base + 2] == key[2]
        && words[base + 3] == key[3]
        && words[base + 4] == key[4]
        && words[base + 5] == key[5]
        && words[base + 6] == key[6];
  }

  private long[] load(final Address address, final Bytes32 slot) {
    final byte[] slotBytes = slot.toArrayUnsafe();
    final byte[] addressBytes = address.getBytes().toArrayUnsafe();
    final long[] key = probe;
    key[0] = (long) LONG_BE.get(slotBytes, 0);
    key[1] = (long) LONG_BE.get(slotBytes, 8);
    key[2] = (long) LONG_BE.get(slotBytes, 16);
    key[3] = (long) LONG_BE.get(slotBytes, 24);
    key[4] = (long) LONG_BE.get(addressBytes, 0);
    key[5] = (long) LONG_BE.get(addressBytes, 8);
    key[6] = (int) INT_BE.get(addressBytes, 16) & 0xffffffffL;
    return key;
  }

  /**
   * Removes the entry at an index, shifting later entries of the same probe run back so every
   * remaining entry stays reachable from its home index.
   */
  private void remove(final int index) {
    int hole = index;
    int next = (hole + 1) & mask;
    while (hashes[next] != 0L) {
      final int home = (int) hashes[next] & mask;
      // the entry can move into the hole only if the hole lies on its way from home
      final boolean movable =
          hole <= next ? (home <= hole || home > next) : (home <= hole && home > next);
      if (movable) {
        hashes[hole] = hashes[next];
        System.arraycopy(words, next * WORDS, words, hole * WORDS, WORDS);
        addresses[hole] = addresses[next];
        slots[hole] = slots[next];
        hole = next;
      }
      next = (next + 1) & mask;
    }
    hashes[hole] = 0L;
    addresses[hole] = null;
    slots[hole] = null;
    size--;
  }

  private void grow() {
    final long[] oldHashes = hashes;
    final long[] oldWords = words;
    final Address[] oldAddresses = addresses;
    final Bytes32[] oldSlots = slots;
    allocate(oldHashes.length * 2);
    for (int i = 0; i < oldHashes.length; i++) {
      final long hash = oldHashes[i];
      if (hash != 0L) {
        int index = (int) hash & mask;
        while (hashes[index] != 0L) {
          index = (index + 1) & mask;
        }
        hashes[index] = hash;
        System.arraycopy(oldWords, i * WORDS, words, index * WORDS, WORDS);
        addresses[index] = oldAddresses[i];
        slots[index] = oldSlots[i];
      }
    }
  }

  private static long hash(final long[] key) {
    final long hash = multiplyMix(key);
    // zero marks an empty cell
    return hash == 0L ? 1L : hash;
  }

  /**
   * Pair-multiply-shift over the 32-bit lanes of the slot and the address: each pair of lanes is
   * offset by secret constants, multiplied, and the products summed; the top 32 bits of the sum are
   * the hash. Summing single-lane products modulo 2^64 would let two keys that differ only in the
   * top bits of two lanes collide regardless of the secret, which is why the lanes are paired and
   * the high half is taken.
   */
  private static long multiplyMix(final long[] key) {
    long sum = MULTIPLIERS[14];
    sum += (MULTIPLIERS[0] + (key[0] >>> 32)) * (MULTIPLIERS[1] + (key[0] & 0xffffffffL));
    sum += (MULTIPLIERS[2] + (key[1] >>> 32)) * (MULTIPLIERS[3] + (key[1] & 0xffffffffL));
    sum += (MULTIPLIERS[4] + (key[2] >>> 32)) * (MULTIPLIERS[5] + (key[2] & 0xffffffffL));
    sum += (MULTIPLIERS[6] + (key[3] >>> 32)) * (MULTIPLIERS[7] + (key[3] & 0xffffffffL));
    sum += (MULTIPLIERS[8] + (key[4] >>> 32)) * (MULTIPLIERS[9] + (key[4] & 0xffffffffL));
    sum += (MULTIPLIERS[10] + (key[5] >>> 32)) * (MULTIPLIERS[11] + (key[5] & 0xffffffffL));
    sum += (MULTIPLIERS[12] + key[6]) * MULTIPLIERS[13];
    return sum >>> 32;
  }
}

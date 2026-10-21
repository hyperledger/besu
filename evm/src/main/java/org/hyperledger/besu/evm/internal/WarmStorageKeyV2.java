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

import org.hyperledger.besu.crypto.SecureRandomProvider;
import org.hyperledger.besu.datatypes.Address;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes32;

/**
 * A storage slot of an account, as a key of the set of slots a transaction EVM v2 runs has warmed
 * up as per EIP-2929.
 *
 * <p>Slot keys are chosen by whoever sends the transaction, so the hash is drawn from a strongly
 * universal family with secret seeds: without the seeds, a sender cannot pick keys that share a
 * bucket more often than chance. The class implements {@link Comparable} directly, so that a hash
 * map bucket which fills up anyway is turned into a tree and stays fast to search.
 */
public final class WarmStorageKeyV2 implements Comparable<WarmStorageKeyV2> {

  // the slot's four words, then the address's two and a half
  private static final int WORDS = 7;

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

  private final Address address;
  private final Bytes32 slot;

  // compared instead of the slot, whose equals tells a UInt256 from a Bytes32 of the same value
  private final long[] words;

  private final int hash;

  /**
   * Creates the key of a storage slot.
   *
   * @param address the account the slot belongs to
   * @param slot the slot
   */
  public WarmStorageKeyV2(final Address address, final Bytes32 slot) {
    this.address = address;
    this.slot = slot;
    this.words = toWords(address, slot);
    this.hash = pairMultiplyShift(words);
  }

  /**
   * The account the slot belongs to.
   *
   * @return the address
   */
  public Address address() {
    return address;
  }

  /**
   * The slot.
   *
   * @return the slot
   */
  public Bytes32 slot() {
    return slot;
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof WarmStorageKeyV2 key && Arrays.equals(words, key.words);
  }

  @Override
  public int compareTo(final WarmStorageKeyV2 other) {
    return Arrays.compareUnsigned(words, other.words);
  }

  private static long[] toWords(final Address address, final Bytes32 slot) {
    final byte[] slotBytes = slot.toArrayUnsafe();
    final byte[] addressBytes = address.getBytes().toArrayUnsafe();

    final long[] words = new long[WORDS];
    words[0] = (long) LONG_BIG_ENDIAN.get(slotBytes, 0);
    words[1] = (long) LONG_BIG_ENDIAN.get(slotBytes, 8);
    words[2] = (long) LONG_BIG_ENDIAN.get(slotBytes, 16);
    words[3] = (long) LONG_BIG_ENDIAN.get(slotBytes, 24);
    words[4] = (long) LONG_BIG_ENDIAN.get(addressBytes, 0);
    words[5] = (long) LONG_BIG_ENDIAN.get(addressBytes, 8);
    words[6] = (int) INT_BIG_ENDIAN.get(addressBytes, 16) & LOW_32_BITS;
    return words;
  }

  /**
   * Pair-multiply-shift over the 32-bit lanes of the key: each pair of lanes is offset by secret
   * seeds, multiplied, and the products summed; the top 32 bits of the sum are the hash. Summing
   * single-lane products would let two keys that differ only in the top bits of two lanes collide
   * whatever the seeds, which is why the lanes are paired and the high half is taken.
   */
  private static int pairMultiplyShift(final long[] words) {
    long sum = HASH_SEEDS[14];
    for (int i = 0; i < 6; i++) {
      final long high = words[i] >>> 32;
      final long low = words[i] & LOW_32_BITS;
      sum += (HASH_SEEDS[2 * i] + high) * (HASH_SEEDS[2 * i + 1] + low);
    }
    // the last word holds a single lane, the address's final four bytes
    sum += (HASH_SEEDS[12] + words[6]) * HASH_SEEDS[13];
    return (int) (sum >>> 32);
  }
}

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

  private static final long LOW_32_BITS = 0xffffffffL;

  private static final long[] HASH_SEEDS = new long[15];

  static {
    final SecureRandom random = SecureRandomProvider.createSecureRandom();
    for (int i = 0; i < HASH_SEEDS.length; i++) {
      HASH_SEEDS[i] = random.nextLong();
    }
  }

  // the hash does not depend on the byte order, so words are read in the one most CPUs use
  private static final VarHandle LONG_LITTLE_ENDIAN =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

  private static final VarHandle INT_LITTLE_ENDIAN =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

  private final Address address;
  private final Bytes32 slot;

  // compared instead of the slot, whose equals tells a UInt256 from a Bytes32 of the same value
  private final byte[] addressBytes;
  private final byte[] slotBytes;

  /**
   * Creates the key of a storage slot.
   *
   * @param address the account the slot belongs to
   * @param slot the slot
   */
  public WarmStorageKeyV2(final Address address, final Bytes32 slot) {
    this.address = address;
    this.slot = slot;
    this.addressBytes = address.getBytes().toArrayUnsafe();
    this.slotBytes = slot.toArrayUnsafe();
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
  public boolean equals(final Object other) {
    return other instanceof WarmStorageKeyV2 key
        && Arrays.equals(slotBytes, key.slotBytes)
        && Arrays.equals(addressBytes, key.addressBytes);
  }

  @Override
  public int compareTo(final WarmStorageKeyV2 other) {
    final int byAddress = Arrays.compareUnsigned(addressBytes, other.addressBytes);
    return byAddress != 0 ? byAddress : Arrays.compareUnsigned(slotBytes, other.slotBytes);
  }

  /**
   * Pair-multiply-shift over the 32-bit lanes of the slot and the address: each pair of lanes is
   * offset by secret seeds, multiplied, and the products summed; the top 32 bits of the sum are the
   * hash. Summing single-lane products would let two keys that differ only in the top bits of two
   * lanes collide whatever the seeds, which is why the lanes are paired and the high half is taken.
   */
  @Override
  public int hashCode() {
    long sum = HASH_SEEDS[14];
    sum += multiplyLanes(word(slotBytes, 0), HASH_SEEDS[0], HASH_SEEDS[1]);
    sum += multiplyLanes(word(slotBytes, 8), HASH_SEEDS[2], HASH_SEEDS[3]);
    sum += multiplyLanes(word(slotBytes, 16), HASH_SEEDS[4], HASH_SEEDS[5]);
    sum += multiplyLanes(word(slotBytes, 24), HASH_SEEDS[6], HASH_SEEDS[7]);
    sum += multiplyLanes(word(addressBytes, 0), HASH_SEEDS[8], HASH_SEEDS[9]);
    sum += multiplyLanes(word(addressBytes, 8), HASH_SEEDS[10], HASH_SEEDS[11]);
    // an address has an odd number of lanes, so its last one is not paired
    final long lastLane = (int) INT_LITTLE_ENDIAN.get(addressBytes, 16) & LOW_32_BITS;
    sum += (HASH_SEEDS[12] + lastLane) * HASH_SEEDS[13];
    return (int) (sum >>> 32);
  }

  private static long word(final byte[] bytes, final int offset) {
    return (long) LONG_LITTLE_ENDIAN.get(bytes, offset);
  }

  private static long multiplyLanes(final long word, final long highSeed, final long lowSeed) {
    return (highSeed + (word >>> 32)) * (lowSeed + (word & LOW_32_BITS));
  }
}

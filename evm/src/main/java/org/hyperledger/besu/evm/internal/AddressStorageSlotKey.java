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
 * Key to use for lookups in Maps/Sets with an address and storage slot combined as a key.
 *
 * <p>The slot is fully attacker-controlled - e.g. it can be whatever a contract pushes to {@code
 * TSTORE} - so {@link #hashCode()} is a <em>seeded</em> hash, mixing in per-process random values
 * drawn once at class initialisation. Seed randomization is very important as it makes the hash
 * non-deterministic off chain so collisions can only be found out while executing EVM payloads
 * which costs gas.
 *
 * <p>{@link #hashCode()} is the multilinear family over {@code Z_2^64}: {@code h = additive +
 * SUM(lane_i * seed_i)} accumulated modulo {@code 2^64}, returning the top 32 bits. Each {@code
 * lane_i} is a 32-bit chunk of the key and each {@code seed_i} a uniformly random 64-bit seed. The
 * family is <em>exactly strongly universal</em> on its 32-bit output: for any two distinct keys and
 * any two output values, {@code Pr[hashCode(x) == a and hashCode(y) == b] == 2^-64}. Two distinct
 * keys therefore collide with probability exactly {@code 2^-32}, and share a bucket of a {@code
 * 2^m} table with probability exactly {@code 2^-m} - the same as an ideal random function, whatever
 * keys are chosen. Choosing them cleverly gains an attacker nothing over choosing them at random.
 *
 * <p><strong>The lanes must stay narrow, and this is exact rather than a rule of thumb.</strong>
 * Let {@code t} be the number of trailing zero bits of the difference between two keys in some
 * lane. Conditioned on the other seeds the accumulator difference is uniform on the multiples of
 * {@code 2^t}, and the per-bucket overlap is a triangular kernel of half-width {@code 2^32}. A
 * triangular kernel is a linear B-spline, so it sums to a constant over any lattice whose spacing
 * divides its half-width. Hence the collision probability is exactly {@code 2^-32} while {@code t
 * <= 32}, and degrades to exactly {@code 2^(t-64)} beyond it. A {@code w}-bit lane admits {@code t
 * <= w-1}, so the guarantee holds while {@code w <= 33}; this code uses 32, with a bit to spare.
 *
 * <p>That is precisely what the previous version got wrong. Summing 64-bit words admits {@code t ==
 * 63} and so a collision probability of {@code 2^(63-64) == 1/2}; forcing every seed odd, as it
 * also did, drove that to a certainty, because {@code 2^63 * odd == 2^63 (mod 2^64)} annihilates
 * the seed and flipping the top bit of two words cancelled for every possible seed. Widening the
 * lanes past 33 bits, forcing the seeds odd, or dropping either the additive seed or the {@code
 * 2^64} wraparound all break the proof. The additive seed is load bearing: without it the all-zero
 * key - {@link org.hyperledger.besu.datatypes.Address#ZERO} with slot zero - would hash to a
 * constant.
 *
 * <p><strong>{@link Comparable} is security critical and must not be removed.</strong> It is the
 * backstop for collisions that are found anyway: Java HashMaps only treeify a bucket if the key
 * implements {@link Comparable} directly, keeping lookups at O(log n). Without it, a treeified
 * bucket whose keys share a hash degrades to searching both subtrees, i.e. O(n) per lookup and
 * O(n^2) to fill, which is a liveness risk rather than a slowdown.
 */
public class AddressStorageSlotKey implements Comparable<AddressStorageSlotKey> {

  private final byte[] address;
  private final byte[] slot;

  /**
   * The AddressStorageSlotKey constructor.
   *
   * @param address the bytes from the address part of the key
   * @param slot the bytes from slot part of the key
   */
  public AddressStorageSlotKey(final Address address, final Bytes32 slot) {
    this.address = address.getBytes().toArrayUnsafe();
    this.slot = slot.toArrayUnsafe();
  }

  private static final VarHandle INT_VIEW =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

  /** Index of the additive seed; entries below it are the per-lane multipliers. */
  private static final int ADDITIVE = 13;

  /**
   * Seeds live in an array rather than in individual {@code static final long} fields because the
   * array form is about 14% faster on x86-64 under the tiered compilation production runs - 5.4ns
   * against 6.3ns per hash - reproducibly, whether the variants are timed alone or alongside each
   * other. AArch64 is indifferent to the choice.
   *
   * <p>The cause is <em>not</em> the seed access, which is worth knowing before anyone "optimises"
   * this back to fields. Under {@code -XX:-TieredCompilation} the same two source shapes land
   * within 3% of each other, the field form even edging ahead, so the gap tracks which profile C2
   * consumed - C1's at tier 3 against the interpreter's - rather than anything about fetching
   * seeds. The disassembly agrees: the field form rematerialises each seed with a 10-byte {@code
   * movabs} before its multiply, a 64-bit constant having no {@code imul} immediate form on x86-64,
   * while the array form hoists the reference once and folds each load into the instruction ({@code
   * imul 0x18(%rax),%r14}) with no bounds checks, a constant array reference having a known length.
   * Half the instructions - and under C2 alone no faster for it, because thirteen multiplies
   * against a single multiply port already floor the loop near 13 cycles.
   *
   * <p>The array is mutable where {@code static final long} fields would not be. That is not a
   * security boundary either way: anything able to write here could read the fields by reflection
   * just as easily.
   */
  private static final long[] SEEDS = new long[ADDITIVE + 1];

  static {
    // Seeds are uniformly random; unlike a multiply-shift multiplier they must not be forced odd,
    // which would both cost a bit of entropy each and hand an attacker a known algebraic property.
    final SecureRandom random = SecureRandomProvider.createSecureRandom();
    for (int i = 0; i < SEEDS.length; i++) {
      SEEDS[i] = random.nextLong();
    }
  }

  /** Reads the 32-bit lane at {@code offset} as an unsigned value widened to a long. */
  private static long lane(final byte[] bytes, final int offset) {
    return ((int) INT_VIEW.get(bytes, offset)) & 0xFFFF_FFFFL;
  }

  /**
   * Sizes are fixed - the address is 20 bytes and the slot is 32 - so both divide evenly into
   * 32-bit lanes: 5 lanes for the address and 8 for the slot, read with no overlap and no bounds
   * trickery. The reads are little-endian over big-endian data, which is irrelevant to the hash: it
   * is a fixed permutation of the input bytes, applied identically to every key.
   */
  @Override
  public int hashCode() {
    final long hash =
        SEEDS[ADDITIVE]
            + lane(slot, 0) * SEEDS[0]
            + lane(slot, 4) * SEEDS[1]
            + lane(slot, 8) * SEEDS[2]
            + lane(slot, 12) * SEEDS[3]
            + lane(slot, 16) * SEEDS[4]
            + lane(slot, 20) * SEEDS[5]
            + lane(slot, 24) * SEEDS[6]
            + lane(slot, 28) * SEEDS[7]
            + lane(address, 0) * SEEDS[8]
            + lane(address, 4) * SEEDS[9]
            + lane(address, 8) * SEEDS[10]
            + lane(address, 12) * SEEDS[11]
            + lane(address, 16) * SEEDS[12];
    return (int) (hash >>> 32);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof AddressStorageSlotKey other) {
      return Arrays.equals(address, other.address) && Arrays.equals(slot, other.slot);
    }
    return false;
  }

  @Override
  public int compareTo(final AddressStorageSlotKey other) {
    if (this == other) {
      return 0;
    }
    if (other == null) {
      return 1;
    }
    int compare = Arrays.compare(address, other.address);
    return compare != 0 ? compare : Arrays.compare(slot, other.slot);
  }
}

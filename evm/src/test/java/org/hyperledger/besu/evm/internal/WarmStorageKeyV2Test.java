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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class WarmStorageKeyV2Test {

  private static final Address A = Address.fromHexString("0x0a");
  private static final Address B = Address.fromHexString("0x0b");

  @Test
  void slotIsTheSameKeyWhicheverBytes32HoldsIt() {
    final WarmStorageKeyV2 fromSload = new WarmStorageKeyV2(A, UInt256.valueOf(7));
    final WarmStorageKeyV2 fromAccessList = new WarmStorageKeyV2(A, Bytes32.leftPad(Bytes.of(7)));

    assertThat(fromSload).isEqualTo(fromAccessList);
    assertThat(fromSload.hashCode()).isEqualTo(fromAccessList.hashCode());
    assertThat(fromSload.compareTo(fromAccessList)).isZero();
  }

  @Test
  void keysOfAnotherAccountOrSlotDiffer() {
    final WarmStorageKeyV2 key = new WarmStorageKeyV2(A, UInt256.ONE);

    assertThat(key).isNotEqualTo(new WarmStorageKeyV2(B, UInt256.ONE));
    assertThat(key).isNotEqualTo(new WarmStorageKeyV2(A, UInt256.valueOf(2)));
  }

  @Test
  void orderAgreesWithEquals() {
    final Random random = new Random(5);
    final List<WarmStorageKeyV2> keys = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      final Address address = random.nextBoolean() ? A : B;
      final Bytes32 slot = random.nextBoolean() ? Bytes32.random(random) : UInt256.valueOf(i % 5);
      keys.add(new WarmStorageKeyV2(address, slot));
    }

    for (final WarmStorageKeyV2 first : keys) {
      for (final WarmStorageKeyV2 second : keys) {
        assertThat(first.compareTo(second) == 0).isEqualTo(first.equals(second));
        assertThat(Integer.signum(first.compareTo(second)))
            .isEqualTo(-Integer.signum(second.compareTo(first)));
      }
    }
  }

  @Test
  void keysDifferingOnlyInTheTopBitsOfTwoLanesDoNotCollide() {
    final byte[] base = Bytes32.random(new Random(9)).toArray();
    final Set<Integer> hashes = new HashSet<>();
    hashes.add(new WarmStorageKeyV2(A, Bytes32.wrap(base)).hashCode());

    // the top byte of a 32-bit lane is its first or its last, depending on the byte order
    final int[] topBytes = {0, 3, 4, 7, 8, 11, 12, 15, 16, 19, 20, 23, 24, 27, 28, 31};
    for (int first = 0; first < topBytes.length; first++) {
      for (int second = first + 1; second < topBytes.length; second++) {
        final byte[] slot = base.clone();
        slot[topBytes[first]] ^= (byte) 0x80;
        slot[topBytes[second]] ^= (byte) 0x80;
        hashes.add(new WarmStorageKeyV2(A, Bytes32.wrap(slot)).hashCode());
      }
    }

    assertThat(hashes).hasSize(1 + 120);
  }
}

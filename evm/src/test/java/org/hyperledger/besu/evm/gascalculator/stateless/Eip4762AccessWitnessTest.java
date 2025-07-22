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
package org.hyperledger.besu.evm.gascalculator.stateless;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.AccessEvent;
import org.hyperledger.besu.datatypes.Address;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Eip4762AccessWitnessTest {
  private Eip4762AccessWitness accessWitness;

  @BeforeEach
  void setUp() {
    accessWitness = new Eip4762AccessWitness();
  }

  @Test
  void touchReadAccessAddsToWitness() {
    Address address = Address.fromHexString("0x1234567");
    LeafAccessEvent expectedAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ZERO);
    final long gasCost =
        accessWitness.touchAddressAndChargeRead(address, UInt256.ZERO, Long.MAX_VALUE);
    assertThat(gasCost).isEqualTo(AccessEvent.getBranchReadCost() + AccessEvent.getLeafReadCost());
    assertThat(accessWitness.getLeafAccesses()).containsExactly(expectedAccessEvent);
  }

  @Test
  void touchReadAccessSameLeaf() {
    Address address = Address.fromHexString("0x1234567");
    LeafAccessEvent expectedAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ZERO);
    accessWitness.touchAddressAndChargeRead(address, UInt256.ZERO, Long.MAX_VALUE);
    final long gasCost =
        accessWitness.touchAddressAndChargeRead(address, UInt256.ZERO, Long.MAX_VALUE);
    assertThat(gasCost).isEqualTo(0);
    assertThat(accessWitness.getLeafAccesses()).containsExactly(expectedAccessEvent);
  }

  @Test
  void touchReadAccessMultipleLeavesAddsToWitness() {
    Address address = Address.fromHexString("0x1234567");
    LeafAccessEvent expectedAccessEvent1 =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ZERO);
    LeafAccessEvent expectedAccessEvent2 =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ONE);
    long gasCost = accessWitness.touchAddressAndChargeRead(address, UInt256.ZERO, Long.MAX_VALUE);
    gasCost += accessWitness.touchAddressAndChargeRead(address, UInt256.ONE, Long.MAX_VALUE);
    assertThat(gasCost)
        .isEqualTo(AccessEvent.getBranchReadCost() + AccessEvent.getLeafReadCost() * 2);
    assertThat(accessWitness.getLeafAccesses())
        .containsExactlyInAnyOrder(expectedAccessEvent1, expectedAccessEvent2);
  }

  @Test
  void singleWitnessAccessRevertedWhenNoGas() {
    Address address = Address.fromHexString("0x1234567");
    long expectedWitnessGasCos = AccessEvent.getBranchReadCost() + AccessEvent.getLeafReadCost();
    final long gasCost =
        accessWitness.touchAddressAndChargeRead(address, UInt256.ZERO, expectedWitnessGasCos - 1);
    assertThat(gasCost).isEqualTo(expectedWitnessGasCos);
    assertThat(accessWitness.getLeafAccesses()).isEmpty();
  }

  @Test
  void otherWitnessAccessRevertedWhenNoGas() {
    Address address = Address.fromHexString("0x1234567");
    LeafAccessEvent expectedAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ZERO);
    final long expectedGasCost = AccessEvent.getLeafReadCost();
    accessWitness.touchAddressAndChargeRead(address, UInt256.ZERO, Long.MAX_VALUE);
    final long gasCost =
        accessWitness.touchAddressAndChargeRead(address, UInt256.ONE, expectedGasCost - 1);
    assertThat(gasCost).isEqualTo(expectedGasCost);
    assertThat(accessWitness.getLeafAccesses()).containsExactly(expectedAccessEvent);
  }

  @Test
  void multipleWitnessAccessRevertedWhenNoGas() {
    Address address = Address.fromHexString("0x1234567");
    final long expectedGasCost =
        AccessEvent.getBranchReadCost()
            + AccessEvent.getLeafReadCost() * 2
            + AccessEvent.getBranchWriteCost()
            + AccessEvent.getLeafResetCost() * 2;
    final long gasCost =
        accessWitness.touchAndChargeContractCreateCompleted(address, expectedGasCost - 1);
    assertThat(gasCost).isEqualTo(expectedGasCost);
    assertThat(accessWitness.getLeafAccesses()).isEmpty();
  }

  @Test
  void touchWriteAccessAddsToWitness() {
    Address address = Address.fromHexString("0x1234567");
    LeafAccessEvent expectedBasicDataAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ZERO);
    LeafAccessEvent expectedCodeHashAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ONE);
    final long gasCost =
        accessWitness.touchAndChargeContractCreateCompleted(address, Long.MAX_VALUE);
    assertThat(gasCost)
        .isEqualTo(
            AccessEvent.getBranchReadCost()
                + AccessEvent.getLeafReadCost() * 2
                + AccessEvent.getBranchWriteCost()
                + AccessEvent.getLeafResetCost() * 2);
    assertThat(accessWitness.getLeafAccesses())
        .containsExactlyInAnyOrder(expectedBasicDataAccessEvent, expectedCodeHashAccessEvent);
  }

  @Test
  void touchWriteAccessSameLeaf() {
    Address address = Address.fromHexString("0x1234567");
    LeafAccessEvent expectedBasicDataAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ZERO);
    LeafAccessEvent expectedCodeHashAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ONE);
    accessWitness.touchAndChargeContractCreateCompleted(address, Long.MAX_VALUE);
    final long gasCost =
        accessWitness.touchAndChargeContractCreateCompleted(address, Long.MAX_VALUE);
    assertThat(gasCost).isEqualTo(0);
    assertThat(accessWitness.getLeafAccesses())
        .containsExactlyInAnyOrder(expectedBasicDataAccessEvent, expectedCodeHashAccessEvent);
  }

  @Test
  void touchReadAndWriteAccessSameLeaf() {
    Address address = Address.fromHexString("0x1234567");
    LeafAccessEvent expectedBasicDataAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ZERO);
    LeafAccessEvent expectedCodeHashAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ONE);
    accessWitness.touchAddressAndChargeRead(address, UInt256.ZERO, Long.MAX_VALUE);
    final long gasCost =
        accessWitness.touchAndChargeContractCreateCompleted(address, Long.MAX_VALUE);
    assertThat(gasCost)
        .isEqualTo(
            AccessEvent.getLeafReadCost()
                + AccessEvent.getBranchWriteCost()
                + AccessEvent.getLeafResetCost() * 2);
    assertThat(accessWitness.getLeafAccesses())
        .containsExactlyInAnyOrder(expectedBasicDataAccessEvent, expectedCodeHashAccessEvent);
  }

  @Test
  void touchWriteAndReadAccessSameLeaf() {
    Address address = Address.fromHexString("0x1234567");
    LeafAccessEvent expectedBasicDataAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ZERO);
    LeafAccessEvent expectedCodeHashAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ONE);
    accessWitness.touchAndChargeContractCreateCompleted(address, Long.MAX_VALUE);
    final long gasCost =
        accessWitness.touchAddressAndChargeRead(address, UInt256.ZERO, Long.MAX_VALUE);
    assertThat(gasCost).isEqualTo(0);
    assertThat(accessWitness.getLeafAccesses())
        .containsExactlyInAnyOrder(expectedBasicDataAccessEvent, expectedCodeHashAccessEvent);
  }

  @Test
  void revertedWriteNoRevertReadWhenNoGas() {
    Address address = Address.fromHexString("0x1234567");
    LeafAccessEvent expectedBasicDataAccessEvent =
        new LeafAccessEvent(new BranchAccessEvent(address, UInt256.ZERO), UInt256.ZERO);
    final long expectedGasCost =
        AccessEvent.getLeafReadCost()
            + AccessEvent.getBranchWriteCost()
            + AccessEvent.getLeafResetCost() * 2;
    accessWitness.touchAddressAndChargeRead(address, UInt256.ZERO, Long.MAX_VALUE);
    final long gasCost =
        accessWitness.touchAndChargeContractCreateCompleted(address, expectedGasCost - 1);
    assertThat(gasCost).isEqualTo(expectedGasCost);
    assertThat(accessWitness.getLeafAccesses())
        .containsExactlyInAnyOrder(expectedBasicDataAccessEvent);
  }
}

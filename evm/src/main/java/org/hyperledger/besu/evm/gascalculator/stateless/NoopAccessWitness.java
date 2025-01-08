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

import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;

/** An access witness that does nothing. */
public class NoopAccessWitness implements AccessWitness {

  private static NoopAccessWitness instance;

  private NoopAccessWitness() {}

  /**
   * Gets a singleton to an access witness that does nothing.
   *
   * @return the singleton
   */
  public static NoopAccessWitness get() {
    if (instance == null) {
      instance = new NoopAccessWitness();
    }
    return instance;
  }

  @Override
  public long touchAndChargeValueTransfer(
      final Address caller,
      final Address target,
      final boolean isAccountCreation,
      final long warmReadCost) {
    return 0;
  }

  @Override
  public long touchAndChargeValueTransferSelfDestruct(
      final Address caller,
      final Address target,
      final boolean isAccountCreation,
      final long warmReadCost) {
    return 0;
  }

  @Override
  public long touchAddressAndChargeRead(final Address address, final UInt256 leafKey) {
    return 0;
  }

  @Override
  public void touchBaseTx(final Address origin, final Optional<Address> target, final Wei value) {}

  @Override
  public long touchAndChargeProofOfAbsence(final Address address) {
    return 0;
  }

  @Override
  public long touchAndChargeContractCreateCompleted(final Address address) {
    return 0;
  }

  @Override
  public long touchAndChargeStorageLoad(final Address address, final UInt256 storageKey) {
    return 0;
  }

  @Override
  public long touchAndChargeStorageStore(
      final Address address, final UInt256 storageKey, final boolean hasPreviousValue) {
    return 0;
  }

  @Override
  public long touchCodeChunksUponContractCreation(final Address address, final long codeLength) {
    return 0;
  }

  @Override
  public long touchCodeChunks(
      final Address address,
      final boolean isContractInDeployment,
      final long offset,
      final long readSize,
      final long codeLength) {
    return 0;
  }
}

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

import java.util.Collections;
import java.util.List;

import org.apache.tuweni.units.bigints.UInt256;

public class NoopAccessWitness implements AccessWitness {

  private static NoopAccessWitness instance;

  private NoopAccessWitness() {}

  public static NoopAccessWitness get() {
    if (instance == null) {
      instance = new NoopAccessWitness();
    }
    return instance;
  }

  @Override
  public List<Address> keys() {
    return Collections.emptyList();
  }

  @Override
  public long touchAndChargeProofOfAbsence(final Address address) {
    return 0;
  }

  @Override
  public long touchAndChargeValueTransfer(
      final Address caller, final Address target, final boolean isAccountCreation) {
    return 0;
  }

  @Override
  public long touchAndChargeMessageCall(final Address address) {
    return 0;
  }

  @Override
  public long touchTxOriginAndComputeGas(final Address origin) {
    return 0;
  }

  @Override
  public long touchTxExistingAndComputeGas(final Address target, final boolean sendsValue) {
    return 0;
  }

  @Override
  public long touchAndChargeContractCreateInit(final Address address) {
    return 0;
  }

  @Override
  public long touchAndChargeContractCreateCompleted(final Address address) {
    return 0;
  }

  @Override
  public long touchAddressOnWriteResetAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return 0;
  }

  @Override
  public long touchAddressOnWriteSetAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return 0;
  }

  @Override
  public long touchAddressOnReadAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return 0;
  }

  @Override
  public List<UInt256> getStorageSlotTreeIndexes(final UInt256 storageKey) {
    return Collections.emptyList();
  }

  @Override
  public long touchCodeChunksUponContractCreation(final Address address, final long codeLength) {
    return 0;
  }

  @Override
  public long touchCodeChunks(
      final Address address, final long offset, final long readSize, final long codeLength) {
    return 0;
  }
}

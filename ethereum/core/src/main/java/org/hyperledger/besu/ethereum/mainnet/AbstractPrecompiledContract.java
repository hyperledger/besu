/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.enclave.EnclaveConfigurationException;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

/** Skeleton class for @{link PrecompileContract} implementations. */
public abstract class AbstractPrecompiledContract implements PrecompiledContract {

  private static final Logger LOG = LogManager.getLogger();

  private final GasCalculator gasCalculator;

  private final String name;

  protected AbstractPrecompiledContract(final String name, final GasCalculator gasCalculator) {
    this.name = name;
    this.gasCalculator = gasCalculator;
  }

  protected GasCalculator gasCalculator() {
    return gasCalculator;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public abstract Gas gasRequirement(Bytes input);

  @Override
  public abstract Bytes compute(Bytes input, MessageFrame messageFrame);

  protected boolean privateFromMatchesSenderKey(
      final Bytes transactionPrivateFrom, final String payloadSenderKey) {
    if (payloadSenderKey == null) {
      LOG.warn(
          "Missing sender key from Orion response. Upgrade Orion to 1.6 to enforce privateFrom check.");
      throw new EnclaveConfigurationException(
          "Incompatible Orion version. Orion version must be 1.6.0 or greater.");
    }

    if (transactionPrivateFrom == null || transactionPrivateFrom.isEmpty()) {
      LOG.warn("Private transaction is missing privateFrom");
      return false;
    }

    if (!payloadSenderKey.equals(transactionPrivateFrom.toBase64String())) {
      LOG.warn("Private transaction privateFrom doesn't match payload sender key");
      return false;
    }

    return true;
  }
}

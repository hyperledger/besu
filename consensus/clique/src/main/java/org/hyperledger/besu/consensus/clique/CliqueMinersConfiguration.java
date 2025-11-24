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
package org.hyperledger.besu.consensus.clique;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Util;

/**
 * Configuration specific to Clique consensus mining operations.
 * Replaces dependency on generic mining parameters for clique-specific settings.
 */
public class CliqueMinersConfiguration {
  
  private final Address localValidatorAddress;
  
  /**
   * Creates a new CliqueMinersConfiguration.
   *
   * @param localValidatorAddress the address of the local validator node
   */
  private CliqueMinersConfiguration(final Address localValidatorAddress) {
    this.localValidatorAddress = localValidatorAddress;
  }
  
  /**
   * Creates clique mining configuration from node key.
   * In clique, the coinbase is always the local validator address derived from the node key.
   *
   * @param nodeKey the node's cryptographic key
   * @return CliqueMinersConfiguration with local validator address
   */
  public static CliqueMinersConfiguration create(final NodeKey nodeKey) {
    final Address localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    return new CliqueMinersConfiguration(localAddress);
  }
  
  /**
   * Gets the local validator address that should be used as coinbase.
   * In clique consensus, this is always derived from the node's public key.
   *
   * @return the local validator address
   */
  public Address getLocalValidatorAddress() {
    return localValidatorAddress;
  }
  
}
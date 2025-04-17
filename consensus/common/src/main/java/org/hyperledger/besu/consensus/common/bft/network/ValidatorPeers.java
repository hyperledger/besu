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
package org.hyperledger.besu.consensus.common.bft.network;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Responsible for tracking the network peers which have a connection to this node, then
 * multicasting packets to ONLY the peers which have been identified as being validators.
 */
public class ValidatorPeers extends Peers {

  private final ValidatorProvider validatorProvider;

  /**
   * Instantiates a new Validator peers.
   *
   * @param validatorProvider the validator provider
   * @param protocolName the protocol name
   */
  public ValidatorPeers(final ValidatorProvider validatorProvider, final String protocolName) {
    super(protocolName);
    this.validatorProvider = validatorProvider;
  }

  @Override
  public void send(final MessageData message, final Collection<Address> denylist) {
    final Collection<Address> includedValidators =
        getLatestValidators().stream()
            .filter(a -> !denylist.contains(a))
            .collect(Collectors.toSet());
    sendMessageToSpecificAddresses(includedValidators, message);
  }

  private Collection<Address> getLatestValidators() {
    return validatorProvider.getValidatorsAtHead();
  }
}

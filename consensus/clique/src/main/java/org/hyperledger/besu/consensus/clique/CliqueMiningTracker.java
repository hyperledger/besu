/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.consensus.clique;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

public class CliqueMiningTracker {

  private final Address localAddress;
  private final ProtocolContext<CliqueContext> protocolContext;

  public CliqueMiningTracker(
      final Address localAddress, final ProtocolContext<CliqueContext> protocolContext) {
    this.localAddress = localAddress;
    this.protocolContext = protocolContext;
  }

  public boolean isProposerAfter(final BlockHeader header) {
    final Address nextProposer =
        CliqueHelpers.getProposerForBlockAfter(
            header, protocolContext.getConsensusState().getVoteTallyCache());
    return localAddress.equals(nextProposer);
  }

  public boolean canMakeBlockNextRound(final BlockHeader header) {
    return CliqueHelpers.addressIsAllowedToProduceNextBlock(localAddress, protocolContext, header);
  }

  public boolean blockCreatedLocally(final BlockHeader header) {
    return CliqueHelpers.getProposerOfBlock(header).equals(localAddress);
  }
}

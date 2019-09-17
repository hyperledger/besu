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
package org.hyperledger.besu.consensus.ibft.statemachine;

import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.payload.PreparedCertificate;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.stream.Collectors;

public class PreparedRoundArtifacts {

  private final Proposal proposal;
  private final Collection<Prepare> prepares;

  public PreparedRoundArtifacts(final Proposal proposal, final Collection<Prepare> prepares) {
    this.proposal = proposal;
    this.prepares = prepares;
  }

  public Block getBlock() {
    return proposal.getBlock();
  }

  public PreparedCertificate getPreparedCertificate() {
    return new PreparedCertificate(
        proposal.getSignedPayload(),
        prepares.stream().map(Prepare::getSignedPayload).collect(Collectors.toList()));
  }
}

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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;

/** The Clique mining tracker. */
public class CliqueMiningTracker {

  private final Address localAddress;
  private final ProtocolContext protocolContext;

  /**
   * Instantiates a new Clique mining tracker.
   *
   * @param localAddress the local address
   * @param protocolContext the protocol context
   */
  public CliqueMiningTracker(final Address localAddress, final ProtocolContext protocolContext) {
    this.localAddress = localAddress;
    this.protocolContext = protocolContext;
  }

  /**
   * Is next proposer.
   *
   * @param header the header
   * @return the boolean
   */
  public boolean isProposerAfter(final BlockHeader header) {
    final Address nextProposer = CliqueHelpers.getProposerForBlockAfter(header);
    return localAddress.equals(nextProposer);
  }

  /**
   * Is signer.
   *
   * @param header the header
   * @return the boolean
   */
  public boolean isSigner(final BlockHeader header) {
    return CliqueHelpers.isSigner(localAddress, protocolContext, header);
  }

  /**
   * Can make block next round.
   *
   * @param header the header
   * @return the boolean
   */
  public boolean canMakeBlockNextRound(final BlockHeader header) {
    return CliqueHelpers.addressIsAllowedToProduceNextBlock(localAddress, protocolContext, header);
  }

  /**
   * Block created locally.
   *
   * @param header the header
   * @return the boolean
   */
  public boolean blockCreatedLocally(final BlockHeader header) {
    return CliqueHelpers.getProposerOfBlock(header).equals(localAddress);
  }
}

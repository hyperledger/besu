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
package org.hyperledger.besu.consensus.qbft.validation;

import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("UnusedVariable")
public class MessageValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final SignedDataValidator signedDataValidator;
  private final ProposalBlockConsistencyValidator proposalConsistencyValidator;
  private final BlockValidator blockValidator;
  private final ProtocolContext protocolContext;

  public MessageValidator(
      final SignedDataValidator signedDataValidator,
      final ProposalBlockConsistencyValidator proposalConsistencyValidator,
      final BlockValidator blockValidator,
      final ProtocolContext protocolContext) {
    this.signedDataValidator = signedDataValidator;
    this.proposalConsistencyValidator = proposalConsistencyValidator;
    this.blockValidator = blockValidator;
    this.protocolContext = protocolContext;
  }

  public boolean validateProposal(final Proposal msg) {
    return signedDataValidator.validateProposal(msg.getSignedPayload());
  }

  public boolean validatePrepare(final Prepare msg) {
    return signedDataValidator.validatePrepare(msg.getSignedPayload());
  }

  public boolean validateCommit(final Commit msg) {
    return signedDataValidator.validateCommit(msg.getSignedPayload());
  }
}

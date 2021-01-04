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

public class MessageValidator {

  private final SignedDataValidator signedDataValidator;
  private final ProposalValidator proposalValidator;

  public MessageValidator(
      final SignedDataValidator signedDataValidator, final ProposalValidator proposalValidator) {
    this.signedDataValidator = signedDataValidator;
    this.proposalValidator = proposalValidator;
  }

  public boolean validateProposal(final Proposal msg) {
    return proposalValidator.validateProposal(msg);
  }

  public boolean validatePrepare(final Prepare msg) {
    return signedDataValidator.validatePrepare(msg.getSignedPayload());
  }

  public boolean validateCommit(final Commit msg) {
    return signedDataValidator.validateCommit(msg.getSignedPayload());
  }
}

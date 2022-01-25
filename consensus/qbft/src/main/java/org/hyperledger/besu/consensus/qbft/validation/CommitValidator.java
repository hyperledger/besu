/*
 * Copyright 2020 ConsenSys AG.
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

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.payload.CommitPayload;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitValidator {

  private static final String ERROR_PREFIX = "Invalid Commit Message";

  private static final Logger LOG = LoggerFactory.getLogger(CommitValidator.class);

  private final Collection<Address> validators;
  private final ConsensusRoundIdentifier targetRound;
  private final Hash expectedDigest;
  private final Hash expectedCommitDigest;

  public CommitValidator(
      final Collection<Address> validators,
      final ConsensusRoundIdentifier targetRound,
      final Hash expectedDigest,
      final Hash expectedCommitDigest) {
    this.validators = validators;
    this.targetRound = targetRound;
    this.expectedDigest = expectedDigest;
    this.expectedCommitDigest = expectedCommitDigest;
  }

  public boolean validate(final Commit msg) {
    return validate(msg.getSignedPayload());
  }

  public boolean validate(final SignedData<CommitPayload> signedPayload) {
    if (!validators.contains(signedPayload.getAuthor())) {
      LOG.info("{}: did not originate from a recognized validator.", ERROR_PREFIX);
      return false;
    }

    final CommitPayload payload = signedPayload.getPayload();

    if (!payload.getRoundIdentifier().equals(targetRound)) {
      LOG.info(
          "{}: did not target expected round {} was {}",
          ERROR_PREFIX,
          targetRound,
          payload.getRoundIdentifier());
      return false;
    }

    if (!payload.getDigest().equals(expectedDigest)) {
      LOG.info(
          "{}: did not contain expected digest {} was {}",
          ERROR_PREFIX,
          expectedDigest,
          payload.getDigest());
      return false;
    }

    final Address commitSealCreator =
        Util.signatureToAddress(payload.getCommitSeal(), expectedCommitDigest);

    if (!commitSealCreator.equals(signedPayload.getAuthor())) {
      LOG.info(
          "{}: Seal was not created by the message transmitter {} was {}",
          ERROR_PREFIX,
          commitSealCreator,
          signedPayload.getAuthor());
      return false;
    }

    return true;
  }
}

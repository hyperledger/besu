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

import static org.hyperledger.besu.consensus.qbft.validation.ValidationHelpers.allAuthorsBelongToValidatorList;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.ethereum.core.Address;

import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Note: This does not validate that the received payload is for a future round, only that it was
 * signed by a known validator, and is for the current chain height. Future-round check must be
 * performed elsewhere (eg. the BlockHeightManager)
 */
public class RoundChangePayloadValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Collection<Address> validators;
  private final long chainHeight;

  public RoundChangePayloadValidator(final Collection<Address> validators, final long chainHeight) {
    this.validators = validators;
    this.chainHeight = chainHeight;
  }

  public boolean validateRoundChange(final SignedData<RoundChangePayload> payload) {

    if (!allAuthorsBelongToValidatorList(List.of(payload), validators)) {
      LOG.info(
          "Invalid RoundChange message: was not transmitted by a validator for the associated"
              + " round.");
      return false;
    }

    final ConsensusRoundIdentifier targetRound = payload.getPayload().getRoundIdentifier();

    if (targetRound.getSequenceNumber() != chainHeight) {
      LOG.info("Invalid RoundChange payload: not valid for local chain height.");
      return false;
    }

    return true;
  }
}

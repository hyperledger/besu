/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft.validation;

import static java.util.Optional.empty;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.consensus.ibft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;

import org.junit.Test;

public class RoundChangeMessageValidatorTest {

  private final RoundChangePayloadValidator payloadValidator =
      mock(RoundChangePayloadValidator.class);
  private final KeyPair keyPair = KeyPair.generate();
  private final MessageFactory messageFactory = new MessageFactory(keyPair);
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 1);

  private ProposalBlockConsistencyValidator proposalBlockConsistencyValidator =
      mock(ProposalBlockConsistencyValidator.class);

  private final RoundChangeMessageValidator validator =
      new RoundChangeMessageValidator(payloadValidator, proposalBlockConsistencyValidator);

  @Test
  public void underlyingPayloadValidatorIsInvokedWithCorrectParameters() {
    final RoundChange message = messageFactory.createRoundChange(roundIdentifier, empty());

    validator.validateRoundChange(message);
    verify(payloadValidator, times(1)).validateRoundChange(message.getSignedPayload());
  }
}

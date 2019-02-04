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
package tech.pegasys.pantheon.consensus.ibft.validation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.TestHelpers;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.util.List;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;

public class MessageValidatorTest {

  private KeyPair keyPair = KeyPair.generate();
  private MessageFactory messageFactory = new MessageFactory(keyPair);
  private ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 1);

  private SignedDataValidator signedDataValidator = mock(SignedDataValidator.class);

  private MessageValidator messageValidator = new MessageValidator(signedDataValidator);

  private final List<Address> validators =
      Lists.newArrayList(
          AddressHelpers.ofValue(0),
          AddressHelpers.ofValue(1),
          AddressHelpers.ofValue(2),
          AddressHelpers.ofValue(3));

  private final Block block =
      TestHelpers.createProposalBlock(validators, roundIdentifier.getRoundNumber());

  @Before
  public void setup() {
    when(signedDataValidator.addSignedProposalPayload(any())).thenReturn(true);
    when(signedDataValidator.validatePrepareMessage(any())).thenReturn(true);
    when(signedDataValidator.validateCommmitMessage(any())).thenReturn(true);
  }

  @Test
  public void messageValidatorDefersToUnderlyingSignedDataValidator() {
    final Proposal proposal = messageFactory.createSignedProposalPayload(roundIdentifier, block);

    final Prepare prepare =
        messageFactory.createSignedPreparePayload(roundIdentifier, block.getHash());

    final Commit commit =
        messageFactory.createSignedCommitPayload(
            roundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), keyPair));

    messageValidator.addSignedProposalPayload(proposal);
    verify(signedDataValidator, times(1)).addSignedProposalPayload(proposal.getSignedPayload());

    messageValidator.validatePrepareMessage(prepare);
    verify(signedDataValidator, times(1)).validatePrepareMessage(prepare.getSignedPayload());

    messageValidator.validateCommitMessage(commit);
    verify(signedDataValidator, times(1)).validateCommmitMessage(commit.getSignedPayload());
  }
}

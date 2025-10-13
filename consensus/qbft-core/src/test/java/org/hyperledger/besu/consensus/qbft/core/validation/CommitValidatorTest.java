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
package org.hyperledger.besu.consensus.qbft.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockCodec;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Hash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CommitValidatorTest {

  private static final int VALIDATOR_COUNT = 4;

  private final ConsensusRoundIdentifier round = new ConsensusRoundIdentifier(1, 0);
  private final Hash expectedHash = Hash.fromHexStringLenient("0x1");
  private final Hash expectedCommitHash = Hash.fromHexStringLenient("0x1");
  private QbftNodeList validators;
  private CommitValidator validator;
  private @Mock QbftBlockCodec qbftBlockCodec;

  @BeforeEach
  public void setup() {
    validators = QbftNodeList.createNodes(VALIDATOR_COUNT, qbftBlockCodec);
    validator =
        new CommitValidator(validators.getNodeAddresses(), round, expectedHash, expectedCommitHash);
  }

  @Test
  public void commitIsValidIfItMatchesExpectedValues() {
    for (int i = 0; i < VALIDATOR_COUNT; i++) {
      final SECPSignature commitSeal = validators.getNode(i).getNodeKey().sign(expectedHash);
      final Commit msg =
          validators.getMessageFactory(i).createCommit(round, expectedHash, commitSeal);
      assertThat(validator.validate(msg)).isTrue();
    }
  }

  @Test
  public void commitSignedByANonValidatorFails() {
    final QbftNode nonValidator = QbftNode.create(qbftBlockCodec);
    final SECPSignature commitSeal = nonValidator.getNodeKey().sign(expectedHash);
    final Commit msg =
        nonValidator.getMessageFactory().createCommit(round, expectedHash, commitSeal);
    assertThat(validator.validate(msg)).isFalse();
  }

  @Test
  public void commitForWrongRoundFails() {
    final SECPSignature commitSeal = validators.getNode(0).getNodeKey().sign(expectedHash);
    final Commit msg =
        validators
            .getMessageFactory(0)
            .createCommit(ConsensusRoundHelpers.createFrom(round, 0, +1), expectedHash, commitSeal);
    assertThat(validator.validate(msg)).isFalse();
  }

  @Test
  public void commitForWrongSequenceFails() {
    final SECPSignature commitSeal = validators.getNode(0).getNodeKey().sign(expectedHash);
    final Commit msg =
        validators
            .getMessageFactory(0)
            .createCommit(ConsensusRoundHelpers.createFrom(round, +1, 0), expectedHash, commitSeal);
    assertThat(validator.validate(msg)).isFalse();
  }

  @Test
  public void commitWithWrongDigestFails() {
    final SECPSignature commitSeal = validators.getNode(0).getNodeKey().sign(expectedHash);
    final Commit msg =
        validators
            .getMessageFactory(0)
            .createCommit(round, Hash.fromHexStringLenient("0x2"), commitSeal);
    assertThat(validator.validate(msg)).isFalse();
  }

  @Test
  public void commitWithMismatchedSealFails() {
    final SECPSignature commitSeal =
        validators.getNode(0).getNodeKey().sign(Hash.fromHexStringLenient("0x2"));
    final Commit msg =
        validators.getMessageFactory(0).createCommit(round, expectedHash, commitSeal);

    assertThat(validator.validate(msg)).isFalse();
  }
}

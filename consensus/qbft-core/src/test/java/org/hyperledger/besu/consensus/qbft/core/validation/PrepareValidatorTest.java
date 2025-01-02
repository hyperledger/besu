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
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.datatypes.Hash;

import org.junit.jupiter.api.Test;

public class PrepareValidatorTest {

  private static final int VALIDATOR_COUNT = 4;

  private final QbftNodeList validators = QbftNodeList.createNodes(VALIDATOR_COUNT);
  private final ConsensusRoundIdentifier round = new ConsensusRoundIdentifier(1, 0);
  private final Hash expectedHash = Hash.fromHexStringLenient("0x1");
  private final PrepareValidator validator =
      new PrepareValidator(validators.getNodeAddresses(), round, expectedHash);

  @Test
  public void prepareIsValidIfItMatchesExpectedValues() {
    for (int i = 0; i < VALIDATOR_COUNT; i++) {
      final Prepare msg = validators.getMessageFactory(i).createPrepare(round, expectedHash);
      assertThat(validator.validate(msg)).isTrue();
    }
  }

  @Test
  public void prepareSignedByANonValidatorFails() {
    final QbftNode nonValidator = QbftNode.create();

    final Prepare msg = nonValidator.getMessageFactory().createPrepare(round, expectedHash);
    assertThat(validator.validate(msg)).isFalse();
  }

  @Test
  public void prepareForWrongRoundFails() {
    final Prepare msg =
        validators
            .getMessageFactory(0)
            .createPrepare(ConsensusRoundHelpers.createFrom(round, 0, +1), expectedHash);
    assertThat(validator.validate(msg)).isFalse();
  }

  @Test
  public void prepareForWrongSequenceFails() {
    final Prepare msg =
        validators
            .getMessageFactory(0)
            .createPrepare(ConsensusRoundHelpers.createFrom(round, +1, 0), expectedHash);
    assertThat(validator.validate(msg)).isFalse();
  }

  @Test
  public void prepareWithWrongDigestFails() {
    final Prepare msg =
        validators.getMessageFactory(0).createPrepare(round, Hash.fromHexStringLenient("0x2"));
    assertThat(validator.validate(msg)).isFalse();
  }
}

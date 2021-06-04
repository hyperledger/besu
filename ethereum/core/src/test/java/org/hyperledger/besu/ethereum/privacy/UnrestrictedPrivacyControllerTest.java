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
package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.markertransaction.PrivateMarkerTransactionFactory;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class UnrestrictedPrivacyControllerTest {
  private static final String ALICE = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String BOB = "OnviftjiizpjRt+HTuFBsKo2bVqD+nNlNYL5EE7y3Id=";

  private final PrivacyParameters privacyParameters = PrivacyParameters.DEFAULT;
  UnrestrictedPrivacyController privacyController =
      new UnrestrictedPrivacyController(
          mock(Blockchain.class),
          privacyParameters,
          Optional.empty(),
          mock(PrivateMarkerTransactionFactory.class),
          mock(PrivateTransactionSimulator.class),
          mock(PrivateNonceProvider.class),
          mock(PrivateWorldStateReader.class));

  @Test
  public void aliceCanCreatePrivacyGroup() {
    privacyParameters.setEnclavePublicKey(ALICE);

    privacyController.createPrivacyGroup(
        List.of(ALICE, BOB), "Unrestricted", "Unrestricted", ALICE);
  }

  @Test
  public void aliceCanNotCreatePrivacyGroupWhenNotAMember() {
    privacyParameters.setEnclavePublicKey(ALICE);

    assertThatThrownBy(
            () ->
                privacyController.createPrivacyGroup(
                    List.of(BOB), "Unrestricted", "Unrestricted", ALICE))
        .isInstanceOf(PrivacyValidationException.class)
        .hasMessage("Privacy group addresses must contain the enclave public key");
  }
}

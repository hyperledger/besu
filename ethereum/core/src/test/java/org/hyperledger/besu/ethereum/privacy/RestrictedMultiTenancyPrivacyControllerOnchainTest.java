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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RestrictedMultiTenancyPrivacyControllerOnchainTest {

  private static final String ENCLAVE_PUBLIC_KEY1 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String ENCLAVE_PUBLIC_KEY2 = "OnviftjiizpjRt+HTuFBsKo2bVqD+nNlNYL5EE7y3Id=";
  private static final String PRIVACY_GROUP_ID = "nNlNYL5EE7y3IdM=";
  private static final ArrayList<Log> LOGS = new ArrayList<>();
  private static final PrivacyGroup ONCHAIN_PRIVACY_GROUP =
      new PrivacyGroup("", PrivacyGroup.Type.ONCHAIN, "", "", List.of(ENCLAVE_PUBLIC_KEY1));

  private final PrivacyController privacyController = mock(PrivacyController.class);
  private final Enclave enclave = mock(Enclave.class);
  private final OnchainPrivacyGroupContract onchainPrivacyGroupContract =
      mock(OnchainPrivacyGroupContract.class);

  private RestrictedMultiTenancyPrivacyController multiTenancyPrivacyController;

  @Before
  public void setup() {
    when(onchainPrivacyGroupContract.getPrivacyGroupByIdAndBlockNumber(
            PRIVACY_GROUP_ID, Optional.of(1L)))
        .thenReturn(Optional.of(ONCHAIN_PRIVACY_GROUP));

    multiTenancyPrivacyController =
        new RestrictedMultiTenancyPrivacyController(
            privacyController,
            Optional.of(BigInteger.valueOf(2018)),
            enclave,
            Optional.of(onchainPrivacyGroupContract));
  }

  @Test
  public void simulatePrivateTransactionSucceedsForPresentEnclaveKey() {
    when(privacyController.simulatePrivateTransaction(any(), any(), any(), any(long.class)))
        .thenReturn(
            Optional.of(
                TransactionProcessingResult.successful(
                    LOGS, 0, 0, Bytes.EMPTY, ValidationResult.valid())));
    final Optional<TransactionProcessingResult> result =
        multiTenancyPrivacyController.simulatePrivateTransaction(
            PRIVACY_GROUP_ID,
            ENCLAVE_PUBLIC_KEY1,
            new CallParameter(Address.ZERO, Address.ZERO, 0, Wei.ZERO, Wei.ZERO, Bytes.EMPTY),
            1);

    assertThat(result.isPresent()).isTrue();
    assertThat(result.get().getValidationResult().isValid()).isTrue();
  }

  @Test(expected = MultiTenancyValidationException.class)
  public void simulatePrivateTransactionFailsForAbsentEnclaveKey() {
    multiTenancyPrivacyController.simulatePrivateTransaction(
        PRIVACY_GROUP_ID,
        ENCLAVE_PUBLIC_KEY2,
        new CallParameter(Address.ZERO, Address.ZERO, 0, Wei.ZERO, Wei.ZERO, Bytes.EMPTY),
        1);
  }
}

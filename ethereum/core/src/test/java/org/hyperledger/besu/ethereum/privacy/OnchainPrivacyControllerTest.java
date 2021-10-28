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

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OnchainPrivacyControllerTest {

  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String ENCLAVE_KEY2 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String PRIVACY_GROUP_ID = "DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w=";
  private static final String MOCK_TRANSACTION_SIMULATOR_RESULT_OUTPUT_BYTES_PREFIX =
      "0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002";

  private OnchainPrivacyController privacyController;
  private PrivateTransactionValidator privateTransactionValidator;
  private PrivateNonceProvider privateNonceProvider;
  private PrivateStateRootResolver privateStateRootResolver;
  private PrivateTransactionSimulator privateTransactionSimulator;
  private PrivateWorldStateReader privateWorldStateReader;
  private Blockchain blockchain;
  private PrivateStateStorage privateStateStorage;
  private Enclave enclave;

  private Enclave mockEnclave() {
    final Enclave mockEnclave = mock(Enclave.class);
    return mockEnclave;
  }

  private PrivateTransactionValidator mockPrivateTransactionValidator() {
    final PrivateTransactionValidator validator = mock(PrivateTransactionValidator.class);
    return validator;
  }

  @Before
  public void setUp() throws Exception {
    blockchain = mock(Blockchain.class);
    privateTransactionSimulator = mock(PrivateTransactionSimulator.class);
    privateStateStorage = mock(PrivateStateStorage.class);
    privateNonceProvider = mock(ChainHeadPrivateNonceProvider.class);
    privateStateRootResolver = mock(PrivateStateRootResolver.class);

    privateWorldStateReader = mock(PrivateWorldStateReader.class);

    privateTransactionValidator = mockPrivateTransactionValidator();
    enclave = mockEnclave();

    privacyController =
        new OnchainPrivacyController(
            blockchain,
            privateStateStorage,
            enclave,
            privateTransactionValidator,
            privateTransactionSimulator,
            privateNonceProvider,
            privateWorldStateReader,
            privateStateRootResolver);
  }

  @Test
  public void findOnchainPrivacyGroups() {
    final List<String> privacyGroupAddresses = newArrayList(ENCLAVE_PUBLIC_KEY, ENCLAVE_KEY2);

    final PrivacyGroup privacyGroup =
        new PrivacyGroup(
            PRIVACY_GROUP_ID, PrivacyGroup.Type.ONCHAIN, "", "", privacyGroupAddresses);

    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        new PrivacyGroupHeadBlockMap(
            Map.of(Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID)), Hash.ZERO));
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any()))
        .thenReturn(Optional.of(privacyGroupHeadBlockMap));

    when(privateTransactionSimulator.process(any(), any()))
        .thenReturn(
            Optional.of(
                new TransactionProcessingResult(
                    TransactionProcessingResult.Status.SUCCESSFUL,
                    emptyList(),
                    0,
                    0,
                    Bytes.fromHexString(
                        MOCK_TRANSACTION_SIMULATOR_RESULT_OUTPUT_BYTES_PREFIX
                            + Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY).toUnprefixedHexString()
                            + Bytes.fromBase64String(ENCLAVE_KEY2).toUnprefixedHexString()),
                    ValidationResult.valid(),
                    Optional.empty())));

    final List<PrivacyGroup> privacyGroups =
        List.of(
            privacyController.findPrivacyGroupByMembers(privacyGroupAddresses, ENCLAVE_PUBLIC_KEY));
    assertThat(privacyGroups).hasSize(1);
    assertThat(privacyGroups.get(0)).isEqualToComparingFieldByField(privacyGroup);
    verify(privateStateStorage).getPrivacyGroupHeadBlockMap(any());
    verify(privateTransactionSimulator).process(any(), any());
  }

  @Test
  public void createsPrivacyGroup() {
    Assertions.assertThatThrownBy(
            () ->
                privacyController.createPrivacyGroup(Lists.emptyList(), "", "", ENCLAVE_PUBLIC_KEY))
        .isInstanceOf(PrivacyConfigurationNotSupportedException.class)
        .hasMessageContaining("Method not supported when using onchain privacy");
  }

  @Test
  public void deletesPrivacyGroup() {
    Assertions.assertThatThrownBy(
            () -> privacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY))
        .isInstanceOf(PrivacyConfigurationNotSupportedException.class)
        .hasMessageContaining("Method not supported when using onchain privacy");
  }
}

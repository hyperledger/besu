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
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.generatePrivateBlockMetadata;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.PrivateTransactionReceiptTestFixture;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PrivateWorldStateReaderTest {

  private final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private final Bytes32 PRIVACY_GROUP_ID_BYTES =
      Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID));
  private final Bytes contractCode = Bytes.fromBase64String("ZXhhbXBsZQ==");
  private final Address contractAddress = Address.ZERO;
  private final Hash blockHash = Hash.ZERO;
  private final Hash stateRootHash = Hash.ZERO;

  @Mock private PrivateStateRootResolver privateStateRootResolver;
  @Mock private WorldStateArchive privateWorldStateArchive;
  @Mock private WorldState privateWorldState;
  @Mock private PrivateStateStorage privateStateStorage;
  @Mock private Account contractAccount;

  private PrivateWorldStateReader privateWorldStateReader;

  @BeforeEach
  public void before() {
    privateWorldStateReader =
        new PrivateWorldStateReader(
            privateStateRootResolver, privateWorldStateArchive, privateStateStorage);
  }

  @AfterEach
  public void after() {
    Mockito.reset(privateStateStorage);
  }

  @Test
  public void absentPrivateWorldStateReturnsEmpty() {
    final Bytes32 privacyGroupBytes = Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID));
    final Address contractAddress = Address.ZERO;

    when(privateStateRootResolver.resolveLastStateRoot(eq(privacyGroupBytes), eq(blockHash)))
        .thenReturn(stateRootHash);
    when(privateWorldStateArchive.get(eq(stateRootHash), any())).thenReturn(Optional.empty());

    final Optional<Bytes> maybecontractCode =
        privateWorldStateReader.getContractCode(PRIVACY_GROUP_ID, blockHash, contractAddress);

    assertThat(maybecontractCode).isNotPresent();
  }

  @Test
  public void absentAccountReturnsEmpty() {
    final Bytes32 privacyGroupBytes = Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID));
    final Address contractAddress = Address.ZERO;

    when(privateStateRootResolver.resolveLastStateRoot(eq(privacyGroupBytes), eq(blockHash)))
        .thenReturn(stateRootHash);
    when(privateWorldStateArchive.get(eq(stateRootHash), any()))
        .thenReturn(Optional.of(privateWorldState));
    when(privateWorldState.get(eq(contractAddress))).thenReturn(null);

    final Optional<Bytes> maybeContractCode =
        privateWorldStateReader.getContractCode(PRIVACY_GROUP_ID, blockHash, contractAddress);

    assertThat(maybeContractCode).isNotPresent();
  }

  @Test
  public void existingAccountWithEmptyCodeReturnsEmpty() {
    final Bytes32 privacyGroupBytes = Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID));
    final Address contractAddress = Address.ZERO;

    when(privateStateRootResolver.resolveLastStateRoot(eq(privacyGroupBytes), eq(blockHash)))
        .thenReturn(stateRootHash);
    when(privateWorldStateArchive.get(eq(stateRootHash), any()))
        .thenReturn(Optional.of(privateWorldState));
    when(privateWorldState.get(eq(contractAddress))).thenReturn(contractAccount);
    when(contractAccount.getCode()).thenReturn(null);

    final Optional<Bytes> maybeContractCode =
        privateWorldStateReader.getContractCode(PRIVACY_GROUP_ID, blockHash, contractAddress);

    assertThat(maybeContractCode).isNotPresent();
  }

  @Test
  public void existingAccountWithCodeReturnsExpectedBytes() {
    when(privateStateRootResolver.resolveLastStateRoot(eq(PRIVACY_GROUP_ID_BYTES), eq(blockHash)))
        .thenReturn(stateRootHash);
    when(privateWorldStateArchive.get(eq(stateRootHash), any()))
        .thenReturn(Optional.of(privateWorldState));
    when(privateWorldState.get(eq(contractAddress))).thenReturn(contractAccount);
    when(contractAccount.getCode()).thenReturn(contractCode);

    final Optional<Bytes> maybeContractCode =
        privateWorldStateReader.getContractCode(PRIVACY_GROUP_ID, blockHash, contractAddress);

    assertThat(maybeContractCode).isPresent().hasValue(contractCode);
  }

  @Test
  public void getPrivateTransactionsMetadataReturnEmptyListWhenNoPrivateBlockFound() {
    when(privateStateStorage.getPrivateBlockMetadata(eq(blockHash), eq(PRIVACY_GROUP_ID_BYTES)))
        .thenReturn(Optional.empty());

    final List<PrivateTransactionMetadata> privateTransactionsMetadata =
        privateWorldStateReader.getPrivateTransactionMetadataList(PRIVACY_GROUP_ID, blockHash);

    assertThat(privateTransactionsMetadata).isEmpty();
  }

  @Test
  public void getPrivateTransactionsMetadataReturnExpectedMetadataList() {
    final PrivateBlockMetadata privateBlockMetadata = generatePrivateBlockMetadata(3);
    when(privateStateStorage.getPrivateBlockMetadata(eq(blockHash), eq(PRIVACY_GROUP_ID_BYTES)))
        .thenReturn(Optional.of(privateBlockMetadata));

    final List<PrivateTransactionMetadata> privateTransactionsMetadata =
        privateWorldStateReader.getPrivateTransactionMetadataList(PRIVACY_GROUP_ID, blockHash);

    assertThat(privateTransactionsMetadata)
        .hasSize(3)
        .containsAll(privateBlockMetadata.getPrivateTransactionMetadataList());
  }

  @Test
  public void getPrivateTransactionReceiptReturnEmptyIfReceiptDoesNotExist() {
    final Bytes32 blockHash = Bytes32.random();
    final Bytes32 transactionHash = Bytes32.random();

    when(privateStateStorage.getTransactionReceipt(blockHash, transactionHash))
        .thenReturn(Optional.empty());

    final Optional<PrivateTransactionReceipt> privateTransactionReceipt =
        privateWorldStateReader.getPrivateTransactionReceipt(
            Hash.hash(blockHash), Hash.hash(transactionHash));

    assertThat(privateTransactionReceipt).isEmpty();
  }

  @Test
  public void getPrivateTransactionReceiptReturnExpectedReceipt() {
    final Hash blockHash = Hash.hash(Bytes32.random());
    final Hash transactionHash = Hash.hash(Bytes32.random());

    final PrivateTransactionReceipt receipt = new PrivateTransactionReceiptTestFixture().create();

    when(privateStateStorage.getTransactionReceipt(blockHash, transactionHash))
        .thenReturn(Optional.of(receipt));

    final Optional<PrivateTransactionReceipt> privateTransactionReceipt =
        privateWorldStateReader.getPrivateTransactionReceipt(blockHash, transactionHash);

    assertThat(privateTransactionReceipt).hasValue(receipt);
  }
}

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivateWorldStateReaderTest {

  private final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private final Bytes32 privacyGroupBytes = Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID));
  private final Bytes contractCode = Bytes.fromBase64String("ZXhhbXBsZQ==");
  private final Address contractAddress = Address.ZERO;
  private final Hash blockHash = Hash.ZERO;
  private final Hash stateRootHash = Hash.ZERO;

  @Mock private PrivateStateRootResolver privateStateRootResolver;
  @Mock private WorldStateArchive privateWorldStateArchive;
  @Mock private WorldState privateWorldState;
  @Mock private Account contractAccount;

  private PrivateWorldStateReader privateWorldStateReader;

  @Before
  public void before() {
    privateWorldStateReader =
        new PrivateWorldStateReader(privateStateRootResolver, privateWorldStateArchive);
  }

  @Test
  public void absentPrivateWorldStateReturnsEmpty() {
    final Bytes32 privacyGroupBytes = Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID));
    final Address contractAddress = Address.ZERO;

    when(privateStateRootResolver.resolveLastStateRoot(eq(privacyGroupBytes), eq(blockHash)))
        .thenReturn(stateRootHash);
    when(privateWorldStateArchive.get(eq(stateRootHash))).thenReturn(Optional.empty());

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
    when(privateWorldStateArchive.get(eq(stateRootHash)))
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
    when(privateWorldStateArchive.get(eq(stateRootHash)))
        .thenReturn(Optional.of(privateWorldState));
    when(privateWorldState.get(eq(contractAddress))).thenReturn(contractAccount);
    when(contractAccount.getCode()).thenReturn(null);

    final Optional<Bytes> maybeContractCode =
        privateWorldStateReader.getContractCode(PRIVACY_GROUP_ID, blockHash, contractAddress);

    assertThat(maybeContractCode).isNotPresent();
  }

  @Test
  public void existingAccountWithCodeReturnsExpectedBytes() {
    when(privateStateRootResolver.resolveLastStateRoot(eq(privacyGroupBytes), eq(blockHash)))
        .thenReturn(stateRootHash);
    when(privateWorldStateArchive.get(eq(stateRootHash)))
        .thenReturn(Optional.of(privateWorldState));
    when(privateWorldState.get(eq(contractAddress))).thenReturn(contractAccount);
    when(contractAccount.getCode()).thenReturn(contractCode);

    final Optional<Bytes> maybeContractCode =
        privateWorldStateReader.getContractCode(PRIVACY_GROUP_ID, blockHash, contractAddress);

    assertThat(maybeContractCode).isPresent().hasValue(contractCode);
  }
}

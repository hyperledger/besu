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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ChainHeadPrivateNonceProviderTest {
  private static final Bytes32 PRIVACY_GROUP_ID =
      Bytes32.wrap(Bytes.fromBase64String("DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w="));
  private static final Address ADDRESS = Address.fromHexString("55");

  private Account account;
  private WorldState worldState;
  private ChainHeadPrivateNonceProvider privateNonceProvider;
  private WorldStateArchive privateWorldStateArchive;
  private PrivateStateRootResolver privateStateRootResolver;

  @BeforeEach
  public void setUp() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final Blockchain blockchain = mock(Blockchain.class);
    when(blockchain.getChainHeadHeader()).thenReturn(gen.header());

    account = mock(Account.class);
    worldState = mock(WorldState.class);

    privateStateRootResolver = mock(PrivateStateRootResolver.class);
    privateWorldStateArchive = mock(WorldStateArchive.class);
    privateNonceProvider =
        new ChainHeadPrivateNonceProvider(
            blockchain, privateStateRootResolver, privateWorldStateArchive);
  }

  @Test
  public void determineNonceForPrivacyGroupRequestWhenPrivateStateDoesNotExist() {
    when(privateStateRootResolver.resolveLastStateRoot(any(Bytes32.class), any(Hash.class)))
        .thenReturn(Hash.ZERO);
    when(privateWorldStateArchive.get(any(Hash.class), any(Hash.class)))
        .thenReturn(Optional.empty());

    final long nonce = privateNonceProvider.getNonce(ADDRESS, PRIVACY_GROUP_ID);

    assertThat(nonce).isEqualTo(Account.DEFAULT_NONCE);
  }

  @Test
  public void determineNonceForPrivacyGroupRequestWhenAccountExists() {
    when(account.getNonce()).thenReturn(4L);
    when(worldState.get(any(Address.class))).thenReturn(account);
    when(privateStateRootResolver.resolveLastStateRoot(any(Bytes32.class), any(Hash.class)))
        .thenReturn(Hash.ZERO);
    when(privateWorldStateArchive.get(any(Hash.class), any(Hash.class)))
        .thenReturn(Optional.of(worldState));

    final long nonce = privateNonceProvider.getNonce(ADDRESS, PRIVACY_GROUP_ID);

    assertThat(nonce).isEqualTo(4L);
  }

  @Test
  public void determineNonceForPrivacyGroupRequestWhenAccountDoesNotExist() {
    when(privateStateRootResolver.resolveLastStateRoot(any(Bytes32.class), any(Hash.class)))
        .thenReturn(Hash.ZERO);
    when(privateWorldStateArchive.get(any(Hash.class), any(Hash.class)))
        .thenReturn(Optional.of(worldState));

    final long nonce = privateNonceProvider.getNonce(ADDRESS, PRIVACY_GROUP_ID);

    assertThat(nonce).isEqualTo(Account.DEFAULT_NONCE);
    verifyNoInteractions(account);
  }
}

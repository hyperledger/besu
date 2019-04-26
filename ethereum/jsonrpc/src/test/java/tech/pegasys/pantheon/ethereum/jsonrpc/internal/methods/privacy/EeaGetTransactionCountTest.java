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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.privacy.PrivateStateStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

import org.junit.Test;

public class EeaGetTransactionCountTest {

  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private final PrivateStateStorage privacyStateStorage = mock(PrivateStateStorage.class);
  private final WorldStateArchive privateWorldStateArchive = mock(WorldStateArchive.class);
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
  private final WorldUpdater worldUpdater = mock(WorldUpdater.class);
  private final MutableAccount mutableAccount = mock(MutableAccount.class);
  private final Hash lastRootHash = mock(Hash.class);
  private final BytesValue privacyGroupId = BytesValue.wrap("0x123".getBytes(UTF_8));

  private final Address senderAddress =
      Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57");
  private final long NONCE = 5;

  @Test
  public void verifyTransactionCount() {
    when(mutableWorldState.updater()).thenReturn(worldUpdater);
    when(mutableAccount.getNonce()).thenReturn(NONCE);
    // Create account in storage with given nonce
    when(worldUpdater.createAccount(senderAddress, NONCE, Wei.ZERO)).thenReturn(mutableAccount);
    when(privateWorldStateArchive.getMutable()).thenReturn(mutableWorldState);

    when(privacyParameters.getPrivateStateStorage()).thenReturn(privacyStateStorage);
    when(privacyParameters.getPrivateWorldStateArchive()).thenReturn(privateWorldStateArchive);

    when(privacyStateStorage.getPrivateAccountState(privacyGroupId))
        .thenReturn(Optional.of(lastRootHash));
    when(privateWorldStateArchive.getMutable(lastRootHash))
        .thenReturn(Optional.of(mutableWorldState));
    when(mutableWorldState.get(senderAddress)).thenReturn(mutableAccount);

    final EeaGetTransactionCount eeaGetTransactionCount =
        new EeaGetTransactionCount(parameters, privacyParameters);

    final Object[] params = new Object[] {senderAddress, privacyGroupId.toString()};
    final JsonRpcRequest request = new JsonRpcRequest("1", "eea_getTransactionCount", params);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) eeaGetTransactionCount.response(request);

    assertEquals("0x5", response.getResult());
  }
}

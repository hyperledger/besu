/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.proof.GetProofResult;
import tech.pegasys.pantheon.ethereum.proof.WorldStateProof;
import tech.pegasys.pantheon.ethereum.worldstate.StateTrieAccountValue;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetProofTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Mock private BlockchainQueries blockchainQueries;

  private final JsonRpcParameter parameters = new JsonRpcParameter();

  private EthGetProof method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_getProof";

  private final Address address =
      Address.fromHexString("0x1234567890123456789012345678901234567890");
  private final UInt256 storageKey =
      UInt256.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
  private final long blockNumber = 1;

  @Before
  public void setUp() {
    method = new EthGetProof(blockchainQueries, parameters);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void errorWhenNoAddressAccountSupplied() {
    final JsonRpcRequest request = requestWithParams(null, null, "latest");

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Missing required json rpc parameter at index 0");

    method.response(request);
  }

  @Test
  public void errorWhenNoStorageKeysSupplied() {
    final JsonRpcRequest request = requestWithParams(address.toString(), null, "latest");

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Missing required json rpc parameter at index 1");

    method.response(request);
  }

  @Test
  public void errorWhenNoBlockNumberSupplied() {
    final JsonRpcRequest request = requestWithParams(address.toString(), new String[] {});

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Missing required json rpc parameter at index 2");

    method.response(request);
  }

  @Test
  public void errorWhenAccountNotFound() {

    generateWorldState();

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.NO_ACCOUNT_FOUND);

    final JsonRpcRequest request =
        requestWithParams(
            Address.fromHexString("0x0000000000000000000000000000000000000000"),
            new String[] {storageKey.toString()},
            String.valueOf(blockNumber));

    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void errorWhenWorldStateUnavailable() {

    when(blockchainQueries.getWorldState(blockNumber)).thenReturn(Optional.empty());

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.WORLD_STATE_UNAVAILABLE);

    final JsonRpcRequest request =
        requestWithParams(
            Address.fromHexString("0x0000000000000000000000000000000000000000"),
            new String[] {storageKey.toString()},
            String.valueOf(blockNumber));

    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void getProof() {

    final GetProofResult expectedResponse = generateWorldState();

    final JsonRpcRequest request =
        requestWithParams(
            address.toString(), new String[] {storageKey.toString()}, String.valueOf(blockNumber));

    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);

    assertThat(response.getResult()).isEqualToComparingFieldByFieldRecursively(expectedResponse);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }

  @SuppressWarnings("unchecked")
  private GetProofResult generateWorldState() {

    final Wei balance = Wei.of(1);
    final Hash codeHash =
        Hash.fromHexString("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470");
    final long nonce = 1;
    final Hash rootHash =
        Hash.fromHexString("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b431");
    final Hash storageRoot =
        Hash.fromHexString("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");

    final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);

    when(blockchainQueries.getWorldStateArchive()).thenReturn(worldStateArchive);

    final StateTrieAccountValue stateTrieAccountValue = mock(StateTrieAccountValue.class);
    when(stateTrieAccountValue.getBalance()).thenReturn(balance);
    when(stateTrieAccountValue.getCodeHash()).thenReturn(codeHash);
    when(stateTrieAccountValue.getNonce()).thenReturn(nonce);
    when(stateTrieAccountValue.getStorageRoot()).thenReturn(storageRoot);

    final WorldStateProof worldStateProof = mock(WorldStateProof.class);
    when(worldStateProof.getAccountProof())
        .thenReturn(
            Collections.singletonList(
                BytesValue.fromHexString(
                    "0x1111111111111111111111111111111111111111111111111111111111111111")));
    when(worldStateProof.getStateTrieAccountValue()).thenReturn(stateTrieAccountValue);
    when(worldStateProof.getStorageKeys()).thenReturn(Collections.singletonList(storageKey));
    when(worldStateProof.getStorageProof(storageKey))
        .thenReturn(
            Collections.singletonList(
                BytesValue.fromHexString(
                    "0x2222222222222222222222222222222222222222222222222222222222222222")));
    when(worldStateProof.getStorageValue(storageKey)).thenReturn(UInt256.ZERO);

    when(worldStateArchive.getAccountProof(eq(rootHash), eq(address), anyList()))
        .thenReturn(Optional.of(worldStateProof));

    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(mutableWorldState.rootHash()).thenReturn(rootHash);
    when(blockchainQueries.getWorldState(blockNumber)).thenReturn(Optional.of(mutableWorldState));

    return GetProofResult.buildGetProofResult(address, worldStateProof);
  }
}

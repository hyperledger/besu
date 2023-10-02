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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.account.Account.MAX_NONCE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.proof.GetProofResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EthGetProofTest {
  @Mock private Blockchain blockchain;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private WorldStateArchive archive;

  @Mock private ChainHead chainHead;
  @Mock private BlockHeader blockHeader;
  private BlockchainQueries blockchainQueries;

  private EthGetProof method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_getProof";

  private final Address address =
      Address.fromHexString("0x1234567890123456789012345678901234567890");
  private final UInt256 storageKey =
      UInt256.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
  private final long blockNumber = 1;

  @BeforeEach
  public void setUp() {
    blockchainQueries = spy(new BlockchainQueries(blockchain, archive));
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchainQueries.headBlockNumber()).thenReturn(14L);
    when(blockchain.getChainHead()).thenReturn(chainHead);
    when(chainHead.getBlockHeader()).thenReturn(blockHeader);
    when(blockHeader.getBlockHash()).thenReturn(Hash.ZERO);
    when(blockchain.getBlockHeader(Hash.ZERO)).thenReturn(Optional.of(mock(BlockHeader.class)));
    method = spy(new EthGetProof(blockchainQueries));
  }

  @Test
  void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  void errorWhenNoAddressAccountSupplied() {
    final JsonRpcRequestContext request = requestWithParams(null, null, "latest");

    Assertions.assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 0");
  }

  @Test
  void errorWhenNoStorageKeysSupplied() {
    final JsonRpcRequestContext request = requestWithParams(address.toString(), null, "latest");

    Assertions.assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 1");
  }

  @Test
  void errorWhenNoBlockNumberSupplied() {
    final JsonRpcRequestContext request = requestWithParams(address.toString(), new String[] {});

    Assertions.assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 2");
  }

  @Test
  void errorWhenAccountNotFound() {
    generateWorldState();
    when(archive.getAccountProof(any(Hash.class), any(Address.class), any()))
        .thenReturn(Optional.empty());
    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.NO_ACCOUNT_FOUND);

    final JsonRpcRequestContext request =
        requestWithParams(
            Address.fromHexString("0x0000000000000000000000000000000000000000"),
            new String[] {storageKey.toString()},
            String.valueOf(blockNumber));

    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  void errorWhenWorldStateUnavailable() {

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.WORLD_STATE_UNAVAILABLE);
    when(archive.getMutable(any(BlockHeader.class), anyBoolean())).thenReturn(Optional.empty());

    final JsonRpcRequestContext request =
        requestWithParams(
            Address.fromHexString("0x0000000000000000000000000000000000000000"),
            new String[] {storageKey.toString()},
            String.valueOf(blockNumber));

    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  void getProof() {

    final GetProofResult expectedResponse = generateWorldState();

    final JsonRpcRequestContext request =
        requestWithParams(
            address.toString(), new String[] {storageKey.toString()}, String.valueOf(blockNumber));

    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);

    final GetProofResult result = (GetProofResult) response.getResult();

    assertThat(result).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(result.getNonce()).isEqualTo("0xfffffffffffffffe");
    assertThat(result.getStorageProof().size()).isGreaterThan(0);
    assertThat(result.getStorageProof().get(0).getKey())
        .isEqualTo(storageKey.trimLeadingZeros().toHexString());
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params));
  }

  private GetProofResult generateWorldState() {

    final Wei balance = Wei.of(1);
    final Hash codeHash =
        Hash.fromHexString("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470");
    final long nonce = MAX_NONCE - 1;
    final Hash rootHash =
        Hash.fromHexString("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b431");
    final Hash storageRoot =
        Hash.fromHexString("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");

    when(blockchainQueries.getWorldStateArchive()).thenReturn(archive);

    final StateTrieAccountValue stateTrieAccountValue = mock(StateTrieAccountValue.class);
    when(stateTrieAccountValue.getBalance()).thenReturn(balance);
    when(stateTrieAccountValue.getCodeHash()).thenReturn(codeHash);
    when(stateTrieAccountValue.getNonce()).thenReturn(nonce);
    when(stateTrieAccountValue.getStorageRoot()).thenReturn(storageRoot);

    final WorldStateProof worldStateProof = mock(WorldStateProof.class);
    when(worldStateProof.getAccountProof())
        .thenReturn(
            Collections.singletonList(
                Bytes.fromHexString(
                    "0x1111111111111111111111111111111111111111111111111111111111111111")));
    when(worldStateProof.getStateTrieAccountValue()).thenReturn(stateTrieAccountValue);
    when(worldStateProof.getStorageKeys()).thenReturn(Collections.singletonList(storageKey));
    when(worldStateProof.getStorageProof(storageKey))
        .thenReturn(
            Collections.singletonList(
                Bytes.fromHexString(
                    "0x2222222222222222222222222222222222222222222222222222222222222222")));
    when(worldStateProof.getStorageValue(storageKey)).thenReturn(UInt256.ZERO);

    when(archive.getAccountProof(eq(rootHash), eq(address), anyList()))
        .thenReturn(Optional.of(worldStateProof));

    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(mutableWorldState.rootHash()).thenReturn(rootHash);
    doAnswer(
            invocation ->
                invocation
                    .<Function<MutableWorldState, Optional<? extends JsonRpcResponse>>>getArgument(
                        1)
                    .apply(mutableWorldState))
        .when(blockchainQueries)
        .getAndMapWorldState(any(), any());

    return GetProofResult.buildGetProofResult(address, worldStateProof);
  }
}

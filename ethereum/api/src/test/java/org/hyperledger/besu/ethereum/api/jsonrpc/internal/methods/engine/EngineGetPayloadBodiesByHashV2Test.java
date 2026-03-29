/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineTestSupport.fromErrorResp;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_RANGE_REQUEST_TOO_LARGE;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadBodiesResultV2;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EngineGetPayloadBodiesByHashV2Test extends AbstractEngineGetPayloadBodiesTest {

  public EngineGetPayloadBodiesByHashV2Test() {
    super(EngineGetPayloadBodiesByHashV2::new);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_getPayloadBodiesByHashV2");
  }

  @Test
  public void shouldReturnBlockAccessListWhenAvailable() {
    final SignatureAlgorithm sig = SignatureAlgorithmFactory.getInstance();
    final Hash blockHash = Hash.wrap(Bytes32.random());
    final BlockBody blockBody =
        new BlockBody(
            List.of(new TransactionTestFixture().createTransaction(sig.generateKeyPair())),
            Collections.emptyList());
    final BlockAccessList blockAccessList = createSampleBlockAccessList();
    final String encodedBlockAccessList = encodeBlockAccessList(blockAccessList);

    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));
    when(blockchain.getBlockAccessList(blockHash)).thenReturn(Optional.of(blockAccessList));

    final var resp = resp(new Hash[] {blockHash});
    final EngineGetPayloadBodiesResultV2 result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().size()).isEqualTo(1);
    assertThat(result.getPayloadBodies().get(0).getBlockAccessList())
        .isEqualTo(encodedBlockAccessList);
  }

  @Test
  public void shouldReturnNullBlockAccessListForPreAmsterdamBlock() {
    final SignatureAlgorithm sig = SignatureAlgorithmFactory.getInstance();
    final Hash blockHash = Hash.wrap(Bytes32.random());
    final BlockBody blockBody =
        new BlockBody(
            List.of(new TransactionTestFixture().createTransaction(sig.generateKeyPair())),
            Collections.emptyList());

    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));

    final var resp = resp(new Hash[] {blockHash});
    final EngineGetPayloadBodiesResultV2 result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().size()).isEqualTo(1);
    assertThat(result.getPayloadBodies().get(0).getBlockAccessList()).isNull();
  }

  @Test
  public void shouldReturnNullBlockAccessListWhenPruned() {
    final SignatureAlgorithm sig = SignatureAlgorithmFactory.getInstance();
    final Hash blockHash = Hash.wrap(Bytes32.random());
    final BlockBody blockBody =
        new BlockBody(
            List.of(new TransactionTestFixture().createTransaction(sig.generateKeyPair())),
            Collections.emptyList());

    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));
    when(blockchain.getBlockAccessList(blockHash)).thenReturn(Optional.empty());

    final var resp = resp(new Hash[] {blockHash});
    final EngineGetPayloadBodiesResultV2 result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().size()).isEqualTo(1);
    assertThat(result.getPayloadBodies().get(0).getBlockAccessList()).isNull();
  }

  @Test
  public void shouldReturnEmptyPayloadBodiesWithEmptyHash() {
    final var resp = resp(new Hash[] {});
    final EngineGetPayloadBodiesResultV2 result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().isEmpty()).isTrue();
  }

  @Test
  public void shouldReturnNullForUnknownHashes() {
    final Hash blockHash1 = Hash.wrap(Bytes32.random());
    final Hash blockHash2 = Hash.wrap(Bytes32.random());
    final Hash blockHash3 = Hash.wrap(Bytes32.random());
    final var resp = resp(new Hash[] {blockHash1, blockHash2, blockHash3});
    final var result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().size()).isEqualTo(3);
    assertThat(result.getPayloadBodies().get(0)).isNull();
    assertThat(result.getPayloadBodies().get(1)).isNull();
    assertThat(result.getPayloadBodies().get(2)).isNull();
  }

  @Test
  public void shouldReturnErrorWhenRequestExceedsPermittedNumberOfBlocks() {
    final Hash blockHash1 = Hash.wrap(Bytes32.random());
    final Hash blockHash2 = Hash.wrap(Bytes32.random());
    final Hash[] hashes = new Hash[] {blockHash1, blockHash2};

    doReturn(1).when(method).getMaxRequestBlocks();

    final JsonRpcResponse resp = resp(hashes);
    final var result = fromErrorResp(resp);
    assertThat(result.getCode()).isEqualTo(INVALID_RANGE_REQUEST_TOO_LARGE.getCode());
  }

  private JsonRpcResponse resp(final Hash[] hashes) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                RpcMethod.ENGINE_GET_PAYLOAD_BODIES_BY_HASH_V2.getMethodName(),
                new Object[] {hashes})));
  }
}

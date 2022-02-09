/*
 * Copyright Hyperledger Besu Contributors.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineForkchoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineUpdateForkChoiceResult;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Optional;
import java.util.stream.Stream;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineForkchoiceUpdatedTest {

  private EngineForkchoiceUpdated method;
  private static final Vertx vertx = Vertx.vertx();
  private static final Hash mockHash = Hash.hash(Bytes32.fromHexStringLenient("0x1337deadbeef"));

  @Mock private ProtocolContext protocolContext;

  @Mock private MergeContext mergeContext;

  @Mock private MergeMiningCoordinator mergeCoordinator;

  @Mock private MutableBlockchain blockchain;

  @Before
  public void before() {
    when(protocolContext.getConsensusContext(Mockito.any())).thenReturn(mergeContext);
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    this.method = new EngineForkchoiceUpdated(vertx, protocolContext, mergeCoordinator);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    // will break as specs change, intentional:
    assertThat(method.getName()).isEqualTo("engine_forkchoiceUpdatedV1");
  }

  @Test
  public void shouldReturnInvalidTerminalblock() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();

    when(blockchain.getBlockHeader(any())).thenReturn(Optional.of(mockHeader));
    when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(any(BlockHeader.class)))
        .thenReturn(false);
    var resp =
        resp(new EngineForkchoiceUpdatedParameter(mockHash, mockHash, mockHash), Optional.empty());
    var res = fromSuccessResp(resp);
    assertThat(res.getStatus()).isEqualTo(JsonRpcError.INVALID_TERMINAL_BLOCK.name());
    assertThat(res.getPayloadId()).isNull();
  }

  private JsonRpcResponse resp(
      final EngineForkchoiceUpdatedParameter forkchoiceParam,
      final Optional<EnginePayloadAttributesParameter> payloadParam) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                RpcMethod.ENGINE_FORKCHOICE_UPDATED.getMethodName(),
                Stream.concat(Stream.of(forkchoiceParam), payloadParam.stream()).toArray())));
  }

  private EngineUpdateForkChoiceResult fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    return Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .map(JsonRpcSuccessResponse::getResult)
        .map(EngineUpdateForkChoiceResult.class::cast)
        .get();
  }
}

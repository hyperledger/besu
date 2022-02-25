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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineExchangeTransitionConfigurationParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineExchangeTransitionConfigurationResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineExchangeTransitionConfigurationTest {
  private EngineExchangeTransitionConfiguration method;
  private static final Vertx vertx = Vertx.vertx();

  @Mock private ProtocolContext protocolContext;
  @Mock private MergeContext mergeContext;

  @Before
  public void setUp() {
    when(protocolContext.getConsensusContext(Mockito.any())).thenReturn(mergeContext);

    this.method = new EngineExchangeTransitionConfiguration(vertx, protocolContext);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    // will break as specs change, intentional:
    assertThat(method.getName()).isEqualTo("engine_exchangeTransitionConfigurationV1");
  }

  @Test
  public void shouldReturnInvalidParamsOnTerminalBlockNumberNotZero() {
    var response =
        resp(new EngineExchangeTransitionConfigurationParameter("0", Hash.ZERO.toHexString(), 1));

    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    JsonRpcErrorResponse res = ((JsonRpcErrorResponse) response);
    assertThat(res.getError()).isEqualTo(JsonRpcError.INVALID_PARAMS);
  }

  @Test
  public void shouldReturnInternalErrorOnTerminalPoWBlockHeaderEmpty() {
    when(mergeContext.getTerminalPoWBlock()).thenReturn(Optional.empty());

    var response =
        resp(new EngineExchangeTransitionConfigurationParameter("0", Hash.ZERO.toHexString(), 0));

    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    JsonRpcErrorResponse res = ((JsonRpcErrorResponse) response);
    assertThat(res.getError()).isEqualTo(JsonRpcError.INTERNAL_ERROR);
  }

  @Test
  public void shouldReturnConfigurationOnConfigurationMisMatch() {
    final BlockHeader fakeBlockHeader = createBlockHeader(Hash.fromHexStringLenient("0x01"), 42);
    when(mergeContext.getTerminalPoWBlock()).thenReturn(Optional.of(fakeBlockHeader));
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(Difficulty.of(24));

    var response =
        resp(
            new EngineExchangeTransitionConfigurationParameter(
                "1", Hash.fromHexStringLenient("0xff").toHexString(), 0));

    var result = fromSuccessResp(response);
    assertThat(result.getTerminalTotalDifficulty()).isEqualTo(Difficulty.of(24));
    assertThat(result.getTerminalBlockHash()).isEqualTo(Hash.fromHexStringLenient("0x01"));
    assertThat(result.getTerminalBlockNumber()).isEqualTo(42);
  }

  @Test
  public void shouldReturnConfigurationOnConfigurationMatch() {
    final BlockHeader fakeBlockHeader = createBlockHeader(Hash.fromHexStringLenient("0x01"), 42);
    when(mergeContext.getTerminalPoWBlock()).thenReturn(Optional.of(fakeBlockHeader));
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(Difficulty.of(24));

    var response =
        resp(
            new EngineExchangeTransitionConfigurationParameter(
                "24", Hash.fromHexStringLenient("0x01").toHexString(), 0));

    var result = fromSuccessResp(response);
    assertThat(result.getTerminalTotalDifficulty()).isEqualTo(Difficulty.of(24));
    assertThat(result.getTerminalBlockHash()).isEqualTo(Hash.fromHexStringLenient("0x01"));
    assertThat(result.getTerminalBlockNumber()).isEqualTo(42);
  }

  private JsonRpcResponse resp(final EngineExchangeTransitionConfigurationParameter param) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                RpcMethod.ENGINE_EXCHANGE_TRANSITION_CONFIGURATION.getMethodName(),
                new Object[] {param})));
  }

  private EngineExchangeTransitionConfigurationResult fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    return Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .map(JsonRpcSuccessResponse::getResult)
        .map(EngineExchangeTransitionConfigurationResult.class::cast)
        .get();
  }

  private BlockHeader createBlockHeader(final Hash blockHash, final long blockNumber) {
    return new BlockHeader(
        Hash.EMPTY,
        Hash.EMPTY,
        Address.ZERO,
        Hash.EMPTY,
        Hash.EMPTY,
        Hash.EMPTY,
        LogsBloomFilter.empty(),
        Difficulty.ZERO,
        blockNumber,
        0,
        0,
        0,
        Bytes.EMPTY,
        Wei.ZERO,
        Bytes32.ZERO,
        0,
        new BlockHeaderFunctions() {
          @Override
          public Hash hash(final BlockHeader header) {
            return blockHash;
          }

          @Override
          public ParsedExtraData parseExtraData(final BlockHeader header) {
            return null;
          }
        });
  }
}

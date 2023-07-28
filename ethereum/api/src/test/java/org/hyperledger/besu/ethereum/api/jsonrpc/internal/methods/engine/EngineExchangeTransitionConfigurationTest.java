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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineExchangeTransitionConfigurationResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EngineExchangeTransitionConfigurationTest {
  private EngineExchangeTransitionConfiguration method;
  private static final Vertx vertx = Vertx.vertx();

  @Mock private ProtocolContext protocolContext;

  @Mock private MergeContext mergeContext;

  @Mock private EngineCallListener engineCallListener;

  @BeforeEach
  public void setUp() {
    when(protocolContext.safeConsensusContext(Mockito.any())).thenReturn(Optional.of(mergeContext));

    this.method =
        new EngineExchangeTransitionConfiguration(vertx, protocolContext, engineCallListener);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    // will break as specs change, intentional:
    assertThat(method.getName()).isEqualTo("engine_exchangeTransitionConfigurationV1");
  }

  @Test
  public void shouldNotReturnInvalidParamsOnTerminalBlockNumberNotZero() {
    var mockBlockHeader =
        new BlockHeaderTestFixture().difficulty(Difficulty.of(1339L)).number(420).buildHeader();
    when(mergeContext.getTerminalPoWBlock()).thenReturn(Optional.of(mockBlockHeader));
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(Difficulty.of(1337L));

    var response =
        resp(
            new EngineExchangeTransitionConfigurationParameter(
                "0", Hash.ZERO.toHexString(), new UnsignedLongParameter(1L)));

    var result = fromSuccessResp(response);
    assertThat(result.getTerminalTotalDifficulty()).isEqualTo(Difficulty.of(1337L));
    assertThat(result.getTerminalBlockHash()).isEqualTo(mockBlockHeader.getHash());
    assertThat(result.getTerminalBlockNumber()).isEqualTo(mockBlockHeader.getNumber());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnZerosOnTerminalPoWBlockHeaderEmpty() {
    when(mergeContext.getTerminalPoWBlock()).thenReturn(Optional.empty());
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(Difficulty.of(1337L));

    var response =
        resp(
            new EngineExchangeTransitionConfigurationParameter(
                "0", Hash.ZERO.toHexString(), new UnsignedLongParameter(0L)));

    var result = fromSuccessResp(response);
    assertThat(result.getTerminalTotalDifficulty()).isEqualTo(Difficulty.of(1337L));
    assertThat(result.getTerminalBlockHash()).isEqualTo(Hash.ZERO);
    assertThat(result.getTerminalBlockNumber()).isEqualTo(0L);
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnDefaultOnNoTerminalTotalDifficultyConfigured() {
    var response =
        resp(
            new EngineExchangeTransitionConfigurationParameter(
                "0", Hash.ZERO.toHexString(), new UnsignedLongParameter(0L)));

    var result = fromSuccessResp(response);
    assertThat(result.getTerminalTotalDifficulty())
        .isEqualTo(
            UInt256.valueOf(
                new BigInteger(
                    "115792089237316195423570985008687907853269984665640564039457584007913129638912",
                    10)));
    assertThat(result.getTerminalBlockHash()).isEqualTo(Hash.ZERO);
    assertThat(result.getTerminalBlockNumber()).isEqualTo(0L);
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnConfigurationOnConfigurationMisMatch() {
    final BlockHeader fakeBlockHeader = createBlockHeader(Hash.fromHexStringLenient("0x01"), 42);
    when(mergeContext.getTerminalPoWBlock()).thenReturn(Optional.of(fakeBlockHeader));
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(Difficulty.of(24));

    var response =
        resp(
            new EngineExchangeTransitionConfigurationParameter(
                "1",
                Hash.fromHexStringLenient("0xff").toHexString(),
                new UnsignedLongParameter(0L)));

    var result = fromSuccessResp(response);
    assertThat(result.getTerminalTotalDifficulty()).isEqualTo(Difficulty.of(24));
    assertThat(result.getTerminalBlockHash()).isEqualTo(Hash.fromHexStringLenient("0x01"));
    assertThat(result.getTerminalBlockNumber()).isEqualTo(42);
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnConfigurationOnConfigurationMatch() {
    final BlockHeader fakeBlockHeader = createBlockHeader(Hash.fromHexStringLenient("0x01"), 42);
    when(mergeContext.getTerminalPoWBlock()).thenReturn(Optional.of(fakeBlockHeader));
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(Difficulty.of(24));

    var response =
        resp(
            new EngineExchangeTransitionConfigurationParameter(
                "24",
                Hash.fromHexStringLenient("0x01").toHexString(),
                new UnsignedLongParameter(0)));

    var result = fromSuccessResp(response);
    assertThat(result.getTerminalTotalDifficulty()).isEqualTo(Difficulty.of(24));
    assertThat(result.getTerminalBlockHash()).isEqualTo(Hash.fromHexStringLenient("0x01"));
    assertThat(result.getTerminalBlockNumber()).isEqualTo(42);
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldAlwaysReturnResultsInHex() throws JsonProcessingException {
    var mapper = new ObjectMapper();
    var mockResult =
        new EngineExchangeTransitionConfigurationResult(Difficulty.ZERO, Hash.ZERO, 0L);

    assertThat(mockResult.getTerminalBlockNumberAsString()).isEqualTo("0x0");
    assertThat(mockResult.getTerminalTotalDifficultyAsString()).isEqualTo("0x0");
    assertThat(mockResult.getTerminalBlockHashAsString()).isEqualTo(Hash.ZERO.toHexString());

    String json = mapper.writeValueAsString(mockResult);
    var res = mapper.readValue(json, Map.class);
    assertThat(res.get("terminalBlockNumber")).isEqualTo("0x0");
    assertThat(res.get("terminalBlockHash"))
        .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
    assertThat(res.get("terminalTotalDifficulty")).isEqualTo("0x0");
  }

  @Test
  public void shouldStripLeadingZeros() throws JsonProcessingException {
    var mapper = new ObjectMapper();
    var mockResult =
        new EngineExchangeTransitionConfigurationResult(Difficulty.ZERO, Hash.ZERO, 100);

    assertThat(mockResult.getTerminalBlockNumberAsString()).isEqualTo("0x64");
    assertThat(mockResult.getTerminalTotalDifficultyAsString()).isEqualTo("0x0");
    assertThat(mockResult.getTerminalBlockHashAsString()).isEqualTo(Hash.ZERO.toHexString());

    String json = mapper.writeValueAsString(mockResult);
    var res = mapper.readValue(json, Map.class);
    assertThat(res.get("terminalBlockNumber")).isEqualTo("0x64");
    assertThat(res.get("terminalBlockHash"))
        .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
    assertThat(res.get("terminalTotalDifficulty")).isEqualTo("0x0");
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
        null,
        null,
        null,
        null,
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

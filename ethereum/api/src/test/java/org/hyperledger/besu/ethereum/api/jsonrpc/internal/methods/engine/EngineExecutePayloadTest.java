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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ExecutionStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ExecutionStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ExecutionStatus.VALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ExecutionPayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineExecutionResult;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineExecutePayloadTest {
  private EngineExecutePayload method;
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
    this.method = new EngineExecutePayload(vertx, protocolContext, mergeCoordinator);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    // will break as specs change, intentional:
    assertThat(method.getName()).isEqualTo("engine_executePayloadV1");
  }

  @Test
  public void shouldReturnValid() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    when(blockchain.getBlockByHash(any())).thenReturn(Optional.empty());
    when(mergeCoordinator.getLatestValidAncestor(any())).thenReturn(Optional.of(mockHash));
    when(mergeCoordinator.executeBlock(any())).thenReturn(Boolean.TRUE);

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EngineExecutionResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEqualTo(mockHeader.getHash().toString());
    assertThat(res.getStatus()).isEqualTo(VALID.name());
    assertThat(res.getValidationError()).isNull();
  }

  @Test
  public void shouldReturnSuccessOnAlreadyPresent() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().buildHeader();
    Block mockBlock =
        new Block(mockHeader, new BlockBody(Collections.emptyList(), Collections.emptyList()));

    when(blockchain.getBlockByHash(any())).thenReturn(Optional.of(mockBlock));

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EngineExecutionResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEqualTo(mockHeader.getHash().toString());
    assertThat(res.getStatus()).isEqualTo(VALID.name());
    assertThat(res.getValidationError()).isNull();
  }

  @Test
  public void shouldReturnInvalidOnBadHashParameter() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().buildHeader();
    // exploit the hash difference between basefee of Optional.empty vs Wei.ZERO (deserialized
    // value):
    BlockHeader realHeader =
        BlockHeaderBuilder.fromHeader(mockHeader)
            .baseFee(Wei.ZERO)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader();

    when(blockchain.getBlockByHash(any())).thenReturn(Optional.empty());
    when(mergeCoordinator.getLatestValidAncestor(any())).thenReturn(Optional.of(mockHash));

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EngineExecutionResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEqualTo(mockHash.toString());
    assertThat(res.getStatus()).isEqualTo(INVALID.name());

    assertThat(res.getValidationError())
        .isEqualTo(
            String.format(
                "Computed block hash %s does not match block hash parameter %s",
                realHeader.getBlockHash(), mockHeader.getBlockHash()));
  }

  @Test
  public void shouldRespondWithSyncingDuringForwardSync() {
    when(mergeContext.isSyncing()).thenReturn(Boolean.TRUE);
    var resp = resp(mock(ExecutionPayloadParameter.class));
    EngineExecutionResult res = fromSuccessResp(resp);
    assertThat(res.getValidationError()).isNull();
    assertThat(res.getStatus()).isEqualTo(SYNCING.name());
    assertThat(res.getLatestValidHash()).isNull();
  }

  @Test
  public void shouldRespondWithSyncingDuringBackwardsSync() {
    when(mergeCoordinator.getLatestValidAncestor(any())).thenReturn(Optional.empty());
    BlockHeader mockHeader = new BlockHeaderTestFixture().buildHeader();
    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EngineExecutionResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isNull();
    assertThat(res.getStatus()).isEqualTo(SYNCING.name());
    assertThat(res.getValidationError()).isNull();
  }

  @Test
  public void shouldRespondWithInvalidTerminalPowBlock() {
    // TODO: https://github.com/hyperledger/besu/issues/3141
  }

  private JsonRpcResponse resp(final ExecutionPayloadParameter payload) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", RpcMethod.ENGINE_EXECUTE_PAYLOAD.getMethodName(), new Object[] {payload})));
  }

  private ExecutionPayloadParameter mockPayload(final BlockHeader header, final List<String> txs) {
    return new ExecutionPayloadParameter(
        header.getHash(),
        header.getParentHash(),
        header.getCoinbase(),
        header.getStateRoot(),
        asUnsingedLongParameter(header.getNumber()),
        header.getBaseFee().map(w -> w.toHexString()).orElse("0x0"),
        asUnsingedLongParameter(header.getGasLimit()),
        asUnsingedLongParameter(header.getGasUsed()),
        asUnsingedLongParameter(header.getTimestamp()),
        header.getExtraData().toHexString(),
        header.getReceiptsRoot(),
        header.getLogsBloom(),
        header.getRandom().map(Bytes32::toHexString).orElse("0x0"),
        txs);
  }

  private UnsignedLongParameter asUnsingedLongParameter(final long val) {
    return new UnsignedLongParameter(Long.toHexString(val));
  }

  private EngineExecutionResult fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    return Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .map(JsonRpcSuccessResponse::getResult)
        .map(EngineExecutionResult.class::cast)
        .get();
  }
}

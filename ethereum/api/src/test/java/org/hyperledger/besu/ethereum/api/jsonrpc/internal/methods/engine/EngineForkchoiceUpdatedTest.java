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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_TERMINAL_BLOCK;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator.ForkchoiceResult;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineForkchoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineUpdateForkchoiceResult;
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
  public void shouldReturnSyncingIfForwardSync() {
    when(mergeContext.isSyncing()).thenReturn(true);
    assertSuccessWithPayloadForForkchoiceResult(
        Optional.empty(), mock(ForkchoiceResult.class), SYNCING);
  }

  @Test
  public void shouldReturnSyncingIfMissingNewHead() {
    assertSuccessWithPayloadForForkchoiceResult(
        Optional.empty(), mock(ForkchoiceResult.class), SYNCING);
  }

  @Test
  public void shouldReturnInvalidTerminalBlock() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();

    when(blockchain.getBlockHeader(any())).thenReturn(Optional.of(mockHeader));
    when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(mockHeader)).thenReturn(false);
    assertSuccessWithPayloadForForkchoiceResult(
        Optional.empty(), mock(ForkchoiceResult.class), INVALID_TERMINAL_BLOCK);
  }

  @Test
  public void shouldReturnSyncingOnHeadNotFound() {
    assertSuccessWithPayloadForForkchoiceResult(
        Optional.empty(), mock(ForkchoiceResult.class), SYNCING);
  }

  @Test
  public void shouldReturnValidWithoutFinalizedOrPayload() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    when(blockchain.getBlockHeader(any())).thenReturn(Optional.of(mockHeader));
    when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(mockHeader)).thenReturn(true);

    assertSuccessWithPayloadForForkchoiceResult(
        Optional.empty(),
        ForkchoiceResult.withResult(Optional.empty(), Optional.of(mockHeader)),
        VALID);
  }

  @Test
  public void shouldReturnInvalidOnOldTimestamp() {
    BlockHeader parent = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    BlockHeader mockHeader =
        new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .parentHash(parent.getHash())
            .timestamp(parent.getTimestamp())
            .buildHeader();
    when(blockchain.getBlockHeader(mockHeader.getHash())).thenReturn(Optional.of(mockHeader));
    when(blockchain.getBlockHeader(parent.getHash())).thenReturn(Optional.of(parent));
    //    when(blockchain.getChainHeadHeader()).thenReturn(parent);
    when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(mockHeader)).thenReturn(true);
    when(mergeContext.isSyncing()).thenReturn(false);
    when(mergeCoordinator.updateForkChoice(mockHeader.getHash(), parent.getHash()))
        .thenReturn(
            ForkchoiceResult.withFailure(
                "new head timestamp not greater than parent", Optional.of(parent.getHash())));

    EngineForkchoiceUpdatedParameter param =
        new EngineForkchoiceUpdatedParameter(
            mockHeader.getBlockHash(), parent.getBlockHash(), parent.getBlockHash());

    EngineUpdateForkchoiceResult resp = fromSuccessResp(resp(param, Optional.empty()));

    assertThat(resp.getPayloadStatus().getStatus()).isEqualTo(INVALID);
    assertThat(resp.getPayloadStatus().getLatestValidHash()).isPresent();
    assertThat(resp.getPayloadStatus().getLatestValidHash().get()).isEqualTo(parent.getBlockHash());
    assertThat(resp.getPayloadStatus().getError())
        .isEqualTo("new head timestamp not greater than parent");
  }

  @Test
  public void shouldReturnValidWithNewHeadAndFinalizedNoPayload() {
    var builder = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE);
    BlockHeader mockParent = builder.number(9L).buildHeader();
    BlockHeader mockHeader = builder.number(10L).parentHash(mockParent.getHash()).buildHeader();
    when(blockchain.getBlockHeader(any())).thenReturn(Optional.of(mockHeader));
    when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(mockHeader)).thenReturn(true);

    assertSuccessWithPayloadForForkchoiceResult(
        Optional.empty(),
        ForkchoiceResult.withResult(Optional.of(mockParent), Optional.of(mockHeader)),
        VALID);
  }

  @Test
  public void shouldReturnValidWithoutFinalizedWithPayload() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    when(blockchain.getBlockHeader(any())).thenReturn(Optional.of(mockHeader));
    when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(mockHeader)).thenReturn(true);

    var payloadParams =
        new EnginePayloadAttributesParameter(
            String.valueOf(System.currentTimeMillis()),
            Bytes32.fromHexStringLenient("0xDEADBEEF").toHexString(),
            Address.ECREC.toString());
    var mockPayloadId =
        PayloadIdentifier.forPayloadParams(mockHeader.getHash(), payloadParams.getTimestamp());

    when(mergeCoordinator.preparePayload(
            mockHeader, payloadParams.getTimestamp(), payloadParams.getPrevRandao(), Address.ECREC))
        .thenReturn(mockPayloadId);

    var res =
        assertSuccessWithPayloadForForkchoiceResult(
            Optional.of(payloadParams),
            ForkchoiceResult.withResult(Optional.empty(), Optional.of(mockHeader)),
            VALID);

    assertThat(res.getPayloadId()).isEqualTo(mockPayloadId.toHexString());
  }

  private EngineUpdateForkchoiceResult assertSuccessWithPayloadForForkchoiceResult(
      final Optional<EnginePayloadAttributesParameter> payloadParam,
      final ForkchoiceResult forkchoiceResult,
      final EngineStatus expectedStatus) {

    // result from mergeCoordinator has no new finalized, new head:
    when(mergeCoordinator.updateForkChoice(any(Hash.class), any(Hash.class)))
        .thenReturn(forkchoiceResult);
    var resp =
        resp(new EngineForkchoiceUpdatedParameter(mockHash, mockHash, mockHash), payloadParam);
    var res = fromSuccessResp(resp);

    assertThat(res.getPayloadStatus().getStatusAsString()).isEqualTo(expectedStatus.name());

    if (expectedStatus.equals(VALID)) {
      // check conditions when response is valid
      assertThat(res.getPayloadStatus().getLatestValidHash())
          .isEqualTo(forkchoiceResult.getNewHead().map(BlockHeader::getBlockHash));
      assertThat(res.getPayloadStatus().getError()).isNullOrEmpty();
      if (payloadParam.isPresent()) {
        assertThat(res.getPayloadId()).isNotNull();
      } else {
        assertThat(res.getPayloadId()).isNull();
      }
    } else {
      // assert null latest valid and payload identifier:
      assertThat(res.getPayloadStatus().getLatestValidHash()).isEmpty();
      assertThat(res.getPayloadId()).isNull();
    }
    return res;
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

  private EngineUpdateForkchoiceResult fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    return Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .map(JsonRpcSuccessResponse::getResult)
        .map(EngineUpdateForkchoiceResult.class::cast)
        .get();
  }
}

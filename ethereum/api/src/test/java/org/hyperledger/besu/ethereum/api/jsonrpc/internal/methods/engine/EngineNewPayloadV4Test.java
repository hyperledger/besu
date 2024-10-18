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
import static org.hyperledger.besu.ethereum.api.graphql.internal.response.GraphQLError.INVALID_PARAMS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.requests.MainnetRequestsValidator;
import org.hyperledger.besu.ethereum.mainnet.requests.ProhibitedRequestValidator;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EngineNewPayloadV4Test extends EngineNewPayloadV3Test {

  public EngineNewPayloadV4Test() {}

  @BeforeEach
  @Override
  public void before() {
    super.before();
    maybeParentBeaconBlockRoot = Optional.of(Bytes32.ZERO);
    // TODO this should be using NewPayloadV4
    this.method =
        new EngineNewPayloadV3(
            vertx,
            protocolSchedule,
            protocolContext,
            mergeCoordinator,
            ethPeers,
            engineCallListener);
    lenient().when(protocolSchedule.hardforkFor(any())).thenReturn(Optional.of(pragueHardfork));
    lenient().when(protocolSpec.getGasCalculator()).thenReturn(new PragueGasCalculator());
  }

  @Override
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadV3");
  }

  @Test
  public void shouldReturnValidIfRequestsIsNull_WhenRequestsProhibited() {
    mockProhibitedRequestsValidator();

    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(
                Optional.of(new BlockProcessingOutputs(null, List.of(), Optional.empty()))),
            Optional.empty(),
            Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(mockHeader))
        .thenReturn(Optional.of(mockHeader.getHash()));

    var resp = resp(mockEnginePayload(mockHeader, Collections.emptyList()));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfRequestsIsNull_WhenRequestsAllowed() {
    mockAllowedRequestsValidator();
    var resp =
        resp(
            mockEnginePayload(
                createBlockHeader(Optional.empty(), Optional.empty()), Collections.emptyList()));

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfRequestsIsNotNull_WhenRequestsAllowed() {
    final List<Request> requests =
        List.of(
            new Request(RequestType.DEPOSIT, Bytes.of(1)),
            new Request(RequestType.WITHDRAWAL, Bytes.of(1)),
            new Request(RequestType.CONSOLIDATION, Bytes.of(1)));

    mockAllowedRequestsValidator();
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(
                Optional.of(new BlockProcessingOutputs(null, List.of(), Optional.of(requests)))),
            Optional.empty(),
            Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(mockHeader))
        .thenReturn(Optional.of(mockHeader.getHash()));
    var resp = resp(mockEnginePayload(mockHeader, Collections.emptyList()), requests);

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfRequestsIsNotNull_WhenRequestsProhibited() {
    final List<Request> requests =
        List.of(
            new Request(RequestType.DEPOSIT, Bytes.of(1)),
            new Request(RequestType.WITHDRAWAL, Bytes.of(1)),
            new Request(RequestType.CONSOLIDATION, Bytes.of(1)));

    mockProhibitedRequestsValidator();

    var resp =
        resp(
            mockEnginePayload(
                createBlockHeader(Optional.empty(), Optional.of(Collections.emptyList())),
                Collections.emptyList()),
            requests);

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  protected BlockHeader createBlockHeader(
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final Optional<List<Request>> maybeRequests) {
    BlockHeader parentBlockHeader =
        new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .timestamp(pragueHardfork.milestone())
            .excessBlobGas(BlobGas.ZERO)
            .blobGasUsed(0L)
            .buildHeader();

    BlockHeader mockHeader =
        new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .parentHash(parentBlockHeader.getParentHash())
            .number(parentBlockHeader.getNumber() + 1)
            .timestamp(parentBlockHeader.getTimestamp() + 1)
            .withdrawalsRoot(maybeWithdrawals.map(BodyValidation::withdrawalsRoot).orElse(null))
            .excessBlobGas(BlobGas.ZERO)
            .blobGasUsed(0L)
            .requestsHash(maybeRequests.map(BodyValidation::requestsHash).orElse(null))
            .parentBeaconBlockRoot(
                maybeParentBeaconBlockRoot.isPresent() ? maybeParentBeaconBlockRoot : null)
            .buildHeader();
    return mockHeader;
  }

  @Override
  protected JsonRpcResponse resp(final EnginePayloadParameter payload) {
    Object[] params =
        maybeParentBeaconBlockRoot
            .map(bytes32 -> new Object[] {payload, Collections.emptyList(), bytes32.toHexString()})
            .orElseGet(() -> new Object[] {payload});
    return method.response(
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", this.method.getName(), params)));
  }

  protected JsonRpcResponse resp(
      final EnginePayloadParameter payload, final List<Request> requests) {
    final List<String> requestsWithoutRequestId =
        requests.stream()
            .sorted(Comparator.comparing(Request::getType))
            .map(r -> r.getData().toHexString())
            .toList();
    Object[] params =
        maybeParentBeaconBlockRoot
            .map(
                bytes32 ->
                    new Object[] {
                      payload,
                      Collections.emptyList(),
                      bytes32.toHexString(),
                      requestsWithoutRequestId
                    })
            .orElseGet(() -> new Object[] {payload});
    return method.response(
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", this.method.getName(), params)));
  }

  private void mockProhibitedRequestsValidator() {
    var validator = new ProhibitedRequestValidator();
    when(protocolSpec.getRequestsValidator()).thenReturn(validator);
  }

  private void mockAllowedRequestsValidator() {
    var validator = new MainnetRequestsValidator();
    when(protocolSpec.getRequestsValidator()).thenReturn(validator);
  }
}

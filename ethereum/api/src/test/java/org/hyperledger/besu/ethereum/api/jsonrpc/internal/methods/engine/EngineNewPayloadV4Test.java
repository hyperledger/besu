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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.DepositParameterTestFixture.DEPOSIT_PARAM_1;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ValidatorExitTestFixture.VALIDATOR_EXIT_PARAMETER_1;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.DepositParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ValidatorExitParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.ValidatorExit;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.DepositsValidator;
import org.hyperledger.besu.ethereum.mainnet.PragueValidatorExitsValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidator;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EngineNewPayloadV4Test extends EngineNewPayloadV3Test {
  private static final Address depositContractAddress =
      Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");

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
  public void shouldReturnValidIfDepositsIsNull_WhenDepositsProhibited() {
    final List<DepositParameter> deposits = null;
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.ProhibitedDeposits());

    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(mockHeader))
        .thenReturn(Optional.of(mockHeader.getHash()));

    var resp = resp(mockEnginePayload(mockHeader, Collections.emptyList(), null, deposits, null));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfDepositsIsNull_WhenDepositsAllowed() {
    final List<DepositParameter> deposits = null;
    lenient()
        .when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.AllowedDeposits(depositContractAddress));

    var resp =
        resp(
            mockEnginePayload(
                createBlockHeader(Optional.empty(), Optional.empty(), Optional.empty()),
                Collections.emptyList(),
                null,
                deposits,
                null));

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfDepositsIsNotNull_WhenDepositsAllowed() {
    final List<DepositParameter> depositsParam = List.of(DEPOSIT_PARAM_1);
    final List<Deposit> deposits = List.of(DEPOSIT_PARAM_1.toDeposit());
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.AllowedDeposits(depositContractAddress));
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.of(deposits),
            Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(mockHeader))
        .thenReturn(Optional.of(mockHeader.getHash()));
    var resp =
        resp(mockEnginePayload(mockHeader, Collections.emptyList(), null, depositsParam, null));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfDepositsIsNotNull_WhenDepositsProhibited() {
    final List<DepositParameter> deposits = List.of();
    lenient()
        .when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.ProhibitedDeposits());

    var resp =
        resp(
            mockEnginePayload(
                createBlockHeader(
                    Optional.empty(), Optional.of(Collections.emptyList()), Optional.empty()),
                Collections.emptyList(),
                null,
                deposits,
                null));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfExitsIsNull_WhenExitsProhibited() {
    when(protocolSpec.getExitsValidator())
        .thenReturn(new ValidatorExitsValidator.ProhibitedExits());

    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(mockHeader))
        .thenReturn(Optional.of(mockHeader.getHash()));

    var resp = resp(mockEnginePayload(mockHeader, Collections.emptyList(), null, null, null));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfExitsIsNull_WhenExitsAllowed() {
    when(protocolSpec.getExitsValidator()).thenReturn(new PragueValidatorExitsValidator());

    var resp =
        resp(
            mockEnginePayload(
                createBlockHeader(Optional.empty(), Optional.empty(), Optional.empty()),
                Collections.emptyList(),
                null,
                null,
                null));

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfExitsIsNotNull_WhenExitsAllowed() {
    final List<ValidatorExitParameter> validatorExitsParams = List.of(VALIDATOR_EXIT_PARAMETER_1);
    final List<ValidatorExit> validatorExits =
        List.of(VALIDATOR_EXIT_PARAMETER_1.toValidatorExit());
    when(protocolSpec.getExitsValidator()).thenReturn(new PragueValidatorExitsValidator());

    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty(),
            Optional.of(validatorExits));
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(mockHeader))
        .thenReturn(Optional.of(mockHeader.getHash()));
    var resp =
        resp(
            mockEnginePayload(
                mockHeader, Collections.emptyList(), null, null, validatorExitsParams));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfExitsIsNotNull_WhenExitsProhibited() {
    final List<ValidatorExitParameter> validatorExits = List.of();
    when(protocolSpec.getExitsValidator())
        .thenReturn(new ValidatorExitsValidator.ProhibitedExits());

    var resp =
        resp(
            mockEnginePayload(
                createBlockHeader(
                    Optional.empty(), Optional.empty(), Optional.of(Collections.emptyList())),
                Collections.emptyList(),
                null,
                null,
                validatorExits));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  protected BlockHeader createBlockHeader(
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final Optional<List<Deposit>> maybeDeposits,
      final Optional<List<ValidatorExit>> maybeExits) {
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
            .depositsRoot(maybeDeposits.map(BodyValidation::depositsRoot).orElse(null))
            .exitsRoot(maybeExits.map(BodyValidation::exitsRoot).orElse(null))
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
}

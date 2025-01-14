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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameterTestFixture.WITHDRAWAL_PARAM_1;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_BLOB_GAS_USED_PARAMS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.UNSUPPORTED_FORK;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EngineNewPayloadV2Test extends AbstractEngineNewPayloadTest {

  public EngineNewPayloadV2Test() {}

  @Override
  @BeforeEach
  public void before() {
    super.before();
    this.method =
        new EngineNewPayloadV2(
            vertx,
            protocolSchedule,
            protocolContext,
            mergeCoordinator,
            ethPeers,
            engineCallListener,
            new NoOpMetricsSystem());
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadV2");
  }

  @Test
  public void shouldReturnValidIfWithdrawalsIsNotNull_WhenWithdrawalsAllowed() {
    final List<WithdrawalParameter> withdrawalsParam = List.of(WITHDRAWAL_PARAM_1);
    final List<Withdrawal> withdrawals = List.of(WITHDRAWAL_PARAM_1.toWithdrawal());
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.of(withdrawals));
    lenient()
        .when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    var resp = resp(mockEnginePayload(mockHeader, Collections.emptyList(), withdrawalsParam));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnValidIfWithdrawalsIsNull_WhenWithdrawalsProhibited() {
    final List<WithdrawalParameter> withdrawals = null;
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty());
    lenient()
        .when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    var resp = resp(mockEnginePayload(mockHeader, Collections.emptyList(), withdrawals));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfWithdrawalsIsNotNull_WhenWithdrawalsProhibited() {
    final List<WithdrawalParameter> withdrawals = List.of();
    lenient()
        .when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());

    var resp =
        resp(
            mockEnginePayload(
                createBlockHeader(Optional.of(Collections.emptyList())),
                Collections.emptyList(),
                withdrawals));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldValidateBlobGasUsedCorrectly() {
    // V2 should return error if non-null blobGasUsed
    BlockHeader blockHeader =
        createBlockHeaderFixture(Optional.of(Collections.emptyList()))
            .blobGasUsed(100L)
            .buildHeader();

    var resp = resp(mockEnginePayload(blockHeader, Collections.emptyList(), List.of()));
    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_BLOB_GAS_USED_PARAMS.getCode());
    assertThat(jsonRpcError.getData()).isEqualTo("Unexpected blob gas used field present");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldValidateExcessBlobGasCorrectly() {
    // V2 should return error if non-null ExcessBlobGas
    BlockHeader blockHeader =
        createBlockHeaderFixture(Optional.of(Collections.emptyList()))
            .excessBlobGas(BlobGas.MAX_BLOB_GAS)
            .buildHeader();

    var resp = resp(mockEnginePayload(blockHeader, Collections.emptyList(), List.of()));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    assertThat(jsonRpcError.getData()).isEqualTo("Unexpected excess blob gas field present");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidIfWithdrawalsIsNull_WhenWithdrawalsAllowed() {
    final List<WithdrawalParameter> withdrawals = null;
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());

    var resp =
        resp(
            mockEnginePayload(
                createBlockHeader(Optional.empty()), Collections.emptyList(), withdrawals));

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnUnsupportedForkIfBlockTimestampIsAfterCancunMilestone() {
    // Cancun starte at timestamp 30
    final long blockTimestamp = 31L;
    BlockHeader blockHeader =
        createBlockHeaderFixture(Optional.of(Collections.emptyList()))
            .timestamp(blockTimestamp)
            .buildHeader();

    var resp = resp(mockEnginePayload(blockHeader, Collections.emptyList(), List.of()));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(UNSUPPORTED_FORK.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  protected ExecutionEngineJsonRpcMethod.EngineStatus getExpectedInvalidBlockHashStatus() {
    return INVALID;
  }
}

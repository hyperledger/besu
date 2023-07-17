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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameterTestFixture.WITHDRAWAL_PARAM_1;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_PARAMS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineNewPayloadV2Test extends AbstractEngineNewPayloadTest {

  public EngineNewPayloadV2Test() {
    super(EngineNewPayloadV2::new);
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadV2");
  }

  @Test
  public void shouldReturnValidIfProtocolScheduleIsEmpty() {
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(null);
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    assertValidResponse(mockHeader, resp);
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
            Optional.of(withdrawals),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList(), withdrawalsParam, null));

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
            Optional.empty(),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList(), withdrawals, null, null));

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
            mockPayload(
                createBlockHeader(Optional.of(Collections.emptyList()), Optional.empty()),
                Collections.emptyList(),
                withdrawals,
                null,
                null));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidIfWithdrawalsIsNull_WhenWithdrawalsAllowed() {
    final List<WithdrawalParameter> withdrawals = null;
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());

    var resp =
        resp(
            mockPayload(
                createBlockHeader(Optional.empty(), Optional.empty()),
                Collections.emptyList(),
                withdrawals,
                null));

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }
}

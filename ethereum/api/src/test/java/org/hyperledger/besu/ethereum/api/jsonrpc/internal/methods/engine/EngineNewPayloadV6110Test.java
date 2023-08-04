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
import static org.hyperledger.besu.ethereum.api.graphql.internal.response.GraphQLError.INVALID_PARAMS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.DepositParameterTestFixture.DEPOSIT_PARAM_1;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.DepositParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.mainnet.DepositsValidator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EngineNewPayloadV6110Test extends EngineNewPayloadV2Test {

  public EngineNewPayloadV6110Test() {}

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadV6110");
  }

  @Test
  public void shouldReturnValidIfDepositsIsNotNull_WhenDepositsAllowed() {
    final List<DepositParameter> depositsParam = List.of(DEPOSIT_PARAM_1);
    final List<Deposit> deposits = List.of(DEPOSIT_PARAM_1.toDeposit());
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.AllowedDeposits(Address.ZERO));
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.of(deposits));

    var resp = resp(mockPayload(mockHeader, Collections.emptyList(), null, depositsParam));

    assertValidResponse(mockHeader, resp);
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
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList(), null, deposits, null));

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
            mockPayload(
                createBlockHeader(Optional.of(Collections.emptyList()), Optional.empty()),
                Collections.emptyList(),
                null,
                deposits,
                null));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidIfDepositsIsNull_WhenDepositsAllowed() {
    final List<DepositParameter> deposits = null;
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.AllowedDeposits(Address.ZERO));

    var resp =
        resp(
            mockPayload(
                createBlockHeader(Optional.empty(), Optional.empty()),
                Collections.emptyList(),
                null,
                deposits));

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }
}

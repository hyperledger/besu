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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePayloadStatusResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineNewPayloadV3Test extends AbstractEngineNewPayloadTest {

  public EngineNewPayloadV3Test() {
    super(EngineNewPayloadV3::new);
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadV3");
  }

  @Before
  @Override
  public void before() {
    super.before();
    lenient().when(protocolSpec.getGasCalculator()).thenReturn(new CancunGasCalculator());
    lenient()
        .when(protocolSchedule.hardforkFor(any()))
        .thenReturn(
            Optional.of(new ScheduledProtocolSpec.Hardfork("Cancun", super.CANCUN_TIMESTAMP)));
  }

  @Test
  public void shouldInvalidPayloadOnShortVersionedHash() {
    Bytes shortHash = Bytes.repeat((byte) 0x69, 31);
    EnginePayloadParameter payload = mock(EnginePayloadParameter.class);
    JsonRpcResponse badParam =
        method.response(
            new JsonRpcRequestContext(
                new JsonRpcRequest(
                    "2.0",
                    RpcMethod.ENGINE_NEW_PAYLOAD_V3.getMethodName(),
                    new Object[] {payload, List.of(shortHash.toHexString())})));
    EnginePayloadStatusResult res = fromSuccessResp(badParam);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    assertThat(res.getError()).isEqualTo("Invalid versionedHash");
  }

  @Override
  protected BlockHeader createBlockHeader(
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final Optional<List<Deposit>> maybeDeposits) {
    BlockHeader parentBlockHeader =
        new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .timestamp(super.CANCUN_TIMESTAMP)
            .buildHeader();
    BlockHeader mockHeader =
        new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .parentHash(parentBlockHeader.getParentHash())
            .number(parentBlockHeader.getNumber() + 1)
            .timestamp(parentBlockHeader.getTimestamp() + 1)
            .withdrawalsRoot(maybeWithdrawals.map(BodyValidation::withdrawalsRoot).orElse(null))
            .depositsRoot(maybeDeposits.map(BodyValidation::depositsRoot).orElse(null))
            .buildHeader();
    return mockHeader;
  }

  @Override
  protected BlockHeader buildBlockHeader() {
    return new BlockHeaderTestFixture().timestamp(super.CANCUN_TIMESTAMP).buildHeader();
  }
}

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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadResultV2;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EngineGetPayloadV2Test extends AbstractEngineGetPayloadTest {

  public EngineGetPayloadV2Test() {
    super(EngineGetPayloadV2::new);
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_getPayloadV2");
  }

  @Override
  @Test
  public void shouldReturnBlockForKnownPayloadId() {
    // should return withdrawals for a post-Shanghai block
    when(mergeContext.retrieveBlockById(mockPid))
        .thenReturn(Optional.of(mockBlockWithReceiptsAndWithdrawals));

    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName(), mockPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV2.class);
              final EngineGetPayloadResultV2 res = (EngineGetPayloadResultV2) r.getResult();
              assertThat(res.getExecutionPayload().getWithdrawals()).isNotNull();
              assertThat(res.getExecutionPayload().getHash())
                  .isEqualTo(mockHeader.getHash().toString());
              assertThat(res.getBlockValue()).isEqualTo(Quantity.create(0));
              assertThat(res.getExecutionPayload().getPrevRandao())
                  .isEqualTo(mockHeader.getPrevRandao().map(Bytes32::toString).orElse(""));
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnBlockForKnownPayloadIdPostV6110() {
    // should return deposits for a post-V6110 block
    when(mergeContext.retrieveBlockById(mockPid))
        .thenReturn(Optional.of(mockBlockWithReceiptsAndDeposits));

    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName(), mockPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV2.class);
              final EngineGetPayloadResultV2 res = (EngineGetPayloadResultV2) r.getResult();
              assertThat(res.getExecutionPayload().getDeposits()).isNotNull();
              assertThat(res.getExecutionPayload().getHash())
                  .isEqualTo(mockHeader.getHash().toString());
              assertThat(res.getBlockValue()).isEqualTo(Quantity.create(0));
              assertThat(res.getExecutionPayload().getPrevRandao())
                  .isEqualTo(mockHeader.getPrevRandao().map(Bytes32::toString).orElse(""));
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnExecutionPayloadWithoutWithdrawals_PreShanghaiBlock() {
    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName(), mockPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV2.class);
              final EngineGetPayloadResultV2 res = (EngineGetPayloadResultV2) r.getResult();
              assertThat(res.getExecutionPayload().getWithdrawals()).isNull();
              assertThat(res.getExecutionPayload().getDeposits()).isNull();
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnExecutionPayloadWithoutDeposits_PreV6110Block() {
    when(mergeContext.retrieveBlockById(mockPid))
        .thenReturn(Optional.of(mockBlockWithReceiptsAndWithdrawals));

    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName(), mockPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV2.class);
              final EngineGetPayloadResultV2 res = (EngineGetPayloadResultV2) r.getResult();
              assertThat(res.getExecutionPayload().getDeposits()).isNull();
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  protected String getMethodName() {
    return RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName();
  }
}

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_BLOCK_HASH;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EngineNewPayloadV1Test extends AbstractEngineNewPayloadTest {

  public EngineNewPayloadV1Test() {}

  @Override
  @BeforeEach
  public void before() {
    super.before();
    this.method =
        new EngineNewPayloadV1(
            vertx,
            protocolSchedule,
            protocolContext,
            mergeCoordinator,
            ethPeers,
            engineCallListener);
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadV1");
  }

  @Test
  public void shouldFailWhenVersionedHashesPresent() {
    final JsonRpcRequestContext requestContextMock =
        getRequestContextMock(null, null, null, new String[] {"1"}, null);
    assertThatThrownBy(() -> method.getAndCheckEngineNewPayloadRequestParams(requestContextMock))
        .hasMessage("non-null VersionedHashes pre-cancun");
  }

  @Test
  public void shouldFailWhenParentBeaconBlockRootPresent() {
    final JsonRpcRequestContext requestContextMock =
        getRequestContextMock(null, null, null, null, "0x1");
    assertThatThrownBy(() -> method.getAndCheckEngineNewPayloadRequestParams(requestContextMock))
        .hasMessage("non-null ParentBeaconBlockRoot pre-cancun");
  }

  @Test
  public void shouldFailWhenBlobGasUsedPresent() {
    final JsonRpcRequestContext requestContextMock =
        getRequestContextMock(null, 1L, null, null, null);
    assertThatThrownBy(() -> method.getAndCheckEngineNewPayloadRequestParams(requestContextMock))
        .hasMessage("non-null BlobGasUsed pre-cancun");
  }

  @Test
  public void shouldFailWhenExcessBlobGasPresent() {
    final JsonRpcRequestContext requestContextMock =
        getRequestContextMock(null, null, "1", null, null);
    assertThatThrownBy(() -> method.getAndCheckEngineNewPayloadRequestParams(requestContextMock))
        .hasMessage("non-null ExcessBlobGas pre-cancun");
  }

  @Test
  public void shouldFailWhenWithdrawalsPresent() {
    final JsonRpcRequestContext requestContextMock =
        getRequestContextMock(Collections.emptyList(), null, "1", null, null);
    assertThatThrownBy(() -> method.getAndCheckEngineNewPayloadRequestParams(requestContextMock))
        .hasMessage("non-null Withdrawals pre-shanghai");
  }

  @Override
  protected ExecutionEngineJsonRpcMethod.EngineStatus getExpectedInvalidBlockHashStatus() {
    return INVALID_BLOCK_HASH;
  }
}

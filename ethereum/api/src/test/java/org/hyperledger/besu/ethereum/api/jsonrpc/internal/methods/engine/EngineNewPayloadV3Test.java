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
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
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

  @Test
  public void shouldInvalidParamsOnShortVersionedHash() {
    Bytes shortHash = Bytes.repeat((byte)0x69, 31);
    EnginePayloadParameter payload = mock(EnginePayloadParameter.class);
    JsonRpcResponse badParam =
        method.response(
            new JsonRpcRequestContext(
                    new JsonRpcRequest(
                            "2.0", RpcMethod.ENGINE_NEW_PAYLOAD_V2.getMethodName(), new Object[] {payload, List.of(shortHash.toHexString())})));
    assertThat(badParam.getType()).isEqualTo(JsonRpcResponseType.ERROR);
  }
}

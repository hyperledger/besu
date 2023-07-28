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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineForkchoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EngineForkchoiceUpdatedV1Test extends AbstractEngineForkchoiceUpdatedTest {

  public EngineForkchoiceUpdatedV1Test() {
    super(EngineForkchoiceUpdatedV1::new);
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_forkchoiceUpdatedV1");
  }

  @Test
  public void shouldReturnInvalidOnBadTerminalBlock() {
    BlockHeader mockHeader = blockHeaderBuilder.buildHeader();

    when(mergeCoordinator.getOrSyncHeadByHash(mockHeader.getHash(), Hash.ZERO))
        .thenReturn(Optional.of(mockHeader));
    when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(mockHeader)).thenReturn(false);
    assertSuccessWithPayloadForForkchoiceResult(
        new EngineForkchoiceUpdatedParameter(mockHeader.getHash(), Hash.ZERO, Hash.ZERO),
        Optional.empty(),
        mock(MergeMiningCoordinator.ForkchoiceResult.class),
        INVALID,
        Optional.of(Hash.ZERO));
  }

  @Override
  protected String getMethodName() {
    return RpcMethod.ENGINE_FORKCHOICE_UPDATED_V1.getMethodName();
  }

  @Override
  protected boolean validateTerminalPoWBlock() {
    return true;
  }

  @Override
  protected RpcErrorType expectedInvalidPayloadError() {
    return RpcErrorType.INVALID_PAYLOAD_ATTRIBUTES;
  }
}

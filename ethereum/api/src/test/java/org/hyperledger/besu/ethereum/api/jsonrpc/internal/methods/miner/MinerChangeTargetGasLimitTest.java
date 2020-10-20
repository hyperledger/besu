/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.MockitoAnnotations.initMocks;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class MinerChangeTargetGasLimitTest {

  @Mock MiningCoordinator miningCoordinator;
  private MinerChangeTargetGasLimit minerChangeTargetGasLimit;

  @Before
  public void setUp() {
    initMocks(this);
    minerChangeTargetGasLimit = new MinerChangeTargetGasLimit(miningCoordinator);
  }

  @Test
  public void failsWithInvalidValue() {
    final long newTargetGasLimit = -1;
    final var request = request(newTargetGasLimit);

    doThrow(IllegalArgumentException.class)
        .when(miningCoordinator)
        .changeTargetGasLimit(newTargetGasLimit);

    assertThat(minerChangeTargetGasLimit.response(request))
        .isEqualTo(
            new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS));
  }

  @Test
  public void failsWithInvalidGasCalculator() {
    final long newTargetGasLimit = 1;
    final var request = request(newTargetGasLimit);

    doThrow(UnsupportedOperationException.class)
        .when(miningCoordinator)
        .changeTargetGasLimit(newTargetGasLimit);

    assertThat(minerChangeTargetGasLimit.response(request))
        .isEqualTo(
            new JsonRpcErrorResponse(
                request.getRequest().getId(),
                JsonRpcError.TARGET_GAS_LIMIT_MODIFICATION_UNSUPPORTED));
  }

  private JsonRpcRequestContext request(final long longParam) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0", "miner_changeTargetGasLimit", new Object[] {String.valueOf(longParam)}));
  }
}

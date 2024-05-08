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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;

/** The type Miner set etherbase. */
public class MinerSetEtherbase implements JsonRpcMethod {

  private final MinerSetCoinbase minerSetCoinbaseMethod;

  /**
   * Instantiates a new Miner set etherbase.
   *
   * @param minerSetCoinbaseMethod the miner set coinbase method
   */
  public MinerSetEtherbase(final MinerSetCoinbase minerSetCoinbaseMethod) {

    this.minerSetCoinbaseMethod = minerSetCoinbaseMethod;
  }

  @Override
  public String getName() {
    return RpcMethod.MINER_SET_ETHERBASE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return minerSetCoinbaseMethod.response(requestContext);
  }
}

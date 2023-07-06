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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner.MinerChangeTargetGasLimit;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner.MinerSetCoinbase;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner.MinerSetEtherbase;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner.MinerStart;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner.MinerStop;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;

import java.util.Map;

public class MinerJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final MiningCoordinator miningCoordinator;

  public MinerJsonRpcMethods(final MiningCoordinator miningCoordinator) {
    this.miningCoordinator = miningCoordinator;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.MINER.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final MinerSetCoinbase minerSetCoinbase = new MinerSetCoinbase(miningCoordinator);
    return mapOf(
        new MinerStart(miningCoordinator),
        new MinerStop(miningCoordinator),
        minerSetCoinbase,
        new MinerSetEtherbase(minerSetCoinbase),
        new MinerChangeTargetGasLimit(miningCoordinator));
  }
}

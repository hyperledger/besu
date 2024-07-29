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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.SyncingResult;
import org.hyperledger.besu.ethereum.core.Synchronizer;

/*
 * SyncProgress retrieves the current progress of the syncing algorithm. If there's no sync
 * currently running, it returns false.
 */
public class EthSyncing implements JsonRpcMethod {

  private final Synchronizer synchronizer;

  public EthSyncing(final Synchronizer synchronizer) {
    this.synchronizer = synchronizer;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SYNCING.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    // Returns false when not synchronizing.
    final Object result =
        synchronizer.getSyncStatus().map(s -> (Object) new SyncingResult(s)).orElse(false);
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
  }
}

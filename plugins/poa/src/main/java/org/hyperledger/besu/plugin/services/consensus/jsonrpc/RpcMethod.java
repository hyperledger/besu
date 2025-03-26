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
package org.hyperledger.besu.plugin.services.consensus.jsonrpc;

import java.util.Collection;
import java.util.HashSet;

/** RPC method class */
public enum RpcMethod {
  /** Placeholder */
  QBFT_DISCARD_VALIDATOR_VOTE("qbft_discardValidatorVote"),
  /** Placeholder */
  QBFT_GET_PENDING_VOTES("qbft_getPendingVotes"),
  /** Placeholder */
  QBFT_GET_VALIDATORS_BY_BLOCK_HASH("qbft_getValidatorsByBlockHash"),
  /** Placeholder */
  QBFT_GET_VALIDATORS_BY_BLOCK_NUMBER("qbft_getValidatorsByBlockNumber"),
  /** Placeholder */
  QBFT_PROPOSE_VALIDATOR_VOTE("qbft_proposeValidatorVote"),
  /** Placeholder */
  QBFT_GET_SIGNER_METRICS("qbft_getSignerMetrics"),
  /** Placeholder */
  QBFT_GET_REQUEST_TIMEOUT_SECONDS("qbft_getRequestTimeoutSeconds");

  private final String methodName;

  private static final Collection<String> allMethodNames;

  /**
   * Placeholder
   *
   * @return get the JSON/RPC method name
   */
  public String getMethodName() {
    return methodName;
  }

  static {
    allMethodNames = new HashSet<>();
    for (RpcMethod m : RpcMethod.values()) {
      allMethodNames.add(m.getMethodName());
    }
  }

  RpcMethod(final String methodName) {
    this.methodName = methodName;
  }

  /**
   * Placeholder
   *
   * @param rpcMethodName the RPC method name
   * @return placeholder
   */
  public static boolean rpcMethodExists(final String rpcMethodName) {
    return allMethodNames.contains(rpcMethodName);
  }
}

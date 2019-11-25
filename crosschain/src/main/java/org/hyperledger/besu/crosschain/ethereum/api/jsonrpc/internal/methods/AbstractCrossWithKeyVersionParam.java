/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.crosschain.core.CrosschainController;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractCrossWithKeyVersionParam implements JsonRpcMethod {
  protected static final Logger LOG = LogManager.getLogger();

  protected final CrosschainController crosschainController;
  protected final JsonRpcParameter parameters;

  public AbstractCrossWithKeyVersionParam(
      final CrosschainController crosschainController, final JsonRpcParameter parameters) {
    this.crosschainController = crosschainController;
    this.parameters = parameters;
  }

  protected long getKeyVersionParameter(final JsonRpcRequest request) {
    Object[] params = request.getParams();
    return parameters.required(params, 0, Long.class);
  }
}

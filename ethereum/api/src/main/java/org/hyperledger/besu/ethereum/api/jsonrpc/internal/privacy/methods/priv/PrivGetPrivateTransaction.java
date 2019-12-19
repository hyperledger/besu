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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionGroupResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.util.bytes.BytesValues;

import org.apache.logging.log4j.Logger;

public class PrivGetPrivateTransaction implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final BlockchainQueries blockchain;
  private final PrivacyController privacyController;

  public PrivGetPrivateTransaction(
      final BlockchainQueries blockchain, final PrivacyController privacyController) {
    this.blockchain = blockchain;
    this.privacyController = privacyController;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_PRIVATE_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    LOG.trace("Executing {}", RpcMethod.PRIV_GET_PRIVATE_TRANSACTION.getMethodName());

    final Hash hash = requestContext.getRequiredParameter(0, Hash.class);
    final TransactionWithMetadata resultTransaction =
        blockchain.transactionByHash(hash).orElse(null);

    if (resultTransaction == null) {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }
    try {
      LOG.trace("Fetching transaction information");
      final ReceiveResponse receiveResponse =
          privacyController.retrieveTransaction(
              BytesValues.asBase64String(resultTransaction.getTransaction().getPayload()));
      LOG.trace("Received transaction information");

      final BytesValueRLPInput bytesValueRLPInput =
          new BytesValueRLPInput(BytesValues.fromBase64(receiveResponse.getPayload()), false);

      final PrivateTransaction privateTransaction = PrivateTransaction.readFrom(bytesValueRLPInput);
      if (privateTransaction.getPrivacyGroupId().isPresent()) {
        return new JsonRpcSuccessResponse(
            requestContext.getRequest().getId(),
            new PrivateTransactionGroupResult(privateTransaction));
      } else {
        return new JsonRpcSuccessResponse(
            requestContext.getRequest().getId(),
            new PrivateTransactionLegacyResult(privateTransaction));
      }
    } catch (final Exception e) {
      LOG.error("Failed to fetch private transaction", e);
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }
  }
}

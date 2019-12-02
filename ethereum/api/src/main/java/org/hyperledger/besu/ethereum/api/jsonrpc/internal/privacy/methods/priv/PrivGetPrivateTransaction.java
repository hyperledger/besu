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

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionGroupResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.util.bytes.BytesValues;

import org.apache.logging.log4j.Logger;

public class PrivGetPrivateTransaction extends PrivacyApiMethod {

  private static final Logger LOG = getLogger();

  private final BlockchainQueries blockchain;
  private final Enclave enclave;

  public PrivGetPrivateTransaction(
      final BlockchainQueries blockchain, final PrivacyParameters privacyParameters) {
    super(privacyParameters);
    this.blockchain = blockchain;
    this.enclave = privacyParameters.getEnclave();
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_PRIVATE_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse doResponse(final JsonRpcRequestContext requestContext) {
    LOG.trace("Executing {}", RpcMethod.PRIV_GET_PRIVATE_TRANSACTION.getMethodName());

    final Hash hash = requestContext.getRequiredParameter(0, Hash.class);
    final TransactionWithMetadata resultTransaction =
        blockchain.transactionByHash(hash).orElse(null);

    if (resultTransaction == null) {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }
    try {
      ReceiveResponse receiveResponse =
          getReceiveResponseFromEnclave(
              BytesValues.asBase64String(resultTransaction.getTransaction().getPayload()),
              privacyParameters.getEnclavePublicKey());

      LOG.trace("Received transaction information from Enclave");

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
    } catch (Exception e) {
      LOG.error("Failed to fetch private transaction with error " + e.getMessage());
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }
  }

  private ReceiveResponse getReceiveResponseFromEnclave(
      final String enclaveKey, final String publicKey) throws Exception {
    LOG.trace("Fetching transaction information from Enclave");
    final ReceiveRequest enclaveRequest = new ReceiveRequest(enclaveKey, publicKey);
    ReceiveResponse enclaveResponse = enclave.receive(enclaveRequest);
    LOG.trace("Received transaction information from Enclave");
    return enclaveResponse;
  }
}

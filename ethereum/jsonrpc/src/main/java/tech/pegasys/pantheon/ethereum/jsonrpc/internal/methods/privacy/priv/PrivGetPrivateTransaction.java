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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.priv;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.privacy.PrivateTransactionGroupResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import org.apache.logging.log4j.Logger;

public class PrivGetPrivateTransaction implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final Enclave enclave;
  private final JsonRpcParameter parameters;
  private final PrivacyParameters privacyParameters;

  public PrivGetPrivateTransaction(
      final Enclave enclave,
      final JsonRpcParameter parameters,
      final PrivacyParameters privacyParameters) {
    this.enclave = enclave;
    this.parameters = parameters;
    this.privacyParameters = privacyParameters;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_PRIVATE_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    LOG.trace("Executing {}", RpcMethod.PRIV_GET_PRIVATE_TRANSACTION.getMethodName());
    final String enclaveKey = parameters.required(request.getParams(), 0, String.class);
    try {
      ReceiveResponse receiveResponse =
          getReceiveResponseFromEnclave(
              BytesValues.asBase64String(BytesValue.fromHexString(enclaveKey)),
              privacyParameters.getEnclavePublicKey());

      LOG.trace("Received transaction information from Enclave");

      final BytesValueRLPInput bytesValueRLPInput =
          new BytesValueRLPInput(BytesValues.fromBase64(receiveResponse.getPayload()), false);

      final PrivateTransaction privateTransaction = PrivateTransaction.readFrom(bytesValueRLPInput);
      if (privateTransaction.getPrivacyGroupId().isPresent()) {
        return new JsonRpcSuccessResponse(
            request.getId(), new PrivateTransactionGroupResult(privateTransaction));
      } else {
        return new JsonRpcSuccessResponse(
            request.getId(), new PrivateTransactionLegacyResult(privateTransaction));
      }
    } catch (Exception e) {
      LOG.error("Failed to fetch private transaction with error " + e.getMessage());
      return new JsonRpcSuccessResponse(request.getId(), null);
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

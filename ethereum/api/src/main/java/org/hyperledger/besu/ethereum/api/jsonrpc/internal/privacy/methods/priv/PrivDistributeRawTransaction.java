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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.ENCLAVE_ERROR;

import java.util.Base64;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacySendTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacySendTransaction.ErrorResponseException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

public class PrivDistributeRawTransaction implements JsonRpcMethod {

  private static final Logger LOG = getLogger();
  private final PrivacySendTransaction privacySendTransaction;

  public PrivDistributeRawTransaction(
      final PrivacySendTransaction privacySendTransaction) {
    this.privacySendTransaction = privacySendTransaction;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_DISTRIBUTE_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final PrivateTransaction privateTransaction;
    try {
      privateTransaction = privacySendTransaction.validateAndDecodeRequest(requestContext);
      final String enclaveKey =
          privacySendTransaction.sendTransactionToEnclave(privateTransaction, requestContext);
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(), hexEncodeEnclaveKey(enclaveKey));
    } catch (final MultiTenancyValidationException e) {
      LOG.error("Unauthorized privacy multi-tenancy rpc request. {}", e.getMessage());
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), ENCLAVE_ERROR);
    } catch (ErrorResponseException e) {
      return e.getResponse();
    }
  }

  private String hexEncodeEnclaveKey(final String enclaveKey) {
    return Bytes.wrap(Base64.getDecoder().decode(enclaveKey)).toHexString();
  }
}

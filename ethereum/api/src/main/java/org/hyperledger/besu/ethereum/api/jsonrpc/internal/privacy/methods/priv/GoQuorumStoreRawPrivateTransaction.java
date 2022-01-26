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

import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.DECODE_ERROR;

import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.enclave.types.StoreRawResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Base64;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoQuorumStoreRawPrivateTransaction implements JsonRpcMethod {

  private static final Logger LOG =
      LoggerFactory.getLogger(GoQuorumStoreRawPrivateTransaction.class);
  private final GoQuorumEnclave enclave;

  public GoQuorumStoreRawPrivateTransaction(final GoQuorumEnclave enclave) {
    this.enclave = enclave;
  }

  @Override
  public String getName() {
    return RpcMethod.GOQUORUM_STORE_RAW.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Object id = requestContext.getRequest().getId();
    final String payload = requestContext.getRequiredParameter(0, String.class);

    try {
      LOG.debug("sending payload to GoQuorum enclave" + payload);
      final StoreRawResponse storeRawResponse =
          enclave.storeRaw(
              Base64.getEncoder().encodeToString(Bytes.fromHexString(payload).toArray()));
      final String enclaveLookupId = storeRawResponse.getKey();
      LOG.debug("retrieved lookupId from GoQuorum enclave " + enclaveLookupId);
      return new JsonRpcSuccessResponse(id, hexEncodeEnclaveKey(enclaveLookupId));
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.error("Unable to decode private transaction for store", e);
      return new JsonRpcErrorResponse(id, DECODE_ERROR);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(id, convertEnclaveInvalidReason(e.getMessage()));
    }
  }

  private String hexEncodeEnclaveKey(final String enclaveKey) {
    return Bytes.wrap(Base64.getDecoder().decode(enclaveKey)).toHexString();
  }
}

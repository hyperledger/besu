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
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.DECODE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.ENCLAVE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Base64;
import java.util.Optional;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class PrivDistributeRawTransaction implements JsonRpcMethod {

  private static final Logger LOG = getLogger();
  private final PrivacyController privacyController;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;
  private final boolean onchainPrivacyGroupsEnabled;

  public PrivDistributeRawTransaction(
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider,
      final boolean onchainPrivacyGroupsEnabled) {
    this.privacyController = privacyController;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
    this.onchainPrivacyGroupsEnabled = onchainPrivacyGroupsEnabled;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_DISTRIBUTE_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Object id = requestContext.getRequest().getId();
    final String rawPrivateTransaction = requestContext.getRequiredParameter(0, String.class);

    try {
      final PrivateTransaction privateTransaction =
          PrivateTransaction.readFrom(RLP.input(Bytes.fromHexString(rawPrivateTransaction)));

      final String enclavePublicKey =
          enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser());

      if (!privateTransaction.getPrivateFrom().equals(Bytes.fromBase64String(enclavePublicKey))) {
        return new JsonRpcErrorResponse(id, PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY);
      }

      Optional<PrivacyGroup> maybePrivacyGroup = null;
      final Optional<Bytes> maybePrivacyGroupId = privateTransaction.getPrivacyGroupId();
      if (onchainPrivacyGroupsEnabled) {
        if (!maybePrivacyGroupId.isPresent()) {
          return new JsonRpcErrorResponse(id, JsonRpcError.ONCHAIN_PRIVACY_GROUP_ID_NOT_AVAILABLE);
        }
        maybePrivacyGroup =
            privacyController.retrieveOnChainPrivacyGroup(
                maybePrivacyGroupId.get(), enclavePublicKey);
        if (maybePrivacyGroup.isEmpty()
            && !privacyController.isGroupAdditionTransaction(privateTransaction)) {
          return new JsonRpcErrorResponse(id, JsonRpcError.ONCCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST);
        }
      } else { // !onchainPirvacyGroupEnabled
        if (maybePrivacyGroupId.isPresent()) {
          maybePrivacyGroup =
              privacyController.retrieveOffChainPrivacyGroup(
                  maybePrivacyGroupId.get().toBase64String(), enclavePublicKey);
        } else {
          maybePrivacyGroup = Optional.empty();
        }
      }

      final ValidationResult<TransactionInvalidReason> validationResult =
          privacyController.validatePrivateTransaction(privateTransaction, enclavePublicKey);
      if (!validationResult.isValid()) {
        return new JsonRpcErrorResponse(
            id, convertTransactionInvalidReason(validationResult.getInvalidReason()));
      }

      final String enclaveKey =
          privacyController.sendTransaction(
              privateTransaction, enclavePublicKey, maybePrivacyGroup);
      return new JsonRpcSuccessResponse(id, hexEncodeEnclaveKey(enclaveKey));
    } catch (final MultiTenancyValidationException e) {
      LOG.error("Unauthorized privacy multi-tenancy rpc request. {}", e.getMessage());
      return new JsonRpcErrorResponse(id, ENCLAVE_ERROR);
    } catch (final IllegalArgumentException | RLPException e) {
      return new JsonRpcErrorResponse(id, DECODE_ERROR);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(id, convertEnclaveInvalidReason(e.getMessage()));
    }
  }

  private String hexEncodeEnclaveKey(final String enclaveKey) {
    return Bytes.wrap(Base64.getDecoder().decode(enclaveKey)).toHexString();
  }
}

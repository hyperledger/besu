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
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.DECODE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.ENCLAVE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.FlexibleUtil;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated(since = "24.12.0")
public class PrivDistributeRawTransaction implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(PrivDistributeRawTransaction.class);
  private final PrivacyController privacyController;
  private final PrivacyIdProvider privacyIdProvider;
  private final boolean flexiblePrivacyGroupsEnabled;

  public PrivDistributeRawTransaction(
      final PrivacyController privacyController,
      final PrivacyIdProvider privacyIdProvider,
      final boolean flexiblePrivacyGroupsEnabled) {
    this.privacyController = privacyController;
    this.privacyIdProvider = privacyIdProvider;
    this.flexiblePrivacyGroupsEnabled = flexiblePrivacyGroupsEnabled;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_DISTRIBUTE_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Object id = requestContext.getRequest().getId();
    final String rawPrivateTransaction;
    try {
      rawPrivateTransaction = requestContext.getRequiredParameter(0, String.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid private transaction parameter (index 0)",
          RpcErrorType.INVALID_TRANSACTION_PARAMS,
          e);
    }

    try {
      final PrivateTransaction privateTransaction =
          PrivateTransaction.readFrom(RLP.input(Bytes.fromHexString(rawPrivateTransaction)));

      final String privacyUserId = privacyIdProvider.getPrivacyUserId(requestContext.getUser());

      if (!privateTransaction.getPrivateFrom().equals(Bytes.fromBase64String(privacyUserId))) {
        return new JsonRpcErrorResponse(id, PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY);
      }

      final Optional<Bytes> maybePrivacyGroupId = privateTransaction.getPrivacyGroupId();

      if (flexiblePrivacyGroupsEnabled && maybePrivacyGroupId.isEmpty()) {
        return new JsonRpcErrorResponse(id, RpcErrorType.FLEXIBLE_PRIVACY_GROUP_ID_NOT_AVAILABLE);
      }

      Optional<PrivacyGroup> maybePrivacyGroup =
          maybePrivacyGroupId.flatMap(
              gId ->
                  privacyController.findPrivacyGroupByGroupId(gId.toBase64String(), privacyUserId));

      if (flexiblePrivacyGroupsEnabled) {
        if (FlexibleUtil.isGroupAdditionTransaction(privateTransaction)) {
          final List<String> participantsFromParameter =
              FlexibleUtil.getParticipantsFromParameter(privateTransaction.getPayload());
          if (maybePrivacyGroup.isEmpty()) {
            maybePrivacyGroup =
                Optional.of(
                    new PrivacyGroup(
                        maybePrivacyGroupId.get().toBase64String(),
                        PrivacyGroup.Type.FLEXIBLE,
                        "",
                        "",
                        participantsFromParameter));
          }
          maybePrivacyGroup.get().addMembers(participantsFromParameter);
        }
        if (maybePrivacyGroup.isEmpty()) {
          return new JsonRpcErrorResponse(id, RpcErrorType.FLEXIBLE_PRIVACY_GROUP_DOES_NOT_EXIST);
        }
      }

      final ValidationResult<TransactionInvalidReason> validationResult =
          privacyController.validatePrivateTransaction(privateTransaction, privacyUserId);

      if (!validationResult.isValid()) {
        return new JsonRpcErrorResponse(
            id, convertTransactionInvalidReason(validationResult.getInvalidReason()));
      }

      final String enclaveKey =
          privacyController.createPrivateMarkerTransactionPayload(
              privateTransaction, privacyUserId, maybePrivacyGroup);
      return new JsonRpcSuccessResponse(id, hexEncodeEnclaveKey(enclaveKey));
    } catch (final MultiTenancyValidationException e) {
      LOG.error("Unauthorized privacy multi-tenancy rpc request. {}", e.getMessage());
      return new JsonRpcErrorResponse(id, ENCLAVE_ERROR);
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.error("Unable to decode transaction for distribute");
      return new JsonRpcErrorResponse(id, DECODE_ERROR);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(id, convertEnclaveInvalidReason(e.getMessage()));
    }
  }

  private String hexEncodeEnclaveKey(final String enclaveKey) {
    return Bytes.wrap(Base64.getDecoder().decode(enclaveKey)).toHexString();
  }
}

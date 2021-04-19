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

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class DebugFaucet implements JsonRpcMethod {
  private static final com.google.common.base.Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final long FAUCET_ACCESS_INTERVAL_SECONDS = TimeUnit.HOURS.toSeconds(6);

  private final Supplier<BlockchainQueries> blockchainQueries;
  private final Supplier<TransactionPool> transactionPool;
  private final BigInteger chaindId;
  private final KeyPair faucetKeyPair;
  private final Address faucetAddress;
  final Wei faucetAmount;
  private final Map<String, Instant> access = new HashMap<>();

  public DebugFaucet(
      final BlockchainQueries blockchainQueries,
      final TransactionPool transactionPool,
      final BigInteger chaindId,
      final String faucetPrivateKey,
      final Wei faucetAmount) {
    this.blockchainQueries = Suppliers.ofInstance(blockchainQueries);
    this.transactionPool = Suppliers.ofInstance(transactionPool);
    this.chaindId = chaindId;
    this.faucetKeyPair = keyPair(faucetPrivateKey);
    this.faucetAddress = Util.publicKeyToAddress(this.faucetKeyPair.getPublicKey());
    this.faucetAmount = faucetAmount;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_FAUCET.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 1) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    final String recipientAddress = requestContext.getRequiredParameter(0, String.class);

    if (!allowed(recipientAddress)) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.UNAUTHORIZED);
    }

    final Transaction transaction;
    try {
      final long nonce = this.blockchainQueries.get().getTransactionCount(faucetAddress);
      transaction =
          Transaction.builder()
              .chainId(chaindId)
              .nonce(nonce)
              .value(faucetAmount)
              .gasLimit(30000)
              .payload(Bytes.EMPTY.trimLeadingZeros())
              .gasPrice(Wei.of(1000))
              .to(Address.fromHexString(recipientAddress))
              .guessType()
              .signAndBuild(faucetKeyPair);
    } catch (final RLPException | IllegalArgumentException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final ValidationResult<TransactionInvalidReason> validationResult =
        transactionPool.get().addLocalTransaction(transaction);
    return validationResult.either(
        () -> {
          access.put(recipientAddress, Instant.now());
          return new JsonRpcSuccessResponse(
              requestContext.getRequest().getId(), transaction.getHash().toString());
        },
        errorReason ->
            new JsonRpcErrorResponse(
                requestContext.getRequest().getId(),
                JsonRpcErrorConverter.convertTransactionInvalidReason(errorReason)));
  }

  private boolean allowed(final String address) {
    if (!access.containsKey(address)) {
      return true;
    }
    final Instant lastSeen = access.get(address);
    return lastSeen.plusSeconds(FAUCET_ACCESS_INTERVAL_SECONDS).compareTo(Instant.now()) <= 0;
  }

  private static KeyPair keyPair(final String privateKey) {
    final SignatureAlgorithm signatureAlgorithm = SIGNATURE_ALGORITHM.get();
    return signatureAlgorithm.createKeyPair(
        signatureAlgorithm.createPrivateKey(Bytes32.fromHexString(privateKey)));
  }
}

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

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.function.Supplier;

import com.google.common.base.Splitter;
import com.google.common.base.Suppliers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class EthSendRawTransaction implements JsonRpcMethod {
  private static final Logger LOG = LogManager.getLogger();

  public EthSendRawTransaction(final TransactionPool transactionPool) {
    this(Suppliers.ofInstance(transactionPool), false);
  }

  public EthSendRawTransaction(
      final Supplier<TransactionPool> transactionPool, final boolean sendEmptyHashOnInvalidBlock) {
    System.out.println(transactionPool);
    System.out.println(sendEmptyHashOnInvalidBlock);
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 1) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    final String raw = requestContext.getRequiredParameter(0, String.class);

    try {
      final String response = decode(raw);
      System.out.println(response);
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), response);
    } catch (Exception e) {
      LOG.error(e);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INTERNAL_ERROR);
    }
  }

  private static String decode(final String raw) throws Exception {
    final String privateKey = "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
    final String rpcUrl = "http://127.0.0.1:8545";
    final Iterable<String> tokens = Splitter.on('-').split(raw);
    final long nonce = Long.parseLong(tokens.iterator().next());
    final long value = Long.parseLong(tokens.iterator().next());
    final long gasLimit = Long.parseLong(tokens.iterator().next());
    final String to = tokens.iterator().next();
    final String gasPriceStr = tokens.iterator().next();
    final String gasPremiumStr = tokens.iterator().next();
    final String feeCapStr = tokens.iterator().next();
    final Transaction.Builder builder =
        new Transaction.Builder()
            .nonce(nonce)
            .gasLimit(gasLimit)
            .value(Wei.of(value))
            .payload(Bytes.fromHexString("0x00"))
            .to(Address.fromHexString(to));

    if (!"NULL".equals(gasPriceStr)) {
      builder.gasPrice(Wei.of(Long.parseLong(gasPriceStr)));
    } else if (!"NULL".equals(gasPremiumStr) && !"NULL".equals(feeCapStr)) {
      builder
          .gasPremium(Wei.of(Long.parseLong(gasPremiumStr)))
          .feeCap(Wei.of(Long.parseLong(feeCapStr)));
    } else {
      throw new RuntimeException("Invalid transaction format");
    }
    final Transaction transaction =
        builder.signAndBuild(
            SECP256K1.KeyPair.create(
                SECP256K1.PrivateKey.create(Bytes32.fromHexString(privateKey))));
    System.out.println("is eip-1559: " + transaction.isEIP1559Transaction());
    final String txRlp = toRlp(transaction);
    System.out.println(txRlp);
    return new RpcClient(rpcUrl).eth_sendRawTransaction(txRlp);
  }

  public static void main(final String[] args) throws Exception {
    System.out.println(
        decode("5-123-30000-0x627306090abaB3A6e1400e9345bC60c78a8BEf57-8910000000-NULL-NULL"));
  }

  private static String toRlp(final Transaction transaction) {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    transaction.writeTo(rlpOutput);
    return rlpOutput.encoded().toHexString();
  }
}

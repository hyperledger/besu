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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptRootResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptStatusResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.fees.TransactionGasBudgetCalculator;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class EthGetTransactionReceiptTest {

  private final TransactionReceipt stateReceipt =
      new TransactionReceipt(1, 12, Collections.emptyList(), Optional.empty());
  private final Hash stateRoot =
      Hash.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");
  private final TransactionReceipt rootReceipt =
      new TransactionReceipt(stateRoot, 12, Collections.emptyList(), Optional.empty());

  private final SECPSignature signature =
      SignatureAlgorithmFactory.getInstance()
          .createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 1);
  private final Address sender =
      Address.fromHexString("0x0000000000000000000000000000000000000003");
  private final Transaction transaction =
      Transaction.builder()
          .nonce(1)
          .gasPrice(Wei.of(12))
          .gasLimit(43)
          .payload(Bytes.EMPTY)
          .value(Wei.ZERO)
          .signature(signature)
          .sender(sender)
          .guessType()
          .build();

  private final Hash hash =
      Hash.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  private final Hash blockHash =
      Hash.fromHexString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

  private final TransactionReceiptWithMetadata stateReceiptWithMetaData =
      TransactionReceiptWithMetadata.create(stateReceipt, transaction, hash, 1, 2, blockHash, 4);
  private final TransactionReceiptWithMetadata rootReceiptWithMetaData =
      TransactionReceiptWithMetadata.create(rootReceipt, transaction, hash, 1, 2, blockHash, 4);

  private final ProtocolSpec rootTransactionTypeSpec =
      new ProtocolSpec(
          "root",
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          BlockHeader::getCoinbase,
          null,
          false,
          null,
          TransactionPriceCalculator.frontier(),
          Optional.empty(),
          TransactionGasBudgetCalculator.frontier(),
          null);
  private final ProtocolSpec statusTransactionTypeSpec =
      new ProtocolSpec(
          "status",
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          BlockHeader::getCoinbase,
          null,
          false,
          null,
          TransactionPriceCalculator.frontier(),
          Optional.empty(),
          TransactionGasBudgetCalculator.frontier(),
          null);

  @SuppressWarnings("unchecked")
  private final ProtocolSchedule protocolSchedule = mock(ProtocolSchedule.class);

  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);
  private final EthGetTransactionReceipt ethGetTransactionReceipt =
      new EthGetTransactionReceipt(blockchain);
  private final String receiptString =
      "0xcbef69eaf44af151aa66677ae4b8d8c343a09f667c873a3a6f4558fa4051fa5f";
  private final Hash receiptHash =
      Hash.fromHexString("cbef69eaf44af151aa66677ae4b8d8c343a09f667c873a3a6f4558fa4051fa5f");
  Object[] params = new Object[] {receiptString};
  private final JsonRpcRequestContext request =
      new JsonRpcRequestContext(new JsonRpcRequest("1", "eth_getTransactionReceipt", params));

  @Test
  public void shouldCreateAStatusTransactionReceiptWhenStatusTypeProtocol() {
    when(blockchain.headBlockNumber()).thenReturn(1L);
    when(blockchain.transactionReceiptByTransactionHash(receiptHash))
        .thenReturn(Optional.of(stateReceiptWithMetaData));
    when(protocolSchedule.getByBlockNumber(1)).thenReturn(statusTransactionTypeSpec);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) ethGetTransactionReceipt.response(request);
    final TransactionReceiptStatusResult result =
        (TransactionReceiptStatusResult) response.getResult();

    assertThat(result.getStatus()).isEqualTo("0x1");
  }

  @Test
  public void shouldCreateARootTransactionReceiptWhenRootTypeProtocol() {
    when(blockchain.headBlockNumber()).thenReturn(1L);
    when(blockchain.transactionReceiptByTransactionHash(receiptHash))
        .thenReturn(Optional.of(rootReceiptWithMetaData));
    when(protocolSchedule.getByBlockNumber(1)).thenReturn(rootTransactionTypeSpec);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) ethGetTransactionReceipt.response(request);
    final TransactionReceiptRootResult result = (TransactionReceiptRootResult) response.getResult();

    assertThat(result.getRoot()).isEqualTo(stateRoot.toString());
  }
}

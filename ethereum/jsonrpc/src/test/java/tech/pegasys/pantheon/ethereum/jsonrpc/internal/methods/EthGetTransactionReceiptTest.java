/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.TransactionReceiptWithMetadata;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptRootResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptStatusResult;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;

public class EthGetTransactionReceiptTest {

  private final TransactionReceipt stateReceipt =
      new TransactionReceipt(1, 12, Collections.emptyList(), Optional.empty());
  private final Hash stateRoot =
      Hash.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");
  private final TransactionReceipt rootReceipt =
      new TransactionReceipt(stateRoot, 12, Collections.emptyList(), Optional.empty());

  private final Signature signature = Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 1);
  private final Address sender =
      Address.fromHexString("0x0000000000000000000000000000000000000003");
  private final Transaction transaction =
      Transaction.builder()
          .nonce(1)
          .gasPrice(Wei.of(12))
          .gasLimit(43)
          .payload(BytesValue.EMPTY)
          .value(Wei.ZERO)
          .signature(signature)
          .sender(sender)
          .build();

  private final Hash hash =
      Hash.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  private final Hash blockHash =
      Hash.fromHexString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

  private final TransactionReceiptWithMetadata stateReceiptWithMetaData =
      TransactionReceiptWithMetadata.create(stateReceipt, transaction, hash, 1, 2, blockHash, 4);
  private final TransactionReceiptWithMetadata rootReceiptWithMetaData =
      TransactionReceiptWithMetadata.create(rootReceipt, transaction, hash, 1, 2, blockHash, 4);

  private final ProtocolSpec<Void> rootTransactionTypeSpec =
      new ProtocolSpec<>(
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
          BlockHeader::getCoinbase,
          null,
          false,
          null);
  private final ProtocolSpec<Void> statusTransactionTypeSpec =
      new ProtocolSpec<>(
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
          BlockHeader::getCoinbase,
          null,
          false,
          null);

  private final JsonRpcParameter parameters = new JsonRpcParameter();

  @SuppressWarnings("unchecked")
  private final ProtocolSchedule<Void> protocolSchedule = mock(ProtocolSchedule.class);

  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);
  private final EthGetTransactionReceipt ethGetTransactionReceipt =
      new EthGetTransactionReceipt(blockchain, parameters);
  private final String receiptString =
      "0xcbef69eaf44af151aa66677ae4b8d8c343a09f667c873a3a6f4558fa4051fa5f";
  private final Hash receiptHash =
      Hash.fromHexString("cbef69eaf44af151aa66677ae4b8d8c343a09f667c873a3a6f4558fa4051fa5f");
  Object[] params = new Object[] {receiptString};
  private final JsonRpcRequest request =
      new JsonRpcRequest("1", "eth_getTransactionReceipt", params);

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

    assertEquals("0x1", result.getStatus());
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

    assertEquals(stateRoot.toString(), result.getRoot());
  }
}

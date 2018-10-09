package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.crypto.SECP256K1.Signature;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.TransactionReceiptWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptRootResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptStatusResult;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.mainnet.TransactionReceiptType;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;

public class EthGetTransactionReceiptTest {

  private final TransactionReceipt stateReceipt =
      new TransactionReceipt(1, 12, Collections.emptyList());
  private final Hash stateRoot =
      Hash.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");
  private final TransactionReceipt rootReceipt =
      new TransactionReceipt(stateRoot, 12, Collections.emptyList());

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
          TransactionReceiptType.ROOT,
          BlockHeader::getCoinbase);
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
          TransactionReceiptType.STATUS,
          BlockHeader::getCoinbase);

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
      new JsonRpcRequest("1", "eth_getTransactionReceipt", params);;

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

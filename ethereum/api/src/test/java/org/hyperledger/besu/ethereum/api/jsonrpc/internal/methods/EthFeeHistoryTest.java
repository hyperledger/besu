package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EthFeeHistoryTest {
  private final Blockchain blockchain = mock(Blockchain.class);
  private final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);

  private EthFeeHistory method;

  @Before
  public void setUp() {
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    method = new EthFeeHistory(blockchainQueries);
  }

  @Test
  public void params() {
    // should fail because no required params given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext()))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because newestBlock not given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext(1)))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because blockCount not given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext("latest")))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should pass because both required params given
    method.response(feeHistoryRequestContext(1, "latest"));
    // should pass because both required params and optional param given
    method.response(feeHistoryRequestContext(1, "latest", new double[] {1, 20.4}));
  }

  @Test
  public void allFieldsPresentForLatestBlock() {
    final JsonRpcRequestContext requestContext =
        feeHistoryRequestContext(1, "latest", new double[] {100.0});
    when(blockchain.getChainHeadBlockNumber()).thenReturn(50L);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    final Block block = mock(Block.class);
    when(block.getHeader()).thenReturn(blockHeader);
    final BlockBody blockBody = mock(BlockBody.class);
    when(block.getBody()).thenReturn(blockBody);
    final Transaction transaction = mock(Transaction.class);
    when(transaction.getEffectivePriorityFeePerGas(any())).thenReturn(150L);
    when(blockBody.getTransactions()).thenReturn(List.of(transaction));
    when(blockchain.getBlockByNumber(50L)).thenReturn(Optional.of(block));
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(200L));
    final long HALF_GAS_LIMIT = 15_000_000L / 2;
    when(blockHeader.getGasUsed()).thenReturn(HALF_GAS_LIMIT);
    when(blockHeader.getGasLimit()).thenReturn(15_000_000L);
    when(blockchain.getBlockHeader(eq(50L))).thenReturn(Optional.of(blockHeader));
    final TransactionReceiptWithMetadata transactionReceiptWithMetadata =
        mock(TransactionReceiptWithMetadata.class);
    when(transactionReceiptWithMetadata.getGasUsed()).thenReturn(HALF_GAS_LIMIT);
    when(blockchainQueries.transactionReceiptByTransactionHash(any()))
        .thenReturn(Optional.of(transactionReceiptWithMetadata));
    assertThat(((JsonRpcSuccessResponse) method.response(requestContext)).getResult())
        .isEqualTo(
            new EthFeeHistory.FeeHistory(
                50, List.of(Optional.of(200L)), List.of(0.5), Optional.of(List.of(List.of(150L)))));
  }

  // test base fee always being of size > 1
  // test block count goes further than chain head
  // test names of field are alright
  // zeros for empty block
  // ascending order for rewards implies sorting
  // check invalid numerals in parsing

  private JsonRpcRequestContext feeHistoryRequestContext(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_feeHistory", params));
  }
}

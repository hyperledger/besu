package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.OptionalLong;

import org.junit.Test;

public class EthGetTransactionCountTest {

  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);
  private final PendingTransactions pendingTransactions = mock(PendingTransactions.class);

  private final EthGetTransactionCount ethGetTransactionCount =
      new EthGetTransactionCount(blockchain, pendingTransactions, parameters);
  private final String pendingTransactionString = "0x00000000000000000000000000000000000000AA";
  private final Object[] pendingParams = new Object[] {pendingTransactionString, "pending"};

  @Test
  public void shouldUsePendingTransactionsWhenToldTo() {
    when(pendingTransactions.getNextNonceForSender(Address.fromHexString(pendingTransactionString)))
        .thenReturn(OptionalLong.of(12));
    final JsonRpcRequest request =
        new JsonRpcRequest("1", "eth_getTransactionCount", pendingParams);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) ethGetTransactionCount.response(request);
    assertEquals("0xc", response.getResult());
  }

  @Test
  public void shouldUseLatestTransactionsWhenNoPendingTransactions() {
    final Address address = Address.fromHexString(pendingTransactionString);
    when(pendingTransactions.getNextNonceForSender(address)).thenReturn(OptionalLong.empty());
    when(blockchain.headBlockNumber()).thenReturn(1L);
    when(blockchain.getTransactionCount(address, 1L)).thenReturn(7L);
    final JsonRpcRequest request =
        new JsonRpcRequest("1", "eth_getTransactionCount", pendingParams);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) ethGetTransactionCount.response(request);
    assertEquals("0x7", response.getResult());
  }
}

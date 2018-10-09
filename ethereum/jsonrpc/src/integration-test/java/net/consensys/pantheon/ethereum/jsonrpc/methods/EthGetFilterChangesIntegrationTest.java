package net.consensys.pantheon.ethereum.jsonrpc.methods;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.core.TransactionPool.TransactionBatchAddedListener;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.FilterIdGenerator;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetFilterChanges;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.List;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetFilterChangesIntegrationTest {

  @Mock private TransactionBatchAddedListener batchAddedListener;
  private MutableBlockchain blockchain;
  private final String ETH_METHOD = "eth_getFilterChanges";
  private final String JSON_RPC_VERSION = "2.0";
  private TransactionPool transactionPool;
  private final PendingTransactions transactions = new PendingTransactions(MAX_TRANSACTIONS);
  private static final int MAX_TRANSACTIONS = 5;
  private static final KeyPair keyPair = KeyPair.generate();
  private final Transaction transaction = createTransaction(1);
  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private FilterManager filterManager;
  private EthGetFilterChanges method;

  @Before
  public void setUp() {
    final GenesisConfig<Void> genesisConfig = GenesisConfig.mainnet();
    final Block genesisBlock = genesisConfig.getBlock();
    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    blockchain =
        new DefaultMutableBlockchain(
            genesisBlock, keyValueStorage, MainnetBlockHashFunction::createHash);
    final WorldStateArchive worldStateArchive =
        new WorldStateArchive(new KeyValueStorageWorldStateStorage(keyValueStorage));
    final ProtocolContext<Void> protocolContext =
        new ProtocolContext<>(blockchain, worldStateArchive, null);
    transactionPool =
        new TransactionPool(
            transactions, genesisConfig.getProtocolSchedule(), protocolContext, batchAddedListener);
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(blockchain, worldStateArchive);
    filterManager = new FilterManager(blockchainQueries, transactionPool, new FilterIdGenerator());
    method = new EthGetFilterChanges(filterManager, parameters);
  }

  @Test
  public void shouldReturnErrorResponseIfFilterNotFound() {
    final JsonRpcRequest request = requestWithParams("0");

    final JsonRpcResponse expected = new JsonRpcErrorResponse(null, JsonRpcError.FILTER_NOT_FOUND);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByField(expected);
  }

  @Test
  public void shouldReturnEmptyArrayIfNoNewBlocks() {
    final String filterId = filterManager.installBlockFilter();

    assertThatFilterExists(filterId);

    final JsonRpcRequest request = requestWithParams(String.valueOf(filterId));
    final JsonRpcSuccessResponse expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByField(expected);

    filterManager.uninstallFilter(filterId);

    assertThatFilterDoesNotExist(filterId);
  }

  @Test
  public void shouldReturnEmptyArrayIfNoAddedPendingTransactions() {
    final String filterId = filterManager.installPendingTransactionFilter();

    assertThatFilterExists(filterId);

    final JsonRpcRequest request = requestWithParams(String.valueOf(filterId));

    // We haven't added any transactions, so the list of pending transactions should be empty.
    final JsonRpcSuccessResponse expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    filterManager.uninstallFilter(filterId);

    assertThatFilterDoesNotExist(filterId);
  }

  @Test
  public void shouldReturnHashesIfNewBlocks() {
    final String filterId = filterManager.installBlockFilter();

    assertThatFilterExists(filterId);

    final JsonRpcRequest request = requestWithParams(String.valueOf(filterId));

    // We haven't added any blocks, so the list of new blocks should be empty.
    JsonRpcSuccessResponse expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    JsonRpcResponse actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    final Block block = appendBlock(transaction);

    // We've added one block, so there should be one new hash.
    expected = new JsonRpcSuccessResponse(null, Lists.newArrayList(block.getHash().toString()));
    actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    // The queue should be flushed and return no results.
    expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    filterManager.uninstallFilter(filterId);

    assertThatFilterDoesNotExist(filterId);
  }

  @Test
  public void shouldReturnHashesIfNewPendingTransactions() {
    final String filterId = filterManager.installPendingTransactionFilter();

    assertThatFilterExists(filterId);

    final JsonRpcRequest request = requestWithParams(String.valueOf(filterId));

    // We haven't added any transactions, so the list of pending transactions should be empty.
    JsonRpcSuccessResponse expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    JsonRpcResponse actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    transactions.addRemoteTransaction(transaction);

    // We've added one transaction, so there should be one new hash.
    expected =
        new JsonRpcSuccessResponse(null, Lists.newArrayList(String.valueOf(transaction.hash())));
    actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    // The queue should be flushed and return no results.
    expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    filterManager.uninstallFilter(filterId);

    assertThatFilterDoesNotExist(filterId);
  }

  private void assertThatFilterExists(final String filterId) {
    assertThat(filterExists(filterId)).isTrue();
  }

  private void assertThatFilterDoesNotExist(final String filterId) {
    assertThat(filterExists(filterId)).isFalse();
  }

  /**
   * Determines whether a specified filter exists.
   *
   * @param filterId The filter ID to check.
   * @return A boolean - true if the filter exists, false if not.
   */
  private boolean filterExists(final String filterId) {
    final JsonRpcResponse response = method.response(requestWithParams(String.valueOf(filterId)));
    if (response instanceof JsonRpcSuccessResponse) {
      return true;
    } else {
      assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
      assertThat(((JsonRpcErrorResponse) response).getError())
          .isEqualTo(JsonRpcError.FILTER_NOT_FOUND);
      return false;
    }
  }

  private Block appendBlock(final Transaction... transactionsToAdd) {
    return appendBlock(UInt256.ONE, getHeaderForCurrentChainHead(), transactionsToAdd);
  }

  private BlockHeader getHeaderForCurrentChainHead() {
    return blockchain.getBlockHeader(blockchain.getChainHeadHash()).get();
  }

  private Block appendBlock(
      final UInt256 difficulty,
      final BlockHeader parentBlock,
      final Transaction... transactionsToAdd) {
    final List<Transaction> transactionList = asList(transactionsToAdd);
    final Block block =
        new Block(
            new BlockHeaderTestFixture()
                .difficulty(difficulty)
                .parentHash(parentBlock.getHash())
                .number(parentBlock.getNumber() + 1)
                .buildHeader(),
            new BlockBody(transactionList, emptyList()));
    final List<TransactionReceipt> transactionReceipts =
        transactionList
            .stream()
            .map(transaction -> new TransactionReceipt(1, 1, emptyList()))
            .collect(toList());
    blockchain.appendBlock(block, transactionReceipts);
    return block;
  }

  private Transaction createTransaction(final int transactionNumber) {
    return Transaction.builder()
        .gasLimit(100)
        .gasPrice(Wei.ZERO)
        .nonce(1)
        .payload(BytesValue.EMPTY)
        .to(Address.ID)
        .value(Wei.of(transactionNumber))
        .sender(Address.ID)
        .chainId(1)
        .signAndBuild(keyPair);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }
}

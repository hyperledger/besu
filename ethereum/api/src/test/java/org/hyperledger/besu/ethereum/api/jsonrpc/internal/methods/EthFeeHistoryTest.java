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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.FeeHistory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.ImmutableFeeHistory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.ImmutableFeeHistoryResult;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EthFeeHistoryTest {
  final BlockDataGenerator gen = new BlockDataGenerator();
  private MutableBlockchain blockchain;
  private EthFeeHistory method;
  private ProtocolSchedule protocolSchedule;
  private MiningCoordinator miningCoordinator;

  @BeforeEach
  public void setUp() {
    protocolSchedule = mock(ProtocolSchedule.class);
    final Block genesisBlock = gen.genesisBlock();
    blockchain = createInMemoryBlockchain(genesisBlock);
    gen.blockSequence(genesisBlock, 10)
        .forEach(block -> blockchain.appendBlock(block, gen.receipts(block)));
    miningCoordinator = mock(MergeCoordinator.class);
    when(miningCoordinator.getMinPriorityFeePerGas()).thenReturn(Wei.ONE);

    method =
        new EthFeeHistory(
            protocolSchedule,
            blockchain,
            miningCoordinator,
            ImmutableApiConfiguration.builder().build());
  }

  @Test
  public void params() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getForNextBlockHeader(any(), anyLong())).thenReturn(londonSpec);
    // should fail because no required params given
    assertThatThrownBy(this::feeHistoryRequest).isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because newestBlock not given
    assertThatThrownBy(() -> feeHistoryRequest("0x1")).isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because blockCount not given
    assertThatThrownBy(() -> feeHistoryRequest("latest"))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should pass because both required params given
    feeHistoryRequest("0x1", "latest");
    // should pass because both required params and optional param given
    feeHistoryRequest("0x1", "latest", new double[] {1, 20.4});
    // should pass because both required params and optional param given
    feeHistoryRequest("0x1", "latest", new double[] {1, 20.4});
  }

  @Test
  public void allFieldsPresentForLatestBlock() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getForNextBlockHeader(
            eq(blockchain.getChainHeadHeader()),
            eq(blockchain.getChainHeadHeader().getTimestamp())))
        .thenReturn(londonSpec);
    final Object latest =
        ((JsonRpcSuccessResponse) feeHistoryRequest("0x1", "latest", new double[] {100.0}))
            .getResult();
    assertThat(latest)
        .isEqualTo(
            FeeHistory.FeeHistoryResult.from(
                ImmutableFeeHistory.builder()
                    .oldestBlock(10)
                    .baseFeePerGas(List.of(Wei.of(25496L), Wei.of(28683L)))
                    .gasUsedRatio(List.of(0.9999999992132459))
                    .reward(List.of(List.of(Wei.of(1524763764L))))
                    .build()));
  }

  @Test
  public void shouldComputeRewardsCorrectly() {
    // Define the percentiles of rewards we want to compute
    List<Double> rewardPercentiles =
        Arrays.asList(0.0, 5.0, 10.0, 27.50, 31.0, 59.0, 60.0, 61.0, 100.0);

    Block block = mock(Block.class);
    Blockchain blockchain = mockBlockchainTransactionsWithPriorityFee(block);

    EthFeeHistory ethFeeHistory =
        new EthFeeHistory(
            null, blockchain, miningCoordinator, ImmutableApiConfiguration.builder().build());

    List<Wei> rewards = ethFeeHistory.computeRewards(rewardPercentiles, block);

    // Define the expected rewards for each percentile
    // The expected rewards match the fees of the transactions at each percentile in the
    // rewardPercentiles list
    List<Wei> expectedRewards = Stream.of(1, 1, 2, 4, 5, 6, 6, 7, 7).map(Wei::of).toList();

    // Check that the number of computed rewards is equal to the number of requested percentiles
    assertThat(rewards.size()).isEqualTo(rewardPercentiles.size());
    assertThat(expectedRewards).isEqualTo(rewards);
  }

  @Test
  public void shouldBoundRewardsCorrectly() {
    // This test checks that the rewards are correctly bounded by the lower and upper limits.
    // The lower and upper limits are defined by the lowerBoundPriorityFeeCoefficient and
    // upperBoundPriorityFeeCoefficient in the ApiConfiguration.
    // The lower limit is 2.0 (Wei.One * 200L / 100) and the upper limit is 5.0 (Wei.One * 500L /
    // 100).
    // The rewards are computed for a list of percentiles, and the expected bounded rewards are
    // defined for each percentile.
    // The test checks that the computed rewards match the expected bounded rewards.

    List<Double> rewardPercentiles =
        Arrays.asList(0.0, 5.0, 10.0, 27.50, 31.0, 59.0, 60.0, 61.0, 100.0);

    Block block = mock(Block.class);
    Blockchain blockchain = mockBlockchainTransactionsWithPriorityFee(block);

    ApiConfiguration apiConfiguration =
        ImmutableApiConfiguration.builder()
            .isGasAndPriorityFeeLimitingEnabled(true)
            .lowerBoundGasAndPriorityFeeCoefficient(200L) // Min reward = Wei.One * 200L / 100 = 2.0
            .upperBoundGasAndPriorityFeeCoefficient(500L)
            .build(); // Max reward = Wei.One * 500L / 100 = 5.0

    EthFeeHistory ethFeeHistory =
        new EthFeeHistory(null, blockchain, miningCoordinator, apiConfiguration);

    List<Wei> rewards = ethFeeHistory.computeRewards(rewardPercentiles, block);

    // Define the expected bounded rewards for each percentile
    List<Wei> expectedBoundedRewards = Stream.of(2, 2, 2, 4, 5, 5, 5, 5, 5).map(Wei::of).toList();
    assertThat(expectedBoundedRewards).isEqualTo(rewards);
  }

  private Blockchain mockBlockchainTransactionsWithPriorityFee(final Block block) {
    final Blockchain blockchain = mock(Blockchain.class);

    // Define a list of gas used and fee pairs. Each pair represents a transaction in the block.
    // The first number is the gas used by the transaction, and the second number the fee.
    // The comments indicate the cumulative gas used up as a percentage of the total gas limit.
    List<Object[]> gasUsedAndFee = new ArrayList<>();
    gasUsedAndFee.add(new Object[] {100, 1L}); // 5%
    gasUsedAndFee.add(new Object[] {150, 2L}); // 12.5%
    gasUsedAndFee.add(new Object[] {200, 3L}); // 22.5%
    gasUsedAndFee.add(new Object[] {100, 4L}); // 27.5%
    gasUsedAndFee.add(new Object[] {200, 5L}); // 37.5%
    gasUsedAndFee.add(new Object[] {450, 6L}); // 60.0%
    gasUsedAndFee.add(new Object[] {800, 7L}); // 100.0%
    Collections.shuffle(gasUsedAndFee);

    when(block.getHash()).thenReturn(Hash.wrap(Bytes32.wrap(Bytes.random(32))));
    BlockBody body = mock(BlockBody.class);
    BlockHeader blockHeader = mock(BlockHeader.class);
    when(block.getHeader()).thenReturn(blockHeader);
    when(block.getBody()).thenReturn(body);
    long cumulativeGasUsed = 0;
    List<Transaction> transactions = new ArrayList<>();
    List<TransactionReceipt> receipts = new ArrayList<>();
    for (Object[] objects : gasUsedAndFee) {
      Transaction transaction = mock(Transaction.class);
      when(transaction.getEffectivePriorityFeePerGas(any())).thenReturn(Wei.of((Long) objects[1]));
      cumulativeGasUsed += (int) objects[0];
      transactions.add(transaction);
      TransactionReceipt receipt = mock(TransactionReceipt.class);
      when(receipt.getCumulativeGasUsed()).thenReturn(cumulativeGasUsed);
      receipts.add(receipt);
    }
    when(blockHeader.getGasUsed()).thenReturn(cumulativeGasUsed);
    when(blockchain.getTxReceipts(any())).thenReturn(Optional.of(receipts));
    when(body.getTransactions()).thenReturn(transactions);
    return blockchain;
  }

  @Test
  public void cantGetBlockHigherThanChainHead() {
    assertThat(
            ((JsonRpcErrorResponse) feeHistoryRequest("0x2", "11", new double[] {100.0}))
                .getErrorType())
        .isEqualTo(RpcErrorType.INVALID_PARAMS);
  }

  @Test
  public void blockCountBounds() {
    assertThat(
            ((JsonRpcErrorResponse) feeHistoryRequest("0x0", "latest", new double[] {100.0}))
                .getErrorType())
        .isEqualTo(RpcErrorType.INVALID_PARAMS);
    assertThat(
            ((JsonRpcErrorResponse) feeHistoryRequest("0x401", "latest", new double[] {100.0}))
                .getErrorType())
        .isEqualTo(RpcErrorType.INVALID_PARAMS);
  }

  @Test
  public void doesntGoPastChainHeadWithHighBlockCount() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getForNextBlockHeader(
            eq(blockchain.getChainHeadHeader()),
            eq(blockchain.getChainHeadHeader().getTimestamp())))
        .thenReturn(londonSpec);
    final FeeHistory.FeeHistoryResult result =
        (ImmutableFeeHistoryResult)
            ((JsonRpcSuccessResponse) feeHistoryRequest("0x14", "latest")).getResult();
    assertThat(Long.decode(result.getOldestBlock())).isEqualTo(0);
    assertThat(result.getBaseFeePerGas()).hasSize(12);
    assertThat(result.getGasUsedRatio()).hasSize(11);
    assertThat(result.getReward()).isNull();
  }

  @Test
  public void feeValuesAreInTheBlockCountAndHighestBlock() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getForNextBlockHeader(
            eq(blockchain.getChainHeadHeader()),
            eq(blockchain.getChainHeadHeader().getTimestamp())))
        .thenReturn(londonSpec);
    double[] percentile = new double[] {100.0};

    final Object ninth =
        ((JsonRpcSuccessResponse) feeHistoryRequest(2, "9", percentile)).getResult();
    assertFeeMetadataSize(ninth, 2);

    final Object eighth =
        ((JsonRpcSuccessResponse) feeHistoryRequest(4, "8", percentile)).getResult();
    assertFeeMetadataSize(eighth, 4);
  }

  @Test
  public void feeValuesDontGoPastHighestBlock() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getForNextBlockHeader(
            eq(blockchain.getChainHeadHeader()),
            eq(blockchain.getChainHeadHeader().getTimestamp())))
        .thenReturn(londonSpec);
    double[] percentile = new double[] {100.0};

    final Object second =
        ((JsonRpcSuccessResponse) feeHistoryRequest(4, "2", percentile)).getResult();
    assertFeeMetadataSize(second, 3);

    final Object third =
        ((JsonRpcSuccessResponse) feeHistoryRequest(11, "3", percentile)).getResult();
    assertFeeMetadataSize(third, 4);
  }

  @Test
  public void correctlyHandlesForkBlock() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(11));
    when(protocolSchedule.getForNextBlockHeader(
            eq(blockchain.getChainHeadHeader()),
            eq(blockchain.getChainHeadHeader().getTimestamp())))
        .thenReturn(londonSpec);
    final FeeHistory.FeeHistoryResult result =
        (FeeHistory.FeeHistoryResult)
            ((JsonRpcSuccessResponse) feeHistoryRequest("0x1", "latest")).getResult();
    assertThat(Wei.fromHexString(result.getBaseFeePerGas().get(1)))
        .isEqualTo(FeeMarket.london(11).getInitialBasefee());
  }

  @Test
  public void allZeroPercentilesForZeroBlock() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    final BlockDataGenerator.BlockOptions blockOptions = BlockDataGenerator.BlockOptions.create();
    blockOptions.hasTransactions(false);
    blockOptions.setParentHash(blockchain.getChainHeadHash());
    blockOptions.setBlockNumber(11);
    final Block emptyBlock = gen.block(blockOptions);
    blockchain.appendBlock(emptyBlock, gen.receipts(emptyBlock));
    when(protocolSchedule.getForNextBlockHeader(
            eq(blockchain.getChainHeadHeader()),
            eq(blockchain.getChainHeadHeader().getTimestamp())))
        .thenReturn(londonSpec);
    final FeeHistory.FeeHistoryResult result =
        (FeeHistory.FeeHistoryResult)
            ((JsonRpcSuccessResponse) feeHistoryRequest("0x1", "latest", new double[] {100.0}))
                .getResult();
    assertThat(result.getReward()).isEqualTo(List.of(List.of("0x0")));
  }

  private void assertFeeMetadataSize(final Object feeObject, final int blockCount) {
    assertThat(((ImmutableFeeHistoryResult) feeObject).getBaseFeePerGas().size())
        .isEqualTo(blockCount + 1);
    assertThat(((ImmutableFeeHistoryResult) feeObject).getReward().size()).isEqualTo(blockCount);
    assertThat(((ImmutableFeeHistoryResult) feeObject).getGasUsedRatio().size())
        .isEqualTo(blockCount);
  }

  private JsonRpcResponse feeHistoryRequest(final Object... params) {
    return method.response(
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_feeHistory", params)));
  }
}

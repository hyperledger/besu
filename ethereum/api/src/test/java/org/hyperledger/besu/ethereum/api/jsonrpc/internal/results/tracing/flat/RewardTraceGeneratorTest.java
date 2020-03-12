package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ClassicBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RewardTraceGeneratorTest {

  private final BlockDataGenerator gen = new BlockDataGenerator();

  @Mock private ProtocolSchedule<Void> protocolSchedule;
  @Mock private ProtocolSpec<Void> protocolSpec;
  @Mock private MiningBeneficiaryCalculator miningBeneficiaryCalculator;
  @Mock private TransactionProcessor transactionProcessor;

  private final Address ommerBeneficiary =
      Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"));
  private final Address blockBeneficiary =
      Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d88"));
  private final Wei blockReward = Wei.of(10000);
  private final BlockHeader ommerHeader = gen.header(0x09);

  private Block block;

  @Before
  public void setUp() {
    final BlockBody blockBody = new BlockBody(Collections.emptyList(), List.of(ommerHeader));
    final BlockHeader blockHeader =
        gen.header(0x0A, blockBody, new BlockDataGenerator.BlockOptions());
    block = new Block(blockHeader, blockBody);
    when(protocolSchedule.getByBlockNumber(block.getHeader().getNumber())).thenReturn(protocolSpec);
    when(protocolSpec.getBlockReward()).thenReturn(blockReward);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(miningBeneficiaryCalculator);
    when(miningBeneficiaryCalculator.calculateBeneficiary(block.getHeader()))
        .thenReturn(blockBeneficiary);
    when(miningBeneficiaryCalculator.calculateBeneficiary(ommerHeader))
        .thenReturn(ommerBeneficiary);
  }

  @Test
  public void assertThatTraceGeneratorReturnValidRewardsForMainnetBlockProcessor() {
    final MainnetBlockProcessor.TransactionReceiptFactory transactionReceiptFactory =
        mock(MainnetBlockProcessor.TransactionReceiptFactory.class);
    final MainnetBlockProcessor blockProcessor =
        new MainnetBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            blockReward,
            BlockHeader::getCoinbase,
            true);
    when(protocolSpec.getBlockProcessor()).thenReturn(blockProcessor);

    final Stream<Trace> traceStream =
        RewardTraceGenerator.generateFromBlock(protocolSchedule, block);

    final Action.Builder actionBlockReward =
        Action.builder()
            .rewardType("block")
            .author(blockBeneficiary.toHexString())
            .value(
                blockProcessor
                    .getCoinbaseReward(blockReward, block.getHeader().getNumber(), 1)
                    .toShortHexString());
    final Trace blocReward =
        new RewardTrace.Builder()
            .blockHash(block.getHash().toHexString())
            .blockNumber(block.getHeader().getNumber())
            .actionBuilder(actionBlockReward)
            .type("reward")
            .build();

    // calculate reward with MainnetBlockProcessor
    final Action.Builder actionOmmerReward =
        Action.builder()
            .rewardType("uncle")
            .author(ommerBeneficiary.toHexString())
            .value(
                blockProcessor
                    .getOmmerReward(
                        blockReward, block.getHeader().getNumber(), ommerHeader.getNumber())
                    .toShortHexString());
    final Trace ommerReward =
        new RewardTrace.Builder()
            .blockHash(block.getHash().toHexString())
            .blockNumber(block.getHeader().getNumber())
            .actionBuilder(actionOmmerReward)
            .type("reward")
            .build();

    List<Trace> traces = traceStream.collect(Collectors.toList());

    // check block reward
    assertThat(traces.get(0)).usingRecursiveComparison().isEqualTo(blocReward);
    // check ommer reward
    assertThat(traces.get(1)).usingRecursiveComparison().isEqualTo(ommerReward);
  }

  @Test
  public void assertThatTraceGeneratorReturnValidRewardsForClassicBlockProcessor() {
    final ClassicBlockProcessor.TransactionReceiptFactory transactionReceiptFactory =
        mock(ClassicBlockProcessor.TransactionReceiptFactory.class);
    final ClassicBlockProcessor blockProcessor =
        new ClassicBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            blockReward,
            BlockHeader::getCoinbase,
            true);
    when(protocolSpec.getBlockProcessor()).thenReturn(blockProcessor);

    final Stream<Trace> traceStream =
        RewardTraceGenerator.generateFromBlock(protocolSchedule, block);

    final Action.Builder actionBlockReward =
        Action.builder()
            .rewardType("block")
            .author(blockBeneficiary.toHexString())
            .value(
                blockProcessor
                    .getCoinbaseReward(blockReward, block.getHeader().getNumber(), 1)
                    .toShortHexString());
    final Trace blocReward =
        new RewardTrace.Builder()
            .blockHash(block.getHash().toHexString())
            .blockNumber(block.getHeader().getNumber())
            .actionBuilder(actionBlockReward)
            .type("reward")
            .build();

    // calculate reward with ClassicBlockProcessor
    final Action.Builder actionOmmerReward =
        Action.builder()
            .rewardType("uncle")
            .author(ommerBeneficiary.toHexString())
            .value(
                blockProcessor
                    .getOmmerReward(
                        blockReward, block.getHeader().getNumber(), ommerHeader.getNumber())
                    .toShortHexString());
    final Trace ommerReward =
        new RewardTrace.Builder()
            .blockHash(block.getHash().toHexString())
            .blockNumber(block.getHeader().getNumber())
            .actionBuilder(actionOmmerReward)
            .type("reward")
            .build();

    List<Trace> traces = traceStream.collect(Collectors.toList());

    // check block reward
    assertThat(traces.get(0)).usingRecursiveComparison().isEqualTo(blocReward);
    // check ommer reward
    assertThat(traces.get(1)).usingRecursiveComparison().isEqualTo(ommerReward);
  }
}

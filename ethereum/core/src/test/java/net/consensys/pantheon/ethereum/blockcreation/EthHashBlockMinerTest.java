package net.consensys.pantheon.ethereum.blockcreation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator.MinedBlockObserver;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.BlockImporter;
import net.consensys.pantheon.ethereum.mainnet.EthHashBlockCreator;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.util.Subscribers;

import com.google.common.collect.Lists;
import org.junit.Test;

public class EthHashBlockMinerTest {

  @Test
  @SuppressWarnings("unchecked")
  public void blockCreatedIsAddedToBlockChain() throws InterruptedException {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    final Block blockToCreate =
        new Block(
            headerBuilder.buildHeader(), new BlockBody(Lists.newArrayList(), Lists.newArrayList()));

    final ProtocolContext<Void> protocolContext = new ProtocolContext<>(null, null, null);

    final EthHashBlockCreator blockCreator = mock(EthHashBlockCreator.class);
    when(blockCreator.createBlock(anyLong())).thenReturn(blockToCreate);

    final BlockImporter<Void> blockImporter = mock(BlockImporter.class);
    final ProtocolSpec<Void> protocolSpec = mock(ProtocolSpec.class);

    final ProtocolSchedule<Void> protocolSchedule = singleSpecSchedule(protocolSpec);

    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);
    when(blockImporter.importBlock(any(), any(), any())).thenReturn(true);

    final MinedBlockObserver observer = mock(MinedBlockObserver.class);
    final DefaultBlockScheduler scheduler = mock(DefaultBlockScheduler.class);
    when(scheduler.waitUntilNextBlockCanBeMined(any())).thenReturn(5L);
    final EthHashBlockMiner miner =
        new EthHashBlockMiner(
            blockCreator,
            protocolSchedule,
            protocolContext,
            subscribersContaining(observer),
            scheduler,
            headerBuilder.buildHeader()); // parent header is arbitrary for the test.

    miner.run();
    verify(blockImporter).importBlock(protocolContext, blockToCreate, HeaderValidationMode.FULL);
    verify(observer, times(1)).blockMined(blockToCreate);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void failureToImportDoesNotTriggerObservers() throws InterruptedException {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    final Block blockToCreate =
        new Block(
            headerBuilder.buildHeader(), new BlockBody(Lists.newArrayList(), Lists.newArrayList()));

    final ProtocolContext<Void> protocolContext = new ProtocolContext<>(null, null, null);

    final EthHashBlockCreator blockCreator = mock(EthHashBlockCreator.class);
    when(blockCreator.createBlock(anyLong())).thenReturn(blockToCreate);

    final BlockImporter<Void> blockImporter = mock(BlockImporter.class);
    final ProtocolSpec<Void> protocolSpec = mock(ProtocolSpec.class);
    final ProtocolSchedule<Void> protocolSchedule = singleSpecSchedule(protocolSpec);

    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);
    when(blockImporter.importBlock(any(), any(), any())).thenReturn(false, false, true);

    final MinedBlockObserver observer = mock(MinedBlockObserver.class);
    final DefaultBlockScheduler scheduler = mock(DefaultBlockScheduler.class);
    when(scheduler.waitUntilNextBlockCanBeMined(any())).thenReturn(5L);
    final EthHashBlockMiner miner =
        new EthHashBlockMiner(
            blockCreator,
            protocolSchedule,
            protocolContext,
            subscribersContaining(observer),
            scheduler,
            headerBuilder.buildHeader()); // parent header is arbitrary for the test.

    miner.run();
    verify(blockImporter, times(3))
        .importBlock(protocolContext, blockToCreate, HeaderValidationMode.FULL);
    verify(observer, times(1)).blockMined(blockToCreate);
  }

  private static Subscribers<MinedBlockObserver> subscribersContaining(
      final MinedBlockObserver... observers) {
    final Subscribers<MinedBlockObserver> result = new Subscribers<>();
    for (final MinedBlockObserver obs : observers) {
      result.subscribe(obs);
    }
    return result;
  }

  private ProtocolSchedule<Void> singleSpecSchedule(final ProtocolSpec<Void> protocolSpec) {
    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(0, protocolSpec);
    return protocolSchedule;
  }
}

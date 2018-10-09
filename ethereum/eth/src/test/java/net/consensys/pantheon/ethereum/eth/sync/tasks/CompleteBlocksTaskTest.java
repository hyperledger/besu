package net.consensys.pantheon.ethereum.eth.sync.tasks;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.eth.manager.EthTask;
import net.consensys.pantheon.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskWithResultsTest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CompleteBlocksTaskTest extends RetryingMessageTaskWithResultsTest<List<Block>> {

  @Override
  protected List<Block> generateDataToBeRequested() {
    // Setup data to be requested and expected response
    final List<Block> blocks = new ArrayList<>();
    for (long i = 0; i < 3; i++) {
      final BlockHeader header = blockchain.getBlockHeader(10 + i).get();
      final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
      blocks.add(new Block(header, body));
    }
    return blocks;
  }

  @Override
  protected EthTask<List<Block>> createTask(final List<Block> requestedData) {
    final List<BlockHeader> headersToComplete =
        requestedData.stream().map(Block::getHeader).collect(Collectors.toList());
    return CompleteBlocksTask.forHeaders(protocolSchedule, ethContext, headersToComplete);
  }
}

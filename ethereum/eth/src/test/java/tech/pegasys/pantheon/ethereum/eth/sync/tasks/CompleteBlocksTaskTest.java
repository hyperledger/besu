package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskWithResultsTest;

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

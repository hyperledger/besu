package net.consensys.pantheon.ethereum.eth.sync.tasks;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.eth.manager.EthTask;
import net.consensys.pantheon.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskWithResultsTest;

import java.util.ArrayList;
import java.util.List;

public class DownloadHeaderSequenceTaskTest
    extends RetryingMessageTaskWithResultsTest<List<BlockHeader>> {

  @Override
  protected List<BlockHeader> generateDataToBeRequested() {
    final List<BlockHeader> requestedHeaders = new ArrayList<>();
    for (long i = 0; i < 3; i++) {
      final long blockNumber = 10 + i;
      final BlockHeader header = blockchain.getBlockHeader(blockNumber).get();
      requestedHeaders.add(header);
    }
    return requestedHeaders;
  }

  @Override
  protected EthTask<List<BlockHeader>> createTask(final List<BlockHeader> requestedData) {
    final BlockHeader lastHeader = requestedData.get(requestedData.size() - 1);
    final BlockHeader referenceHeader = blockchain.getBlockHeader(lastHeader.getNumber() + 1).get();
    return DownloadHeaderSequenceTask.endingAtHeader(
        protocolSchedule, protocolContext, ethContext, referenceHeader, requestedData.size());
  }
}

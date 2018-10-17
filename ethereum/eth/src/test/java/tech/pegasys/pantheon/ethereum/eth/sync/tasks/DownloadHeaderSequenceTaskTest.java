/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskTest;

import java.util.ArrayList;
import java.util.List;

public class DownloadHeaderSequenceTaskTest extends RetryingMessageTaskTest<List<BlockHeader>> {

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
        protocolSchedule,
        protocolContext,
        ethContext,
        referenceHeader,
        requestedData.size(),
        maxRetries);
  }
}

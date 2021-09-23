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
package org.hyperledger.besu.ethereum.api.query;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.PrivateWorldStateReader;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class PrivacyQueries {

  private final BlockchainQueries blockchainQueries;
  private final PrivateWorldStateReader privateWorldStateReader;

  public PrivacyQueries(
      final BlockchainQueries blockchainQueries,
      final PrivateWorldStateReader privateWorldStateReader) {
    this.blockchainQueries = blockchainQueries;
    this.privateWorldStateReader = privateWorldStateReader;
  }

  public List<LogWithMetadata> matchingLogs(
      final String privacyGroupId,
      final long fromBlockNumber,
      final long toBlockNumber,
      final LogsQuery query) {

    return LongStream.rangeClosed(fromBlockNumber, toBlockNumber)
        .mapToObj(blockchainQueries::getBlockHashByNumber)
        .takeWhile(Optional::isPresent)
        .map(Optional::get)
        .map(hash -> matchingLogs(privacyGroupId, hash, query))
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  public List<LogWithMetadata> matchingLogs(
      final String privacyGroupId, final Hash blockHash, final LogsQuery query) {

    final Optional<BlockHeader> blockHeader = blockchainQueries.getBlockHeaderByHash(blockHash);
    if (blockHeader.isEmpty()) {
      return Collections.emptyList();
    }

    final List<PrivateTransactionMetadata> privateTransactionMetadataList =
        privateWorldStateReader.getPrivateTransactionMetadataList(privacyGroupId, blockHash);

    final List<Hash> pmtHashList =
        privateTransactionMetadataList.stream()
            .map(PrivateTransactionMetadata::getPrivateMarkerTransactionHash)
            .collect(Collectors.toList());

    final List<PrivateTransactionReceipt> privateTransactionReceiptList =
        pmtHashList.stream()
            .map(
                pmtHash -> privateWorldStateReader.getPrivateTransactionReceipt(blockHash, pmtHash))
            .flatMap(Optional::stream)
            .collect(Collectors.toList());

    final long blockNumber = blockHeader.get().getNumber();
    final boolean removed = !blockchainQueries.blockIsOnCanonicalChain(blockHash);

    return IntStream.range(0, privateTransactionReceiptList.size())
        .mapToObj(
            i ->
                LogWithMetadata.generate(
                    privateTransactionReceiptList.get(i),
                    blockNumber,
                    blockHash,
                    privateTransactionMetadataList.get(i).getPrivateMarkerTransactionHash(),
                    findPMTIndex(pmtHashList.get(i)),
                    removed))
        .flatMap(Collection::stream)
        .filter(query::matches)
        .collect(Collectors.toList());
  }

  private int findPMTIndex(final Hash pmtHash) {
    return blockchainQueries
        .transactionLocationByHash(pmtHash)
        .map(TransactionLocation::getTransactionIndex)
        .orElseThrow(() -> new IllegalStateException("Can't find PMT index with hash " + pmtHash));
  }
}

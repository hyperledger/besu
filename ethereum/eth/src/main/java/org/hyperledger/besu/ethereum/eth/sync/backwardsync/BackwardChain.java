/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.warnLambda;
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;

public class BackwardChain {
  private static final Logger LOG = getLogger(BackwardChain.class);

  private final List<Hash> ancestors = new ArrayList<>();
  private final List<Hash> successors = new ArrayList<>();

  protected final GenericKeyValueStorageFacade<Hash, BlockHeader> headers;
  protected final GenericKeyValueStorageFacade<Hash, Block> blocks;

  public BackwardChain(
      final StorageProvider provider,
      final BlockHeaderFunctions blockHeaderFunctions,
      final Block pivot) {
    this(
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            BlocksHeadersConvertor.of(blockHeaderFunctions),
            provider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.BACKWARD_SYNC_HEADERS)),
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            BlocksConvertor.of(blockHeaderFunctions),
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BACKWARD_SYNC_BLOCKS)),
        pivot);
  }

  public BackwardChain(
      final GenericKeyValueStorageFacade<Hash, BlockHeader> headersStorage,
      final GenericKeyValueStorageFacade<Hash, Block> blocksStorage,
      final Block pivot) {

    this.headers = headersStorage;
    this.blocks = blocksStorage;
    headersStorage.put(pivot.getHeader().getHash(), pivot.getHeader());
    blocksStorage.put(pivot.getHash(), pivot);
    ancestors.add(pivot.getHeader().getHash());
    successors.add(pivot.getHash());
  }

  public Optional<BlockHeader> getFirstAncestorHeader() {
    if (ancestors.isEmpty()) {
      return Optional.empty();
    }
    return headers.get(ancestors.get(ancestors.size() - 1));
  }

  public List<BlockHeader> getFirstNAncestorHeaders(final int size) {
    List<Hash> resultList = new ArrayList<>(size);
    for (int i = Math.min(size, ancestors.size()); i > 0; --i) {
      resultList.add(ancestors.get(ancestors.size() - i));
    }
    return resultList.stream()
        .map(h -> this.headers.get(h).orElseThrow())
        .collect(Collectors.toList());
  }

  public List<BlockHeader> getAllAncestors() {
    return getFirstNAncestorHeaders(ancestors.size());
  }

  public void prependAncestorsHeader(final BlockHeader blockHeader) {
    BlockHeader firstHeader =
        getFirstAncestorHeader()
            .orElseThrow(
                () ->
                    new BackwardSyncException(
                        "Cannot save more headers during forward sync", true));
    if (firstHeader.getNumber() != blockHeader.getNumber() + 1) {
      throw new BackwardSyncException(
          "Wrong height of header "
              + blockHeader.getHash().toHexString()
              + " is "
              + blockHeader.getNumber()
              + " when we were expecting "
              + (firstHeader.getNumber() - 1));
    }
    if (!firstHeader.getParentHash().equals(blockHeader.getHash())) {
      throw new BackwardSyncException(
          "Hash of header does not match our expectations, was "
              + blockHeader.getHash().toHexString()
              + " when we expected "
              + firstHeader.getParentHash().toHexString());
    }
    headers.put(blockHeader.getHash(), blockHeader);
    ancestors.add(blockHeader.getHash());
    debugLambda(
        LOG,
        "Added header {} on height {} to backward chain led by pivot {} on height {}",
        () -> blockHeader.getHash().toHexString(),
        blockHeader::getNumber,
        () -> firstHeader.getHash().toHexString(),
        firstHeader::getNumber);
  }

  public void prependChain(final BackwardChain historicalBackwardChain) {
    BlockHeader firstHeader =
        getFirstAncestorHeader()
            .orElseThrow(
                () -> new BackwardSyncException("Cannot merge when syncing forward...", true));
    Optional<BlockHeader> historicalHeader =
        historicalBackwardChain.getHeaderOnHeight(firstHeader.getNumber() - 1);
    if (historicalHeader.isEmpty()) {
      return;
    }
    if (firstHeader.getParentHash().equals(historicalHeader.orElseThrow().getHash())) {
      for (Block successor : historicalBackwardChain.getSuccessors()) {
        if (successor.getHeader().getNumber() > getPivot().getHeader().getNumber()) {
          this.successors.add(successor.getHeader().getHash());
        }
      }
      Collections.reverse(historicalBackwardChain.getSuccessors());
      for (Block successor : historicalBackwardChain.getSuccessors()) {
        if (successor.getHeader().getNumber()
            < getFirstAncestorHeader().orElseThrow().getNumber()) {
          this.ancestors.add(successor.getHeader().getHash());
        }
      }
      for (BlockHeader ancestor : historicalBackwardChain.getAllAncestors()) {
        if (ancestor.getNumber() < getFirstAncestorHeader().orElseThrow().getNumber()) {
          this.ancestors.add(ancestor.getHash());
        }
      }
      debugLambda(
          LOG,
          "Merged backward chain. New chain starts at height {} and ends at height {}",
          () -> getPivot().getHeader().getNumber(),
          () -> getFirstAncestorHeader().orElseThrow().getNumber());
    } else {
      warnLambda(
          LOG,
          "Cannot merge previous historical run because headers on height {} ({}) of {} and {} are not equal. Ignoring previous run. Did someone lie to us?",
          () -> firstHeader.getNumber() - 1,
          () -> historicalHeader.orElseThrow().getNumber(),
          () -> firstHeader.getParentHash().toHexString(),
          () -> historicalHeader.orElseThrow().getHash().toHexString());
    }
  }

  public Block getPivot() {
    return blocks.get(successors.get(successors.size() - 1)).orElseThrow();
  }

  public void dropFirstHeader() {
    headers.drop(ancestors.get(ancestors.size() - 1));
    ancestors.remove(ancestors.size() - 1);
  }

  public void appendExpectedBlock(final Block newPivot) {
    successors.add(newPivot.getHash());
    headers.put(newPivot.getHash(), newPivot.getHeader());
    blocks.put(newPivot.getHash(), newPivot);
  }

  public List<Block> getSuccessors() {
    return successors.stream()
        .map(hash -> blocks.get(hash).orElseThrow())
        .collect(Collectors.toList());
  }

  public boolean isTrusted(final Hash hash) {
    return blocks.get(hash).isPresent();
  }

  public Block getTrustedBlock(final Hash hash) {
    return blocks.get(hash).orElseThrow();
  }

  public void clear() {
    ancestors.clear();
    successors.clear();
    blocks.clear();
    headers.clear();
  }

  public void commit() {}

  public Optional<BlockHeader> getHeaderOnHeight(final long height) {
    if (ancestors.isEmpty()) {
      return Optional.empty();
    }
    final long firstAncestor = headers.get(ancestors.get(0)).orElseThrow().getNumber();
    if (firstAncestor >= height) {
      if (firstAncestor - height < ancestors.size()) {
        final Optional<BlockHeader> blockHeader =
            headers.get(ancestors.get((int) (firstAncestor - height)));
        blockHeader.ifPresent(
            blockHeader1 ->
                LOG.debug(
                    "First: {} Height: {}, result: {}",
                    firstAncestor,
                    height,
                    blockHeader.orElseThrow().getNumber()));
        return blockHeader;
      } else {
        return Optional.empty();
      }
    } else {
      if (successors.isEmpty()) {
        return Optional.empty();
      }
      final long firstSuccessor = headers.get(successors.get(0)).orElseThrow().getNumber();
      if (height - firstSuccessor < successors.size()) {
        LOG.debug(
            "First: {} Height: {}, result: {}",
            firstSuccessor,
            height,
            headers.get(successors.get((int) (height - firstSuccessor))).orElseThrow().getNumber());

        return headers.get(successors.get((int) (height - firstSuccessor)));
      } else {
        return Optional.empty();
      }
    }
  }
}

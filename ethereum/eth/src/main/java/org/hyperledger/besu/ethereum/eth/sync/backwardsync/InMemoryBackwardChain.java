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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryBackwardChain implements BackwardSyncStorage {
  private static final Logger LOG = LoggerFactory.getLogger(InMemoryBackwardChain.class);

  private final List<BlockHeader> ancestors = new ArrayList<>();
  private final List<Block> successors = new ArrayList<>();
  private final Map<Hash, Block> trustedBlocks = new HashMap<>();

  public InMemoryBackwardChain(final Block pivot) {
    ancestors.add(pivot.getHeader());
    successors.add(pivot);
  }

  @Override
  public Optional<BlockHeader> getFirstAncestorHeader() {
    if (ancestors.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(ancestors.get(ancestors.size() - 1));
  }

  @Override
  public List<BlockHeader> getFirstNAncestorHeaders(final int size) {
    List<BlockHeader> headers = new ArrayList<>(size);
    for (int i = 0; i < ancestors.size() && i < size; ++i) {
      headers.add(ancestors.get(ancestors.size() - 1 - i));
    }
    return headers;
  }

  @Override
  public List<BlockHeader> getAllAncestors() {
    return ancestors;
  }

  public Map<Hash, Block> getAllTrustedBlocks() {
    return trustedBlocks;
  }

  @Override
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
              + blockHeader.getHash().toString().substring(0, 20)
              + " is "
              + blockHeader.getNumber()
              + " when we were expecting "
              + (firstHeader.getNumber() - 1));
    }
    if (!firstHeader.getParentHash().equals(blockHeader.getHash())) {
      throw new BackwardSyncException(
          "Hash of header does not match our expectations, was "
              + blockHeader.getHash().toString().substring(0, 20)
              + " when we expected "
              + firstHeader.getParentHash().toString().substring(0, 20));
    }
    ancestors.add(blockHeader);
    debugLambda(
        LOG,
        "Added header {} on height {} to backward chain led by pivot {} on height {}",
        () -> blockHeader.getHash().toString().substring(0, 20),
        blockHeader::getNumber,
        () -> firstHeader.getHash().toString().substring(0, 20),
        firstHeader::getNumber);
  }

  @Override
  public void prependChain(final BackwardSyncStorage historicalBackwardChain) {
    if (!(historicalBackwardChain instanceof InMemoryBackwardChain)) {
      throw new BackwardSyncException("Cannot merge non memory chain into memory chain");
    }
    BlockHeader firstHeader =
        getFirstAncestorHeader()
            .orElseThrow(
                () -> new BackwardSyncException("Cannot merge when syncing forward...", true));

    Block historicalPivot = historicalBackwardChain.getPivot();
    LOG.info(
        "Prepending previous chain we end at {}, they start at {}",
        firstHeader.getNumber(),
        historicalBackwardChain.getPivot().getHeader().getNumber());
    Block pivot = getPivot();
    if (firstHeader.getParentHash().equals(historicalPivot.getHash())) {
      Collections.reverse(historicalBackwardChain.getSuccessors());
      this.ancestors.addAll(
          historicalBackwardChain.getSuccessors().stream()
              .map(Block::getHeader)
              .collect(Collectors.toList()));
      this.ancestors.addAll(historicalBackwardChain.getAllAncestors());
      debugLambda(
          LOG,
          "Merged backward chain led by block {} into chain led by block {}, new backward chain starts at height {} and ends at height {}",
          () -> historicalPivot.getHash().toString().substring(0, 20),
          () -> pivot.getHash().toString().substring(0, 20),
          () -> pivot.getHeader().getNumber(),
          () -> getFirstAncestorHeader().orElseThrow().getNumber());

      trustedBlocks.putAll(((InMemoryBackwardChain) historicalBackwardChain).getAllTrustedBlocks());
    } else {
      warnLambda(
          LOG,
          "Cannot merge previous historical run because hashes of {}({}) and {}({}) do not equal. Ignoring previous run. Did someone lie to us?",
          () -> firstHeader.getNumber() - 1,
          () -> firstHeader.getParentHash().toHexString(),
          () -> historicalPivot.getHeader().getNumber(),
          () -> historicalPivot.getHash().toHexString());
    }
  }

  @Override
  public Block getPivot() {
    return successors.get(successors.size() - 1);
  }

  @Override
  public void dropFirstHeader() {
    ancestors.remove(ancestors.size() - 1);
  }

  @Override
  public void appendExpectedBlock(final Block newPivot) {
    successors.add(newPivot);
    trustedBlocks.put(newPivot.getHash(), newPivot);
  }

  @Override
  public List<Block> getSuccessors() {
    return successors;
  }

  @Override
  public boolean isTrusted(final Hash hash) {
    return trustedBlocks.containsKey(hash);
  }

  @Override
  public Block getTrustedBlock(final Hash hash) {
    return trustedBlocks.get(hash);
  }

  @Override
  public void clear() {
    ancestors.clear();
    successors.clear();
    trustedBlocks.clear();
  }

  @Override
  public void commit() {}
}

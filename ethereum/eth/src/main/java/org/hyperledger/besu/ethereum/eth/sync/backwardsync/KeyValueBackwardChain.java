/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.warnLambda;
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;

public class KeyValueBackwardChain implements BackwardSyncStorage, ValueConvertor<BlockHeader> {
  private static final Logger LOG = getLogger(KeyValueBackwardChain.class);

  private final List<Hash> ancestors = new ArrayList<>();
  private final List<Hash> successors = new ArrayList<>();

  protected final GenericKeyValueStorage<Hash, BlockHeader> headers;
  protected final GenericKeyValueStorage<Hash, Block> blocks;
  private final BlockHeaderFunctions blockHeaderFunctions;

  public KeyValueBackwardChain(
      final StorageProvider provider,
      final BlockHeaderFunctions blockHeaderFunctions,
      final Block pivot) {
    this.blockHeaderFunctions = blockHeaderFunctions;
    headers =
        new GenericKeyValueStorage<>(
            provider, KeyValueSegmentIdentifier.BACKWARD_SYNC_HEADERS, Bytes::toArrayUnsafe, this);
    blocks =
        new GenericKeyValueStorage<>(
            provider,
            KeyValueSegmentIdentifier.BACKWARD_SYNC_BLOCKS,
            Bytes::toArrayUnsafe,
            new ValueConvertor<Block>() {
              @Override
              public Block fromBytes(final byte[] bytes) {

                final RLPInput input = RLP.input(Bytes.wrap(bytes));
                return Block.readFrom(input, blockHeaderFunctions);
              }

              @Override
              public byte[] toBytes(final Block value) {
                return value.toRlp().toArrayUnsafe();
              }
            });
    headers.put(pivot.getHeader().getHash(), pivot.getHeader());
    blocks.put(pivot.getHash(), pivot);
    ancestors.add(pivot.getHeader().getHash());
    successors.add(pivot.getHash());
  }

  @Override
  public Optional<BlockHeader> getFirstAncestorHeader() {
    if (ancestors.isEmpty()) {
      return Optional.empty();
    }
    return headers.get(ancestors.get(ancestors.size() - 1));
  }

  @Override
  public List<BlockHeader> getFirstNAncestorHeaders(final int size) {
    List<Hash> resultList = new ArrayList<>(size);
    for (int i = size; i > 0; --i) {
      resultList.add(ancestors.get(ancestors.size() - i));
    }
    return resultList.stream()
        .map(h -> this.headers.get(h).orElseThrow())
        .collect(Collectors.toList());
  }

  @Override
  public List<BlockHeader> getAllAncestors() {
    return getFirstNAncestorHeaders(ancestors.size());
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

  @Override
  public void prependChain(final BackwardSyncStorage historicalBackwardChain) {
    BlockHeader firstHeader =
        getFirstAncestorHeader()
            .orElseThrow(
                () -> new BackwardSyncException("Cannot merge when syncing forward...", true));
    BlockHeader historicalHeader =
        historicalBackwardChain.getHeaderOnHeight(firstHeader.getNumber() - 1);
    if (firstHeader.getParentHash().equals(historicalHeader.getHash())) {
      Collections.reverse(historicalBackwardChain.getSuccessors());
      this.ancestors.addAll(
          historicalBackwardChain.getSuccessors().stream()
              .map(Block::getHeader)
              .filter(blockHeader -> blockHeader.getNumber() < firstHeader.getNumber())
              .map(BlockHeader::getHash)
              .collect(Collectors.toList()));
      this.ancestors.addAll(
          historicalBackwardChain.getAllAncestors().stream()
              .filter(blockHeader -> blockHeader.getNumber() < firstHeader.getNumber())
              .map(BlockHeader::getHash)
              .collect(Collectors.toList()));
      // todo maybe some successors are after our successors...
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
          () -> historicalHeader.getNumber(),
          () -> firstHeader.getParentHash().toHexString(),
          () -> historicalHeader.getHash().toHexString());
    }
  }

  @Override
  public Block getPivot() {
    return blocks.get(successors.get(successors.size() - 1)).orElseThrow();
  }

  @Override
  public void dropFirstHeader() {
    headers.drop(ancestors.get(ancestors.size() - 1));
    ancestors.remove(ancestors.size() - 1);
  }

  @Override
  public void appendExpectedBlock(final Block newPivot) {
    successors.add(newPivot.getHash());
    blocks.put(newPivot.getHash(), newPivot);
  }

  @Override
  public List<Block> getSuccessors() {
    return successors.stream()
        .map(hash -> blocks.get(hash).orElseThrow())
        .collect(Collectors.toList());
  }

  @Override
  public boolean isTrusted(final Hash hash) {
    return blocks.get(hash).isPresent();
  }

  @Override
  public Block getTrustedBlock(final Hash hash) {
    return blocks.get(hash).orElseThrow();
  }

  @Override
  public BlockHeader fromBytes(final byte[] bytes) {
    return BlockHeader.readFrom(RLP.input(Bytes.wrap(bytes)), blockHeaderFunctions);
  }

  @Override
  public byte[] toBytes(final BlockHeader value) {
    BytesValueRLPOutput output = new BytesValueRLPOutput();
    value.writeTo(output);
    return output.encoded().toArrayUnsafe();
  }

  @Override
  public void clear() {
    ancestors.clear();
    successors.clear();
    blocks.clear();
    headers.clear();
  }

  @Override
  public void commit() {}

  @Override
  public BlockHeader getHeaderOnHeight(final long height) {
    final long firstAncestor = headers.get(ancestors.get(0)).orElseThrow().getNumber();
    if (firstAncestor >= height) {
      LOG.info(
          "First: {} Height: {}, result: {}",
          firstAncestor,
          height,
          headers.get(ancestors.get((int) (firstAncestor - height))).orElseThrow().getNumber());
      return headers.get(ancestors.get((int) (firstAncestor - height))).orElseThrow();
    } else {
      final long firstSuccessor = headers.get(successors.get(0)).orElseThrow().getNumber();
      LOG.info(
          "First: {} Height: {}, result: {}",
          firstSuccessor,
          height,
          headers.get(successors.get((int) (height - firstSuccessor))).orElseThrow().getNumber());

      return headers.get(successors.get((int) (height - firstSuccessor))).orElseThrow();
    }
  }
}

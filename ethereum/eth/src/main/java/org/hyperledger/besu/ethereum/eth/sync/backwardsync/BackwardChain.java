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
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import org.slf4j.Logger;

public class BackwardChain {
  private static final Logger LOG = getLogger(BackwardChain.class);

  private final GenericKeyValueStorageFacade<Hash, BlockHeader> headers;
  private final GenericKeyValueStorageFacade<Hash, Block> blocks;
  private final GenericKeyValueStorageFacade<Hash, Hash> chainStorage;
  private Optional<BlockHeader> firstStoredAncestor = Optional.empty();
  private Optional<BlockHeader> lastStoredPivot = Optional.empty();
  private final Queue<Hash> hashesToAppend = new ArrayDeque<>();

  public BackwardChain(
      final GenericKeyValueStorageFacade<Hash, BlockHeader> headersStorage,
      final GenericKeyValueStorageFacade<Hash, Block> blocksStorage,
      final GenericKeyValueStorageFacade<Hash, Hash> chainStorage) {
    this.headers = headersStorage;
    this.blocks = blocksStorage;
    this.chainStorage = chainStorage;
  }

  public static BackwardChain from(
      final StorageProvider storageProvider, final BlockHeaderFunctions blockHeaderFunctions) {
    return new BackwardChain(
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            BlocksHeadersConvertor.of(blockHeaderFunctions),
            storageProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.BACKWARD_SYNC_HEADERS)),
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            BlocksConvertor.of(blockHeaderFunctions),
            storageProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.BACKWARD_SYNC_BLOCKS)),
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            new HashConvertor(),
            storageProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.BACKWARD_SYNC_CHAIN)));
  }

  public synchronized Optional<BlockHeader> getFirstAncestorHeader() {
    return firstStoredAncestor;
  }

  public synchronized List<BlockHeader> getFirstNAncestorHeaders(final int size) {
    List<BlockHeader> result = new ArrayList<>(size);
    Optional<BlockHeader> it = firstStoredAncestor;
    while (it.isPresent() && result.size() < size) {
      result.add(it.get());
      it = chainStorage.get(it.get().getHash()).flatMap(headers::get);
    }
    return result;
  }

  public synchronized void prependAncestorsHeader(final BlockHeader blockHeader) {
    if (firstStoredAncestor.isEmpty()) {
      firstStoredAncestor = Optional.of(blockHeader);
      lastStoredPivot = Optional.of(blockHeader);
      headers.put(blockHeader.getHash(), blockHeader);
      return;
    }
    BlockHeader firstHeader = firstStoredAncestor.get();
    headers.put(blockHeader.getHash(), blockHeader);
    chainStorage.put(blockHeader.getHash(), firstStoredAncestor.get().getHash());
    firstStoredAncestor = Optional.of(blockHeader);
    debugLambda(
        LOG,
        "Added header {} on height {} to backward chain led by pivot {} on height {}",
        blockHeader::toLogString,
        blockHeader::getNumber,
        () -> lastStoredPivot.orElseThrow().toLogString(),
        firstHeader::getNumber);
  }

  public synchronized Optional<Block> getPivot() {
    if (lastStoredPivot.isEmpty()) {
      return Optional.empty();
    }
    return blocks.get(lastStoredPivot.get().getHash());
  }

  public synchronized void dropFirstHeader() {
    if (firstStoredAncestor.isEmpty()) {
      return;
    }
    headers.drop(firstStoredAncestor.get().getHash());
    final Optional<Hash> hash = chainStorage.get(firstStoredAncestor.get().getHash());
    chainStorage.drop(firstStoredAncestor.get().getHash());
    firstStoredAncestor = hash.flatMap(headers::get);
    if (firstStoredAncestor.isEmpty()) {
      lastStoredPivot = Optional.empty();
    }
  }

  public synchronized void appendTrustedBlock(final Block newPivot) {
    debugLambda(LOG, "appending trusted block {}", newPivot::toLogString);
    headers.put(newPivot.getHash(), newPivot.getHeader());
    blocks.put(newPivot.getHash(), newPivot);
    if (lastStoredPivot.isEmpty()) {
      firstStoredAncestor = Optional.of(newPivot.getHeader());
    } else {
      if (newPivot.getHeader().getParentHash().equals(lastStoredPivot.get().getHash())) {
        chainStorage.put(lastStoredPivot.get().getHash(), newPivot.getHash());
      } else {
        firstStoredAncestor = Optional.of(newPivot.getHeader());
      }
    }
    lastStoredPivot = Optional.of(newPivot.getHeader());
  }

  public synchronized boolean isTrusted(final Hash hash) {
    return blocks.get(hash).isPresent();
  }

  public synchronized Block getTrustedBlock(final Hash hash) {
    return blocks.get(hash).orElseThrow();
  }

  public synchronized void clear() {
    blocks.clear();
    headers.clear();
    chainStorage.clear();
    firstStoredAncestor = Optional.empty();
    lastStoredPivot = Optional.empty();
    hashesToAppend.clear();
  }

  public synchronized Optional<Hash> getDescendant(final Hash blockHash) {
    return chainStorage.get(blockHash);
  }

  public synchronized Optional<Block> getBlock(final Hash hash) {
    return blocks.get(hash);
  }

  public synchronized Optional<BlockHeader> getHeader(final Hash hash) {
    return headers.get(hash);
  }

  public synchronized void addNewHash(final Hash newBlockHash) {
    if (hashesToAppend.contains(newBlockHash)) {
      return;
    }
    this.hashesToAppend.add(newBlockHash);
  }

  public synchronized Optional<Hash> getFirstHashToAppend() {
    return Optional.ofNullable(hashesToAppend.peek());
  }

  public synchronized void removeFromHashToAppend(final Hash hashToRemove) {
    if (hashesToAppend.contains(hashToRemove)) {
      hashesToAppend.remove(hashToRemove);
    }
  }
}

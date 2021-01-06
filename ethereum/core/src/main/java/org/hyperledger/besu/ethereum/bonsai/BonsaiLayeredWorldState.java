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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** A World State backed first by trie log layer and then by another world state. */
public class BonsaiLayeredWorldState implements BonsaiWorldView, WorldState {

  private final BonsaiWorldView parent;
  protected final long height;
  protected final TrieLogLayer trieLog;

  private final Hash worldStateRootHash;

  BonsaiLayeredWorldState(
      final BonsaiWorldView parent,
      final long height,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog) {
    this.parent = parent;
    this.height = height;
    this.worldStateRootHash = worldStateRootHash;
    this.trieLog = trieLog;
  }

  public BonsaiWorldView getParent() {
    return parent;
  }

  public TrieLogLayer getTrieLog() {
    return trieLog;
  }

  public long getHeight() {
    return height;
  }

  @Override
  public Optional<Bytes> getCode(final Address address) {
    // this must be iterative and lambda light because the stack may blow up
    // mainly because we don't have tail calls.
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      final Optional<Bytes> maybeCode = currentLayer.trieLog.getCode(address);
      if (maybeCode.isPresent()) {
        return maybeCode;
      }
      if (currentLayer.parent == null) {
        currentLayer = null;
      } else if (currentLayer.parent instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.parent;
      } else {
        return currentLayer.parent.getCode(address);
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getStateTrieNode(final Bytes location) {
    // this must be iterative and lambda light because the stack may blow up
    // mainly because we don't have tail calls.
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      if (currentLayer.parent == null) {
        currentLayer = null;
      } else if (currentLayer.parent instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.parent;
      } else {
        return currentLayer.parent.getStateTrieNode(location);
      }
    }
    return Optional.empty();
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 key) {
    return getStorageValueBySlotHash(address, Hash.hash(key.toBytes())).orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueBySlotHash(final Address address, final Hash slotHash) {
    // this must be iterative and lambda light because the stack may blow up
    // mainly because we don't have tail calls.
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      final Optional<UInt256> maybeValue =
          currentLayer.trieLog.getStorageBySlotHash(address, slotHash);
      if (maybeValue.isPresent()) {
        return maybeValue;
      }
      if (currentLayer.parent == null) {
        currentLayer = null;
      } else if (currentLayer.parent instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.parent;
      } else {
        return currentLayer.parent.getStorageValueBySlotHash(address, slotHash);
      }
    }
    return Optional.empty();
  }

  @Override
  public UInt256 getOriginalStorageValue(final Address address, final UInt256 key) {
    // This is the base layer for a block, all values are original.
    return getStorageValue(address, key);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    // this must be iterative and lambda light because the stack may blow up
    // mainly because we don't have tail calls.
    final Map<Bytes32, Bytes> results = new HashMap<>();
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      if (currentLayer.trieLog.hasStorageChanges(address)) {
        currentLayer
            .trieLog
            .streamStorageChanges(address)
            .forEach(
                entry -> {
                  if (!results.containsKey(entry.getKey())) {
                    final UInt256 value = entry.getValue().getUpdated();
                    // yes, store the nulls.  If it was deleted it should stay deleted
                    results.put(entry.getKey(), value == null ? null : value.toBytes());
                  }
                });
      }
      if (currentLayer.parent == null) {
        currentLayer = null;
      } else if (currentLayer.parent instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.parent;
      } else {
        final Account account = currentLayer.parent.get(address);
        if (account != null) {
          account
              .storageEntriesFrom(Hash.ZERO, Integer.MAX_VALUE)
              .forEach(
                  (k, v) -> {
                    if (!results.containsKey(k)) {
                      results.put(k, v.getValue().toBytes());
                    }
                  });
        }
        currentLayer = null;
      }
    }
    return results;
  }

  @Override
  public Account get(final Address address) {
    // this must be iterative and lambda light because the stack may blow up
    // mainly because we don't have tail calls.
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      final Optional<StateTrieAccountValue> maybeStateTrieAccount =
          currentLayer.trieLog.getAccount(address);
      if (maybeStateTrieAccount.isPresent()) {
        return new BonsaiAccount(
            BonsaiLayeredWorldState.this, address, maybeStateTrieAccount.get(), false);
      }
      if (currentLayer.parent == null) {
        currentLayer = null;
      } else if (currentLayer.parent instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.parent;
      } else {
        return currentLayer.parent.get(address);
      }
    }
    return null;
  }

  @Override
  public Hash rootHash() {
    return worldStateRootHash;
  }

  public Hash blockHash() {
    return trieLog.getBlockHash();
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    throw new UnsupportedOperationException("Bonsai does not support pruning and debug RPCs");
  }
}

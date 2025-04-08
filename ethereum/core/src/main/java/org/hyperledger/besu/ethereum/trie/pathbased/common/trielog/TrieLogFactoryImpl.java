/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.common.trielog;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogAccumulator;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public abstract class TrieLogFactoryImpl implements TrieLogFactory {
  @Override
  public TrieLogLayer create(final TrieLogAccumulator accumulator, final BlockHeader blockHeader) {
    TrieLogLayer layer = new TrieLogLayer();
    layer.setBlockHash(blockHeader.getBlockHash());
    layer.setBlockNumber(blockHeader.getNumber());
    for (final var updatedAccount : accumulator.getAccountsToUpdate().entrySet()) {
      final var value = updatedAccount.getValue();
      final var oldAccountValue = value.getPrior();
      final var newAccountValue = value.getUpdated();
      if (oldAccountValue == null && newAccountValue == null) {
        // by default do not persist empty reads of accounts to the trie log
        continue;
      }
      layer.addAccountChange(updatedAccount.getKey(), oldAccountValue, newAccountValue);
    }

    for (final var updatedCode : accumulator.getCodeToUpdate().entrySet()) {
      layer.addCodeChange(
          updatedCode.getKey(),
          updatedCode.getValue().getPrior(),
          updatedCode.getValue().getUpdated(),
          blockHeader.getBlockHash());
    }

    for (final var updatesStorage : accumulator.getStorageToUpdate().entrySet()) {
      final Address address = updatesStorage.getKey();
      for (final var slotUpdate : updatesStorage.getValue().entrySet()) {
        var val = slotUpdate.getValue();

        if (val.getPrior() == null && val.getUpdated() == null) {
          // by default do not persist empty reads to the trie log
          continue;
        }
        layer.addStorageChange(address, slotUpdate.getKey(), val.getPrior(), val.getUpdated());
      }
    }
    return layer;
  }

  protected static <T> T nullOrValue(final RLPInput input, final Function<RLPInput, T> reader) {
    if (input.nextIsNull()) {
      input.skipNext();
      return null;
    } else {
      return reader.apply(input);
    }
  }

  protected static boolean getOptionalIsCleared(final RLPInput input) {
    return Optional.of(input.isEndOfCurrentList())
        .filter(isEnd -> !isEnd) // isCleared is optional
        .map(__ -> nullOrValue(input, RLPInput::readInt))
        .filter(i -> i == 1)
        .isPresent();
  }

  public static <T> void writeRlp(
      final TrieLog.LogTuple<T> value,
      final RLPOutput output,
      final BiConsumer<RLPOutput, T> writer) {
    output.startList();
    writeInnerRlp(value, output, writer);
    output.endList();
  }

  public static <T> void writeInnerRlp(
      final TrieLog.LogTuple<T> value,
      final RLPOutput output,
      final BiConsumer<RLPOutput, T> writer) {
    if (value.getPrior() == null) {
      output.writeNull();
    } else {
      writer.accept(output, value.getPrior());
    }
    if (value.getUpdated() == null) {
      output.writeNull();
    } else {
      writer.accept(output, value.getUpdated());
    }
    if (!value.isLastStepCleared()) {
      output.writeNull();
    } else {
      output.writeInt(1);
    }
  }
}

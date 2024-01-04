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

package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_HASH_COUNT;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.List;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class CodeHashCodeStorageStrategyTest {
  final CodeHashCodeStorageStrategy codeStorage = new CodeHashCodeStorageStrategy();
  final SegmentedKeyValueStorage keyValueStorage =
      new InMemoryKeyValueStorageProvider()
          .getStorageBySegmentIdentifiers(List.of(CODE_STORAGE, CODE_HASH_COUNT));
  final Bytes code = Bytes.fromHexString("0x10");
  final Hash codeHash = Hash.hash(code);

  @Test
  void updatesCodeCountWhenCodeDoesntAlreadyExist() {
    useTransaction(t -> codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code));

    assertThat(keyValueStorage.get(CODE_STORAGE, codeHash.toArray())).hasValue(code.toArray());
    assertThat(keyValueStorage.get(CODE_HASH_COUNT, codeHash.toArray())).hasValue(codeCount(1));
  }

  @Test
  void updatesCodeCountWhenCodeAlreadyExists() {
    useTransaction(t -> codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code));
    useTransaction(t -> codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code));

    assertThat(keyValueStorage.get(CODE_STORAGE, codeHash.toArray())).hasValue(code.toArray());
    assertThat(keyValueStorage.get(CODE_HASH_COUNT, codeHash.toArray())).hasValue(codeCount(2));
  }

  @Test
  void updatesCodeCountForMultipleCodeUpdatesInSameTransaction() {
    useTransaction(
        t -> {
          codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code);
          codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code);
        });

    assertThat(keyValueStorage.get(CODE_STORAGE, codeHash.toArray())).hasValue(code.toArray());
    assertThat(keyValueStorage.get(CODE_HASH_COUNT, codeHash.toArray())).hasValue(codeCount(2));
  }

  @Test
  void onlyStoresCodeWhenCodeDoesNotAlreadyExist() {
    useTransaction(t -> codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code));
    assertThat(keyValueStorage.get(CODE_STORAGE, codeHash.toArray())).hasValue(code.toArray());
    assertThat(keyValueStorage.get(CODE_HASH_COUNT, codeHash.toArray())).hasValue(codeCount(1));

    // count will be incremented, but code will not be stored again
    useTransaction(
        t -> {
          final var txSpy = spy(t);
          codeStorage.putFlatCode(txSpy, Hash.ZERO, codeHash, code);
          verify(txSpy, never()).put(CODE_STORAGE, codeHash.toArray(), code.toArray());
        });
    assertThat(keyValueStorage.get(CODE_HASH_COUNT, codeHash.toArray())).hasValue(codeCount(2));
  }

  @Test
  void removeDeletesWhenZeroReferences() {
    useTransaction(t -> codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code));
    useTransaction(t -> codeStorage.removeFlatCode(t, Hash.ZERO, codeHash));

    assertThat(keyValueStorage.get(CODE_STORAGE, codeHash.toArray())).isEmpty();
    assertThat(keyValueStorage.get(CODE_HASH_COUNT, codeHash.toArray())).isEmpty();
  }

  @Test
  void removeDoesntDeleteWhenMoreThanZeroReferences() {
    useTransaction(t -> codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code));
    useTransaction(t -> codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code));
    useTransaction(t -> codeStorage.removeFlatCode(t, Hash.ZERO, codeHash));

    assertThat(keyValueStorage.get(CODE_STORAGE, codeHash.toArray())).hasValue(code.toArray());
    assertThat(keyValueStorage.get(CODE_HASH_COUNT, codeHash.toArray())).hasValue(codeCount(1));
  }

  @Test
  void removeDoesntDecrementBelowZero() {
    useTransaction(t -> codeStorage.removeFlatCode(t, Hash.ZERO, codeHash));
    useTransaction(t -> codeStorage.removeFlatCode(t, Hash.ZERO, codeHash));

    assertThat(keyValueStorage.get(CODE_STORAGE, codeHash.toArray())).isEmpty();
    assertThat(keyValueStorage.get(CODE_HASH_COUNT, codeHash.toArray())).isEmpty();
  }

  @Test
  void clearDeletesCodeStorageAndCodeHashCount() {
    useTransaction(t -> codeStorage.putFlatCode(t, Hash.ZERO, codeHash, code));

    codeStorage.clear(keyValueStorage);

    assertThat(keyValueStorage.hasValues(CODE_STORAGE)).isFalse();
    assertThat(keyValueStorage.hasValues(CODE_HASH_COUNT)).isFalse();
  }

  private void useTransaction(
      final Consumer<SegmentedKeyValueStorageTransaction> transactionAction) {
    var transaction = keyValueStorage.startTransaction();
    transactionAction.accept(transaction);
    transaction.commit();
  }

  private byte[] codeCount(final long value) {
    return Bytes.ofUnsignedLong(value).trimLeadingZeros().toArray();
  }
}

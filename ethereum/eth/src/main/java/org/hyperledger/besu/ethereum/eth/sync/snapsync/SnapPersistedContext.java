/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.eth.sync.backwardsync.GenericKeyValueStorageFacade;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.ValueConvertor;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;

public class SnapPersistedContext {

  private final byte[] SNAP_INCONSISTENT_ACCOUNT_INDEX =
      "snapInconsistentAccountsStorageIndex".getBytes(StandardCharsets.UTF_8);

  private final GenericKeyValueStorageFacade<BigInteger, AccountRangeDataRequest>
      accountRangeToDownload;
  private final GenericKeyValueStorageFacade<BigInteger, Bytes> inconsistentAccounts;

  public SnapPersistedContext(final StorageProvider storageProvider) {
    this.accountRangeToDownload =
        new GenericKeyValueStorageFacade<>(
            BigInteger::toByteArray,
            new ValueConvertor<>() {
              @Override
              public AccountRangeDataRequest fromBytes(final byte[] bytes) {
                return AccountRangeDataRequest.deserialize(
                    new BytesValueRLPInput(Bytes.of(bytes), false));
              }

              @Override
              public byte[] toBytes(final AccountRangeDataRequest value) {
                return value.serialize().toArrayUnsafe();
              }
            },
            storageProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.SNAPSYNC_MISSING_ACCOUNT_RANGE));
    this.inconsistentAccounts =
        new GenericKeyValueStorageFacade<>(
            BigInteger::toByteArray,
            new ValueConvertor<>() {
              @Override
              public Bytes fromBytes(final byte[] bytes) {
                return Bytes.of(bytes);
              }

              @Override
              public byte[] toBytes(final Bytes value) {
                return value.toArrayUnsafe();
              }
            },
            storageProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.SNAPSYNC_ACCOUNT_TO_FIX));
  }

  public void updatePersistedTasks(final List<? extends SnapDataRequest> accountRangeDataRequests) {
    accountRangeToDownload.clear();
    accountRangeToDownload.putAll(
        keyValueStorageTransaction ->
            IntStream.range(0, accountRangeDataRequests.size())
                .forEach(
                    index ->
                        keyValueStorageTransaction.put(
                            BigInteger.valueOf(index).toByteArray(),
                            ((AccountRangeDataRequest) accountRangeDataRequests.get(index))
                                .serialize()
                                .toArrayUnsafe())));
  }

  public void addInconsistentAccount(final Bytes inconsistentAccount) {
    final BigInteger index =
        inconsistentAccounts
            .get(SNAP_INCONSISTENT_ACCOUNT_INDEX)
            .map(bytes -> new BigInteger(bytes.toArrayUnsafe()).add(BigInteger.ONE))
            .orElse(BigInteger.ZERO);
    inconsistentAccounts.putAll(
        keyValueStorageTransaction -> {
          keyValueStorageTransaction.put(SNAP_INCONSISTENT_ACCOUNT_INDEX, index.toByteArray());
          keyValueStorageTransaction.put(index.toByteArray(), inconsistentAccount.toArrayUnsafe());
        });
  }

  public List<AccountRangeDataRequest> getPersistedTasks() {
    return accountRangeToDownload
        .streamValuesFromKeysThat(bytes -> true)
        .collect(Collectors.toList());
  }

  public HashSet<Bytes> getInconsistentAccounts() {
    return inconsistentAccounts
        .streamValuesFromKeysThat(notEqualsTo(SNAP_INCONSISTENT_ACCOUNT_INDEX))
        .collect(Collectors.toCollection(HashSet::new));
  }

  public void clearAccountRangeTasks() {
    accountRangeToDownload.clear();
  }

  public void clear() {
    accountRangeToDownload.clear();
    inconsistentAccounts.clear();
  }

  public void close() throws IOException {
    accountRangeToDownload.close();
    inconsistentAccounts.close();
  }

  private Predicate<byte[]> notEqualsTo(final byte[] name) {
    return key -> !Arrays.equals(key, name);
  }
}

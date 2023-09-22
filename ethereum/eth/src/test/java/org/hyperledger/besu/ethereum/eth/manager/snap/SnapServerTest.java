/*
 * Copyright Hyperledger Besu Contributors
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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.DEFAULT_CONFIG;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class SnapServerTest {
  static Random rand = new Random();

  record SnapTestAccount(Hash addressHash, StateTrieAccountValue accountValue) {
    Bytes asRLP() {
      return RLP.encode(accountValue::writeTo);
    }
  }

  static final ObservableMetricsSystem noopMetrics = new NoOpMetricsSystem();

  static final SnapTestAccount acct1 = testAcct("10");
  static final SnapTestAccount acct2 = testAcct("20");
  static final SnapTestAccount acct3 = testAcct("30");
  static final SnapTestAccount acct4 = testAcct("40");

  final KeyValueStorageProvider storageProvider = new InMemoryKeyValueStorageProvider();
  final BonsaiWorldStateKeyValueStorage inMemoryStorage =
      new BonsaiWorldStateKeyValueStorage(storageProvider, noopMetrics, DEFAULT_CONFIG);

  final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
      new StoredMerklePatriciaTrie<>(
          inMemoryStorage::getAccountStateTrieNode, Function.identity(), Function.identity());
  final WorldStateProofProvider proofProvider = new WorldStateProofProvider(inMemoryStorage);

  final SnapServer snapServer =
      new SnapServer(new EthMessages(), __ -> Optional.of(inMemoryStorage));

  @Test
  public void assertEmptyRangeLeftProofOfExclusionAndNextAccount() {
    // for a range request that returns empty, we should return just a proof of exclusion on the
    // left
    // and the next account after the limit hash
    insertTestAccounts(acct1, acct4);

    var rangeData =
        getAndVerifyAcountRangeData(requestAccountRange(acct2.addressHash, acct3.addressHash), 1);

    // expect to find only one value acct4, outside the requested range
    var outOfRangeVal = rangeData.accounts().entrySet().stream().findFirst();
    assertThat(outOfRangeVal).isPresent();
    assertThat(outOfRangeVal.get().getKey()).isEqualTo(acct4.addressHash());

    // assert proofs are valid for the requested range
    assertThat(assertIsValidRangeProof(acct2.addressHash, rangeData)).isTrue();
  }

  @Test
  public void assertLimitRangeResponse() {
    // When our final range request is empty, no next account is possible,
    //      and we should return just a proof of exclusion of the right
    insertTestAccounts(acct1, acct2, acct3, acct4);

    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    tmp.writeBytes(storageTrie.getRootHash());
    tmp.writeBytes(acct1.addressHash);
    tmp.writeBytes(acct4.addressHash);
    tmp.writeBigIntegerScalar(BigInteger.valueOf(256L));
    tmp.endList();
    var tinyRangeLimit = new GetAccountRangeMessage(tmp.encoded()).wrapMessageData(BigInteger.ONE);

    var rangeData =
        getAndVerifyAcountRangeData(
            (AccountRangeMessage) snapServer.constructGetAccountRangeResponse(tinyRangeLimit), 2);

    // assert proofs are valid for the requested range
    assertThat(assertIsValidRangeProof(acct1.addressHash, rangeData)).isTrue();
  }

  @Test
  public void assertLastEmptyRange() {
    // When our final range request is empty, no next account is possible,
    //      and we should return just a proof of exclusion of the right
    insertTestAccounts(acct1, acct2);
    var rangeData =
        getAndVerifyAcountRangeData(requestAccountRange(acct3.addressHash, acct4.addressHash), 0);

    // assert proofs are valid for the requested range
    assertThat(assertIsValidRangeProof(acct3.addressHash, rangeData)).isTrue();
  }

  @Test
  public void assertAccountFoundAtStartHashProof() {
    // account found at startHash
    insertTestAccounts(acct1, acct2, acct3, acct4);
    var rangeData =
        getAndVerifyAcountRangeData(requestAccountRange(acct1.addressHash, acct4.addressHash), 4);

    // assert proofs are valid for requested range
    assertThat(assertIsValidRangeProof(acct1.addressHash, rangeData)).isTrue();
  }

  static SnapTestAccount testAcct(final String hexAddr) {
    return new SnapTestAccount(
        Hash.wrap(Bytes32.rightPad(Bytes.fromHexString(hexAddr))),
        new StateTrieAccountValue(
            rand.nextInt(0, 1), Wei.of(rand.nextLong(0L, 1L)), Hash.EMPTY_TRIE_HASH, Hash.EMPTY));
  }

  void insertTestAccounts(final SnapTestAccount... accounts) {
    final var updater = inMemoryStorage.updater();
    for (SnapTestAccount account : accounts) {
      updater.putAccountInfoState(account.addressHash(), account.asRLP());
      storageTrie.put(account.addressHash(), account.asRLP());
    }
    storageTrie.commit(updater::putAccountStateTrieNode);
    updater.commit();
  }

  boolean assertIsValidRangeProof(
      final Hash startHash, final AccountRangeMessage.AccountRangeData accountRange) {
    Bytes32 lastKey =
        accountRange.accounts().keySet().stream()
            .reduce((first, second) -> second)
            .orElse(startHash);

    return proofProvider.isValidRangeProof(
        acct2.addressHash,
        lastKey,
        storageTrie.getRootHash(),
        accountRange.proofs(),
        accountRange.accounts());
  }

  AccountRangeMessage requestAccountRange(final Hash startHash, final Hash limitHash) {
    return (AccountRangeMessage)
        snapServer.constructGetAccountRangeResponse(
            GetAccountRangeMessage.create(
                    Hash.wrap(storageTrie.getRootHash()), startHash, limitHash)
                .wrapMessageData(BigInteger.ONE));
  }

  AccountRangeMessage.AccountRangeData getAndVerifyAcountRangeData(
      final AccountRangeMessage range, final int expectedSize) {
    assertThat(range).isNotNull();
    var accountData = range.accountData(false);
    assertThat(accountData).isNotNull();
    assertThat(accountData.accounts().size()).isEqualTo(expectedSize);
    return accountData;
  }
}

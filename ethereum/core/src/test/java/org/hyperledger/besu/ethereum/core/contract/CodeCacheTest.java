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

package org.hyperledger.besu.ethereum.core.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.UpdateTrackingAccount;
import org.hyperledger.besu.ethereum.vm.Code;

import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class CodeCacheTest {
  private final String dummyContract = "0x010203040506070809";

  final BlockDataGenerator gen = new BlockDataGenerator();

  @Test
  public void overflowCacheAndEvictLRU() {

    // put a contract in expecting it to be the LRU
    final UpdateTrackingAccount<EvmAccount> lruContract =
        new UpdateTrackingAccount<>(gen.address());
    Bytes lruCode = Bytes.concatenate(Bytes.of(0), Bytes.fromHexString(dummyContract));
    lruContract.setCode(lruCode);
    final CodeCache cache = new CodeCache(4 * lruCode.size(), new CodeLoader());
    Optional<Code> lookedUp = cache.getContract(lruContract);
    assertThat(lookedUp).isNotEmpty();
    assertThat(cache.getIfPresent(lruContract)).isNotNull();
    assertThat(cache.size()).isEqualTo(1);
    assertThat(lookedUp.get().getBytes()).isEqualTo(lruCode);
    IntStream.range(1, 5)
        .forEach(
            index -> {
              final UpdateTrackingAccount<EvmAccount> mruContract =
                  new UpdateTrackingAccount<>(gen.address());
              Bytes otherContractCode =
                  Bytes.concatenate(Bytes.of(index), Bytes.fromHexString(dummyContract));
              mruContract.setCode(otherContractCode);
              Optional<Code> notNull = cache.getContract(mruContract);
              assertThat(notNull).isNotEmpty();
              assertThat(notNull.get().getBytes()).isEqualTo(otherContractCode);
            });
    cache.cleanUp();
    assertThat(cache.size()).isEqualTo(4);
    assertThat(cache.getIfPresent(lruContract)).isNull();
  }
}

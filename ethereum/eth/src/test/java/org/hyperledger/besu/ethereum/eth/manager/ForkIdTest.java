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
package org.hyperledger.besu.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class ForkIdTest {
  private Long[] forksMainnet = {1150000L, 1920000L, 2463000L, 2675000L, 4370000L, 7280000L};
  private String mainnetGenHash =
      "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3";

  @Test
  public void checkItFunctionsWithPresentBehavior() {
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), null, null);
    assertThat(forkId.peerCheck(Hash.fromHexString(mainnetGenHash))).isFalse();
  }

  @Test
  public void checkCorrectMainnetForkIdHashesGenerated() {
    ForkId.ForkIdEntry[] checkIds = {
      ForkId.createIdEntry("0xfc64ec04", 1150000L), // Unsynced
      ForkId.createIdEntry("0x97c2c34c", 1920000L), // First Homestead block
      ForkId.createIdEntry("0x91d1f948", 2463000L), // First DAO block
      ForkId.createIdEntry("0x7a64da13", 2675000L), // First Tangerine block
      ForkId.createIdEntry("0x3edd5b10", 4370000L), // First Spurious block
      ForkId.createIdEntry("0xa00bc324", 7280000L), // First Byzantium block
      ForkId.createIdEntry("0x668db0af", 0L) // Today Petersburg block
    };
    List<Long> list = Arrays.asList(forksMainnet);
    ForkId forkId = ForkId.buildCollection(Hash.fromHexString(mainnetGenHash), list);
    ArrayDeque<ForkId.ForkIdEntry> entries = forkId.getForkAndHashList();

    for (ForkId.ForkIdEntry id : checkIds) {
      ForkId.ForkIdEntry testVal = entries.poll();
      if (testVal == null) {
        break;
      }
      assertThat(testVal.equals(id)).isTrue();
    }
  }

  @Test
  public void checkCorrectRopstenForkIdHashesGenerated() {
    Long[] forks = {10L, 1700000L, 4230000L, 4939394L};
    String genHash = "0x41941023680923e0fe4d74a34bdac8141f2540e3ae90623718e47d66d1ca4a2d";
    ForkId.ForkIdEntry[] checkIds = {
      ForkId.createIdEntry(
          "0x30c7ddbc", 10L), // Unsynced, last Frontier, Homestead and first Tangerine block
      ForkId.createIdEntry("0x63760190", 1700000L), // First Spurious block
      ForkId.createIdEntry("0x3ea159c7", 4230000L), // First Byzantium block
      ForkId.createIdEntry("0x97b544f3", 4939394L), // First Constantinople block
      ForkId.createIdEntry("0xd6e2149b", 0L) // Today Petersburg block
    };
    List<Long> list = Arrays.asList(forks);
    ForkId forkId = ForkId.buildCollection(Hash.fromHexString(genHash), list);
    ArrayDeque<ForkId.ForkIdEntry> entries = forkId.getForkAndHashList();

    for (ForkId.ForkIdEntry id : checkIds) {
      ForkId.ForkIdEntry testVal = entries.poll();
      if (testVal == null) {
        break;
      }
      assertThat(testVal.equals(id)).isTrue();
    }
  }

  @Test
  public void checkCorrectRinkebyForkIdHashesGenerated() {
    Long[] forks = {1L, 2L, 3L, 1035301L, 3660663L, 4321234L};
    String genHash = "0x6341fd3daf94b748c72ced5a5b26028f2474f5f00d824504e4fa37a75767e177";
    ForkId.ForkIdEntry[] checkIds = {
      ForkId.createIdEntry(
          "0x3b8e0691", 1L), // Unsynced, last Frontier, Homestead and first Tangerine block
      ForkId.createIdEntry("0x60949295", 2L), // Last Tangerine block
      ForkId.createIdEntry("0x8bde40dd", 3L), // First Spurious block
      ForkId.createIdEntry("0xcb3a64bb", 1035301L), // First Byzantium block
      ForkId.createIdEntry("0x8d748b57", 3660663L), // First Constantinople block
      ForkId.createIdEntry("0xe49cab14", 4321234L), // First Petersburg block
      ForkId.createIdEntry("0xafec6b27", 0L) // Today Petersburg block
    };
    List<Long> list = Arrays.asList(forks);
    ForkId forkId = ForkId.buildCollection(Hash.fromHexString(genHash), list);
    ArrayDeque<ForkId.ForkIdEntry> entries = forkId.getForkAndHashList();

    for (ForkId.ForkIdEntry id : checkIds) {
      ForkId.ForkIdEntry testVal = entries.poll();
      if (testVal == null) {
        break;
      }
      assertThat(testVal.equals(id)).isTrue();
    }
  }

  @Test
  public void check1PetersburgWithRemoteAnnouncingTheSame() {
    // 1 Local is mainnet Petersburg, remote announces the same. No future fork is announced.
    //  {7987396, ID{Hash: 0x668db0af, Next: 0}, nil},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7987396L);
    Boolean result = forkId.peerCheck("0x668db0af", 0L);
    assertThat(result).isTrue();
  }

  @Test
  public void check2PetersburgWithRemoteAnnouncingTheSameAndNextFork() {
    // 2 Local is mainnet Petersburg, remote announces the same. Remote also announces a next fork
    // at block 0xffffffff, but that is uncertain.
    //	{7987396, ID{Hash: 0x668db0af, Next: math.MaxUint64}, nil},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7987396L);
    Boolean result = forkId.peerCheck("0x668db0af", Long.MAX_VALUE);
    assertThat(result).isTrue();
  }

  @Test
  public void check3ByzantiumAwareOfPetersburgRemoteUnawareOfPetersburg() {
    // 3 Local is mainnet currently in Byzantium only (so it's aware of Petersburg), remote
    // announces also Byzantium, but it's not yet aware of Petersburg (e.g. non updated node before
    // the fork).
    // In this case we don't know if Petersburg passed yet or not.
    //	{7279999, ID{Hash: 0xa00bc324, Next: 0}, nil},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7279999L);
    Boolean result = forkId.peerCheck("0xa00bc324", 0L);
    assertThat(result).isTrue();
  }

  @Test
  public void check4ByzantiumAwareOfPetersburgRemoteAwareOfPetersburg() {
    // 4 Local is mainnet currently in Byzantium only (so it's aware of Petersburg), remote
    // announces  also Byzantium, and it's also aware of Petersburg (e.g. updated node before the
    // fork). We don't know if Petersburg passed yet (will pass) or not.
    //	{7279999, ID{Hash: 0xa00bc324, Next: 7280000}, nil},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7279999L);
    Boolean result = forkId.peerCheck("0xa00bc324", 7280000L);
    assertThat(result).isTrue();
  }

  @Test
  public void check5ByzantiumAwareOfPetersburgRemoteAnnouncingUnknownFork() {
    // 5 Local is mainnet currently in Byzantium only (so it's aware of Petersburg), remote
    // announces also Byzantium, and it's also aware of some random fork (e.g. misconfigured
    // Petersburg).
    // As neither forks passed at neither nodes, they may mismatch, but we still connect for now.
    //	{7279999, ID{Hash: 0xa00bc324, Next: math.MaxUint64}, nil},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7279999L);
    Boolean result = forkId.peerCheck("0xa00bc324", Long.MAX_VALUE);
    assertThat(result).isTrue();
  }

  @Test
  public void check6PetersburgWithRemoteAnnouncingByzantiumAwareOfPetersburg() {
    // 6 Local is mainnet Petersburg, remote announces Byzantium + knowledge about Petersburg.
    // Remote is simply out of sync, accept.
    //	{7987396, ID{Hash: 0x668db0af, Next: 7280000}, nil},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7987396L);
    Boolean result = forkId.peerCheck("0x668db0af", 7280000L);
    assertThat(result).isTrue();
  }

  @Test
  public void check7PetersburgWithRemoteAnnouncingSpuriousAwareOfByzantiumRemoteMayNeedUpdate() {
    // 7 Local is mainnet Petersburg, remote announces Spurious + knowledge about Byzantium.
    // Remote is definitely out of sync. It may or may not need the Petersburg update, we don't know
    // yet.
    //	{7987396, ID{Hash: 0x3edd5b10, Next: 4370000}, nil},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7987396L);
    Boolean result = forkId.peerCheck("0x3edd5b10", 4370000L);
    assertThat(result).isTrue();
  }

  @Test
  public void check8ByzantiumWithRemoteAnnouncingPetersburgLocalOutOfSync() {
    // 8 Local is mainnet Byzantium, remote announces Petersburg. Local is out of sync, accept.
    //	{7279999, ID{Hash: 0x668db0af, Next: 0}, nil},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7279999L);
    Boolean result = forkId.peerCheck("0x668db0af", 0L);
    assertThat(result).isTrue();
  }

  @Test
  public void check9SpuriousWithRemoteAnnouncingByzantiumRemoteUnawareOfPetersburg() {
    // 9 Local is mainnet Spurious, remote announces Byzantium, but is not aware of Petersburg.
    // Local out of sync. Local also knows about a future fork, but that is uncertain yet.
    //	{4369999, ID{Hash: 0xa00bc324, Next: 0}, nil},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 4369999L);
    Boolean result = forkId.peerCheck("0xa00bc324", 0L);
    assertThat(result).isTrue();
  }

  @Test
  public void check10PetersburgWithRemoteAnnouncingByzantiumRemoteUnawareOfAdditionalForks() {
    // 10 Local is mainnet Petersburg. remote announces Byzantium but is not aware of further forks.
    // Remote needs software update.
    //	{7987396, ID{Hash: 0xa00bc324, Next: 0}, ErrRemoteStale},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7987396L);
    Boolean result = forkId.peerCheck("0xa00bc324", 0L);
    assertThat(result).isFalse();
  }

  @Test
  public void check11PetersburgWithRemoteAnnouncingPetersburgAndFutureForkLocalNeedsUpdate() {
    // 11 Local is mainnet Petersburg, and isn't aware of more forks. Remote announces Petersburg +
    // 0xffffffff. Local needs software update, reject.
    //	{7987396, ID{Hash: 0x5cddc0e1, Next: 0}, ErrLocalIncompatibleOrStale},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7987396L);
    Boolean result = forkId.peerCheck("0x5cddc0e1", 0L);
    assertThat(result).isFalse();
  }

  @Test
  public void check12ByzantiumWithRemoteAnnouncingPetersburgAndFutureForkLocalNeedsUpdate() {
    // 12 Local is mainnet Byzantium, and is aware of Petersburg. Remote announces Petersburg +
    // 0xffffffff. Local needs software update, reject.
    //	{7279999, ID{Hash: 0x5cddc0e1, Next: 0}, ErrLocalIncompatibleOrStale},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7279999L);
    Boolean result = forkId.peerCheck("0x5cddc0e1", 0L);
    assertThat(result).isFalse();
  }

  @Test
  public void check13ByzantiumWithRemoteAnnouncingRinkebyPetersburg() {
    // 13 Local is mainnet Petersburg, remote is Rinkeby Petersburg.
    //	{7987396, ID{Hash: 0xafec6b27, Next: 0}, ErrLocalIncompatibleOrStale},
    List<Long> list = Arrays.asList(forksMainnet);
    Set<Long> forkSet = new LinkedHashSet<>(list);
    ForkId forkId = new ForkId(Hash.fromHexString(mainnetGenHash), forkSet, 7987396L);
    Boolean result = forkId.peerCheck("0xafec6b27", 0L);
    assertThat(result).isFalse();
  }

  @Test
  public void createAndDecodeRLP() {
    ForkId.ForkIdEntry forkIdEntry = ForkId.createIdEntry("0xa00bc324", 7280000L);
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeList(forkIdEntry.asList(), ForkId.ForkIdEntry::writeTo);
    BytesValue bytesValue = out.encoded();
    BytesValueRLPInput in = new BytesValueRLPInput(bytesValue, false);
    List<ForkId.ForkIdEntry> forkId = in.readList(ForkId::readFrom);
    ForkId.ForkIdEntry decodedEntry = forkId.get(0);
    assertThat(forkIdEntry.equals(decodedEntry)).isTrue();
  }
}

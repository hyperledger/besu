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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.ForkIdManager.ForkId;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.Arrays;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class ForkIdManagerTest {
  private final Long[] forksMainnet = {1150000L, 1920000L, 2463000L, 2675000L, 4370000L, 7280000L};
  private final String mainnetGenHash =
      "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3";
  private final String consortiumNetworkGenHash =
      "0x4109c6d17ca107e4de7565c94b429db8f5839593a9c57f3f31430b29b378b39d";

  private Blockchain mockBlockchain(final String genesisHash, final long chainHeight) {
    final Blockchain mockchain = mock(Blockchain.class);
    final BlockHeader mockHeader = mock(BlockHeader.class);
    final Block block = new Block(mockHeader, null);
    when(mockchain.getGenesisBlock()).thenReturn(block);
    when(mockchain.getChainHeadBlockNumber()).thenReturn(chainHeight);
    when(mockHeader.getHash()).thenReturn(Hash.fromHexString(genesisHash));
    return mockchain;
  }

  @Test
  public void checkItFunctionsWithPresentBehavior() {
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 0), emptyList());
    assertThat(forkIdManager.peerCheck(Hash.fromHexString(mainnetGenHash))).isFalse();
    assertThat(forkIdManager.getLatestForkId()).isNull();
  }

  @Test
  public void checkCorrectMainnetForkIdHashesGenerated() {
    final ForkId[] checkIds = {
      new ForkId(Bytes.fromHexString("0xfc64ec04"), 1150000L), // Unsynced
      new ForkId(Bytes.fromHexString("0x97c2c34c"), 1920000L), // First Homestead block
      new ForkId(Bytes.fromHexString("0x91d1f948"), 2463000L), // First DAO block
      new ForkId(Bytes.fromHexString("0x7a64da13"), 2675000L), // First Tangerine block
      new ForkId(Bytes.fromHexString("0x3edd5b10"), 4370000L), // First Spurious block
      new ForkId(Bytes.fromHexString("0xa00bc324"), 7280000L), // First Byzantium block
      new ForkId(Bytes.fromHexString("0x668db0af"), 0L) // Today Petersburg block
    };
    final List<Long> list = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager = new ForkIdManager(mockBlockchain(mainnetGenHash, 0), list);
    final List<ForkId> entries = forkIdManager.getForkAndHashList();
    assertThat(entries).containsExactly(checkIds);
    assertThat(forkIdManager.getLatestForkId()).isNotNull();
    assertThat(forkIdManager.getLatestForkId()).isEqualTo(checkIds[6]);
  }

  @Test
  public void checkCorrectRopstenForkIdHashesGenerated() {
    final Long[] forks = {10L, 1700000L, 4230000L, 4939394L};
    final String genHash = "0x41941023680923e0fe4d74a34bdac8141f2540e3ae90623718e47d66d1ca4a2d";
    final ForkId[] checkIds = {
      new ForkId(
          Bytes.fromHexString("0x30c7ddbc"),
          10L), // Unsynced, last Frontier, Homestead and first Tangerine block
      new ForkId(Bytes.fromHexString("0x63760190"), 1700000L), // First Spurious block
      new ForkId(Bytes.fromHexString("0x3ea159c7"), 4230000L), // First Byzantium block
      new ForkId(Bytes.fromHexString("0x97b544f3"), 4939394L), // First Constantinople block
      new ForkId(Bytes.fromHexString("0xd6e2149b"), 0L) // Today Petersburg block
    };
    final List<Long> list = Arrays.asList(forks);
    final ForkIdManager forkIdManager = new ForkIdManager(mockBlockchain(genHash, 0), list);
    final List<ForkId> entries = forkIdManager.getForkAndHashList();

    assertThat(entries).containsExactly(checkIds);
    assertThat(forkIdManager.getLatestForkId()).isNotNull();
    assertThat(forkIdManager.getLatestForkId()).isEqualTo(checkIds[4]);
  }

  @Test
  public void checkCorrectRinkebyForkIdHashesGenerated() {
    final Long[] forks = {1L, 2L, 3L, 1035301L, 3660663L, 4321234L};
    final String genHash = "0x6341fd3daf94b748c72ced5a5b26028f2474f5f00d824504e4fa37a75767e177";
    final ForkId[] checkIds = {
      new ForkId(
          Bytes.fromHexString("0x3b8e0691"),
          1L), // Unsynced, last Frontier, Homestead and first Tangerine block
      new ForkId(Bytes.fromHexString("0x60949295"), 2L), // Last Tangerine block
      new ForkId(Bytes.fromHexString("0x8bde40dd"), 3L), // First Spurious block
      new ForkId(Bytes.fromHexString("0xcb3a64bb"), 1035301L), // First Byzantium block
      new ForkId(Bytes.fromHexString("0x8d748b57"), 3660663L), // First Constantinople block
      new ForkId(Bytes.fromHexString("0xe49cab14"), 4321234L), // First Petersburg block
      new ForkId(Bytes.fromHexString("0xafec6b27"), 0L) // Today Petersburg block
    };
    final List<Long> list = Arrays.asList(forks);
    final ForkIdManager forkIdManager = new ForkIdManager(mockBlockchain(genHash, 0), list);
    final List<ForkId> entries = forkIdManager.getForkAndHashList();

    assertThat(entries).containsExactly(checkIds);
    assertThat(forkIdManager.getLatestForkId()).isNotNull();
    assertThat(forkIdManager.getLatestForkId()).isEqualTo(checkIds[6]);
  }

  @Test
  public void checkCorrectGoerliForkIdHashesGenerated() {
    final Long[] forks = {1561651L};
    final String genHash = "0xbf7e331f7f7c1dd2e05159666b3bf8bc7a8a3a9eb1d518969eab529dd9b88c1a";
    final ForkId[] checkIds = {
      new ForkId(Bytes.fromHexString("0xa3f5ab08"), 1561651L), // Frontier->Petersburg
      new ForkId(Bytes.fromHexString("0xc25efa5c"), 0L) // Istanbul
    };
    final List<Long> list = Arrays.asList(forks);
    final ForkIdManager forkIdManager = new ForkIdManager(mockBlockchain(genHash, 0), list);
    final List<ForkId> entries = forkIdManager.getForkAndHashList();

    assertThat(entries).containsExactly(checkIds);
    assertThat(forkIdManager.getLatestForkId()).isNotNull();
    assertThat(forkIdManager.getLatestForkId()).isEqualTo(checkIds[1]);
  }

  @Test
  public void check1PetersburgWithRemoteAnnouncingTheSame() {
    // 1 Local is mainnet Petersburg, remote announces the same. No future fork is announced.
    //  {7987396, ID{Hash: 0x668db0af, Next: 0}, nil},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7987396L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0x668db0af"), 0L));
    assertThat(result).isTrue();
    assertThat(forkIdManager.getLatestForkId()).isNotNull();
  }

  @Test
  public void check2PetersburgWithRemoteAnnouncingTheSameAndNextFork() {
    // 2 Local is mainnet Petersburg, remote announces the same. Remote also announces a next fork
    // at block 0xffffffff, but that is uncertain.
    //	{7987396, ID{Hash: 0x668db0af, Next: math.MaxUint64}, nil},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7987396L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0x668db0af"), Long.MAX_VALUE));
    assertThat(result).isTrue();
  }

  @Test
  public void check3ByzantiumAwareOfPetersburgRemoteUnawareOfPetersburg() {
    // 3 Local is mainnet currently in Byzantium only (so it's aware of Petersburg), remote
    // announces also Byzantium, but it's not yet aware of Petersburg (e.g. non updated node before
    // the fork).
    // In this case we don't know if Petersburg passed yet or not.
    //	{7279999, ID{Hash: 0xa00bc324, Next: 0}, nil},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7279999L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0xa00bc324"), 0L));
    assertThat(result).isTrue();
  }

  @Test
  public void check4ByzantiumAwareOfPetersburgRemoteAwareOfPetersburg() {
    // 4 Local is mainnet currently in Byzantium only (so it's aware of Petersburg), remote
    // announces  also Byzantium, and it's also aware of Petersburg (e.g. updated node before the
    // fork). We don't know if Petersburg passed yet (will pass) or not.
    //	{7279999, ID{Hash: 0xa00bc324, Next: 7280000}, nil},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7987396L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0xa00bc324"), 7280000L));
    assertThat(result).isTrue();
  }

  @Test
  public void check5ByzantiumAwareOfPetersburgRemoteAnnouncingUnknownFork() {
    // 5 Local is mainnet currently in Byzantium only (so it's aware of Petersburg), remote
    // announces also Byzantium, and it's also aware of some random fork (e.g. misconfigured
    // Petersburg).
    // As neither forks passed at neither nodes, they may mismatch, but we still connect for now.
    //	{7279999, ID{Hash: 0xa00bc324, Next: math.MaxUint64}, nil},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7279999), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0xa00bc324"), Long.MAX_VALUE));
    assertThat(result).isTrue();
  }

  @Test
  public void check6PetersburgWithRemoteAnnouncingByzantiumAwareOfPetersburg() {
    // 6 Local is mainnet Petersburg, remote announces Byzantium + knowledge about Petersburg.
    // Remote is simply out of sync, accept.
    //	{7987396, ID{Hash: 0x668db0af, Next: 7280000}, nil},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7987396L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0x668db0af"), 7280000L));
    assertThat(result).isTrue();
  }

  @Test
  public void check7PetersburgWithRemoteAnnouncingSpuriousAwareOfByzantiumRemoteMayNeedUpdate() {
    // 7 Local is mainnet Petersburg, remote announces Spurious + knowledge about Byzantium.
    // Remote is definitely out of sync. It may or may not need the Petersburg update, we don't know
    // yet.
    //	{7987396, ID{Hash: 0x3edd5b10, Next: 4370000}, nil},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7987396L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0x3edd5b10"), 4370000L));
    assertThat(result).isTrue();
  }

  @Test
  public void check8ByzantiumWithRemoteAnnouncingPetersburgLocalOutOfSync() {
    // 8 Local is mainnet Byzantium, remote announces Petersburg. Local is out of sync, accept.
    //	{7279999, ID{Hash: 0x668db0af, Next: 0}, nil},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 727999L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0x668db0af"), 0L));
    assertThat(result).isTrue();
  }

  @Test
  public void check9SpuriousWithRemoteAnnouncingByzantiumRemoteUnawareOfPetersburg() {
    // 9 Local is mainnet Spurious, remote announces Byzantium, but is not aware of Petersburg.
    // Local out of sync. Local also knows about a future fork, but that is uncertain yet.
    //	{4369999, ID{Hash: 0xa00bc324, Next: 0}, nil},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 4369999L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0xa00bc324"), 0L));
    assertThat(result).isTrue();
  }

  @Test
  public void check10PetersburgWithRemoteAnnouncingByzantiumRemoteUnawareOfAdditionalForks() {
    // 10 Local is mainnet Petersburg. remote announces Byzantium but is not aware of further forks.
    // Remote needs software update.
    //	{7987396, ID{Hash: 0xa00bc324, Next: 0}, ErrRemoteStale},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7987396L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0xa00bc324"), 0L));
    assertThat(result).isFalse();
  }

  @Test
  public void check11PetersburgWithRemoteAnnouncingPetersburgAndFutureForkLocalNeedsUpdate() {
    // 11 Local is mainnet Petersburg, and isn't aware of more forks. Remote announces Petersburg +
    // 0xffffffff. Local needs software update, reject.
    //	{7987396, ID{Hash: 0x5cddc0e1, Next: 0}, ErrLocalIncompatibleOrStale},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7987396L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0x5cddc0e1"), 0L));
    assertThat(result).isFalse();
  }

  @Test
  public void check12ByzantiumWithRemoteAnnouncingPetersburgAndFutureForkLocalNeedsUpdate() {
    // 12 Local is mainnet Byzantium, and is aware of Petersburg. Remote announces Petersburg +
    // 0xffffffff. Local needs software update, reject.
    //	{7279999, ID{Hash: 0x5cddc0e1, Next: 0}, ErrLocalIncompatibleOrStale},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7279999L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0x5cddc0e1"), 0L));
    assertThat(result).isFalse();
  }

  @Test
  public void check13ByzantiumWithRemoteAnnouncingRinkebyPetersburg() {
    // 13 Local is mainnet Petersburg, remote is Rinkeby Petersburg.
    //	{7987396, ID{Hash: 0xafec6b27, Next: 0}, ErrLocalIncompatibleOrStale},
    final List<Long> forkList = Arrays.asList(forksMainnet);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(mainnetGenHash, 7987396L), forkList);
    final Boolean result =
        forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0xafec6b27"), 0L));
    assertThat(result).isFalse();
  }

  @Test
  public void createAndDecodeRLP() {
    final ForkId forkIdEntry = new ForkId(Bytes.fromHexString("0xa00bc324"), 7280000L);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    forkIdEntry.writeTo(out);
    final Bytes bytesValue = out.encoded();
    final BytesValueRLPInput in = new BytesValueRLPInput(bytesValue, false);
    final ForkId decodedEntry = ForkIdManager.readFrom(in);
    assertThat(forkIdEntry).isEqualTo(decodedEntry);
  }

  @Test
  public void check1ZeroZeroProperRLPEncoding() {
    final ForkId forkIdEntry = new ForkId(Bytes.fromHexString("0x00000000"), 0);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    forkIdEntry.writeTo(out);
    final String str1 = "0xc6840000000080";
    final Bytes bytesValue = out.encoded();
    assertThat(str1).isEqualTo(bytesValue.toString());
    final BytesValueRLPInput in = new BytesValueRLPInput(bytesValue, false);
    final ForkId decodedEntry = ForkIdManager.readFrom(in);
    assertThat(forkIdEntry).isEqualTo(decodedEntry);
  }

  @Test
  public void check2ArbitraryProperRLPEncoding() {
    final ForkId forkIdEntry = new ForkId(Bytes.fromHexString("0xdeadbeef"), 0xbaddcafeL);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    forkIdEntry.writeTo(out);
    final String str1 = "0xca84deadbeef84baddcafe";
    final Bytes bytesValue = out.encoded();
    assertThat(str1).isEqualTo(bytesValue.toString());
    final BytesValueRLPInput in = new BytesValueRLPInput(bytesValue, false);
    final ForkId decodedEntry = ForkIdManager.readFrom(in);
    assertThat(forkIdEntry).isEqualTo(decodedEntry);
  }

  @Test
  public void check3MaximumsProperRLPEncoding() {
    final ForkId forkIdEntry = new ForkId(Bytes.fromHexString("0xffffffff"), 0xffffffffffffffffL);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    forkIdEntry.writeTo(out);
    final String str1 =
        "0xce84ffffffff88ffffffffffffffff"; // Check value supplied in EIP-2124 spec via GO lang
    final Bytes bytesValue = out.encoded();
    assertThat(str1).isEqualTo(bytesValue.toString());
    final BytesValueRLPInput in = new BytesValueRLPInput(bytesValue, false);
    final ForkId decodedEntry = ForkIdManager.readFrom(in);
    assertThat(forkIdEntry).isEqualTo(decodedEntry);
  }

  @Test
  public void checkConsortiumNetworkAlwaysAcceptPeersIfOnlyZeroForkBlocks() {
    final List<Long> list = Arrays.asList(0L, 0L, 0L, 0L);

    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(consortiumNetworkGenHash, 0), list);
    assertThat(forkIdManager.peerCheck(new ForkId(Bytes.random(32), 0))).isTrue();
  }

  @Test
  public void checkAlwaysAcceptPeersIfNull() {
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(consortiumNetworkGenHash, 0), emptyList());
    assertThat(forkIdManager.peerCheck((ForkId) null)).isTrue();
  }

  @Test
  public void assertThatConstructorParametersMustNotBeNull() {
    assertThatThrownBy(() -> new ForkIdManager(mockBlockchain(consortiumNetworkGenHash, 0), null))
        .isExactlyInstanceOf(NullPointerException.class);
  }
}

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
package org.hyperledger.besu.ethereum.p2p.discovery.dns;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.security.Security;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DNSResolverTest {
  @BeforeAll
  static void setup() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void enrTreeRootIsParsed() {
    final String txtRecord =
        "\"enrtree-root:v1 e=KVKZLGARGADDZSMCF65QQMEWLE l=FDXN3SN67NA5DKA4J2GOK7BVQI seq=919 sig=braPmdwMk-g65lQxums6hEy553s3bWMoecW0QQ0IdykIoM9i3We0bxFT0IDONPaFcRePcN-yaOpt8GBfeQ4qDAE\"";
    final DNSEntry entry = DNSResolver.readDNSEntry(txtRecord);
    assertThat(entry).isInstanceOf(DNSEntry.ENRTreeRoot.class);
    final DNSEntry.ENRTreeRoot enrTreeRoot = (DNSEntry.ENRTreeRoot) entry;
    assertThat(enrTreeRoot.enrRoot()).isEqualTo("KVKZLGARGADDZSMCF65QQMEWLE");
    assertThat(enrTreeRoot.linkRoot()).isEqualTo("FDXN3SN67NA5DKA4J2GOK7BVQI");
    assertThat(enrTreeRoot.seq()).isEqualTo(919);
  }

  @Test
  void enrTreeBranchIsParsed() {
    final String txtRecord =
        "\"enrtree-branch:HVKDJGU7SZMOAMNLBJYQBSKZTM,PVSVWO3NLKHTBAIWOY2NB67RFI,"
            + "6TCKCNWXNGBMNFTGSRKNRO4ERA,37NSKCRJVI5XRRHWLTHW4A6OX4,NV3IJMKDVQHHALY6MAVMPYN6ZU,"
            + "SZCFDMTYOERMIVOUXEWXSGDVEY,FZ26UT4LSG7D2NRX7SV6P3S6BI,7TWNYLCOQ7FEM4IG65WOTL4MVE,"
            + "6OJXGI7NJUESOLL2OZPS4B\" \"EC6Q,437FN4NSGMGFQLAXYWPX5JNACI,FCA7LN6NCO5IAWPG5FH7LX6XJA,"
            + "EYBOZ2NZSHDWDSNHV66XASXOHM,FUVRJMMMKJMCL4L4EBEOWCSOFA\"";
    final DNSEntry entry = DNSResolver.readDNSEntry(txtRecord);

    assertThat(entry).isInstanceOf(DNSEntry.ENRTree.class);
    assertThat(((DNSEntry.ENRTree) entry).entries())
        .containsExactly(
            "HVKDJGU7SZMOAMNLBJYQBSKZTM",
            "PVSVWO3NLKHTBAIWOY2NB67RFI",
            "6TCKCNWXNGBMNFTGSRKNRO4ERA",
            "37NSKCRJVI5XRRHWLTHW4A6OX4",
            "NV3IJMKDVQHHALY6MAVMPYN6ZU",
            "SZCFDMTYOERMIVOUXEWXSGDVEY",
            "FZ26UT4LSG7D2NRX7SV6P3S6BI",
            "7TWNYLCOQ7FEM4IG65WOTL4MVE",
            "6OJXGI7NJUESOLL2OZPS4B",
            "437FN4NSGMGFQLAXYWPX5JNACI",
            "FCA7LN6NCO5IAWPG5FH7LX6XJA",
            "EYBOZ2NZSHDWDSNHV66XASXOHM",
            "FUVRJMMMKJMCL4L4EBEOWCSOFA")
        .doesNotContain("EC6Q");
  }

  @Test
  void enrNodeIsParsed() throws Exception {
    final String txtRecord =
        "\"enr:-KO4QK1ecw-CGrDDZ4YwFrhgqctD0tWMHKJhUVxsS4um3aUFe3yBHRtVL9uYKk16DurN1IdSKTOB1zNCvjBybjZ_KAqGAYt"
            + "J5U8wg2V0aMfGhJsZKtCAgmlkgnY0gmlwhA_MtDmJc2VjcDI1NmsxoQNXD7fj3sscyOKBiHYy14igj1vJYWdKYZH7n3T8qRpIcY"
            + "RzbmFwwIN0Y3CCdl-DdWRwgnZf\"";
    final DNSEntry entry = DNSResolver.readDNSEntry(txtRecord);

    assertThat(entry).isInstanceOf(DNSEntry.ENRNode.class);
    final EthereumNodeRecord record = ((DNSEntry.ENRNode) entry).nodeRecord();
    assertThat(record.rlp())
        .isEqualTo(
            Bytes.fromHexString(
                "0xf8a3b840ad5e730f821ab0c367863016b860a9cb43d2d58"
                    + "c1ca261515c6c4b8ba6dda5057b7c811d1b552fdb982a4d7a0eeacdd48752293381d73342be30726e367f280a86018b49e54f3"
                    + "083657468c7c6849b192ad080826964827634826970840fccb43989736563703235366b31a103570fb7e3decb1cc8e28188763"
                    + "2d788a08f5bc961674a6191fb9f74fca91a487184736e6170c08374637082765f8375647082765f"));
    assertThat(record.publicKey())
        .isEqualTo(
            Bytes.fromHexString(
                "0x570fb7e3decb1cc8e281887632d788a08f5bc9616"
                    + "74a6191fb9f74fca91a4871957e3775d4bdfd4fdeff9bff92ad2f5965234d0e3c04ab4b85ab3eabd3193c35"));
    assertThat(record.ip())
        .isEqualTo(InetAddress.getByAddress(new byte[] {15, (byte) 204, (byte) 180, 57}));
    assertThat(record.tcp()).isEqualTo(Optional.of(30303));
    assertThat(record.udp()).isEqualTo(Optional.of(30303));
  }

  @Test
  void invalidEnrNodeIsIgnored() {
    final String txtRecord =
        "\"enr:-Lu4QMFaKrYJyYO06WxKfW8njcWATSuGJZV72zCIv6dTsihJJ4QM48Sxpi1xN--CI3MX4MTy-qhknkn9ESZF3_AOvhuGAZS-"
            + "S2T2g2V0aMrJhPxk7ASDEYwwgmlkgnY0gmlwhLkcZFKDaXA2kCABFegBEChSAAAAAAAAAAGJc2VjcDI1NmsxoQM9Tj7Od8vEHMK8"
            + "qCD8T0RHeN_LeLbbETpKFlfhx4UVzIRzbmFwwIN0Y3CCdl-DdWRwg\"";
    final DNSEntry entry = DNSResolver.readDNSEntry(txtRecord);

    assertThat(entry).isNull();
  }
}

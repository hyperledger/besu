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

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DNSEntryTest {
  @BeforeAll
  static void setup() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void enrTreeRootIsParsed() {
    final String txtRecord =
        "\"enrtree-root:v1 e=KVKZLGARGADDZSMCF65QQMEWLE l=FDXN3SN67NA5DKA4J2GOK7BVQI seq=919 sig=braPmdwMk-g65lQxums6hEy553s3bWMoecW0QQ0IdykIoM9i3We0bxFT0IDONPaFcRePcN-yaOpt8GBfeQ4qDAE\"";
    final DNSEntry entry = DNSEntry.readDNSEntry(txtRecord);
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
    final DNSEntry entry = DNSEntry.readDNSEntry(txtRecord);

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
}

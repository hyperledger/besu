/*
 * Copyright contributors to Besu.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class EthereumNodeRecordTest {

  @Test
  void buildFromRLP() throws Exception {
    final Bytes rlp =
        Bytes.fromHexString(
            "0xf8a3b84033b8a07e5c8e19dc8ac2529354b21a6c09e5516335eb57c383924aa0ca73434c0c65d8625eb05236e172fcc00d80e913506bde5446fb5c55ea2035380c97480a86018d56dc241083657468c7c6849b192ad0808269648276348269708441157e4389736563703235366b31a102a48c4c032f4c2e1b4007dd15b0d7046b60774f6bc38e2f52a8e0361c65e4234284736e6170c08374637082765f8375647082765f");
    // method under test
    final EthereumNodeRecord enr = EthereumNodeRecord.fromRLP(rlp);
    // expected values
    final InetAddress expectedIPAddr =
        InetAddress.getByAddress(Bytes.fromHexString("0x41157e43").toArrayUnsafe());
    final Bytes expectedPublicKey =
        Bytes.fromHexString(
            "0xa48c4c032f4c2e1b4007dd15b0d7046b60774f6bc38e2f52a8e0361c65e423424520b07898c59a8c9e85c440594ca734f23b7f2b906d5da54676eee6a1d64874");

    // assertions
    assertThat(enr.ip()).isEqualTo(expectedIPAddr);
    assertThat(enr.publicKey()).isEqualTo(expectedPublicKey);
    assertThat(enr.tcp()).isNotEmpty().contains(30303);
    assertThat(enr.udp()).isNotEmpty().contains(30303);
  }

  @Test
  void buildFromRLPWithSizeGreaterThan300() {
    final Bytes rlp = Bytes.random(301, new Random(1L));
    assertThatThrownBy(() -> EthereumNodeRecord.fromRLP(rlp))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Record too long");
  }
}

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

import java.util.Map;

import org.junit.jupiter.api.Test;

public class KVReaderTest {
  // copied from `dig all.holesky.ethdisco.net txt`
  private static final String txtRecord =
      "enrtree-root:v1 e=KVKZLGARGADDZSMCF65QQMEWLE l=FDXN3SN67NA5DKA4J2GOK7BVQI seq=919 sig=braPmdwMk-g65lQxums6hEy553s3bWMoecW0QQ0IdykIoM9i3We0bxFT0IDONPaFcRePcN-yaOpt8GBfeQ4qDAE";

  @Test
  void parseTXTRecord() {
    final Map<String, String> kv = KVReader.readKV(txtRecord);
    assertThat(kv)
        .containsEntry("enrtree-root", "v1")
        .containsEntry("e", "KVKZLGARGADDZSMCF65QQMEWLE")
        .containsEntry("l", "FDXN3SN67NA5DKA4J2GOK7BVQI")
        .containsEntry("seq", "919")
        .containsEntry(
            "sig",
            "braPmdwMk-g65lQxums6hEy553s3bWMoecW0QQ0IdykIoM9i3We0bxFT0IDONPaFcRePcN-yaOpt8GBfeQ4qDAE");
  }
}

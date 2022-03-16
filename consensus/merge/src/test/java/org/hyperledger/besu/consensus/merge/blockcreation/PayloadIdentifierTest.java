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
package org.hyperledger.besu.consensus.merge.blockcreation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.datatypes.Hash;

import java.util.List;

import org.junit.Test;

public class PayloadIdentifierTest {

  @Test
  public void serializesToEvenHexRepresentation() {

    List.of("0x150dd", "0xdeadbeef", Long.valueOf(Long.MAX_VALUE).toString(), "0").stream()
        .forEach(
            id -> {
              PayloadIdentifier idTest = new PayloadIdentifier(id);
              assertThat(idTest.toHexString().length() % 2).isEqualTo(0);
              assertThat(idTest.toShortHexString().length() % 2).isEqualTo(0);
              assertThat(idTest.serialize().length() % 2).isEqualTo(0);
            });
  }

  @Test
  public void conversionCoverage() {
    var idTest = PayloadIdentifier.forPayloadParams(Hash.ZERO, 1337L);
    assertThat(new PayloadIdentifier(idTest.getAsBigInteger().longValue())).isEqualTo(idTest);
    assertThat(new PayloadIdentifier(idTest.getAsBigInteger().longValue())).isEqualTo(idTest);
  }
}

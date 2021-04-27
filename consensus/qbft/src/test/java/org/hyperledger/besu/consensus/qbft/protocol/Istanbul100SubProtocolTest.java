/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.consensus.qbft.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class Istanbul100SubProtocolTest {

  @Test
  public void messageSpaceReportsCorrectly() {
    final Istanbul100SubProtocol subProt = new Istanbul100SubProtocol();

    assertThat(subProt.messageSpace(1)).isEqualTo(0x16);
  }

  @Test
  public void allIbftMessageTypesAreRecognisedAsValidByTheSubProtocol() {
    final Istanbul100SubProtocol subProt = new Istanbul100SubProtocol();

    assertThat(subProt.isValidMessageCode(1, 0x12)).isTrue();
    assertThat(subProt.isValidMessageCode(1, 0x13)).isTrue();
    assertThat(subProt.isValidMessageCode(1, 0x14)).isTrue();
    assertThat(subProt.isValidMessageCode(1, 0x15)).isTrue();
  }

  @Test
  public void invalidMessageTypesAreNotAcceptedByTheSubprotocol() {
    final Istanbul100SubProtocol subProt = new Istanbul100SubProtocol();

    assertThat(subProt.isValidMessageCode(1, 0x16)).isFalse();
  }
}

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
package org.hyperledger.besu.consensus.ibft.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class IbftSubProtocolTest {

  @Test
  public void messageSpaceReportsCorrectly() {
    final IbftSubProtocol subProt = new IbftSubProtocol();

    assertThat(subProt.messageSpace(1)).isEqualTo(4);
  }

  @Test
  public void allIbftMessageTypesAreRecognisedAsValidByTheSubProtocol() {
    final IbftSubProtocol subProt = new IbftSubProtocol();

    assertThat(subProt.isValidMessageCode(1, 0)).isTrue();
    assertThat(subProt.isValidMessageCode(1, 1)).isTrue();
    assertThat(subProt.isValidMessageCode(1, 2)).isTrue();
    assertThat(subProt.isValidMessageCode(1, 3)).isTrue();
  }

  @Test
  public void invalidMessageTypesAreNotAcceptedByTheSubprotocol() {
    final IbftSubProtocol subProt = new IbftSubProtocol();

    assertThat(subProt.isValidMessageCode(1, 4)).isFalse();
  }
}

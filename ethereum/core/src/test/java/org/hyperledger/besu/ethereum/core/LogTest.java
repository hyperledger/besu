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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.evm.log.Log;

import org.junit.Test;

public class LogTest {

  @Test
  public void toFromRlp() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Log log = gen.log();
    final Log copy = Log.readFrom(RLP.input(RLP.encode(log::writeTo)));
    assertThat(copy).isEqualTo(log);
  }
}

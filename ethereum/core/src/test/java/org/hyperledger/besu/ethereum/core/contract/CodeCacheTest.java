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

package org.hyperledger.besu.ethereum.core.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.vm.Code;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.BeforeClass;
import org.junit.Test;

public class CodeCacheTest {

  @BeforeClass
  public static void resetConfig() {
    CodeCache.destroy(); // tests run in parallel, often with defaults
  }

  @Test
  public void testScale() {
    Bytes contractCode = Bytes.fromHexString("0xDEADBEEF");
    CodeScale scale = new CodeScale();
    Bytes32 address = Bytes32.fromHexString("0xB0B0FACE");
    int weight =
        scale.weigh(ImmutableCodeHash.of(Hash.hash(address), contractCode), new Code(contractCode));
    assertThat(weight).isEqualTo(4);
  }

  @Test
  public void testLoader() {
    Bytes contractCode = Bytes.fromHexString("0xDEADBEEF");
    CodeLoader loader = new CodeLoader();
    Bytes32 address = Bytes32.fromHexString("0xB0B0FACE");
    ImmutableCodeHash key = ImmutableCodeHash.of(Hash.hash(address), contractCode);
    Code loaded = loader.load(key);
    assertThat(loaded).isNotNull();
    assertThat(loaded.getBytes()).isEqualTo(key.contract());
  }
}

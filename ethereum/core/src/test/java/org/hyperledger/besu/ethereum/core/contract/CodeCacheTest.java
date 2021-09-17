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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.vm.Code;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CodeCacheTest {

  @Test
  public void testScale(){
      Bytes contractCode = Bytes.fromHexString("0xDEADBEEF");
    CodeScale scale = new CodeScale();
    int weight = scale.weigh(new CodeHash(contractCode), new Code(contractCode));
    assertThat(weight).isEqualTo(4);
  }

  @Test
  public void testLoader(){
      Bytes contractCode = Bytes.fromHexString("0xDEADBEEF");
      CodeLoader loader = new CodeLoader();
      CodeHash key = new CodeHash(contractCode);
      Code loaded = loader.load(key);
      assertThat(loaded).isNotNull();
      assertThat(loaded.getBytes()).isEqualTo(key.getContract());
  }
}

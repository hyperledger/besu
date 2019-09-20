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
package org.hyperledger.besu.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.controller.KeyPairUtil;

import java.io.File;

import org.junit.Test;

public class KeyPairUtilTest {

  @Test
  public void shouldLoadValidKeyPair() throws Exception {
    assertThat(
            KeyPairUtil.loadKeyPair(
                new File(this.getClass().getResource("/validPrivateKey.txt").toURI())))
        .isNotNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldNotLoadInvalidKeyPair() throws Exception {
    KeyPairUtil.loadKeyPair(
        new File(this.getClass().getResource("/invalidPrivateKey.txt").toURI()));
  }
}

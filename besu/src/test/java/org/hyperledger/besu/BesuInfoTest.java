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
package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class BesuInfoTest {

  /**
   * Ethstats wants a version string like &lt;foo&gt/v&lt;bar&gt/&lt;baz&gt/&lt;bif&gt. Foo is the
   * client identity (besu, Geth, Parity, etc). Bar is the version, in semantic version form
   * (1.2.3-whatever), baz is OS and chip architecture, and bif is "compiler" - which we use as JVM
   * info.
   */
  @Test
  public void versionStringIsEthstatsFriendly() {
    assertThat(BesuInfo.version()).matches("[^/]+/v(\\d+\\.\\d+\\.\\d+[^/]*|null)/[^/]+/[^/]+");
  }

  /**
   * Ethstats wants a version string like &lt;foo&gt/v&lt;bar&gt/&lt;baz&gt/&lt;bif&gt. Foo is the
   * client identity (besu, Geth, Parity, etc). Bar is the version, in semantic version form
   * (1.2.3-whatever), baz is OS and chip architecture, and bif is "compiler" - which we use as JVM
   * info.
   */
  @Test
  public void noIdentityNodeNameIsEthstatsFriendly() {
    assertThat(BesuInfo.nodeName(Optional.empty()))
        .matches("[^/]+/v(\\d+\\.\\d+\\.\\d+[^/]*|null)/[^/]+/[^/]+");
  }

  /**
   * Ethstats also accepts a version string like
   * &lt;foo&gt/%lt;qux&gt;/v&lt;bar&gt/&lt;baz&gt/&lt;bif&gt. Foo is the client identity (besu,
   * Geth, Parity, etc). Qux is user identity (PegaSysEng, Yes-EIP-1679, etc) Bar is the version, in
   * semantic version form (1.2.3-whatever), baz is OS and chip architecture, and bif is "compiler"
   * - which we use as JVM info.
   */
  @Test
  public void userIdentityNodeNameIsEthstatsFriendly() {
    assertThat(BesuInfo.nodeName(Optional.of("TestUserIdentity")))
        .matches("[^/]+/[^/]+/v(\\d+\\.\\d+\\.\\d+[^/]*|null)/[^/]+/[^/]+");
  }
}

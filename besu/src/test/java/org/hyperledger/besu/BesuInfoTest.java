/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

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
}

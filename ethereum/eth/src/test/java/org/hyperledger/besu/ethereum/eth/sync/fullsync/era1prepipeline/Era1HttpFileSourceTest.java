/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.fullsync.era1prepipeline;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Era1HttpFileSourceTest {
  private static final URI testFilePath = URI.create("https://mainnet.era1.nimbus.team/");

  @Test
  public void testHasNext() {
    Era1HttpFileSource era1FileSource = new Era1HttpFileSource(testFilePath, 0);
    Assertions.assertTrue(era1FileSource.hasNext());
  }

  @Test
  public void testNext() throws URISyntaxException {
    Era1HttpFileSource era1FileSource = new Era1HttpFileSource(testFilePath, 0);
    Assertions.assertEquals(
        testFilePath.resolve("mainnet-00000-5ec1ffb8.era1"), era1FileSource.next());
  }
}

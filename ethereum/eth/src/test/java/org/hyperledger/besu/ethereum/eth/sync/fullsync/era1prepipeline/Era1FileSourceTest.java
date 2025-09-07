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
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Era1FileSourceTest {
  private static URI testFilePath;

  @BeforeAll
  public static void setupClass() throws URISyntaxException {
    testFilePath =
        Path.of(
                Era1FileSourceTest.class
                    .getClassLoader()
                    .getResource("mainnet-00000-5ec1ffb8.era1")
                    .toURI())
            .getParent()
            .toUri();
  }

  @Test
  public void testHasNext() {
    Era1FileSource era1FileSource = new Era1FileSource(testFilePath, 0);
    Assertions.assertTrue(era1FileSource.hasNext());
  }

  @Test
  public void testNext() throws URISyntaxException {
    Era1FileSource era1FileSource = new Era1FileSource(testFilePath, 0);
    URI expectedResult =
        Era1FileSourceTest.class
            .getClassLoader()
            .getResource("mainnet-00000-5ec1ffb8.era1")
            .toURI();
    Assertions.assertEquals(expectedResult, era1FileSource.next());
  }

  @Test
  public void testHasNextAfterNextConsumesLastItem() {
    Era1FileSource era1FileSource = new Era1FileSource(testFilePath, 0);
    era1FileSource.next();
    Assertions.assertFalse(era1FileSource.hasNext());
  }

  @Test
  public void testHasNextWhenInitializedToAbsentFile() {
    Era1FileSource era1FileSource = new Era1FileSource(testFilePath, 8192);
    Assertions.assertFalse(era1FileSource.hasNext());
  }

  @Test
  public void testNextWhenInitializedToAbsentFile() {
    Era1FileSource era1FileSource = new Era1FileSource(testFilePath, 8192);
    Assertions.assertNull(era1FileSource.next());
  }
}

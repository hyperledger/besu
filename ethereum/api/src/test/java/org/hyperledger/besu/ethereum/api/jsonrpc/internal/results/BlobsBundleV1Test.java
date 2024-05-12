/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.InvalidParameterException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BlobsBundleV1Test {
  @Test
  public void blobsBundleV1MustHaveSameNumberOfElements() {
    String actualMessage =
        assertThrows(
                InvalidParameterException.class,
                () -> new BlobsBundleV1(List.of(""), List.of(""), List.of()))
            .getMessage();
    final String expectedMessage = "There must be an equal number of blobs, commitments and proofs";
    assertThat(actualMessage).isEqualTo(expectedMessage);
  }
}

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
package org.hyperledger.besu.ethereum.core.kzg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.InvalidParameterException;
import java.util.List;

import org.junit.jupiter.api.Test;

public class BlobsWithCommitmentsTest {
  @Test
  public void blobsWithCommitmentsMustHaveAtLeastOneBlob() {
    String actualMessage =
        assertThrows(
                InvalidParameterException.class,
                () ->
                    new BlobsWithCommitments(
                        BlobProofBundle.VERSION_0_KZG_PROOFS,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()))
            .getMessage();
    final String expectedMessage =
        "There needs to be a minimum of one blob in a blob transaction with commitments";
    assertThat(actualMessage).isEqualTo(expectedMessage);
  }
}

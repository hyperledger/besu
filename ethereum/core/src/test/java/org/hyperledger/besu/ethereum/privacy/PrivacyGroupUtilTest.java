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
package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class PrivacyGroupUtilTest {

  private static final String ENCLAVE_PUBLIC_KEY_1 = "negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=";
  private static final String ENCLAVE_PUBLIC_KEY_2 = "g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw=";
  private static final String ENCLAVE_PUBLIC_KEY_3 = "6fg8q5rWMBoAT2oIiU3tYJbk4b7oAr7dxaaVY7TeM3U=";

  // This is the expected generated legacy privacy group id for a privacy group containing the
  // enclave public keys enclave_public_key_1, enclave_public_key_2 and enclave_public_key_3
  private static final String EXPECTED_LEGACY_PRIVACY_GROUP_ID =
      "/xzRjCLioUBkm5LYuzll61GXyrD5x7bvXzQk/ovJA/4=";

  @Test
  public void calculatesPrivacyGroupIdWithPrivateFromAndEmptyPrivateFor() {
    final String expected = "kAbelwaVW7okoEn1+okO+AbA4Hhz/7DaCOWVQz9nx5M=";
    assertThat(privacyGroupId(ENCLAVE_PUBLIC_KEY_1)).isEqualTo(expected);
  }

  @Test
  public void calculatesSamePrivacyGroupIdForDuplicateValues() {
    assertThat(
            privacyGroupId(
                ENCLAVE_PUBLIC_KEY_2,
                ENCLAVE_PUBLIC_KEY_1,
                ENCLAVE_PUBLIC_KEY_3,
                ENCLAVE_PUBLIC_KEY_1))
        .isEqualTo(EXPECTED_LEGACY_PRIVACY_GROUP_ID);
    assertThat(
            privacyGroupId(
                ENCLAVE_PUBLIC_KEY_2,
                ENCLAVE_PUBLIC_KEY_1,
                ENCLAVE_PUBLIC_KEY_1,
                ENCLAVE_PUBLIC_KEY_3))
        .isEqualTo(EXPECTED_LEGACY_PRIVACY_GROUP_ID);
    assertThat(
            privacyGroupId(
                ENCLAVE_PUBLIC_KEY_2,
                ENCLAVE_PUBLIC_KEY_1,
                ENCLAVE_PUBLIC_KEY_3,
                ENCLAVE_PUBLIC_KEY_3))
        .isEqualTo(EXPECTED_LEGACY_PRIVACY_GROUP_ID);
  }

  @Test
  public void calculatesSamePrivacyGroupIdForPrivateForInDifferentOrders() {
    assertThat(privacyGroupId(ENCLAVE_PUBLIC_KEY_1, ENCLAVE_PUBLIC_KEY_2, ENCLAVE_PUBLIC_KEY_3))
        .isEqualTo(EXPECTED_LEGACY_PRIVACY_GROUP_ID);
    assertThat(privacyGroupId(ENCLAVE_PUBLIC_KEY_1, ENCLAVE_PUBLIC_KEY_3, ENCLAVE_PUBLIC_KEY_2))
        .isEqualTo(EXPECTED_LEGACY_PRIVACY_GROUP_ID);
    assertThat(privacyGroupId(ENCLAVE_PUBLIC_KEY_2, ENCLAVE_PUBLIC_KEY_1, ENCLAVE_PUBLIC_KEY_3))
        .isEqualTo(EXPECTED_LEGACY_PRIVACY_GROUP_ID);
    assertThat(privacyGroupId(ENCLAVE_PUBLIC_KEY_2, ENCLAVE_PUBLIC_KEY_3, ENCLAVE_PUBLIC_KEY_1))
        .isEqualTo(EXPECTED_LEGACY_PRIVACY_GROUP_ID);
    assertThat(privacyGroupId(ENCLAVE_PUBLIC_KEY_3, ENCLAVE_PUBLIC_KEY_1, ENCLAVE_PUBLIC_KEY_2))
        .isEqualTo(EXPECTED_LEGACY_PRIVACY_GROUP_ID);
    assertThat(privacyGroupId(ENCLAVE_PUBLIC_KEY_3, ENCLAVE_PUBLIC_KEY_2, ENCLAVE_PUBLIC_KEY_1))
        .isEqualTo(EXPECTED_LEGACY_PRIVACY_GROUP_ID);
  }

  private String privacyGroupId(final String privateFrom, final String... privateFor) {
    return PrivacyGroupUtil.calculateEeaPrivacyGroupId(
            Bytes.fromBase64String(privateFrom),
            Arrays.stream(privateFor).map(Bytes::fromBase64String).collect(Collectors.toList()))
        .toBase64String();
  }
}

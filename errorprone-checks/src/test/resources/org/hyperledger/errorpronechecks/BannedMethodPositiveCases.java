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

package org.hyperledger.errorpronechecks;

import com.google.common.base.Objects;

public class BannedMethodPositiveCases {

  public void callsObjectsEquals() throws Exception {
    // BUG: Diagnostic contains:  Do not use com.google.common.base.Objects methods, use
    // java.util.Objects methods instead.
    Objects.equal("1", "1");
  }

  public void callsObjectsHashCode() throws Exception {
    // BUG: Diagnostic contains:  Do not use com.google.common.base.Objects methods, use
    // java.util.Objects methods instead.
    Objects.hashCode("1", "1");
  }

  public void usesJUnitAssertions() throws Exception {
    // BUG: Diagnostic contains: Do not use junit assertions. Use assertj assertions instead.
    org.junit.Assert.assertEquals(1, 1);
    // BUG: Diagnostic contains: Do not use junit assertions. Use assertj assertions instead.
    org.junit.Assert.assertNotEquals(1, 2);
    // BUG: Diagnostic contains: Do not use junit assertions. Use assertj assertions instead.
    org.junit.Assert.assertTrue(true);
    // BUG: Diagnostic contains: Do not use junit assertions. Use assertj assertions instead.
    org.junit.Assert.assertFalse(false);
    // BUG: Diagnostic contains: Do not use junit assertions. Use assertj assertions instead.
    org.junit.Assert.assertNull(null);
    // BUG: Diagnostic contains: Do not use junit assertions. Use assertj assertions instead.
    org.junit.Assert.assertNotNull("foo");
    // BUG: Diagnostic contains: Do not use junit assertions. Use assertj assertions instead.
    org.junit.Assert.assertArrayEquals(new int[] {1}, new int[] {1});
  }
}

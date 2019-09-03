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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy.account;

import java.net.URL;

/** Supplier of known funded accounts defined in dev.json */
public class PrivacyAccountResolver {

  public static final PrivacyAccount ALICE =
      PrivacyAccount.create(
          resolveResource("key"),
          resolveResource("orion_key_0.pub"),
          resolveResource("orion_key_0.key"));

  public static final PrivacyAccount BOB =
      PrivacyAccount.create(
          resolveResource("key1"),
          resolveResource("orion_key_1.pub"),
          resolveResource("orion_key_1.key"));

  public static final PrivacyAccount CHARLIE =
      PrivacyAccount.create(
          resolveResource("key2"),
          resolveResource("orion_key_2.pub"),
          resolveResource("orion_key_2.key"));

  private static URL resolveResource(final String resource) {
    return PrivacyAccountResolver.class.getClassLoader().getResource(resource);
  }

  public PrivacyAccountResolver() {}

  public PrivacyAccount resolve(final Integer account) {
    switch (account) {
      case 0:
        return ALICE;
      case 1:
        return BOB;
      case 2:
        return CHARLIE;
      default:
        throw new RuntimeException("Unknown privacy account");
    }
  }
}

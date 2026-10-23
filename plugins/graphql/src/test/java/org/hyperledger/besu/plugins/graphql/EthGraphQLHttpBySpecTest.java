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
package org.hyperledger.besu.plugins.graphql;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class EthGraphQLHttpBySpecTest extends AbstractEthGraphQLHttpServiceTest {

  public static Stream<Arguments> specs() {
    return GraphQLSpecFixtures.list(GraphQLSpecFixtures.SPEC_DIRECTORY).stream().map(Arguments::of);
  }

  public static Stream<Arguments> extraSpecs() {
    return GraphQLSpecFixtures.list(GraphQLSpecFixtures.EXTRA_DIRECTORY).stream()
        .map(Arguments::of);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("specs")
  void graphQLCallWithSpecFile(final String specFile) throws Exception {
    GraphQLSpecFixtures.assertFixture(client, baseUrl, specFile);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("extraSpecs")
  void graphQLCallWithExtraSpecFile(final String specFile) throws Exception {
    GraphQLSpecFixtures.assertFixture(client, baseUrl, specFile);
  }
}

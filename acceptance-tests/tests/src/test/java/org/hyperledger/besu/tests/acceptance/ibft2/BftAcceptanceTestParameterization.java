/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.ibft2;

import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeFactory;

import java.util.List;

import org.junit.Ignore;

@Ignore("Must be ignored to prevent it being executed by JUnit")
public class BftAcceptanceTestParameterization {

  public static List<BftAcceptanceTestParameterization> FACTORIES =
      List.of(
          new BftAcceptanceTestParameterization(
              BesuNodeFactory::createIbft2Node, BesuNodeFactory::createIbft2NodeWithValidators),
          new BftAcceptanceTestParameterization(
              BesuNodeFactory::createQbftNode, BesuNodeFactory::createQbftNodeWithValidators));

  @FunctionalInterface
  public interface NodeCreatorBiFunction {

    BesuNode create(BesuNodeFactory factory, String name) throws Exception;
  }

  @FunctionalInterface
  public interface NodeCreatorWithValidatorsFunction {

    BesuNode create(BesuNodeFactory factory, String name, String[] validators) throws Exception;
  }

  private final NodeCreatorBiFunction creatorFn;
  private final NodeCreatorWithValidatorsFunction createorWithValidatorFn;

  public BftAcceptanceTestParameterization(
      final NodeCreatorBiFunction creatorFn,
      final NodeCreatorWithValidatorsFunction createorWithValidatorFn) {
    this.creatorFn = creatorFn;
    this.createorWithValidatorFn = createorWithValidatorFn;
  }

  public BesuNode createNode(BesuNodeFactory factory, String name) throws Exception {
    return creatorFn.create(factory, name);
  }

  public BesuNode createNodeWithValidators(
      BesuNodeFactory factory, String name, String[] validators) throws Exception {
    return createorWithValidatorFn.create(factory, name, validators);
  }
}

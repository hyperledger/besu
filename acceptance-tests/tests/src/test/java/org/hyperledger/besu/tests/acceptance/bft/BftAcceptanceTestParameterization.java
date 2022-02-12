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
package org.hyperledger.besu.tests.acceptance.bft;

import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeFactory;

import java.util.ArrayList;
import java.util.List;

public class BftAcceptanceTestParameterization {

  public static List<Object[]> getFactories() {
    final List<Object[]> ret = new ArrayList<>();
    ret.addAll(
        List.of(
            new Object[] {
              "ibft2",
              new BftAcceptanceTestParameterization(
                  BesuNodeFactory::createIbft2Node, BesuNodeFactory::createIbft2NodeWithValidators)
            },
            new Object[] {
              "qbft",
              new BftAcceptanceTestParameterization(
                  BesuNodeFactory::createQbftNode, BesuNodeFactory::createQbftNodeWithValidators)
            }));
    return ret;
  }

  @FunctionalInterface
  public interface NodeCreator {

    BesuNode create(BesuNodeFactory factory, String name) throws Exception;
  }

  @FunctionalInterface
  public interface NodeWithValidatorsCreator {

    BesuNode create(BesuNodeFactory factory, String name, String[] validators) throws Exception;
  }

  private final NodeCreator creatorFn;
  private final NodeWithValidatorsCreator createorWithValidatorFn;

  public BftAcceptanceTestParameterization(
      final NodeCreator creatorFn, final NodeWithValidatorsCreator createorWithValidatorFn) {
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

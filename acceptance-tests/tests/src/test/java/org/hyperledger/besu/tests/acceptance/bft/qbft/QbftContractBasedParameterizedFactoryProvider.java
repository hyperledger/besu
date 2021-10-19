/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.tests.acceptance.bft.qbft;

import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeFactory;

import java.util.ArrayList;
import java.util.List;

public class QbftContractBasedParameterizedFactoryProvider {

  private final NodeCreator creatorFn;
  private final NodeWithContractBasedValidatorsCreator creatorWithContractBasedValidatorsFn;

  public QbftContractBasedParameterizedFactoryProvider(
      final NodeCreator creatorFn,
      final NodeWithContractBasedValidatorsCreator creatorWithContractBasedValidatorsFn) {
    this.creatorFn = creatorFn;
    this.creatorWithContractBasedValidatorsFn = creatorWithContractBasedValidatorsFn;
  }

  public BesuNode createNode(BesuNodeFactory factory, String name) throws Exception {
    return creatorFn.create(factory, name);
  }

  public BesuNode createNodeWithValidators(
      BesuNodeFactory factory, String name, String[] validators) throws Exception {
    return creatorWithContractBasedValidatorsFn.create(factory, name, validators);
  }

  public static List<Object[]> getFactories() {
    final List<Object[]> ret = new ArrayList<>();
    ret.add(
        new Object[] {
          "qbft-contract-validators",
          new QbftContractBasedParameterizedFactoryProvider(
              BesuNodeFactory::createQbftNode,
              BesuNodeFactory::createQbftNodeWithContractBasedValidators)
        });
    return ret;
  }

  @FunctionalInterface
  public interface NodeCreator {
    BesuNode create(BesuNodeFactory factory, String name) throws Exception;
  }

  @FunctionalInterface
  public interface NodeWithContractBasedValidatorsCreator {
    BesuNode create(BesuNodeFactory factory, String name, String[] validators) throws Exception;
  }
}

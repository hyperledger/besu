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
package org.hyperledger.besu.consensus.qbft.core.validation;

import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory;
import org.hyperledger.besu.datatypes.Address;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

public class QbftNodeList {

  public static QbftNodeList createNodes(final int count) {
    final List<QbftNode> nodes = Lists.newArrayList();

    for (int i = 0; i < count; i++) {
      nodes.add(QbftNode.create());
    }
    return new QbftNodeList(nodes);
  }

  private final List<QbftNode> nodes;

  public QbftNodeList(final List<QbftNode> nodes) {
    this.nodes = nodes;
  }

  public List<Address> getNodeAddresses() {
    return nodes.stream().map(QbftNode::getAddress).collect(Collectors.toList());
  }

  public MessageFactory getMessageFactory(final int index) {
    return nodes.get(index).getMessageFactory();
  }

  public Collection<QbftNode> getNodes() {
    return nodes;
  }

  public QbftNode getNode(final int index) {
    return nodes.get(index);
  }
}

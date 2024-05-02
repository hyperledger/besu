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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

/**
 * An attached proof of work validation rule that wraps the detached version of the same. Suitable
 * for use in block validator stacks supporting the merge.
 */
public class AttachedComposedFromDetachedRule implements AttachedBlockHeaderValidationRule {

  private final DetachedBlockHeaderValidationRule detachedRule;

  public AttachedComposedFromDetachedRule(final DetachedBlockHeaderValidationRule detachedRule) {
    this.detachedRule = detachedRule;
  }

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext protocolContext) {
    return detachedRule.validate(header, parent);
  }
}

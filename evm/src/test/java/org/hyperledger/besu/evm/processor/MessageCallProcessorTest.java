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
package org.hyperledger.besu.evm.processor;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Nested
@ExtendWith(MockitoExtension.class)
class MessageCallProcessorTest extends AbstractMessageProcessorTest<MessageCallProcessor> {

  @Mock EVM evm;
  @Mock PrecompileContractRegistry precompileContractRegistry;

  @Override
  protected MessageCallProcessor getAbstractMessageProcessor() {
    return new MessageCallProcessor(evm, precompileContractRegistry);
  }
}

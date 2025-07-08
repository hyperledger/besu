/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.debug;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.util.List;

public class DebugGetBadBlocks implements Transaction<List<DebugRequestFactory.BadBlock>> {

  @Override
  public List<DebugRequestFactory.BadBlock> execute(final NodeRequests node) {
    try {
      final var resp = node.debug().getBadBlocks().send();
      assertThat(resp).isNotNull();
      assertThat(resp.hasError()).isFalse();
      return resp.getResult();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}

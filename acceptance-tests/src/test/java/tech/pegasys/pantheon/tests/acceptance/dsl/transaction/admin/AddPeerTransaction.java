/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.admin;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.net.URI;

import org.web3j.protocol.core.Response;

public class AddPeerTransaction implements Transaction<Boolean> {

  private final URI peer;

  public AddPeerTransaction(final URI peer) {
    this.peer = peer;
  }

  @Override
  public Boolean execute(final NodeRequests node) {
    try {
      final Response<Boolean> resp = node.admin().adminAddPeer(peer).send();
      assertThat(resp).isNotNull();
      assertThat(resp.hasError()).isFalse();
      return resp.getResult();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}

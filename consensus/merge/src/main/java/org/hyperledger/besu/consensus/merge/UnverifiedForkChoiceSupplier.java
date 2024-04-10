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
package org.hyperledger.besu.consensus.merge;

import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Unverified forkchoice supplier. */
public class UnverifiedForkChoiceSupplier
    implements Supplier<Optional<ForkChoiceEvent>>, UnverifiedForkChoiceListener {
  private static final Logger LOG = LoggerFactory.getLogger(UnverifiedForkChoiceSupplier.class);

  private volatile Optional<ForkChoiceEvent> maybeLastForkChoiceUpdate = Optional.empty();

  @Override
  public void onNewUnverifiedForkChoice(final ForkChoiceEvent event) {
    maybeLastForkChoiceUpdate = Optional.of(event);
    LOG.debug("New forkchoice announced {}", maybeLastForkChoiceUpdate);
  }

  @Override
  public Optional<ForkChoiceEvent> get() {
    return maybeLastForkChoiceUpdate;
  }
}

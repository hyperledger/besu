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
package org.hyperledger.besu.nat.core;

import org.hyperledger.besu.nat.core.domain.NATMethod;
import org.hyperledger.besu.nat.core.domain.NATPortMapping;
import org.hyperledger.besu.nat.core.domain.NATServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractNATSystem implements NATSystem {
  protected static final Logger LOG = LogManager.getLogger();

  protected final NATMethod natMethod;
  protected final AtomicBoolean started = new AtomicBoolean();

  protected AbstractNATSystem(final NATMethod natMethod) {
    this.natMethod = natMethod;
  }

  public abstract void doStart();

  public abstract void doStop();

  @Override
  public NATMethod getNatMethod() {
    return natMethod;
  }

  @Override
  public boolean isStarted() {
    return started.get();
  }

  @Override
  public CompletableFuture<String> getLocalIPAddress() {
    final CompletableFuture<String> future = new CompletableFuture<>();
    Executors.newCachedThreadPool()
        .submit(
            () -> {
              try {
                future.complete(InetAddress.getLocalHost().getHostAddress());
              } catch (UnknownHostException e) {
                future.completeExceptionally(e);
              }
            });
    return future;
  }

  @Override
  public void start() {
    if (started.compareAndSet(false, true)) {
      doStart();
    } else {
      LOG.warn("Attempt to start an already-started {}", getClass().getSimpleName());
    }
  }

  @Override
  public void stop() {
    if (started.compareAndSet(true, false)) {
      doStop();
    } else {
      LOG.warn("Attempt to stop an already-stopped {}", getClass().getSimpleName());
    }
  }

  @Override
  public NATPortMapping getPortMapping(
      final NATServiceType serviceType, final NetworkProtocol networkProtocol) {
    try {
      final List<NATPortMapping> natPortMappings =
          getPortMappings().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      final Optional<NATPortMapping> foundPortMapping =
          natPortMappings.stream()
              .filter(
                  c ->
                      c.getNatServiceType().equals(serviceType)
                          && c.getProtocol().equals(networkProtocol))
              .findFirst();
      return foundPortMapping.orElseThrow();
    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException(
          String.format("Required service type not found : %s %s", serviceType, networkProtocol));
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(
          String.format("Unable to retrieve the service type : %s", serviceType.toString()));
    }
  }
}

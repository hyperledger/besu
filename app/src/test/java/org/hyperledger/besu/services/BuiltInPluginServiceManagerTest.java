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
package org.hyperledger.besu.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.api.pluginadapter.NetworkEndpointServiceImpl;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.NetworkEndpointService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.StorageService;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class BuiltInPluginServiceManagerTest {

  @Command
  static final class SimpleCommand {
    @Option(names = "--existing")
    String existingOption = "defaultexisting";
  }

  static final class BuiltInOptions {
    @Option(names = "--graphql-http-enabled")
    Boolean enabled = false;
  }

  private final PicoCLIOptions contextCliOptions = mock(PicoCLIOptions.class);
  private ServiceManager context;
  private CommandLine commandLine;
  private BuiltInPluginServiceManager serviceManager;

  @BeforeEach
  public void setUp() {
    context = new ServiceManager.SimpleServiceManager();
    context.addService(PicoCLIOptions.class, contextCliOptions);
    commandLine = new CommandLine(new SimpleCommand());
    serviceManager = new BuiltInPluginServiceManager(context, commandLine, Set.of("--graphql-"));
  }

  @Test
  public void picoCLIOptionsAcceptAdditionalPrefixAndBindToCommandLine() {
    final PicoCLIOptions cliOptions = serviceManager.getService(PicoCLIOptions.class).orElseThrow();
    assertThat(cliOptions).isNotSameAs(contextCliOptions);

    final BuiltInOptions options = new BuiltInOptions();
    cliOptions.addPicoCLIOptions("graphql", options);
    commandLine.parseArgs("--graphql-http-enabled");

    assertThat(options.enabled).isTrue();
  }

  @Test
  public void contextPicoCLIOptionsAreUnchanged() {
    assertThat(context.getService(PicoCLIOptions.class)).containsSame(contextCliOptions);
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(
            () ->
                new PicoCLIOptionsImpl(commandLine)
                    .addPicoCLIOptions("graphql", new BuiltInOptions()));
  }

  @Test
  public void otherServicesAreDelegated() {
    final StorageService storageService = mock(StorageService.class);
    context.addService(StorageService.class, storageService);

    assertThat(serviceManager.getService(StorageService.class)).containsSame(storageService);
    assertThat(serviceManager.getService(NetworkEndpointService.class)).isEmpty();
  }

  @Test
  public void addedServicesGoToTheContext() {
    final StorageService storageService = mock(StorageService.class);

    serviceManager.addService(StorageService.class, storageService);

    assertThat(context.getService(StorageService.class)).containsSame(storageService);
  }

  @Test
  public void withServiceTakesPrecedenceOverContext() {
    final NetworkEndpointServiceImpl inContext = new NetworkEndpointServiceImpl();
    final NetworkEndpointServiceImpl own = new NetworkEndpointServiceImpl();
    context.addService(NetworkEndpointService.class, inContext);

    serviceManager.withService(NetworkEndpointService.class, own);

    assertThat(serviceManager.getService(NetworkEndpointService.class)).containsSame(own);
    assertThat(context.getService(NetworkEndpointService.class)).containsSame(inContext);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void withServiceRejectsWhatTheContextRejects() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> serviceManager.withService(NetworkEndpointService.class, null));
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> serviceManager.withService(null, new NetworkEndpointServiceImpl()));
    // a class rather than an interface
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                serviceManager.withService(
                    (Class) NetworkEndpointServiceImpl.class, new NetworkEndpointServiceImpl()))
        .withMessage("Services must be Java interfaces.");
    // a service that does not implement the type
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                serviceManager.withService(
                    (Class) StorageService.class, new NetworkEndpointServiceImpl()))
        .withMessage("The service registered with a type must implement that type");
    assertThat(serviceManager.getService(NetworkEndpointService.class)).isEmpty();
  }
}

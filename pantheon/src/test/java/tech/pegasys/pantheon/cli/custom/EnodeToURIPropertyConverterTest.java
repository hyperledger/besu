/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.cli.custom;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.function.Function;

import org.junit.Test;

public class EnodeToURIPropertyConverterTest {

  @Test
  @SuppressWarnings("unchecked")
  public void converterDelegatesToFunction() {
    Function<String, URI> function = mock(Function.class);

    new EnodeToURIPropertyConverter(function).convert("foo");

    verify(function).apply(eq("foo"));
  }
}

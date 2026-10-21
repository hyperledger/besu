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
package org.hyperledger.besu.evm.loopgen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;

import com.sun.source.util.TreePath;

/**
 * What the variables of copied code stand for in the copy. A method whose body is copied in place
 * of a call gets a scope of its own, inside the scope of the code that called it.
 *
 * <p>A variable is one of:
 *
 * <ul>
 *   <li>declared by the copy, under a name {@link CaseLocals#declare} chose;
 *   <li>a parameter bound to an argument without side effects, which the copy repeats where the
 *       parameter is used;
 *   <li>a name for the frame, which the copy may use only as {@link UntracedLoop} allows.
 * </ul>
 */
final class Scope {

  /**
   * An argument a parameter is bound to, with the scope it is copied in.
   *
   * @param argument the argument at the call
   * @param scope the scope of the code that made the call
   */
  record Binding(TreePath argument, Scope scope) {}

  private final Scope caller;
  private final Map<Element, String> names = new HashMap<>();
  private final Map<Element, Binding> bindings = new HashMap<>();
  private final Set<Element> frames = new HashSet<>();

  Scope(final Scope caller) {
    this.caller = caller;
  }

  void name(final Element variable, final String name) {
    names.put(variable, name);
  }

  void bind(final Element parameter, final TreePath argument, final Scope argumentScope) {
    bindings.put(parameter, new Binding(argument, argumentScope));
  }

  void nameFrame(final Element parameter) {
    frames.add(parameter);
  }

  /** The variable's name in the copy, or null if the copy has not declared it. */
  String nameOf(final Element variable) {
    for (Scope s = this; s != null; s = s.caller) {
      if (s.names.containsKey(variable)) {
        return s.names.get(variable);
      }
    }
    return null;
  }

  /** The argument the parameter is bound to, or null if it is not a bound parameter. */
  Binding bindingOf(final Element parameter) {
    for (Scope s = this; s != null; s = s.caller) {
      if (s.bindings.containsKey(parameter)) {
        return s.bindings.get(parameter);
      }
    }
    return null;
  }

  boolean isFrame(final Element variable) {
    for (Scope s = this; s != null; s = s.caller) {
      if (s.frames.contains(variable)) {
        return true;
      }
    }
    return false;
  }
}

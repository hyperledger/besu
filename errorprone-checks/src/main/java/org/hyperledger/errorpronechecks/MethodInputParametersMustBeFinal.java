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
package org.hyperledger.errorpronechecks;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;

import javax.lang.model.element.Modifier;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.VariableTree;

@AutoService(BugChecker.class)
@BugPattern(
    summary = "Method input parameters must be final.",
    severity = WARNING,
    linkType = BugPattern.LinkType.NONE)
public class MethodInputParametersMustBeFinal extends BugChecker
    implements MethodTreeMatcher, ClassTreeMatcher {

  private boolean isAbstraction = false;

  @Override
  public Description matchClass(final ClassTree tree, final VisitorState state) {
    isAbstraction =
        isInterface(tree.getModifiers())
            || isAnonymousClassInAbstraction(tree)
            || isEnumInAbstraction(tree);
    return Description.NO_MATCH;
  }

  @Override
  public Description matchMethod(final MethodTree tree, final VisitorState state) {
    final ModifiersTree mods = tree.getModifiers();

    if (isAbstraction) {
      if (isConcreteMethod(mods)) {
        return matchParameters(tree);
      }
    } else if (isNotAbstract(mods)) {
      return matchParameters(tree);
    }

    return Description.NO_MATCH;
  }

  private Description matchParameters(final MethodTree tree) {
    for (final VariableTree inputParameter : tree.getParameters()) {
      if (isMissingFinalModifier(inputParameter)) {
        return describeMatch(tree);
      }
    }

    return Description.NO_MATCH;
  }

  private boolean isMissingFinalModifier(final VariableTree inputParameter) {
    return !inputParameter.getModifiers().getFlags().contains(Modifier.FINAL);
  }

  private boolean isNotAbstract(final ModifiersTree mods) {
    return !mods.getFlags().contains(Modifier.ABSTRACT);
  }

  @SuppressWarnings("TreeToString")
  private boolean isInterface(final ModifiersTree mods) {
    return mods.toString().contains("interface");
  }

  private boolean isConcreteMethod(final ModifiersTree mods) {
    return mods.getFlags().contains(Modifier.DEFAULT) || mods.getFlags().contains(Modifier.STATIC);
  }

  private boolean isAnonymousClassInAbstraction(final ClassTree tree) {
    return isAbstraction && isAnonymousClass(tree);
  }

  private boolean isAnonymousClass(final ClassTree tree) {
    return tree.getSimpleName().contentEquals("");
  }

  private boolean isEnumInAbstraction(final ClassTree tree) {
    return isAbstraction && isEnum(tree);
  }

  @SuppressWarnings("TreeToString")
  private boolean isEnum(final ClassTree tree) {
    return tree.toString().contains("enum");
  }
}

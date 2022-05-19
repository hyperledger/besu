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
import static com.google.errorprone.fixes.SuggestedFixes.addModifiers;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.isSubtype;

import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;

@AutoService(BugChecker.class)
@BugPattern(
    summary = "Logger classes should be private, static, and final.",
    severity = WARNING,
    linkType = BugPattern.LinkType.NONE)
public class PrivateStaticFinalLoggers extends BugChecker implements VariableTreeMatcher {

  static final Supplier<Type> ORG_SLF4J_LOGGER = Suppliers.typeFromString("org.slf4j.Logger");

  @Override
  public Description matchVariable(final VariableTree tree, final VisitorState state) {
    final Symbol.VarSymbol sym = ASTHelpers.getSymbol(tree);
    if (sym == null || sym.getKind() != ElementKind.FIELD) {
      return NO_MATCH;
    }
    if (sym.getModifiers()
        .containsAll(List.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL))) {
      return NO_MATCH;
    }
    if (!isSubtype(getType(tree), ORG_SLF4J_LOGGER.get(state), state)) {
      return NO_MATCH;
    }
    Optional<SuggestedFix> fixes =
        addModifiers(tree, state, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
    return buildDescription(tree)
        .addFix(fixes.isPresent() ? fixes.get() : SuggestedFix.emptyFix())
        .build();
  }
}

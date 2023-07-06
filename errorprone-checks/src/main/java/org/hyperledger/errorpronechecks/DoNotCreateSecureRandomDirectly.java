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

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Symbol;

@AutoService(BugChecker.class)
@BugPattern(
    summary = "Do not create SecureRandom directly.",
    severity = WARNING,
    linkType = BugPattern.LinkType.NONE)
public class DoNotCreateSecureRandomDirectly extends BugChecker
    implements MethodInvocationTreeMatcher, NewClassTreeMatcher {

  @SuppressWarnings("TreeToString")
  @Override
  public Description matchMethodInvocation(
      final MethodInvocationTree tree, final VisitorState state) {
    if (tree.getMethodSelect().toString().equals("SecureRandom.getInstance")) {
      return describeMatch(tree);
    }

    return Description.NO_MATCH;
  }

  @Override
  public Description matchNewClass(final NewClassTree tree, final VisitorState state) {
    final Symbol sym = ASTHelpers.getSymbol(tree.getIdentifier());
    if (sym != null && sym.toString().equals("java.security.SecureRandom")) {
      return describeMatch(tree);
    }

    return Description.NO_MATCH;
  }
}

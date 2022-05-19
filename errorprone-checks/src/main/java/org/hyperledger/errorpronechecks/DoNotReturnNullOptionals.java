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

import static com.google.errorprone.BugPattern.SeverityLevel.SUGGESTION;
import static com.google.errorprone.matchers.Matchers.contains;
import static com.sun.source.tree.Tree.Kind.NULL_LITERAL;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;

/*
 * This is reworked from an example found at:
 * https://github.com/google/error-prone/wiki/Writing-a-check
 */

@AutoService(BugChecker.class) // the service descriptor
@BugPattern(
    summary = "Do not return null optionals.",
    severity = SUGGESTION,
    linkType = BugPattern.LinkType.NONE)
public class DoNotReturnNullOptionals extends BugChecker implements MethodTreeMatcher {

  private static class ReturnNullMatcher implements Matcher<Tree> {

    @Override
    public boolean matches(final Tree tree, final VisitorState state) {
      if ((tree instanceof ReturnTree) && (((ReturnTree) tree).getExpression() != null)) {
        return ((ReturnTree) tree).getExpression().getKind() == NULL_LITERAL;
      }
      return false;
    }
  }

  private static final Matcher<Tree> RETURN_NULL = new ReturnNullMatcher();
  private static final Matcher<Tree> CONTAINS_RETURN_NULL = contains(RETURN_NULL);

  @SuppressWarnings("TreeToString")
  @Override
  public Description matchMethod(final MethodTree tree, final VisitorState state) {
    if ((tree.getReturnType() == null)
        || !tree.getReturnType().toString().startsWith("Optional<")
        || (tree.getBody() == null)
        || (!CONTAINS_RETURN_NULL.matches(tree.getBody(), state))) {
      return Description.NO_MATCH;
    }
    return describeMatch(tree);
  }
}

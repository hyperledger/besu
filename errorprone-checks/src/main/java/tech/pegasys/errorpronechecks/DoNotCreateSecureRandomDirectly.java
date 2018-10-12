package tech.pegasys.errorpronechecks;

import static com.google.errorprone.BugPattern.Category.JDK;
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
  name = "DoNotCreateSecureRandomDirectly",
  summary = "Do not create SecureRandom directly.",
  category = JDK,
  severity = WARNING
)
public class DoNotCreateSecureRandomDirectly extends BugChecker
    implements MethodInvocationTreeMatcher, NewClassTreeMatcher {

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

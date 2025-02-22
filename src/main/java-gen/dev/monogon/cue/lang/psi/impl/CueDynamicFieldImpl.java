// This is a generated file. Not intended for manual editing.
package dev.monogon.cue.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static dev.monogon.cue.lang.CueTypes.*;
import dev.monogon.cue.lang.psi.*;

public class CueDynamicFieldImpl extends CueDeclarationImpl implements CueDynamicField {

  public CueDynamicFieldImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull CueVisitor visitor) {
    visitor.visitDynamicField(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CueVisitor) accept((CueVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CueExpression> getExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CueExpression.class);
  }

  @Override
  @NotNull
  public List<CueAttribute> getAttributeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CueAttribute.class);
  }

}

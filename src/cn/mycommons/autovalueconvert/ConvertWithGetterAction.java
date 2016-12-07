package cn.mycommons.autovalueconvert;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;

/**
 * ConvertAction <br/>
 * Created by xiaqiulei on 2016-12-05.
 */
public class ConvertWithGetterAction extends ConvertAction {

    protected void modify(Project project, PsiJavaFile javaFile, PsiElement element) {
        new WriteCommandAction.Simple<Boolean>(project, javaFile) {
            @Override
            protected void run() throws Throwable {
                new ModifySource(javaFile, (PsiClass) element, project, true).modify();
            }
        }.execute();
    }
}
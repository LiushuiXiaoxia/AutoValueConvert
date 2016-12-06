package cn.mycommons.autovalueconvert

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile


/**
 * ConvertAction <br/>
 * Created by xiaqiulei on 2016-12-05.
 */
class ConvertAction extends AnAction {


    @Override
    void update(AnActionEvent e) {
        super.update(e)
        boolean visible = isFileOk(e)
        e.presentation.setVisible(visible)
    }

    static boolean isFileOk(AnActionEvent e) {
        VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE)
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE)
        boolean isVFOk = virtualFile != null && virtualFile.name.toLowerCase().endsWith("java")
        boolean isPsiOk = psiFile != null && psiFile.name.toLowerCase().endsWith("java")
        return isVFOk && isPsiOk
    }

    @Override
    void actionPerformed(AnActionEvent event) {
        try {
            doAction(event)
        } catch (Exception e) {
            e.printStackTrace()
            Project project = event.getData(PlatformDataKeys.PROJECT)
            Messages.showMessageDialog(project, e.message, "Warning", Messages.warningIcon)
        }
    }

    private static void doAction(AnActionEvent event) throws Exception {
        String msg = null
        for (int i = 0; i < 1; i++) {
            PsiFile psiFile = event.getData(LangDataKeys.PSI_FILE)
            if (psiFile == null) {
                msg = "No file"
                break
            }

            println "psiFile = ${psiFile}"

            if (!(psiFile instanceof PsiJavaFile)) {
                msg = "No java file"
                break
            }
            PsiJavaFile javaFile = psiFile

            Project project = event.getData(PlatformDataKeys.PROJECT)
            if (project == null) {
                break
            }
            PsiElement[] children = javaFile.children;

            for (PsiElement element : children) {
                if (element instanceof PsiClass) {
                    new WriteCommandAction.Simple<Boolean>(project, javaFile) {
                        @Override
                        protected void run() throws Throwable {
                            new ModifySource(javaFile, (PsiClass) element, project).modify()
                        }
                    }.execute()
                }
            }
        }
        if (msg != null && msg.length() != 0) {
            throw new RuntimeException(msg)
        }
    }
}
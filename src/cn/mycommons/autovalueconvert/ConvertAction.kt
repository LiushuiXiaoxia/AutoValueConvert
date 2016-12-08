package cn.mycommons.autovalueconvert

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile


/**
 * ConvertAction
 * Created by xiaqiulei on 2016-12-05.
 */
open class ConvertAction : AnAction() {

    override fun update(event: AnActionEvent?) {
        super.update(event)
        if (event != null) {
            event.presentation.isVisible = isFileOk(event)
        }
    }

    private fun isFileOk(e: AnActionEvent): Boolean {
        val virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE)
        val psiFile = e.getData(LangDataKeys.PSI_FILE)
        val isVFOk = virtualFile?.name?.toLowerCase()?.endsWith("java")
        val isPsiOk = psiFile?.name?.toLowerCase()?.endsWith("java")
        return isVFOk != null && isVFOk && isPsiOk != null && isPsiOk
    }

    override fun actionPerformed(event: AnActionEvent) {
        try {
            doAction(event)
        } catch (e: Exception) {
            val project = event.getData(PlatformDataKeys.PROJECT)
            Messages.showMessageDialog(project, e.message, "Warning", Messages.getWarningIcon())
        }
    }

    @Throws(Exception::class)
    private fun doAction(event: AnActionEvent) {
        var msg: String? = null
        for (i in 0..1) {
            val psiFile = event.getData(LangDataKeys.PSI_FILE)
            if (psiFile == null) {
                msg = "No file"
                break
            }

            if (psiFile !is PsiJavaFile) {
                msg = "No java file"
                break
            }

            val project = event.getData(PlatformDataKeys.PROJECT) ?: break

            psiFile.children
                    .filter { it is PsiClass }
                    .forEach { modify(project, psiFile, it as PsiClass) }
        }
        if (msg != null && msg.isNotEmpty()) {
            throw RuntimeException(msg)
        }
    }

    protected open fun modify(project: Project, javaFile: PsiJavaFile, clazz: PsiClass) {
        object : WriteCommandAction.Simple<Boolean>(project, javaFile) {
            @Throws(Throwable::class)
            override fun run() {
                ModifySource(javaFile, clazz, project, false).modify()
            }
        }.execute()
    }
}
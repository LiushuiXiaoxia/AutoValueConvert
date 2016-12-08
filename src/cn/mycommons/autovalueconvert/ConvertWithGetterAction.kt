package cn.mycommons.autovalueconvert

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile

/**
 * ConvertAction
 * Created by xiaqiulei on 2016-12-05.
 */
open class ConvertWithGetterAction : ConvertAction() {

    override fun modify(project: Project, javaFile: PsiJavaFile, clazz: PsiClass) {
        object : WriteCommandAction.Simple<Boolean>(project, javaFile) {
            @Throws(Throwable::class)
            override fun run() {
                ModifySource(javaFile, clazz, project, true).modify()
            }
        }.execute()
    }
}
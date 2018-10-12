package cn.mycommons.autovalueconvert

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import java.util.*

/**
 * ModifySource
 * Created by xiaqiulei on 2016-12-06.
 */

val removeMethodNames = arrayOf(
        "equals",
        "hashCode",
        "toString"
)

val ignoreTypes = arrayOf(
        "byte",
        "short",
        "int",
        "long",
        "char",
        "float",
        "double",
        "boolean"
)

internal class ModifySource(
        private val javaFile: PsiJavaFile,
        private val psiClass: PsiClass,
        private val project: Project,
        // getter方式的命名
        private val getterStyleMethod: Boolean) {

    private val fields: ArrayList<PsiField> = ArrayList()

    fun modify() {
        if (init()) {
            // 删除setter,getter等方法
            removeMethod()

            // 修改文件和类
            updateFile()

            // 生成方法
            addMethod()
        }
    }

    private fun init(): Boolean {
        val modifierList = psiClass.modifierList
        if (modifierList != null) {
            modifierList.annotations
                    .filter { "@AutoValue" == it.text }
                    .forEach { return false }

            // 遍历所有字段
            for (field in psiClass.fields) {
                val list = field.modifierList
                if (list != null && !list.hasModifierProperty("static")) {
                    fields.add(field)
                }
            }
        }

        return !fields.isEmpty()
    }

    private fun removeMethod() {
        for (method in psiClass.methods) {
            var isDelete = false
            // 静态方法不算
            if (!method.modifierList.hasModifierProperty("static")) {
                // 删除 setter getter方法
                for (field in fields) {
                    val fieldName = field.name

                    if (fieldName.isEmpty()) {
                        continue
                    }
                    if (getMethodNames(fieldName).contains(method.name)) {
                        method.delete()
                        isDelete = true
                    }
                }
            }

            if (!isDelete && removeMethodNames.contains(method.name)) {
                method.delete()
            }
        }
    }

    private fun updateFile() {
        val modifierList = psiClass.modifierList ?: return

        // 设置为抽象类
        modifierList.setModifierProperty("abstract", true)

        // 添加AutoValue
        modifierList.addAnnotation("AutoValue")

        val importList = javaFile.importList ?: return

        val factory = PsiElementFactory.SERVICE.getInstance(project)

        // 添加importSerializedName,Nullable
        if (importList.findSingleImportStatement("SerializedName") == null) {
            importList.add(factory.createImportStatementOnDemand("com.google.gson.annotations"))
        }
        if (importList.findSingleImportStatement("Gson") == null) {
            importList.add(factory.createImportStatementOnDemand("com.google.gson"))
        }
        if (importList.findSingleImportStatement("Nullable") == null) {
            importList.add(factory.createImportStatementOnDemand("android.support.annotation"))
        }
        if (importList.findSingleImportStatement("AutoValue") == null) {
            importList.add(factory.createImportStatementOnDemand("com.google.auto.value"))
        }
    }

    private fun addMethod() {
        val factory = PsiElementFactory.SERVICE.getInstance(project)

        for (field in fields) {
            val fieldAnnotationStr = StringBuilder()

            val modifierList = field.modifierList ?: continue
            val annotations = modifierList.annotations
            for (annotation in annotations) {
                fieldAnnotationStr.append(annotation.text).append("\n")
            }

            // 生成函数名字
            val name = genMethodName(field)

            // 生成函数
            val methodStr = "${fieldAnnotationStr}public abstract ${field.type.canonicalText} $name();"
            val method = factory.createMethodFromText(methodStr, null)

            // 是否有@SerializedName,没有则添加
            if (method.modifierList.findAnnotation("SerializedName") == null) {
                method.modifierList.addAnnotation("""SerializedName("${field.name}")""")
            }
            // 不是原始类型,添加@Nullable
            if (field.type.canonicalText !in ignoreTypes) {
                method.modifierList.addAnnotation("Nullable")
            }

            psiClass.add(method)
            field.delete()
        }

        // 添加typeAdapter方法
        val clzName = psiClass.name
        val string = "public static TypeAdapter<$clzName> typeAdapter(Gson gson) {return new AutoValue_$clzName.GsonTypeAdapter(gson);}"
        psiClass.add(factory.createMethodFromText(string, null))
    }

    private fun getMethodNames(fieldName: String): List<String> {
        val first = fieldName.substring(0, 1).toUpperCase()
        val second = fieldName.substring(1)
        return Arrays.asList<String>(
                "set$first$second",
                "get$first$second",
                "is$first$second",
                if (fieldName.startsWith("is")) fieldName else null,
                if (fieldName.startsWith("is")) "set${fieldName.substring(2)}" else null
        )
    }

    private fun genMethodName(field: PsiField): String? {
        var name = field.name
        if (name.isNotEmpty() && getterStyleMethod) {
            // String ok;  --> String getOk();
            // boolean ok;  --> boolean isOk();
            // boolean isOk;  --> boolean isOk();

            if ("boolean" == field.type.canonicalText) { // boolean 类型
                if (!name.startsWith("is")) {
                    name = "is${name.substring(0, 1).toUpperCase()}${name.substring(1)}"
                }
            } else {
                name = "get${name.substring(0, 1).toUpperCase()}${name.substring(1)}"
            }
        }
        return name
    }
}
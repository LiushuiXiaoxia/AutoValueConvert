package cn.mycommons.autovalueconvert

import com.intellij.openapi.project.Project
import com.intellij.psi.*

/**
 * ModifySource <br/>
 * Created by xiaqiulei on 2016-12-06.
 */
class ModifySource {

    static List<String> removeMethodNames = ["equals", "hashCode", "toString"]

    static List<String> ignoreTypes = [
            byte.class.typeName,
            short.class.typeName,
            int.class.typeName,
            long.class.typeName,
            char.class.typeName,
            float.class.typeName,
            double.class.typeName,
            boolean.class.typeName,
    ]

    PsiJavaFile javaFile
    PsiClass psiClass
    Project project
    List<PsiField> fields

    ModifySource(PsiJavaFile javaFile, PsiClass psiClass, Project project) {
        this.javaFile = javaFile
        this.psiClass = psiClass
        this.project = project
        fields = new ArrayList<>()
    }

    void modify() {
        for (PsiAnnotation annotation : psiClass.modifierList.annotations) {
            if ("@AutoValue" == annotation.text) {
                return
            }
        }
        // 添加AutoValue
        psiClass.modifierList.addAnnotation("AutoValue")

        // 遍历所有字段
        for (PsiField field : psiClass.fields) {
            if (!field.modifierList.hasModifierProperty("static")) {
                fields.add(field)
            }
        }
        if (fields.isEmpty()) {
            return
        }

        // 删除setter,getter等方法
        removeMethod()

        // 修改文件和类
        updateFile()

        // 生成方法
        addMethod()
    }

    private void removeMethod() {
        for (PsiMethod method : psiClass.methods) {
            boolean isDelete = false;
            // 静态方法不算
            if (!method.modifierList.hasModifierProperty("static")) {
                // 删除 setter getter方法
                for (PsiField field : fields) {
                    String fieldName = field.name;

                    if (fieldName == null || fieldName.length() == 0) {
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

    private void updateFile() {
        // 设置为抽象类
        psiClass.modifierList.setModifierProperty("abstract", true)

        def factory = PsiElementFactory.SERVICE.getInstance(project)
        // 添加importSerializedName,Nullable
        if (javaFile.importList.findSingleImportStatement("SerializedName") == null) {
            def serializedname = factory.createImportStatementOnDemand("com.google.gson.annotations")
            javaFile.importList.add(serializedname)
        }
        if (javaFile.importList.findSingleImportStatement("Nullable") == null) {
            def nullable = factory.createImportStatementOnDemand("android.support.annotation")
            javaFile.importList.add(nullable)
        }
        if (javaFile.importList.findSingleImportStatement("AutoValue") == null) {
            def value = factory.createImportStatementOnDemand("com.google.auto.value")
            javaFile.importList.add(value)
        }
    }

    private void addMethod() {
        PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(project)

        for (PsiField field : fields) {
            StringBuilder fieldAnnotationStr = new StringBuilder()
            if (field.modifierList.annotations != null && field.modifierList.annotations.length > 0) {
                for (PsiAnnotation annotation : field.modifierList.annotations) {
                    fieldAnnotationStr.append(annotation.text + "\n")
                }
            }

            def string = String.format("public abstract %s %s();", field.type.canonicalText, field.name)
            def method = factory.createMethodFromText(fieldAnnotationStr + string, null)

            // 是否有@SerializedName,没有则添加
            def serializedName = method.modifierList.findAnnotation("SerializedName")
            if (serializedName == null) {
                method.modifierList.addAnnotation("""SerializedName("${field.name}")""")
            }

            // 不是原始类型,添加@Nullable
            if (!ignoreTypes.contains(field.type.canonicalText)) {
                method.modifierList.addAnnotation("Nullable")
            }

            psiClass.add(method)
            field.delete()
        }

        // 添加typeAdapter方法
        def str = """
public static TypeAdapter<${psiClass.name}> typeAdapter(Gson gson) {
    return new AutoValue_${psiClass.name}.GsonTypeAdapter(gson);
}
"""
        // psiClass.add(factory.createMethodFromText(str, null))
    }

    private static List<String> getMethodNames(String fieldName) {
        def first = fieldName.substring(0, 1).toUpperCase()
        def second = fieldName.substring(1)
        Arrays.asList(
                "set${first}${second}".toString(),
                "get${first}${second}".toString(),
                "is${first}${second}".toString(),
                fieldName.startsWith("is") ? fieldName : ""
        ) as List<String>
    }
}
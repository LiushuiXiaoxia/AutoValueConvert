package cn.mycommons.autovalueconvert;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ModifySource <br/>
 * Created by xiaqiulei on 2016-12-06.
 */
class ModifySource {

    private static List<String> removeMethodNames = Arrays.asList(
            "equals",
            "hashCode",
            "toString"
    );

    private static List<String> ignoreTypes = Arrays.asList(
            byte.class.getName(),
            short.class.getName(),
            int.class.getName(),
            long.class.getName(),
            char.class.getName(),
            float.class.getName(),
            double.class.getName(),
            boolean.class.getName()
    );

    private PsiJavaFile javaFile;
    private PsiClass psiClass;
    private Project project;
    private List<PsiField> fields;

    ModifySource(PsiJavaFile javaFile, PsiClass psiClass, Project project) {
        this.javaFile = javaFile;
        this.psiClass = psiClass;
        this.project = project;
        fields = new ArrayList<>();
    }

    void modify() {
        if (init()) {
            // 删除setter,getter等方法
            removeMethod();

            // 修改文件和类
            updateFile();

            // 生成方法
            addMethod();
        }
    }

    private boolean init() {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList != null) {
            PsiAnnotation[] annotations = modifierList.getAnnotations();
            for (PsiAnnotation annotation : annotations) {
                if ("@AutoValue".equals(annotation.getText())) {
                    return false;
                }
            }
            // 遍历所有字段
            for (PsiField field : psiClass.getFields()) {
                PsiModifierList list = field.getModifierList();
                if (list != null && !list.hasModifierProperty("static")) {
                    fields.add(field);
                }
            }
        }

        return !fields.isEmpty();
    }

    private void removeMethod() {
        for (PsiMethod method : psiClass.getMethods()) {
            boolean isDelete = false;
            // 静态方法不算
            if (!method.getModifierList().hasModifierProperty("static")) {
                // 删除 setter getter方法
                for (PsiField field : fields) {
                    String fieldName = field.getName();

                    if (fieldName == null || fieldName.length() == 0) {
                        continue;
                    }
                    if (getMethodNames(fieldName).contains(method.getName())) {
                        method.delete();
                        isDelete = true;
                    }
                }
            }

            if (!isDelete && removeMethodNames.contains(method.getName())) {
                method.delete();
            }
        }
    }

    private void updateFile() {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList == null) {
            return;
        }

        // 设置为抽象类
        modifierList.setModifierProperty("abstract", true);

        // 添加AutoValue
        modifierList.addAnnotation("AutoValue");

        PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return;
        }

        PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(project);
        // 添加importSerializedName,Nullable
        if (importList.findSingleImportStatement("SerializedName") == null) {
            importList.add(factory.createImportStatementOnDemand("com.google.gson"));
            importList.add(factory.createImportStatementOnDemand("com.google.gson.annotations"));
        }
        if (importList.findSingleImportStatement("Nullable") == null) {
            PsiElement nullable = factory.createImportStatementOnDemand("android.support.annotation");
            importList.add(nullable);
        }
        if (importList.findSingleImportStatement("AutoValue") == null) {
            PsiElement value = factory.createImportStatementOnDemand("com.google.auto.value");
            importList.add(value);
        }
    }

    private void addMethod() {
        PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(project);

        for (PsiField field : fields) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null) {
                continue;
            }
            StringBuilder fieldAnnotationStr = new StringBuilder();
            PsiAnnotation[] annotations = modifierList.getAnnotations();
            for (PsiAnnotation annotation : annotations) {
                fieldAnnotationStr.append(annotation.getText()).append("\n");
            }

            String format = "public abstract %s %s();";
            String string = String.format(format, field.getType().getCanonicalText(), field.getName());
            PsiMethod method = factory.createMethodFromText(fieldAnnotationStr + string, null);

            // 是否有@SerializedName,没有则添加
            PsiElement serializedName = method.getModifierList().findAnnotation("SerializedName");
            if (serializedName == null) {
                String anno = String.format("SerializedName(\"%s\")", field.getName());
                method.getModifierList().addAnnotation(anno);
            }

            // 不是原始类型,添加@Nullable
            if (!ignoreTypes.contains(field.getType().getCanonicalText())) {
                method.getModifierList().addAnnotation("Nullable");
            }

            psiClass.add(method);
            field.delete();
        }

        // 添加typeAdapter方法
        String string = "" +
                "public static TypeAdapter<" + psiClass.getName() + "> typeAdapter(Gson gson) {" +
                "    return new AutoValue_" + psiClass.getName() + ".GsonTypeAdapter(gson);" +
                "}";
        psiClass.add(factory.createMethodFromText(string, null));
    }

    private static List<String> getMethodNames(String fieldName) {
        String first = fieldName.substring(0, 1).toUpperCase();
        String second = fieldName.substring(1);
        return Arrays.asList(
                "set" + first + second,
                "get" + first + second,
                "is" + first + second,
                fieldName.startsWith("is") ? fieldName : ""
        );
    }
}
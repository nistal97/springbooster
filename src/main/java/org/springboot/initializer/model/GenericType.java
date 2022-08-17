package org.springboot.initializer.model;

import com.squareup.javapoet.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.ExportSerializer;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.util.BuiltInMethodFunc;
import org.springboot.initializer.util.TypeUtil;

import javax.lang.model.element.Modifier;
import java.nio.file.Path;
import java.util.*;

public class GenericType extends SpringBooster.Base implements ExportSerializer {

    public static class FormatExporter {
        public enum Type{
            BEGIN_FLOW,
            STMT,
            CODE_BLOCK,
            NEXT_FLOW,
            END_FLOW
        }
        String format;
        Object[] args;
        CodeBlock codeBlock;
        Type t;

        public FormatExporter(Type t) {
            this.t = t;
        }

        public FormatExporter(Type t, String format, Object... args) {
            this.format = format;
            this.args = args;
            this.t = t;
        }

        public FormatExporter(Type t, CodeBlock codeBlock) {
            this.t = t;
            this.codeBlock = codeBlock;
        }
    }

    private String superclass;
    private Set<String> superinterfaces;
    private List<ExportPoint> fields;
    private Set<ExportPoint> methods;
    private String packageName;
    private String className;
    private Modifier[] modifiers;
    private CodeBlock.Builder staticblock;

    private Set<Annotation> annotations = new HashSet<>();
    private boolean enableGetSet = true;

    private Set<String> crucialFields = new HashSet<>();

    public GenericType(){}

    public GenericType(String superclass, Set<String> superinterfaces, List<ExportPoint> fields, Set<ExportPoint> methods, String packageName, String className, Modifier... modifiers) {
        this.superclass = superclass;
        this.superinterfaces = superinterfaces;
        this.fields = fields == null ? new ArrayList<>() : fields;
        this.methods = methods == null ? new HashSet<>() : methods;
        this.packageName = packageName;
        this.className = className;
        this.modifiers = modifiers;
    }

    public GenericType(String superclass, Set<String> superinterfaces, List<ExportPoint> fields, Set<ExportPoint> methods, String packageName, String className,
                       boolean enableGetSet, Modifier... modifiers) {
        this.superclass = superclass;
        this.superinterfaces = superinterfaces;
        this.fields = fields == null ? new ArrayList<>() : fields;
        this.methods = methods == null ? new HashSet<>() : methods;
        this.packageName = packageName;
        this.className = className;
        this.modifiers = modifiers;
        this.enableGetSet = enableGetSet;
    }

    public boolean check() {
        if (className == null || className.isEmpty()) return false;
        for (ExportPoint e : fields) {
            if (!e.check()) return false;
        }
        for (ExportPoint m : methods) {
            if (!m.check()) return false;
        }
        return true;
    }

    @Override
    public void decorateDataType() {}

    @Override
    public void serialize(int lineNum, String line, Path destPath) throws Exception {
    }

    private final TypeSpec.Builder generateBuilder() throws Exception {
        TypeSpec.Builder builder = TypeSpec.classBuilder(className);
        for (Annotation a : annotations) builder.addAnnotation((AnnotationSpec)a.export());
        if (!Strings.isBlank(superclass)) builder.superclass(ClassName.bestGuess(superclass));
        if (superinterfaces != null) superinterfaces.forEach(s -> builder.addSuperinterface(ClassName.bestGuess(s)));
        if (modifiers != null) {
            for (Modifier m : modifiers) builder.addModifiers(m);
        }
        return builder;
    }

    private TypeSpec.Builder generateStaticBlock(TypeSpec.Builder builder) throws Exception {
        if (staticblock != null && !staticblock.isEmpty()) {
            builder.addStaticBlock(staticblock.build());
        }
        return builder;
    }

    private TypeSpec.Builder generateFields(TypeSpec.Builder builder) throws Exception {
        for (ExportPoint e : fields) {
            Field f = (Field)e;
            FieldSpec spec = (FieldSpec)f.export();
            if (spec != null) builder.addField(spec);
            else throw new Exception("Invalid field to export:" + f.toString());
        }
        return builder;
    }

    private TypeSpec.Builder generateMethods(TypeSpec.Builder builder) throws Exception {
        for (ExportPoint m : methods) {
            MethodSpec spec = (MethodSpec)m.export();
            if (spec != null) builder.addMethod(spec);
            else throw new Exception("Invalid method to export:" + m.toString());
        }
        return builder;
    }

    private TypeSpec.Builder generateGetSets(TypeSpec.Builder builder) throws Exception {
        for (ExportPoint e : fields) {
            Field f = (Field)e;
            if (f.getName().equals("serialVersionUID")) continue;

            String firstUpperName = BuiltInMethodFunc.upperFirstCharName(f.getName());
            MethodSpec.Builder getbuilder = MethodSpec.methodBuilder("get" + firstUpperName)
                    .addModifiers(Modifier.PUBLIC).addCode("return " + f.getName() + ";\n");
            if (f.getType() == null) getbuilder.returns(f.getTypeName());
            else getbuilder.returns(f.getType());

            builder.addMethod(getbuilder.build());

            MethodSpec.Builder setbuilder = MethodSpec.methodBuilder("set" + firstUpperName)
                    .addModifiers(Modifier.PUBLIC).addCode("this." + f.getName() + " = " + f.getName() + ";\n");
            if (f.getType() == null) setbuilder.addParameter(f.getTypeName(), f.getName());
            else setbuilder.addParameter(f.getType(), f.getName());

            builder.addMethod(setbuilder.build());
        }
        return builder;
    }

    @Override
    public void doSerialize(Path path) throws Exception {
        TypeSpec.Builder builder = generateBuilder();
        //static block
        generateStaticBlock(builder);
        //fields
        generateFields(builder);
        //getsets
        if (enableGetSet) generateGetSets(builder);
        //additional methods
        generateMethods(builder);
        //equals&hashcode
        generateEqualsHashcode(builder);
        //clone
        generateClone(builder);
        //serialize
        serialize(path, packageName, builder.build());
    }

    public void addAnnotation(Annotation annotation) {
        this.annotations.add(annotation);
    }

    private TypeSpec.Builder generateEqualsHashcode(TypeSpec.Builder builder) {
        if (!crucialFields.isEmpty()) {
            MethodSpec.Builder hashcode = MethodSpec.methodBuilder("hashcode").returns(int.class).addModifiers(Modifier.PUBLIC)
                    .addCode("return $T.hash(", Objects.class);
            int i = 0;
            for (String f : crucialFields) {
                if (i > 0) hashcode.addCode(", ");
                hashcode.addCode(f);
                i ++;
            }
            hashcode.addCode(");");
            builder.addMethod(hashcode.build());

            MethodSpec.Builder equals = MethodSpec.methodBuilder("equals").returns(boolean.class).addModifiers(Modifier.PUBLIC);
            equals.addParameter(ClassName.get(Object.class), "o");
            equals.addStatement("if (this == o) return true").addStatement("if (o == null || getClass() != o.getClass()) return false");
            equals.addStatement("$T that = ($T)o", ClassName.bestGuess(packageName + "." + className),
                    ClassName.bestGuess(packageName + "." + className));
            equals.addCode("return ");
            i = 0;
            for (String f : crucialFields) {
                if (i > 0) equals.addCode(" && ");
                String typeName = null;
                for (ExportPoint e : fields) {
                    Field field = (Field) e;
                    if (field.getName().equals(f)) {
                        typeName = field.getFieldTypeName();
                        break;
                    }
                }
                if (TypeUtil.isCollection(typeName)) {
                    equals.addCode("$T.isEqualCollection($L, that.$L)", CollectionUtils.class, f, f);
                } else {
                    if (BaseModel.BIG_DECIMAL.equals(typeName)) {
                        equals.addCode("$L.compareTo(that.$L) == 0", f, f);
                    } else {
                        equals.addCode("$T.equals($L, that.$L)", Objects.class, f, f);
                    }
                }
                i ++;
            }
            equals.addCode(";");
            builder.addMethod(equals.build());
        }
        return builder;
    }

    private TypeSpec.Builder generateClone(TypeSpec.Builder builder) {
        if (!crucialFields.isEmpty()) {
            MethodSpec.Builder clone = MethodSpec.methodBuilder("clone")
                    .returns(ClassName.bestGuess(packageName + "." + className)).addModifiers(Modifier.PUBLIC)
                    .addStatement("$T obj = new $T()", ClassName.bestGuess(packageName + "." + className)
                            ,ClassName.bestGuess(packageName + "." + className));
            fields.forEach(e -> {
                Field f = (Field) e;
                //skip static
                for (Modifier m : f.modifiers) {
                    if (m.equals(Modifier.STATIC)) return;
                }
                if (TypeUtil.isPrimitive(f.getFieldTypeName())) {
                    clone.addStatement("obj.set$L(get$L())", BuiltInMethodFunc.upperFirstCharName(f.name), BuiltInMethodFunc.upperFirstCharName(f.name));
                } else {
                    if (TypeUtil.isCollection(f.getFieldTypeName())) {
                        clone.addStatement("get$L().forEach(o -> obj.get$L().add(o))", BuiltInMethodFunc.upperFirstCharName(f.name),
                                BuiltInMethodFunc.upperFirstCharName(f.name));
                    } else {
                        clone.addStatement("obj.set$L(get$L() == null ? null : get$L().clone())", BuiltInMethodFunc.upperFirstCharName(f.name),
                                BuiltInMethodFunc.upperFirstCharName(f.name), BuiltInMethodFunc.upperFirstCharName(f.name));
                    }
                }
            });
            clone.addStatement("return obj");
            builder.addMethod(clone.build());
        }
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenericType that = (GenericType) o;
        return Objects.equals(fields, that.fields) && Objects.equals(methods, that.methods)
                && Objects.equals(packageName, that.packageName) && Objects.equals(className, that.className)
                && Objects.equals(modifiers, that.modifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields, methods, packageName, className, modifiers);
    }

    public String getSuperclass() {
        return superclass;
    }

    public void setSuperclass(String superclass) {
        this.superclass = superclass;
    }

    public List<ExportPoint> getFields() {
        return fields;
    }

    public void setFields(List<ExportPoint> fields) {
        this.fields = fields;
    }

    public Set<ExportPoint> getMethods() {
        return methods;
    }

    public void setMethods(Set<ExportPoint> methods) {
        this.methods = methods;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Modifier[] getModifiers() {
        return modifiers;
    }

    public void setModifiers(Modifier[] modifiers) {
        this.modifiers = modifiers;
    }

    public Set<Annotation> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Set<Annotation> annotations) {
        this.annotations = annotations;
    }

    public Set<String> getSuperinterfaces() {
        return superinterfaces;
    }

    public void setSuperinterfaces(Set<String> superinterfaces) {
        this.superinterfaces = superinterfaces;
    }

    public boolean isEnableGetSet() {
        return enableGetSet;
    }

    public void setEnableGetSet(boolean enableGetSet) {
        this.enableGetSet = enableGetSet;
    }

    public CodeBlock.Builder getStaticblock() {
        return staticblock;
    }

    public void setStaticblock(CodeBlock.Builder staticblock) {
        this.staticblock = staticblock;
    }

    public Set<String> getCrucialFields() {
        return crucialFields;
    }

    public void setCrucialFields(Set<String> crucialFields) {
        this.crucialFields = crucialFields;
    }
}

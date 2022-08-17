package org.springboot.initializer.model;

import com.squareup.javapoet.*;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.util.TypeUtil;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Type;
import java.util.*;

public class Method extends BaseModel implements ExportPoint {

    private LinkedHashMap<String, TypeName> params;
    private Map<String, Set<Annotation>> paramAnnotations = new HashMap<>();
    private List<GenericType.FormatExporter> statements;
    private List<TypeName> exceptionList = new ArrayList<>();
    private String genericType;
    private boolean isConstructor;
    private boolean bAbstract;

    public Method(String name, Type type, LinkedHashMap<String, TypeName> params, List<GenericType.FormatExporter> statements, Modifier... modifiers) {
        this.name = name;
        this.modifiers = modifiers;
        this.type = type;
        this.params = params != null ? new LinkedHashMap<>(params) : params;
        this.statements = statements != null ? new ArrayList<>(statements) : statements;
    }

    public Method(String name, TypeName typename, LinkedHashMap<String, TypeName> params, List<GenericType.FormatExporter> statements, Modifier... modifiers) {
        this.name = name;
        this.modifiers = modifiers;
        this.typeName = typename;
        this.params = params != null ? new LinkedHashMap<>(params) : params;
        this.statements = statements != null ? new ArrayList<>(statements) : statements;
    }

    public Method(Method method) {
        this.name = method.name;
        this.modifiers = method.modifiers;
        this.type = method.type;
        this.typeName = method.getTypeName();
        this.params = method.params != null ? new LinkedHashMap<>(method.params) : null;
        this.statements = method.statements != null ? new ArrayList<>(method.statements) : null;
        this.exceptionList = new ArrayList<>(method.exceptionList);
        this.genericType = method.genericType;
        this.isConstructor = method.isConstructor;
        this.paramAnnotations = method.paramAnnotations != null ? new LinkedHashMap<>(method.paramAnnotations) : null;
        this.annotations = method.annotations != null ? new HashSet<>(method.annotations) : null;
        this.bAbstract = method.bAbstract;
    }

    @Override
    public boolean check() {
        return super.check();
    }

    public String getMethodReturnTypeName() {
        return typeName != null ? typeName.toString() : type.getTypeName();
    }

    public MethodSpec doExport() throws Exception {
        MethodSpec.Builder builder = isConstructor ? MethodSpec.constructorBuilder() : MethodSpec.methodBuilder(name);
        if (!isConstructor) {
            if (type != null) builder.returns(type);
            else builder.returns(typeName);
        }

        if (modifiers != null) Arrays.stream(modifiers).forEach((m) -> {builder.addModifiers(m);});
        if (params != null) {
            for (String k : params.keySet()) {
                if (paramAnnotations != null && paramAnnotations.containsKey(k)) {
                    ParameterSpec.Builder paramBuilder = ParameterSpec.builder(params.get(k), k);
                    for (Annotation a : paramAnnotations.get(k)) {
                        paramBuilder.addAnnotation((AnnotationSpec) a.export());
                    }
                    ParameterSpec ps = paramBuilder.build();
                    builder.addParameter(ps);
                }
                else builder.addParameter(params.get(k), k);
            }
        }
        if (statements != null) statements.forEach((exporter) -> {
            if (exporter.t == GenericType.FormatExporter.Type.BEGIN_FLOW) {
                builder.beginControlFlow(exporter.format, exporter.args);
            } else if (exporter.t == GenericType.FormatExporter.Type.NEXT_FLOW) {
                builder.nextControlFlow(exporter.format, exporter.args);
            } else if (exporter.t == GenericType.FormatExporter.Type.STMT) {
                builder.addStatement(exporter.format, exporter.args);
            } else if (exporter.t == GenericType.FormatExporter.Type.CODE_BLOCK) {
                builder.addStatement(exporter.codeBlock);
            } else {
                builder.endControlFlow();
            }
        });
        for (Annotation a : annotations) builder.addAnnotation((AnnotationSpec)a.export());
        if (bAbstract) builder.addModifiers(Modifier.ABSTRACT);
        if (genericType != null) builder.addTypeVariable(TypeUtil.getMethodGenericTypeVar(genericType));
        exceptionList.forEach(e->builder.addException(e));
        return builder.build();
    }

    public static Method generateConstructor(List<ExportPoint> fields) {
        LinkedHashMap<String, TypeName> params = new LinkedHashMap<>();
        List<GenericType.FormatExporter> stmts = new ArrayList();
        for (ExportPoint e : fields) {
            Field f = (Field)e;
            params.put(f.getName(), f.getTypeName() != null ? f.getTypeName() : ClassName.bestGuess(f.getType().getTypeName()));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "this.$L = $L", f.getName(), f.getName()));
        }
        return generateConstructor(params, stmts);
    }

    public static Method generateConstructor(LinkedHashMap<String, TypeName> params, List<GenericType.FormatExporter> stmts) {
        Method constructor = new Method("Constructor", void.class, params, stmts, Modifier.PUBLIC);
        constructor.setConstructor(true);
        return constructor;
    }

    public void addThrowable(TypeName t) {
        exceptionList.add(t);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Method method = (Method) o;
        return super.equals(o) && bAbstract == method.bAbstract && Objects.equals(params, method.params) && Objects.equals(paramAnnotations, method.paramAnnotations) && Objects.equals(exceptionList, method.exceptionList);
    }

    public void clearStatements() {
        this.statements = null;
    }

    public void clearAnnotations() {
        this.annotations.clear();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), bAbstract, params, paramAnnotations, exceptionList);
    }

    public LinkedHashMap<String, TypeName> getParams() {
        return params;
    }

    public void setParams(LinkedHashMap<String, TypeName> params) {
        this.params = params;
    }

    public List<GenericType.FormatExporter> getStatements() {
        return statements;
    }

    public void setStatements(List<GenericType.FormatExporter> statements) {
        this.statements = statements;
    }

    public boolean isbAbstract() {
        return bAbstract;
    }

    public void setbAbstract(boolean bAbstract) {
        this.bAbstract = bAbstract;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public void setConstructor(boolean constructor) {
        isConstructor = constructor;
    }

    public List<TypeName> getExceptionList() {
        return exceptionList;
    }

    public void setExceptionList(List<TypeName> exceptionList) {
        this.exceptionList = exceptionList;
    }

    public String getGenericType() {
        return genericType;
    }

    public void setGenericType(String genericType) {
        this.genericType = genericType;
    }

    public Map<String, Set<Annotation>> getParamAnnotations() {
        return paramAnnotations;
    }

    public void setParamAnnotations(Map<String, Set<Annotation>> paramAnnotations) {
        this.paramAnnotations = paramAnnotations;
    }

    public void addParamAnnotation(String k, Annotation a) {
        if (!paramAnnotations.containsKey(k)) {
            paramAnnotations.put(k, new HashSet<>());
        }
        paramAnnotations.get(k).add(a);
    }

    //model methods
    static class ModelMethodGenerator {
        static Method[] generateBaseTimestampMethods() {
            Method[] methods = new Method[2];
            List<GenericType.FormatExporter> stmts = new ArrayList<>();
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "updated = created = new $T()", Date.class));
            methods[0] = generateTimeStampMethod("onCreate", stmts, Annotation.ModelAnnotationGenerator.BASE_ONCREATE);
            stmts.clear();

            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "updated = new $T()", Date.class));
            methods[1] = generateTimeStampMethod("onUpdate", stmts, Annotation.ModelAnnotationGenerator.BASE_ONUPDATE);
            return methods;
        }

        static Method generateTimeStampMethod(String name, List<GenericType.FormatExporter> statements,
                                              String annoName) {
            Method m = new Method(name, void.class, null, statements, Modifier.PROTECTED);
            m.addAnnotation(new Annotation(annoName));
            return m;
        }
    }

}


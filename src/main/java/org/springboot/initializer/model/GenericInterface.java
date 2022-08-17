package org.springboot.initializer.model;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.ExportSerializer;
import org.springboot.initializer.SpringBooster;

import javax.lang.model.element.Modifier;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class GenericInterface extends SpringBooster.Base implements ExportSerializer {

    protected Set<ExportPoint> methods = new HashSet<>();
    protected String packageName;
    protected String name;

    protected Set<Annotation> annotations = new HashSet<>();

    protected GenericInterface(){}

    public GenericInterface(Set<ExportPoint> methods, String packageName, String interfaceName) {
        this.methods = methods;
        this.packageName = packageName;
        this.name = interfaceName;
    }

    public void addAnnotation(Annotation annotation) {
        annotations.add(annotation);
    }

    @Override
    public boolean check() {
        return !Strings.isBlank(packageName) && !Strings.isBlank(name);
    }

    @Override
    public void decorateDataType() {}

    @Override
    public void serialize(int lineNum, String line, Path destPath) throws Exception {
    }

    protected TypeSpec.Builder generateBuilder() throws Exception {
        TypeSpec.Builder builder = TypeSpec.interfaceBuilder(name).addModifiers(Modifier.PUBLIC);
        for (Annotation a : annotations) builder.addAnnotation((AnnotationSpec) a.export());
        return builder;
    }

    protected void decorateBuilder(TypeSpec.Builder builder) throws Exception {}

    protected void generateMethods(TypeSpec.Builder builder) throws Exception {
        if (methods != null) {
            for (ExportPoint m : methods) {
                builder.addMethod((MethodSpec)m.export());
            }
        }
    }

    @Override
    public void doSerialize(Path path) throws Exception {
        TypeSpec.Builder builder = generateBuilder();
        generateMethods(builder);
        decorateBuilder(builder);
        serialize(path, packageName, builder.build());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenericInterface that = (GenericInterface) o;
        return Objects.equals(methods, that.methods) && packageName.equals(that.packageName) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methods, packageName, name);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Annotation> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Set<Annotation> annotations) {
        this.annotations = annotations;
    }
}

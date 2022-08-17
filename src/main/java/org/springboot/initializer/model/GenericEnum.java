package org.springboot.initializer.model;

import com.squareup.javapoet.TypeSpec;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportSerializer;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.util.BuiltInMethodFunc;

import javax.lang.model.element.Modifier;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class GenericEnum extends SpringBooster.Base implements ExportSerializer {

    protected String packageName;
    protected String name;
    protected Set<String> values = new HashSet<>();

    public GenericEnum(String packageName, String name, Set<String> values) {
        this.packageName = packageName;
        this.name = name;
        this.values = values;
    }

    @Override
    public void doSerialize(Path path) throws Exception {
        TypeSpec.Builder builder = TypeSpec.enumBuilder(BuiltInMethodFunc.upperFirstCharName(name)).addModifiers(Modifier.PUBLIC);
        values.forEach(v -> builder.addEnumConstant(v));
        serialize(path, packageName, builder.build());
    }

    @Override
    public boolean check() {
        return !Strings.isBlank(packageName) && !Strings.isBlank(name) && !values.isEmpty();
    }

    @Override
    public void decorateDataType() {
    }

    @Override
    public void serialize(int lineNum, String line, Path destPath) throws Exception {
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

    public Set<String> getValues() {
        return values;
    }

    public void setValues(Set<String> values) {
        this.values = values;
    }

}

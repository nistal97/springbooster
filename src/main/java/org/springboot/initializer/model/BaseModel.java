package org.springboot.initializer.model;

import com.squareup.javapoet.TypeName;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.SpringBooster;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class BaseModel extends SpringBooster.Base {
    public static final String BIG_DECIMAL = "java.math.BigDecimal";

    protected String name;
    protected Type type;
    // typename could be translated
    protected TypeName typeName;
    protected Modifier[] modifiers;

    protected Set<Annotation> annotations = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseModel baseModel = (BaseModel) o;
        boolean bEqual = name.equals(baseModel.name) && (Objects.equals(type, baseModel.type)
                || Objects.equals(typeName, baseModel.typeName));
        if (bEqual) return true;
        if (baseModel.type != null) return baseModel.type.getTypeName().equals(typeName.toString());
        else return baseModel.typeName.toString().equals(type.getTypeName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, typeName);
    }

    protected boolean check() {
        return !Strings.isBlank(name) && (type != null || typeName != null);
    }

    public void addAnnotation(Annotation annotation) {
        annotations.add(annotation);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public TypeName getTypeName() {
        return typeName;
    }

    public void setTypeName(TypeName typeName) {
        this.typeName = typeName;
    }

    public String getFieldTypeName() {
        return typeName != null ? typeName.toString() : type.getTypeName();
    }
}

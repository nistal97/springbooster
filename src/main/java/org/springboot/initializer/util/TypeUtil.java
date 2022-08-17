package org.springboot.initializer.util;

import com.squareup.javapoet.*;
import org.springboot.initializer.model.ConfigGraph;
import org.springboot.initializer.model.GenericEnum;
import org.springboot.initializer.model.GenericType;
import org.springboot.initializer.model.ModelMetaData;

public final class TypeUtil {
    public static final String OBJECT_CLASS = "java.lang.Object";

    public static TypeName getGenericListType(String type) {
        ClassName list = ClassName.get("java.util", "List");
        return ParameterizedTypeName.get(list, ClassName.bestGuess(type));
    }

    public static TypeName getGenericListAny() {
        ClassName list = ClassName.get("java.util", "List");
        return ParameterizedTypeName.get(list, WildcardTypeName.subtypeOf(Object.class));
    }

    public static GenericType.FormatExporter getArrayListInitializer() {
        ClassName arrayList = ClassName.get("java.util", "ArrayList");
        return new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                "new $T()", arrayList);
    }

    public static TypeVariableName getMethodGenericTypeVar(String superclass){
        return TypeVariableName.get("T", ClassName.bestGuess(superclass) );
    }

    public static TypeVariableName getMethodGenericTypeVar(){
        return TypeVariableName.get("T");
    }

    public static boolean isPrimitive(String t){
        switch (t) {
            case "int":
            case "short":
            case "long":
            case "double":
            case "float":
            case "boolean":
            case "char":
            case "byte":
            case "String":
                return true;
        }
        if (t.startsWith("java.lang") ||  t.startsWith("java.math")) return true;
        if (isEnum(t)) return true;
        return false;
    }

    // "=="
    public static boolean isPrimitiveEquals(String t){
        switch (t) {
            case "int":
            case "short":
            case "long":
            case "double":
            case "float":
            case "boolean":
            case "char":
            case "byte":
                return true;
        }
        if (isEnum(t)) return true;
        return false;
    }

    public static boolean isEnum(String t) {
        //check enums
        for (ModelMetaData.Enum e : ConfigGraph.getGraph().getModel().getEnums()) {
            if (t.equals(e.getFqcn())) return true;
        }
        return false;
    }

    public static boolean isCollection(String t) {
        if (t.startsWith("java.util.List") || t.startsWith("java.util.Set") || t.startsWith("java.util.Map"))  return true;
        return false;
    }

    public static String extractGenericType(String t) {
        if (isCollection(t)) {
            return t.split("<")[1].split(">")[0];
        }
        return null;
    }

}

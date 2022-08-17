package org.springboot.initializer.util;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.model.*;
import org.springboot.initializer.repo.QueryDSLRepo;
import org.springboot.initializer.repo.Repository;
import org.springboot.initializer.service.Service;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Type;
import java.util.*;

public final class BuiltInMethodFunc {
    public static final char RELATION_DELIMITER = '>';
    public static final char FQCN_DELIMITER = '#';
    public static final String SETTING_DELIMITER = ",";

    public static Method generateBuiltInMethod(String funcName, String var, String returnTypeName,
                                               String identityTypeName, boolean bAbstract, boolean bOverride, boolean bController) {
        List<GenericType.FormatExporter> stmts = new ArrayList<>();
        LinkedHashMap<String, TypeName> params = new LinkedHashMap<>();
        Map<String, Set<Annotation>> paramAnnotations = new HashMap<>();

        Type t = null;
        TypeName typename = null;
        List<Annotation> annotations = new ArrayList<>();

        boolean btransactional = false;
        String[] entityNameTokens = extractPackageAndName(returnTypeName);
        switch (funcName) {
            case "save":
                typename = ClassName.bestGuess(returnTypeName);
                params.put("obj", ClassName.bestGuess(returnTypeName));
                if (!bAbstract) {
                    Set<Annotation> annos = new HashSet<>();
                    annos.add(Annotation.ControllerAnnotationGenerator.generateReqBodyAnnotation());
                    annos.add(Annotation.ControllerAnnotationGenerator.generateValidatorAnnotation());
                    paramAnnotations.put("obj", annos);
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "return $L.saveAndFlush(obj)", var));
                }
                if (bController) {
                    annotations.add(Annotation.ControllerAnnotationGenerator.generatePostMappingAnnotation("/" +
                            entityNameTokens[1].toLowerCase()));
                }
                if (!bAbstract && !bController) {
                    btransactional = true;
                }
                break;
            case "findAll":
                typename = TypeUtil.getGenericListType(returnTypeName);
                if (!bAbstract) stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "return $L.findAll()", var));
                if (bController) {
                    annotations.add(Annotation.ControllerAnnotationGenerator.generateGetMappingAnnotation("/" +
                            entityNameTokens[1].toLowerCase() + "s"));
                }
                break;
            case "deleteById":
                if (!bController) {
                    t = void.class;
                    params.put("id", ClassName.bestGuess(identityTypeName));
                    if (!bAbstract) stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$L.deleteById(id)", var));
                }
                if (!bAbstract && !bController) {
                    btransactional = true;
                }
                break;
        }

        if (btransactional) {
            annotations.add(Annotation.ServiceAnnotationGenerator.generateTransactionAnnotation());
        }

        if (t == null && typename == null) return null;

        Method method = null;
        if (t != null) method = new Method(funcName, t, params, stmts, Modifier.PUBLIC);
        else method = new Method(funcName, typename, params, stmts, Modifier.PUBLIC);

        if (!annotations.isEmpty()) method.getAnnotations().addAll(annotations);
        method.setbAbstract(bAbstract);
        if (bOverride) {
            method.addAnnotation(Annotation.ServiceAnnotationGenerator.generateOverrideAnnotation());
        }
        method.setParamAnnotations(paramAnnotations);
        return method;
    }

    public static void addPagingCtxParam(LinkedHashMap<String, TypeName> params) {
        params.put("pagingContext", ClassName.bestGuess(ConfigGraph.getGraph().getRepo().getPagingContext()));
    }

    public static void addOffsetLimitCtxParam(LinkedHashMap<String, TypeName> params) {
        params.put("offset", TypeName.INT);
        params.put("limit", TypeName.INT);
    }

    public static String[] identifyMasterSlave(String name) {
        return extract(name, RELATION_DELIMITER);
    }

    public static String getListVar(String fqcn) {
        String[] ns = BuiltInMethodFunc.extractPackageAndName(fqcn);
        return ns[1].toLowerCase() + "List";
    }

    public static String convertFQCN2Path(String fqcn) {
        return fqcn.replace('.', '/');
    }

    public static String[] extractPackageAndName(String fqcn) {
        if (fqcn.indexOf(".") == -1) return new String[]{fqcn};
        return extract(fqcn, '.');
    }

    public static String[] extractAbility(String setting) {
        return setting.split(SETTING_DELIMITER);
    }

    public static String[] extractSelectFields(String setting) {
        return setting.split(SETTING_DELIMITER);
    }

    public static String[] extractFQCNAndMethod(String methodSignature) {
        return extract(methodSignature, FQCN_DELIMITER);
    }

    public static String[] extractDTOField(String fieldName) {
        int idx = 0;
        while (!(fieldName.charAt(idx) >= 'A' && fieldName.charAt(idx) <= 'Z')) {
            idx ++;
        }
        String[] arr = new String[2];
        arr[0] = fieldName.substring(0, idx);
        arr[1] = fieldName.substring(idx);
        return arr;
    }

    private static String[] extract(String str, char delimiter) {
        int idx = str.lastIndexOf(delimiter);
        String[] n = new String[2];
        n[0] = str.substring(0, idx);
        n[1] = str.substring(idx + 1);
        return n;
    }

    public static String upperFirstCharName(String name) {
        if (!Strings.isBlank(name)) return name.substring(0, 1).toUpperCase() + name.substring(1);
        return name;
    }

    public static String lowerFirstCharName(String name) {
        if (!Strings.isBlank(name)) return name.substring(0, 1).toLowerCase() + name.substring(1);
        return name;
    }

}

package org.springboot.initializer.repo;

import com.querydsl.core.types.*;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPADeleteClause;
import com.querydsl.jpa.impl.JPAUpdateClause;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import org.assertj.core.util.Lists;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.model.*;
import org.springboot.initializer.service.Service;
import org.springboot.initializer.util.BuiltInMethodFunc;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class QueryDSLRepo extends BaseRepo implements ExportPoint {
    private List<Service.View> viewList;
    private String name;

    public QueryDSLRepo(String name, List<Service.View> viewList) {
        this.viewList = viewList;
        this.name = name;
    }

    public static GenericType exportBaseRepo() throws Exception {
        List<ExportPoint> fields = new ArrayList<>();
        Set<ExportPoint> methods = new HashSet<>();
        fields.add(Field.ModelField.ModelFieldGenerator.generateEntityMgrField());
        fields.add(Field.ModelField.ModelFieldGenerator.generateJpaQueryFactoryField());

        List<GenericType.FormatExporter> stmts = new ArrayList<>();
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "q = new JPAQueryFactory(em)"));
        Method postConstructor = new Method("init", void.class,null, stmts, Modifier.PUBLIC);
        postConstructor.addAnnotation(new Annotation(Annotation.ModelAnnotationGenerator.POST_CONSTRUCTOR));
        methods.add(postConstructor);

        CustomizedRepo.generateAddPagingContextFunc().forEach(method -> methods.add(method));

        GenericType t = new GenericType(null, null, fields,
                methods, ConfigGraph.getGraph().getRepo().getPackageName(), "BaseQueryRepo", false,
                Modifier.PUBLIC, Modifier.ABSTRACT);
        return t;
    }

    @Override
    public GenericType doExport() throws Exception {
        Set<ExportPoint> methods = new HashSet<>();
        for (Service.View view : viewList) {
            List<Method> ms = (List<Method>)view.export();
            ms.forEach(m -> methods.add(m));
        }
        GenericType t = new GenericType(ClassName.bestGuess("BaseQueryRepo").toString(),
                null, null, methods, ConfigGraph.getGraph().getRepo().getPackageName(), name, false,
                Modifier.PUBLIC);
        t.addAnnotation(Annotation.ServiceAnnotationGenerator.generateComponentAnnotation());
        return t;
    }

    public static void generateDTO(Service.View view) {
        String modelPackage = BuiltInMethodFunc.extractPackageAndName(ConfigGraph.getGraph().getModel().getBasemodel())[0];
        List<ExportPoint> fields = new ArrayList<>();
        view.getDtoMap().keySet().forEach(pojoName -> {
            view.getDtoMap().get(pojoName).forEach(fname -> {
                for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().getModels()) {
                    String name = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn())[1].toLowerCase();
                    if (pojoName.equals(name)) {
                        Field sysField = Field.tryCloneSysField(fname);
                        if (sysField != null) {
                            sysField.setName(pojoName + BuiltInMethodFunc.upperFirstCharName(fname));
                            fields.add(sysField);
                            return;
                        }
                        pojo.getFields().forEach(f -> {
                            if (f.getName().equals(fname)) {
                                try {
                                    Field dtoField = f.cloneNewField();
                                    dtoField.setName(pojoName + BuiltInMethodFunc.upperFirstCharName(fname));
                                    fields.add(dtoField);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                                return;
                            }
                        });
                        break;
                    }
                }
            });
        });
        if (!fields.isEmpty()) {
            Set<ExportPoint> methods = new HashSet<>();
            for (ExportPoint e : fields) {
                Field f = (Field) e;
                methods.add(generateGetter(f));
            }

            GenericInterface t = new GenericInterface(methods,
                    modelPackage + ".dto", BuiltInMethodFunc.upperFirstCharName(view.getName()) + "DTO");
            view.setDto(t);

            Set<ExportPoint> implMethods = new HashSet<>();
            //default constructor
            implMethods.add(Method.generateConstructor(Lists.emptyList()));
            //parameterized constructor
            implMethods.add(Method.generateConstructor(fields));
            Set<String> superinterfaces = new HashSet<>();
            superinterfaces.add(t.getPackageName() + "." + t.getName());
            GenericType tImpl = new GenericType(null, superinterfaces,
                    fields, implMethods, modelPackage + ".dto", BuiltInMethodFunc.upperFirstCharName(view.getName()) + "DTOImpl", true,
                    Modifier.PUBLIC);
            tImpl.setCrucialFields(fields.stream().map(e -> ((Field)e).getName()).collect(Collectors.toSet()));
            view.setDtoImpl(tImpl);
        }
    }

    public static Method generateGetter(Field f) {
        Method m = f.getType() != null ? new Method("get" + BuiltInMethodFunc.upperFirstCharName(f.getName()),
                f.getType(), null, null, Modifier.PUBLIC) :
                new Method("get" + BuiltInMethodFunc.upperFirstCharName(f.getName()),
                        f.getTypeName(), null, null, Modifier.PUBLIC);
        m.setbAbstract(true);
        return m;
    }

    public static Method generateUpdater(String name, GenericInterface dto, GenericType dtoImpl,
                                         String[] tableFQArr, String[] ACTION, String where) {
        return generateUpdaterOrDeleter(true, name, dto, dtoImpl, tableFQArr, ACTION, where);
    }

    public static Method generateDeleter(String name, GenericInterface dto, GenericType dtoImpl,
                                         String[] tableFQArr, String[] ACTION, String where) {
        return generateUpdaterOrDeleter(false, name, dto, dtoImpl, tableFQArr, ACTION, where);
    }

    private static Method generateUpdaterOrDeleter(boolean isUpdate, String name, GenericInterface dto, GenericType dtoImpl,
                                                   String[] tableFQArr, String[] ACTION, String where) {
        List<GenericType.FormatExporter> stmts = new ArrayList<>();
        LinkedHashMap<String, TypeName> params = isUpdate ? generateDTOParams(dto) : getAllCandidates(where);

        String clauseName = isUpdate ? "updateClause" : "deleteClause";
        String action = isUpdate ? ACTION[1] : ACTION[2];
        String modelPackage = BuiltInMethodFunc.extractPackageAndName(dto.getPackageName())[0];
        String[] fqqn = tableFQArr[0].split("\\.");
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                "$T $L = q.$L($T.$L)", isUpdate ? JPAUpdateClause.class : JPADeleteClause.class,
                clauseName, isUpdate ? "update" : "delete", ClassName.bestGuess(modelPackage + "." + fqqn[0]), fqqn[1]));
        String pojoName = tableFQArr[0].split("\\.")[1];

        if (isUpdate) {
            CodeBlock.Builder cb = CodeBlock.builder().add(clauseName);
            for (ExportPoint e : dtoImpl.getFields()) {
                Field f = (Field) e;
                if (f.getName().startsWith(pojoName)) {
                    String fName = f.getName().split(pojoName)[1].toLowerCase();
                    if (fName.equalsIgnoreCase("id") || fName.equalsIgnoreCase("created")
                            || fName.equalsIgnoreCase("updated")) {
                        continue;
                    }
                    cb.add(".set($T.$L.$L, obj.get$L())", ClassName.bestGuess(modelPackage + "." + fqqn[0]),
                            fqqn[1], fName, BuiltInMethodFunc.upperFirstCharName(f.getName()));
                }
            }
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, cb.build()));
        }
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK,
                generateWherePredicate(where, action, clauseName).build()));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$L.execute()", clauseName));

        return new Method(action + BuiltInMethodFunc.upperFirstCharName(name), void.class,
                params, stmts, Modifier.PUBLIC);
    }

    public static CodeBlock.Builder generateWherePredicate(String where, String action, String objName) {
        //generate Predicate
        if (where != null) {
            CodeBlock.Builder predicateBuilder = QueryDSLRepo.PredicateBuilder.parse(action, CodeBlock.builder().add(objName +
                    ".where(", Predicate.class), where);
            predicateBuilder.add(")");
            return predicateBuilder;
        }
        return null;
    }

    public static class PredicateBuilder {
        public static final Set<String> BOOLEAN_PREDICATE = new HashSet<>();

        public static String getQName(String name) {
            return "Q" + BuiltInMethodFunc.upperFirstCharName(name);
        }

        public static CodeBlock.Builder parse(String action, CodeBlock.Builder builder, String where) {
            //tablea.fielda==tableafielda OR ((tableb.fieldb==tablebfieldb OR tablec.fieldc=tablecfieldc) AND tabled.fieldd=tabledfieldd)
            //QTablea.tablea.fielda.eq(tableafielda).orAllOf(QTableb.tableb.fieldb.eq(tablebfieldb).or(QTablec.tablec.fieldc.eq(tablecfieldc)).and(QTabled.tabled.fieldd.eq(tabledfieldd)))
            //parentiness is required only following BOOLEAN PREDICATE
            //so only predicate with ( matters, "AND (" -> ".andAllOf(", "OR (" -> ".orAllOf("
            BOOLEAN_PREDICATE.add("AND");
            BOOLEAN_PREDICATE.add("OR");

            String[] arr = where.split(" ");
            String lastToken = null;
            int predicateOpening = 0;

            //preprocess for LIKE
            List<String> list = new ArrayList<>();
            for (int i = 0;i < arr.length;i ++) {
                if (BOOLEAN_PREDICATE.contains(arr[i].toUpperCase())) {
                    if ((i - 1 >= 0 && arr[i - 1].indexOf(OPER_STR[6]) > -1) || (i + 1 < arr.length && arr[i + 1].indexOf(OPER_STR[6]) > -1)) {
                        list.add(list.size() - 1,"OPS#" + arr[i].toUpperCase());
                    }
                }
                list.add(arr[i]);
            }

            boolean predicateMode = false;
            for (int i = 0;i < list.size();i ++) {
                String str = list.get(i);
                if (str.startsWith("OPS#")) {
                    String ops = str.split("#")[1];
                    builder.add("$T.$L($T.$L, ", ExpressionUtils.class, "predicate", Ops.class, ops);
                    predicateMode = true;
                    predicateOpening ++;
                    continue;
                }

                boolean bContainsLeftBracket = str.indexOf("(") > -1;
                boolean bContainsRightBracket = str.indexOf(")") > -1;

                //no brackets,just addpredicate codeblock
                if (!bContainsLeftBracket && !bContainsRightBracket) {
                    if (!BOOLEAN_PREDICATE.contains(str.toUpperCase())) {
                        if (BOOLEAN_PREDICATE.contains(lastToken) && !predicateMode) {
                            addPredicate(builder, lastToken, false);
                            predicateOpening ++;
                        }
                        addPredicateCodeBlock(action, builder, str);
                    } else {
                        //predicateOpening ++;
                    }
                    lastToken = str;
                } else {
                    String[] tokens = null;
                    if (bContainsLeftBracket && BOOLEAN_PREDICATE.contains(lastToken.toUpperCase())) {
                        if (!predicateMode) {
                            addPredicate(builder, lastToken, false);
                            predicateOpening ++;
                        }
                        tokens = str.split("\\(");
                    } else {
                        if (BOOLEAN_PREDICATE.contains(lastToken.toUpperCase())) {
                            if (!predicateMode) {
                                addPredicate(builder, lastToken, false);
                                predicateOpening ++;
                            }
                        }
                        tokens = str.split("\\)");
                    }
                    addPredicateCodeBlock(action, builder, tokens[tokens.length - 1]);
                    int n = str.length() - 1;
                    while (str.charAt(n--) == ')') {
                        builder.add(")");
                        predicateOpening --;
                        predicateMode = false;
                    }
                }
                if (predicateMode && BOOLEAN_PREDICATE.contains(str.toUpperCase())) {
                    builder.add(", ");
                }
            }
            //add closing brackets
            for (int i = 0;i < predicateOpening;i ++) builder.add(")");
            return builder;
        }

        public static List<GenericType.FormatExporter> orderBy(String[] tableFQArr) {
            List<GenericType.FormatExporter> stmts = new ArrayList<>();
            orderBy(stmts, tableFQArr, true);
            orderBy(stmts, tableFQArr,false);
            return stmts;
        }

        private static void orderBy(List<GenericType.FormatExporter> stmts, String[] tableFQArr, boolean asc) {
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW, "for (String f : pagingContext." + (asc ? "getSortasc" : "getSortdesc")  + "())"));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,"$T base = null", EntityPathBase.class));

            for (String str : tableFQArr) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                        "if (f.startsWith($S))", str));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "base = $L", str));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
            }
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "$T<Object> fieldPath = $T.path(Object.class, base, f.split(\"#\")[1])", Path.class, Expressions.class));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "query.orderBy(new $T($T.$L, fieldPath))", OrderSpecifier.class, Order.class, asc ? "ASC" : "DESC"));

            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
        }

        public static List<GenericType.FormatExporter> addDynamicWhereClauses(String[] tableFQArr) {
            //filters
            List<GenericType.FormatExporter> stmts = new ArrayList<>();
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "$T<$T> predicates = new $T<>()", List.class, Predicate.class, ArrayList.class));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                    "for (FilterContext f : pagingContext.getFilters())"));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$T base = null", Path.class));

            for (String str : tableFQArr) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                        "if (f.getField().startsWith($S))", str));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "base = $L", str));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
            }
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "$T path = $T.stringPath(base, f.getField().split(\"#\")[1])", StringPath.class, Expressions.class));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "predicates.add(path.like(f.getPattern()))"));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));

            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                    "if (!predicates.isEmpty())"));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "query.where(predicates.toArray(new Predicate[]{}))"));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
            return stmts;
        }

        private static final String getPredicateOper(String predicate) {
            switch (predicate) {
                case "AND":
                    return "andAllOf";
                case "OR":
                    return "orAllOf";
            }
            return null;
        }

        private static CodeBlock.Builder addPredicate(CodeBlock.Builder builder, String exp, boolean mask) {
            return builder.add(".$L(", mask ? getPredicateOper(exp) : exp.toLowerCase());
        }

        private static CodeBlock.Builder addPredicateCodeBlock(String action, CodeBlock.Builder builder, String exp) {
            String oper = null;
            String[] operatee = null;
            for (String str : OPER_STR) {
                if (exp.indexOf(str) > -1) {
                    oper = str;
                    operatee = exp.split(Pattern.quote(oper));
                    break;
                }
            }
            String[] arr = operatee[0].split("\\.");
            String val = (action.equals(Service.View.ACTION[0]) || action.equals(Service.View.ACTION[2])) ? operatee[1] :
                    "obj.get" + BuiltInMethodFunc.upperFirstCharName(arr[0]) +
                            BuiltInMethodFunc.upperFirstCharName(arr[1]) + "()";
            switch (oper) {
                case "==":
                    builder.add("$L.$L.$L.$L($L)", getQName(arr[0]), arr[0], arr[1], "eq", val);
                    break;
                case "!=":
                    builder.add("$L.$L.$L.$L($L)", getQName(arr[0]), arr[0], arr[1], "ne", val);
                    break;
                case ">":
                    builder.add("$L.$L.$L.$L($L)", getQName(arr[0]), arr[0], arr[1], "gt", val);
                    break;
                case "<":
                    builder.add("$L.$L.$L.$L($L)", getQName(arr[0]), arr[0], arr[1], "lt", val);
                    break;
                case ">=":
                    builder.add("$L.$L.$L.$L($L)", getQName(arr[0]), arr[0], arr[1], "goe", val);
                    break;
                case "<=":
                    builder.add("$L.$L.$L.$L($L)", getQName(arr[0]), arr[0], arr[1], "loe", val);
                case "%":
                    builder.add("$T.$L($T.$L, $L.$L.$L, $T.constant($L + \"%\"))", ExpressionUtils.class, "predicate",
                           Ops.class, Ops.LIKE, getQName(arr[0]), arr[0], arr[1],
                            Expressions.class, arr[0] + BuiltInMethodFunc.upperFirstCharName(arr[1]));
                    break;
                default:
                    break;
            }
            return builder;
        }

        private static String translateOper(String oper) {
            if (OPER_STR[0].equals(oper)) return "=";
            if (OPER_STR[4].equals(oper)) return " like ";
            return oper;
        }

        public static String translate2NativeWhereClause(String where) {
            String[] tokens = where.split(" ");
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (String token : tokens) {
                boolean isExp = false;
                for (String str : OPER_STR) {
                    if (token.indexOf(str) > -1) {
                        String[] arr = token.split(str);
                        String[] field = arr[0].split("\\.");
                        Field.ModelField f = ModelMetaData.POJO.getFieldByName(field[0], field[1]);
                        sb.append(f.getColumn() != null ? f.getColumn() : f.getName()).append(translateOper(str))
                                .append("?").append(i++).append(" ");
                        isExp = true;
                        break;
                    }
                }
                if (!isExp) sb.append(token);
            }
            return sb.toString();
        }

    }

    @Override
    public boolean check() {
        return !viewList.isEmpty();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Service.View> getJoinMethodList() {
        return viewList;
    }

    public void setJoinMethodList(List<Service.View> viewList) {
        this.viewList = viewList;
    }
}

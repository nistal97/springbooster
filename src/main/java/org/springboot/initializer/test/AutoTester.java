package org.springboot.initializer.test;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.springframework.MariaDB4jSpringService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.Assertions;
import org.mockito.internal.util.collections.Sets;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.model.*;
import org.springboot.initializer.repo.BaseRepo;
import org.springboot.initializer.repo.QueryDSLRepo;
import org.springboot.initializer.service.Service;
import org.springboot.initializer.util.BuiltInMethodFunc;
import org.springboot.initializer.util.TypeUtil;

import javax.lang.model.element.Modifier;
import java.math.RoundingMode;
import java.util.*;

public class AutoTester extends SpringBooster.Base implements ExportPoint {
    public static final String BASE_TEST = "BaseTest";
    public static final String TEST_SUTE = "TestSuite";
    public static final String ENV = "ut";

    private String packageName;
    private String componentScanPackage;

    @JsonIgnore
    private ServiceTester serviceTester = new ServiceTester();

    public AutoTester(String packageName, String componentScanPackage) {
        this.packageName = packageName;
        this.componentScanPackage = componentScanPackage;
    }

    public void addAnnotations(GenericType t, String env, boolean rollback) throws ClassNotFoundException {
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateDataJPATestAnnotation());
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateSpringExtAnnotation());
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateActiveProfileAnnotation(env));
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateTestDBAutoConfigAnnotation());
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateTestInstanceAnnotation());
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateRollbackAnnotation(rollback));
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateDirtyCtxAnnotation());
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateComponentScanAnnotation(componentScanPackage));
    }

    @Override
    public List<GenericType> doExport() throws Exception {
        List<GenericType> list = new ArrayList<>();
        list.add(generateBaseTester());
        list.add(generateTestSuite());
        list.addAll((List<GenericType>)serviceTester.export());
        return list;
    }

    @Override
    public boolean check() {
        return !Strings.isBlank(packageName);
    }

    private Set<ExportPoint> generatePOJOGenerator(Set<ExportPoint> methods) {
        Map<String, String> generatorMethods = new HashMap<>();
        for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().getModels()) {
            String[] ns = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn());
            String var = ns[1].toLowerCase();
            TypeName typeName = ClassName.get(ns[0], ns[1]);

            List<GenericType.FormatExporter> stmts = new ArrayList<>();
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,"$T list = new $T()",
                            TypeUtil.getGenericListType(ns[1]), ArrayList.class));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                    "for (int i = 0;i < $L;i ++)", MockMetaData.MockGenerator.FullGraphMockGenerator.MOCK_SIZE));

            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,"$T $L = new $T()",
                    typeName, var, typeName));

            int i = 0;
            for (Field f : pojo.getFields()) {
                boolean bskip = false;
                for (Annotation a : f.getAnnotations()) {
                    switch (a.getName()) {
                        case Annotation.ModelAnnotationGenerator.JOIN_ONE2ONE:
                        case Annotation.ModelAnnotationGenerator.JOIN_ONE2MANY:
                        case Annotation.ModelAnnotationGenerator.JOIN_MANY2ONE:
                        case Annotation.ModelAnnotationGenerator.JOIN_MANY2MANY:
                            bskip = true;
                            break;
                        default:
                            break;
                    }
                    if (bskip) break;
                }
                for (String str : Field.ModelField.ModelFieldGenerator.SYS_FIELDS) {
                    if (f.getName().equalsIgnoreCase(str)) {
                        bskip = true;
                        break;
                    }
                }
                if (bskip) continue;

                boolean bcomplexType = false;
                String format = "$T.";
                TypeName[] randomnizer = new TypeName[]{ClassName.bestGuess("java.util.concurrent.ThreadLocalRandom")};
                String randomCreator = null;
                switch (f.getTypeName().toString()) {
                    case "int":
                        randomCreator = "current().nextInt()";
                        break;
                    case "long":
                        randomCreator = "current().nextLong()";
                        break;
                    case "float":
                        randomCreator = "current().nextFloat()";
                        break;
                    case "double":
                        randomCreator = "current().nextDouble()";
                        break;
                    case "String":
                        randomnizer[0] = ClassName.bestGuess("java.util.UUID");
                        randomCreator = "randomUUID().toString()";
                        break;
                    case "boolean":
                        randomCreator = "current().nextBoolean()";
                        break;
                    default:
                        bcomplexType = true;
                        break;
                }
                if (randomCreator != null) format += randomCreator;
                if (!bcomplexType) {
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$T obj$L = " + format, f.getTypeName(), ++i, randomnizer[0]));
                } else {
                    switch (f.getTypeName().toString()) {
                        case BaseModel.BIG_DECIMAL:
                            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                    "$T obj$L = new $T($T.current().nextDouble()).setScale(2, $T.DOWN)",
                                            f.getTypeName(), ++i, ClassName.bestGuess(BaseModel.BIG_DECIMAL),
                                    ClassName.bestGuess("java.util.concurrent.ThreadLocalRandom"), RoundingMode.class));
                            break;
                        default:
                            //enum
                            for (ModelMetaData.Enum e : ConfigGraph.getGraph().getModel().getEnums()) {
                                if (e.getFqcn().equals(f.getTypeName().toString())) {
                                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                            "$T obj$L = $T.$L", f.getTypeName(), ++i,
                                            f.getTypeName(), e.getValues().toArray(new String[]{})[0]));
                                    break;
                                }
                            }
                            break;
                    }
                }

                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,"$L.set" +
                        BuiltInMethodFunc.upperFirstCharName(f.getName()) + "(obj$L)", var, i));
            }
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "list.add($L)", var));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "return list", var));

            String methodName = ns[1].toLowerCase() + "ListGenerator";
            generatorMethods.put(ns[1].toLowerCase(), methodName);
            Method m = new Method(methodName, TypeUtil.getGenericListType(ns[1]), null, stmts,
                    Modifier.PROTECTED, Modifier.STATIC);
            //m.addThrowable(ClassName.get(Exception.class));
            methods.add(m);
        }
        return methods;
    }

    private GenericType generateBaseTester() throws Exception {
        List<ExportPoint> fields = new ArrayList<>();
        for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().getModels()) {
            String[] ns = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn());
            String var = ns[1].toLowerCase();
            TypeName typeName = ClassName.get(ns[0], ns[1]);
            String listVar = var + "List";
            ServiceTester.serverLists.add(listVar);

            //add list field
            TypeName listFieldType = TypeUtil.getGenericListType(typeName.toString());
            Field listField = new Field(listVar, listFieldType, TypeUtil.getArrayListInitializer(),
                    Modifier.PROTECTED);
            fields.add(listField);
        }

        //add repo
        for (String repo : ConfigGraph.getGraph().getRepo().getRepositoryMap().keySet()) {
            String[] arr = BuiltInMethodFunc.extractPackageAndName(repo);
            Field repoField = new Field(BuiltInMethodFunc.lowerFirstCharName(arr[1]), ClassName.bestGuess(repo), null, Modifier.PROTECTED);
            repoField.addAnnotation(Annotation.ServiceAnnotationGenerator.generateAutowiredAnnotation());
            fields.add(repoField);
        }
        //maria4j
        fields.add(new Field("DB", MariaDB4jSpringService.class, null, Modifier.PROTECTED, Modifier.STATIC));
        //entitymgr
        fields.add(Field.ModelField.ModelFieldGenerator.generateEntityMgrField());

        CodeBlock.Builder mariaINIT = CodeBlock.builder();
        mariaINIT.addStatement("DB = new $T()", MariaDB4jSpringService.class);
        mariaINIT.addStatement("DB.setDefaultPort(3306)");
        mariaINIT.addStatement("DB.start()");
        mariaINIT.beginControlFlow("try ");
        mariaINIT.addStatement("DB.getDB().createDB(\"test\")");
        mariaINIT.addStatement("DB.getDB().source(\"create.sql\", \"test\")");
        mariaINIT.nextControlFlow("catch ($T e)", ManagedProcessException.class);
        mariaINIT.addStatement("throw new RuntimeException(e)");
        mariaINIT.endControlFlow();

        Set<ExportPoint> methods = generatePOJOGenerator(new HashSet<>());
        //generate createPagingContext
        methods.add(createPagingContext());
        //add generateFullGraph
        methods.add((Method)ConfigGraph.getGraph().getMock().getGenerator().getRepoMockGenerator().export());

        GenericType t = new GenericType(null, null, fields, methods, packageName,
                BASE_TEST, false, Modifier.ABSTRACT, Modifier.PUBLIC);
        t.setStaticblock(mariaINIT);
        return t;
    }

    private Method createPagingContext() {
        LinkedHashMap<String, TypeName> params = new LinkedHashMap<>();
        params.put("offset", TypeName.INT);
        params.put("limit", TypeName.INT);
        params.put("asc", TypeUtil.getGenericListType("String"));
        params.put("desc", TypeUtil.getGenericListType("String"));
        params.put("filterContexts", TypeUtil.getGenericListType(ConfigGraph.getGraph().getRepo().getFilterContext()));

        List<GenericType.FormatExporter> stmts = new ArrayList<>();
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$T pagingContext = new $T()",
                ClassName.bestGuess(ConfigGraph.getGraph().getRepo().getPagingContext()),
                ClassName.bestGuess(ConfigGraph.getGraph().getRepo().getPagingContext())));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "pagingContext.setOffset(offset)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "pagingContext.setLimit(limit)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW, "if (asc != null)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "pagingContext.setSortasc(asc)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW, "if (desc != null)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "pagingContext.setSortdesc(desc)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW, "if (filterContexts != null)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "pagingContext.setFilters(filterContexts)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "return pagingContext"));

        Method createPagingContext = new Method("createPagingContext", ClassName.bestGuess(ConfigGraph.getGraph().getRepo().getPagingContext()),
                params, stmts, Modifier.PROTECTED);
        return createPagingContext;
    }

    private GenericType generateTestSuite() throws Exception {
        GenericType t = new GenericType(null, null, null, null, packageName,
                TEST_SUTE, false,  Modifier.PUBLIC);
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateTestSuiteAnnotation());
        t.addAnnotation(Annotation.TestAnnotationGenerator.generateSelectedPkgAnnotation(
                new String[] {packageName + ".service"}));
        return t;
    }

    public class ServiceTester implements ExportPoint {
        private static final String GENERATE_INDIVIDUAL_LIST = "generateIndividualList";

        public static List<String> serverLists = new ArrayList<>();

        @Override
        public List<GenericType> doExport() throws Exception {
            List<GenericType> list = new ArrayList<>();
            for (Service service : ConfigGraph.getGraph().getService().getServices()) {
                List<ExportPoint> fields = new ArrayList<>();

                String[] repoNaming = BuiltInMethodFunc.extractPackageAndName(service.getFqcn());
                //add service
                Field f = new Field(BuiltInMethodFunc.lowerFirstCharName(repoNaming[1]), service.getFqcn(), null, Modifier.PRIVATE);
                f.addAnnotation(Annotation.ServiceAnnotationGenerator.generateAutowiredAnnotation());
                fields.add(f);

                Set<ExportPoint> methods = new HashSet<>();

                String pojoName = BuiltInMethodFunc.extractDTOField(BuiltInMethodFunc.lowerFirstCharName(repoNaming[1]))[0];
                String pojoFQCN = ModelMetaData.POJO.getFQCNByPojoName(pojoName);
                for (ExportPoint e : service.getMethods()) {
                    Method m = (Method) e;
                    List<GenericType.FormatExporter> stmts = generateStmts(f, m, pojoName, pojoFQCN);
                    Method method = new Method("test" + BuiltInMethodFunc.upperFirstCharName(m.getName()),
                            ClassName.VOID, null, stmts, Modifier.PUBLIC);
                    method.addAnnotation(Annotation.TestAnnotationGenerator.generateTestAnnotation());
                    methods.add(method);
                }
                //add generateIndividualList
                methods.add(generateIndividualList(pojoName, pojoFQCN));

                GenericType t = new GenericType(packageName + "." + BASE_TEST, null,
                        fields, methods, packageName + ".service", service.getName() + "Test", false, Modifier.PUBLIC);
                addAnnotations(t, ENV, true);
                list.add(t);
            }
            return list;
        }

        @Override
        public boolean check() {
            return true;
        }

        private List<GenericType.FormatExporter> generateStmts(Field serviceField, Method testee,
                                                               String pojoName, String pojoFQCN)  {
            List<GenericType.FormatExporter> stmts = new ArrayList<>();
            String fname = testee.getName();
            String[] arr = BuiltInMethodFunc.extractDTOField(serviceField.getName());

            //save
            if (fname.equals("save") || fname.startsWith("insert")) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "List<$T> list = $N()", ClassName.bestGuess(pojoFQCN), GENERATE_INDIVIDUAL_LIST));
                Map<String, ModelMetaData.Cascade.Builder>[] abilityMapArr = ModelMetaData.serachCascadeAbilityAsMaster(
                        testee.getTypeName().toString());

                List<GenericType.FormatExporter> relationCountChecker = new ArrayList<>();
                for (int i = 0;i < abilityMapArr.length;i ++) {
                    if (abilityMapArr[i] != null) {
                        for (String k : abilityMapArr[i].keySet()) {
                            ModelMetaData.Cascade.Builder cascadeBuilder = abilityMapArr[i].get(k);
                            if (cascadeBuilder.canCascadePersist()) {
                                String[] kfqn = BuiltInMethodFunc.extractPackageAndName(k);
                                //match count
                                relationCountChecker.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$T.assertEquals(1, $L.count())",
                                        Assertions.class, kfqn[1].toLowerCase() + "Repo"));
                            }
                        }
                    }
                }

                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$L.save(list.get(0))", serviceField.getName()));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$T.assertEquals(1, $L.count())",
                        Assertions.class, arr[0] + "Repo"));
                relationCountChecker.forEach(checker -> stmts.add(checker));
            } else if (fname.equals("findAll")) {
                generateFullGraph(stmts);
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "List<$L> list = $L.findAll()", BuiltInMethodFunc.upperFirstCharName(arr[0]), serviceField.getName()));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "Assertions.assertEquals($LList.size(), list.size())", pojoName));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                        "for (int i = 0;i < list.size();i ++)"));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "Assertions.assertTrue(list.get(i).equals($LList.get(i)))", pojoName));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
            } else if (fname.endsWith("Paged") || fname.startsWith("get")) {
                boolean isPaged = fname.endsWith("Paged");
                generateFullGraph(stmts);
                StringBuilder extraFieldAppender = new StringBuilder();
                int n = 0;
                for (String k : testee.getParams().keySet()) {
                    if (!k.equals("pagingContext")) {
                        if (n > 0) extraFieldAppender.append(", ");
                        String[] paramToken = BuiltInMethodFunc.extractDTOField(k);
                        extraFieldAppender.append(paramToken[0]).append("List.get(0).get").append(paramToken[1]).append("()");
                        if (fname.startsWith("getFilter")) {
                            Service.View view = ConfigGraph.getGraph().getService().getViewByName(fname.split("get")[1]);
                            if (view != null) {
                                String oper = view.getWhereClauseOper(k);
                                if (oper != null && oper.equals(BaseRepo.OPER_STR[6])) {
                                    extraFieldAppender.append(".substring(0, 1)");
                                }
                            }
                        }
                        n ++;
                    }
                }
                if (isPaged) {
                    if (!extraFieldAppender.isEmpty()) {
                        extraFieldAppender.append(", ");
                    }
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$L pagingContext = createPagingContext(10, 10, null, null, null)", ClassName.bestGuess(ConfigGraph.getGraph().getRepo().getPagingContext())));
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$T list = $L.$L($LpagingContext)", testee.getTypeName(), serviceField.getName(), fname, extraFieldAppender.toString()));
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$T.assertEquals(list.size(), 10)", Assertions.class));
                } else {
                    //get
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$T list = $L.$L($L)", testee.getTypeName(), serviceField.getName(), fname, extraFieldAppender.toString()));
                }

                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "$T dtoList = new $T<>()", testee.getTypeName(), ArrayList.class));

                CodeBlock.Builder builder = CodeBlock.builder().add("$LList.forEach(o -> {\n", pojoName);
                GenericType dtoImpl = ConfigGraph.getGraph().getService().getDTOImplByName(getDTOImplFQCN(testee));
                boolean containsListArg = false, incompleteConstruct = false;
                Map<String, String> listArgs = new HashMap<>();
                if (dtoImpl != null) {
                    for (ExportPoint e : dtoImpl.getMethods()) {
                        Method m = (Method) e;
                        if (m.isConstructor() && !m.getParams().isEmpty()) {
                            if (m.getParams().size() < dtoImpl.getFields().size()) {
                                incompleteConstruct = true;
                            }

                            //traverse first time to see if contains list arg
                            for (String k : m.getParams().keySet()) {
                                String[] dtoFieldToken = BuiltInMethodFunc.extractDTOField(k);
                                if (!k.startsWith(pojoName)) {
                                    //first try object type
                                    Field f = ModelMetaData.POJO.getFieldByName(pojoName, dtoFieldToken[0]);
                                    if (f != null && !TypeUtil.isCollection(f.getFieldTypeName())) {
                                    } else {
                                        listArgs.put(f.getName(), f.getFieldTypeName());
                                        containsListArg = true;
                                        break;
                                    }
                                }
                            }
                            if (containsListArg) {
                                listArgs.forEach((k, v) -> builder.beginControlFlow("for ($T obj : o.get$L())",
                                        ClassName.bestGuess(TypeUtil.extractGenericType(v)), BuiltInMethodFunc.upperFirstCharName(k)));                            } else {
                            }
                            if (incompleteConstruct) {
                                builder.add("$T obj = new $T();\n", ClassName.bestGuess(dtoImpl.getPackageName() + "." + dtoImpl.getClassName()),
                                        ClassName.bestGuess(dtoImpl.getPackageName() + "." + dtoImpl.getClassName()));
                                dtoImpl.getFields().forEach(ee -> {
                                    Field f = (Field) ee;
                                    String[] dtoFieldToken = BuiltInMethodFunc.extractDTOField(f.getName());
                                    builder.add("obj.set$L(", BuiltInMethodFunc.upperFirstCharName(f.getName()));
                                    addExistingDTOFieldInitializer(builder, f.getName(), pojoName, dtoFieldToken);
                                    builder.add(");\n");
                                });
                            }
                            //traverse second time to render
                            if (!incompleteConstruct) {
                                builder.add("dtoList.add(new $T(", ClassName.bestGuess(getDTOImplFQCN(testee)));
                                int i = 0;
                                for (String k : m.getParams().keySet()) {
                                    if (i > 0) builder.add(", ");
                                    String[] dtoFieldToken = BuiltInMethodFunc.extractDTOField(k);
                                    addExistingDTOFieldInitializer(builder, k, pojoName, dtoFieldToken);
                                    i ++;
                                }
                                builder.add("));\n");
                            } else {
                                builder.add("dtoList.add(obj);\n");
                            }
                        }
                    }
                }
                if (containsListArg) {
                    listArgs.forEach((k, v) -> builder.endControlFlow());
                }
                builder.add("})");
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build()));
                compareListObjects(stmts, testee, isPaged);

                //sorting
                if (dtoImpl != null && isPaged) {
                    Field f = (Field) dtoImpl.getFields().get(0);
                    //one field asc/desc
                    sortByField(stmts, f.getName(), true, getDTOFQCN(testee), serviceField.getName(), fname, extraFieldAppender, testee);
                    sortByField(stmts, f.getName(), false, getDTOFQCN(testee), serviceField.getName(), fname, extraFieldAppender, testee);
                    //DISABLE try all fields for now as we generate all random no duplicate field values
                    /**
                    int i = 1;
                    Set<String> ascs = Sets.newSet(), descs = Sets.newSet();
                    for (ExportPoint e : dtoImpl.getFields()) {
                        if ((i++ & 1) == 1) {
                            ascs.add(((Field)e).getName());
                        } else {
                            descs.add(((Field)e).getName());
                        }
                    }
                    sortByField(stmts, ascs, descs, dtoFQCN, serviceField.getName(), fname, extraFieldAppender, testee);**/
                }
            } else if (fname.startsWith("update")) {
                generateFullGraph(stmts);
                CodeBlock.Builder builder = CodeBlock.builder();
                GenericType dtoImpl = ConfigGraph.getGraph().getService().getDTOImplByName(getDTOImplFQCN(testee));
                if (dtoImpl != null) {
                    for (ExportPoint e : dtoImpl.getMethods()) {
                        Method m = (Method) e;
                        if (m.isConstructor() && !m.getParams().isEmpty()) {
                            //we need to identify if the field is in where, if so then do not update
                            Service.View view = ConfigGraph.getGraph().getService().getViewByName(fname.split("update")[1]);
                            //first generate new randoms
                            for (String k : m.getParams().keySet()) {
                                boolean isInWhereClause = view.isWhereContainsField(k);
                                if (!isInWhereClause) {
                                    boolean bcomplexType = false;
                                    String format = "$T.";
                                    TypeName[] randomnizer = new TypeName[]{ClassName.bestGuess("java.util.concurrent.ThreadLocalRandom")};
                                    String randomCreator = null;
                                    switch (m.getParams().get(k).toString()) {
                                        case "int":
                                            randomCreator = "current().nextInt()";
                                            break;
                                        case "long":
                                            randomCreator = "current().nextLong()";
                                            break;
                                        case "float":
                                            randomCreator = "current().nextFloat()";
                                            break;
                                        case "double":
                                            randomCreator = "current().nextDouble()";
                                            break;
                                        case "String":
                                            randomnizer[0] = ClassName.bestGuess("java.util.UUID");
                                            randomCreator = "randomUUID().toString()";
                                            break;
                                        case "boolean":
                                            randomCreator = "current().nextBoolean()";
                                            break;
                                        default:
                                            bcomplexType = true;
                                            break;
                                    }
                                    if (randomCreator != null) format += randomCreator;
                                    builder.add("$T $L = ", m.getParams().get(k), k);
                                    if (!bcomplexType) {
                                        builder.add(format);
                                    } else {
                                        switch (m.getParams().get(k).toString()) {
                                            case BaseModel.BIG_DECIMAL:
                                                builder.add("new $T($T.current().nextDouble()).setScale(2, $T.DOWN)",
                                                        ClassName.bestGuess(BaseModel.BIG_DECIMAL),
                                                        ClassName.bestGuess("java.util.concurrent.ThreadLocalRandom"), RoundingMode.class);
                                                break;
                                            default:
                                                //enum
                                                for (ModelMetaData.Enum _enum : ConfigGraph.getGraph().getModel().getEnums()) {
                                                    if (_enum.getFqcn().equals(m.getParams().get(k).toString())) {
                                                        builder.add("$T.$L", m.getParams().get(k), _enum.getValues().toArray(new String[]{})[0]);
                                                        break;
                                                    }
                                                }
                                                break;
                                        }
                                    }
                                    builder.add(";");
                                }
                            }
                        }
                    }
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build()));
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$T o = $LList.get(0)",
                            ClassName.bestGuess(BuiltInMethodFunc.upperFirstCharName(arr[0])), arr[0]));
                    builder.clear();
                    builder.add("$T obj = new $T(", ClassName.bestGuess(getDTOImplFQCN(testee)), ClassName.bestGuess(getDTOImplFQCN(testee)));
                    int j = 0;
                    Set<String> updatedFields = new HashSet<>();
                    for (ExportPoint e : dtoImpl.getMethods()) {
                        Method m = (Method) e;
                        if (m.isConstructor() && !m.getParams().isEmpty()) {
                            Service.View view = ConfigGraph.getGraph().getService().getViewByName(fname.split("update")[1]);
                            //now we generate constructor
                            for (String k : m.getParams().keySet()) {
                                if (j > 0) builder.add(", ");
                                String[] dtoFieldToken = BuiltInMethodFunc.extractDTOField(k);
                                boolean isInWhereClause = view.isWhereContainsField(k);
                                if (isInWhereClause) {
                                    addExistingDTOFieldInitializer(builder, k, pojoName, dtoFieldToken);
                                } else {
                                    updatedFields.add(k);
                                    builder.add("$L", k);
                                }
                                j ++;
                            }
                        }
                    }
                    builder.add(")");
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build()));

                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$L.$L(obj)",
                            serviceField.getName(), testee.getName()));
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$T<$T> $L = $LRepo.findById($LList.get(0).getId())", Optional.class, ClassName.bestGuess(BuiltInMethodFunc.upperFirstCharName(arr[0])),
                            arr[0], arr[0], arr[0]));
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$T.assertNotNull($L.get())",
                            Assertions.class, arr[0]));
                    updatedFields.forEach(k -> {
                        String[] dtoFieldToken = BuiltInMethodFunc.extractDTOField(k);
                        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$T.assertEquals($L.get().get$L(), obj.get$L())",
                                Assertions.class, arr[0], BuiltInMethodFunc.upperFirstCharName(dtoFieldToken[1]), BuiltInMethodFunc.upperFirstCharName(k)));
                    });
                }
            } else if (fname.startsWith("delete")) {
                generateFullGraph(stmts);
                //remove relationships
                Queue<String> queue = new LinkedList<>();
                queue.add(pojoFQCN);

                Set<String> cascadeRelationRecorder = new HashSet<>();
                for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().getModels()) {
                    String[] thisPojoTokens = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn());
                    List<ModelMetaData.One2One> relations = ModelMetaData.searchRelation(thisPojoTokens[1], pojoName);
                    if (!relations.isEmpty()) {
                        for (ModelMetaData.One2One relation : relations) {
                            for (ModelMetaData.Cascade cascade : relation.getCascades()) {
                                String[] cascader = BuiltInMethodFunc.identifyMasterSlave(cascade.getRelation());
                                if ((pojoFQCN.equalsIgnoreCase(cascader[0]) || pojoFQCN.equalsIgnoreCase(cascader[1])) &&
                                                (!cascadeRelationRecorder.contains(relation.getName()))) {
                                    cascadeRelationRecorder.add(relation.getName());
                                    CodeBlock.Builder cb = CodeBlock.builder();
                                    String thisPojoName = BuiltInMethodFunc.lowerFirstCharName(thisPojoTokens[1]);
                                    String fieldInThisPojo = relation.getClassA().equalsIgnoreCase(pojoFQCN) ? relation.getFieldB() : relation.getFieldA();
                                    Field.ModelField modelField = ModelMetaData.POJO.getFieldByName(thisPojoName, fieldInThisPojo);
                                    if (modelField != null) {
                                        cb.add("$LList.forEach($L -> {\n", thisPojoName, thisPojoName);
                                        if (TypeUtil.isCollection(modelField.getFieldTypeName())) {
                                            cb.beginControlFlow("if ($L.get$L().contains($LList.get(0)))", thisPojoName,
                                                    BuiltInMethodFunc.upperFirstCharName(fieldInThisPojo), pojoName);
                                            cb.add("$L.get$L().remove($LList.get(0));\n", thisPojoName, BuiltInMethodFunc.upperFirstCharName(fieldInThisPojo), pojoName);
                                        } else {
                                            cb.beginControlFlow("if ($L.get$L().equals($LList.get(0)))", thisPojoName,
                                                    BuiltInMethodFunc.upperFirstCharName(fieldInThisPojo), pojoName);
                                            cb.add("$L.set$L(null);\n", thisPojoName, BuiltInMethodFunc.upperFirstCharName(fieldInThisPojo));
                                        }
                                        cb.add("$LRepo.saveAndFlush($L);", thisPojoName, thisPojoName);
                                        cb.endControlFlow();
                                        cb.add("})");
                                        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, cb.build()));
                                    }
                                }
                            }
                        }
                    }
                }

                CodeBlock.Builder builder = CodeBlock.builder().add("$L.$L(", serviceField.getName(), testee.getName());
                int i = 0;
                StringBuilder sb = new StringBuilder();
                boolean filterMode = false;
                for (String k : testee.getParams().keySet()) {
                    if (i > 0) builder.add(", ");
                    TypeName t = testee.getParams().get(k);
                    if (TypeUtil.isPrimitive(t.toString())) {
                        if (k.equals(k.toLowerCase())) {
                            builder.add("$LList.get(0).get$L()", arr[0], BuiltInMethodFunc.upperFirstCharName(k));
                        } else {
                            filterMode = true;
                            String[] dtoArr = BuiltInMethodFunc.extractDTOField(k);
                            if (dtoArr[0].equals(pojoName)) {
                                builder.add("$LList.get(0).get$L()", arr[0], BuiltInMethodFunc.upperFirstCharName(dtoArr[1]));
                            } else {
                                builder.add("$LList.get(0).get$L().get$L()", arr[0], BuiltInMethodFunc.upperFirstCharName(dtoArr[0]),
                                        BuiltInMethodFunc.upperFirstCharName(dtoArr[1]));
                            }
                        }
                    } else {
                        filterMode = true;
                        String[] dtoArr = BuiltInMethodFunc.extractDTOField(k);
                        builder.add("$LList.get(0).get$L()", arr[0], BuiltInMethodFunc.upperFirstCharName(dtoArr[1]));
                    }
                    i ++;
                }
                builder.add(")");
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build()));
                if (!filterMode) {
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$T.assertEquals($LList.size() - 1, $LRepo.count())", Assertions.class, arr[0], arr[0]));
                } else {
                    Service.View view = ConfigGraph.getGraph().getService().getViewByName(fname.split("delete")[1]);
                    int j = 0;
                    for (String k : testee.getParams().keySet()) {
                        if (j > 0) sb.append(" && ");
                        String[] dtoArr = BuiltInMethodFunc.extractDTOField(k);
                        String oper = view.getWhereClauseOper(k);
                        generateFilterLambdas(pojoName, sb, oper, testee, k, dtoArr[1]);
                        j ++;
                    }
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$T.assertEquals($LList.stream().filter(o -> !($L)).count(), $LRepo.count())", Assertions.class, arr[0],
                                    sb.toString(), arr[0]));
                }
            }
            return stmts;
        }

        private void addExistingDTOFieldInitializer(CodeBlock.Builder builder, String k, String pojoName, String[] dtoFieldToken) {
            if (k.startsWith(pojoName)) {
                builder.add("o.get$L()", dtoFieldToken[1]);
            } else {
                //first try object type
                Field f = ModelMetaData.POJO.getFieldByName(pojoName, dtoFieldToken[0]);
                if (f != null && !TypeUtil.isCollection(f.getFieldTypeName())) {
                    builder.add("o.get$L().get$L()", BuiltInMethodFunc.upperFirstCharName(dtoFieldToken[0]),
                            dtoFieldToken[1]);
                } else {
                    //the it should be collection
                    builder.add("obj.get$L()", dtoFieldToken[1]);
                }
            }
        }

        private Method generateIndividualList(String pojo, String fqcn) {
            List<GenericType.FormatExporter> stmts = new ArrayList<>();

            Map<String, ModelMetaData.Cascade.Builder>[] abilityMapArr = ModelMetaData.serachCascadeAbilityAsMaster(fqcn);
            //generate multiple graphs but not serialize
            boolean isEmptyCascadeRelation = true;
            for (int i = 0;i < abilityMapArr.length;i ++) {
                if (abilityMapArr[i] != null) {
                    for (ModelMetaData.Cascade.Builder builder : abilityMapArr[i].values()) {
                        if (builder.canCascadePersist() || builder.canCascadeUpdate() || builder.canCascadeRemove()) {
                            if (isEmptyCascadeRelation) {
                                isEmptyCascadeRelation = false;
                                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                        "List<$T> $LList = $LListGenerator()", ClassName.bestGuess(fqcn), pojo, pojo));
                            }
                            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                    "List<$T> $LList = $LListGenerator()",
                                    ClassName.bestGuess(builder.getSlave()), builder.getMaster(),
                                    BuiltInMethodFunc.extractPackageAndName(builder.getSlave())[1].toLowerCase()));
                        }
                    }
                }
            }
            if (isEmptyCascadeRelation) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "return $LListGenerator()", pojo));
            } else {
                //reset cause we have to traverse again
                isEmptyCascadeRelation = true;
                for (int i = 0;i < abilityMapArr.length;i ++) {
                    if (abilityMapArr[i] != null) {
                        for (String k : abilityMapArr[i].keySet()) {
                            ModelMetaData.Cascade.Builder cascadeBuilder = abilityMapArr[i].get(k);
                            if (cascadeBuilder.canCascadePersist() || cascadeBuilder.canCascadeUpdate()
                                            || cascadeBuilder.canCascadeRemove()) {
                                if (isEmptyCascadeRelation) {
                                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                                            "for (int i = 0;i < $LList.size();i ++)", pojo));
                                    isEmptyCascadeRelation = false;
                                }

                                String candidatePOJO = cascadeBuilder.getMaster().toLowerCase();
                                if (i == 0) {
                                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                            "$LList.get(i).set$L($LList.get(i).clone())", pojo,
                                            BuiltInMethodFunc.upperFirstCharName(abilityMapArr[i].get(k).getMaster()), candidatePOJO));
                                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                            "$LList.get(i).get$L().set$L($LList.get(i))", pojo,
                                            BuiltInMethodFunc.upperFirstCharName(abilityMapArr[i].get(k).getMaster()),
                                            BuiltInMethodFunc.upperFirstCharName(pojo), pojo));
                                } else {
                                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                            "$LList.get(i).get$L().add($LList.get(i).clone())", pojo,
                                            BuiltInMethodFunc.upperFirstCharName(abilityMapArr[i].get(k).getMaster()), candidatePOJO));
                                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                            "$LList.get(i).get$L().get($LList.get(i).get$L().size() - 1).set$L($LList.get(i))", pojo,
                                            BuiltInMethodFunc.upperFirstCharName(abilityMapArr[i].get(k).getMaster()), pojo,
                                            BuiltInMethodFunc.upperFirstCharName(abilityMapArr[i].get(k).getMaster()),
                                            BuiltInMethodFunc.upperFirstCharName(pojo), pojo));
                                }
                            }
                        }
                    }
                }
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
            }
            if (!isEmptyCascadeRelation) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "return $LList", pojo));
            }

            Method method = new Method(GENERATE_INDIVIDUAL_LIST, TypeUtil.getGenericListType(fqcn), null, stmts, Modifier.PUBLIC);
            return method;
        }
    }

    private String getDTOFQCN(Method testee) {
        String fieldTypeName = testee.getFieldTypeName();
        if (!fieldTypeName.equalsIgnoreCase("void")) {
            return fieldTypeName.split("<")[1].split(">")[0];
        }
        for (TypeName t : testee.getParams().values()) {
            if (!TypeUtil.isPrimitive(t.toString())) {
                return t.toString();
            }
        }
        return null;
    }

    private String getDTOImplFQCN(Method testee) {
        //here could be possible dto or dtoimpl already
        String dtoFQCN = getDTOFQCN(testee);
        String dtoImplFQCN = dtoFQCN.endsWith("Impl") ? dtoFQCN : dtoFQCN + "Impl";
        return dtoImplFQCN;
    }

    private void generateFullGraph(List<GenericType.FormatExporter> stmts) {
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "generateFullGraph()"));
    }

    private void sortByField(List<GenericType.FormatExporter> stmts, String fieldName, boolean bAsc,
                             String dtoFQCN, String serviceName, String funcName, StringBuilder extraFieldAppender, Method method) {
        if (bAsc) {
            sortByField(stmts, Sets.newSet(fieldName), null, dtoFQCN, serviceName, funcName, extraFieldAppender, method);
        } else {
            sortByField(stmts, null, Sets.newSet(fieldName), dtoFQCN, serviceName, funcName, extraFieldAppender, method);
        }
    }

    private void addComparingClause(CodeBlock.Builder builder, Set<String> set, String dtoFQCN, boolean basc, boolean createHead) {
        int i = 0;
        for (String str : set) {
            if (i == 0 && createHead) {
                builder.add("$T.comparing($L::$L)", Comparator.class, dtoFQCN, "get" + BuiltInMethodFunc.upperFirstCharName(str));
            } else {
                builder.add(".thenComparing($L::$L)", dtoFQCN, "get" + BuiltInMethodFunc.upperFirstCharName(str));
            }
            i ++;
            if (!basc) builder.add(".reversed()");
        }
    }

    private void sortByField(List<GenericType.FormatExporter> stmts, Set<String> ascs, Set<String> descs,
                             String dtoFQCN, String serviceName, String funcName, StringBuilder extraFieldAppender, Method method) {

        CodeBlock.Builder builder = CodeBlock.builder().add("$T.sort(dtoList, ", Collections.class);
        if (ascs != null) addComparingClause(builder, ascs, dtoFQCN, true, true);
        if (descs != null) addComparingClause(builder, descs, dtoFQCN, false, ascs == null);
        builder.add(")");
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build()));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "pagingContext.getSortasc().clear()"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "pagingContext.getSortdesc().clear()"));
        if (ascs != null) {
            ascs.forEach(asc -> stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "pagingContext.getSort$L().add($S)", "asc", asc)));
        }
        if (descs != null) {
            descs.forEach(desc -> stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "pagingContext.getSort$L().add($S)", "desc", desc)));
        }
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                "list = $L.$L($LpagingContext)", serviceName, funcName, extraFieldAppender.toString()));
        compareListObjects(stmts, method, true);
    }

    private void generateFilterLambdas(String pojoName, StringBuilder sb, String oper, Method method, String k, String... fieldName) {
        String[] paramToken = BuiltInMethodFunc.extractDTOField(k);
        if (oper != null) {
            if (oper.equals(BaseRepo.OPER_STR[1])) {
                sb.append("!");
            }
            sb.append("o.get");
            boolean nested = false;
            if (pojoName != null && !k.startsWith(pojoName)) {
                nested = true;
                sb.append(BuiltInMethodFunc.upperFirstCharName(paramToken[0]) + "().get");
            }
            sb.append(BuiltInMethodFunc.upperFirstCharName(fieldName.length > 0 ? fieldName[0] : k)).append("()");
            boolean substringMode = false;
            if (oper.equals(BaseRepo.OPER_STR[0]) || oper.equals(BaseRepo.OPER_STR[1])) {
                TypeName t = method.getParams().get(k);
                if (TypeUtil.isPrimitiveEquals(t.toString())) {
                    sb.append(" == ");
                } else {
                    sb.append(".equals");
                }
            } else if (oper.equals(BaseRepo.OPER_STR[2]) || oper.equals(BaseRepo.OPER_STR[3])
                    || oper.equals(BaseRepo.OPER_STR[4]) || oper.equals(BaseRepo.OPER_STR[5])) {
                sb.append(" ").append(oper).append(" ");
            } else if (oper.equals(BaseRepo.OPER_STR[6])) {
                sb.append(".startsWith");
                substringMode = true;
            }
            if (!nested) {
                sb.append("(").append(paramToken[0]).append("List.get(0).get").append(paramToken[1]);
            } else {
                sb.append("(").append(pojoName).append("List.get(0).get").append(BuiltInMethodFunc.upperFirstCharName(paramToken[0]))
                        .append("().get").append(paramToken[1]);
            }
            sb.append("()" +
                    (substringMode ? ".substring(0, 1)" : "") + ")");
        }
    }

    private void compareListObjects(List<GenericType.FormatExporter> stmts, Method method, boolean isPaged) {
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                "for (int i = 0;i < list.size();i ++)"));
        StringBuilder sb = new StringBuilder("");
        int i = 0;
        String fname = method.getName().endsWith("Paged") ? method.getName().split("Paged")[0] : method.getName();
        Service.View view = ConfigGraph.getGraph().getService().getViewByName(fname.split("get")[1]);
        if (view != null) {
            String[] tokens = null;
            if (view.getWhere() != null) {
                tokens = view.getWhere().split(" ");
            } else if (view.getSql() != null && !view.getSql().isEmpty() && view.getSql().get(0).indexOf(" where ") > -1){
                tokens = view.translateNativeSQLToCommonWhere().split(" where ")[1].split(" ");
            }

            if (tokens != null) {
                for (String str : tokens) {
                    if (QueryDSLRepo.PredicateBuilder.BOOLEAN_PREDICATE.contains(str.toUpperCase())) {
                        sb.append("AND".equalsIgnoreCase(str) ? " && " : " || ");
                    }

                    for (String k : method.getParams().keySet()) {
                        if (str.contains(k)) {
                            if (str.startsWith("(")) sb.append("(");
                            String oper = view.getWhereClauseOper(k);
                            generateFilterLambdas(null, sb, oper, method, k);
                            if (str.endsWith(")")) sb.append(")");
                            break;
                        }
                    }
                }
            }
        }

        if (sb.isEmpty()) {
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "$T.assertTrue(list.contains(dtoList.get($Li)))",
                    Assertions.class, isPaged ? "pagingContext.getOffset() + " : ""));
        } else{
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "$T.assertTrue(list.contains(dtoList.stream().filter(o->$L).toList().get($Li)))",
                    Assertions.class, sb.toString(), isPaged ? "pagingContext.getOffset() + " : ""));
        }

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));

        if (sb.isEmpty()) {
            if (!isPaged) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "$T.assertEquals(list.size(), dtoList.size())", Assertions.class));
            }
        } else{
            if (!isPaged) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "$T.assertEquals(list.size(), dtoList.stream().filter(o->$L).toList().size())", Assertions.class, sb.toString()));
            }
        }
    }

}

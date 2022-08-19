package org.springboot.initializer.service;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQuery;
import com.squareup.javapoet.*;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.model.ConfigGraph;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.model.*;
import org.springboot.initializer.repo.BaseRepo;
import org.springboot.initializer.repo.CustomizedRepo;
import org.springboot.initializer.repo.QueryDSLRepo;
import org.springboot.initializer.repo.Repository;
import org.springboot.initializer.util.BuiltInMethodFunc;
import org.springboot.initializer.util.TypeUtil;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class Service extends GenericInterface {

    protected String fqcn;
    private ServiceImpl impl = new ServiceImpl();
    private List<String> repos = new ArrayList<>();
    private List<GenericType> queryDSLRepos = new ArrayList<>();
    private List<String> builtinMethods = new ArrayList<>();
    private List<Method> graphQLMethods = new ArrayList<>();
    private List<View> views = new ArrayList<>();

    public Service(){
    }

    public Service(Set<ExportPoint> methods, String packageName, String interfaceName) {
        super(methods, packageName, interfaceName);
    }

    @Override
    public boolean check() {
        return super.check();
    }

    @Override
    public void decorateDataType() {
        if (!Strings.isBlank(fqcn)) {
            String[] arr = BuiltInMethodFunc.extractPackageAndName(fqcn);
            packageName = arr[0];
            name = arr[1];
        }
    }

    @Override
    protected void decorateBuilder(TypeSpec.Builder builder) throws Exception {
        //add builtin methods
        methods.addAll(getBuiltInMethod(true, false));
        //add QueryDSLmethods
        queryDSLRepos.forEach(repo -> {
            repo.getMethods().forEach(e -> {
                Method m = new Method((Method) e);
                m.setbAbstract(true);
                m.clearStatements();
                methods.add(m);
            });
        });

        for (ExportPoint m : methods) {
            builder.addMethod((MethodSpec) m.export());
        }
    }

    private Set<ExportPoint> getBuiltInMethod(boolean bAbstract, boolean bOverride) {
        Set<ExportPoint> methods = new HashSet<>();
        for (String name : builtinMethods) {
            String[] funcNaming = BuiltInMethodFunc.extractFQCNAndMethod(name);
            Repository repo = ConfigGraph.getGraph().getRepo().getRepositoryMap().get(funcNaming[0]);
            Method method = BuiltInMethodFunc.generateBuiltInMethod(funcNaming[1], repo.getName().toLowerCase(),
                    repo.getEntityType(), repo.getIdentityType(), bAbstract, bOverride, false);
            if (method != null) methods.add(method);
        }

        return methods;
    }

    public void addQueryDSLRepo(GenericType repo) {
        queryDSLRepos.add(repo);
    }

    public static class View implements ExportPoint {
        private String name;
        private String field;
        private String where;
        private boolean paging = false;
        private String generator = GENERATOR[0];
        private List<String> sql = new ArrayList<>();
        private String action = ACTION[0];
        private List<View> graphs = new ArrayList<>();
        private Set<Method> graphQLMethods = new HashSet<>();
        private Map<String, List<String>> dtoFields = new LinkedHashMap<>();
        private GenericInterface dto;
        private GenericType dtoImpl;
        private boolean dtoViewParam = false;
        private boolean graphql = false;

        public static final String[] GENERATOR = new String[]{"querydsl", "customrepo"};
        public static final String[] ACTION = new String[]{"get", "update", "delete"};

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public boolean isPaging() {
            return paging;
        }

        public void setPaging(boolean paging) {
            this.paging = paging;
        }

        public String getGenerator() {
            return generator;
        }

        public void setGenerator(String generator) {
            this.generator = generator;
        }

        public List<View> getGraphs() {
            return graphs;
        }

        public void setGraphs(List<View> graphs) {
            this.graphs = graphs;
        }

        public List<String> getSql() {
            return sql;
        }

        public void setSql(List<String> sql) {
            this.sql = sql;
        }

        public String getWhere() {
            return where;
        }

        public void setWhere(String where) {
            this.where = where;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Map<String, List<String>> getDtoMap() {
            return dtoFields;
        }

        public void setDtoMap(Map<String, List<String>> dtoMap) {
            this.dtoFields = dtoMap;
        }

        public GenericInterface getDto() {
            return dto;
        }

        public void setDto(GenericInterface dto) {
            this.dto = dto;
        }

        public GenericType getDtoImpl() {
            return dtoImpl;
        }

        public void setDtoImpl(GenericType dtoImpl) {
            this.dtoImpl = dtoImpl;
        }

        public boolean isDtoViewParam() {
            return dtoViewParam;
        }

        public void setDtoViewParam(boolean dtoViewParam) {
            this.dtoViewParam = dtoViewParam;
        }

        public boolean isGraphql() {
            return graphql;
        }

        public void setGraphql(boolean graphql) {
            this.graphql = graphql;
        }

        public Set<Method> getGraphQLMethods() {
            return graphQLMethods;
        }

        @Override
        public List<Method> doExport() throws Exception {
            List<Method> methods = new ArrayList<>();

            //generate jpaquery stmt
            List<GenericType.FormatExporter> stmts = new ArrayList<>(), pagedStmts = new ArrayList<>();
            String[] arr = field.split(",");
            CodeBlock.Builder builder = null;
            String[] fqfn = arr[0].split("\\.");

            TypeName typeName = ClassName.get(Tuple.class);
            int fieldNum = collectFieldCount(this, 0);
            if (fieldNum == 1) {
                Field f = ModelMetaData.POJO.getFieldByName(fqfn[0], fqfn[1]);
                typeName = f.getTypeName();
                builder = CodeBlock.builder().add("$T<$T> query = q.select(", JPAQuery.class, typeName);
            } else {
                builder = CodeBlock.builder().add("$T<$T> query = q.select(", JPAQuery.class, Tuple.class);
            }

            CustomizedRepo.GraphLevel root = new CustomizedRepo.GraphLevel();
            //main graph
            int fieldSeq = 1;
            //record DTO fields
            for (int i = 0;i < arr.length;i ++) {
                String[] tfqfn = arr[i].split("\\.");
                fieldSeq = addSelectField(fieldSeq, builder, root, tfqfn[0], tfqfn[1]);
            }
            //subgraphs
            traverseSubGraph(fieldSeq, this, builder, root);
            //disable generate DTO when we don't have subgraph,
            //if (graphs.isEmpty()) dtoFields.clear();
            //export DTO & DTOImpl
            QueryDSLRepo.generateDTO(this);

            Queue<CustomizedRepo.GraphLevel> queue = new LinkedList();
            List<String> tableFQ = new ArrayList<>();
            //get from
            for (String fname : root.getFields()) {
                builder.add(").from($L)", fname);
                tableFQ.add(fname);
                queue.add(root);
                break;
            }
            while (!queue.isEmpty()) {
                CustomizedRepo.GraphLevel node = queue.poll();
                String parentFQName = null;
                for (String fname : node.getFields()) {
                    parentFQName = fname;
                    break;
                }

                String[] lastJoinTableToken = parentFQName.split("\\.");
                for (CustomizedRepo.GraphLevel child : node.getChildren()) {
                    for (String fname : child.getFields()) {
                        List<ModelMetaData.One2One> relation = ModelMetaData.searchRelation(lastJoinTableToken[1],
                                    fname.split("\\.")[1]);
                        if (!relation.isEmpty()) {
                            //here we just need join info, pick up the first
                            String[] classAPOJO = BuiltInMethodFunc.extractPackageAndName(relation.get(0).getClassA());
                            builder.add(".innerJoin($L, $L)", parentFQName + "." + (classAPOJO[1].equalsIgnoreCase(lastJoinTableToken[1]) ?
                                    relation.get(0).getFieldA() : relation.get(0).getFieldB()), fname);
                        }
                        tableFQ.add(fname);
                        break;
                    }
                    queue.add(child);
                }
            }

            String[] tableFQArr = tableFQ.toArray(new String[]{});

            GenericType.FormatExporter selecter = new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build());
            //paging
            builder.add(".offset(pagingContext.getOffset()).limit(pagingContext.getLimit())");
            GenericType.FormatExporter pagedSelecter = new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build());

            addStmt(stmts, pagedStmts, selecter, pagedSelecter);

            //generate Predicate
            if (where != null) {
                addStmt(stmts, pagedStmts, new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK,
                        QueryDSLRepo.generateWherePredicate(where, ACTION[0], "query").build()));
            }

            //add paged dynamic clauses
            if (paging) {
                pagedStmts.addAll(QueryDSLRepo.PredicateBuilder.addDynamicWhereClauses(tableFQArr));
                pagedStmts.addAll(QueryDSLRepo.PredicateBuilder.orderBy(tableFQArr));
            }

            if (dto == null) {
                addStmt(stmts, pagedStmts, new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "return query.fetch()"));
            } else {
                //return query.fetch().stream().map(t -> new GetFullAssetGraphDTO(t.get(0, String.class), t.get(1, String.class))).collect(Collectors.toList());
                final CodeBlock.Builder tupleConvert = CodeBlock.builder().add("return query.fetch().stream().map(t -> new $T(",
                        ClassName.bestGuess(dtoImpl.getPackageName() + "." + dtoImpl.getClassName()));
                for (ExportPoint e : dtoImpl.getMethods()) {
                    Method m = (Method) e;
                    if (m.isConstructor()) {
                        int i = 0;
                        if (m.getParams().size() == 1) {
                            tupleConvert.add("t");
                        } else {
                            for (Map.Entry<String, TypeName> entry : m.getParams().entrySet()) {
                                if (i > 0) tupleConvert.add(", ");
                                tupleConvert.add("t.get($L, $T.class)", i++, entry.getValue());
                            }
                        }
                    }
                }
                tupleConvert.add(")).collect($T.toList())", Collectors.class);
                addStmt(stmts, pagedStmts, new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, tupleConvert.build()));
            }

            //generate params
            LinkedHashMap<String, TypeName> params = getParameterList();
            for (String k : params.keySet()) {
                boolean exist = false;
                for (ExportPoint e : dtoImpl.getFields()) {
                    Field f = (Field) e;
                    if (f.getName().equals(k)) {
                        exist = true;
                        break;
                    }
                }
                if (!exist && dto != null && dtoImpl != null) {
                    Field f = new Field(k, params.get(k), null, Modifier.PRIVATE);
                    dto.getMethods().add(QueryDSLRepo.generateGetter(f));
                    dtoImpl.getFields().add(f);
                }
            }

            Method method = new Method(ACTION[0] + BuiltInMethodFunc.upperFirstCharName(name), dto == null ?
                    (TypeUtil.getGenericListType(tableFQArr.length == 1 ?  typeName.toString() : Tuple.class.getCanonicalName())) :
                    TypeUtil.getGenericListType(dto.getPackageName() + "." + dto.getName()), params, stmts, Modifier.PUBLIC);

            boolean needSpecifyGet = false;
            String[] actions = action.split(",");
            //update&delete
            for (int i = 0;i < actions.length;i ++) {
                //update & delete
                if (actions[i].equalsIgnoreCase(ACTION[1]) || actions[i].equalsIgnoreCase(ACTION[2])) {
                    needSpecifyGet = true;
                    if (generator.equals(GENERATOR[0])) {
                        methods.add(actions[i].equalsIgnoreCase(ACTION[1]) ? QueryDSLRepo.generateUpdater(name, dto, dtoImpl,
                                tableFQArr, ACTION, where) : QueryDSLRepo.generateDeleter(name, dto, dtoImpl,
                                tableFQArr, ACTION, where));
                    } else if (generator.equals(GENERATOR[1])) {
                        methods.add((Method)new CustomizedRepo(actions[i] + BuiltInMethodFunc.upperFirstCharName(name),
                                sql.get(i), "void", dtoImpl, actions[i].equalsIgnoreCase(ACTION[1]) ?
                                BaseRepo.generateDTOParams(dto) : params, false, actions[i]).export());
                    }
                }
            }

            if (!needSpecifyGet || (needSpecifyGet && Arrays.stream(actions).allMatch(a -> a.equals(ACTION[0])))) {
                if (paging) {
                    Method pagedMethod = null;
                    String mName = method.getName() + "Paged";
                    if (generator.equals(GENERATOR[0])) {
                        pagedMethod = new Method(method);
                        pagedMethod.setName(mName);
                        pagedMethod.setStatements(pagedStmts);
                        BuiltInMethodFunc.addPagingCtxParam(pagedMethod.getParams());
                    } else if (generator.equals(GENERATOR[1])) {
                        BuiltInMethodFunc.addPagingCtxParam(params);
                        pagedMethod = (Method) new CustomizedRepo(mName, sql.get(0), null, dtoImpl, params, paging).export();
                    }
                    //pagedMethod.getParamAnnotations().put("pagingContext", Annotation.RepoAnnotationGenerator.generateParamAnnotation("pagingContext"));
                    methods.add(pagedMethod);
                } else {
                    if (generator.equals(GENERATOR[1])) {
                        methods.add((Method) new CustomizedRepo(method.getName(), sql.get(0), null, dtoImpl, params, paging).export());
                    } else {
                        methods.add(method);
                    }
                }
            }

            if (needSpecifyGet && Arrays.stream(actions).allMatch(a -> a.equals(ACTION[2]))) {
                dto = null;
                dtoImpl = null;
            }

            if (isGraphql()) {
                graphQLMethods.addAll(methods);
            }
            return methods;
        }

        public boolean isWhereContainsField(String field) {
            if (where != null && where.contains(field)) {
                String[] arr = where.split(field);
                if ((arr[0] != null && Character.isLetter(arr[0].charAt(arr[0].length() - 1))) ||
                        (arr.length > 1 && arr[1] != null && Character.isLetter(arr[1].charAt(0)))) return false;
                return true;
            } else if (sql != null && !sql.isEmpty()) {
                //native
                String firstSQLWhere = sql.get(0).split(" where ")[1];
                if (firstSQLWhere.indexOf(":" + field) > -1) {
                    return true;
                }
            }
            return false;
        }

        public String getWhereClauseOper(String dtoField) {
            return where != null ? getWhereClauseOper(where, dtoField) : getSQLWhereClauseOper(dtoField);
        }

        public String getSQLWhereClauseOper(String dtoField) {
            return  getWhereClauseOper(translateNativeSQLToCommonWhere(), ":" + dtoField);
        }

        public String translateNativeSQLToCommonWhere() {
            //here we need to clone a new sql to translate "=" -> "=="
            StringBuilder sb = new StringBuilder();
            for (int i = 0;i < sql.get(0).toCharArray().length;i ++) {
                char c = sql.get(0).toCharArray()[i];
                if (c == '=') {
                    if (c > 0 && sql.get(0).toCharArray()[i - 1] == '!') {
                        //ne
                    } else {
                        sb.append('=');
                    }
                }
                sb.append(c);
            }
            return sb.toString();
        }

        private String getWhereClauseOper(String source, String dtoField) {
            for (String oper : BaseRepo.OPER_STR) {
                if (source.contains(oper + dtoField)) {
                    return oper;
                }
            }
            return null;
        }

        private void addStmt(List<GenericType.FormatExporter> stmts, List<GenericType.FormatExporter> pagedStmts,
                             GenericType.FormatExporter stmt, GenericType.FormatExporter... pagedStmt) {
            stmts.add(stmt);
            if (pagedStmt.length == 0) pagedStmts.add(stmt);
            else pagedStmts.add(pagedStmt[0]);
        }

        private LinkedHashMap<String, TypeName> getParameterList() {
            //generate params
            LinkedHashMap<String, TypeName> map = BaseRepo.getAllCandidates(where);
            if (map.isEmpty() && sql != null && !sql.isEmpty()) {
                //try nativesql
                map = BaseRepo.getNativeSQLParams(sql.get(0));
            }
            return map;
        }

        private int addSelectField(int seq, CodeBlock.Builder builder, CustomizedRepo.GraphLevel node, String pojoName, String fieldName) {
            String qName = QueryDSLRepo.PredicateBuilder.getQName(pojoName);
            builder.add(seq == 1 ? ("$T.$L.$L") : (", $T.$L.$L"),
                    ClassName.bestGuess(ModelMetaData.POJO.getPackageName(pojoName) + "." + qName), pojoName, fieldName);
            node.getFields().add(ClassName.bestGuess(qName) + "." + pojoName);

            if (!dtoFields.containsKey(pojoName)) dtoFields.put(pojoName, new ArrayList<>());
            dtoFields.get(pojoName).add(fieldName);
            return ++seq;
        }

        private int collectFieldCount(View view, int n) {
            if (view.graphs == null) return n;

            n += view.getField().split(",").length;
            for (View subView : view.graphs) {
                n += collectFieldCount(subView, n);
            }
            return n;
        }

        private void traverseSubGraph(int seq, View view, CodeBlock.Builder builder, CustomizedRepo.GraphLevel node) {
            if (view.graphs == null) return;

            for (View subView : view.graphs) {
                CustomizedRepo.GraphLevel child = new CustomizedRepo.GraphLevel();
                node.getChildren().add(child);

                String[] arr = subView.field.split(",");
                for (int i = 0;i < arr.length;i ++) {
                    String[] fqfn = arr[i].split("\\.");
                    seq = addSelectField(seq, builder, child, fqfn[0], fqfn[1]);
                }
                traverseSubGraph(seq, subView, builder, child);
            }
        }

        @Override
        public boolean check() {
            boolean bValid1 = false, bValid2 = true;
            for (String str : GENERATOR) {
                if (str.equals(generator)) {
                    bValid1 = true;
                    break;
                }
            }
            String[] actions = action.split(",");
            for (String a : actions) {
                boolean bvalid = false;
                for (String str : ACTION) {
                    if (str.equals(a)) {
                        bvalid = true;
                        break;
                    }
                }
                if (!bvalid) {
                    bValid2 = false;
                    break;
                }
            }

            return bValid1 && bValid2 && !Strings.isBlank(name) && !Strings.isBlank(field);
        }
    }

    public class ServiceImpl extends SpringBooster.Base implements ExportPoint {

        private ServiceImpl() { }

        @Override
        public GenericType doExport() throws Exception {
            String[] n = BuiltInMethodFunc.extractPackageAndName(fqcn);
            List<ExportPoint> fields = new ArrayList<>();
            Set<ExportPoint> methods = new HashSet<>();
            for (String name : repos) {
                String[] repoNaming = BuiltInMethodFunc.extractPackageAndName(name);
                if (ConfigGraph.getGraph().getRepo().getRepositoryMap().containsKey(name)) {
                    Field f = new Field(repoNaming[1].toLowerCase(), name, null, Modifier.PRIVATE);
                    //we treat repository as interface so do we need to autowired?
                    f.addAnnotation(Annotation.ServiceAnnotationGenerator.generateAutowiredAnnotation());
                    fields.add(f);
                }
            }

            //add querydsl methods in serviceimpl accordingly
            for (GenericType repo : queryDSLRepos) {
                String var = repo.getClassName().toLowerCase();
                Field f = new Field(var, repo.getPackageName() + "." +
                        repo.getClassName(), null, Modifier.PRIVATE);
                f.addAnnotation(Annotation.ServiceAnnotationGenerator.generateAutowiredAnnotation());
                fields.add(f);

                repo.getMethods().forEach(e -> {
                    Method m = (Method)e;
                    m.clearStatements();
                    List<GenericType.FormatExporter> stmts = new ArrayList<>();
                    CodeBlock.Builder builder = CodeBlock.builder();
                    boolean isVoidReturnType = m.getType() != null ? m.getType().equals(void.class) : (
                            m.getTypeName().equals(TypeName.VOID));
                    builder.add((isVoidReturnType ? "" : "return ") + "$L.$L(", var, m.getName());
                    int i = 0;
                    for (String name : m.getParams().keySet()) {
                        name = name.equals("pagingContext") ? "decoratePagingContext(pagingContext)" : name;
                        if (i == 0) builder.add("$L", name);
                        else builder.add(", $L", name);
                        i ++;
                    }
                    builder.add(")");

                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build()));
                    m.setStatements(stmts);
                    m.addAnnotation(Annotation.ServiceAnnotationGenerator.generateOverrideAnnotation());
                    methods.add(m);
                });
            }

            fields.add(Field.generateLOGGER(n[1] + "Impl"));

            //add transactional
            for (View view : views) {
                String[] actions = view.action.split(",");
                for (String action : actions) {
                    for (int i = 1;i < View.ACTION.length;i ++) {
                        if (action.equalsIgnoreCase(View.ACTION[i])) {
                            methods.forEach(e -> {
                                Method m = (Method) e;
                                m.addAnnotation(Annotation.ServiceAnnotationGenerator.generateTransactionAnnotation());
                            });
                        }
                    }
                }
            }

            methods.addAll(getBuiltInMethod(false, true));

            Set<String> interfaces = new HashSet<>();
            interfaces.add(fqcn);
            addGraphQLInterfaceIfApplicable(interfaces);
            GenericType t = new GenericType(ConfigGraph.getGraph().getService().getBaseService(), interfaces, fields,
                    methods, n[0] + ".impl", n[1] + "Impl", false, Modifier.PUBLIC);
            t.addAnnotation(Annotation.ServiceAnnotationGenerator.generateServiceAnnotation());
            return t;
        }

        private void addGraphQLInterfaceIfApplicable(Set<String> interfaces) {
            views.forEach(view -> {
                if (view.isGraphql()) {
                    interfaces.add("graphql.kickstart.tools.GraphQLQueryResolver");
                    return;
                }
            });
        }

        @Override
        public boolean check() {
            return true;
        }
    }

    public View getView(String name) {
        for (View view : views) {
            if (view.getName().equalsIgnoreCase(name)) {
                return view;
            }
        }
        return null;
    }

    public List<String> getRepos() {
        return repos;
    }

    public void setRepos(List<String> repos) {
        this.repos = repos;
    }

    public String getFqcn() {
        return fqcn;
    }

    public void setFqcn(String fqcn) {
        this.fqcn = fqcn;
        decorateDataType();
    }

    public ServiceImpl getImpl() {
        return impl;
    }

    public void setImpl(ServiceImpl impl) {
        this.impl = impl;
    }

    public List<String> getBuiltinMethods() {
        return builtinMethods;
    }

    public void setBuiltinMethods(List<String> builtinMethods) {
        this.builtinMethods = builtinMethods;
    }

    public List<View> getViews() {
        return views;
    }

    public void setViews(List<View> views) {
        this.views = views;
    }

    public List<GenericType> getQueryDSLRepos() {
        return queryDSLRepos;
    }

    public void setQueryDSLRepos(List<GenericType> queryDSLRepos) {
        this.queryDSLRepos = queryDSLRepos;
    }

}

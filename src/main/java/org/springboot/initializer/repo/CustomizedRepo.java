package org.springboot.initializer.repo;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.model.*;
import org.springboot.initializer.service.Service;
import org.springboot.initializer.util.BuiltInMethodFunc;
import org.springboot.initializer.util.TypeUtil;

import javax.lang.model.element.Modifier;
import javax.persistence.Query;
import java.util.*;

public class CustomizedRepo extends BaseRepo implements ExportPoint {
    private String sql;
    private String name;
    private String entityType;
    private GenericType dtoImpl;
    private LinkedHashMap<String, TypeName> params;
    private boolean paging;
    private String action = Service.View.ACTION[0];

    public static class GraphLevel {
        Set<String> fields = new LinkedHashSet<>();
        List<GraphLevel> children = new ArrayList<>();

        public GraphLevel() {
        }

        public Set<String> getFields() {
            return fields;
        }

        public void setFields(Set<String> fields) {
            this.fields = fields;
        }

        public List<GraphLevel> getChildren() {
            return children;
        }

        public void setChildren(List<GraphLevel> children) {
            this.children = children;
        }
    }

    public CustomizedRepo(String name, String sql, String entityType, GenericType dtoImpl,
                          LinkedHashMap<String, TypeName> params, boolean paging, String... action) {
        this.sql = sql;
        this.name = name;
        this.entityType = entityType;
        this.dtoImpl = dtoImpl;
        this.params = new LinkedHashMap<>(params);
        this.paging = paging;
        if (action != null && action.length > 0) {
            this.action = action[0];
        }
    }

    @Override
    public Method doExport() throws Exception {
        decorateSQL();

        List<GenericType.FormatExporter> stmts = new ArrayList<>();
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "String sql = $S", sql));
        GenericType.FormatExporter qStmt;
        if (paging) {
            addPagingContext(stmts);
        }

        if (action.equalsIgnoreCase(Service.View.ACTION[0]) && entityType != null) {
            qStmt = new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "$T q = em.createNativeQuery(" + "$L" + ", $T.class)",
                    Query.class, "sql", ClassName.bestGuess(entityType));
        } else {
            qStmt = new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "$T q = em.createNativeQuery("+ "$L" + ")",
                    Query.class, "sql");
        }
        stmts.add(qStmt);

        for (String k : params.keySet()) {
            if (k.equalsIgnoreCase("pagingContext")) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "q.setParameter(\"offset\", pagingContext.getOffset())"));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "q.setParameter(\"limit\", pagingContext.getLimit())"));
            } else {
                TypeName t = params.get(k);
                if (t.isPrimitive()) {
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "q.setParameter(\"" + k
                            + "\", $L)", k));
                } else {
                    dtoImpl.getFields().forEach(e -> {
                        Field f = (Field) e;
                        String[] arr = BuiltInMethodFunc.extractDTOField(f.getName());
                        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "q.setParameter(\"" + f.getName()
                                + "\", $L)", "obj.get" + BuiltInMethodFunc.upperFirstCharName(f.getName()) + "()"));
                    });
                }
            }
        }

        if (action.equalsIgnoreCase(Service.View.ACTION[0])) {
            if (entityType != null) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "return q.getResultList()"));
            } else {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "List<Object[]> objects = q.getResultList()"));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "List<$T> resultList = new $T<>()",
                        ClassName.bestGuess(dtoImpl.getClassName()), ArrayList.class));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                        "for (Object[] t : objects)"));
                CodeBlock.Builder builder = CodeBlock.builder().add("resultList.add(new $T(", ClassName.bestGuess(dtoImpl.getClassName()));
                for (ExportPoint e : dtoImpl.getMethods()) {
                    Method m = (Method) e;
                    if (m.isConstructor() && m.getParams().size() > 0) {
                        int i = 0;
                        boolean fieldAdded = false;
                        for (String k : m.getParams().keySet()) {
                            i ++;
                            if (k.equalsIgnoreCase("id")) continue;
                            if (fieldAdded) builder.add(", ");
                            TypeName tm = m.getParams().get(k);

                            if (TypeUtil.isPrimitive(tm.toString())) {
                                if (TypeUtil.isEnum(tm.toString())) {
                                    builder.add("$T.valueOf((String)t[$L])", tm, i);
                                } else {
                                    builder.add("($L)t[$L]", tm.toString(), i);
                                }
                            } else {
                                builder.add("($L)t[$L]", tm.toString(), i);
                            }

                            fieldAdded = true;
                        }
                        break;
                    }
                }
                builder.add("))");
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build()));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "return resultList"));
            }
        } else if (action.equalsIgnoreCase(Service.View.ACTION[1]) || action.equalsIgnoreCase(Service.View.ACTION[2])) {
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "q.executeUpdate()"));
        }

        return new Method(name, action.equalsIgnoreCase(Service.View.ACTION[0]) ? (TypeUtil.getGenericListType(entityType != null ? entityType :
                dtoImpl.getPackageName() + "." + dtoImpl.getClassName())) : TypeName.VOID, params, stmts, Modifier.PUBLIC);
    }

    private void addPagingContext(List<GenericType.FormatExporter> stmts) {
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "sql = addPagingContext(sql, pagingContext);"));
    }

    public static List<Method> generateAddPagingContextFunc() {
        List<Method> methods = new ArrayList<>();

        LinkedHashMap<String, TypeName> params = new LinkedHashMap<>();
        params.put("sql", ClassName.get(String.class));
        params.put("pagingContext", ClassName.bestGuess("PagingContext"));

        List<GenericType.FormatExporter> stmts = new ArrayList<>();

        //filters
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                "if (!pagingContext.getFilters().isEmpty())"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "sql = addWhere(sql)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                "for (FilterContext filter : pagingContext.getFilters())"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                "sql = addWhereClause(sql, filter.getField().split(\"#\")[1], \"like\", '\\'' + filter.getPattern() + \"'\")"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));

        //orderby
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "String[] sqlTokens = null"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                "if (!pagingContext.getSortasc().isEmpty() || !pagingContext.getSortdesc().isEmpty())"));

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                    "if (sql.indexOf(\":orderby\") > -1)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "sqlTokens = sql.split(\":orderby\")"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "sql = sqlTokens[0]"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "sql += \" order by \""));

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "int i = 0"));

        addOrderByClause(stmts, true);
        addOrderByClause(stmts, false);

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.NEXT_FLOW, "else"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "sql = sql.replace(\":orderby\", \"\")"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                "if (sqlTokens != null)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "sql += sqlTokens[1]"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "return sql"));

        methods.add(new Method("addPagingContext", String.class, params, stmts, Modifier.PROTECTED));

        //where
        stmts = new ArrayList<>();
        params = new LinkedHashMap<>();
        params.put("sql", ClassName.get(String.class));
        params.put("field", ClassName.get(String.class));
        params.put("oper", ClassName.get(String.class));
        params.put("val", ClassName.get(String.class));

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                "if (sql.strip().endsWith(\"where\"))"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                "sql += field + \" \" + oper + \" \" + val"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.NEXT_FLOW,
                "else"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                "sql += \" and \" + field + \" \" + oper + \" \" + val"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "return sql"));

        methods.add(new Method("addWhereClause", String.class, params, stmts, Modifier.PROTECTED));

        stmts = new ArrayList<>();
        params = new LinkedHashMap<>();
        params.put("sql", ClassName.get(String.class));

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "String[] arr = sql.split(\" \")"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "boolean bwhereExist = false"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                "for (int i = arr.length - 1;i >= 0;i --)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "if (arr[i].endsWith(\")\")) break"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                "if (arr[i].equalsIgnoreCase(\"where\"))"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "bwhereExist = true"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "break"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW, "if (!bwhereExist)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "sql += \" where \""));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "return sql"));

        methods.add(new Method("addWhere", String.class, params, stmts, Modifier.PROTECTED));
        return methods;
    }

    private static void addOrderByClause(List<GenericType.FormatExporter> stmts, boolean basc) {
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                "for (String f : pagingContext.getSort" + (basc ? "asc" : "desc") + "())"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "if (i > 0) sql += \", \""));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "String[] arr = f.split(\"#\")"));

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW, "if (sqlTokens == null)"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                "String alias = sql.split(arr[0].split(\"\\\\.\")[1] + \" as \")[1].split(\" \")[0]"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                "sql += alias + \".\" + arr[1] + \" " + (basc ? "asc" : "desc") + "\""));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.NEXT_FLOW, "else"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                "sql += arr[1] + \" "+ (basc ? "asc" : "desc") + "\""));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));

        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "i ++"));
        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
    }

    private void decorateSQL() {
        String[] arr = sql.split(" ");
        for (int i = 0;i < arr.length;i ++) {
            if (arr[i].equalsIgnoreCase("from")) {
                for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().getModels()) {
                    String[] fn = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn());
                    if (fn[1].equalsIgnoreCase(arr[i + 1])) {
                        //get table name
                        arr[i + 1] = pojo.getTable();
                    }
                }
            }
        }
    }

    @Override
    public boolean check() {
        return !Strings.isBlank(sql);
    }
}

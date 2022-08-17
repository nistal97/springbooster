package org.springboot.initializer.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.TestInstance;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.util.BuiltInMethodFunc;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.annotation.DirtiesContext;

import javax.persistence.CascadeType;
import javax.persistence.EnumType;
import javax.persistence.FetchType;
import java.lang.reflect.Type;
import java.util.*;

public class Annotation extends SpringBooster.Base implements ExportPoint {

    private String name;
    private Map<String, Object> members = new HashMap<>();

    public Annotation() {}

    public Annotation(String name) {
        this.name = name;
    }

    public Annotation(String name, Map<String, Object> members) {
        this.name = name;
        this.members = members;
    }

    @Override
    public AnnotationSpec doExport() throws Exception {
        ClassName clazz = ClassName.bestGuess(name);
        AnnotationSpec.Builder builder =  AnnotationSpec.builder(clazz);
        for (String k : members.keySet()) {
            Object v = members.get(k);
            if (v instanceof String) builder.addMember(k, "$S", v);
            else if (v instanceof String[] arr) {
                Arrays.stream(arr).forEach(str -> builder.addMember(k, "$S", str));
            }
            else if (v instanceof FQVN fqvn) {
                builder.addMember(k, "$T.$L", fqvn.type, fqvn.val);
            }
            else if (v instanceof FQVN[] fqvns) {
                Arrays.stream(fqvns).forEach(fqvn -> builder.addMember(k, "$T.$L", fqvn.type, fqvn.val));
            }
            else if (v instanceof Class) {
                builder.addMember(k, "$T.class", v);
            }
            else if (v instanceof List) {
                List<Annotation> annotations = (List<Annotation>) v;
                for (Annotation a : annotations) builder.addMember(k, "$L", a.export());
            }
            else builder.addMember(k, "$L", v);
        }
        return builder.build();
    }

    @Override
    public boolean check() {
        return !Strings.isBlank(name);
    }

    public static class FQVN {
        public Type type;
        public Object val;

        public FQVN(Type type, Object val) {
            this.type = type;
            this.val = val;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Annotation that = (Annotation) o;
        return name.equals(that.name) && Objects.equals(members, that.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, members);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getMembers() {
        return members;
    }

    public void setMembers(Map<String, Object> members) {
        this.members = members;
    }

    public void addMember(String k, Object v) {
        this.members.put(k, v);
    }

    public static class ModelAnnotationGenerator {
        public static final String BASE_ID = "javax.persistence.Id";
        public static final String BASE_MAPSUPERCLASS = "javax.persistence.MappedSuperclass";
        public static final String BASE_GENERATEDVAL = "javax.persistence.GeneratedValue";
        public static final String BASE_ONCREATE = "javax.persistence.PrePersist";
        public static final String BASE_ONUPDATE = "javax.persistence.PreUpdate";

        public static final String ENTITY = "javax.persistence.Entity";
        public static final String TABLE = "javax.persistence.Table";
        public static final String COLUMN = "javax.persistence.Column";
        public static final String INDEX = "javax.persistence.Index";
        public static final String LOB = "javax.persistence.Lob";
        public static final String ENUMERATED = "javax.persistence.Enumerated";

        public static final String JOIN_ONE2ONE = "javax.persistence.OneToOne";
        public static final String JOIN_ONE2MANY = "javax.persistence.OneToMany";
        public static final String JOIN_MANY2ONE = "javax.persistence.ManyToOne";
        public static final String JOIN_MANY2MANY = "javax.persistence.ManyToMany";

        public static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
        public static final String JSON_MANAGED = "com.fasterxml.jackson.annotation.JsonManagedReference";
        public static final String JSON_BACKED = "com.fasterxml.jackson.annotation.JsonBackReference";
        public static final String JSON_ID_REF = "com.fasterxml.jackson.annotation.JsonIdentityReference";
        public static final String JSON_ID_GEN = "com.fasterxml.jackson.annotation.JsonIdentityInfo";

        public static final String FETCH_TYPE = "javax.persistence.FetchType";

        public static final String POST_CONSTRUCTOR = "javax.annotation.PostConstruct";

        public static final Annotation generateEntityAnnotation() {
            return new Annotation(ENTITY);
        }

        public static final Annotation generateJsonIgnoreAnnotation() {
            return new Annotation(JSON_IGNORE);
        }

        public static final Annotation generateIdentifyInfoAnnotation() {
            Map<String, Object> m = new HashMap<>();
            m.put("property", "id");
            m.put("scope", Long.class);
            m.put("generator", ObjectIdGenerators.PropertyGenerator.class);
            return new Annotation(JSON_ID_GEN, m);
        }

        public static final Annotation[] generateJsonManagedAnnotations(String val) {
            Annotation[] annotations = new Annotation[2];
            Map<String, Object> m = new HashMap<>();
            m.put("value", val);
            annotations[0] = new Annotation(ModelAnnotationGenerator.JSON_MANAGED, m);
            m = new HashMap<>();
            m.put("alwaysAsId", true);
            annotations[1] = new Annotation(ModelAnnotationGenerator.JSON_ID_REF, m);
            return annotations;
        }

        public static final Annotation generateJsonBackedAnnotations(String val) {
            Map<String, Object> m = new HashMap<>();
            m.put("value", val);
            return new Annotation(ModelAnnotationGenerator.JSON_BACKED, m);
        }

        public static final Annotation generateEnumeratedAnnotation(String type) {
            Map<String, Object> m = new HashMap<>();
            EnumType t = "ordinal".equalsIgnoreCase(type) ? EnumType.ORDINAL : EnumType.STRING;
            m.put("value", new Annotation.FQVN(EnumType.class, t));
            return new Annotation(ENUMERATED, m);
        }

        public static final Annotation generateTableAnnotation(String fqcn, String table, Set<Index> indexs) throws Exception {
            Map<String, Object> m = new HashMap<>();
            m.put("name", table);
            List<Annotation> idxs = new ArrayList<>();
            for (Index idx : indexs) {
                if (idx.getField().indexOf(",") == -1) idxs.add((Annotation)idx.export());
                else {
                    String[] fields = idx.getField().split(",");
                    for (String field : fields) {
                        Index t = new Index(idx.getName(), field.strip(), idx.isUnique());
                        idxs.add((Annotation)t.export());
                    }
                }
            }
            //generate idx for one2one
            for (ModelMetaData.One2One one2One : ConfigGraph.getGraph().getModel().getOne2ones()) {
                Index t = null;
                String[] arrA = BuiltInMethodFunc.extractPackageAndName(one2One.getClassA());
                String[] arrB = BuiltInMethodFunc.extractPackageAndName(one2One.getClassB());

                if (one2One.getClassA().equals(fqcn)) {
                    t = new Index("one2one_" + arrA[1] + "_" + arrB[1],
                            one2One.getFieldA() + "_id", true);
                } else if (one2One.getClassB().equals(fqcn)) {
                    t = new Index("one2one_" + arrB[1] + "_" + arrA[1],
                            one2One.getFieldB() + "_id", true);
                }
                //cascade indicates it's unidirectional or bidirectional
                if (t != null && one2One.getCascades().size() > 1) idxs.add((Annotation)t.export());
            }
            m.put("indexes", idxs);

            return new Annotation(Annotation.ModelAnnotationGenerator.TABLE, m);
        }

        @FunctionalInterface
        interface TableJoinAnnotation {
            Annotation generate(String[] cascadeLevel);
        }

        public static final Annotation generateOne2OneAnnotation(String[] cascadeLevel) {
            Annotation annotation = generateJoinAnnotation(JOIN_ONE2ONE, cascadeLevel);
            annotation.addMember("fetch", new FQVN(FetchType.class, FetchType.LAZY));
            return annotation;
        }

        public static final Annotation generateOne2ManyAnnotation(String[] cascadeLevel) {
            return generateJoinAnnotation(JOIN_ONE2MANY, cascadeLevel);
        }

        public static final Annotation generateMany2OneAnnotation(String[] cascadeLevel) {
            Annotation annotation = generateJoinAnnotation(JOIN_MANY2ONE, cascadeLevel);
            annotation.addMember("fetch", new FQVN(FetchType.class, FetchType.LAZY));
            return annotation;
        }

        public static final Annotation generateMany2ManyAnnotation(String[] cascadeLevel) {
            Annotation annotation = generateJoinAnnotation(JOIN_MANY2MANY, cascadeLevel);
            annotation.addMember("fetch", new FQVN(FetchType.class, FetchType.LAZY));
            return annotation;
        }

        private static final Annotation generateJoinAnnotation(String annoName, String[] cascadeLevel) {
            if (cascadeLevel == null || ModelMetaData.Cascade.NO_CASCADE.equals(cascadeLevel[0]))
                return new Annotation(annoName);

            Map<String, Object> m = new HashMap<>();
            if (cascadeLevel != null) {
                List<Annotation.FQVN> abilities = new ArrayList<>();
                Arrays.stream(cascadeLevel).forEach(level->
                    abilities.add(new Annotation.FQVN(CascadeType.class, CascadeType.valueOf(level)))
                );
                m.put("cascade", abilities.toArray(new Annotation.FQVN[abilities.size()]));
            }
            return new Annotation(annoName, m);
        }


        static class Index extends SpringBooster.Base implements ExportPoint{
            private String name;
            private String field;
            private boolean unique;

            public Index(){}

            public Index(String name, String field, boolean unique) {
                this.name = name;
                this.field = field;
                this.unique = unique;
            }

            @Override
            public Annotation doExport() throws Exception {
                Map<String, Object> m = new HashMap<>();
                m.put("name", name);
                m.put("columnList", field);
                m.put("unique", unique);
                return new Annotation(INDEX, m);
            }

            @Override
            public boolean check() {
                return !Strings.isBlank(name) && !Strings.isBlank(field);
            }

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

            public boolean isUnique() {
                return unique;
            }

            public void setUnique(boolean unique) {
                this.unique = unique;
            }

        }
    }

    public static class RepoAnnotationGenerator {
        public static final String QUERY = "org.springframework.data.jpa.repository.Query";
        public static final String PARAM = "org.springframework.data.repository.query.Param";

        public static final Annotation generateQueryAnnotation(String sql, boolean bnative) {
            Map<String, Object> m = new HashMap<>();
            m.put("nativeQuery", bnative);
            m.put("value", sql);
            return new Annotation(QUERY, m);
        }

        public static final Annotation generateParamAnnotation(String value) {
            Map<String, Object> m = new HashMap<>();
            m.put("value", value);
            return new Annotation(PARAM, m);
        }
    }

    public static class ServiceAnnotationGenerator {
        public static final String SERVICE = "org.springframework.stereotype.Service";
        public static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
        public static final String OVERRIDE = "java.lang.Override";
        public static final String COMPONENT = "org.springframework.stereotype.Component";
        public static final String TRANSACTION = "javax.transaction.Transactional";

        public static final Annotation generateServiceAnnotation() {
            return new Annotation(SERVICE);
        }
        public static final Annotation generateAutowiredAnnotation() {
            return new Annotation(AUTOWIRED);
        }
        public static final Annotation generateOverrideAnnotation() {
            return new Annotation(OVERRIDE);
        }
        public static final Annotation generateComponentAnnotation() {
            return new Annotation(COMPONENT);
        }
        public static final Annotation generateTransactionAnnotation() {
            Map<String, Object> m = new HashMap<>();
            m.put("rollbackOn", Throwable.class);
            return new Annotation(TRANSACTION, m);
        }
    }

    public static class ControllerAnnotationGenerator {
        public static final String RESTCONTROLLER = "org.springframework.web.bind.annotation.RestController";
        public static final String REQUESTMAPPING = "org.springframework.web.bind.annotation.RequestMapping";
        public static final String GETMAPPING = "org.springframework.web.bind.annotation.GetMapping";
        public static final String POSTMAPPING = "org.springframework.web.bind.annotation.PostMapping";
        public static final String PUTMAPPING = "org.springframework.web.bind.annotation.PutMapping";
        public static final String DELETEMAPPING = "org.springframework.web.bind.annotation.DeleteMapping";
        public static final String REQBODY = "org.springframework.web.bind.annotation.RequestBody";
        public static final String VALIDATOR = "org.springframework.validation.annotation.Validated";

        public static final Annotation generateRestControllerAnnotation() {
            return new Annotation(RESTCONTROLLER);
        }
        public static final Annotation generateRequestMappingAnnotation(String path) {
            Map<String, Object> m = new HashMap<>();
            m.put("path", path);
            return new Annotation(REQUESTMAPPING, m);
        }
        public static final Annotation generateGetMappingAnnotation(String path) {
            Map<String, Object> m = new HashMap<>();
            m.put("path", path);
            return new Annotation(GETMAPPING, m);
        }
        public static final Annotation generatePostMappingAnnotation(String path) {
            Map<String, Object> m = new HashMap<>();
            m.put("path", path);
            return new Annotation(POSTMAPPING, m);
        }
        public static final Annotation generatePutMappingAnnotation(String path) {
            Map<String, Object> m = new HashMap<>();
            m.put("path", path);
            return new Annotation(PUTMAPPING, m);
        }
        public static final Annotation generateDeleteMappingAnnotation(String path) {
            Map<String, Object> m = new HashMap<>();
            m.put("path", path);
            return new Annotation(DELETEMAPPING, m);
        }
        public static final Annotation generateReqBodyAnnotation() {
            return new Annotation(REQBODY);
        }
        public static final Annotation generateValidatorAnnotation() {
            return new Annotation(VALIDATOR);
        }
    }

    public static class TestAnnotationGenerator {
        public static final String TEST = "org.junit.jupiter.api.Test";
        public static final String DATAJPATEST = "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest";
        public static final String EXTENDWITH = "org.junit.jupiter.api.extension.ExtendWith";
        public static final String PROFILE = "org.springframework.test.context.ActiveProfiles";
        public static final String TESTDBAUTOCONFIG = "org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase";
        public static final String ROLLBACK = "org.springframework.test.annotation.Rollback";
        public static final String TESTINSTANCE = "org.junit.jupiter.api.TestInstance";
        public static final String DIRTYCTX = "org.springframework.test.annotation.DirtiesContext";
        public static final String COMPONENTSCAN = "org.springframework.context.annotation.ComponentScan";
        public static final String TESTSUITE = "org.junit.platform.suite.api.Suite";
        public static final String SELECTEDPKG = "org.junit.platform.suite.api.SelectPackages";

        public static final String BEFOREALL = "org.junit.jupiter.api.BeforeAll";
        public static final String BEFOREEACH = "org.junit.jupiter.api.BeforeEach";
        public static final String AFTERALL = "org.junit.jupiter.api.AfterAll";


        public static final Annotation generateTestAnnotation() {
            return new Annotation(TEST);
        }
        public static final Annotation generateTestSuiteAnnotation() {
            return new Annotation(TESTSUITE);
        }
        public static final Annotation generateSelectedPkgAnnotation(String[] pkgs) {
            Map<String, Object> m = new HashMap<>();
            m.put("value", pkgs);
            return new Annotation(SELECTEDPKG, m);
        }
        public static final Annotation generateDataJPATestAnnotation() {
            return new Annotation(DATAJPATEST);
        }
        public static final Annotation generateSpringExtAnnotation() {
            Map<String, Object> m = new HashMap<>();
            m.put("value", org.springframework.test.context.junit.jupiter.SpringExtension.class);
            return new Annotation(EXTENDWITH, m);
        }
        public static final Annotation generateActiveProfileAnnotation(String profile)  {
            Map<String, Object> m = new HashMap<>();
            m.put("value", profile);
            return new Annotation(PROFILE, m);
        }
        public static final Annotation generateTestDBAutoConfigAnnotation() {
            Map<String, Object> m = new HashMap<>();
            m.put("replace", new Annotation.FQVN(AutoConfigureTestDatabase.Replace.class, AutoConfigureTestDatabase.Replace.NONE));
            return new Annotation(TESTDBAUTOCONFIG, m);
        }
        public static final Annotation generateRollbackAnnotation(boolean rollback)  {
            Map<String, Object> m = new HashMap<>();
            m.put("value", rollback);
            return new Annotation(ROLLBACK, m);
        }
        public static final Annotation generateBeforeAllAnnotation()  {
            return new Annotation(BEFOREALL);
        }
        public static final Annotation generateBeforeEachAnnotation()  {
            return new Annotation(BEFOREEACH);
        }
        public static final Annotation generateAfterAllAnnotation()  {
            return new Annotation(AFTERALL);
        }
        public static final Annotation generateTestInstanceAnnotation()  {
            Map<String, Object> m = new HashMap<>();
            m.put("value", new Annotation.FQVN(TestInstance.Lifecycle.class, TestInstance.Lifecycle.PER_CLASS));
            return new Annotation(TESTINSTANCE, m);
        }
        public static final Annotation generateDirtyCtxAnnotation() {
            Map<String, Object> m = new HashMap<>();
            m.put("classMode", new Annotation.FQVN(DirtiesContext.ClassMode.class, DirtiesContext.ClassMode.AFTER_CLASS));
            return new Annotation(DIRTYCTX, m);
        }
        public static final Annotation generateComponentScanAnnotation(String packageName) {
            Map<String, Object> m = new HashMap<>();
            m.put("value", packageName);
            return new Annotation(COMPONENTSCAN, m);
        }

    }

}


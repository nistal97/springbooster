package org.springboot.initializer.model;

import com.squareup.javapoet.TypeName;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.util.BuiltInMethodFunc;
import org.springboot.initializer.util.TypeUtil;

import javax.lang.model.element.Modifier;
import java.util.*;

public class ModelMetaData extends SpringBooster.Base{

    private String basemodel;
    private String dateformat;
    private Set<Enum> enums = new HashSet<>();
    private Set<POJO> models = new HashSet<>();
    private Set<One2One> one2ones = new HashSet<>();
    private Set<One2Many> one2manys = new HashSet<>();
    private Set<Many2Many> many2manys = new HashSet<>();

    public static class Enum extends SpringBooster.Base implements ExportPoint {
        private String fqcn;
        private String type;
        private Set<String> values;

        public Enum(){}

        public String getFqcn() {
            return fqcn;
        }

        public void setFqcn(String fqcn) {
            this.fqcn = fqcn;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Set<String> getValues() {
            return values;
        }

        public void setValues(Set<String> values) {
            this.values = values;
        }

        @Override
        public GenericEnum doExport() throws Exception {
            String[] arr = BuiltInMethodFunc.extractPackageAndName(fqcn);
            return new GenericEnum(arr[0], arr[1], values);
        }

        @Override
        public boolean check() {
            return !Strings.isBlank(fqcn) && !Strings.isBlank(type) && !values.isEmpty();
        }
    }

    public GenericType exportBaseModel() throws Exception {
        if (Strings.isBlank(getBasemodel())) return null;

        List<ExportPoint> fields = new ArrayList<>();
        fields.add(Field.ModelFieldGenerator.generateModelIDField());
        Arrays.stream(Field.ModelFieldGenerator.generateBaseTimestampField()).forEach((f)->{fields.add(f);});

        Set<ExportPoint> methods = new HashSet<>();
        Arrays.stream(Method.ModelMethodGenerator.generateBaseTimestampMethods()).forEach((m)->{methods.add(m);});

        String[] n = BuiltInMethodFunc.extractPackageAndName(basemodel);
        GenericType t = new GenericType(null, null, fields, methods, n[0], n[1], Modifier.PUBLIC, Modifier.ABSTRACT);
        t.addAnnotation(new Annotation(Annotation.ModelAnnotationGenerator.BASE_MAPSUPERCLASS));
        return t;
    }

    public static class POJO extends SpringBooster.Base implements ExportPoint {
        private String fqcn;
        private String table;
        private Set<Field.ModelField> fields = new HashSet();
        private Set<Annotation.ModelAnnotationGenerator.Index> indexs = new HashSet();

        public POJO(){}

        @Override
        public boolean check() {
            if (Strings.isBlank(fqcn) || fqcn.indexOf('.') == -1) return false;
            return true;
        }

        @Override
        public GenericType doExport() throws Exception {
            String[] n = BuiltInMethodFunc.extractPackageAndName(fqcn);
            List<ExportPoint> fs = new ArrayList<>();
            fs.add(Field.ModelFieldGenerator.generateSerializableField());

            for (Field.ModelField f : fields) {
                for (Enum e : ConfigGraph.getGraph().getModel().getEnums()) {
                    if (f.typeName.toString().equals(e.fqcn)) {
                        f.setEnumType(e.type);
                        break;
                    }
                }
            }
            //fields.add(Field.ModelFieldGenerator.generateModelIDField());
            //fields.addAll(Arrays.asList(Field.ModelFieldGenerator.generateBaseTimestampField()));
            fs.addAll(fields);

            GenericType t = new GenericType(ConfigGraph.getGraph().getModel().basemodel, null, fs, null, n[0], n[1], Modifier.PUBLIC);
            fields.forEach(f -> {
                if (TypeUtil.isPrimitive(f.getFieldTypeName())) {
                    t.getCrucialFields().add(f.getName());
                }
            });
            addModelAnnotation(t);
            return t;
        }

        final GenericType addModelAnnotation(GenericType t) throws Exception {
            t.addAnnotation(Annotation.ModelAnnotationGenerator.generateEntityAnnotation());
            t.addAnnotation(Annotation.ModelAnnotationGenerator.generateIdentifyInfoAnnotation());

            indexs.forEach(idx -> idx.setField(getColumnName(idx.getField())));
            Annotation tableAnnotation = Annotation.ModelAnnotationGenerator.generateTableAnnotation(fqcn, table, indexs);
            t.addAnnotation(tableAnnotation);
            return t;
        }

        public String getColumnName(String field) {
            for (Field.ModelField f : fields) {
                if (f.getName().equals(field) && f.getColumn() != null) return f.getColumn();
            }
            return field;
        }

        public static String getPackageName(String pojoName) {
            String packageName = null;
            for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().getModels()) {
                String[] fqcn = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn());
                if (fqcn[1].toLowerCase().equals(pojoName)) {
                    packageName = fqcn[0];
                    break;
                }
            }
            return packageName;
        }

        public static String getFQCNByPojoName(String pojoName) {
            for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().getModels()) {
                if (pojoName.equalsIgnoreCase(BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn())[1])) {
                    return pojo.getFqcn();
                }
            }
            return null;
        }

        public static Field.ModelField getFieldByName(String pojoName, String fieldName) {
            Field.ModelField t = null;
            //if sys field
            Field sysField = Field.tryCloneSysField(fieldName);
            if (sysField != null) return (Field.ModelField)sysField;

            for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().getModels()) {
                String[] fqcn = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn());
                if (fqcn[1].toLowerCase().equals(pojoName)) {
                    for (Field.ModelField f : pojo.getFields()) {
                        if (f.getName().equals(fieldName)) {
                            t = f;
                            break;
                        }
                    }
                    break;
                }
            }
            return t;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            POJO pojo = (POJO) o;
            return fqcn.equals(pojo.fqcn) && table.equals(pojo.table) && Objects.equals(fields, pojo.fields);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fqcn, table, fields);
        }

        public String getFqcn() {
            return fqcn;
        }

        public void setFqcn(String fqcn) {
            this.fqcn = fqcn;
        }

        public Set<Field.ModelField> getFields() {
            return fields;
        }

        public void setFields(Set<Field.ModelField> fields) {
            this.fields = fields;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public Set<Annotation.ModelAnnotationGenerator.Index> getIndexs() {
            return indexs;
        }

        public void setIndexs(Set<Annotation.ModelAnnotationGenerator.Index> indexs) {
            this.indexs = indexs;
        }
    }

    public static class Cascade extends SpringBooster.Base implements ExportPoint {
        public static final String NO_CASCADE = "NO_CASCADE";
        public static final String CASCADE_ALL = "CascadeType.ALL";
        public static final String CASCADE_PERSIST = "CascadeType.PERSIST";
        public static final String CASCADE_MERGE = "CascadeType.MERGE";
        public static final String CASCADE_REMOVE = "CascadeType.REMOVE";
        public static final String CASCADE_REFRESH = "CascadeType.REFRESH";
        public static final String CASCADE_DETACH = "CascadeType.DETACH";

        private String relation;
        private String level;

        public Cascade() {}

        public static class Builder {
            private String master;
            private String slave;
            private String[] abilities;

            public Builder(String master, String slave, String[] abilities) {
                this.master = master;
                this.slave = slave;
                this.abilities = abilities;
            }

            public String getMaster() {
                return master;
            }

            public String getSlave() {
                return slave;
            }

            public boolean canCascadePersist() {
                return canCascade(CASCADE_PERSIST);
            }

            public boolean canCascadeUpdate() {
                return canCascade(CASCADE_MERGE);
            }

            public boolean canCascadeRemove() {
                return canCascade(CASCADE_REMOVE);
            }

            private boolean canCascade(String passport) {
                boolean[] val = new boolean[]{false};
                Arrays.stream(abilities).forEach(ability -> {
                    if (ability.equals(passport)) {
                        val[0] = true;
                        return;
                    }
                });
                return val[0];
            }
        }

        @Override
        public Cascade.Builder doExport() throws Exception {
            String[] arr = BuiltInMethodFunc.identifyMasterSlave(relation);
            String[] abilities = BuiltInMethodFunc.extractAbility(level);
            String[] aNames = new String[abilities.length];
            for (int i = 0;i < abilities.length;i ++) {
                String[] ts = BuiltInMethodFunc.extractPackageAndName(abilities[i]);
                aNames[i] = ts.length > 1 ? ts[1] : ts[0];
            }
            return new Cascade.Builder(arr[0], arr[1], aNames);
        }

        @Override
        public boolean check() {
            String[] abilities = BuiltInMethodFunc.extractAbility(level);
            for (String a : abilities) {
                switch (a) {
                    case NO_CASCADE:
                    case CASCADE_ALL:
                    case CASCADE_PERSIST:
                    case CASCADE_MERGE:
                    case CASCADE_REMOVE:
                    case CASCADE_REFRESH:
                    case CASCADE_DETACH:
                        break;
                    default:
                        return false;
                }
            }
            return !Strings.isBlank(relation) && relation.indexOf(BuiltInMethodFunc.RELATION_DELIMITER) > -1;
        }

        public String getRelation() {
            return relation;
        }

        public void setRelation(String relation) {
            this.relation = relation;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }
    }

    public static List<One2One> searchRelation(String pojo1, String pojo2) {
        List<One2One> relations = new ArrayList<>();
        for (One2One one2One : ConfigGraph.getGraph().getModel().getOne2ones()) {
            String[] classAPOJO = BuiltInMethodFunc.extractPackageAndName(one2One.getClassA());
            String[] classBPOJO = BuiltInMethodFunc.extractPackageAndName(one2One.getClassB());
            if ((classAPOJO[1].equalsIgnoreCase(pojo1) && classBPOJO[1].equalsIgnoreCase(pojo2)) ||
                    (classAPOJO[1].equalsIgnoreCase(pojo2) && classBPOJO[1].equalsIgnoreCase(pojo1))) {
                relations.add(one2One);
            }
        }
        for (One2One one2One : ConfigGraph.getGraph().getModel().getOne2manys()) {
            String[] classAPOJO = BuiltInMethodFunc.extractPackageAndName(one2One.getClassA());
            String[] classBPOJO = BuiltInMethodFunc.extractPackageAndName(one2One.getClassB());
            if ((classAPOJO[1].equalsIgnoreCase(pojo1) && classBPOJO[1].equalsIgnoreCase(pojo2)) ||
                    (classAPOJO[1].equalsIgnoreCase(pojo2) && classBPOJO[1].equalsIgnoreCase(pojo1))) {
                relations.add(one2One);
            }
        }
        for (One2One one2One : ConfigGraph.getGraph().getModel().getMany2manys()) {
            String[] classAPOJO = BuiltInMethodFunc.extractPackageAndName(one2One.getClassA());
            String[] classBPOJO = BuiltInMethodFunc.extractPackageAndName(one2One.getClassB());
            if ((classAPOJO[1].equalsIgnoreCase(pojo1) && classBPOJO[1].equalsIgnoreCase(pojo2)) ||
                    (classAPOJO[1].equalsIgnoreCase(pojo2) && classBPOJO[1].equalsIgnoreCase(pojo1))) {
                relations.add(one2One);
            }
        }
        return relations;
    }

    public static Map<String, ModelMetaData.Cascade.Builder>[]  serachCascadeAbilityAsMaster(String pojo, String... fieldName) {
        Map<String, ModelMetaData.Cascade.Builder>[] abilityMapArr = new Map[3];
        abilityMapArr[0] = ModelMetaData.searchOne2OneCascadeAbilitiesAsMaster(pojo, fieldName);
        abilityMapArr[1] = ModelMetaData.searchOne2ManyCascadeAbilitiesAsMaster(pojo, fieldName);
        abilityMapArr[2] = ModelMetaData.searchMany2ManyCascadeAbilitiesAsMaster(pojo, fieldName);
        return abilityMapArr;
    }

    public static Map<String, Cascade.Builder> searchOne2OneCascadeAbilitiesAsMaster(String pojo, String... fieldName) {
        return searchCascadeAbilitiesAsMaster(ConfigGraph.getGraph().getModel().getOne2ones(), pojo, fieldName);
    }

    public static Map<String, Cascade.Builder> searchOne2ManyCascadeAbilitiesAsMaster(String pojo, String... fieldName) {
        Set<One2One> set = new HashSet<>();
        ConfigGraph.getGraph().getModel().getOne2manys().forEach(r -> set.add(r));
        return searchCascadeAbilitiesAsMaster(set, pojo, fieldName);
    }

    public static Map<String, Cascade.Builder> searchMany2ManyCascadeAbilitiesAsMaster(String pojo, String... fieldName) {
        Set<One2One> set = new HashSet<>();
        ConfigGraph.getGraph().getModel().getMany2manys().forEach(r -> set.add(r));
        return searchCascadeAbilitiesAsMaster(set, pojo, fieldName);
    }

    private static Map<String, Cascade.Builder> searchCascadeAbilitiesAsMaster(Set<One2One> relations, String pojo, String... filterField) {
        Map<String, Cascade.Builder> abilityMap = new HashMap<>();

        match: for (One2One one2One : relations) {
            if ((one2One.getClassA().equalsIgnoreCase(pojo) || one2One.getClassB().equalsIgnoreCase(pojo))) {
                for (Cascade cascade : one2One.getCascades()) {
                    String[] arr = BuiltInMethodFunc.identifyMasterSlave(cascade.relation);
                    if (arr[0].equals(pojo)) {
                        if (filterField.length > 0) {
                            if (!((one2One.getClassA().equalsIgnoreCase(pojo) && one2One.getFieldB().equalsIgnoreCase(filterField[0])) ||
                                    (one2One.getClassB().equalsIgnoreCase(pojo) && one2One.getFieldA().equalsIgnoreCase(filterField[0])))) {
                                continue match;
                            }
                        }
                        String[] abilities = BuiltInMethodFunc.extractAbility(cascade.level);
                        //here we put field name as master slave, and its FQCN as slave
                        String fieldName = one2One.getClassA().equalsIgnoreCase(pojo) ? one2One.fieldA :
                                one2One.fieldB;
                        abilityMap.put(arr[1], new Cascade.Builder(
                                fieldName, fieldName.equals(one2One.fieldA) ? one2One.classB : one2One.classA
                                , abilities));
                    }
                }
            }
        }
        return abilityMap;
    }

    public static class One2One extends SpringBooster.Base implements ExportPoint {
        protected String classA;
        protected String fieldA;
        protected String classB;
        protected String fieldB;
        protected String name;

        protected List<Cascade> cascades = new ArrayList<>();

        public One2One(){}

        @Override
        public Object doExport() throws Exception {
            return doExport(Field.ModelFieldGenerator::generateOne2OneField);
        }

        protected Object doExport(Field.ModelFieldGenerator.TableJoinField tableJoinAnnotator) throws Exception {
            List<Cascade.Builder> cascadeBuilders = getCascadeBuilders();
            boolean bJsonIgnore = isJsonIgnoreRelation(cascadeBuilders);

            for (Cascade.Builder builder : cascadeBuilders) {
                String fieldInMaster = builder.master.equals(classA) ? fieldB : fieldA;
                String fieldInSlave = fieldInMaster.equals(fieldA) ? fieldB : fieldA;

                for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().models) {
                    if (pojo.getFqcn().equals(builder.master)) {
                        Field.ModelField field = tableJoinAnnotator.generateField(fieldInSlave, builder.slave,
                                null, getCascadeLevels(cascadeBuilders, builder.master, builder.slave));
                        if (bJsonIgnore) {
                            addJsonIgnoreRelationship(field);
                        } else {
                            addJsonManageRelationship(name, field, builder.abilities);
                        }

                        pojo.getFields().add(field);
                    }
                }
            }

            return null;
        }

        protected boolean isJsonIgnoreRelation(List<Cascade.Builder> cascadeBuilders) {
            for (Cascade.Builder builder : cascadeBuilders) {
                for (String ability : builder.abilities) {
                    if (ability.equalsIgnoreCase(Cascade.CASCADE_PERSIST.split("\\.")[1]) ||
                            ability.equalsIgnoreCase(Cascade.CASCADE_MERGE.split("\\.")[1]) ||
                            ability.equalsIgnoreCase(Cascade.CASCADE_REMOVE.split("\\.")[1])) {
                        return false;
                    }
                }
            }
            return true;
        }

        protected void addJsonIgnoreRelationship(Field.ModelField field) {
            field.addAnnotation(Annotation.ModelAnnotationGenerator.generateJsonIgnoreAnnotation());
        }

        protected void addJsonManageRelationship(String name, Field.ModelField field, String[] abilities) {
            for (String ability : abilities) {
                if (ability.equalsIgnoreCase(Cascade.CASCADE_PERSIST.split("\\.")[1])) {
                    field.getAnnotations().addAll(Arrays.asList(
                            Annotation.ModelAnnotationGenerator.generateJsonManagedAnnotations(name)));
                    break;
                } else if (ability.equalsIgnoreCase(Cascade.NO_CASCADE) ||
                        ability.equalsIgnoreCase(Cascade.CASCADE_DETACH.split("\\.")[1])) {
                    field.addAnnotation(Annotation.ModelAnnotationGenerator.generateJsonBackedAnnotations(name));
                    break;
                }
            }
        }

        protected List<Cascade.Builder> getCascadeBuilders() throws Exception {
            List<Cascade.Builder> cascadeBuilders = new ArrayList<>();
            for (Cascade cascade : cascades) cascadeBuilders.add((Cascade.Builder) cascade.export());
            return cascadeBuilders;
        }

        protected String[] getCascadeLevels(List<Cascade.Builder> cascadeBuilders, String masterCandidate,
                                       String slaveCandidate) {
            String[] cascadeLevel = null;
            for (Cascade.Builder builder : cascadeBuilders) {
                if (builder.master.equals(masterCandidate) && builder.slave.equals(slaveCandidate)) {
                    cascadeLevel = builder.abilities;
                    break;
                }
            }
            return cascadeLevel;
        }

        @Override
        public boolean check() {
            return !(Strings.isBlank(classA) || Strings.isBlank(fieldA)
                    || Strings.isBlank(classB) || Strings.isBlank(fieldB));
        }

        public String getClassA() {
            return classA;
        }

        public void setClassA(String classA) {
            this.classA = classA;
        }

        public String getFieldA() {
            return fieldA;
        }

        public void setFieldA(String fieldA) {
            this.fieldA = fieldA;
        }

        public String getClassB() {
            return classB;
        }

        public void setClassB(String classB) {
            this.classB = classB;
        }

        public String getFieldB() {
            return fieldB;
        }

        public void setFieldB(String fieldB) {
            this.fieldB = fieldB;
        }

        public List<Cascade> getCascades() {

            return cascades;
        }

        public void setCascades(List<Cascade> cascades) {
            this.cascades = cascades;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class One2Many extends One2One implements ExportPoint {

        @Override
        public Object doExport() throws Exception {
            List<Cascade.Builder> cascadeBuilders = getCascadeBuilders();
            boolean bJsonIgnore = isJsonIgnoreRelation(cascadeBuilders);

            for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().models) {
                if (pojo.getFqcn().equals(classA)) {
                    TypeName listFieldType = TypeUtil.getGenericListType(classB);

                    String[] cascadeLevels = getCascadeLevels(cascadeBuilders, classA, classB);
                    Field.ModelField field = Field.ModelFieldGenerator.generateOne2ManyField(fieldA, listFieldType,
                            TypeUtil.getArrayListInitializer(), cascadeLevels);
                    if (bJsonIgnore) {
                        addJsonIgnoreRelationship(field);
                    } else {
                        addJsonManageRelationship(name, field, cascadeLevels);
                    }

                    pojo.getFields().add(field);
                } else if (pojo.getFqcn().equals(classB)) {
                    String[] cascadeLevels = getCascadeLevels(cascadeBuilders, classB, classA);
                    Field.ModelField field = Field.ModelFieldGenerator.generateMany2OneField(fieldB, classA,
                            null, cascadeLevels);
                    if (bJsonIgnore) {
                        addJsonIgnoreRelationship(field);
                    } else {
                        addJsonManageRelationship(name, field, cascadeLevels);
                    }

                    pojo.getFields().add(field);
                }
            }
            return null;
        }

    }

    public static class Many2Many extends One2One implements ExportPoint {
        @Override
        public Object doExport() throws Exception {
            List<Cascade.Builder> cascadeBuilders = getCascadeBuilders();
            boolean bJsonIgnore = isJsonIgnoreRelation(cascadeBuilders);

            for (ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().models) {
                if (pojo.getFqcn().equals(classA)) {
                    TypeName listFieldType = TypeUtil.getGenericListType(classB);

                    String[] cascadeLevels = getCascadeLevels(cascadeBuilders, classA, classB);
                    Field.ModelField field = Field.ModelFieldGenerator.generateMany2ManyField(fieldA, listFieldType,
                            TypeUtil.getArrayListInitializer(), getCascadeLevels(cascadeBuilders, classA, classB));
                    if (bJsonIgnore) {
                        addJsonIgnoreRelationship(field);
                    } else {
                        addJsonManageRelationship(name, field, cascadeLevels);
                    }

                    pojo.getFields().add(field);
                } else if (pojo.getFqcn().equals(classB)) {
                    TypeName listFieldType = TypeUtil.getGenericListType(classA);

                    String[] cascadeLevels = getCascadeLevels(cascadeBuilders, classB, classA);
                    Field.ModelField field = Field.ModelFieldGenerator.generateMany2ManyField(fieldB, listFieldType,
                            TypeUtil.getArrayListInitializer(), getCascadeLevels(cascadeBuilders, classB, classA));
                    if (bJsonIgnore) {
                        addJsonIgnoreRelationship(field);
                    } else {
                        addJsonManageRelationship(name, field, cascadeLevels);
                    }

                    pojo.getFields().add(field);
                }
            }
            return null;
        }
    }

    public Set<POJO> getModels() {
        return models;
    }

    public void setModels(Set<POJO> models) {
        this.models = models;
    }

    public String getBasemodel() {
        return basemodel;
    }

    public void setBasemodel(String basemodel) {
        this.basemodel = basemodel;
    }

    public String getDateformat() {
        return dateformat;
    }

    public void setDateformat(String dateformat) {
        this.dateformat = dateformat;
    }

    public Set<One2One> getOne2ones() {
        return one2ones;
    }

    public void setOne2ones(Set<One2One> one2ones) {
        this.one2ones = one2ones;
    }

    public Set<One2Many> getOne2manys() {
        return one2manys;
    }

    public void setOne2manys(Set<One2Many> one2manys) {
        this.one2manys = one2manys;
    }

    public Set<Many2Many> getMany2manys() {
        return many2manys;
    }

    public void setMany2manys(Set<Many2Many> many2manys) {
        this.many2manys = many2manys;
    }

    public Set<Enum> getEnums() {
        return enums;
    }

    public void setEnums(Set<Enum> enums) {
        this.enums = enums;
    }
}

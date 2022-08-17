package org.springboot.initializer.model;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springboot.initializer.ExportPoint;

import javax.lang.model.element.Modifier;
import javax.persistence.GenerationType;
import java.lang.reflect.Type;
import java.util.*;

public class Field extends BaseModel implements ExportPoint {

    protected GenericType.FormatExporter initializer;

    public Field(){
        this.modifiers = new Modifier[]{Modifier.PUBLIC};
        this.initializer = null;
    }

    public Field(String name, String typeName, GenericType.FormatExporter initializer, Modifier... modifiers) throws Exception {
        this(name, ClassName.bestGuess(typeName), initializer, modifiers);
    }

    public Field(String name, TypeName t, GenericType.FormatExporter initializer, Modifier... modifiers) throws Exception {
        this.name = name;
        this.typeName = t;
        this.modifiers = modifiers;
        this.initializer = initializer;
    }

    public Field(String name, Type type, GenericType.FormatExporter initializer, Modifier... modifiers) {
        this.modifiers = modifiers;
        this.initializer = initializer;
        this.type = type;
        this.name = name;
    }

    public Field cloneNewField() throws Exception {
        Field f = new Field(getName(), getTypeName(), getInitializer(), getModifiers());
        f.setType(getType());
        return f;
    }

    public static Field tryCloneSysField(String name) {
        Field field = null;
        if (ModelFieldGenerator.SYS_FIELDS[0].equals(name)) {
            field = new ModelField(name, Long.class, null, Modifier.PROTECTED);
        } else if (ModelFieldGenerator.SYS_FIELDS[1].equals(name) || ModelFieldGenerator.SYS_FIELDS[2].equals(name)) {
            field = new ModelField(name, Date.class, null, Modifier.PROTECTED);
        }
        return field;
    }

    @Override
    public boolean check() {
        if (!super.check()) return false;
        if (type == null && typeName == null) return false;
        return true;
    }

    @Override
    public FieldSpec doExport() throws Exception {
        FieldSpec.Builder builder = type != null ? FieldSpec.builder(type, name) : FieldSpec.builder(typeName, name);
        for (Modifier m : modifiers) builder.addModifiers(m);
        if (initializer != null) builder.initializer(initializer.format, initializer.args);
        for (Annotation a : annotations) builder.addAnnotation((AnnotationSpec)a.export());
        return builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Field field = (Field) o;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    public GenericType.FormatExporter getInitializer() {
        return initializer;
    }

    public void setInitializer(GenericType.FormatExporter initializer) {
        this.initializer = initializer;
    }

    //model
    public static class ModelField extends Field {
        private static final String VALIDATOR_PACKAGE = "javax.validation.constraints.";

        protected String column;
        protected boolean nullable = true;
        protected boolean updatable = true;
        protected String enumType;
        protected Set<String> validate = new HashSet<>();

        public ModelField(){
            super();
        }

        public ModelField(String name, String typeName, GenericType.FormatExporter initializer, Modifier... modifiers) throws Exception {
            super(name, typeName, initializer, modifiers);
            setTypeName(ClassName.bestGuess(typeName));
        }

        public ModelField(String name, TypeName typeName, GenericType.FormatExporter initializer, Modifier... modifiers) throws Exception {
            super(name, typeName, initializer, modifiers);
        }

        public ModelField(String name, Type t, GenericType.FormatExporter initializer, Modifier... modifiers)  {
            super(name, t, initializer, modifiers);
        }

        @Override
        public void setTypeName(TypeName typeName)  {
            super.setTypeName(typeName);
            //translate when deserialize
            switch (typeName.toString()) {
                case "text":
                    this.typeName = ClassName.bestGuess("String");
                    annotations.add(new Annotation(Annotation.ModelAnnotationGenerator.LOB));
                    Map<String, Object> m = new HashMap<>();
                    m.put("columnDefinition", "text");
                    annotations.add(new Annotation(Annotation.ModelAnnotationGenerator.COLUMN, m));
                    break;
            }
            if (this.typeName == null)  this.typeName = typeName;
        }

        @Override
        public FieldSpec doExport() throws Exception {
            Map<String, Object> m = new HashMap<>();
            if (column != null) {
                m.put("name", column);
                if (!nullable) m.put("nullable", false);
                if (!updatable) m.put("updatable", false);
                getAnnotations().add(new Annotation(Annotation.ModelAnnotationGenerator.COLUMN, m));
            }
            if (enumType != null) {
                getAnnotations().add(Annotation.ModelAnnotationGenerator.generateEnumeratedAnnotation(enumType));
            }
            if (!validate.isEmpty()) {
                validate.forEach(v -> getAnnotations().add(new Annotation(VALIDATOR_PACKAGE + v)));
            }
            return super.doExport();
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
        }

        public boolean isNullable() {
            return nullable;
        }

        public void setNullable(boolean nullable) {
            this.nullable = nullable;
        }

        public boolean isUpdatable() {
            return updatable;
        }

        public void setUpdatable(boolean updatable) {
            this.updatable = updatable;
        }

        public String getEnumType() {
            return enumType;
        }

        public void setEnumType(String enumType) {
            this.enumType = enumType;
        }

        public Set<String> getValidate() {
            return validate;
        }

        public void setValidate(Set<String> validate) {
            this.validate = validate;
        }
    }

    public static Field generateLOGGER(String className) {
        return new Field("LOGGER", Logger.class,
                new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "$T.getLogger($L.class)", LoggerFactory.class, className),
                Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);
    }

    public static class ModelFieldGenerator {
        public static final String[] SYS_FIELDS = new String[]{"id", "created", "updated"};

        public static final String IDENTIFY_CLAZZ = "java.lang.Long";
        public static final String CREATE_TIMESTAMP_CLAZZ = "org.hibernate.annotations.CreationTimestamp";
        public static final String DATETIME_FORMAT_CLAZZ = "org.springframework.format.annotation.DateTimeFormat";
        public static final String ENTITY_MGR = "javax.persistence.EntityManager";
        public static final String PERSISTENCE_CTX = "javax.persistence.PersistenceContext";
        public static final String JAPQ_FACTORY = "com.querydsl.jpa.impl.JPAQueryFactory";

        static Field generateSerializableField() {
            return new Field("serialVersionUID", long.class, new GenericType.FormatExporter(
                    GenericType.FormatExporter.Type.STMT, "$Ll", new Random().nextLong()),
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        }

        static ModelField generateModelIDField() throws Exception {
            ModelField id = new ModelField("id", ClassName.bestGuess(IDENTIFY_CLAZZ), null, Modifier.PROTECTED);
            id.addAnnotation(new Annotation(Annotation.ModelAnnotationGenerator.BASE_ID));
            Map<String, Object> m = new HashMap<>();
            m.put("strategy", new Annotation.FQVN(GenerationType.class, GenerationType.IDENTITY));
            id.addAnnotation(new Annotation(Annotation.ModelAnnotationGenerator.BASE_GENERATEDVAL, m));
            return id;
        }

        static ModelField[] generateBaseTimestampField() throws Exception {
            ModelField[] fields = new ModelField[2];
            fields[0] = new ModelField("created", ClassName.get(Date.class), null, Modifier.PROTECTED);
            fields[0].addAnnotation(new Annotation(CREATE_TIMESTAMP_CLAZZ));
            fields[1] = new ModelField("updated", ClassName.get(Date.class), null, Modifier.PROTECTED);

            Map<String, Object> m = new HashMap<>();
            m.put("pattern", ConfigGraph.getGraph().getModel().getDateformat());
            Annotation dateTimeFormat = new Annotation(DATETIME_FORMAT_CLAZZ, m);
            Arrays.stream(fields).forEach((f) -> { f.addAnnotation(dateTimeFormat); });
            return fields;
        }

        public static Field generateEntityMgrField() throws Exception {
            Field em = new Field("em", ENTITY_MGR, null, Modifier.PROTECTED);
            em.addAnnotation(Annotation.ServiceAnnotationGenerator.generateAutowiredAnnotation());
            em.addAnnotation(new Annotation(PERSISTENCE_CTX));
            return em;
        }

        public static Field generateJpaQueryFactoryField() throws Exception {
            Field q = new Field("q", JAPQ_FACTORY, null, Modifier.PROTECTED);
            return q;
        }

        @FunctionalInterface
        interface TableJoinField {
            ModelField generateField(String name, String typename, GenericType.FormatExporter initializer,
                                            String[] cascadeLevel) throws Exception;
        }

        static ModelField generateOne2OneField(String name, String typename, GenericType.FormatExporter initializer,
                                               String[] cascadeLevel) throws Exception {
            return generateJoinField(name, typename, initializer, cascadeLevel,
                    Annotation.ModelAnnotationGenerator::generateOne2OneAnnotation);
        }

        static ModelField generateOne2ManyField(String name, TypeName typename, GenericType.FormatExporter initializer,
                                               String[] cascadeLevel) throws Exception {
            return generateJoinField(name, typename, initializer, cascadeLevel,
                    Annotation.ModelAnnotationGenerator::generateOne2ManyAnnotation);
        }

        static ModelField generateMany2OneField(String name, String typename, GenericType.FormatExporter initializer,
                                                String[] cascadeLevel) throws Exception {
            return generateJoinField(name, typename, initializer, cascadeLevel,
                    Annotation.ModelAnnotationGenerator::generateMany2OneAnnotation);
        }

        static ModelField generateMany2ManyField(String name, TypeName typename, GenericType.FormatExporter initializer,
                                                String[] cascadeLevel) throws Exception {
            return generateJoinField(name, typename, initializer, cascadeLevel,
                    Annotation.ModelAnnotationGenerator::generateMany2ManyAnnotation);
        }

        private static ModelField generateJoinField(String name, Object typename, GenericType.FormatExporter initializer,
                                                    String[] cascadeLevel,
                                                    Annotation.ModelAnnotationGenerator.TableJoinAnnotation tableJoinAnnotation) throws Exception {
            Field.ModelField field = null;
            if (typename instanceof TypeName) field = new Field.ModelField(name,
                    (TypeName) typename, initializer, Modifier.PRIVATE);
            else field = new Field.ModelField(name,
                    (String) typename, initializer, Modifier.PRIVATE);
            if (ModelMetaData.Cascade.NO_CASCADE.equals(cascadeLevel[0])) {
                field.addAnnotation(tableJoinAnnotation.generate(null));
            } else {
                field.addAnnotation(tableJoinAnnotation.generate(cascadeLevel));
            }
            //field.addAnnotation(new Annotation(Annotation.ModelAnnotationGenerator.JSON_IGNORE));
            return field;
        }

    }

}

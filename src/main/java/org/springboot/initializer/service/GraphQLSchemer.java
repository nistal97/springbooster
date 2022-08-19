package org.springboot.initializer.service;

import org.apache.commons.collections4.BagUtils;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.ExportSerializer;
import org.springboot.initializer.model.Field;
import org.springboot.initializer.model.GenericType;
import org.springboot.initializer.model.Method;
import org.springboot.initializer.util.BuiltInMethodFunc;
import org.springboot.initializer.util.TypeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class GraphQLSchemer {

    private Service service;
    private List<GenericType> dtoImpls;

    public GraphQLSchemer(Service service, List<GenericType> dtoImpls) {
        this.service = service;
        this.dtoImpls = dtoImpls;
    }

    public void addDtoImpl(GenericType dtoImpl) {
        dtoImpls.add(dtoImpl);
    }

    public static class GraphQLSchemaSerializer extends ExportSerializer.TextFileSerializer {
        private GraphQLSchemer schemer;

        public GraphQLSchemaSerializer() {
        }

        public GraphQLSchemaSerializer(GraphQLSchemer schemer) {
            this.schemer = schemer;
        }

        public void serializeRootGraph(List<Method> graphQLQuerySignature, Path path) throws IOException {
            ensureParent(path);

            path = Paths.get(path + "/root.graphqls");
            Files.writeString(path, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(path, addLineEnd("schema {"), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            Files.writeString(path, addLineEnd("    query: Query"), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            Files.writeString(path, addLineEnd("}"), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            Files.writeString(path, addLineEnd("type Query {"), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            for (Method m : graphQLQuerySignature) {
                String returnSignature = TypeUtil.isCollection(m.getMethodReturnTypeName()) ?
                        "[" + BuiltInMethodFunc.extractPackageAndName(TypeUtil.extractGenericType(m.getMethodReturnTypeName()))[1] + "Impl]"
                        : BuiltInMethodFunc.extractPackageAndName(m.getMethodReturnTypeName())[1];
                StringBuilder paramSb = new StringBuilder();
                int i = 0;
                for (String k : m.getParams().keySet()) {
                    if (i > 0) paramSb.append(", ");
                    paramSb.append(k).append(":").append(m.getParams().get(k));
                    i ++;
                }
                Files.writeString(path, addLineEnd("    " + m.getName() + "(" +  paramSb.toString() + ") : " + returnSignature),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            Files.writeString(path, addLineEnd("}"), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }

        private String mapGraphQLType(String typeName) {
            if (TypeUtil.isEnum(typeName)) return "String";
            switch (typeName) {
                case "int":
                    return "Int";
                default:
                    return typeName;
            }
        }

        private final Path getSchemaPath(Path path) {
            return Paths.get(path.toString() + "/" + schemer.service.getName() + ".graphqls");
        }

        @Override
        public void doSerialize(Path path) throws Exception {
            path = getSchemaPath(path);
            ensureParent(path);

            Files.writeString(path, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            for (GenericType dtoImpl : schemer.dtoImpls) {
                Files.writeString(path, addLineEnd("type " + dtoImpl.getClassName() + "{"), StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                for (ExportPoint e : dtoImpl.getFields()) {
                    Field f = (Field) e;
                    String content = "  " + f.getName() + " : ";
                    if (f.getName().endsWith("Id")) {
                        content += "ID!";
                    } else {
                        content += mapGraphQLType(f.getFieldTypeName());
                    }
                    content += ",";
                    Files.writeString(path, addLineEnd(content), StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                }
                Files.writeString(path, addLineEnd("}"), StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        }
    }
}
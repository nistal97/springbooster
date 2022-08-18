package org.springboot.initializer.service;

import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.ExportSerializer;
import org.springboot.initializer.model.Field;
import org.springboot.initializer.model.GenericType;

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

        public GraphQLSchemaSerializer(GraphQLSchemer schemer) {
            this.schemer = schemer;
        }

        private String mapGraphQLType(String typeName) {
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
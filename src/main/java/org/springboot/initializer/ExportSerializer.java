package org.springboot.initializer;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.model.ConfigGraph;
import org.springboot.initializer.util.BuiltInMethodFunc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public interface ExportSerializer {
    void doSerialize(Path path) throws Exception;
    boolean check();
    void decorateDataType();

    default void ensureParent(Path path) throws IOException {
        Path parentDir = path.getParent();
        if (!Files.exists(parentDir))
            Files.createDirectories(parentDir);
    }

    default void serialize(Path path) throws Exception {
        if (!this.check()) throw new Exception("Check failed:" + toString());
        ensureParent(path);
        doSerialize(path);
    }

    default void serialize(Path path, String packageName, TypeSpec spec) throws IOException {
        JavaFile javaFile = JavaFile.builder(packageName, spec).indent("    ").build();
        javaFile.writeToPath(path);
    }

    default void serialize(Path srcPath, Path destPath) throws Exception {
        List<String> contents = Files.readAllLines(srcPath);
        //check parent folder
        Path parentDir = destPath.getParent();
        if (!Files.exists(parentDir))
            Files.createDirectories(parentDir);
        for (int i = 0;i < contents.size();i ++) serialize(i, contents.get(i), destPath);
    }

    void serialize(int lineNum, String line, Path destPath) throws Exception;

    class TextFileSerializer implements ExportSerializer {

        @Override
        public void doSerialize(Path path) throws Exception {
        }

        @Override
        public boolean check() {
            return true;
        }

        @Override
        public void decorateDataType() {
        }

        @Override
        public void serialize(int lineNum, String line, Path destPath) throws Exception {
            if (lineNum == 0) {
                Files.writeString(destPath, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            Files.writeString(destPath, addLineEnd(line), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }

        protected final String addLineEnd(String str) {
            return str + "\n";
        }

    }

    class JavaFileSerializer extends TextFileSerializer {

        private String fqcn;

        public JavaFileSerializer(String fqcn) {
            this.fqcn = fqcn;
        }

        @Override
        public boolean check() {
            return !Strings.isBlank(fqcn) ;
        }

        @Override
        public void serialize(int lineNum, String line, Path destPath) throws Exception {
            String[] tokens = BuiltInMethodFunc.extractPackageAndName(fqcn);
            if (lineNum == 0) {
                Files.writeString(destPath, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Files.writeString(destPath, addLineEnd("package " + tokens[0] + ";"), StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                Files.writeString(destPath, addLineEnd("import " + ConfigGraph.getGraph().getRepo().getPagingContext()
                        + ";"), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(destPath, addLineEnd(line), StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        }

    }

}

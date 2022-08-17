package org.springboot.initializer;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface ExportSerializer {
    void doSerialize(Path path) throws Exception;
    boolean check();
    void decorateDataType();

    default void serialize(Path path) throws Exception {
        if (!this.check()) throw new Exception("Check failed:" + toString());
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

}

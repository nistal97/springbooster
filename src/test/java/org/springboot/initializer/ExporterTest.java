package org.springboot.initializer;

import com.squareup.javapoet.ClassName;
import org.junit.jupiter.api.Test;
import org.springboot.initializer.controller.ControllerMetaData;
import org.springboot.initializer.model.ConfigGraph;
import org.springboot.initializer.model.Field;
import org.springboot.initializer.model.GenericType;
import org.springboot.initializer.model.ModelMetaData;
import org.springboot.initializer.repo.RepoMetaData;
import org.springboot.initializer.service.ServiceMetaData;
import org.springboot.initializer.test.MockMetaData;
import org.springboot.initializer.util.YamlClient;

import javax.lang.model.element.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExporterTest {

    private static final Path SRC_GENERATED_PATH = Paths.get("src/main/generated");

    @Test
    public void testToString() {
        class A extends SpringBooster.Base{
            private String str = "abc";
            private int integer1 = 123;
            private double double2 = 567.89d;
            private B b = new B();

            class B extends SpringBooster.Base{
                List<C> list = new ArrayList<>();
                private long long3 = 987654321l;

                B() {
                    C c1 = new C();
                    c1.objs[0] = 1;
                    c1.objs[1] = '1';
                    list.add(c1);
                }

                class C extends SpringBooster.Base{
                    private Object[] objs = new Object[2];
                }
            }
        }
        System.out.println(new A());
    }

    @Test
    public void exportPOJO() throws Exception {
        Field f1 = new Field("strField", String.class, new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$S", "abc"), Modifier.PRIVATE);
        Field f2 = new Field("intField", int.class, new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "$L", "1"), Modifier.PRIVATE);
        Field f3 = new Field("setField", Set.class, new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "new $T()", ClassName.get("java.util", "HashSet")), Modifier.PRIVATE);

        List<ExportPoint> fields = new ArrayList<>();

        fields.add(f1);
        fields.add(f2);
        fields.add(f3);

        GenericType t = new GenericType(null, null, fields, null, "org.springboot.initializer", "GenericTestType");
        t.serialize(SRC_GENERATED_PATH);
    }

    @Test
    public void importThenExportModels() throws Exception {
        String modelstr = Files.readString(Paths.get("src/test/resources/model.yaml"));
        ModelMetaData model = (ModelMetaData) YamlClient.deserialize(modelstr, ModelMetaData.class);
        String repostr = Files.readString(Paths.get("src/test/resources/repo.yaml"));
        RepoMetaData repo = (RepoMetaData) YamlClient.deserialize(repostr, RepoMetaData.class);
        String servicestr = Files.readString(Paths.get("src/test/resources/service.yaml"));
        ServiceMetaData service = (ServiceMetaData) YamlClient.deserialize(servicestr, ServiceMetaData.class);
        String controllerstr = Files.readString(Paths.get("src/test/resources/controller.yaml"));
        ControllerMetaData controller = (ControllerMetaData) YamlClient.deserialize(controllerstr, ControllerMetaData.class);
        String testerstr = Files.readString(Paths.get("src/test/resources/test.yaml"));
        MockMetaData mock = (MockMetaData) YamlClient.deserialize(testerstr, MockMetaData.class);

        ConfigGraph graph = new ConfigGraph(model, repo, service, controller, mock);
        ConfigGraph.setGraph(graph);
        graph.serialize(SRC_GENERATED_PATH);
    }

}

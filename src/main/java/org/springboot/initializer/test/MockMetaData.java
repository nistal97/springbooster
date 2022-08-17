package org.springboot.initializer.test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.squareup.javapoet.ClassName;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.model.*;
import org.springboot.initializer.util.BuiltInMethodFunc;

import javax.lang.model.element.Modifier;
import java.util.*;

public class MockMetaData extends SpringBooster.Base{
    public static final String ENV = "dev";

    protected String packageName;
    protected String componentScanPackage;
    @JsonIgnore
    private MockGenerator generator = new MockGenerator();
    @JsonIgnore
    private AutoTester autoTester;

    public MockMetaData(){}

    public class MockGenerator implements ExportPoint {

        private FullGraphMockGenerator fullGraphMockGenerator = new FullGraphMockGenerator();

        @Override
        public GenericType doExport() throws Exception {
            List<ExportPoint> fields = new ArrayList<>();
            Set<ExportPoint> methods = new HashSet<>();
            methods.add(generateMockData());
            GenericType t = new GenericType(packageName + "." + AutoTester.BASE_TEST, null, fields, methods, packageName + ".mock",
                    "MockTest", false, Modifier.PUBLIC);
            getAutoTester().addAnnotations(t, ENV, false);
            return t;
        }

        private Method generateMockData() {
            List<GenericType.FormatExporter> stmts = new ArrayList<>();
            stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "generateFullGraph()"));

            Method m = new Method("testCreateRandomDataSet", void.class, null, stmts, Modifier.PROTECTED);
            m.addAnnotation(Annotation.TestAnnotationGenerator.generateTestAnnotation());
            return m;
        }

        @Override
        public boolean check() {
            return true;
        }

        public class FullGraphMockGenerator implements ExportPoint {
            public static final int MOCK_SIZE = 100;

            @Override
            public Method doExport() throws Exception {
                List<GenericType.FormatExporter> stmts = new ArrayList<>();
                //create individual list
                createIndividualList(stmts);
                //save no ref objs first
                saveAll(stmts);
                //one2one
                for (ModelMetaData.One2One one2One : ConfigGraph.getGraph().getModel().getOne2ones()) {
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                            "for (int i = 0;i < $L;i ++)", MOCK_SIZE));
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$L.get(i).set$L($L.get(i))",
                            BuiltInMethodFunc.getListVar(one2One.getClassA()), BuiltInMethodFunc.upperFirstCharName(one2One.getFieldA()),
                            BuiltInMethodFunc.getListVar(one2One.getClassB())));
                    if (checkFieldExists(one2One.getClassB(), one2One.getFieldB())) {
                        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                "$L.get(i).set$L($L.get(i))",
                                BuiltInMethodFunc.getListVar(one2One.getClassB()), BuiltInMethodFunc.upperFirstCharName(one2One.getFieldB()),
                                BuiltInMethodFunc.getListVar(one2One.getClassA())));
                    }
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
                }
                //one2many
                for (ModelMetaData.One2One one2One : ConfigGraph.getGraph().getModel().getOne2manys()) {
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                            "for (int i = 0;i < $L;i ++)", MOCK_SIZE/2));
                    //every A match 2 B
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$L.get(i).get$L().add($L.get(i*2))",
                            BuiltInMethodFunc.getListVar(one2One.getClassA()), BuiltInMethodFunc.upperFirstCharName(one2One.getFieldA()),
                            BuiltInMethodFunc.getListVar(one2One.getClassB())));
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$L.get(i).get$L().add($L.get(i*2 + 1))",
                            BuiltInMethodFunc.getListVar(one2One.getClassA()), BuiltInMethodFunc.upperFirstCharName(one2One.getFieldA()),
                            BuiltInMethodFunc.getListVar(one2One.getClassB())));
                    if (checkFieldExists(one2One.getClassB(), one2One.getFieldB())) {
                        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                "$L.get(i*2).set$L($L.get(i))",
                                BuiltInMethodFunc.getListVar(one2One.getClassB()), BuiltInMethodFunc.upperFirstCharName(one2One.getFieldB()),
                                BuiltInMethodFunc.getListVar(one2One.getClassA())));
                        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                "$L.get(i*2 + 1).set$L($L.get(i))",
                                BuiltInMethodFunc.getListVar(one2One.getClassB()), BuiltInMethodFunc.upperFirstCharName(one2One.getFieldB()),
                                BuiltInMethodFunc.getListVar(one2One.getClassA())));
                    }
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
                }
                //many2many
                for (ModelMetaData.One2One one2One : ConfigGraph.getGraph().getModel().getMany2manys()) {
                    //every A match all B
                    attachMultiple(stmts, one2One.getClassA(), BuiltInMethodFunc.upperFirstCharName(one2One.getFieldA()),
                            one2One.getClassB());
                    //every B match all A
                    if (checkFieldExists(one2One.getClassB(), one2One.getFieldB())) {
                        attachMultiple(stmts, one2One.getClassB(), BuiltInMethodFunc.upperFirstCharName(one2One.getFieldB()),
                                one2One.getClassA());
                    }
                }
                //now save
                saveAll(stmts);
                //clear L1 Cache
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "em.clear()"));

                Method generateFullGraph = new Method("generateFullGraph", void.class, null, stmts, Modifier.PROTECTED);
                return generateFullGraph;
            }

            private boolean checkFieldExists(String fqcn, String fieldName) {
                //possibly not a bidirection relation, need to check if ClassB contains fieldB
                for(ModelMetaData.POJO pojo : ConfigGraph.getGraph().getModel().getModels()) {
                    if (pojo.getFqcn().equals(fqcn)) {
                        for (ExportPoint e : pojo.getFields()) {
                            Field f = (Field) e;
                            if (f.getName().equals(fieldName)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            private void attachMultiple(List<GenericType.FormatExporter> stmts, String classA, String fieldA, String classB) {
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                        "for (int i = 0;i < $L;i ++)", MOCK_SIZE));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW,
                        "for (int j = 0;j < $L;j ++)", MOCK_SIZE));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "$L.get(i).get$L().add($L.get(j))",
                        BuiltInMethodFunc.getListVar(classA), BuiltInMethodFunc.upperFirstCharName(fieldA),
                        BuiltInMethodFunc.getListVar(classB)));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
            }

            private void createIndividualList(List<GenericType.FormatExporter> stmts) {
                ConfigGraph.getGraph().getModel().getModels().forEach(pojo -> {
                    String[] ns = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn());
                    String var = ns[1].toLowerCase();
                    String listVar = BuiltInMethodFunc.getListVar(pojo.getFqcn());
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$LList = $LListGenerator()", var, var));
                });
            }

            private void saveAll(List<GenericType.FormatExporter> stmts) {
                ConfigGraph.getGraph().getModel().getModels().forEach(pojo -> {
                    String[] ns = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn());
                    String var = ns[1].toLowerCase();
                    String listVar = BuiltInMethodFunc.getListVar(pojo.getFqcn());
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "$LRepo.saveAllAndFlush($L)", var, listVar));
                });
            }

            @Override
            public boolean check() {
                return true;
            }

        }

        public FullGraphMockGenerator getRepoMockGenerator() {
            return fullGraphMockGenerator;
        }

        public void setRepoMockGenerator(FullGraphMockGenerator fullGraphMockGenerator) {
            this.fullGraphMockGenerator = fullGraphMockGenerator;
        }
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getComponentScanPackage() {
        return componentScanPackage;
    }

    public void setComponentScanPackage(String componentScanPackage) {
        this.componentScanPackage = componentScanPackage;
    }

    public MockGenerator getGenerator() {
        return generator;
    }

    public void setGenerator(MockGenerator generator) {
        this.generator = generator;
    }

    public AutoTester getAutoTester() {
        if (autoTester == null) autoTester = new AutoTester(packageName, componentScanPackage);
        return autoTester;
    }

    public void setAutoTester(AutoTester autoTester) {
        this.autoTester = autoTester;
    }
}

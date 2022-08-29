package org.springboot.initializer.model;

import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportSerializer;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.auth.AuthMetaData;
import org.springboot.initializer.controller.Controller;
import org.springboot.initializer.controller.ControllerMetaData;
import org.springboot.initializer.repo.QueryDSLRepo;
import org.springboot.initializer.repo.RepoMetaData;
import org.springboot.initializer.repo.Repository;
import org.springboot.initializer.service.GraphQLSchemer;
import org.springboot.initializer.service.Service;
import org.springboot.initializer.service.ServiceMetaData;
import org.springboot.initializer.test.AutoTester;
import org.springboot.initializer.test.MockMetaData;
import org.springboot.initializer.util.BuiltInMethodFunc;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConfigGraph extends SpringBooster.Base implements ExportSerializer {

    private ModelMetaData model;
    private RepoMetaData repo;
    private ServiceMetaData service;
    private AuthMetaData auth;
    private ControllerMetaData controller;
    private MockMetaData mock;

    private static ConfigGraph graph;
    public static ConfigGraph getGraph() {
        return graph;
    }
    public static void setGraph(ConfigGraph graph1) {
        graph = graph1;
    }

    public ConfigGraph(ModelMetaData model, RepoMetaData repo, ServiceMetaData service, AuthMetaData auth,
                       ControllerMetaData controller, MockMetaData mock) {
        this.model = model;
        this.repo = repo;
        this.service = service;
        this.auth = auth;
        this.controller = controller;
        this.mock = mock;
    }

    @Override
    public void doSerialize(Path path) throws Exception {
        if (model != null) {
            Path graphqlPath = Paths.get(path + "/graphqls/");

            if (!Strings.isBlank(model.getBasemodel())) model.exportBaseModel().serialize(path);
            //render enums
            for (ModelMetaData.Enum e : model.getEnums()) {
                GenericEnum ge = (GenericEnum) e.export();
                ge.serialize(path);
            }

            //render pojo models
            for (ModelMetaData.POJO pojo : model.getModels()) {
                GenericType t = (GenericType)pojo.export();
                if (t == null) throw new Exception("Invalid POJO defination found:" + t.getClassName());
            }
            //add one2one
            for (ModelMetaData.One2One o : model.getOne2ones()) o.export();
            //add one2many
            for (ModelMetaData.One2One o : model.getOne2manys()) o.export();
            //add many2many
            for (ModelMetaData.One2One o : model.getMany2manys()) o.export();
            //render again
            for (ModelMetaData.POJO pojo : model.getModels()) ((GenericType)pojo.export()).serialize(path);


            //generate repo map first
            for (GenericType t : repo.exportContexts()) {
                t.serialize(path);
            }
            for (ModelMetaData.POJO pojo : model.getModels()) {
                String[] arr = BuiltInMethodFunc.extractPackageAndName(pojo.getFqcn());
                Repository repository = new Repository(null, repo.getPackageName(),
                        arr[1] + "Repo", pojo.getFqcn(), Field.ModelFieldGenerator.IDENTIFY_CLAZZ);
                repo.addRepository(repository);
            }

            //services & impl
            //BaseServiceImpl
            if (!Strings.isBlank(service.getBaseService())) {
                Path srcPath = Path.of(ClassLoader.getSystemResource("template/BaseServiceImpl").toURI());
                Path destPath = Paths.get("src/main/generated/" + BuiltInMethodFunc.convertFQCN2Path(service.getBaseService()) + ".java");
                new JavaFileSerializer(service.getBaseService()).serialize(srcPath, destPath);
            }
            //exportviews
            QueryDSLRepo.exportBaseRepo().serialize(path);
            List<Method> graphQLQuerySignature = new ArrayList<>();
            for (Service service : service.getServices()) {
                List<GenericType> dtoImpls = new ArrayList<>();
                if (!service.getViews().isEmpty()) {
                    String[] fqcn = BuiltInMethodFunc.extractPackageAndName(service.getFqcn());
                    QueryDSLRepo repo = new QueryDSLRepo(fqcn[1].split("Service")[0] + "QueryRepo", service.getViews());
                    GenericType repoType = ((GenericType)repo.export());
                    service.addQueryDSLRepo(repoType);
                    for (Service.View view : service.getViews()) {
                        if (view.getDto() != null) {
                            view.getDto().serialize(path);
                            view.getDtoImpl().serialize(path);
                            if (view.isGraphql()) {
                                dtoImpls.add(view.getDtoImpl());
                                graphQLQuerySignature.addAll((List<Method>)view.export());
                            }
                        }
                    }
                    repoType.serialize(path);
                }
                //graphql schema
                if (!dtoImpls.isEmpty()) {
                    new GraphQLSchemer.GraphQLSchemaSerializer(new GraphQLSchemer(service, dtoImpls)).serialize(graphqlPath);
                }
                //auth
                boolean bGenerateAuthToken = false;
                if (auth != null && auth.getTokenService() != null && auth.getTokenService().getFqcn() != null &&
                        service.getFqcn().equals(auth.getTokenService().getFqcn())) {
                    bGenerateAuthToken = true;
                }
                if (bGenerateAuthToken) {
                    auth.getTokenService().setbAbstract(true);
                    service.getMethods().add((Method) auth.getTokenService().export());
                }
                service.serialize(path);
                GenericType serviceImplExported = (GenericType)service.getImpl().export();
                if (bGenerateAuthToken) {
                    auth.getTokenService().setbAbstract(false);
                    serviceImplExported.getMethods().add((Method) auth.getTokenService().export());
                }
                serviceImplExported.serialize(path);
            }
            if (!graphQLQuerySignature.isEmpty()) {
                new GraphQLSchemer.GraphQLSchemaSerializer().serializeRootGraph(graphQLQuerySignature, graphqlPath);
            }

            //serialize repo
            for (Repository repository : repo.getRepositoryMap().values()) {
                repository.serialize(path);
            }

            //validator
            if (!Strings.isBlank(controller.getValidator())) {
                Path srcPath = Path.of(ClassLoader.getSystemResource("template/Validator").toURI());
                Path destPath = Paths.get("src/main/generated/" + BuiltInMethodFunc.convertFQCN2Path(controller.getValidator()) + ".java");
                new JavaFileSerializer(controller.getValidator()).serialize(srcPath, destPath);
            }
            for (Controller controller : controller.getControllers()) {
                ((GenericType)controller.export()).serialize(path);
            }

            //mock & tester
            ((GenericType)mock.getGenerator().export()).serialize(path);
            for (GenericType t : (List<GenericType>)mock.getAutoTester().export()) {
                t.serialize(path);
            }
        }
    }

    @Override
    public boolean check() {
        return true;
    }

    @Override
    public void decorateDataType() {}

    @Override
    public void serialize(int lineNum, String line, Path destPath) throws Exception {
    }

    public ModelMetaData getModel() {
        return model;
    }

    public void setModel(ModelMetaData model) {
        this.model = model;
    }

    public RepoMetaData getRepo() {
        return repo;
    }

    public void setRepo(RepoMetaData repo) {
        this.repo = repo;
    }

    public ServiceMetaData getService() {
        return service;
    }

    public void setService(ServiceMetaData service) {
        this.service = service;
    }

    public ControllerMetaData getController() {
        return controller;
    }

    public void setController(ControllerMetaData controller) {
        this.controller = controller;
    }

    public MockMetaData getMock() {
        return mock;
    }

    public void setMock(MockMetaData mock) {
        this.mock = mock;
    }
}


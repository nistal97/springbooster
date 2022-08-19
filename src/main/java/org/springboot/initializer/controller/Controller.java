package org.springboot.initializer.controller;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.model.*;
import org.springboot.initializer.repo.BaseRepo;
import org.springboot.initializer.service.Service;
import org.springboot.initializer.util.BuiltInMethodFunc;
import org.springboot.initializer.util.TypeUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.lang.model.element.Modifier;
import java.util.*;

public class Controller extends SpringBooster.Base implements ExportPoint {
    private String fqcn;
    private String path;
    private List<Service> services = new ArrayList<>();

    public Controller(){}

    @Override
    public GenericType doExport() throws Exception {
        List<ExportPoint> fields = new ArrayList<>();
        Set<ExportPoint> methods = new HashSet<>();

        String[] n = BuiltInMethodFunc.extractPackageAndName(fqcn);
        for (Service s : services) {
            //find by fqcn
            Service service = ConfigGraph.getGraph().getService().getServiceMap().get(s.getFqcn());
            Set<ExportPoint> allmethods = service.getMethods(), serviceMethods = new HashSet<>();
            //filter graphql
            for (ExportPoint e : allmethods) {
                Method m = (Method) e;
                boolean isGraphQLMethod = false;
                loop : for (Service.View view : service.getViews()) {
                    if (view.isGraphql()) {
                        for (Method graphMethod : view.getGraphQLMethods()) {
                            if (graphMethod.getName().equals(m.getName())) {
                                isGraphQLMethod = true;
                                break loop;
                            }
                        }
                    }
                }
                if (!isGraphQLMethod) serviceMethods.add(m);
            }

            //fields
            String[] repoNaming = BuiltInMethodFunc.extractPackageAndName(s.getFqcn());
            Field f = new Field(repoNaming[1].toLowerCase(), s.getFqcn(), null, Modifier.PRIVATE);
            f.addAnnotation(Annotation.ServiceAnnotationGenerator.generateAutowiredAnnotation());
            fields.add(f);
            fields.add(Field.generateLOGGER(n[1]));

            for (ExportPoint e : serviceMethods) {
                Method m = new Method((Method) e);
                m.setbAbstract(false);
                m.clearAnnotations();
                m.clearStatements();

                //reset param if dtoViewParam
                LinkedHashMap<String, TypeName> dtoParamMap = null;
                if (m.getName().startsWith("get")) {
                    Service.View view = service.getView(m.getName().split("get")[1].split("Paged")[0]);
                    if (view != null && view.isDtoViewParam()) {
                        dtoParamMap = BaseRepo.generateDTOParams(view.getDto());
                    }
                }

                String path = m.getName().toLowerCase();
                if (m.getParams().isEmpty()) {
                    m.addAnnotation(Annotation.ControllerAnnotationGenerator.generateGetMappingAnnotation(path));
                } else {
                    if (m.getName().startsWith("delete")) {
                        m.addAnnotation(Annotation.ControllerAnnotationGenerator.generateDeleteMappingAnnotation(path));
                    } else if (m.getName().startsWith("update")) {
                        m.addAnnotation(Annotation.ControllerAnnotationGenerator.generatePutMappingAnnotation(path));
                    } else {
                        m.addAnnotation(Annotation.ControllerAnnotationGenerator.generatePostMappingAnnotation(path));
                    }
                }

                //stmts
                List<GenericType.FormatExporter> stmts = new ArrayList<>();

                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.BEGIN_FLOW, "try"));
                CodeBlock.Builder builder = CodeBlock.builder();

                int i = 0;
                boolean emptyBody = false;
                if ((m.getType() != null && m.getType().getTypeName().equalsIgnoreCase("void")) || (m.getTypeName() != null && m.getTypeName().equals(TypeName.VOID) )) {
                    builder.add("$L.$L(", repoNaming[1].toLowerCase(), m.getName());
                    for (String name : m.getParams().keySet()) {
                        if (i == 0) builder.add("$L", name);
                        else builder.add(", $L", name);
                        i++;
                    }
                    builder.add(")");
                    emptyBody = true;
                } else {
                    builder.add("return $T.status($T.$L).body($L.$L(", ResponseEntity.class,
                            HttpStatus.class, HttpStatus.OK.name(), repoNaming[1].toLowerCase(), m.getName());
                    for (String name : m.getParams().keySet()) {
                        if (i > 0) builder.add(", ");
                        if (dtoParamMap == null) {
                            builder.add("$L", name);
                        } else {
                            builder.add("obj.get$L()", BuiltInMethodFunc.upperFirstCharName(name));
                        }
                        i++;
                    }
                    builder.add("))");
                }

                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.CODE_BLOCK, builder.build()));
                if (emptyBody) {
                    stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                            "return $T.status($T.$L).build()", ResponseEntity.class,
                            HttpStatus.class, HttpStatus.OK.name()));
                }
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.NEXT_FLOW, "catch ($T e)", Exception.class));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                        "LOGGER.error(\"Status: $L Error occured:\", e)", HttpStatus.INTERNAL_SERVER_ERROR.value()));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT, "return $T.status($T.$L).build()", ResponseEntity.class,
                        HttpStatus.class, HttpStatus.INTERNAL_SERVER_ERROR.name()));
                stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.END_FLOW));
                m.setStatements(stmts);

                if (dtoParamMap != null)  m.setParams(dtoParamMap);
                m.getParams().keySet().forEach(k -> {
                    if (!TypeUtil.isPrimitive(m.getParams().get(k).toString())) {
                        m.addParamAnnotation(k, Annotation.ControllerAnnotationGenerator.generateReqBodyAnnotation());
                    }
                    m.addParamAnnotation(k, Annotation.ControllerAnnotationGenerator.generateValidatorAnnotation());
                });
                m.setType(ResponseEntity.class);
                m.setTypeName(null);
                methods.add(m);
            }
        }
        GenericType t = new GenericType(null, null, fields, methods, n[0], n[1], Modifier.PUBLIC);
        t.setEnableGetSet(false);
        addControllerAnnotation(t);
        return t;
    }

    @Override
    public boolean check() {
        return !Strings.isBlank(fqcn);
    }

    protected void addControllerAnnotation(GenericType t) {
        t.addAnnotation(Annotation.ControllerAnnotationGenerator.generateRestControllerAnnotation());
        t.addAnnotation(Annotation.ControllerAnnotationGenerator.generateCrossOriginAnnotation());
        t.addAnnotation(Annotation.ControllerAnnotationGenerator.generateRequestMappingAnnotation(path));
    }

    public String getFqcn() {
        return fqcn;
    }

    public void setFqcn(String fqcn) {
        this.fqcn = fqcn;
    }

    public List<Service> getServices() {
        return services;
    }

    public void setServices(List<Service> services) {
        this.services = services;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}

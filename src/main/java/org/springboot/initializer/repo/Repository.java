package org.springboot.initializer.repo;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.model.GenericInterface;

import java.util.Set;


public class Repository extends GenericInterface {

    protected String entityType;
    protected String identityType;

    protected Class[] superInterfaces = new Class[]{org.springframework.data.jpa.repository.JpaRepository.class,
            org.springframework.data.querydsl.QuerydslPredicateExecutor.class};

    public Repository(Set<ExportPoint> methods, String packageName, String interfaceName, String entityType, String identityType) {
        super(methods, packageName, interfaceName);
        this.entityType = entityType;
        this.identityType = identityType;
    }

    @Override
    protected void decorateBuilder(TypeSpec.Builder builder) {
        builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(superInterfaces[0]),
                        ClassName.bestGuess(entityType), ClassName.bestGuess(identityType)))
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(superInterfaces[1]),
                        ClassName.bestGuess(entityType)))
                .addAnnotation(org.springframework.stereotype.Repository.class);
    }

    @Override
    public boolean check() {
        return !Strings.isBlank(identityType) && super.check();
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }
}

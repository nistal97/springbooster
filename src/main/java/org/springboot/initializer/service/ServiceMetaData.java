package org.springboot.initializer.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.model.ConfigGraph;
import org.springboot.initializer.model.GenericType;
import org.springboot.initializer.util.BuiltInMethodFunc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class ServiceMetaData extends SpringBooster.Base {

    private String baseService;
    private List<Service> services = new ArrayList<>();

    @JsonIgnore
    private Map<String, Service> serviceMap = new HashMap<>();

    public List<Service> getServices() {
        return services;
    }

    public GenericType getDTOImplByName(String dtoImplFQCN) {
        for (Service service : getServices()) {
            for (Service.View view : service.getViews()) {
                GenericType dtoImpl = view.getDtoImpl();
                if ((dtoImpl.getPackageName() + "." + dtoImpl.getClassName()).equals(dtoImplFQCN)) {
                    return dtoImpl;
                }
            }
        }
        return null;
    }

    public Service.View getViewByName(String viewName) {
        for (Service service : getServices()) {
            for (Service.View view : service.getViews()) {
                if (view.getName().equalsIgnoreCase(viewName)) {
                    return view;
                }
            }
        }
        return null;
    }

    public void setServices(List<Service> services) {
        this.services = services;
        serviceMap.clear();
        services.forEach(service -> serviceMap.put(service.getFqcn(), service));
    }

    public Map<String, Service> getServiceMap() {
        return serviceMap;
    }

    public String getBaseService() {
        return baseService;
    }

    public void setBaseService(String baseService) {
        this.baseService = baseService;
    }
}

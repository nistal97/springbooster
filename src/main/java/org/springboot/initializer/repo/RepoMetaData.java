package org.springboot.initializer.repo;

import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.model.Field;
import org.springboot.initializer.model.GenericType;
import org.springboot.initializer.util.BuiltInMethodFunc;
import org.springboot.initializer.util.TypeUtil;

import javax.lang.model.element.Modifier;
import java.util.*;

public class RepoMetaData extends SpringBooster.Base{
    private String packageName;
    private String pagingContext;
    private String filterContext;
    private List<Repository> repositories = new ArrayList<>();

    private Map<String, Repository> repositoryMap = new HashMap<>();

    public RepoMetaData(){}

    public List<GenericType> exportContexts() throws Exception {
        List<GenericType> ts = new ArrayList<>();
        if (!Strings.isBlank(getFilterContext())) {
            List<ExportPoint> fields = new ArrayList<>();
            fields.add(new Field("field", String.class, null, Modifier.PRIVATE));
            fields.add(new Field("pattern", String.class, null, Modifier.PRIVATE));
            String[] n = BuiltInMethodFunc.extractPackageAndName(filterContext);
            ts.add(new GenericType(null, null, fields, null, n[0], n[1], Modifier.PUBLIC));
        }

        if (!Strings.isBlank(getPagingContext())) {
            List<ExportPoint> fields = new ArrayList<>();
            fields.add(new Field("offset", int.class, null, Modifier.PRIVATE));
            fields.add(new Field("limit", int.class, null, Modifier.PRIVATE));
            GenericType.FormatExporter initializer = new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                    "new $T()", ArrayList.class);
            fields.add(new Field("sortasc", TypeUtil.getGenericListType("String"), initializer, Modifier.PRIVATE));
            fields.add(new Field("sortdesc", TypeUtil.getGenericListType("String"), initializer, Modifier.PRIVATE));
            fields.add(new Field("filters", TypeUtil.getGenericListType(filterContext), initializer, Modifier.PRIVATE));

            String[] n = BuiltInMethodFunc.extractPackageAndName(pagingContext);
            ts.add(new GenericType(null, null, fields, null, n[0], n[1], Modifier.PUBLIC));
        }
        return ts;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void addRepository(Repository repository) {
        repositories.add(repository);
        repositoryMap.put(repository.getPackageName() + "." + repository.getName(), repository);
    }

    public void setRepositories(List<Repository> repositories) {
        this.repositories = repositories;
        repositoryMap.clear();
        repositories.forEach(repository -> repositoryMap.put(repository.getPackageName() + "." + repository.getName(),repository));
    }

    public Map<String, Repository> getRepositoryMap() {
        return repositoryMap;
    }

    public String getPagingContext() {
        return pagingContext;
    }

    public void setPagingContext(String pagingContext) {
        this.pagingContext = pagingContext;
    }

    public String getFilterContext() {
        return filterContext;
    }

    public void setFilterContext(String filterContext) {
        this.filterContext = filterContext;
    }
}

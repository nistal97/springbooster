package org.springboot.initializer.repo;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.springboot.initializer.model.Field;
import org.springboot.initializer.model.GenericInterface;
import org.springboot.initializer.model.ModelMetaData;
import org.springboot.initializer.util.BuiltInMethodFunc;

import java.util.Arrays;
import java.util.LinkedHashMap;

public abstract class BaseRepo {
    public static final String[] OPER_STR = new String[]{"==", "!=", ">", "<", ">=", "<=", "%"};

    public static LinkedHashMap<String, TypeName> generateDTOParams(GenericInterface dto) {
        LinkedHashMap<String, TypeName> params = new LinkedHashMap<>();
        params.put("obj", ClassName.bestGuess(dto.getPackageName() + "." + dto.getName() + "Impl"));
        return params;
    }

    public static LinkedHashMap<String, TypeName> getAllCandidates(String where) {
        LinkedHashMap<String, TypeName> map = new LinkedHashMap();
        if (where == null) {
            return map;
        }

        for (String oper : OPER_STR) where = where.replaceAll(oper, " ");
        String[] QUOTES = new String[]{"\\(", "\\)"};
        for (String c : QUOTES) where = where.replaceAll(c, " ");
        Arrays.stream(where.split(" ")).forEach(w->{
            if (w.indexOf(".") > -1) {
                String[] arr = w.split("\\.");
                Field f = ModelMetaData.POJO.getFieldByName(arr[0], arr[1]);
                map.put(arr[0] + BuiltInMethodFunc.upperFirstCharName(arr[1]), f.getTypeName() != null ? f.getTypeName() : ClassName.get(f.getType()));
            }
        });
        return map;
    }

    public static LinkedHashMap<String, TypeName> getNativeSQLParams(String sql) {
        LinkedHashMap<String, TypeName> map = new LinkedHashMap();
        if (sql != null) {
            String[] tokens = sql.split(":");
            for (int i = 1; i < tokens.length; i ++) {
                String param = tokens[i].split(" ")[0];
                if (!param.equals(param.toLowerCase())) {
                    String[] dtoToken = BuiltInMethodFunc.extractDTOField(param);
                    Field f = ModelMetaData.POJO.getFieldByName(dtoToken[0], dtoToken[1].toLowerCase());
                    map.put(dtoToken[0] + BuiltInMethodFunc.upperFirstCharName(dtoToken[1]), f.getTypeName() != null ? f.getTypeName() : ClassName.get(f.getType()));
                }
            }
        }
        return map;
    }

}

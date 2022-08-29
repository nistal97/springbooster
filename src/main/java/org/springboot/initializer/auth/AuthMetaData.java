package org.springboot.initializer.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.apache.logging.log4j.util.Strings;
import org.springboot.initializer.ExportPoint;
import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.model.GenericType;
import org.springboot.initializer.model.Method;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class AuthMetaData extends SpringBooster.Base {

    private AuthStrategy authStrategy;
    private TokenService tokenService;

    public enum AuthStrategy {
        JWT,
    }

    public class TokenService implements ExportPoint {
        private String fqcn;
        private boolean bAbstract = false;

        @Override
        public Object doExport() throws Exception {
            Method m = null;
            switch (authStrategy) {
                case JWT ->  {
                    LinkedHashMap<String, TypeName> credentialParams = new LinkedHashMap<>();
                    credentialParams.put("uid", ClassName.get(String.class));
                    credentialParams.put("pwd", ClassName.get(String.class));
                    List<GenericType.FormatExporter> stmts = new ArrayList<>();
                    m = new Method("generateToken", String.class, credentialParams, stmts, Modifier.PUBLIC);
                    m.setbAbstract(bAbstract);
                    if (!bAbstract) {
                        stmts.add(new GenericType.FormatExporter(GenericType.FormatExporter.Type.STMT,
                                "return $T.create().withAudience(uid).sign($T.HMAC256(pwd))",
                                JWT.class, Algorithm.class));
                        m.setStatements(stmts);
                    }
                }
            }
            return m;
        }

        @Override
        public boolean check() {
            return authStrategy != null && tokenService != null && !Strings.isBlank(tokenService.getFqcn());
        }

        public String getFqcn() {
            return fqcn;
        }

        public void setFqcn(String fqcn) {
            this.fqcn = fqcn;
        }

        public boolean isbAbstract() {
            return bAbstract;
        }

        public void setbAbstract(boolean bAbstract) {
            this.bAbstract = bAbstract;
        }
    }

    public AuthStrategy getAuthStrategy() {
        return authStrategy;
    }

    public void setAuthStrategy(AuthStrategy authStrategy) {
        this.authStrategy = authStrategy;
    }

    public TokenService getTokenService() {
        return tokenService;
    }

    public void setTokenService(TokenService tokenService) {
        this.tokenService = tokenService;
    }
}

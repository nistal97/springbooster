package org.springboot.initializer;

public interface ExportPoint {
    Object doExport() throws Exception;
    boolean check();

    default Object export() throws Exception {
        if (!this.check()) throw new Exception("Check failed:" + toString());
        return doExport();
    }

}

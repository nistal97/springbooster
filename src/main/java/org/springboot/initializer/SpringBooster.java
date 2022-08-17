package org.springboot.initializer;

import java.lang.reflect.Field;

public class SpringBooster {

    public static abstract class Base {

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(this.getClass().getName()).append('{');

            int n = 0;
            Class clazz = this.getClass();
            while (clazz != null) {
                for (int i = 0;i < clazz.getDeclaredFields().length;i ++) {
                    Field f = clazz.getDeclaredFields()[i];
                    f.setAccessible(true);
                    Object fieldValue = null;
                    try {
                        fieldValue = f.get(this);
                        if (fieldValue != null) {
                            if (n > 0) sb.append(", ");
                            boolean isArr = fieldValue.getClass().isArray();
                            if (isArr) {
                                sb.append(f.getName()).append("=[");
                                Object[] arr = (Object[]) fieldValue;
                                for (int j = 0;j < arr.length;j ++) {
                                    if (j > 0) sb.append(",");
                                    sb.append(arr[i]);
                                }
                                sb.append(']');
                            } else {
                                sb.append(f.getName()).append('=').append(fieldValue);
                            }
                            n++;
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
                clazz = clazz.getSuperclass();
            }
            sb.append('}');

            return sb.toString();
        }
    }

    public static void main(String[] args) {
    }

}

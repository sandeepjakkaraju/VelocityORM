package com.velocityorm.processor;

import com.velocityorm.core.annotation.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author sandeepkumarjakkaraju
 */
@SupportedAnnotationTypes("com.velocityorm.core.annotation.Entity")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class VelocityORMProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Entity.class)) {
            if (element.getKind() == ElementKind.CLASS) {
                TypeElement classElement = (TypeElement) element;
                try {
                    generateMetaClass(classElement);
                    generateRepositoryImplClass(classElement);
                } catch (Exception e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate VelocityORM code: " + e.getMessage());
                }
            }
        }
        return true;
    }

    private void generateMetaClass(TypeElement classElement) throws Exception {
        String packageName = processingEnv.getElementUtils().getPackageOf(classElement).getQualifiedName().toString();
        String className = classElement.getSimpleName().toString();
        String metaClassName = className + "Meta";

        Table tableAnn = classElement.getAnnotation(Table.class);
        String tableName = tableAnn != null ? tableAnn.value() : className.toLowerCase() + "s";

        List<FieldInfo> fields = new ArrayList<>();
        String idFieldName = null;
        String idColumnName = null;
        String idType = null;
        boolean isIdGenerated = false;

        String versionFieldName = null;
        String createdAtFieldName = null;
        String updatedAtFieldName = null;

        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                
                if (field.getAnnotation(Ignore.class) != null || field.getAnnotation(Transient.class) != null ||
                        field.getModifiers().contains(Modifier.TRANSIENT)) {
                    continue;
                }

                String fName = field.getSimpleName().toString();
                String fType = field.asType().toString();
                
                Column colAnn = field.getAnnotation(Column.class);
                String cName = (colAnn != null && !colAnn.name().isEmpty()) ? colAnn.name() : fName;
                boolean nullable = colAnn == null || colAnn.nullable();
                int length = colAnn != null ? colAnn.length() : 255;
                boolean unique = colAnn != null && colAnn.unique();

                boolean isId = field.getAnnotation(Id.class) != null;
                boolean isGenerated = field.getAnnotation(GeneratedValue.class) != null;
                boolean isVersion = field.getAnnotation(Version.class) != null;
                boolean isCreatedAt = field.getAnnotation(CreatedAt.class) != null;
                boolean isUpdatedAt = field.getAnnotation(UpdatedAt.class) != null;
                boolean isEncrypted = field.getAnnotation(Encrypted.class) != null;

                if (isId) {
                    idFieldName = fName;
                    idColumnName = cName;
                    idType = getBoxedType(fType);
                    isIdGenerated = isGenerated;
                }

                if (isVersion) versionFieldName = fName;
                if (isCreatedAt) createdAtFieldName = fName;
                if (isUpdatedAt) updatedAtFieldName = fName;

                fields.add(new FieldInfo(fName, cName, fType, nullable, length, unique, isId, isVersion, isCreatedAt, isUpdatedAt, isEncrypted));
            }
        }

        if (idFieldName == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "No @Id field found in Entity: " + className);
            return;
        }

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + metaClassName);
        try (Writer writer = builderFile.openWriter()) {
            StringBuilder sb = new StringBuilder();
            sb.append("package ").append(packageName).append(";\n\n");
            sb.append("import com.velocityorm.core.metadata.ColumnMeta;\n");
            sb.append("import com.velocityorm.core.metadata.EntityMeta;\n");
            sb.append("import java.sql.PreparedStatement;\n");
            sb.append("import java.sql.ResultSet;\n");
            sb.append("import java.sql.SQLException;\n");
            sb.append("import java.util.List;\n");
            sb.append("import java.util.ArrayList;\n\n");

            sb.append("public class ").append(metaClassName).append(" implements EntityMeta<").append(className).append(", ").append(idType).append("> {\n");
            sb.append("    private final List<ColumnMeta> columns = new ArrayList<>();\n\n");

            sb.append("    public ").append(metaClassName).append("() {\n");
            for (FieldInfo f : fields) {
                sb.append("        columns.add(new ColumnMeta(\"")
                  .append(f.colName).append("\", \"")
                  .append(f.fieldName).append("\", ")
                  .append(f.type).append(".class, ")
                  .append(f.nullable).append(", ")
                  .append(f.length).append(", ")
                  .append(f.unique).append(", ")
                  .append(f.isId).append(", ")
                  .append(f.isVersion).append(", ")
                  .append(f.isCreatedAt).append(", ")
                  .append(f.isUpdatedAt).append(", ")
                  .append(f.isEncrypted).append("));\n");
            }
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public Class<").append(className).append("> getEntityClass() { return ").append(className).append(".class; }\n\n");

            sb.append("    @Override\n");
            sb.append("    public String getTableName() { return \"").append(tableName).append("\"; }\n\n");

            sb.append("    @Override\n");
            sb.append("    public String getIdColumnName() { return \"").append(idColumnName).append("\"; }\n\n");

            sb.append("    @Override\n");
            sb.append("    public String getIdFieldName() { return \"").append(idFieldName).append("\"; }\n\n");

            sb.append("    @Override\n");
            sb.append("    public Class<?> getIdType() { return ").append(idType).append(".class; }\n\n");

            sb.append("    @Override\n");
            sb.append("    public boolean isIdGenerated() { return ").append(isIdGenerated).append("; }\n\n");

            sb.append("    @Override\n");
            sb.append("    public List<ColumnMeta> getColumns() { return columns; }\n\n");

            sb.append("    @Override\n");
            sb.append("    public ColumnMeta getColumnByFieldName(String fieldName) {\n");
            sb.append("        for (ColumnMeta col : columns) {\n");
            sb.append("            if (col.getFieldName().equals(fieldName)) return col;\n");
            sb.append("        }\n");
            sb.append("        return null;\n");
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public ").append(idType).append(" getId(").append(className).append(" entity) {\n");
            sb.append("        return entity.get").append(capitalize(idFieldName)).append("();\n");
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public void setId(").append(className).append(" entity, ").append(idType).append(" id) {\n");
            sb.append("        entity.set").append(capitalize(idFieldName)).append("(id);\n");
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public Object getVersion(").append(className).append(" entity) {\n");
            if (versionFieldName != null) {
                sb.append("        return entity.get").append(capitalize(versionFieldName)).append("();\n");
            } else {
                sb.append("        return null;\n");
            }
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public void setVersion(").append(className).append(" entity, Object version) {\n");
            if (versionFieldName != null) {
                sb.append("        entity.set").append(capitalize(versionFieldName)).append("((").append(getFieldTypeByFieldName(fields, versionFieldName)).append(") version);\n");
            }
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public void setCreatedAt(").append(className).append(" entity, Object timestamp) {\n");
            if (createdAtFieldName != null) {
                sb.append("        entity.set").append(capitalize(createdAtFieldName)).append("((").append(getFieldTypeByFieldName(fields, createdAtFieldName)).append(") timestamp);\n");
            }
            sb.append("    }\n\n");

            sb.append("    @Override\n");
            sb.append("    public void setUpdatedAt(").append(className).append(" entity, Object timestamp) {\n");
            if (updatedAtFieldName != null) {
                sb.append("        entity.set").append(capitalize(updatedAtFieldName)).append("((").append(getFieldTypeByFieldName(fields, updatedAtFieldName)).append(") timestamp);\n");
            }
            sb.append("    }\n\n");

            // Reflection-free getters
            for (FieldInfo f : fields) {
                if (f.isId) continue;
                sb.append("    public ").append(f.type).append(" get").append(capitalize(f.fieldName)).append("(").append(className).append(" entity) {\n");
                sb.append("        return entity.get").append(capitalize(f.fieldName)).append("();\n");
                sb.append("    }\n\n");
            }

            // mapRow
            sb.append("    @Override\n");
            sb.append("    public ").append(className).append(" mapRow(ResultSet rs) throws SQLException {\n");
            sb.append("        ").append(className).append(" entity = new ").append(className).append("();\n");
            for (FieldInfo f : fields) {
                sb.append("        entity.set").append(capitalize(f.fieldName)).append("(");
                sb.append(getResultSetExtractor(f)).append(");\n");
            }
            sb.append("        return entity;\n");
            sb.append("    }\n\n");

            // bindInsert
            sb.append("    @Override\n");
            sb.append("    public void bindInsert(PreparedStatement ps, ").append(className).append(" entity) throws SQLException {\n");
            sb.append("        int idx = 1;\n");
            if (!isIdGenerated) {
                sb.append("        ps.setObject(idx++, entity.get").append(capitalize(idFieldName)).append("());\n");
            }
            for (FieldInfo f : fields) {
                if (f.isId) continue;
                sb.append("        ps.setObject(idx++, entity.get").append(capitalize(f.fieldName)).append("());\n");
            }
            sb.append("    }\n\n");

            // bindUpdate
            sb.append("    @Override\n");
            sb.append("    public void bindUpdate(PreparedStatement ps, ").append(className).append(" entity) throws SQLException {\n");
            sb.append("        int idx = 1;\n");
            sb.append("        ps.setObject(idx++, entity.get").append(capitalize(idFieldName)).append("());\n");
            for (FieldInfo f : fields) {
                if (f.isId) continue;
                sb.append("        ps.setObject(idx++, entity.get").append(capitalize(f.fieldName)).append("());\n");
            }
            sb.append("    }\n");

            sb.append("}\n");
            writer.write(sb.toString());
        }
    }

    private void generateRepositoryImplClass(TypeElement classElement) throws Exception {
        String packageName = processingEnv.getElementUtils().getPackageOf(classElement).getQualifiedName().toString();
        String className = classElement.getSimpleName().toString();
        String repoImplName = className + "RepositoryImpl";
        
        String idType = "Long";
        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                if (field.getAnnotation(Id.class) != null) {
                    idType = getBoxedType(field.asType().toString());
                    break;
                }
            }
        }

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + repoImplName);
        try (Writer writer = builderFile.openWriter()) {
            StringBuilder sb = new StringBuilder();
            sb.append("package ").append(packageName).append(";\n\n");
            sb.append("import com.velocityorm.core.VelocityORM;\n");
            sb.append("import com.velocityorm.core.metadata.EntityMeta;\n");
            sb.append("import com.velocityorm.core.repository.BaseRepository;\n\n");
            
            sb.append("public class ").append(repoImplName).append(" extends BaseRepository<").append(className).append(", ").append(idType).append("> {\n");
            sb.append("    public ").append(repoImplName).append("(VelocityORM orm, EntityMeta<").append(className).append(", ").append(idType).append("> meta) {\n");
            sb.append("        super(orm, meta);\n");
            sb.append("    }\n");
            sb.append("}\n");
            writer.write(sb.toString());
        }
    }

    private String getBoxedType(String type) {
        if ("long".equals(type)) return "Long";
        if ("int".equals(type)) return "Integer";
        if ("double".equals(type)) return "Double";
        if ("boolean".equals(type)) return "Boolean";
        return type;
    }

    private String getFieldTypeByFieldName(List<FieldInfo> fields, String name) {
        for (FieldInfo f : fields) {
            if (f.fieldName.equals(name)) return f.type;
        }
        return "Object";
    }

    private String getResultSetExtractor(FieldInfo f) {
        String col = "\"" + f.colName + "\"";
        if ("java.lang.String".equals(f.type) || "String".equals(f.type)) {
            return "rs.getString(" + col + ")";
        }
        if ("java.lang.Long".equals(f.type) || "Long".equals(f.type)) {
            return "rs.getObject(" + col + ") != null ? rs.getLong(" + col + ") : null";
        }
        if ("long".equals(f.type)) {
            return "rs.getLong(" + col + ")";
        }
        if ("java.lang.Integer".equals(f.type) || "Integer".equals(f.type)) {
            return "rs.getObject(" + col + ") != null ? rs.getInt(" + col + ") : null";
        }
        if ("int".equals(f.type)) {
            return "rs.getInt(" + col + ")";
        }
        if ("java.lang.Double".equals(f.type) || "Double".equals(f.type)) {
            return "rs.getObject(" + col + ") != null ? rs.getDouble(" + col + ") : null";
        }
        if ("double".equals(f.type)) {
            return "rs.getDouble(" + col + ")";
        }
        if ("java.lang.Boolean".equals(f.type) || "Boolean".equals(f.type)) {
            return "rs.getObject(" + col + ") != null ? rs.getBoolean(" + col + ") : null";
        }
        if ("boolean".equals(f.type)) {
            return "rs.getBoolean(" + col + ")";
        }
        if ("java.math.BigDecimal".equals(f.type)) {
            return "rs.getBigDecimal(" + col + ")";
        }
        if ("java.time.LocalDateTime".equals(f.type)) {
            return "rs.getTimestamp(" + col + ") != null ? rs.getTimestamp(" + col + ").toLocalDateTime() : null";
        }
        if ("java.util.Date".equals(f.type)) {
            return "rs.getTimestamp(" + col + ")";
        }
        return "(" + f.type + ") rs.getObject(" + col + ")";
    }

    private String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private static class FieldInfo {
        final String fieldName;
        final String colName;
        final String type;
        final boolean nullable;
        final int length;
        final boolean unique;
        final boolean isId;
        final boolean isVersion;
        final boolean isCreatedAt;
        final boolean isUpdatedAt;
        final boolean isEncrypted;

        FieldInfo(String fieldName, String colName, String type, boolean nullable, int length, boolean unique, 
                  boolean isId, boolean isVersion, boolean isCreatedAt, boolean isUpdatedAt, boolean isEncrypted) {
            this.fieldName = fieldName;
            this.colName = colName;
            this.type = ftype(type);
            this.nullable = nullable;
            this.length = length;
            this.unique = unique;
            this.isId = isId;
            this.isVersion = isVersion;
            this.isCreatedAt = isCreatedAt;
            this.isUpdatedAt = isUpdatedAt;
            this.isEncrypted = isEncrypted;
        }
        
        private String ftype(String t) {
            if (t.equals("long")) return "java.lang.Long";
            if (t.equals("int")) return "java.lang.Integer";
            if (t.equals("double")) return "java.lang.Double";
            if (t.equals("boolean")) return "java.lang.Boolean";
            return t;
        }
    }
}

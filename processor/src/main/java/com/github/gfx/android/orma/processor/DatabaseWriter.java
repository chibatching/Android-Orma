package com.github.gfx.android.orma.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

public class DatabaseWriter {

    static final String kClassName = "OrmaDatabase"; // TODO: let it customizable

    static final String kBuilderClassName = "Builder";

    static final Modifier[] publicStaticFinal = {
            Modifier.PUBLIC,
            Modifier.STATIC,
            Modifier.FINAL,
    };

    static final String connection = "connection";

    static final String SCHEMAS = "SCHEMAS";

    final ProcessingEnvironment processingEnv;

    List<SchemaDefinition> schemas = new ArrayList<>();

    public DatabaseWriter(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void add(SchemaDefinition schema) {
        schemas.add(schema);
    }

    public boolean isRequired() {
        return schemas.size() > 0;
    }

    public String getPackageName() {
        assert isRequired();

        return schemas.get(0).getPackageName();
    }

    public TypeSpec buildTypeSpec() {
        assert isRequired();

        ClassName builderClass = ClassName.get(getPackageName(), kBuilderClassName);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(kClassName);
        classBuilder.addJavadoc("The database handle class.<br><br>\n");
        classBuilder.addJavadoc("This is generated by {@code $L}\n", OrmaProcessor.class.getCanonicalName());
        classBuilder.addModifiers(Modifier.PUBLIC);

        classBuilder.addType(buildBuilderTypeSpec(builderClass));

        classBuilder.addFields(buildFieldSpecs());
        classBuilder.addMethods(buildMethodSpecs(builderClass));

        return classBuilder.build();
    }

    private TypeSpec buildBuilderTypeSpec(ClassName builderClass) {
        TypeSpec.Builder builder = TypeSpec.classBuilder(builderClass.simpleName());

        builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        builder.superclass(ParameterizedTypeName.get(Types.OrmaConfiguration, builderClass));

        builder.addMethod(MethodSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder(Types.Context, "context")
                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                        .build())
                .addStatement("super(context)")
                .build());

        builder.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(getPackageName(), kClassName))
                .addStatement("return new $L(new $T(this.fillDefaults(), $L))", kClassName, Types.OrmaConnection, SCHEMAS)
                .build());

        return builder.build();
    }

    public List<FieldSpec> buildFieldSpecs() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();

        List<FieldSpec> schemaFields = new ArrayList<>();

        schemas.forEach(schema -> {
            schemaFields.add(
                    FieldSpec.builder(schema.getSchemaClassName(),
                            "schema" + schema.getModelClassName().simpleName())
                            .addModifiers(publicStaticFinal)
                            .initializer("new $T()", schema.getSchemaClassName())
                            .build());
        });

        fieldSpecs.addAll(schemaFields);

        fieldSpecs.add(
                FieldSpec.builder(Types.getList(Types.WildcardSchema), SCHEMAS, publicStaticFinal)
                        .initializer(buildSchemasInitializer(schemaFields))
                        .build());

        fieldSpecs.add(
                FieldSpec.builder(Types.OrmaConnection, connection, Modifier.PRIVATE, Modifier.FINAL)
                        .build());

        return fieldSpecs;
    }

    private CodeBlock buildSchemasInitializer(List<FieldSpec> schemaFields) {
        CodeBlock.Builder builder = CodeBlock.builder();
        builder.add("$T.<$T>asList(\n", Types.Arrays, Types.WildcardSchema).indent();

        for (int i = 0; i < schemaFields.size(); i++) {
            builder.add("$N", schemaFields.get(i));

            if ((i + 1) != schemaFields.size()) {
                builder.add(",\n");
            } else {
                builder.add("\n");
            }
        }

        builder.unindent().add(")");
        return builder.build();
    }

    public List<MethodSpec> buildMethodSpecs(ClassName builderClass) {
        List<MethodSpec> methodSpecs = new ArrayList<>();

        methodSpecs.addAll(buildConstructorSpecs());

        methodSpecs.add(
                MethodSpec.methodBuilder("builder")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(builderClass)
                        .addParameter(ParameterSpec.builder(Types.Context, "context")
                                .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                .build())
                        .addStatement("return new $T(context)", builderClass)
                        .build());

        methodSpecs.add(
                MethodSpec.methodBuilder("getSchemas")
                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.getList(Types.WildcardSchema))
                        .addStatement("return $L", SCHEMAS)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getConnection")
                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.OrmaConnection)
                        .addStatement("return $L", connection)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("transactionSync")
                        .addException(Types.TransactionAbortException)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(
                                ParameterSpec.builder(Types.TransactionTask, "task")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addStatement("$L.transactionSync(task)", connection)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("transactionAsync")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(
                                ParameterSpec.builder(Types.TransactionTask, "task")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addStatement("$L.transactionAsync(task)", connection)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("transactionNonExclusiveSync")
                        .addException(Types.TransactionAbortException)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(
                                ParameterSpec.builder(Types.TransactionTask, "task")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addStatement("$L.transactionNonExclusiveSync(task)", connection)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("transactionNonExclusiveAsync")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(
                                ParameterSpec.builder(Types.TransactionTask, "task")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addStatement("$L.transactionNonExclusiveAsync(task)", connection)
                        .build()
        );

        schemas.forEach(schema -> {
            String simpleModelName = schema.getModelClassName().simpleName();
            String schemaInstance = "schema" + simpleModelName;

            methodSpecs.add(MethodSpec.methodBuilder("create" + simpleModelName)
                    .addJavadoc(
                            "Creates and inserts a model built by {@code Modelbuilder<T>}.\n"
                                    + "The return value has a newly created row id\n")
                    .addAnnotation(Specs.buildNonNullAnnotationSpec())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(schema.getModelClassName())
                    .addParameter(
                            ParameterSpec.builder(Types.getModelBuilder(schema.getModelClassName()), "builder")
                                    .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                    .build()
                    )
                    .addStatement("return $L.createModel($L, builder)", connection, schemaInstance)
                    .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("selectFrom" + simpleModelName)
                            .addJavadoc("Starts building query {@code SELECT * FROM $T ...}.\n", schema.getModelClassName())
                            .addAnnotation(Specs.buildNonNullAnnotationSpec())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(schema.getRelationClassName())
                            .addStatement("return new $T($L, $L)",
                                    schema.getRelationClassName(),
                                    connection,
                                    schemaInstance)
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("update" + simpleModelName)
                            .addJavadoc("Starts building query {@code UPDAT $T ...}.\n", schema.getModelClassName())
                            .addAnnotation(Specs.buildNonNullAnnotationSpec())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(schema.getUpdaterClassName())
                            .addStatement("return new $T($L, $L)",
                                    schema.getUpdaterClassName(),
                                    connection,
                                    schemaInstance)
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("deleteFrom" + simpleModelName)
                            .addJavadoc("Starts building query {@code DELETE FROM $T ...}.\n", schema.getModelClassName())
                            .addAnnotation(Specs.buildNonNullAnnotationSpec())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(schema.getDeleterClassName())
                            .addStatement("return new $T($L, $L)",
                                    schema.getDeleterClassName(),
                                    connection,
                                    schemaInstance)
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("insertInto" + simpleModelName)
                            .addJavadoc("Starts building query {@code INSERT INTO $T ...}.\n", schema.getModelClassName())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(long.class)
                            .addParameter(
                                    ParameterSpec.builder(schema.getModelClassName(), "model")
                                            .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                            .build()
                            )
                            .addStatement("return $L.insert($L, model)",
                                    connection,
                                    schemaInstance
                            )
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("prepareInsertInto" + simpleModelName)
                            .addJavadoc("Starts building a prepared statement for {@code INSERT INTO $T ...}.\n",
                                    schema.getModelClassName())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(Types.getInserter(schema.getModelClassName()))
                            .addStatement("return $L.prepareInsert($L)",
                                    connection,
                                    schemaInstance
                            )
                            .build());
        });

        return methodSpecs;
    }

    public List<MethodSpec> buildConstructorSpecs() {
        List<MethodSpec> methodSpecs = new ArrayList<>();

        methodSpecs.add(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(
                        ParameterSpec.builder(Types.OrmaConnection, connection)
                                .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                .build())
                .addStatement("this.$L = $L", connection, connection)
                .build());

        return methodSpecs;
    }
}

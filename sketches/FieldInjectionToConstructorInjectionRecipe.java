package com.example.rewrite;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenRewrite recipe that converts @Autowired field injection to constructor injection.
 *
 * Before:
 * -------
 * @Service
 * public class MyService {
 *     @Autowired
 *     private MyRepository myRepository;
 * }
 *
 * After:
 * ------
 * @Service
 * public class MyService {
 *     private final MyRepository myRepository;
 *
 *     public MyService(MyRepository myRepository) {
 *         this.myRepository = myRepository;
 *     }
 * }
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class FieldInjectionToConstructorInjectionRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Convert @Autowired field injection to constructor injection";
    }

    @Override
    public String getDescription() {
        return "Replaces Spring @Autowired field injection with constructor injection. " +
                "Marks injected fields as final, removes @Autowired annotations from fields, " +
                "and generates a constructor that initializes all formerly injected fields. " +
                "Skips classes that already have a constructor with parameters.";
    }

    @JsonCreator
    public FieldInjectionToConstructorInjectionRecipe() {
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new FieldInjectionVisitor();
    }

    private static class FieldInjectionVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String AUTOWIRED_FQN =
                "org.springframework.beans.factory.annotation.Autowired";
        private static final AnnotationMatcher AUTOWIRED_MATCHER =
                new AnnotationMatcher("@" + AUTOWIRED_FQN);

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                        ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            // Collect all @Autowired fields
            List<J.VariableDeclarations> autowiredFields = cd.getBody().getStatements().stream()
                    .filter(s -> s instanceof J.VariableDeclarations)
                    .map(s -> (J.VariableDeclarations) s)
                    .filter(this::hasAutowiredAnnotation)
                    .collect(Collectors.toList());

            if (autowiredFields.isEmpty()) {
                return cd;
            }

            // Skip if a parameterized constructor already exists
            boolean hasParameterizedConstructor = cd.getBody().getStatements().stream()
                    .filter(s -> s instanceof J.MethodDeclaration)
                    .map(s -> (J.MethodDeclaration) s)
                    .anyMatch(m -> m.isConstructor()
                            && !m.getParameters().isEmpty()
                            && !(m.getParameters().size() == 1
                            && m.getParameters().get(0) instanceof J.Empty));

            if (hasParameterizedConstructor) {
                return cd;
            }

            // ----------------------------------------------------------------
            // Step 1 — Strip @Autowired, add `final`, normalize field spacing
            //
            // Each @Autowired field originally has two lines of prefix:
            //   "\n\n    " (blank line + indent) because the annotation occupied a line.
            // After removing the annotation we collapse that to "\n    " (single indent).
            // For the very first field inside the class body we keep "\n\n    " so the
            // opening brace still has one blank line before the first field.
            // ----------------------------------------------------------------
            List<Statement> newStatements = new ArrayList<>();
            boolean firstAutowiredSeen = false;
            for (Statement stmt : cd.getBody().getStatements()) {
                if (stmt instanceof J.VariableDeclarations
                        && hasAutowiredAnnotation((J.VariableDeclarations) stmt)) {

                    J.VariableDeclarations varDecl = (J.VariableDeclarations) stmt;

                    // Remove @Autowired annotation
                    varDecl = varDecl.withLeadingAnnotations(
                            varDecl.getLeadingAnnotations().stream()
                                    .filter(a -> !AUTOWIRED_MATCHER.matches(a))
                                    .collect(Collectors.toList())
                    );

                    // Add `final` modifier if absent
                    if (varDecl.getModifiers().stream()
                            .noneMatch(m -> m.getType() == J.Modifier.Type.Final)) {
                        List<J.Modifier> modifiers = new ArrayList<>(varDecl.getModifiers());
                        modifiers.add(new J.Modifier(
                                Tree.randomId(),
                                Space.SINGLE_SPACE,
                                Markers.EMPTY,
                                null,
                                J.Modifier.Type.Final,
                                Collections.emptyList()
                        ));
                        varDecl = varDecl.withModifiers(modifiers);
                    }

                    // First @Autowired field keeps the blank line after the opening brace.
                    // Subsequent ones are collapsed to a single-line indent (no blank line).
                    if (!firstAutowiredSeen) {
                        varDecl = varDecl.withPrefix(Space.build("\n\n    ", Collections.emptyList()));
                        firstAutowiredSeen = true;
                    } else {
                        varDecl = varDecl.withPrefix(Space.build("\n    ", Collections.emptyList()));
                    }

                    newStatements.add(varDecl);
                } else {
                    newStatements.add(stmt);
                }
            }
            cd = cd.withBody(cd.getBody().withStatements(newStatements));

            // ----------------------------------------------------------------
            // Step 2 — Build and inject constructor via JavaTemplate.
            //
            // JavaTemplate bug: the generated constructor is always named "Template".
            // Workaround: include "public" explicitly in the snippet, use a neutral
            // placeholder name "TargetClass", apply the template, then fix the name.
            // ----------------------------------------------------------------
            String params = autowiredFields.stream()
                    .map(f -> getSimpleTypeName(f) + " " + f.getVariables().get(0).getSimpleName())
                    .collect(Collectors.joining(", "));

            String assignments = autowiredFields.stream()
                    .map(f -> {
                        String n = f.getVariables().get(0).getSimpleName();
                        return "this." + n + " = " + n + ";";
                    })
                    .collect(Collectors.joining("\n        "));

            // "public" must be in the template string — it comes out correctly.
            // The constructor name "TargetClass" will be fixed in Step 3.
            JavaTemplate constructorTemplate = JavaTemplate
                    .builder("public TargetClass(" + params + ") {\n        " + assignments + "\n    }")
                    .build();

            cd = constructorTemplate.apply(
                    new Cursor(getCursor(), cd),
                    cd.getBody().getCoordinates().lastStatement()
            );

            // ----------------------------------------------------------------
            // Step 3 — Rename "TargetClass" → real class name.
            //
            // J.MethodDeclaration.getName() returns IdentifierWithAnnotations.
            // We must construct a new IdentifierWithAnnotations with the real
            // J.Identifier taken from cd.getName() — this carries correct type info.
            // ----------------------------------------------------------------
            List<Statement> stmts = new ArrayList<>(cd.getBody().getStatements());
            for (int i = stmts.size() - 1; i >= 0; i--) {
                if (stmts.get(i) instanceof J.MethodDeclaration) {
                    J.MethodDeclaration ctor = (J.MethodDeclaration) stmts.get(i);
                    if (ctor.isConstructor()) {
                        // Take the real class name identifier from the class declaration node
                        J.Identifier realNameId = cd.getName()
                                .withPrefix(Space.SINGLE_SPACE)
                                .withComments(Collections.emptyList());

                        // Wrap in IdentifierWithAnnotations, preserving any annotations
                        J.MethodDeclaration.IdentifierWithAnnotations renamed =
                                new J.MethodDeclaration.IdentifierWithAnnotations(
                                        realNameId,
                                        ctor.getName().getAnnotations()
                                );

                        ctor = ctor.withName(renamed.getIdentifier());
                        stmts.set(i, ctor);
                        break;
                    }
                }
            }
            cd = cd.withBody(cd.getBody().withStatements(stmts));

            // ----------------------------------------------------------------
            // Step 4 — Remove unused @Autowired import
            // ----------------------------------------------------------------
            maybeRemoveImport(AUTOWIRED_FQN);

            return cd;
        }

        private boolean hasAutowiredAnnotation(J.VariableDeclarations varDecl) {
            return varDecl.getLeadingAnnotations().stream()
                    .anyMatch(AUTOWIRED_MATCHER::matches);
        }

        private String getSimpleTypeName(J.VariableDeclarations varDecl) {
            TypeTree typeExpr = varDecl.getTypeExpression();
            if (typeExpr == null) return "Object";
            String typeName = typeExpr.toString().trim();
            if (typeName.contains(".")) {
                typeName = typeName.substring(typeName.lastIndexOf('.') + 1);
            }
            return typeName;
        }
    }
}
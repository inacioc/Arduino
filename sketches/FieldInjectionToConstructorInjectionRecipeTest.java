package com.example.rewrite;
// mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.32.0:run \
  -Drewrite.recipeArtifactCoordinates=com.example:rewrite-field-to-constructor:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.example.rewrite.FieldInjectionToConstructorInjectionRecipe
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

/**
 * Unit tests for {@link FieldInjectionToConstructorInjectionRecipe}.
 *
 * Type validation is partially relaxed because test sources reference types
 * (MyRepository, UserService, etc.) that only exist at runtime in real projects.
 * Spring annotations (@Service, @Autowired, etc.) ARE fully resolved via the
 * spring-beans / spring-context / spring-web jars on the test classpath.
 */
class FieldInjectionToConstructorInjectionRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .recipe(new FieldInjectionToConstructorInjectionRecipe())
                .parser(JavaParser.fromJavaVersion()
                        // Resolve @Service, @Component, @Autowired, @RestController from real jars
                        .classpath("spring-context", "spring-beans", "spring-web")
                        .logCompilationWarningsAndErrors(false))
                // Relax type validation for user-defined types (MyRepository, UserService…)
                // that do not exist on the test classpath — official OpenRewrite approach
                .typeValidationOptions(TypeValidation.builder()
                        .identifiers(false)
                        .methodInvocations(false)
                        .methodDeclarations(false)
                        .constructorInvocations(false)
                        .build());
    }

    // -------------------------------------------------------------------------
    // TEST 1 — Single @Autowired field
    // -------------------------------------------------------------------------
    @Test
    void singleAutowiredField() {
        rewriteRun(
                java(
                        """
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class MyService {
        
                            @Autowired
                            private MyRepository myRepository;
                        }
                        """,
                        """
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class MyService {
        
                            private final MyRepository myRepository;
        
                            public MyService(MyRepository myRepository) {
                                this.myRepository = myRepository;
                            }
                        }
                        """
                )
        );
    }

    // -------------------------------------------------------------------------
    // TEST 2 — Multiple @Autowired fields
    // -------------------------------------------------------------------------
    @Test
    void multipleAutowiredFields() {
        rewriteRun(
                java(
                        """
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class OrderService {
        
                            @Autowired
                            private OrderRepository orderRepository;
        
                            @Autowired
                            private PaymentService paymentService;
        
                            @Autowired
                            private NotificationService notificationService;
                        }
                        """,
                        """
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class OrderService {
        
                            private final OrderRepository orderRepository;
                            private final PaymentService paymentService;
                            private final NotificationService notificationService;
        
                            public OrderService(OrderRepository orderRepository, PaymentService paymentService, NotificationService notificationService) {
                                this.orderRepository = orderRepository;
                                this.paymentService = paymentService;
                                this.notificationService = notificationService;
                            }
                        }
                        """
                )
        );
    }

    // -------------------------------------------------------------------------
    // TEST 3 — Class with no @Autowired fields — should NOT be changed
    // -------------------------------------------------------------------------
    @Test
    void noAutowiredFields_shouldNotChange() {
        rewriteRun(
                java(
                        """
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class PureService {
        
                            private String name;
        
                            public String getName() {
                                return name;
                            }
                        }
                        """
                )
        );
    }

    // -------------------------------------------------------------------------
    // TEST 4 — Class already has a parameterized constructor — should NOT be changed
    // -------------------------------------------------------------------------
    @Test
    void existingParameterizedConstructor_shouldNotChange() {
        rewriteRun(
                java(
                        """
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class AlreadyMigratedService {
        
                            private final MyRepository myRepository;
        
                            public AlreadyMigratedService(MyRepository myRepository) {
                                this.myRepository = myRepository;
                            }
                        }
                        """
                )
        );
    }

    // -------------------------------------------------------------------------
    // TEST 5 — Mix of @Autowired and non-@Autowired fields
    //           Only @Autowired ones move to constructor; others stay as-is
    // -------------------------------------------------------------------------
    @Test
    void mixedFields_onlyAutowiredMigratedToConstructor() {
        rewriteRun(
                java(
                        """
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.stereotype.Component;
        
                        @Component
                        public class ReportComponent {
        
                            @Autowired
                            private ReportRepository reportRepository;
        
                            private String reportTitle = "Default";
        
                            private int pageSize = 10;
                        }
                        """,
                        """
                        import org.springframework.stereotype.Component;
        
                        @Component
                        public class ReportComponent {
        
                            private final ReportRepository reportRepository;
        
                            private String reportTitle = "Default";
        
                            private int pageSize = 10;
        
                            public ReportComponent(ReportRepository reportRepository) {
                                this.reportRepository = reportRepository;
                            }
                        }
                        """
                )
        );
    }

    // -------------------------------------------------------------------------
    // TEST 6 — @Autowired on a @RestController
    // -------------------------------------------------------------------------
    @Test
    void autowiredInRestController() {
        rewriteRun(
                java(
                        """
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.web.bind.annotation.RestController;
        
                        @RestController
                        public class UserController {
        
                            @Autowired
                            private UserService userService;
        
                            @Autowired
                            private UserMapper userMapper;
                        }
                        """,
                        """
                        import org.springframework.web.bind.annotation.RestController;
        
                        @RestController
                        public class UserController {
        
                            private final UserService userService;
                            private final UserMapper userMapper;
        
                            public UserController(UserService userService, UserMapper userMapper) {
                                this.userService = userService;
                                this.userMapper = userMapper;
                            }
                        }
                        """
                )
        );
    }

    // -------------------------------------------------------------------------
    // TEST 7 — @Autowired import is removed when no longer used
    // -------------------------------------------------------------------------
    @Test
    void autowiredImportIsRemoved() {
        rewriteRun(
                java(
                        """
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class CleanService {
        
                            @Autowired
                            private CleanRepository cleanRepository;
                        }
                        """,
                        """
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class CleanService {
        
                            private final CleanRepository cleanRepository;
        
                            public CleanService(CleanRepository cleanRepository) {
                                this.cleanRepository = cleanRepository;
                            }
                        }
                        """
                )
        );
    }

    // -------------------------------------------------------------------------
    // TEST 8 — Class with only a no-arg constructor: recipe adds the parameterized
    //           one alongside, leaving the no-arg intact
    // -------------------------------------------------------------------------
    @Test
    void classWithNoArgConstructor_shouldMigrate() {
        rewriteRun(
                java(
                        """
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class LegacyService {
        
                            @Autowired
                            private LegacyRepository legacyRepository;
        
                            public LegacyService() {
                            }
                        }
                        """,
                        """
                        import org.springframework.stereotype.Service;
        
                        @Service
                        public class LegacyService {
        
                            private final LegacyRepository legacyRepository;
        
                            public LegacyService() {
                            }
        
                            public LegacyService(LegacyRepository legacyRepository) {
                                this.legacyRepository = legacyRepository;
                            }
                        }
                        """
                )
        );
    }
}

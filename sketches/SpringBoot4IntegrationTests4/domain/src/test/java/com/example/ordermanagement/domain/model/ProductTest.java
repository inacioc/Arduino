package com.example.ordermanagement.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the {@link Product} domain aggregate — no Spring context.
 * Runs under Surefire (class name ends in {@code Test}).
 */
class ProductTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111001");

    private static Product validProduct() {
        return Product.create(ID, "Widget Alpha", new BigDecimal("49.99"), true);
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("builds a product and exposes its fields")
        void create_valid() {
            Product product = validProduct();

            assertThat(product.getId()).isEqualTo(ID);
            assertThat(product.getName()).isEqualTo("Widget Alpha");
            assertThat(product.getPrice()).isEqualByComparingTo("49.99");
            assertThat(product.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("rejects a null id")
        void create_nullId() {
            assertThatThrownBy(() -> Product.create(null, "Widget", BigDecimal.ONE, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("rejects a blank name")
        void create_blankName() {
            assertThatThrownBy(() -> Product.create(ID, "  ", BigDecimal.ONE, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("rejects a null name")
        void create_nullName() {
            assertThatThrownBy(() -> Product.create(ID, null, BigDecimal.ONE, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects a non-positive price")
        void create_nonPositivePrice() {
            assertThatThrownBy(() -> Product.create(ID, "Widget", BigDecimal.ZERO, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("price");
            assertThatThrownBy(() -> Product.create(ID, "Widget", new BigDecimal("-1.00"), true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("behaviour")
    class Behaviour {

        @Test
        @DisplayName("isOrderable() reflects availability")
        void isOrderable() {
            assertThat(validProduct().isOrderable()).isTrue();

            Product product = validProduct();
            product.markUnavailable();
            assertThat(product.isOrderable()).isFalse();

            product.markAvailable();
            assertThat(product.isOrderable()).isTrue();
        }

        @Test
        @DisplayName("rename() changes the name and rejects blanks")
        void rename() {
            Product product = validProduct();
            product.rename("Widget Beta");
            assertThat(product.getName()).isEqualTo("Widget Beta");

            assertThatThrownBy(() -> product.rename(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("changePrice() updates price and rejects non-positive values")
        void changePrice() {
            Product product = validProduct();
            product.changePrice(new BigDecimal("12.50"));
            assertThat(product.getPrice()).isEqualByComparingTo("12.50");

            assertThatThrownBy(() -> product.changePrice(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("priceFor() multiplies unit price by quantity")
        void priceFor() {
            Product product = validProduct();

            assertThat(product.priceFor(3)).isEqualByComparingTo("149.97");
        }

        @Test
        @DisplayName("priceFor() rejects a non-positive quantity")
        void priceFor_nonPositiveQuantity() {
            Product product = validProduct();

            assertThatThrownBy(() -> product.priceFor(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

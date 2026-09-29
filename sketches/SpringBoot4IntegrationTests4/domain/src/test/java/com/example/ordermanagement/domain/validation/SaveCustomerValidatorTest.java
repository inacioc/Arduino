package com.example.ordermanagement.domain.validation;

import com.example.ordermanagement.domain.exception.CustomerAlreadyExistsException;
import com.example.ordermanagement.domain.exception.DomainValidationException;
import com.example.ordermanagement.domain.model.Customer;
import com.example.ordermanagement.domain.port.in.SaveCustomerUseCase.SaveCustomerCommand;
import com.example.ordermanagement.domain.port.out.CustomerRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

class SaveCustomerValidatorTest {

    private final InMemoryCustomerRepository customerRepository = new InMemoryCustomerRepository();
    private final SaveCustomerValidator validator = new SaveCustomerValidator(customerRepository);

    @Test
    @DisplayName("a brand new email is valid")
    void newEmail_isValid() {
        assertThatCode(() -> validator.assertValid(new SaveCustomerCommand(
                "Ada", "Lovelace", "555-0100", "ada@example.com"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("email already registered → CustomerAlreadyExistsException, not a notification violation")
    void emailTaken_throwsCustomerAlreadyExists() {
        customerRepository.save(Customer.create("Ada", "Lovelace", "555-0100", "ada@example.com"));

        assertThatThrownBy(() -> validator.assertValid(new SaveCustomerCommand(
                "Grace", "Hopper", "555-0200", "ada@example.com")))
                .isInstanceOf(CustomerAlreadyExistsException.class);
    }

    @Test
    @DisplayName("blank names, blank telephone and invalid email → all four violations at once, uniqueness never checked")
    void missingMandatoryFields_collectsAllViolationsAndSkipsUniquenessCheck() {
        DomainValidationException ex = catchThrowableOfType(
                () -> validator.assertValid(new SaveCustomerCommand(" ", " ", " ", "not-an-email")),
                DomainValidationException.class);

        assertThat(ex.getViolations()).extracting(DomainViolation::code, DomainViolation::propertyPath)
                .containsExactly(
                        tuple(SaveCustomerValidator.FIRST_NAME_REQUIRED, "firstName"),
                        tuple(SaveCustomerValidator.LAST_NAME_REQUIRED, "lastName"),
                        tuple(SaveCustomerValidator.TELEPHONE_REQUIRED, "telephone"),
                        tuple(SaveCustomerValidator.EMAIL_INVALID, "email"));
    }

    private static final class InMemoryCustomerRepository implements CustomerRepositoryPort {
        private final Map<UUID, Customer> store = new HashMap<>();

        @Override public Customer save(Customer customer) { store.put(customer.getId(), customer); return customer; }
        @Override public Optional<Customer> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public Optional<Customer> findByEmail(String email) {
            return store.values().stream().filter(c -> c.getEmail().equals(email)).findFirst();
        }
        @Override public List<Customer> findAll() { return List.copyOf(store.values()); }
        @Override public void deleteById(UUID id) { store.remove(id); }
    }
}

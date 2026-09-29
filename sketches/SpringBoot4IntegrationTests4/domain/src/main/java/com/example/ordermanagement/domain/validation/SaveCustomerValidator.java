package com.example.ordermanagement.domain.validation;

import com.example.ordermanagement.domain.exception.CustomerAlreadyExistsException;
import com.example.ordermanagement.domain.model.Customer;
import com.example.ordermanagement.domain.port.in.SaveCustomerUseCase.SaveCustomerCommand;
import com.example.ordermanagement.domain.port.out.CustomerRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Functional rules for registering a customer: mirrors {@code SaveProductValidator} exactly
 * — mandatory fields are reported through {@link ValidationNotification} (defense in depth
 * behind the web adapter's Bean Validation), and the email-uniqueness check is reported as
 * {@link CustomerAlreadyExistsException} (409) rather than through the notification, so the
 * response matches whichever catches the duplicate first: this fast pre-check, or the
 * {@code uk_customers_email} constraint under a race. See {@code Validation.md}.
 */
@Component
public class SaveCustomerValidator implements DomainValidator<SaveCustomerCommand> {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static final String FIRST_NAME_REQUIRED = "FIRST_NAME_REQUIRED";
    public static final String LAST_NAME_REQUIRED = "LAST_NAME_REQUIRED";
    public static final String TELEPHONE_REQUIRED = "TELEPHONE_REQUIRED";
    public static final String EMAIL_INVALID = "EMAIL_INVALID";

    private final CustomerRepositoryPort customerRepository;

    public SaveCustomerValidator(CustomerRepositoryPort customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public void validate(SaveCustomerCommand command, ValidationNotification notification) {
        boolean missingMandatoryField = false;

        if (command.firstName() == null || command.firstName().isBlank()) {
            notification.addError(FIRST_NAME_REQUIRED, "firstName", "First name must not be blank.");
            missingMandatoryField = true;
        }
        if (command.lastName() == null || command.lastName().isBlank()) {
            notification.addError(LAST_NAME_REQUIRED, "lastName", "Last name must not be blank.");
            missingMandatoryField = true;
        }
        if (command.telephone() == null || command.telephone().isBlank()) {
            notification.addError(TELEPHONE_REQUIRED, "telephone", "Telephone must not be blank.");
            missingMandatoryField = true;
        }
        if (command.email() == null || !EMAIL_PATTERN.matcher(command.email()).matches()) {
            notification.addError(EMAIL_INVALID, "email", "Email must be a valid address.");
            missingMandatoryField = true;
        }
        // The uniqueness check needs a usable email to compare against - skip it rather than
        // query the repository with a null/invalid email or report a misleading duplicate.
        if (missingMandatoryField) {
            return;
        }

        Optional<Customer> existing = customerRepository.findByEmail(command.email());
        if (existing.isPresent()) {
            throw new CustomerAlreadyExistsException(command.email());
        }
    }
}

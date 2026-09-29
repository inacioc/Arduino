package com.example.ordermanagement.domain.model;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Customer domain entity.
 * <p>
 * A rich aggregate that guards its own invariants, mirroring {@link Product}'s shape:
 * a private constructor, a validating factory method, and behaviour (here, just
 * {@link #rename}/{@link #updateContactDetails}) rather than bare setters. Unlike
 * {@link Product} (whose id is client-supplied, an intentional upsert key - see that
 * class), {@link #create} generates its own id the same way {@link Order#create} does:
 * a customer is a genuinely new identity each time, never an upsert target.
 */
public class Customer {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UUID id;
    private String firstName;
    private String lastName;
    private String telephone;
    private String email;

    private Customer(UUID id, String firstName, String lastName, String telephone, String email) {
        this.id        = id;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.telephone = telephone;
        this.email     = email;
    }

    // ── Factory method (new customers) ──────────────────────────────────────

    public static Customer create(String firstName, String lastName, String telephone, String email) {
        requireContactDetails(firstName, lastName, telephone, email);
        return new Customer(UUID.randomUUID(), firstName, lastName, telephone, email);
    }

    // ── Reconstitution from persistence ──────────────────────────────────────

    public static Customer reconstitute(UUID id, String firstName, String lastName,
                                         String telephone, String email) {
        return new Customer(id, firstName, lastName, telephone, email);
    }

    // ── Behaviour ───────────────────────────────────────────────────────────────

    public void updateContactDetails(String telephone, String email) {
        if (telephone == null || telephone.isBlank()) {
            throw new IllegalArgumentException("Customer telephone must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(email == null ? "" : email).matches()) {
            throw new IllegalArgumentException("Customer email must be a valid address");
        }
        this.telephone = telephone;
        this.email     = email;
    }

    public void rename(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("Customer first name must not be blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Customer last name must not be blank");
        }
        this.firstName = firstName;
        this.lastName  = lastName;
    }

    /** Convenience for anything (e.g. a simulated email) that wants a display name. */
    public String fullName() {
        return firstName + " " + lastName;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void requireContactDetails(String firstName, String lastName,
                                                String telephone, String email) {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("Customer first name must not be blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Customer last name must not be blank");
        }
        if (telephone == null || telephone.isBlank()) {
            throw new IllegalArgumentException("Customer telephone must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(email == null ? "" : email).matches()) {
            throw new IllegalArgumentException("Customer email must be a valid address");
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()           { return id; }
    public String getFirstName()  { return firstName; }
    public String getLastName()   { return lastName; }
    public String getTelephone()  { return telephone; }
    public String getEmail()      { return email; }
}

package com.example.ordermanagement.domain.exception;

/**
 * A product with this name already exists. {@code name} is the identifier here
 * (not the product's {@code id}): {@code id} is a client-supplied UUID that
 * {@code ProductPersistenceAdapter.save} intentionally treats as an upsert key
 * (re-saving the same id updates the row — see that adapter's tests), so it can
 * never itself be "already taken". {@code name} is this catalogue's actual
 * business key — enforced by a unique constraint (migration {@code V3}) and
 * translated up from the resulting constraint violation by
 * {@code AbstractPersistenceAdapter}. See {@code Validation.md}.
 */
public class ProductAlreadyExistsException extends ResourceAlreadyExistsException {

    public ProductAlreadyExistsException(String name) {
        super("Product", name);
    }
}

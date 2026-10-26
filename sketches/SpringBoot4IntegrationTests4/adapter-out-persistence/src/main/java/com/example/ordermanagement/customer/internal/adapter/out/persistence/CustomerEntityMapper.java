package com.example.ordermanagement.customer.internal.adapter.out.persistence;

import com.example.ordermanagement.customer.Customer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerEntityMapper {

    // ── Domain → Entity ───────────────────────────────────────────────────────

    CustomerEntity toEntity(Customer customer);

    // ── Entity → Domain ───────────────────────────────────────────────────────

    default Customer toDomain(CustomerEntity entity) {
        return Customer.reconstitute(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getTelephone(),
                entity.getEmail()
        );
    }
}

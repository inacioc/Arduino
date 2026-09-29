package com.example.ordermanagement.infrastructure.adapter.in.web;

import com.example.ordermanagement.domain.exception.CustomerNotFoundException;
import com.example.ordermanagement.domain.port.in.GetCustomerUseCase;
import com.example.ordermanagement.domain.port.in.SaveCustomerUseCase;
import com.example.ordermanagement.domain.port.in.SaveCustomerUseCase.SaveCustomerCommand;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateCustomerRequest;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CustomerResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final SaveCustomerUseCase saveCustomer;
    private final GetCustomerUseCase getCustomer;

    public CustomerController(SaveCustomerUseCase saveCustomer, GetCustomerUseCase getCustomer) {
        this.saveCustomer = saveCustomer;
        this.getCustomer  = getCustomer;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        SaveCustomerCommand command = new SaveCustomerCommand(
                request.firstName(), request.lastName(), request.telephone(), request.email());
        CustomerResponse body = CustomerResponse.from(saveCustomer.save(command));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public CustomerResponse getById(@PathVariable UUID id) {
        return getCustomer.findById(id)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new CustomerNotFoundException(id.toString()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<CustomerResponse> getAll() {
        return getCustomer.findAll().stream()
                .map(CustomerResponse::from)
                .toList();
    }
}

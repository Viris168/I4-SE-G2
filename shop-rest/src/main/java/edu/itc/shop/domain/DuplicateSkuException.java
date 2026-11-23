package edu.itc.shop.domain;

import jakarta.ws.rs.ClientErrorException;

/** 409 for now; Task 3 turns it into a problem+json body through the mapper. */
public class DuplicateSkuException extends ClientErrorException {
    public DuplicateSkuException(String sku) {
        super("A product with SKU " + sku + " already exists", 409);
    }
}

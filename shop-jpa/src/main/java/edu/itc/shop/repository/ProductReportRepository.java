package edu.itc.shop.repository;

import java.util.List;

import edu.itc.shop.web.dto.ProductReportRow;

// Challenge: custom repository fragment, implemented with the Criteria API in ProductReportRepositoryImpl
public interface ProductReportRepository {

    /** Sortable attributes: sku, name, amount, stock, tagCount. {@code sort} is "attribute" or "attribute,asc|desc". */
    List<ProductReportRow> report(ProductReportFilter filter, String sort);
}

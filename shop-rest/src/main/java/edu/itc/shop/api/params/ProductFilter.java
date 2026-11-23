package edu.itc.shop.api.params;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.ProductQuery;

/** Query parameters of GET /products, grouped with @BeanParam and checked with @Valid. */
public class ProductFilter {
    static final String CURRENCY = "EUR";

    @QueryParam("q") public String q;
    @QueryParam("minPrice") public Money minPrice;                 // "5 EUR"
    @QueryParam("maxPrice") public Money maxPrice;
    @QueryParam("inStock") @DefaultValue("false") public boolean inStock;

    /** The whitelist of sortable fields is in the pattern; nothing else reaches the service. */
    @QueryParam("sort") @DefaultValue("name,asc")
    @Pattern(regexp = "(name|price|stock),(asc|desc)",
             message = "use field,asc or field,desc with field one of name, price, stock")
    public String sort;

    @QueryParam("page") @DefaultValue("0") @Min(0) public int page;
    @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) public int size;

    public String sortField() { return sort.split(",")[0]; }
    public boolean descending() { return sort.endsWith(",desc"); }

    @AssertTrue(message = "minPrice and maxPrice must be in " + CURRENCY)
    public boolean isPriceCurrency() {
        return (minPrice == null || CURRENCY.equals(minPrice.currency()))
            && (maxPrice == null || CURRENCY.equals(maxPrice.currency()));
    }

    public ProductQuery toQuery() {
        return new ProductQuery(q,
                minPrice == null ? null : minPrice.amount(),
                maxPrice == null ? null : maxPrice.amount(),
                inStock, sortField(), descending());
    }
}

package edu.itc.shop.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import edu.itc.shop.domain.Money_;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.Product_;
import edu.itc.shop.domain.Tag;
import edu.itc.shop.domain.Tag_;
import edu.itc.shop.web.dto.ProductReportRow;

// Challenge: Criteria API report with the static metamodel, no attribute names as strings
class ProductReportRepositoryImpl implements ProductReportRepository {

    private final EntityManager em;

    ProductReportRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<ProductReportRow> report(ProductReportFilter filter, String sort) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ProductReportRow> query = cb.createQuery(ProductReportRow.class);
        Root<Product> p = query.from(Product.class);

        Path<String> sku = p.get(Product_.sku);
        Path<String> name = p.get(Product_.name);
        Path<java.math.BigDecimal> amount = p.get(Product_.price).get(Money_.amount);
        Path<Integer> stock = p.get(Product_.stock);
        // size() is a correlated count over product_tag, so it counts ALL tags of the product,
        // not only the one matched by the optional tag filter below
        Expression<Integer> tagCount = cb.size(p.get(Product_.tags));

        List<Predicate> where = new ArrayList<>();
        if (filter.q() != null && !filter.q().isBlank()) {
            where.add(cb.like(cb.lower(name), "%" + filter.q().toLowerCase(Locale.ROOT) + "%"));
        }
        if (filter.category() != null) {
            where.add(cb.equal(p.get(Product_.category), filter.category()));
        }
        if (filter.maxPrice() != null) {
            where.add(cb.le(amount, filter.maxPrice()));
        }
        if (filter.minStock() != null) {
            where.add(cb.ge(stock, filter.minStock()));
        }
        if (filter.tag() != null && !filter.tag().isBlank()) {
            // the join to tags exists only when a tag is requested
            Join<Product, Tag> t = p.join(Product_.tags);
            where.add(cb.equal(t.get(Tag_.name), filter.tag()));
        }

        Map<String, Expression<?>> sortable = Map.of(
                "sku", sku, "name", name, "amount", amount, "stock", stock, "tagCount", tagCount);
        String[] parts = (sort == null || sort.isBlank() ? "sku" : sort).split(",");
        Expression<?> sortBy = sortable.get(parts[0].trim());
        if (sortBy == null) {
            throw new InvalidSortException("cannot sort by '" + parts[0].trim() + "', allowed: " + sortable.keySet());
        }
        boolean desc = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc");

        query.select(cb.construct(ProductReportRow.class, sku, name, amount, stock, tagCount))
                .where(where.toArray(Predicate[]::new))
                // one row per product, even if a join ever produces several
                .groupBy(p.get(Product_.id), sku, name, amount, stock)
                .orderBy(desc ? cb.desc(sortBy) : cb.asc(sortBy), cb.asc(sku));
        return em.createQuery(query).getResultList();
    }
}

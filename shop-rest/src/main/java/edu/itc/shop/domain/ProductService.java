package edu.itc.shop.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

/**
 * Business rules (unique SKU, no negative stock) live here, not in the resource.
 * Writes are synchronized so check-then-save stays atomic across request threads.
 */
public class ProductService {
    private final ProductRepository repo;

    public ProductService(ProductRepository repo) { this.repo = repo; }

    /** Sortable fields; anything else is rejected, never forwarded. */
    public static final Set<String> SORT_FIELDS = Set.of("name", "price", "stock");
    private static final Map<String, Comparator<Product>> SORTS = Map.of(
            "name", Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER),
            "price", Comparator.comparing(Product::price),
            "stock", Comparator.comparingInt(Product::stock));

    public List<Product> list() {
        return repo.findAll();
    }

    /** Filters by name text, price bounds and stock, then sorts by a whitelisted field. */
    public List<Product> search(ProductQuery query) {
        Comparator<Product> order = SORTS.get(query.sortField());
        if (order == null) throw new BadRequestException("Cannot sort by '" + query.sortField() + "'");
        if (query.descending()) order = order.reversed();

        Predicate<Product> keep = p -> true;
        if (query.text() != null && !query.text().isBlank()) {
            String needle = query.text().toLowerCase(Locale.ROOT);
            keep = keep.and(p -> p.name().toLowerCase(Locale.ROOT).contains(needle));
        }
        if (query.minPrice() != null) keep = keep.and(p -> p.price().compareTo(query.minPrice()) >= 0);
        if (query.maxPrice() != null) keep = keep.and(p -> p.price().compareTo(query.maxPrice()) <= 0);
        if (query.inStockOnly()) keep = keep.and(p -> p.stock() > 0);

        return repo.findAll().stream().filter(keep).sorted(order.thenComparingLong(Product::id)).toList();
    }

    public Product get(long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    public synchronized Product create(Product p) {
        repo.findBySku(p.sku()).ifPresent(existing -> { throw new DuplicateSkuException(p.sku()); });
        return repo.save(new Product(0, p.name(), p.sku(), p.price(), p.stock(), 0));
    }

    public synchronized Product replace(long id, Product changes) {
        Product current = get(id);
        repo.findBySku(changes.sku())
            .filter(other -> other.id() != id)
            .ifPresent(other -> { throw new DuplicateSkuException(changes.sku()); });
        return repo.save(new Product(id, changes.name(), changes.sku(), changes.price(),
                                     changes.stock(), current.version()));
    }

    public synchronized Product adjustStock(long id, int delta) {
        Product current = get(id);
        int newStock = current.stock() + delta;
        if (newStock < 0) throw new InsufficientStockException(id, current.stock(), delta);
        return repo.save(current.withStock(newStock));
    }

    public synchronized void delete(long id) {
        if (!repo.delete(id)) throw new NotFoundException("Product " + id + " not found");
    }
}

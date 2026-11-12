package edu.itc.shop.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;

// Challenge: nightly supplier import, CSV format sku;name;amount;currency;category;stock
@Service
public class BulkImportService {

    private static final int CHUNK = 500;

    private final SessionFactory sessionFactory;
    private final ProductRepository products;
    private final EntityManager em;

    public BulkImportService(EntityManagerFactory emf, ProductRepository products, EntityManager em) {
        this.sessionFactory = emf.unwrap(SessionFactory.class);
        this.products = products;
        this.em = em;
    }

    /** StatelessSession: no persistence context, no dirty checking, one JDBC batch per chunk. */
    public int importCsv(Path csv) throws IOException {
        int imported = 0;
        try (StatelessSession ss = sessionFactory.openStatelessSession();
             Stream<String> lines = Files.lines(csv).skip(1)) {
            Transaction tx = ss.beginTransaction();
            ss.setJdbcBatchSize(CHUNK);
            List<Product> chunk = new ArrayList<>(CHUNK);
            for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                chunk.add(parse(it.next()));
                if (chunk.size() == CHUNK) {
                    ss.insertMultiple(chunk);
                    imported += chunk.size();
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                ss.insertMultiple(chunk);
                imported += chunk.size();
            }
            tx.commit();
        }
        return imported;
    }

    /** saveAll in one transaction; every entity stays managed unless flushClear is true. */
    @Transactional
    public int importWithSaveAll(Path csv, boolean flushClear) throws IOException {
        int imported = 0;
        try (Stream<String> lines = Files.lines(csv).skip(1)) {
            List<Product> chunk = new ArrayList<>(CHUNK);
            for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                chunk.add(parse(it.next()));
                if (chunk.size() == CHUNK || !it.hasNext()) {
                    products.saveAll(chunk);
                    imported += chunk.size();
                    chunk = new ArrayList<>(CHUNK);
                    if (flushClear) {
                        em.flush();
                        em.clear();
                    }
                }
            }
        }
        return imported;
    }

    static Product parse(String line) {
        String[] f = line.split(";", -1);
        return new Product(f[0], f[1], new Money(new BigDecimal(f[2]), f[3]),
                Category.valueOf(f[4]), Integer.parseInt(f[5]));
    }
}

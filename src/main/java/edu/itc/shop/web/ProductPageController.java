package edu.itc.shop.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import edu.itc.shop.service.PricingService;
import edu.itc.shop.service.ProductService;
import edu.itc.shop.web.dto.CreateProduct;
import edu.itc.shop.web.dto.ProductDto;
import edu.itc.shop.web.dto.ProductRow;

@Controller
@RequestMapping("/products")
public class ProductPageController {

    private static final Logger log = LoggerFactory.getLogger(ProductPageController.class);

    private final ProductService service;
    private final PricingService pricing;

    public ProductPageController(ProductService service, PricingService pricing) {
        this.service = service;
        this.pricing = pricing;
    }

    @ModelAttribute("currency")
    public String currency() {
        return pricing.currency();
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        List<ProductRow> rows = service.search(q).stream()
                .map(p -> ProductRow.from(p, pricing.gross(p.price())))
                .toList();
        model.addAttribute("products", rows);
        model.addAttribute("q", q);
        model.addAttribute("threshold", pricing.lowStockThreshold());
        return "products/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("product", new CreateProduct("", BigDecimal.ZERO, 0));
        return "products/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("product") CreateProduct product, BindingResult result,
                         RedirectAttributes redirect) {
        if (!result.hasFieldErrors("name") && service.nameExists(product.name())) {
            result.rejectValue("name", "duplicate", "a product with this name already exists");
        }
        if (result.hasErrors()) {
            log.warn("Validation failed for product form: {}", result.getFieldErrors().stream()
                    .map(e -> e.getField() + " " + e.getDefaultMessage()).toList());
            return "products/form";
        }
        ProductDto created = service.create(product);
        redirect.addFlashAttribute("message", "Product '" + created.name() + "' created");
        return "redirect:/products";
    }
}

package edu.itc.shop.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.itc.shop.service.PricingService;
import edu.itc.shop.service.ProductService;
import edu.itc.shop.web.dto.CreateProduct;
import edu.itc.shop.web.dto.ProductDto;
import edu.itc.shop.web.dto.ProductRow;

@WebMvcTest(ProductPageController.class)
class ProductPageControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductService service;

    @MockitoBean
    PricingService pricing;

    @Test
    void listShowsProductsWithGrossPrice() throws Exception {
        given(service.search(null)).willReturn(List.of(new ProductDto(2, "Keyboard", new BigDecimal("89.00"), 3)));
        given(pricing.gross(new BigDecimal("89.00"))).willReturn(new BigDecimal("97.90"));
        given(pricing.currency()).willReturn("USD");
        given(pricing.lowStockThreshold()).willReturn(5);

        mvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/list"))
                .andExpect(model().attribute("currency", "USD"))
                .andExpect(model().attribute("threshold", 5))
                .andExpect(model().attribute("products",
                        List.of(new ProductRow(2, "Keyboard", new BigDecimal("89.00"), new BigDecimal("97.90"), 3))));
    }

    @Test
    void invalidFormIsShownAgainWithFieldErrors() throws Exception {
        mvc.perform(post("/products").param("name", "").param("price", "0").param("stock", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/form"))
                .andExpect(model().attributeHasFieldErrors("product", "name", "price", "stock"));

        verify(service, never()).create(any());
    }

    @Test
    void duplicateNameIsRejectedOnTheForm() throws Exception {
        given(service.nameExists("Keyboard")).willReturn(true);

        mvc.perform(post("/products").param("name", "Keyboard").param("price", "1.00").param("stock", "1"))
                .andExpect(view().name("products/form"))
                .andExpect(model().attributeHasFieldErrorCode("product", "name", "duplicate"));
    }

    @Test
    void validFormRedirectsWithFlashMessage() throws Exception {
        given(service.create(any(CreateProduct.class)))
                .willReturn(new ProductDto(6, "USB-C cable", new BigDecimal("7.90"), 25));

        mvc.perform(post("/products").param("name", "USB-C cable").param("price", "7.90").param("stock", "25"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attribute("message", "Product 'USB-C cable' created"));
    }
}
